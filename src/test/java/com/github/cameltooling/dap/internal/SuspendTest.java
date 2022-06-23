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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.debugger.BacklogDebugger;
import org.apache.camel.impl.engine.DefaultProducerTemplate;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A test ensuring that message processing can be suspended.
 */
class SuspendTest extends BaseTest {

	@Test
	@SetSystemProperty(key = BacklogDebugger.SUSPEND_MODE_SYSTEM_PROP_NAME, value = "true")
	void testOnSuspendModeEnabled() throws Exception {
		try (CamelContext context = new DefaultCamelContext()) {
			String routeId = "a-route-id";
			String startEndpointUri = "direct:testResume";
			context.addRoutes(new RouteBuilder() {

				@Override
				public void configure() throws Exception {
					from(startEndpointUri)
						.routeId(routeId)
						.log("Log from test");  // XXX-breakpoint-route-instance-1
				}
			});
			context.start();
			assertThat(context.isStarted()).isTrue();
			DefaultProducerTemplate producerTemplate = DefaultProducerTemplate.newInstance(context, startEndpointUri);
			producerTemplate.start();

			producerTemplate.asyncSendBody(startEndpointUri, "a body");

			initDebugger();
			attach(server);

			SetBreakpointsArguments setBreakpointsArguments = createSetBreakpointArgument("XXX-breakpoint-route-instance-1");
			server.setBreakpoints(setBreakpointsArguments).get();

			server.configurationDone(new ConfigurationDoneArguments());
			waitBreakpointNotification(1);

			producerTemplate.stop();
		}
	}

	@Test
	@SetSystemProperty(key = BacklogDebugger.SUSPEND_MODE_SYSTEM_PROP_NAME, value = "false")
	void testOnSuspendModeDisabled() throws Exception {
		try (CamelContext context = new DefaultCamelContext()) {
			String routeId = "a-route-id";
			String startEndpointUri = "direct:testResume";
			context.addRoutes(new RouteBuilder() {

				@Override
				public void configure() throws Exception {
					from(startEndpointUri)
						.routeId(routeId)
						.log("Log from test");  // XXX-breakpoint-route-instance-2
				}
			});
			context.start();
			assertThat(context.isStarted()).isTrue();
			initDebugger();
			attach(server);
			SetBreakpointsArguments setBreakpointsArguments = createSetBreakpointArgument("XXX-breakpoint-route-instance-2");

			server.setBreakpoints(setBreakpointsArguments).get();

			DefaultProducerTemplate producerTemplate = DefaultProducerTemplate.newInstance(context, startEndpointUri);
			producerTemplate.start();

			producerTemplate.asyncSendBody(startEndpointUri, "a body");

			server.configurationDone(new ConfigurationDoneArguments());
			waitBreakpointNotification(1);

			producerTemplate.stop();
		}
	}

}
