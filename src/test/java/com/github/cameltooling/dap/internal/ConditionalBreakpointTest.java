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

import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultProducerTemplate;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.eclipse.lsp4j.debug.Thread;
import org.junit.jupiter.api.Test;

import com.github.cameltooling.dap.internal.model.CamelExchangeThread;

class ConditionalBreakpointTest extends BaseTest {

	
	@Test
	void testCondition() throws Exception {
		context = new DefaultCamelContext();
		context.setSourceLocationEnabled(true);
		String routeId = "a-route-id";
		String startEndpointUri = "direct:testConditionalBreakpoint";
		context.addRoutes(new RouteBuilder() {

			@Override
			public void configure() throws Exception {
				from(startEndpointUri)
					.routeId(routeId)
					.log("Log from test");  // XXX-breakpoint-XXX
			}
		});
		context.start();
		assertThat(context.isStarted()).isTrue();
		initDebugger();
		attach(server);
		SetBreakpointsArguments setBreakpointsArguments = createSetBreakpointArgument("XXX-breakpoint-XXX", "${body} == 'specific content'");

		server.setBreakpoints(setBreakpointsArguments).get();

		producerTemplate = DefaultProducerTemplate.newInstance(context, startEndpointUri);
		producerTemplate.start();

		CompletableFuture<Object> asyncSendBody1 = producerTemplate.asyncSendBody(startEndpointUri, "a body");
		waitRouteIsDone(asyncSendBody1);

		CompletableFuture<Object> asyncSendBody2 = producerTemplate.asyncSendBody(startEndpointUri, "specific content");

		waitBreakpointNotification(1);
		StoppedEventArguments secondStoppedEventArgument = clientProxy.getStoppedEventArguments().get(0);
		assertThat(secondStoppedEventArgument.getReason()).isEqualTo(StoppedEventArgumentsReason.BREAKPOINT);
		assertThat(asyncSendBody2.isDone()).isFalse();
		awaitAllVariablesFilled(0);
		server.continue_(new ContinueArguments());

		waitRouteIsDone(asyncSendBody2);

		Thread[] threads = server.threads().get().getThreads();
		assertThat(Stream.of(threads)).doesNotHaveAnyElementsOfTypes(CamelExchangeThread.class);
	}
	
	
	protected SetBreakpointsArguments createSetBreakpointArgument(String markerToPutBreakpoint, String condition)
			throws FileNotFoundException {
		File sourceFile = new File("src/test/java/" + getClass().getName().replaceAll("\\.", "/") + ".java");
		return createSetBreakpointArgument(sourceFile, markerToPutBreakpoint, condition);
	}

	protected SetBreakpointsArguments createSetBreakpointArgument(File sourceFile, String markerToPutBreakpoint, String condition)
			throws FileNotFoundException {
		SetBreakpointsArguments setBreakpointsArguments = new SetBreakpointsArguments();
		Source source = new Source();
		String pathToItself = sourceFile.getAbsolutePath();
		source.setPath(pathToItself);
		setBreakpointsArguments.setSource(source);
		SourceBreakpoint[] breakpoints = new SourceBreakpoint[1];
		int lineNumberToPutBreakpoint = findLineNumber(sourceFile, markerToPutBreakpoint);
		SourceBreakpoint sourceBreakpoint = new SourceBreakpoint();
		sourceBreakpoint.setLine(lineNumberToPutBreakpoint);
		sourceBreakpoint.setCondition(condition);
		breakpoints[0] = sourceBreakpoint;
		setBreakpointsArguments.setBreakpoints(breakpoints);
		return setBreakpointsArguments;
	}
}
