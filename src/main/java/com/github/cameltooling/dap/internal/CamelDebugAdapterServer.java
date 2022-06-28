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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.camel.api.management.mbean.ManagedBacklogDebuggerMBean;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.ContinueResponse;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.NextArguments;
import org.eclipse.lsp4j.debug.OutputEventArguments;
import org.eclipse.lsp4j.debug.OutputEventArgumentsCategory;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.ScopesResponse;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.eclipse.lsp4j.debug.SetVariableArguments;
import org.eclipse.lsp4j.debug.SetVariableResponse;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StackTraceArguments;
import org.eclipse.lsp4j.debug.StackTraceResponse;
import org.eclipse.lsp4j.debug.TerminateArguments;
import org.eclipse.lsp4j.debug.ThreadsResponse;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.eclipse.lsp4j.debug.VariablesResponse;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.github.cameltooling.dap.internal.model.CamelBreakpoint;
import com.github.cameltooling.dap.internal.model.CamelScope;
import com.github.cameltooling.dap.internal.model.CamelStackFrame;
import com.github.cameltooling.dap.internal.model.CamelThread;
import com.github.cameltooling.dap.internal.telemetry.TelemetryEvent;

public class CamelDebugAdapterServer implements IDebugProtocolServer {
	
	private static final String CAMEL_LANGUAGE_SIMPLE = "simple";

	private static final Logger LOGGER = LoggerFactory.getLogger(CamelDebugAdapterServer.class);

	private volatile IDebugProtocolClient client;
	private final BacklogDebuggerConnectionManager connectionManager = new BacklogDebuggerConnectionManager();

	private final Map<String, Set<String>> sourceToBreakpointIds = new ConcurrentHashMap<>();

	public void connect(IDebugProtocolClient clientProxy) {
		this.client = clientProxy;
	}
	
