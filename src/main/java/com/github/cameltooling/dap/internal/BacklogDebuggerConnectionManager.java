/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.cameltooling.dap.internal;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.camel.api.management.mbean.ManagedBacklogDebuggerMBean;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.api.management.mbean.ManagedSuspendableRouteMBean;
import org.eclipse.lsp4j.debug.ContinuedEventArguments;
import org.eclipse.lsp4j.debug.OutputEventArguments;
import org.eclipse.lsp4j.debug.OutputEventArgumentsCategory;
import org.eclipse.lsp4j.debug.PauseArguments;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.eclipse.lsp4j.debug.ThreadEventArguments;
import org.eclipse.lsp4j.debug.ThreadEventArgumentsReason;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.github.cameltooling.dap.internal.model.CamelBreakpoint;
import com.github.cameltooling.dap.internal.model.CamelExchangeThread;
import com.github.cameltooling.dap.internal.model.CamelRouteDefinitionThread;
import com.github.cameltooling.dap.internal.types.EventMessage;
import com.github.cameltooling.dap.internal.types.UnmarshallerEventMessage;
import com.sun.tools.attach.VirtualMachine;

public class BacklogDebuggerConnectionManager {

	private static final String OBJECTNAME_BACKLOGDEBUGGER = "org.apache.camel:context=*,type=tracer,name=BacklogDebugger";
	private static final String OBJECTNAME_CAMELCONTEXT = "org.apache.camel:context=*,type=context,name=*";
	private static final String OBJECTNAME_CAMELROUTES = "org.apache.camel:context=*,type=routes,name=*";
	public static final String DEFAULT_JMX_URI = "service:jmx:rmi:///jndi/rmi://localhost:1099/jmxrmi/camel";
	private static final Logger LOGGER = LoggerFactory.getLogger(BacklogDebuggerConnectionManager.class);

	public static final String ATTACH_PARAM_PID = "attach_pid";
	public static final String ATTACH_PARAM_JMX_URL = "attach_jmx_url";

	private volatile JMXConnector jmxConnector;
	private volatile MBeanServerConnection mbeanConnection;
	private volatile ManagedBacklogDebuggerMBean backlogDebugger;
	private volatile Document routesDOMDocument;
	private volatile IDebugProtocolClient client;
	private final Set<String> notifiedSuspendedBreakpointIds = ConcurrentHashMap.newKeySet();
	private final Set<CamelExchangeThread> camelExchangeThreads = ConcurrentHashMap.newKeySet();
	private final Set<CamelRouteDefinitionThread> camelDefinitionThreads = ConcurrentHashMap.newKeySet();
	private final AtomicInteger threadIdCounter = new AtomicInteger();
	private final Map<String, CamelBreakpoint> camelBreakpointsWithSources = new ConcurrentHashMap<>();
	
	private volatile boolean isStepping;
	private Thread checkSuspendedNodeThread;

	private String getLocalJMXUrl(String javaProcessPID) {
		try {
			final String localConnectorAddressProperty = "com.sun.management.jmxremote.localConnectorAddress";
			VirtualMachine vm = VirtualMachine.attach(javaProcessPID);
			vm.startLocalManagementAgent();
			String localJmxUrl = vm.getAgentProperties().getProperty(localConnectorAddressProperty);
			vm.detach();
			return localJmxUrl;
		} catch (Exception e) {
			LOGGER.error("Error when trying to get local JMX Url from provided PID", e);
			return null;
		}
	}

