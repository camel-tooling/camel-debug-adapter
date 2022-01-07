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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.eclipse.lsp4j.debug.TerminateArguments;
import org.eclipse.lsp4j.debug.TerminatedEventArguments;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CamelDebugAdapterServerTest {
	
	private CamelDebugAdapterServer server;
	private DummyCamelDebugClient clientProxy;
	
	@AfterEach
	void tearDown() {
		if (server != null) {
			server.terminate(new TerminateArguments());
			server = null;
		}
		if (clientProxy != null) {
			clientProxy.terminated(new TerminatedEventArguments());
			clientProxy = null;
		}
	}

	@Test
	void testInitialize() throws InterruptedException, ExecutionException {
		CompletableFuture<Capabilities> initialization = initDebugger();
		assertThat(initialization.get()).isNotNull();
		assertThat(clientProxy.hasReceivedInitializedEvent()).isTrue();
		
	}
	
	@Test
	void testAttachToCamelWithPid() throws Exception {
		try (CamelContext context = new DefaultCamelContext()) {
			context.addRoutes(new RouteBuilder() {
			
				@Override
				public void configure() throws Exception {
					from("direct:test")
						.log("Log from test");
				}
			});
			context.start();
			assertThat(context.isStarted()).isTrue();
			initDebugger();
			server.attach(Collections.singletonMap(BacklogDebuggerConnectionManager.ATTACH_PARAM_PID, Long.toString(ProcessHandle.current().pid())));
			BacklogDebuggerConnectionManager connectionManager = server.getConnectionManager();
			assertThat(connectionManager.getMbeanConnection()).as("The MBeanConnection has not been established.").isNotNull();
			assertThat(connectionManager.getBacklogDebugger()).as("The BacklogDebugger has not been found.").isNotNull();
			assertThat(connectionManager.getBacklogDebugger().isEnabled()).isTrue();
			assertThat(connectionManager.getRoutesDOMDocument()).as("Routes instantiated.").isNotNull();
		}
	}
	
	@Test
	void testSetBreakpoint() throws Exception {
		try (CamelContext context = new DefaultCamelContext()) {
			context.addRoutes(new RouteBuilder() {
			
				@Override
				public void configure() throws Exception {
					from("direct:testSetBreakpoint")
						.log("Log from test"); // line number to use from here
				}
			});
			int lineNumberToPutBreakpoint = 98;
			context.start();
			assertThat(context.isStarted()).isTrue();
			initDebugger();
			server.attach(Collections.singletonMap(BacklogDebuggerConnectionManager.ATTACH_PARAM_PID, Long.toString(ProcessHandle.current().pid())));
			BacklogDebuggerConnectionManager connectionManager = server.getConnectionManager();
			assertThat(connectionManager.getMbeanConnection()).as("The MBeanConnection has not been established.").isNotNull();
			assertThat(connectionManager.getBacklogDebugger()).as("The BacklogDebugger has not been found.").isNotNull();
			SetBreakpointsArguments setBreakpointsArguments = createSetBreakpointArgument(lineNumberToPutBreakpoint);
			
			SetBreakpointsResponse response = server.setBreakpoints(setBreakpointsArguments).get();
			
			Breakpoint[] responseBreakpoints = response.getBreakpoints();
			assertThat(responseBreakpoints).hasSize(1);
			Breakpoint responseBreakpoint = responseBreakpoints[0];
			assertThat(responseBreakpoint.getLine()).isEqualTo(lineNumberToPutBreakpoint);
			assertThat(responseBreakpoint.isVerified()).isTrue();
			Set<String> breakpointsSetInCamel = connectionManager.getBacklogDebugger().breakpoints();
			assertThat(breakpointsSetInCamel).hasSize(1);
			
			//TODO: trigger the route and check that we have a suspended breakpoint
			
		}
	}

	private SetBreakpointsArguments createSetBreakpointArgument(int lineNumberToPutBreakpoint) {
		SetBreakpointsArguments setBreakpointsArguments = new SetBreakpointsArguments();
		Source source = new Source();
		String pathToItself = (new File("src/test/java/"+CamelDebugAdapterServerTest.class.getName()+".java")).getAbsolutePath();
		source.setPath(pathToItself);
		setBreakpointsArguments.setSource(source);
		SourceBreakpoint[] breakpoints = new SourceBreakpoint[1];
		SourceBreakpoint sourceBreakpoint = new SourceBreakpoint();
		sourceBreakpoint.setLine(lineNumberToPutBreakpoint);
		breakpoints[0] = sourceBreakpoint;
		setBreakpointsArguments.setBreakpoints(breakpoints);
		return setBreakpointsArguments;
	}
	
	private CompletableFuture<Capabilities> initDebugger() {
		server = new CamelDebugAdapterServer();
		clientProxy = new DummyCamelDebugClient();
		server.connect(clientProxy);
		return server.initialize(new InitializeRequestArguments());
	}

}