	@Override
	public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
		Capabilities capabilities = new Capabilities();
		capabilities.setSupportsSetVariable(Boolean.TRUE);
		capabilities.setSupportsConditionalBreakpoints(Boolean.TRUE);
		capabilities.setSupportsConfigurationDoneRequest(Boolean.TRUE);
		return CompletableFuture.completedFuture(capabilities);
	}
	
	@Override
	public CompletableFuture<Void> attach(Map<String, Object> args) {
		IDebugProtocolClient protocolClient = client;
		boolean attached = connectionManager.attach(args, protocolClient);
		if (attached) {
			protocolClient.initialized();
		}
		OutputEventArguments telemetryEvent = new OutputEventArguments();
		telemetryEvent.setCategory(OutputEventArgumentsCategory.TELEMETRY);
		telemetryEvent.setOutput("camel.dap.attach");
		telemetryEvent.setData(new TelemetryEvent("camel.dap.attach", Collections.singletonMap("success", attached)));
		protocolClient.output(telemetryEvent);
		return CompletableFuture.completedFuture(null);
	}
	
	@Override
	public CompletableFuture<SetBreakpointsResponse> setBreakpoints(SetBreakpointsArguments setBreakpointsArguments) {
		SetBreakpointsResponse response = new SetBreakpointsResponse();
		Source source = setBreakpointsArguments.getSource();
		SourceBreakpoint[] sourceBreakpoints = setBreakpointsArguments.getBreakpoints();
		Breakpoint[] breakpoints = new Breakpoint[sourceBreakpoints.length];
		Set<String> breakpointIds = new HashSet<>();
		for (int i = 0; i< sourceBreakpoints.length; i++) {
			SourceBreakpoint sourceBreakpoint = sourceBreakpoints[i];
			int line = sourceBreakpoint.getLine();
			CamelBreakpoint breakpoint = new CamelBreakpoint(source, line);
			breakpoint.setSource(source);
			breakpoint.setLine(line);
			breakpoint.setMessage("the breakpoint "+ i);
			breakpoints[i] = breakpoint;
			Document routesDOMDocument = connectionManager.getRoutesDOMDocument();
			if (routesDOMDocument != null) {
				String path = "//*[@sourceLineNumber='" + line + "']";
				//TODO: take care of sourceLocation and not only line number
				// "//*[@sourceLocation='" + sourceLocation + "' and @sourceLineNumber='" + line + "']";

				try {
					XPath xPath = XPathFactory.newInstance().newXPath();
					Node breakpointTagFromContext = (Node) xPath.evaluate(path, routesDOMDocument, XPathConstants.NODE);
					if (breakpointTagFromContext != null) {
						String nodeId = breakpointTagFromContext.getAttributes().getNamedItem("id").getTextContent();
						breakpoint.setNodeId(nodeId);
						connectionManager.updateBreakpointsWithSources(breakpoint);
						breakpointIds.add(nodeId);
						if (sourceBreakpoint.getCondition() != null) {
							connectionManager.getBacklogDebugger().addConditionalBreakpoint(nodeId, CAMEL_LANGUAGE_SIMPLE, sourceBreakpoint.getCondition());
						} else {
							connectionManager.getBacklogDebugger().addBreakpoint(nodeId);
						}
						breakpoint.setVerified(true);
					}
				} catch (Exception e) {
					LOGGER.warn("Cannot find related id for "+ source.getPath() + "l." + line, e);
				}
			} else {
				LOGGER.warn("No active routes find in Camel context. Consequently, cannot set breakpoint for {} l.{}", source.getPath(), line);
			}
		}
		removeOldBreakpoints(source, breakpointIds);
		sourceToBreakpointIds.put(source.getPath(), breakpointIds);
		response.setBreakpoints(breakpoints);
		return CompletableFuture.completedFuture(response);
	}

	private void removeOldBreakpoints(Source source, Set<String> breakpointIds) {
		Set<String> previouslySetBreakpointIds = sourceToBreakpointIds.getOrDefault(source.getPath(), Collections.emptySet());
		for (String previouslySetBreakpointId : previouslySetBreakpointIds) {
			if(!breakpointIds.contains(previouslySetBreakpointId)) {
				connectionManager.removeBreakpoint(previouslySetBreakpointId);
			}
		}
	}
	
	@Override
	public CompletableFuture<ThreadsResponse> threads() {
		ThreadsResponse value = new ThreadsResponse();
		Set<CamelThread> threads = connectionManager.getCamelThreads();
		value.setThreads(threads.toArray(new CamelThread[0]));
		return CompletableFuture.completedFuture(value);
	}
	
	@Override
	public CompletableFuture<StackTraceResponse> stackTrace(StackTraceArguments args) {
		StackTraceResponse response = new StackTraceResponse();
		Set<StackFrame> stackFrames = new HashSet<>();
		Set<CamelThread> camelThreads = connectionManager.getCamelThreads();
		Optional<CamelThread> camelThreadOptional = camelThreads.stream().filter(camelThread -> camelThread.getId() == args.getThreadId()).findAny();
		if (camelThreadOptional.isPresent()) {
			CamelThread camelThread = camelThreadOptional.get();
			stackFrames.add(camelThread.getStackFrame());
		}
		response.setStackFrames(stackFrames.toArray(new StackFrame[0]) );
		return CompletableFuture.completedFuture(response);
	}
	
	@Override
	public CompletableFuture<ScopesResponse> scopes(ScopesArguments args) {
		ScopesResponse response = new ScopesResponse();
		Optional<CamelStackFrame> camelStackFrameOptional = connectionManager.getCamelThreads().stream()
				.map(CamelThread::getStackFrame)
				.filter(stackFrame -> args.getFrameId() == stackFrame.getId())
				.findAny();
		Set<CamelScope> scopes = new HashSet<>();
		if (camelStackFrameOptional.isPresent()) {
			scopes = camelStackFrameOptional.get().createScopes();
		}
		response.setScopes(scopes.toArray(new Scope[0]));
		return CompletableFuture.completedFuture(response);
	}

	@Override
	public CompletableFuture<VariablesResponse> variables(VariablesArguments args) {
		int variablesReference = args.getVariablesReference();
		VariablesResponse response = new VariablesResponse();
		Set<Variable> variables = new HashSet<>();
		
		ManagedBacklogDebuggerMBean debugger = connectionManager.getBacklogDebugger();
		for (CamelThread camelThread : connectionManager.getCamelThreads()) {
			variables.addAll(camelThread.createVariables(variablesReference, debugger));
		}
		response.setVariables(variables.toArray(new Variable[0]));
		return CompletableFuture.completedFuture(response);
	}

	@Override
	public CompletableFuture<ContinueResponse> continue_(ContinueArguments args) {
		ContinueResponse response = new ContinueResponse();
		int threadId = args.getThreadId();
		if (threadId != 0) {
			response.setAllThreadsContinued(Boolean.FALSE);
			Optional<CamelThread> findAny = findThread(threadId);
			if (findAny.isPresent()) {
				CamelThread camelThread = findAny.get();
				connectionManager.resume(camelThread);
			}
		} else {
			connectionManager.resumeAll();
			response.setAllThreadsContinued(Boolean.TRUE);
		}
		return CompletableFuture.completedFuture(response);
	}

	private Optional<CamelThread> findThread(int threadId) {
		return connectionManager.getCamelThreads().stream().filter(camelThread -> camelThread.getId() == threadId).findAny();
	}
	
	@Override
	public CompletableFuture<Void> next(NextArguments args) {
		int threadId = args.getThreadId();
		Optional<CamelThread> findAny = findThread(threadId);
		if (findAny.isPresent()) {
			CamelThread camelThread = findAny.get();
			connectionManager.next(camelThread);
		}
		return CompletableFuture.completedFuture(null);
	}
	
	@Override
	public CompletableFuture<Void> terminate(TerminateArguments args) {
		connectionManager.terminate();
		return CompletableFuture.completedFuture(null);
	}
	
	@Override
	public CompletableFuture<Void> disconnect(DisconnectArguments args) {
		connectionManager.terminate();
		return CompletableFuture.completedFuture(null);
	}
	
	@Override
	public CompletableFuture<SetVariableResponse> setVariable(SetVariableArguments args) {
		for(CamelThread thread : connectionManager.getCamelThreads()) {
			for(CamelScope scope : thread.getStackFrame().getScopes()) {
				try {
					SetVariableResponse response = scope.setVariableIfInScope(args, connectionManager.getBacklogDebugger());
					if (response != null) {
						return CompletableFuture.completedFuture(response);
					}
				} catch (Exception ex) {
					OutputEventArguments eventToAlertUser = new OutputEventArguments();
					eventToAlertUser.setCategory(OutputEventArgumentsCategory.STDERR);
					eventToAlertUser.setOutput("Cannot set variable " + args.getName() + ": "+ ex.getClass().getCanonicalName() + ": " + ex.getMessage());
					client.output(eventToAlertUser);
					throw ex;
				}
			}
		}
		return CompletableFuture.completedFuture(null);
	}

	public BacklogDebuggerConnectionManager getConnectionManager() {
		return connectionManager;
	}

	@Override
	public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
		// Resume potentially the message processing
		return CompletableFuture.runAsync(
			() -> {
				try {
					connectionManager.getBacklogDebugger().attach();
				} catch (Exception e) {
					LOGGER.warn("Could not attach the debugger: {}", e.getMessage());
				}
			}
		);
	}
}
