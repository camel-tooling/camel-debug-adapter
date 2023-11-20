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

class RemoveBreakpointTest extends BaseTest {

	@Test
	void testRemoveOneBreakpoint() throws Exception {
		context = new DefaultCamelContext();
		context.setSourceLocationEnabled(true);
		String fromUri = "direct:testRemoveBreakpoint";
		context.addRoutes(new RouteBuilder() {

			@Override
			public void configure() throws Exception {
				from(fromUri)
					.log("Log from test"); // XXX-breakpoint-XXX
			}
		});
		context.start();
		assertThat(context.isStarted()).isTrue();
		initDebugger();
		attach(server);
		SetBreakpointsArguments setBreakpointsArguments = createSetBreakpointArgument("XXX-breakpoint-XXX");
		server.setBreakpoints(setBreakpointsArguments).get();

		producerTemplate = DefaultProducerTemplate.newInstance(context, fromUri);
		producerTemplate.start();
		startRouteAndCheckBreakPointHit(fromUri, producerTemplate);

		SetBreakpointsArguments unsetBreakpointsArguments = createSetBreakpointArgument();
		server.setBreakpoints(unsetBreakpointsArguments).get();

		CompletableFuture<Object> asyncSendBody2 = producerTemplate.asyncSendBody(fromUri, null);
		waitRouteIsDone(asyncSendBody2);
	}

	private void startRouteAndCheckBreakPointHit(String fromUri, DefaultProducerTemplate producerTemplate) {
		CompletableFuture<Object> asyncSendBody = producerTemplate.asyncSendBody(fromUri, null);
		
		waitBreakpointNotification(1);
		StoppedEventArguments stoppedEventArgument = clientProxy.getStoppedEventArguments().get(0);
		assertThat(stoppedEventArgument.getThreadId()).isEqualTo(1);
		assertThat(stoppedEventArgument.getReason()).isEqualTo(StoppedEventArgumentsReason.BREAKPOINT);
		
		assertThat(asyncSendBody.isDone()).isFalse();
		
		awaitAllVariablesFilled(0);
		
		assertThat(clientProxy.getAllStacksAndVars()).hasSize(1);
		StackAndVarOnStopEvent stackAndData = clientProxy.getAllStacksAndVars().get(0);
		assertThat(stackAndData.getThreads()).hasSize(1);
		assertThat(stackAndData.getStackFrames()).hasSize(1);
		assertThat(stackAndData.getScopes()).hasSize(5);

		server.continue_(new ContinueArguments());
		waitRouteIsDone(asyncSendBody);
	}
	
}
