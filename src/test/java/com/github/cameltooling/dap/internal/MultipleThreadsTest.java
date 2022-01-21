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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultProducerTemplate;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.eclipse.lsp4j.debug.ThreadEventArgumentsReason;
import org.junit.jupiter.api.Test;

class MultipleThreadsTest extends BaseTest {

	@Test
	void test2SuspendedBreakpointsCreates2Threads() throws Exception {
		try (CamelContext context = new DefaultCamelContext()) {
			String startEndpointUri1 = "direct:testThreads1";
			String startEndpointUri2 = "direct:testThreads2";
			context.addRoutes(new RouteBuilder() {
			
				@Override
				public void configure() throws Exception {
					from(startEndpointUri1)
						.log("Log from test 1");  // line number to use from here
					
					from(startEndpointUri2)
						.log("Log from test 2");  // line number to use from here
				}
			});
			context.start();
			assertThat(context.isStarted()).isTrue();
			initDebugger();
			attach(server);
			SetBreakpointsArguments setBreakpointsArguments = createSetBreakpointArgument(46, 49);
			
			server.setBreakpoints(setBreakpointsArguments).get();
			
			DefaultProducerTemplate producerTemplate = DefaultProducerTemplate.newInstance(context, startEndpointUri1);
			producerTemplate.start();
			
			CompletableFuture<Object> asyncSendBody1 = producerTemplate.asyncSendBody(startEndpointUri1, "a body 1");
			CompletableFuture<Object> asyncSendBody2 = producerTemplate.asyncSendBody(startEndpointUri2, "a body 2");
			
			waitBreakpointNotification(2);
			StoppedEventArguments stoppedEventArgument1 = clientProxy.getStoppedEventArguments().get(0);
			assertThat(stoppedEventArgument1.getReason()).isEqualTo(StoppedEventArgumentsReason.BREAKPOINT);
			assertThat(clientProxy.getThreadEventArgumentss()).hasSize(2);
			assertThat(clientProxy.getThreadEventArgumentss().stream().filter(args -> ThreadEventArgumentsReason.STARTED.equals(args.getReason()))).hasSize(2);
			
			assertThat(asyncSendBody1.isDone()).isFalse();
			assertThat(asyncSendBody2.isDone()).isFalse();	
			
			assertThat(clientProxy.getAllStacksAndVars()).hasSize(2);
			assertThat(clientProxy.getAllStacksAndVars().get(0).getStackFrames()).hasSize(1);
			server.continue_(new ContinueArguments());
			
			waitRouteIsDone(asyncSendBody1);
			waitRouteIsDone(asyncSendBody2);
			assertThat(clientProxy.getThreadEventArgumentss()).hasSize(4);
			assertThat(clientProxy.getThreadEventArgumentss().stream().filter(args -> ThreadEventArgumentsReason.EXITED.equals(args.getReason()))).hasSize(2);
			
			producerTemplate.stop();
		}
	}
	
}
