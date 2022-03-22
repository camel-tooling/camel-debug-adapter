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

import java.io.FileNotFoundException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.mbean.ManagedBacklogDebuggerMBean;
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

import com.github.cameltooling.dap.internal.model.variables.debugger.BodyIncludeFilesCamelVariable;
import com.github.cameltooling.dap.internal.model.variables.debugger.BodyIncludeStreamsCamelVariable;
import com.github.cameltooling.dap.internal.model.variables.debugger.DebugCounterCamelVariable;
import com.github.cameltooling.dap.internal.model.variables.debugger.FallbackTimeoutCamelVariable;
import com.github.cameltooling.dap.internal.model.variables.debugger.LoggingLevelCamelVariable;
import com.github.cameltooling.dap.internal.model.variables.debugger.MaxCharsForBodyCamelVariable;
import com.github.cameltooling.dap.internal.model.variables.message.MessageBodyCamelVariable;

abstract class BasicDebugFlowTest extends BaseTest {

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
			registerRouteToTest(context, routeId, logEndpointId);
			context.start();
			assertThat(context.isStarted()).isTrue();
			initDebugger();
			attach(server);
			SetBreakpointsArguments setBreakpointsArguments = createSetBreakpointArgument();
			int lineNumberToPutBreakpoint = setBreakpointsArguments.getBreakpoints()[0].getLine();
			
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
			await().untilAsserted(() -> assertThat(stackAndData.getThreads()).hasSize(1));
			await().untilAsserted(() -> assertThat(stackAndData.getStackFrames()).hasSize(1));
			await().untilAsserted(() -> assertThat(stackAndData.getScopes()).hasSize(5));
			await("handling of stop event response is finished")
			 .atMost(Duration.ofSeconds(60))
			 .until(() -> {
				 return stackAndData.getVariables().size() == 23;
			 });
			ManagedBacklogDebuggerMBean debugger = server.getConnectionManager().getBacklogDebugger();
			List<Variable> variables = stackAndData.getVariables();
			assertThat(variables)
				.contains(
						new MessageBodyCamelVariable(logEndpointId, body),
						new LoggingLevelCamelVariable(debugger),
						new MaxCharsForBodyCamelVariable(debugger),
						new FallbackTimeoutCamelVariable(debugger),
						new DebugCounterCamelVariable(debugger),
						new BodyIncludeFilesCamelVariable(debugger),
						new BodyIncludeStreamsCamelVariable(debugger),
						createVariable("To node", logEndpointId),
						createVariable("Route ID", routeId),
						createVariable("header1", "value of header 1"),
						createVariable("header2", "value of header 2"),
						createVariable("property1", "value of property 1"),
						createVariable("property2", "value of property 2"));
			
			assertThat(variables.stream().map(var -> var.getValue()))
				.as("All variables must have a non-null values due to limitation in VS Code implementation, see https://github.com/microsoft/vscode/issues/141544")
				.doesNotContain(new String[] {null});
			
			server.continue_(new ContinueArguments());
			
			waitRouteIsDone(asyncSendBody);
			
			producerTemplate.stop();
		}
	}

	protected abstract SetBreakpointsArguments createSetBreakpointArgument() throws FileNotFoundException;

	protected abstract void registerRouteToTest(CamelContext context, String routeId, String logEndpointId) throws Exception;
	
}