	/**
	 * @param args a Map of parameter. Currently supported: attach_pid
	 * @param client The debug adapter client proxy
	 * @return if it has been successfully attached
	 */
	public boolean attach(Map<String, Object> args, IDebugProtocolClient client) {
		this.client = client;
		try {
			String jmxAddress = (String) args.getOrDefault(ATTACH_PARAM_JMX_URL, DEFAULT_JMX_URI);
			Object pid = args.get(ATTACH_PARAM_PID);
			if (pid != null) {
				jmxAddress = getLocalJMXUrl((String) pid);
			}
			JMXServiceURL jmxUrl = new JMXServiceURL(jmxAddress);
			jmxConnector = connect(jmxUrl);
			mbeanConnection = jmxConnector.getMBeanServerConnection();
			ObjectName objectName = new ObjectName(OBJECTNAME_BACKLOGDEBUGGER);
			Set<ObjectName> names = mbeanConnection.queryNames(objectName, null);
			if (names != null && !names.isEmpty()) {
				ObjectName debuggerMBeanObjectName = names.iterator().next();
				backlogDebugger = JMX.newMBeanProxy(mbeanConnection, debuggerMBeanObjectName,
						ManagedBacklogDebuggerMBean.class);
				try {
					backlogDebugger.setIncludeExchangeProperties(true);
				} catch(Exception ex) {
					// Ignore, we might be connected to pre 4.2 Camel version
					System.out.println(ex);
				}
				try {
					backlogDebugger.setIncludeExchangeVariables(true);
				} catch(Exception ex) {
					// Ignore, we might be connected to pre 4.4 Camel version
					System.out.println(ex);
				}
				backlogDebugger.enableDebugger();
				routesDOMDocument = retrieveRoutesWithSourceLineNumber(jmxAddress);
				
				checkSuspendedNodeThread = new Thread((Runnable) this::checkLoop, "Camel DAP - Check Suspended node");
				checkSuspendedNodeThread.start();
				return true;
			} else {
				String errorMessage = "No BacklogDebugger found on connection with "+ jmxAddress;
				LOGGER.warn(errorMessage);
				sendAttachErrorOutput(client, errorMessage);
			}
		} catch (Exception e) {
			LOGGER.error("Error trying to attach", e);
			sendAttachErrorOutput(client, e.getMessage());
		}
		return false;
	}

	private void sendAttachErrorOutput(IDebugProtocolClient client, String specificErrorMessage) {
		OutputEventArguments errorEvent = new OutputEventArguments();
		errorEvent.setCategory(OutputEventArgumentsCategory.STDERR);
		errorEvent.setOutput("Error when trying to connect the Camel debugger: "+specificErrorMessage+"\n"
				+ "Please check that the Camel application under debug has the following requirements:\n"
				+ " - version 3.16+\n"
				+ " - camel-debug is available on the classpath\n"
				+ " - have JMX enabled\n"
				+ "It might be interesting having also a look to the Debug Adapter for Camel log: "+ System.getProperty("java.io.tmpdir") + File.separator +  "log-camel-dap.log.\n");
		client.output(errorEvent);
	}

	private JMXConnector connect(JMXServiceURL jmxUrl) throws IOException {
		JMXConnector connector = null;
		int tries = 0;
		int maxTries = 10;
		int delayBetweenTries = 200;
		while (connector == null && tries < maxTries) {
			tries++;
			try {
				connector = JMXConnectorFactory.connect(jmxUrl);
			} catch (Exception e) {
				if(tries >= maxTries) {
					throw e;
				}
				try {
					Thread.sleep(delayBetweenTries);
				} catch (InterruptedException e1) {
					Thread.currentThread().interrupt();
				}
			}
		}
		return connector;
	}

