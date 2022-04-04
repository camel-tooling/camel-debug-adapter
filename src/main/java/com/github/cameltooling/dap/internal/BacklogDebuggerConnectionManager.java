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
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
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
import com.github.cameltooling.dap.internal.model.CamelThread;
import com.github.cameltooling.dap.internal.types.EventMessage;
import com.github.cameltooling.dap.internal.types.UnmarshallerEventMessage;
import com.sun.tools.attach.VirtualMachine;

public class BacklogDebuggerConnectionManager {

	private static final String OBJECTNAME_BACKLOGDEBUGGER = "org.apache.camel:context=*,type=tracer,name=BacklogDebugger";
	private static final String OBJECTNAME_CAMELCONTEXT = "org.apache.camel:context=*,type=context,name=*";
	public static final String DEFAULT_JMX_URI = "service:jmx:rmi:///jndi/rmi://localhost:1099/jmxrmi/camel";
	private static final Logger LOGGER = LoggerFactory.getLogger(BacklogDebuggerConnectionManager.class);

	public static final String ATTACH_PARAM_PID = "attach_pid";
	public static final String ATTACH_PARAM_JMX_URL = "attach_jmx_url";

	private JMXConnector jmxConnector;
	private MBeanServerConnection mbeanConnection;
	private ManagedBacklogDebuggerMBean backlogDebugger;
	private Document routesDOMDocument;
	private IDebugProtocolClient client;
	private Set<String> notifiedSuspendedBreakpointIds = new HashSet<>();
	private Set<CamelThread> camelThreads = new HashSet<>();
	int threadIdCounter = 1;
	private Map<String, CamelBreakpoint> camelBreakpointsWithSources = new HashMap<>();
	
	private boolean isStepping = false;

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
				backlogDebugger.enableDebugger();
				routesDOMDocument = retrieveRoutesWithSourceLineNumber(jmxAddress);
				
