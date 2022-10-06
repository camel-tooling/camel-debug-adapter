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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteBuilderLifecycleStrategy;
import org.apache.camel.impl.DefaultCamelContext;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.junit.jupiter.api.Test;

class UnverifiedBreakpointTest extends BaseTest {

	protected void registerRouteToTest(CamelContext context, String routeId, String logEndpointId) throws Exception {
		context.addRoutes(new RouteBuilder() {
		
			@Override
			public void configure() throws Exception {
				from("direct:testSetBreakpointOutsideRoute")
					.routeId(routeId)
					.log("Log from test").id(logEndpointId);
			}
			
			@Override
			public void addLifecycleInterceptor(RouteBuilderLifecycleStrategy interceptor) {
				super.addLifecycleInterceptor(interceptor); // XXX-breakpoint-XXX
			}
		});
	}
	
	@Test
	void testBreakpointSetOutsideOfRoute() throws Exception {
		context = new DefaultCamelContext();
		String routeId = "a-route-id";
		String logEndpointId = "testBasicFlow-log-id";
		registerRouteToTest(context, routeId, logEndpointId);
		context.start();
		assertThat(context.isStarted()).isTrue();
		initDebugger();
		attach(server);
		SetBreakpointsArguments setBreakpointsArguments = createSetBreakpointArgument("XXX-breakpoint-XXX");
		int lineNumberToPutBreakpoint = setBreakpointsArguments.getBreakpoints()[0].getLine();

		SetBreakpointsResponse response = server.setBreakpoints(setBreakpointsArguments).get();

		Breakpoint[] responseBreakpoints = response.getBreakpoints();
		assertThat(responseBreakpoints).hasSize(1);
		Breakpoint responseBreakpoint = responseBreakpoints[0];
		assertThat(responseBreakpoint.getLine()).isEqualTo(lineNumberToPutBreakpoint);
		assertThat(responseBreakpoint.isVerified()).isFalse();
		assertThat(responseBreakpoint.getMessage()).isNotNull();
	}
}
