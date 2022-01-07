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

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.eclipse.lsp4j.debug.TerminateArguments;
import org.eclipse.lsp4j.debug.ThreadsResponse;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class CamelDebugAdapterServer implements IDebugProtocolServer {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CamelDebugAdapterServer.class);

	private IDebugProtocolClient client;
	private BacklogDebuggerConnectionManager connectionManager = new BacklogDebuggerConnectionManager();

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
		return CompletableFuture.completedFuture(null);
	}
	
	@Override
	public CompletableFuture<Void> attach(Map<String, Object> args) {
		connectionManager.attach(args);
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
		return CompletableFuture.completedFuture(new ThreadsResponse());
	}
	
	@Override
	public CompletableFuture<Void> terminate(TerminateArguments args) {
		getConnectionManager().terminate();
		return CompletableFuture.completedFuture(null);
	}

	public BacklogDebuggerConnectionManager getConnectionManager() {
		return connectionManager;
	}

}
