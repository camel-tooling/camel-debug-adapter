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
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

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

	private IDebugProtocolClient client;
	private final BacklogDebuggerConnectionManager connectionManager = new BacklogDebuggerConnectionManager();

	private final Map<String, Set<String>> sourceToBreakpointIds = new ConcurrentHashMap<>();

	public void connect(IDebugProtocolClient clientProxy) {
		this.client = clientProxy;
	}
	
	@Override
	public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
		return supplyAsync(
			() -> {
				Capabilities capabilities = new Capabilities();
				capabilities.setSupportsSetVariable(Boolean.TRUE);
				capabilities.setSupportsConditionalBreakpoints(Boolean.TRUE);
				capabilities.setSupportsConfigurationDoneRequest(Boolean.TRUE);
				return capabilities;
			}
		);
	}
	
	@Override
	public CompletableFuture<Void> attach(Map<String, Object> args) {
		return runAsync(
			() -> {
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
			}
		);
	}
	
	@Override
	public CompletableFuture<SetBreakpointsResponse> setBreakpoints(SetBreakpointsArguments setBreakpointsArguments) {
		return supplyAsync(() -> setBreakpointsSync(setBreakpointsArguments));
	}

	private SetBreakpointsResponse setBreakpointsSync(SetBreakpointsArguments setBreakpointsArguments) {
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
		SetBreakpointsResponse response = new SetBreakpointsResponse();
		response.setBreakpoints(breakpoints);
		return response;
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
		return supplyAsync(
			() -> {
				Set<CamelThread> threads = connectionManager.getCamelThreads();
				ThreadsResponse value = new ThreadsResponse();
				value.setThreads(threads.toArray(new CamelThread[0]));
				return value;
			}
		);
	}
	
	@Override
	public CompletableFuture<StackTraceResponse> stackTrace(StackTraceArguments args) {
		return supplyAsync(
			() -> {
				Set<CamelThread> camelThreads = connectionManager.getCamelThreads();
				Optional<CamelThread> camelThreadOptional = camelThreads.stream().filter(camelThread -> camelThread.getId() == args.getThreadId()).findAny();
				Set<StackFrame> stackFrames = new HashSet<>();
				if (camelThreadOptional.isPresent()) {
					CamelThread camelThread = camelThreadOptional.get();
					stackFrames.add(camelThread.getStackFrame());
				}
				StackTraceResponse response = new StackTraceResponse();
				response.setStackFrames(stackFrames.toArray(new StackFrame[0]));
				return response;
			}
		);
	}
	
	@Override
	public CompletableFuture<ScopesResponse> scopes(ScopesArguments args) {
		return supplyAsync(
			() -> {
				Optional<CamelStackFrame> camelStackFrameOptional = connectionManager.getCamelThreads().stream()
					.map(CamelThread::getStackFrame)
					.filter(stackFrame -> args.getFrameId() == stackFrame.getId())
					.findAny();
				Set<CamelScope> scopes = new HashSet<>();
				if (camelStackFrameOptional.isPresent()) {
					scopes = camelStackFrameOptional.get().createScopes();
				}
				ScopesResponse response = new ScopesResponse();
				response.setScopes(scopes.toArray(new Scope[0]));
				return response;
			}
		);
	}

	@Override
	public CompletableFuture<VariablesResponse> variables(VariablesArguments args) {
		return supplyAsync(
			() -> {
				Set<Variable> variables = new HashSet<>();
				ManagedBacklogDebuggerMBean debugger = connectionManager.getBacklogDebugger();
				for (CamelThread camelThread : connectionManager.getCamelThreads()) {
					variables.addAll(camelThread.createVariables(args.getVariablesReference(), debugger));
				}
				VariablesResponse response = new VariablesResponse();
				response.setVariables(variables.toArray(new Variable[0]));
				return response;
			}
		);
	}

	@Override
	public CompletableFuture<ContinueResponse> continue_(ContinueArguments args) {
		return supplyAsync(
			() -> {
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
				return response;
			}
		);
	}

	private Optional<CamelThread> findThread(int threadId) {
		return connectionManager.getCamelThreads().stream().filter(camelThread -> camelThread.getId() == threadId).findAny();
	}
	
	@Override
	public CompletableFuture<Void> next(NextArguments args) {
		return runAsync(
			() -> {
				Optional<CamelThread> findAny = findThread(args.getThreadId());
				if (findAny.isPresent()) {
					CamelThread camelThread = findAny.get();
					connectionManager.next(camelThread);
				}
			}
		);
	}
	
	@Override
	public CompletableFuture<Void> terminate(TerminateArguments args) {
		return runAsync(connectionManager::terminate);
	}
	
	@Override
	public CompletableFuture<Void> disconnect(DisconnectArguments args) {
		return runAsync(connectionManager::terminate);
	}
	
	@Override
	public CompletableFuture<SetVariableResponse> setVariable(SetVariableArguments args) {
		return supplyAsync(
			() -> {
				for(CamelThread thread : connectionManager.getCamelThreads()) {
					for(CamelScope scope : thread.getStackFrame().getScopes()) {
						try {
							SetVariableResponse response = scope.setVariableIfInScope(args, connectionManager.getBacklogDebugger());
							if (response != null) {
								return response;
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
				return null;
			}
		);
	}

	public BacklogDebuggerConnectionManager getConnectionManager() {
		return connectionManager;
	}

	@Override
	public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
		// Resume potentially the message processing
		return runAsync(
			() -> {
				try {
					connectionManager.getBacklogDebugger().attach();
				} catch (Exception e) {
					LOGGER.warn("Could not attach the debugger: {}", e.getMessage());
				}
			}
		);
	}

	/**
	 * Executes asynchronously the given task ensuring that the context class loader is properly set to ensure that
	 * the classes from third party libraries are found.
	 *
	 * @param runnable the task to execute
	 * @return the new CompletableFuture
	 */
	private static CompletableFuture<Void> runAsync(Runnable runnable) {
		final ClassLoader callerCCL = Thread.currentThread().getContextClassLoader();
		return CompletableFuture.runAsync(
			() -> {
				final ClassLoader currentCCL = Thread.currentThread().getContextClassLoader();
				try {
					Thread.currentThread().setContextClassLoader(callerCCL);
					runnable.run();
				} finally {
					Thread.currentThread().setContextClassLoader(currentCCL);
				}
			}
		);
	}

	/**
	 * Calls asynchronously the given supplier ensuring that the context class loader is properly set to ensure that
	 * the classes from third party libraries are found.
	 *
	 * @param supplier the supplier to call
	 * @return the new CompletableFuture
	 * @param <U> the type of the result
	 */
	private static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
		final ClassLoader callerCCL = Thread.currentThread().getContextClassLoader();
		return CompletableFuture.supplyAsync(
			() -> {
				final ClassLoader currentCCL = Thread.currentThread().getContextClassLoader();
				try {
					Thread.currentThread().setContextClassLoader(callerCCL);
					return supplier.get();
				} finally {
					Thread.currentThread().setContextClassLoader(currentCCL);
				}
			}
		);
	}
}
