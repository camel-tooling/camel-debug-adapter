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
import static org.awaitility.Awaitility.await;

import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultProducerTemplate;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.eclipse.lsp4j.debug.TerminateArguments;
import org.eclipse.lsp4j.debug.TerminatedEventArguments;
import org.eclipse.lsp4j.debug.Variable;
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
	
	/**
	 * Basic flow:
	 * - set breakpoint
	 * - start route
	 * - hit breakpoint
	 * - continue
	 */
	@Test
	void testBasicFlow() throws Exception {
		try (CamelContext context = new DefaultCamelContext()) {
			String routeId = "a-route-id";
			context.addRoutes(new RouteBuilder() {
			
				@Override
				public void configure() throws Exception {
					from("direct:testSetBreakpoint")
						.routeId(routeId)
						.setHeader("header1", constant("value of header 1"))
						.setHeader("header2", constant("value of header 2"))
						.setProperty("property1", constant("value of property 1"))
						.setProperty("property2", constant("value of property 2"))
						.log("Log from test"); // line number to use from here
				}
			});
			int lineNumberToPutBreakpoint = 118;
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

			DefaultProducerTemplate producerTemplate = DefaultProducerTemplate.newInstance(context, "direct:testSetBreakpoint");
			producerTemplate.start();
			String body = "a body for test";
			CompletableFuture<Object> asyncSendBody = producerTemplate.asyncSendBody("direct:testSetBreakpoint", body);
			
			await("Wait that breakpoint hit is notified")
				.atMost(Duration.ofSeconds(3))
				.until(() -> 
				{ 
					return clientProxy.getStoppedEventArguments().size() == 1;
				});
			StoppedEventArguments stoppedEventArgument = clientProxy.getStoppedEventArguments().get(0);
			assertThat(stoppedEventArgument.getThreadId()).isZero();
			assertThat(stoppedEventArgument.getReason()).isEqualTo(StoppedEventArgumentsReason.BREAKPOINT);
			
			assertThat(asyncSendBody.isDone()).isFalse();
			
			assertThat(clientProxy.getAllStacksAndVars()).hasSize(1);
			StackAndVarOnStopEvent stackAndData = clientProxy.getAllStacksAndVars().get(0);
			assertThat(stackAndData.getThreads()).hasSize(1);
			assertThat(stackAndData.getStackFrames()).hasSize(1);
			assertThat(stackAndData.getScopes()).hasSize(5);
			await("handling of stop event response is finished")
			 .atMost(Duration.ofSeconds(60))
			 .until(() -> {
				 return stackAndData.getVariables().size() == 23;
			 });
			List<Variable> variables = stackAndData.getVariables();
			assertThat(variables)
				.contains(
						createVariable("Body", body),
						createVariable("Logging level","INFO"),
						createVariable("Max chars for body", "131072"),
						createVariable("Fallback timeout", "300"),
						createVariable("Debug counter", "1"),
						createVariable("Body include files", "true"),
						createVariable("Body include streams", "false"),
						createVariable("To node", "log1"),
						createVariable("Route ID", routeId),
						createVariable("header1", "value of header 1"),
						createVariable("header2", "value of header 2"),
						createVariable("property1", "value of property 1"),
						createVariable("property2", "value of property 2"));		
			
			server.continue_(new ContinueArguments());
			
			await("Wait that route has resumed and is finished")
				.atMost(Duration.ofSeconds(3))
				.until(() -> {
					return asyncSendBody.isDone();
				});
			
			producerTemplate.stop();
		}
	}
	
	@Test
	void testResume() throws Exception {
		try (CamelContext context = new DefaultCamelContext()) {
			String routeId = "a-route-id";
			context.addRoutes(new RouteBuilder() {
			
				@Override
				public void configure() throws Exception {
					from("direct:testResume")
						.routeId(routeId)
						.log("Log from test");  // line number to use from here
				}
			});
			int lineNumberToPutBreakpoint = 207;
			context.start();
			assertThat(context.isStarted()).isTrue();
			initDebugger();
			server.attach(Collections.singletonMap(BacklogDebuggerConnectionManager.ATTACH_PARAM_PID, Long.toString(ProcessHandle.current().pid())));
			BacklogDebuggerConnectionManager connectionManager = server.getConnectionManager();
			assertThat(connectionManager.getMbeanConnection()).as("The MBeanConnection has not been established.").isNotNull();
			assertThat(connectionManager.getBacklogDebugger()).as("The BacklogDebugger has not been found.").isNotNull();
			SetBreakpointsArguments setBreakpointsArguments = createSetBreakpointArgument(lineNumberToPutBreakpoint);
			
			server.setBreakpoints(setBreakpointsArguments).get();
			
			DefaultProducerTemplate producerTemplate = DefaultProducerTemplate.newInstance(context, "direct:testResume");
			producerTemplate.start();
			
			CompletableFuture<Object> asyncSendBody = producerTemplate.asyncSendBody("direct:testResume", "a body");
			
			await("Wait that breakpoint hit is notified")
				.atMost(Duration.ofSeconds(3))
				.until(() -> 
				{ 
					return clientProxy.getStoppedEventArguments().size() == 1;
				});
			StoppedEventArguments stoppedEventArgument = clientProxy.getStoppedEventArguments().get(0);
			assertThat(stoppedEventArgument.getThreadId()).isZero();
			assertThat(stoppedEventArgument.getReason()).isEqualTo(StoppedEventArgumentsReason.BREAKPOINT);
			
			assertThat(asyncSendBody.isDone()).isFalse();
			
			server.continue_(new ContinueArguments());
			
			await("Wait that route has resumed and is finished")
				.atMost(Duration.ofSeconds(3))
				.until(() -> {
					return asyncSendBody.isDone();
				});
			
			CompletableFuture<Object> asyncSendBody2 = producerTemplate.asyncSendBody("direct:testResume", "a body 2");
			
			assertThat(asyncSendBody2.isDone()).isFalse();
			
			await("Wait that second breakpoint hit is notified")
			.atMost(Duration.ofSeconds(3))
			.until(() -> 
			{ 
				return clientProxy.getStoppedEventArguments().size() == 2;
			});
			
			producerTemplate.stop();
		}
	}

	private Variable createVariable(String name, String value) {
		Variable bodyVariable = new Variable();
		bodyVariable.setName(name);
		bodyVariable.setValue(value);
		return bodyVariable;
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
		clientProxy = new DummyCamelDebugClient(server);
		server.connect(clientProxy);
		return server.initialize(new InitializeRequestArguments());
	}

}
