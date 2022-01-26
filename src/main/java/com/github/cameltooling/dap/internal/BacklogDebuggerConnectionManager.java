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

import com.github.cameltooling.dap.internal.model.CamelBreakpoint;
import com.github.cameltooling.dap.internal.model.CamelThread;
import com.github.cameltooling.dap.internal.types.EventMessage;
import com.github.cameltooling.dap.internal.types.UnmarshallerEventMessage;
import com.sun.tools.attach.VirtualMachine;

public class BacklogDebuggerConnectionManager {

	private static final String OBJECTNAME_BACKLOGDEBUGGER = "org.apache.camel:context=*,type=tracer,name=BacklogDebugger";
	private static final String OBJECTNAME_CAMELCONTEXT = "org.apache.camel:context=*,type=context,name=*";
	private static final String DEFAULT_JMX_URI = "service:jmx:rmi:///jndi/rmi://localhost:1099/jmxrmi";
	private static final Logger LOGGER = LoggerFactory.getLogger(BacklogDebuggerConnectionManager.class);

	public static final String ATTACH_PARAM_PID = "attach_pid";

	private JMXConnector jmxConnector;
	private MBeanServerConnection mbeanConnection;
	private ManagedBacklogDebuggerMBean backlogDebugger;
	private Document routesDOMDocument;
	private IDebugProtocolClient client;
	private Set<String> notifiedSuspendedBreakpointIds = new HashSet<>();
	private Set<CamelThread> camelThreads = new HashSet<>();
	int threadIdCounter = 1;
	private Map<String, CamelBreakpoint> camelBreakpointsWithSources = new HashMap<>();

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

	public void attach(Map<String, Object> args, IDebugProtocolClient client) {
		this.client = client;
		try {
			String jmxAddress = DEFAULT_JMX_URI;
			Object pid = args.get(ATTACH_PARAM_PID);
			if (pid != null) {
				jmxAddress = getLocalJMXUrl((String) pid);
			}
			LOGGER.error("Will use JMX adress: {}", jmxAddress);
			JMXServiceURL jmxUrl = new JMXServiceURL(jmxAddress);
			jmxConnector = JMXConnectorFactory.connect(jmxUrl);
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
			} else {
				LOGGER.warn("No BacklogDebugger found on connection with {}", jmxAddress);
			}
		} catch (Exception e) {
			LOGGER.error("Error trying to attach", e);
		}
	}

	private void checkSuspendedBreakpoints() {
		while(backlogDebugger != null && backlogDebugger.isEnabled()) {
			Set<String> suspendedBreakpointNodeIds = backlogDebugger.suspendedBreakpointNodeIds();
			// TODO: ensure list is cleaned at a time
			for (String nodeId : suspendedBreakpointNodeIds) {
				if (!notifiedSuspendedBreakpointIds.contains(nodeId)) {
					StoppedEventArguments stoppedEventArgs = new StoppedEventArguments();
					stoppedEventArgs.setReason(StoppedEventArgumentsReason.BREAKPOINT);
					stoppedEventArgs.setThreadId(threadIdCounter);
					String xml = backlogDebugger.dumpTracedMessagesAsXml(nodeId, true);
					EventMessage eventMessage = new UnmarshallerEventMessage().getUnmarshalledEventMessage(xml);
					Optional<CamelThread> thread = camelThreads.stream().filter(camelThread -> camelThread.getExchangeId().equals(eventMessage.getExchangeId())).findAny();
					if(thread.isEmpty()) {
						camelThreads.add(new CamelThread(threadIdCounter, nodeId, eventMessage, camelBreakpointsWithSources.get(nodeId)));
						ThreadEventArguments threadEventArguments = new ThreadEventArguments();
						threadEventArguments.setReason(ThreadEventArgumentsReason.STARTED);
						threadEventArguments.setThreadId(threadIdCounter);
						client.thread(threadEventArguments);
						threadIdCounter++;
					} else {
						thread.get().update(nodeId, eventMessage);
					}
					notifiedSuspendedBreakpointIds.add(nodeId);
					client.stopped(stoppedEventArgs);
				}
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
			ThreadEventArguments threadEventArguments = new ThreadEventArguments();
			threadEventArguments.setReason(ThreadEventArgumentsReason.EXITED);
			threadEventArguments.setThreadId(camelThread.getId());
			client.thread(threadEventArguments);
		}
		backlogDebugger.resumeAll();
		camelThreads.clear();
		notifiedSuspendedBreakpointIds.clear();
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

}
