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
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultProducerTemplate;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.junit.jupiter.api.Test;

class AutomaticReloadTest extends BaseTest {
	
	private void addRoute(CamelContext context, String startEndpointUri) throws Exception {
		System.out.println("add route");
		context.addRoutes(new RouteBuilder() {
			
			@Override
			public void configure() throws Exception {
				from(startEndpointUri)
					.log("Log from test 1");  // XXX-breakpoint-XXX
			}
		});
	}
	
	@Test
	void testAutomaticReloadOfRoutes() throws Exception {
		try (CamelContext context = new DefaultCamelContext()) {
			String startEndpointUri = "direct:testAutoReload";
			
			addRoute(context, startEndpointUri);
			
			context.start();
			initDebugger();
			attach(server);
			SetBreakpointsArguments setBreakpointsArguments = createSetBreakpointArgument("XXX-breakpoint-XXX");
			
			server.setBreakpoints(setBreakpointsArguments).get();
			
			DefaultProducerTemplate producerTemplate = DefaultProducerTemplate.newInstance(context, startEndpointUri);
			producerTemplate.start();
			
			CompletableFuture<Object> asyncSendBody1 = producerTemplate.asyncSendBody(startEndpointUri, "a body 1");
			waitBreakpointNotification(1);
			awaitAllVariablesFilled(0);
			StoppedEventArguments stoppedEventArgument = clientProxy.getStoppedEventArguments().get(0);
			assertThat(stoppedEventArgument.getReason()).isEqualTo(StoppedEventArgumentsReason.BREAKPOINT);
			
			//TODO how to trigger/simulate the live reload? stop/remove/add is simulating what is happening with Camel JBang. But not with quarkus:dev which is restarting context.
			String routeId = context.getRoutes().get(0).getId();
			context.getRouteController().stopRoute(routeId);
			assertThat(context.removeRoute(routeId)).isTrue();
			Thread.sleep(1000); // we have a know hole, if the JMX service is down less than a second
			addRoute(context, startEndpointUri);
			
			Thread.sleep(1000); // find a conditional wait for breakpoint to be added back
			
			
			producerTemplate.asyncSendBody(startEndpointUri, "a body 2");
			waitBreakpointNotification(2);
			awaitAllVariablesFilled(1);
			StoppedEventArguments stoppedEventArgument2 = clientProxy.getStoppedEventArguments().get(1);
			assertThat(stoppedEventArgument2.getReason()).isEqualTo(StoppedEventArgumentsReason.BREAKPOINT);
		}
	}
	
	@Test
	void testAutomaticReloadOfContext() throws Exception {
		String startEndpointUri = "direct:testAutoReload";
		try (CamelContext context = new DefaultCamelContext()) {
			
			addRoute(context, startEndpointUri);
			
			context.start();
			initDebugger();
			attach(server);
			SetBreakpointsArguments setBreakpointsArguments = createSetBreakpointArgument("XXX-breakpoint-XXX");
			
			server.setBreakpoints(setBreakpointsArguments).get();
			
			DefaultProducerTemplate producerTemplate = DefaultProducerTemplate.newInstance(context, startEndpointUri);
			producerTemplate.start();
			
			CompletableFuture<Object> asyncSendBody1 = producerTemplate.asyncSendBody(startEndpointUri, "a body 1");
			waitBreakpointNotification(1);
			awaitAllVariablesFilled(0);
			StoppedEventArguments stoppedEventArgument = clientProxy.getStoppedEventArguments().get(0);
			assertThat(stoppedEventArgument.getReason()).isEqualTo(StoppedEventArgumentsReason.BREAKPOINT);
			
			// TODO how to trigger/simulate the live reload? stop context/start context/ add route again is simulating
			// what is happening with Camel Quarkus.
			System.out.println("Will stop context");
			context.stop();
			System.out.println("context stopped");
			Thread.sleep(1000); // we have a known hole, if the JMX service is down less than a second
		}
		try (CamelContext context = new DefaultCamelContext()) {
			addRoute(context, startEndpointUri);

			Thread.sleep(1000); // find a conditional wait for breakpoint to be added back

			DefaultProducerTemplate producerTemplate = DefaultProducerTemplate.newInstance(context, startEndpointUri);
			producerTemplate.start();
			producerTemplate.asyncSendBody(startEndpointUri, "a body 2");
			waitBreakpointNotification(2, 5);
			awaitAllVariablesFilled(1);
			StoppedEventArguments stoppedEventArgument2 = clientProxy.getStoppedEventArguments().get(1);
			assertThat(stoppedEventArgument2.getReason()).isEqualTo(StoppedEventArgumentsReason.BREAKPOINT);
		}
	}
}
