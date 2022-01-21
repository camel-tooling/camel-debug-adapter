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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultProducerTemplate;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.eclipse.lsp4j.debug.Variable;
import org.junit.jupiter.api.Test;

class ResumeAllTest extends BaseTest {

	@Test
	void testResumeAndSecondBreakpointHitOfAnotherRouteInstance() throws Exception {
		try (CamelContext context = new DefaultCamelContext()) {
			String routeId = "a-route-id";
			String startEndpointUri = "direct:testResume";
			context.addRoutes(new RouteBuilder() {
			
				@Override
				public void configure() throws Exception {
					from(startEndpointUri)
						.routeId(routeId)
						.log("Log from test");  // line number to use from here
				}
			});
			int lineNumberToPutBreakpoint = 49;
			context.start();
			assertThat(context.isStarted()).isTrue();
			initDebugger();
			attach(server);
			SetBreakpointsArguments setBreakpointsArguments = createSetBreakpointArgument(lineNumberToPutBreakpoint);
			
			server.setBreakpoints(setBreakpointsArguments).get();
			
			DefaultProducerTemplate producerTemplate = DefaultProducerTemplate.newInstance(context, startEndpointUri);
			producerTemplate.start();
			
			CompletableFuture<Object> asyncSendBody = producerTemplate.asyncSendBody(startEndpointUri, "a body");
			
			waitBreakpointNotification(1);
			int index = 0;
			StoppedEventArguments stoppedEventArgument = clientProxy.getStoppedEventArguments().get(index);
			assertThat(stoppedEventArgument.getThreadId()).isEqualTo(1);
			assertThat(stoppedEventArgument.getReason()).isEqualTo(StoppedEventArgumentsReason.BREAKPOINT);
			waitForBodyVariable(0);
			Variable bodyVariable1 = findBodyVariableAtIndex(index).get();
			assertThat(bodyVariable1.getValue()).isEqualTo("a body");
			
			assertThat(asyncSendBody.isDone()).isFalse();
			
			server.continue_(new ContinueArguments());
			
			waitRouteIsDone(asyncSendBody);
			
			CompletableFuture<Object> asyncSendBody2 = producerTemplate.asyncSendBody(startEndpointUri, "a body 2");
			
			assertThat(asyncSendBody2.isDone()).isFalse();
			
			waitBreakpointNotification(2);
			
			waitForBodyVariable(1);
			Variable bodyVariable2 = findBodyVariableAtIndex(1).get();
			assertThat(bodyVariable2.getValue()).isEqualTo("a body 2");
			
			producerTemplate.stop();
		}
	}

	private void waitForBodyVariable(int index) {
		await("Wait for body variable to be filled up")
			.until(() -> {
				return findBodyVariableAtIndex(index).isPresent();
			});
	}

	private Optional<Variable> findBodyVariableAtIndex(int index) {
		return clientProxy.getAllStacksAndVars().get(index).getVariables().stream().filter(variable -> "body".equalsIgnoreCase(variable.getName())).findAny();
	}
	
	@Test
	void testResumeAndSecondBreakpointHitOnSameRouteInstance() throws Exception {
		try (CamelContext context = new DefaultCamelContext()) {
			String routeId = "a-route-id";
			String startEndpointUri = "direct:testResume";
			context.addRoutes(new RouteBuilder() {
			
				@Override
				public void configure() throws Exception {
					from(startEndpointUri)
						.routeId(routeId)
						.log("Log from test")  // line number to use from here
						.log("second log");
				}
			});
			int firstLineNumberToPutBreakpoint = 117;
			int secondLineNumberToPutBreakpoint = firstLineNumberToPutBreakpoint + 1;
			context.start();
			assertThat(context.isStarted()).isTrue();
			initDebugger();
			attach(server);
			SetBreakpointsArguments setBreakpointsArguments = createSetBreakpointArgument(firstLineNumberToPutBreakpoint, secondLineNumberToPutBreakpoint);
			
			server.setBreakpoints(setBreakpointsArguments).get();
			
			DefaultProducerTemplate producerTemplate = DefaultProducerTemplate.newInstance(context, startEndpointUri);
			producerTemplate.start();
			
			CompletableFuture<Object> asyncSendBody = producerTemplate.asyncSendBody(startEndpointUri, "a body");
			
			waitBreakpointNotification(1);
			StoppedEventArguments stoppedEventArgument = clientProxy.getStoppedEventArguments().get(0);
			assertThat(stoppedEventArgument.getThreadId()).isEqualTo(1);
			assertThat(stoppedEventArgument.getReason()).isEqualTo(StoppedEventArgumentsReason.BREAKPOINT);
			assertThat(asyncSendBody.isDone()).isFalse();
			server.continue_(new ContinueArguments());
			
			waitBreakpointNotification(2);
			StoppedEventArguments secondStoppedEventArgument = clientProxy.getStoppedEventArguments().get(1);
			assertThat(secondStoppedEventArgument.getThreadId()).isEqualTo(2);
			assertThat(secondStoppedEventArgument.getReason()).isEqualTo(StoppedEventArgumentsReason.BREAKPOINT);
			assertThat(asyncSendBody.isDone()).isFalse();
			server.continue_(new ContinueArguments());
			
			waitRouteIsDone(asyncSendBody);
			
			producerTemplate.stop();
		}
	}
	
}
