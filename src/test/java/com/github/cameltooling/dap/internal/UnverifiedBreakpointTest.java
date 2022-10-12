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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;

import java.io.FileNotFoundException;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteBuilderLifecycleStrategy;
import org.apache.camel.impl.DefaultCamelContext;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.github.cameltooling.dap.internal.model.CamelBreakpoint;

class UnverifiedBreakpointTest extends BaseTest {

	protected void registerRouteToTest(CamelContext context) throws Exception {
		context.addRoutes(new RouteBuilder() {
		
			@Override
			public void configure() throws Exception {
				from("direct:testSetBreakpointOutsideRoute")
					.log("Log from test"); // XXX-breakpoint-valid-XXX
			}
			
			@Override
			public void addLifecycleInterceptor(RouteBuilderLifecycleStrategy interceptor) {
				super.addLifecycleInterceptor(interceptor); // XXX-breakpoint-outside-XXX
			}
		});
	}
	
	@Test
	void testBreakpointSetOutsideOfRoute() throws Exception {
		SetBreakpointsArguments setBreakpointsArguments = startWithBreakpoint("XXX-breakpoint-outside-XXX");
		int lineNumberToPutBreakpoint = setBreakpointsArguments.getBreakpoints()[0].getLine();
		String fileOnWhichBreakpointIsSet = setBreakpointsArguments.getSource().getPath();

		SetBreakpointsResponse response = server.setBreakpoints(setBreakpointsArguments).get();

		String expectedMessage = String.format(CamelDebugAdapterServer.BREAKPOINT_MESSAGE_CANNOT_FIND_ID, fileOnWhichBreakpointIsSet, lineNumberToPutBreakpoint);
		checkReturnedBreakpoint(response, lineNumberToPutBreakpoint, expectedMessage);
	}

	@Test
	void testNoMoreCamelRoutesDetected() throws Exception {
		SetBreakpointsArguments setBreakpointsArguments = startWithBreakpoint("XXX-breakpoint-valid-XXX");
		int lineNumberToPutBreakpoint = setBreakpointsArguments.getBreakpoints()[0].getLine();
		String fileOnWhichBreakpointIsSet = setBreakpointsArguments.getSource().getPath();
		server.getConnectionManager().setRoutesDomDocument(null);

		SetBreakpointsResponse response = server.setBreakpoints(setBreakpointsArguments).get();
		
		String expectedMessage = String.format(CamelDebugAdapterServer.MESSAGE_NO_ACTIVE_ROUTES_FOUND, fileOnWhichBreakpointIsSet, lineNumberToPutBreakpoint);
		checkReturnedBreakpoint(response, lineNumberToPutBreakpoint, expectedMessage);
	}
	
	@Test
	void testExceptionWhenSettingBreakpoint() throws Exception {
		SetBreakpointsArguments setBreakpointsArguments = startWithBreakpoint("XXX-breakpoint-valid-XXX");
		int lineNumberToPutBreakpoint = setBreakpointsArguments.getBreakpoints()[0].getLine();
		String fileOnWhichBreakpointIsSet = setBreakpointsArguments.getSource().getPath();
		BacklogDebuggerConnectionManager spy = Mockito.spy(server.getConnectionManager());
		server.setConnectionManager(spy);
		String messageOfException = "Exception mocked";
		doThrow(new RuntimeException(messageOfException)).when(spy).updateBreakpointsWithSources(any(CamelBreakpoint.class));

		SetBreakpointsResponse response = server.setBreakpoints(setBreakpointsArguments).get();
		
		String expectedMessage = String.format(CamelDebugAdapterServer.BREAKPOINT_MESSAGE_EXCEPTION_OCCURED_WHEN_SEARCHING_ID,
									String.format(CamelDebugAdapterServer.BASE_MESSAGE_EXCEPTION_WHEN_SEARCHING_FOR_ID, fileOnWhichBreakpointIsSet, lineNumberToPutBreakpoint),
									messageOfException);
		checkReturnedBreakpoint(response, lineNumberToPutBreakpoint, expectedMessage);
	}
	
	private SetBreakpointsArguments startWithBreakpoint(String breakpoint) throws Exception, FileNotFoundException {
		context = new DefaultCamelContext();
		registerRouteToTest(context);
		context.start();
		assertThat(context.isStarted()).isTrue();
		initDebugger();
		attach(server);
		SetBreakpointsArguments setBreakpointsArguments = createSetBreakpointArgument(breakpoint);
		return setBreakpointsArguments;
	}
	
	private void checkReturnedBreakpoint(SetBreakpointsResponse response, int lineNumberToPutBreakpoint, String expectedMessage) {
		Breakpoint[] responseBreakpoints = response.getBreakpoints();
		assertThat(responseBreakpoints).hasSize(1);
		Breakpoint responseBreakpoint = responseBreakpoints[0];
		assertThat(responseBreakpoint.getLine()).isEqualTo(lineNumberToPutBreakpoint);
		assertThat(responseBreakpoint.isVerified()).isFalse();
		assertThat(responseBreakpoint.getMessage()).isEqualTo(expectedMessage);
	}
}
