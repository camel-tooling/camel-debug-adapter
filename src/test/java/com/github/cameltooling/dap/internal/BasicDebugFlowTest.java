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

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultProducerTemplate;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.eclipse.lsp4j.debug.Variable;
import org.junit.jupiter.api.Test;

class BasicDebugFlowTest extends BaseTest {

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
			String logEndpointId = "testBasicFlow-log-id";
			context.addRoutes(new RouteBuilder() {
			
				@Override
				public void configure() throws Exception {
					from("direct:testSetBreakpoint")
						.routeId(routeId)
						.setHeader("header1", constant("value of header 1"))
						.setHeader("header2", constant("value of header 2"))
						.setProperty("property1", constant("value of property 1"))
						.setProperty("property2", constant("value of property 2"))
						.log("Log from test").id(logEndpointId); // line number to use from here
				}
			});
			int lineNumberToPutBreakpoint = 64;
			context.start();
			assertThat(context.isStarted()).isTrue();
			initDebugger();
			attach(server);
			SetBreakpointsArguments setBreakpointsArguments = createSetBreakpointArgument(lineNumberToPutBreakpoint);
			
			SetBreakpointsResponse response = server.setBreakpoints(setBreakpointsArguments).get();
			
			Breakpoint[] responseBreakpoints = response.getBreakpoints();
			assertThat(responseBreakpoints).hasSize(1);
			Breakpoint responseBreakpoint = responseBreakpoints[0];
			assertThat(responseBreakpoint.getLine()).isEqualTo(lineNumberToPutBreakpoint);
			assertThat(responseBreakpoint.isVerified()).isTrue();
			Set<String> breakpointsSetInCamel = server.getConnectionManager().getBacklogDebugger().breakpoints();
			assertThat(breakpointsSetInCamel).hasSize(1);

			DefaultProducerTemplate producerTemplate = DefaultProducerTemplate.newInstance(context, "direct:testSetBreakpoint");
			producerTemplate.start();
			String body = "a body for test";
			CompletableFuture<Object> asyncSendBody = producerTemplate.asyncSendBody("direct:testSetBreakpoint", body);
			
			waitBreakpointNotification(1);
			StoppedEventArguments stoppedEventArgument = clientProxy.getStoppedEventArguments().get(0);
			assertThat(stoppedEventArgument.getThreadId()).isEqualTo(1);
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
						createVariable("To node", logEndpointId),
						createVariable("Route ID", routeId),
						createVariable("header1", "value of header 1"),
						createVariable("header2", "value of header 2"),
						createVariable("property1", "value of property 1"),
						createVariable("property2", "value of property 2"));		
			
			server.continue_(new ContinueArguments());
			
			waitRouteIsDone(asyncSendBody);
			
			producerTemplate.stop();
		}
	}
	
}
