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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.assertj.core.api.Condition;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.PauseArguments;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.ThreadEventArguments;
import org.eclipse.lsp4j.debug.ThreadEventArgumentsReason;
import org.junit.jupiter.api.Test;

class PauseRouteDefinitionTest extends BaseTest {

	private void createThreeRoutes() throws Exception {
		context = new DefaultCamelContext();
		context.setSourceLocationEnabled(true);
		context.addRoutes(new RouteBuilder() {

			@Override
			public void configure() throws Exception {
				from("direct:testThreads1")
					.log("Log from test 1");

				from("direct:testThreads2")
					.log("Log from test 2");
				
				from("direct:testThreads3")
					.log("Log from test 3");
			}
		});
		context.start();
		assertThat(context.isStarted()).isTrue();
		
		initDebugger();
		attach(server);
		
		Condition<ThreadEventArguments> startedThreads = new Condition<>() {
			@Override
			public boolean matches(ThreadEventArguments args) {
				return ThreadEventArgumentsReason.STARTED.equals(args.getReason());
			}
		};
		
		await("Thread Started events sent")
			.untilAsserted(() -> assertThat(clientProxy.getThreadEventArgumentss()).haveExactly(3, startedThreads));
	}
	
	@Test
	void testPauseAll() throws Exception {
		createThreeRoutes();

		server.pause(new PauseArguments());
		
		// TODO: how to check that it is really Paused?
		await("Stopped event sent")
			.untilAsserted(() -> assertThat(clientProxy.getStoppedEventArguments()).hasSize(3));
		
		ContinueArguments continueArgs = new ContinueArguments();
		continueArgs.setSingleThread(Boolean.FALSE);
		server.continue_(continueArgs);
		
		await("Continue event sent")
		.untilAsserted(() -> assertThat(clientProxy.getContinuedEventArgumentss()).hasSize(3));
	}

	@Test
	void testPauseASingleRoute() throws Exception {
		createThreeRoutes();
		
		Thread[] threads = server.threads().get().getThreads();
		assertThat(threads).hasSize(3);
		
		PauseArguments args = new PauseArguments();
		args.setThreadId(threads[0].getId());

		server.pause(args);
		
		// TODO: how to check that it is really Paused?
		await("Stopped event sent")
			.untilAsserted(() -> assertThat(clientProxy.getStoppedEventArguments()).hasSize(1));
		assertThat(clientProxy.getStoppedEventArguments().get(0).getAllThreadsStopped()).isFalse();
		assertThat(clientProxy.getStoppedEventArguments().get(0).getThreadId()).isEqualTo(threads[0].getId());
		
		ContinueArguments continueArguments = new ContinueArguments();
		continueArguments.setSingleThread(Boolean.TRUE);
		continueArguments.setThreadId(threads[0].getId());
		server.continue_(continueArguments);
		
		await("Continue event sent")
			.untilAsserted(() -> assertThat(clientProxy.getContinuedEventArgumentss()).hasSize(1));
		assertThat(clientProxy.getContinuedEventArgumentss().get(0).getAllThreadsContinued()).isFalse();
		assertThat(clientProxy.getContinuedEventArgumentss().get(0).getThreadId()).isEqualTo(threads[0].getId());
	}

}
