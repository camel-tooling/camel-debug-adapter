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
import java.util.stream.Stream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultProducerTemplate;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.StepInArguments;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.eclipse.lsp4j.debug.Thread;
import org.junit.jupiter.api.Test;

import com.github.cameltooling.dap.internal.model.CamelExchangeThread;

class StepInTest extends BaseTest {
	
	@Test
	void testStepInRouteRedirectToNext() throws Exception {
		context = new DefaultCamelContext();
		context.setSourceLocationEnabled(true);
		String routeId = "a-route-id";
		String startEndpointUri = "direct:testResume";
		context.addRoutes(new RouteBuilder() {

			@Override
			public void configure() throws Exception {
				from(startEndpointUri)
					.routeId(routeId)
					.log("Log from test")  // XXX-breakpoint-step-inside-XXX
					.log("second log")
					.log("last log");
			}
		});
		context.start();
		assertThat(context.isStarted()).isTrue();
		initDebugger();
		attach(server);
		SetBreakpointsArguments setBreakpointsArguments = createSetBreakpointArgument("XXX-breakpoint-step-inside-XXX");

		server.setBreakpoints(setBreakpointsArguments).get();

		producerTemplate = DefaultProducerTemplate.newInstance(context, startEndpointUri);
		producerTemplate.start();

		CompletableFuture<Object> asyncSendBody = producerTemplate.asyncSendBody(startEndpointUri, "a body");

		waitBreakpointNotification(1);
		StoppedEventArguments stoppedEventArgument = clientProxy.getStoppedEventArguments().get(0);
		assertThat(stoppedEventArgument.getThreadId()).isEqualTo(1);
		assertThat(stoppedEventArgument.getReason()).isEqualTo(StoppedEventArgumentsReason.BREAKPOINT);
		assertThat(asyncSendBody.isDone()).isFalse();
		awaitAllVariablesFilled(0);

		StepInArguments stepInArguments = new StepInArguments();
		stepInArguments.setThreadId(1);
		server.stepIn(stepInArguments);

		waitBreakpointNotification(2);
		StoppedEventArguments secondStoppedEventArgument = clientProxy.getStoppedEventArguments().get(1);
		assertThat(secondStoppedEventArgument.getThreadId()).isEqualTo(1);
		assertThat(secondStoppedEventArgument.getReason()).isEqualTo(StoppedEventArgumentsReason.BREAKPOINT);
		assertThat(asyncSendBody.isDone()).isFalse();
		awaitAllVariablesFilled(1);
		server.continue_(new ContinueArguments());

		waitRouteIsDone(asyncSendBody);

		Thread[] threads = server.threads().get().getThreads();
		assertThat(Stream.of(threads)).doesNotHaveAnyElementsOfTypes(CamelExchangeThread.class);
	}

}
