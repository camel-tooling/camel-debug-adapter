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

import java.util.concurrent.CompletableFuture;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultProducerTemplate;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.junit.jupiter.api.Test;

class ResumeSingleThreadTest extends BaseTest {

	@Test
	void testResume() throws Exception {
		context = new DefaultCamelContext();
		String routeId1 = "a-route-id-1";
		String startEndpointUri1 = "direct:testResume1";
		String routeId2 = "a-route-id-2";
		String startEndpointUri2 = "direct:testResume2";
		context.addRoutes(new RouteBuilder() {

			@Override
			public void configure() throws Exception {
				from(startEndpointUri1)
					.routeId(routeId1)
					.log("Log from test 1");  // XXX-breakpoint1-XXX

				from(startEndpointUri2)
					.routeId(routeId2)
					.log("Log from test 2");  // XXX-breakpoint2-XXX
			}
		});
		context.start();
		assertThat(context.isStarted()).isTrue();
		initDebugger();
		attach(server);
		SetBreakpointsArguments setBreakpointsArguments = createSetBreakpointArgument("XXX-breakpoint1-XXX", "XXX-breakpoint2-XXX");

		server.setBreakpoints(setBreakpointsArguments).get();

		producerTemplate = DefaultProducerTemplate.newInstance(context, startEndpointUri1);
		producerTemplate.start();

		CompletableFuture<Object> asyncSendBody1 = producerTemplate.asyncSendBody(startEndpointUri1, "a body1");
		waitBreakpointNotification(1);
		awaitAllVariablesFilled(0);
		CompletableFuture<Object> asyncSendBody2 = producerTemplate.asyncSendBody(startEndpointUri2, "a body2");

		waitBreakpointNotification(2);
		awaitAllVariablesFilled(1, DEFAULT_VARIABLES_NUMBER *2);
		StoppedEventArguments stoppedEventArgument = clientProxy.getStoppedEventArguments().get(0);
		assertThat(stoppedEventArgument.getThreadId()).isEqualTo(1);
		assertThat(stoppedEventArgument.getReason()).isEqualTo(StoppedEventArgumentsReason.BREAKPOINT);
		assertThat(asyncSendBody1.isDone()).isFalse();

		StoppedEventArguments secondStoppedEventArgument = clientProxy.getStoppedEventArguments().get(1);
		assertThat(secondStoppedEventArgument.getThreadId()).isEqualTo(2);
		assertThat(secondStoppedEventArgument.getReason()).isEqualTo(StoppedEventArgumentsReason.BREAKPOINT);
		assertThat(asyncSendBody2.isDone()).isFalse();


		ContinueArguments continueArgs1 = new ContinueArguments();
		continueArgs1.setThreadId(1);
		server.continue_(continueArgs1);

		waitRouteIsDone(asyncSendBody1);
		assertThat(asyncSendBody2.isDone()).isFalse();

		ContinueArguments continueArgs2 = new ContinueArguments();
		continueArgs2.setThreadId(2);
		server.continue_(continueArgs2);
		waitRouteIsDone(asyncSendBody2);
	}
	
}
