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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.eclipse.lsp4j.debug.Thread;
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

import com.github.cameltooling.dap.internal.types.EventMessage;
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
	private Map<String, Source> breakpointIdToSource = new HashMap<>();
	private Map<String, Integer> breakpointIdToLine = new HashMap<>();



	public void connect(IDebugProtocolClient clientProxy) {
		this.client = clientProxy;
	}
	
	@Override
	public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
		client.initialized();
		return CompletableFuture.completedFuture(new Capabilities());
	}
	
	@Override
	public CompletableFuture<Void> launch(Map<String, Object> args) {
		// TODO: built-in Debug Configuration launch in Eclipse only allows to launch and not attach. So here is a trick.
		return attach(args);
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
		for (int i = 0; i< sourceBreakpoints.length; i++) {
			SourceBreakpoint sourceBreakpoint = sourceBreakpoints[i];
			Breakpoint breakpoint = new Breakpoint();
			breakpoint.setSource(source);
			int line = sourceBreakpoint.getLine();
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
					String breakpointId = breakpointTagFromContext.getAttributes().getNamedItem("id").getTextContent();
					breakpointIdToSource.put(breakpointId, source);
					breakpointIdToLine.put(breakpointId, line);
					connectionManager.getBacklogDebugger().addBreakpoint(breakpointId);
					breakpoint.setVerified(true);
				}
			} catch (Exception e) {
				LOGGER.warn("Cannot find related id", e);
			}
		}
		response.setBreakpoints(breakpoints);
		return CompletableFuture.completedFuture(response);
	}
	
	@Override
	public CompletableFuture<ThreadsResponse> threads() {
		ThreadsResponse value = new ThreadsResponse();
		Thread[] threads = new Thread[1];
		Thread thread = new Thread();
		thread.setId(0);
		thread.setName("Camel context");
		threads[0] = thread;
		value.setThreads(threads);
		return CompletableFuture.completedFuture(value);
	}
	
	@Override
	public CompletableFuture<StackTraceResponse> stackTrace(StackTraceArguments args) {
		StackTraceResponse response = new StackTraceResponse();
		Set<StackFrame> stackFrames = new HashSet<>();
		
		Set<String> breakpointIds = connectionManager.getNotifiedSuspendedBreakpointIds();
		for (String breakpointId : breakpointIds) {
			StackFrame stackFrame = new StackFrame();
			// TODO: compute a better name
			stackFrame.setName(breakpointId);
			// TODO: provide a better hashcode for stackframe containing the camelcontext too
			int frameId = breakpointId.hashCode();
			stackFrame.setId(frameId);
			stackFrame.setSource(breakpointIdToSource.get(breakpointId));
			stackFrame.setLine(breakpointIdToLine.get(breakpointId));
			frameIdToBreakpointId.put(frameId, breakpointId);
			stackFrames.add(stackFrame);
		}
		response.setStackFrames(stackFrames.toArray(new StackFrame[stackFrames.size()]) );
		return CompletableFuture.completedFuture(response);
	}
	
	@Override
	public CompletableFuture<ScopesResponse> scopes(ScopesArguments args) {
		ScopesResponse response = new ScopesResponse();
		String breakpointId = frameIdToBreakpointId.get(args.getFrameId());
		Set<Scope> scopes = new HashSet<>();
		
		scopes.add(createScope("Debugger", breakpointId, debuggerVariableReferenceToBreakpointId));
		scopes.add(createScope("Endpoint", breakpointId, endpointVariableReferenceToBreakpointId));
		scopes.add(createScope("Processor", breakpointId, processorVariableReferenceToBreakpointId));
		scopes.add(createScope("Exchange", breakpointId, exchangeVariableReferenceToBreakpointId));
		scopes.add(createScope("Message", breakpointId, messagevariableReferenceToBreakpointId));
		
		response.setScopes(scopes.toArray(new Scope[scopes.size()]));
		return CompletableFuture.completedFuture(response);
	}

	private Scope createScope(String name, String breakpointId, Map<Integer, String> variableReferences) {
		Scope scope = new Scope();
		scope.setName(name);
		int variableRefId = ("@"+ name + "@" + breakpointId).hashCode();
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
				int headerVarRefId = (variablesReference+"@headers@").hashCode();
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
			//variables.add(createVariable("Route Id", connectionManager.getBacklogDebugger().getRouteId(breakpointId)));
			variables.add(createVariable("Camel Id", debugger.getCamelId()));
			//variables.add(createVariable("Completed Exchange", debugger.getCompletedExchanges(breakpointId)));
		}
		
		breakpointId = debuggerVariableReferenceToBreakpointId.get(variablesReference);
		if(breakpointId != null) {
			variables.add(createVariable("Logging level", connectionManager.getBacklogDebugger().getLoggingLevel()));
			variables.add(createVariable("Max chars for body", Integer.toString(connectionManager.getBacklogDebugger().getBodyMaxChars())));
			variables.add(createVariable("Debug counter", Long.toString(connectionManager.getBacklogDebugger().getDebugCounter())));
			variables.add(createVariable("Fallback timeout", Long.toString(connectionManager.getBacklogDebugger().getFallbackTimeout())));
			variables.add(createVariable("Body include files", Boolean.toString(connectionManager.getBacklogDebugger().isBodyIncludeFiles())));
			variables.add(createVariable("Body include streams", Boolean.toString(connectionManager.getBacklogDebugger().isBodyIncludeStreams())));
		}
		
		breakpointId = exchangeVariableReferenceToBreakpointId.get(variablesReference);
		if(breakpointId != null) {
			String xml = debugger.dumpTracedMessagesAsXml(breakpointId, true);
			EventMessage eventMessage = new UnmarshallerEventMessage().getUnmarshalledEventMessage(xml);
			if(eventMessage != null) {
				variables.add(createVariable("ID", eventMessage.getExchangeId()));
				variables.add(createVariable("To node", eventMessage.getToNode()));
				variables.add(createVariable("Route ID", eventMessage.getRouteId()));
				//TODO: exchange properties
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
		connectionManager.getBacklogDebugger().resumeAll();
		// TODO: clear cache of suspended breakpointid
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