	private void checkLoop() {
		while(!Thread.currentThread().isInterrupted() && backlogDebugger != null && backlogDebugger.isEnabled()) {
			Set<String> suspendedBreakpointNodeIds = backlogDebugger.suspendedBreakpointNodeIds();
			for (String nodeId : suspendedBreakpointNodeIds) {
				handleSuspendedBreakpoint(nodeId);
			}
			
			handleRouteDefinitions();
			
			// TODO: might worth updating routesDomDocument?
			
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private void handleRouteDefinitions() {
		// TODO: support several Camel context - especially 2 routes with same name but in a different context
		try {
			Set<String> camelRouteIds = retrieveCamelRouteIds(mbeanConnection);
			registerThreadForNewCamelRoute(camelRouteIds);
			removeThreadForRemovedRoute(camelRouteIds);
		} catch (MalformedObjectNameException | IOException e) {
			LOGGER.error("Cannot retrieve Camel routes", e);
		}
	}

	private void removeThreadForRemovedRoute(Set<String> camelRouteIds) {
		Set<CamelRouteDefinitionThread> camelDefinitionThreadsToRemove = new HashSet<>();
		for (CamelRouteDefinitionThread camelContextDefinitionThread : camelDefinitionThreads) {
			if(!camelRouteIds.contains(camelContextDefinitionThread.getName())) {
				camelDefinitionThreadsToRemove.add(camelContextDefinitionThread);
			}
		}
		for (CamelRouteDefinitionThread camelRouteDefinitionThreadToRemove : camelDefinitionThreadsToRemove) {
			camelDefinitionThreads.remove(camelRouteDefinitionThreadToRemove);
			ThreadEventArguments threadEventArguments = new ThreadEventArguments();
			threadEventArguments.setThreadId(camelRouteDefinitionThreadToRemove.getId());
			threadEventArguments.setReason(ThreadEventArgumentsReason.EXITED);
			client.thread(threadEventArguments);
		}
	}

	private void registerThreadForNewCamelRoute(Set<String> camelRouteIds) {
		for (String camelId : camelRouteIds) {
			Optional<CamelRouteDefinitionThread> definitionThreadOptional = camelDefinitionThreads
					.stream()
					.filter(definitionThread -> camelId.equals(definitionThread.getName()))
					.findAny();
			if (definitionThreadOptional.isEmpty()) {
				CamelRouteDefinitionThread camelRouteDefinitionThread = new CamelRouteDefinitionThread(camelId);
				camelDefinitionThreads.add(camelRouteDefinitionThread);
				ThreadEventArguments threadEventArguments = new ThreadEventArguments();
				threadEventArguments.setThreadId(camelRouteDefinitionThread.getId());
				threadEventArguments.setReason(ThreadEventArgumentsReason.STARTED);
				client.thread(threadEventArguments);
			}
		}
	}

	private Set<String> retrieveCamelRouteIds(MBeanServerConnection connection)
			throws MalformedObjectNameException, IOException {
		ObjectName camelRoutesObjectName = new ObjectName(OBJECTNAME_CAMELROUTES);
		Set<ObjectName> camelRouteMbeanNames = connection.queryNames(camelRoutesObjectName, null);
		Set<String> camelRouteIds = new HashSet<>();
		if (camelRouteMbeanNames != null && !camelRouteMbeanNames.isEmpty()) {
			for (ObjectName camelContextMbeanName : camelRouteMbeanNames) {
				ManagedRouteMBean camelRoute = JMX.newMBeanProxy(connection, camelContextMbeanName, ManagedRouteMBean.class);
				String camelRouteId = camelRoute.getRouteId();
				camelRouteIds.add(camelRouteId);
			}
		}
		return camelRouteIds;
	}

	private void handleSuspendedBreakpoint(String nodeId) {
		if (!isStepping && !notifiedSuspendedBreakpointIds.contains(nodeId)) {
			StoppedEventArguments stoppedEventArgs = new StoppedEventArguments();
			stoppedEventArgs.setReason(StoppedEventArgumentsReason.BREAKPOINT);
			// Keep using deprecated method to have it still working with 4.1- 
			String xml = backlogDebugger.dumpTracedMessagesAsXml(nodeId, true);
			EventMessage eventMessage = new UnmarshallerEventMessage().getUnmarshalledEventMessage(xml);
			Optional<CamelExchangeThread> thread = camelExchangeThreads.stream().filter(camelThread -> camelThread.getExchangeId().equals(eventMessage.getExchangeId())).findAny();
			if(thread.isEmpty()) {
				final int threadId = threadIdCounter.incrementAndGet();
				camelExchangeThreads.add(new CamelExchangeThread(threadId, nodeId, eventMessage, camelBreakpointsWithSources.get(nodeId)));
				ThreadEventArguments threadEventArguments = new ThreadEventArguments();
				threadEventArguments.setReason(ThreadEventArgumentsReason.STARTED);
				threadEventArguments.setThreadId(threadId);
				client.thread(threadEventArguments);
				stoppedEventArgs.setThreadId(threadId);
			} else {
				CamelExchangeThread camelThread = thread.get();
				CamelBreakpoint camelBreakpoint = retrieveCorrespondingBreakpoint(nodeId, camelThread);
				stoppedEventArgs.setThreadId(camelThread.getId());
				if (camelBreakpoint != null) {
					camelExchangeThreads.remove(camelThread);
					camelExchangeThreads.add(new CamelExchangeThread(camelThread.getId(), nodeId, eventMessage, camelBreakpoint));
				}
			}
			notifiedSuspendedBreakpointIds.add(nodeId);
			client.stopped(stoppedEventArgs);
		}
	}

	private CamelBreakpoint retrieveCorrespondingBreakpoint(String nodeId, CamelExchangeThread camelThread) {
		CamelBreakpoint camelBreakpoint = camelBreakpointsWithSources.get(nodeId);
		if(camelBreakpoint != null) {
			return camelBreakpoint;
		} else {
			String path = "//*[@id='" + nodeId + "']";
	        XPath xPath = XPathFactory.newInstance().newXPath();
			try {
				Node tagNode = (Node) xPath.evaluate(path, routesDOMDocument, XPathConstants.NODE);
				if (tagNode != null) {
					Element tag = (Element) tagNode;
					String lineNumber = tag.getAttribute("sourceLineNumber");
					if (lineNumber != null && !"-1".equals(lineNumber.trim())) {
						// Suppose that it is a stepping and we stay in the same file but just the line is modified
						return new CamelBreakpoint(camelThread.getStackFrame().getSource(), Integer.valueOf(lineNumber));
					}
				}
			} catch (XPathExpressionException e) {
				LOGGER.warn("Cannot find the element with id "+ nodeId, e);
			}
		}
		return null;
	}

	private Document retrieveRoutesWithSourceLineNumber(String jmxAddress) throws Exception {
		MBeanServerConnection connection = mbeanConnection;
		ObjectName camelContextObjectName = new ObjectName(OBJECTNAME_CAMELCONTEXT);
		Set<ObjectName> camelContextMbeanNames = connection.queryNames(camelContextObjectName, null);
		if (camelContextMbeanNames != null && !camelContextMbeanNames.isEmpty()) {
			ObjectName mbeanName = camelContextMbeanNames.iterator().next();
			ManagedCamelContextMBean camelContext = JMX.newMBeanProxy(connection, mbeanName,
					ManagedCamelContextMBean.class);

			String routes = camelContext.dumpRoutesAsXml(false);
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			DocumentBuilder documentBuilder = dbf.newDocumentBuilder();
			InputStream targetStream = new ByteArrayInputStream(routes.getBytes());
			return documentBuilder.parse(targetStream);
		} else {
			LOGGER.warn("No Camel context found on connection with {}", jmxAddress);
			return null;
		}
	}

	public void terminate() {
		if (checkSuspendedNodeThread != null) {
			checkSuspendedNodeThread.interrupt();
			try {
				checkSuspendedNodeThread.join(2000);
			} catch (InterruptedException e) {
				LOGGER.warn("Error while waiting for Thread checking for suspended node to finish: {}", e.getMessage());
				Thread.currentThread().interrupt();
			}
		}
		
		if (backlogDebugger != null) {
			try {
				backlogDebugger.detach();
			} catch (Exception e) {
				LOGGER.warn("Could not detach the debugger: {}", e.getMessage());
			}
			backlogDebugger = null;
		}
		if (jmxConnector != null) {
			try {
				jmxConnector.close();
			} catch (IOException e) {
				LOGGER.error("Error while terminating debug session and closing connection", e);
			}
		}
	}

	public MBeanServerConnection getMbeanConnection() {
		return mbeanConnection;
	}

	public ManagedBacklogDebuggerMBean getBacklogDebugger() {
		return backlogDebugger;
	}

	public Document getRoutesDOMDocument() {
		return routesDOMDocument;
	}
	
	/**
	 * hThis is provided for testing purpose. This is supposed to be updated internally by this class only.
	 * 
	 * @param document
	 */
	public void setRoutesDomDocument(Document document) {
		this.routesDOMDocument = document;
	}

	public Set<String> getNotifiedSuspendedBreakpointIds() {
		return notifiedSuspendedBreakpointIds;
	}

	public void resumeAll() {
		for (CamelExchangeThread camelThread : camelExchangeThreads) {
			sendThreadExitEvent(camelThread);
		}
		backlogDebugger.resumeAll();
		camelExchangeThreads.clear();
		notifiedSuspendedBreakpointIds.clear();
		
		for (CamelRouteDefinitionThread camelContextDefinitionThread : camelDefinitionThreads) {
			resume(camelContextDefinitionThread);
		}
	}

	private void sendThreadResumeEvent(org.eclipse.lsp4j.debug.Thread camelThread) {
		ContinuedEventArguments args = new ContinuedEventArguments();
		args.setAllThreadsContinued(Boolean.FALSE);
		args.setThreadId(camelThread.getId());
		client.continued(args);
	}

	private void sendThreadExitEvent(CamelExchangeThread camelThread) {
		ThreadEventArguments threadEventArguments = new ThreadEventArguments();
		threadEventArguments.setReason(ThreadEventArgumentsReason.EXITED);
		threadEventArguments.setThreadId(camelThread.getId());
		client.thread(threadEventArguments);
	}

	public Set<CamelExchangeThread> getCamelExchangeThreads() {
		return camelExchangeThreads;
	}
	
	public Set<CamelRouteDefinitionThread> getCamelContextDefinitionThreads() {
		return camelDefinitionThreads;
	}
	
	public Set<org.eclipse.lsp4j.debug.Thread> getAllThreads() {
		return Stream.concat(camelExchangeThreads.stream(), camelDefinitionThreads.stream())
		        .collect(Collectors.toSet());
	}

	public void updateBreakpointsWithSources(CamelBreakpoint breakpoint) {
		this.camelBreakpointsWithSources.put(breakpoint.getNodeId(), breakpoint);
	}

	public void removeBreakpoint(String previouslySetBreakpointId) {
		backlogDebugger.removeBreakpoint(previouslySetBreakpointId);
		this.camelBreakpointsWithSources.remove(previouslySetBreakpointId);
	}

	public void resume(org.eclipse.lsp4j.debug.Thread camelThread) {
		if (camelThread instanceof CamelExchangeThread camelExchangeThread) {
			backlogDebugger.resumeBreakpoint(camelExchangeThread.getBreakPointId());
			notifiedSuspendedBreakpointIds.remove(camelExchangeThread.getBreakPointId());
			camelExchangeThreads.remove(camelThread);
			sendThreadExitEvent(camelExchangeThread);
		} else if (camelThread instanceof CamelRouteDefinitionThread camelRouteDefinitionThread) {
			ManagedRouteMBean camelRoute = findCorrespondingCamelRouteMBean(mbeanConnection, camelRouteDefinitionThread);
			if (camelRoute != null) {
				resume(camelThread, camelRoute);
			}
		}
	}

	private void resume(org.eclipse.lsp4j.debug.Thread camelThread, ManagedRouteMBean camelRoute) {
		try {
			if (camelRoute instanceof ManagedSuspendableRouteMBean suspendableCamelRoute) {
				suspendableCamelRoute.resume();
			} else {
				camelRoute.start();
			}
			sendThreadResumeEvent(camelThread);
		} catch (Exception e) {
			LOGGER.error("Cannot resume Camel route" + camelRoute.getCamelId(), e);
		}
	}

	public void next(org.eclipse.lsp4j.debug.Thread camelThread) {
		if (camelThread instanceof CamelExchangeThread camelExchangeThread) {
			isStepping = true;
			String breakPointId = camelExchangeThread.getBreakPointId();
			if(isLastInroute(breakPointId)) {
				camelExchangeThreads.remove(camelExchangeThread);
				sendThreadExitEvent(camelExchangeThread);
			}
			backlogDebugger.stepBreakpoint(breakPointId);
			notifiedSuspendedBreakpointIds.remove(breakPointId);
			isStepping = false;
		}
	}

	private boolean isLastInroute(String id) {
		String path = "//*[@id='" + id + "']";
        XPath xPath = XPathFactory.newInstance().newXPath();
        try {
            Node tagNode = (Node) xPath.evaluate(path, routesDOMDocument, XPathConstants.NODE);
            Element tag = (Element) tagNode;
            Node sibling = tag.getNextSibling();
            while (null != sibling && sibling.getNodeType() != Node.ELEMENT_NODE) {
            	sibling = sibling.getNextSibling();
            }
            if (sibling != null) {
            	return ((Element) sibling).getAttribute("id") == null;
            } else {
            	Node parent = tag.getParentNode();
            	while (null != parent && parent.getNodeType() != Node.ELEMENT_NODE) {
            		parent = parent.getNextSibling();
            	}
            	if (parent != null && !"route".equals(parent.getNodeName())) {
            		Element parentElement = (Element) parent;
            		return isLastInroute(parentElement.getAttribute("id"));
            	}
            }
        } catch (XPathExpressionException e) {
            return true;
        }
        return true;
	}

	public void suspend(PauseArguments args) {
		MBeanServerConnection connection = mbeanConnection;
		if (args.getThreadId() == 0) {
			for (CamelRouteDefinitionThread camelRouteDefinitionThread : camelDefinitionThreads) {
				suspend(connection, camelRouteDefinitionThread);
			}
		} else {
			Optional<CamelRouteDefinitionThread> camelDefinition = camelDefinitionThreads.stream()
					.filter(camelDefinitionThread -> args.getThreadId() == camelDefinitionThread.getId())
					.findAny();
			if (camelDefinition.isPresent()) {
				CamelRouteDefinitionThread camelRouteDefinitionThread = camelDefinition.get();
				suspend(connection, camelRouteDefinitionThread);
			}
		}
	}

	private void suspend(MBeanServerConnection connection, CamelRouteDefinitionThread camelRouteDefinitionThread) {
		ManagedRouteMBean camelRoute = findCorrespondingCamelRouteMBean(connection, camelRouteDefinitionThread);
		if (camelRoute != null) {
			suspend(camelRoute, camelRouteDefinitionThread);
		}
	}

	private ManagedRouteMBean findCorrespondingCamelRouteMBean(MBeanServerConnection connection,
			CamelRouteDefinitionThread camelRouteDefinitionThread) {
		try {
			ObjectName camelRoutesObjectName = new ObjectName(OBJECTNAME_CAMELROUTES);
			Set<ObjectName> camelRouteMbeanNames = connection.queryNames(camelRoutesObjectName, null);
			for (ObjectName camelRouteMbeanName : camelRouteMbeanNames) {
				String classNameOfMBean = connection.getMBeanInfo(camelRouteMbeanName).getClassName();
				if (classNameOfMBean.contains("ManagedSuspendableRoute")) {
					ManagedSuspendableRouteMBean camelRoute = JMX.newMBeanProxy(connection, camelRouteMbeanName, ManagedSuspendableRouteMBean.class);
					if (camelRoute.getRouteId().equals(camelRouteDefinitionThread.getName())) {
						return camelRoute;
					}
				} else {
					ManagedRouteMBean camelRoute = JMX.newMBeanProxy(connection, camelRouteMbeanName, ManagedRouteMBean.class);
					if (camelRoute.getRouteId().equals(camelRouteDefinitionThread.getName())) {
						return camelRoute;
					}
				}
			}
		} catch (MalformedObjectNameException | IOException | InstanceNotFoundException | IntrospectionException | ReflectionException e ) {
			LOGGER.error("Cannot retrieve Camel route", e);
		}
		return null;
	}

	private void suspend(ManagedRouteMBean camelRoute,
			CamelRouteDefinitionThread camelRouteDefinitionThread) {
		try {
			if(camelRoute instanceof ManagedSuspendableRouteMBean suspendableCamelRoute) {
				suspendableCamelRoute.suspend();
			} else {
				camelRoute.stop();
			}
			StoppedEventArguments stoppedEventArgs = new StoppedEventArguments();
			stoppedEventArgs.setAllThreadsStopped(Boolean.FALSE);
			stoppedEventArgs.setReason(StoppedEventArgumentsReason.PAUSE);
			stoppedEventArgs.setThreadId(camelRouteDefinitionThread.getId());
			client.stopped(stoppedEventArgs);
		} catch (Exception e) {
			LOGGER.error("Cannot suspend Camel route", e);
		}
	}
}
