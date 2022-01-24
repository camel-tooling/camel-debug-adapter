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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.camel.api.management.mbean.ManagedBacklogDebuggerMBean;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.ContinueResponse;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.ScopesResponse;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
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
import com.github.cameltooling.dap.internal.model.CamelThread;
import com.github.cameltooling.dap.internal.types.EventMessage;
import com.github.cameltooling.dap.internal.types.ExchangeProperty;
import com.github.cameltooling.dap.internal.types.Header;
import com.github.cameltooling.dap.internal.types.UnmarshallerEventMessage;

public class CamelDebugAdapterServer implements IDebugProtocolServer {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CamelDebugAdapterServer.class);

	private IDebugProtocolClient client;
	private BacklogDebuggerConnectionManager connectionManager = new BacklogDebuggerConnectionManager();
	private Map<Integer, String> endpointVariableReferenceToBreakpointId = new HashMap<>();
	private Map<Integer, String> frameIdToBreakpointId = new HashMap<>();
	private Map<Integer, String> processorVariableReferenceToBreakpointId = new HashMap<>();
	private Map<Integer, String> messagevariableReferenceToBreakpointId = new HashMap<>();
	private Map<Integer, String> debuggerVariableReferenceToBreakpointId = new HashMap<>();
	private Map<Integer, String> exchangeVariableReferenceToBreakpointId = new HashMap<>();
	private Map<Integer, List<Header>> headersVariableReferenceToHeaders = new HashMap<>();
	private Map<Integer, List<ExchangeProperty>> variableReferenceToExchangeProperties = new HashMap<>();
	private Map<String, Set<String>> sourceToBreakpointIds = new HashMap<>();

	public void connect(IDebugProtocolClient clientProxy) {
		this.client = clientProxy;
	}
	
	@Override
	public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
		client.initialized();
		return CompletableFuture.completedFuture(new Capabilities());
	}
	
	@Override
	public CompletableFuture<Void> attach(Map<String, Object> args) {
		connectionManager.attach(args, client);
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
					connectionManager.getBacklogDebugger().addBreakpoint(nodeId);
					breakpoint.setVerified(true);
				}
			} catch (Exception e) {
				LOGGER.warn("Cannot find related id", e);
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
		value.setThreads(threads.toArray(new CamelThread[threads.size()]));
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
		response.setStackFrames(stackFrames.toArray(new StackFrame[stackFrames.size()]) );
		return CompletableFuture.completedFuture(response);
	}
	
	@Override
	public CompletableFuture<ScopesResponse> scopes(ScopesArguments args) {
		ScopesResponse response = new ScopesResponse();
		Optional<CamelThread> camelThreadOptional = connectionManager.getCamelThreads().stream()
				.filter(camelThread -> args.getFrameId() == camelThread.getStackFrame().getId())
				.findAny();
		Set<Scope> scopes = new HashSet<>();
		if (camelThreadOptional.isPresent()) {
			String breakpointId = camelThreadOptional.get().getBreakPointId();
			
			scopes.add(createScope("Debugger", breakpointId, debuggerVariableReferenceToBreakpointId));
			scopes.add(createScope("Endpoint", breakpointId, endpointVariableReferenceToBreakpointId));
			scopes.add(createScope("Processor", breakpointId, processorVariableReferenceToBreakpointId));
			scopes.add(createScope("Exchange", breakpointId, exchangeVariableReferenceToBreakpointId));
			scopes.add(createScope("Message", breakpointId, messagevariableReferenceToBreakpointId));
		}
		response.setScopes(scopes.toArray(new Scope[scopes.size()]));
		return CompletableFuture.completedFuture(response);
	}

	private Scope createScope(String name, String breakpointId, Map<Integer, String> variableReferences) {
		Scope scope = new Scope();
		scope.setName(name);
		int variableRefId = IdUtils.getPositiveIntFromHashCode(("@"+ name + "@" + breakpointId).hashCode());
		scope.setVariablesReference(variableRefId);
		variableReferences.put(variableRefId, breakpointId);
		return scope;
	}
	
	@Override
	public CompletableFuture<VariablesResponse> variables(VariablesArguments args) {
		int variablesReference = args.getVariablesReference();
		VariablesResponse response = new VariablesResponse();
		Set<Variable> variables = new HashSet<>();

		ManagedBacklogDebuggerMBean debugger = connectionManager.getBacklogDebugger();
		
		String breakpointId = endpointVariableReferenceToBreakpointId.get(variablesReference);
		if (breakpointId != null) {
			//TODO: retrieve name instead of ID
			variables.add(createVariable("Name", breakpointId));
		}
		breakpointId = messagevariableReferenceToBreakpointId.get(variablesReference);
		if (breakpointId != null) {
			String xml = debugger.dumpTracedMessagesAsXml(breakpointId, true);
			EventMessage eventMessage = new UnmarshallerEventMessage().getUnmarshalledEventMessage(xml);
			if(eventMessage != null) {
				variables.add(createVariable("Exchange ID", eventMessage.getExchangeId()));
				variables.add(createVariable("UID", Long.toString(eventMessage.getUid())));
				variables.add(createVariable("Body", eventMessage.getMessage().getBody()));
				Variable headersVariable = new Variable();
				headersVariable.setName("Headers");
				int headerVarRefId = IdUtils.getPositiveIntFromHashCode((variablesReference+"@headers@").hashCode());
				headersVariableReferenceToHeaders.put(headerVarRefId, eventMessage.getMessage().getHeaders());
				headersVariable.setVariablesReference(headerVarRefId);
				variables.add(headersVariable);
			}
		}
		
		List<Header> headers = headersVariableReferenceToHeaders.get(variablesReference);
		if(headers != null) {
			for (Header header : headers) {
				variables.add(createVariable(header.getKey(), header.getValue()));
			}
		}
		
		breakpointId = processorVariableReferenceToBreakpointId.get(variablesReference);
		if (breakpointId != null){
			variables.add(createVariable("Processor Id", breakpointId));
			//TODO: variables.add(createVariable("Route Id", connectionManager.getBacklogDebugger().getRouteId(breakpointId)));
			variables.add(createVariable("Camel Id", debugger.getCamelId()));
			//TODO: variables.add(createVariable("Completed Exchange", debugger.getCompletedExchanges(breakpointId)));
		}
		
		breakpointId = debuggerVariableReferenceToBreakpointId.get(variablesReference);
		if(breakpointId != null) {
			variables.add(createVariable("Logging level", debugger.getLoggingLevel()));
			variables.add(createVariable("Max chars for body", Integer.toString(debugger.getBodyMaxChars())));
			variables.add(createVariable("Debug counter", Long.toString(debugger.getDebugCounter())));
			variables.add(createVariable("Fallback timeout", Long.toString(debugger.getFallbackTimeout())));
			variables.add(createVariable("Body include files", Boolean.toString(debugger.isBodyIncludeFiles())));
			variables.add(createVariable("Body include streams", Boolean.toString(debugger.isBodyIncludeStreams())));
		}
		
		breakpointId = exchangeVariableReferenceToBreakpointId.get(variablesReference);
		if(breakpointId != null) {
			String xml = debugger.dumpTracedMessagesAsXml(breakpointId, true);
			EventMessage eventMessage = new UnmarshallerEventMessage().getUnmarshalledEventMessage(xml);
			if(eventMessage != null) {
				variables.add(createVariable("ID", eventMessage.getExchangeId()));
				variables.add(createVariable("To node", eventMessage.getToNode()));
				variables.add(createVariable("Route ID", eventMessage.getRouteId()));
				Variable exchangeVariable = new Variable();
				exchangeVariable.setName("Properties");
				int exchangeVarRefId = IdUtils.getPositiveIntFromHashCode((variablesReference+"@exchange@").hashCode());
				variableReferenceToExchangeProperties.put(exchangeVarRefId, eventMessage.getExchangeProperties());
				exchangeVariable.setVariablesReference(exchangeVarRefId);
				variables.add(exchangeVariable);
			}
		}
		
		List<ExchangeProperty> exchangeProperties = variableReferenceToExchangeProperties.get(variablesReference);
		if (exchangeProperties != null) {
			for (ExchangeProperty exchangeProperty : exchangeProperties) {
				variables.add(createVariable(exchangeProperty.getName(), exchangeProperty.getContent()));
			}
		}
		
		response.setVariables(variables.toArray(new Variable[variables.size()]));
		return CompletableFuture.completedFuture(response);
	}

	private Variable createVariable(String variableName, String variableValue) {
		Variable processorIdVariable = new Variable();
		processorIdVariable.setName(variableName);
		processorIdVariable.setValue(variableValue);
		return processorIdVariable;
	}
	
	@Override
	public CompletableFuture<ContinueResponse> continue_(ContinueArguments args) {
		debuggerVariableReferenceToBreakpointId.clear();
		endpointVariableReferenceToBreakpointId.clear();
		exchangeVariableReferenceToBreakpointId.clear();
		frameIdToBreakpointId.clear();
		headersVariableReferenceToHeaders.clear();
		messagevariableReferenceToBreakpointId.clear();
		processorVariableReferenceToBreakpointId.clear();
		variableReferenceToExchangeProperties.clear();
		connectionManager.resumeAll();
		return CompletableFuture.completedFuture(new ContinueResponse());
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

	public BacklogDebuggerConnectionManager getConnectionManager() {
		return connectionManager;
	}

}
