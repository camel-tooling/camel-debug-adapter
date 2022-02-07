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

import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.eclipse.lsp4j.debug.TerminateArguments;
import org.eclipse.lsp4j.debug.TerminatedEventArguments;
import org.eclipse.lsp4j.debug.Variable;
import org.junit.jupiter.api.AfterEach;

public abstract class BaseTest {

	protected static final int DEFAULT_VARIABLES_NUMBER = 19;
	protected CamelDebugAdapterServer server;
	protected DummyCamelDebugClient clientProxy;

	public BaseTest() {
		super();
	}

	@AfterEach
	void tearDown() {
		if (server != null) {
			server.terminate(new TerminateArguments());
			server = null;
		}
		if (clientProxy != null) {
			clientProxy.terminated(new TerminatedEventArguments());
			clientProxy = null;
		}
	}

	protected void waitBreakpointNotification(int numberOfBreakpointNotifications) {
		await("Wait that breakpoint hit is notified")
			.atMost(Duration.ofSeconds(3))
			.until(() -> 
			{ 
				return clientProxy.getStoppedEventArguments().size() == numberOfBreakpointNotifications;
			});
	}

	protected void attach(CamelDebugAdapterServer server) {
		server.attach(Collections.singletonMap(BacklogDebuggerConnectionManager.ATTACH_PARAM_PID, Long.toString(ProcessHandle.current().pid())));
		BacklogDebuggerConnectionManager connectionManager = server.getConnectionManager();
		assertThat(connectionManager.getMbeanConnection()).as("The MBeanConnection has not been established.").isNotNull();
		assertThat(connectionManager.getBacklogDebugger()).as("The BacklogDebugger has not been found.").isNotNull();
	}

	protected void waitRouteIsDone(CompletableFuture<Object> asyncSendBody) {
		await("Wait that route has resumed and is finished")
			.atMost(Duration.ofSeconds(3))
			.until(() -> {
				return asyncSendBody.isDone();
			});
	}

	protected Variable createVariable(String name, String value) {
		Variable bodyVariable = new Variable();
		bodyVariable.setName(name);
		bodyVariable.setValue(value);
		return bodyVariable;
	}

	protected SetBreakpointsArguments createSetBreakpointArgument(int... lineNumberToPutBreakpoints) {
		SetBreakpointsArguments setBreakpointsArguments = new SetBreakpointsArguments();
		Source source = new Source();
		String pathToItself = (new File("src/test/java/"+getClass().getName()+".java")).getAbsolutePath();
		source.setPath(pathToItself);
		setBreakpointsArguments.setSource(source);
		SourceBreakpoint[] breakpoints = new SourceBreakpoint[lineNumberToPutBreakpoints.length];
		for (int i = 0; i < lineNumberToPutBreakpoints.length; i++) {
			int lineNumberToPutBreakpoint = lineNumberToPutBreakpoints[i];
			SourceBreakpoint sourceBreakpoint = new SourceBreakpoint();
			sourceBreakpoint.setLine(lineNumberToPutBreakpoint);
			breakpoints[i] = sourceBreakpoint;
		}
		setBreakpointsArguments.setBreakpoints(breakpoints);
		return setBreakpointsArguments;
	}

	protected CompletableFuture<Capabilities> initDebugger() {
		server = new CamelDebugAdapterServer();
		clientProxy = new DummyCamelDebugClient(server);
		server.connect(clientProxy);
		return server.initialize(new InitializeRequestArguments());
	}

	protected void awaitAllVariablesFilled(int indexOfAllStacksAndVars, int variablesNumber) {
		await().untilAsserted(() -> {
			assertThat(clientProxy.getAllStacksAndVars().get(indexOfAllStacksAndVars).getVariables()).hasSize(variablesNumber);
		});
	}
	
	protected void awaitAllVariablesFilled(int indexOfAllStacksAndVars) {
		awaitAllVariablesFilled(indexOfAllStacksAndVars, DEFAULT_VARIABLES_NUMBER);
	}

}