				Thread checkSuspendedNodeThread = new Thread((Runnable) this::checkSuspendedBreakpoints, "Camel DAP - Check Suspended node");
				checkSuspendedNodeThread.start();
				return true;
			} else {
				LOGGER.warn("No BacklogDebugger found on connection with {}", jmxAddress);
			}
		} catch (Exception e) {
			LOGGER.error("Error trying to attach", e);
		}
		return false;
	}

	private JMXConnector connect(JMXServiceURL jmxUrl) throws IOException, InterruptedException {
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
				Thread.sleep(delayBetweenTries);
			}
			
		}
		return connector;
	}

	private void checkSuspendedBreakpoints() {
		while(backlogDebugger != null && backlogDebugger.isEnabled()) {
			Set<String> suspendedBreakpointNodeIds = backlogDebugger.suspendedBreakpointNodeIds();
			for (String nodeId : suspendedBreakpointNodeIds) {
				handleSuspendedBreakpoint(nodeId);
			}
			
			// TODO: might worth updating routesDomDocument?
			
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private void handleSuspendedBreakpoint(String nodeId) {
		if (!isStepping && !notifiedSuspendedBreakpointIds.contains(nodeId)) {
			StoppedEventArguments stoppedEventArgs = new StoppedEventArguments();
			stoppedEventArgs.setReason(StoppedEventArgumentsReason.BREAKPOINT);
			String xml = backlogDebugger.dumpTracedMessagesAsXml(nodeId, true);
			EventMessage eventMessage = new UnmarshallerEventMessage().getUnmarshalledEventMessage(xml);
			Optional<CamelThread> thread = camelThreads.stream().filter(camelThread -> camelThread.getExchangeId().equals(eventMessage.getExchangeId())).findAny();
			if(thread.isEmpty()) {
				camelThreads.add(new CamelThread(threadIdCounter, nodeId, eventMessage, camelBreakpointsWithSources.get(nodeId)));
				ThreadEventArguments threadEventArguments = new ThreadEventArguments();
				threadEventArguments.setReason(ThreadEventArgumentsReason.STARTED);
				threadEventArguments.setThreadId(threadIdCounter);
				client.thread(threadEventArguments);
				stoppedEventArgs.setThreadId(threadIdCounter);
				threadIdCounter++;
			} else {
				CamelThread camelThread = thread.get();
				CamelBreakpoint camelBreakpoint = retrieveCorrespondingBreakpoint(nodeId, camelThread);
				stoppedEventArgs.setThreadId(camelThread.getId());
				if (camelBreakpoint != null) {
					camelThreads.remove(camelThread);
					camelThreads.add(new CamelThread(camelThread.getId(), nodeId, eventMessage, camelBreakpoint));
				}
			}
			notifiedSuspendedBreakpointIds.add(nodeId);
			client.stopped(stoppedEventArgs);
		}
	}

	private CamelBreakpoint retrieveCorrespondingBreakpoint(String nodeId, CamelThread camelThread) {
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

	private Document retrieveRoutesWithSourceLineNumber(String jmxAddress) throws Exception{
		ObjectName camelContextObjectName = new ObjectName(OBJECTNAME_CAMELCONTEXT);
		Set<ObjectName> camelContextMbeanNames = mbeanConnection.queryNames(camelContextObjectName, null);
		if (camelContextMbeanNames != null && !camelContextMbeanNames.isEmpty()) {
			ObjectName mbeanName = camelContextMbeanNames.iterator().next();
			ManagedCamelContextMBean camelContext = JMX.newMBeanProxy(mbeanConnection, mbeanName,
					ManagedCamelContextMBean.class);

			String routes = camelContext.dumpRoutesAsXml(false, true);
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
		if (backlogDebugger != null) {
			backlogDebugger = null;
		}
		if (jmxConnector != null) {
			try {
				jmxConnector.close();
			} catch (IOException e) {
				LOGGER.error("Error while termintating debug session and closing connection", e);
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

	public Set<String> getNotifiedSuspendedBreakpointIds() {
		return notifiedSuspendedBreakpointIds;
	}

	public void resumeAll() {
		for (CamelThread camelThread : camelThreads) {
			sendThreadExitEvent(camelThread);
		}
		backlogDebugger.resumeAll();
		camelThreads.clear();
		notifiedSuspendedBreakpointIds.clear();
	}

	private void sendThreadExitEvent(CamelThread camelThread) {
		ThreadEventArguments threadEventArguments = new ThreadEventArguments();
		threadEventArguments.setReason(ThreadEventArgumentsReason.EXITED);
		threadEventArguments.setThreadId(camelThread.getId());
		client.thread(threadEventArguments);
	}

	public Set<CamelThread> getCamelThreads() {
		return camelThreads;
	}

	public void updateBreakpointsWithSources(CamelBreakpoint breakpoint) {
		this.camelBreakpointsWithSources.put(breakpoint.getNodeId(), breakpoint);
	}

	public void removeBreakpoint(String previouslySetBreakpointId) {
		backlogDebugger.removeBreakpoint(previouslySetBreakpointId);
		this.camelBreakpointsWithSources.remove(previouslySetBreakpointId);
	}

	public void resume(CamelThread camelThread) {
		backlogDebugger.resumeBreakpoint(camelThread.getBreakPointId());
		notifiedSuspendedBreakpointIds.remove(camelThread.getBreakPointId());
		camelThreads.remove(camelThread);
		sendThreadExitEvent(camelThread);
	}

	public void next(CamelThread camelThread) {
		isStepping = true;
		String breakPointId = camelThread.getBreakPointId();
		if(isLastInroute(breakPointId)) {
			camelThreads.remove(camelThread);
			sendThreadExitEvent(camelThread);
		}
		backlogDebugger.stepBreakpoint(breakPointId);
		notifiedSuspendedBreakpointIds.remove(breakPointId);
		isStepping = false;
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

}
