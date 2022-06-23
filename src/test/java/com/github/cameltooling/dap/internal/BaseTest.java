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
import java.io.FileNotFoundException;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.engine.DefaultProducerTemplate;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.eclipse.lsp4j.debug.TerminateArguments;
import org.eclipse.lsp4j.debug.TerminatedEventArguments;
import org.eclipse.lsp4j.debug.Variable;
import org.junit.jupiter.api.AfterEach;

import com.github.cameltooling.dap.internal.telemetry.TelemetryEvent;

public abstract class BaseTest {

	protected static final int DEFAULT_VARIABLES_NUMBER = 19;
	protected CamelDebugAdapterServer server;
	protected DummyCamelDebugClient clientProxy;
	protected CamelContext context;
	protected DefaultProducerTemplate producerTemplate;

	public BaseTest() {
		super();
	}

	@AfterEach
	void tearDown() throws Exception {
		if (server != null) {
			server.terminate(new TerminateArguments());
			server = null;
		}
		if (clientProxy != null) {
			clientProxy.terminated(new TerminatedEventArguments());
			clientProxy = null;
		}
		if (producerTemplate != null) {
			producerTemplate.close();
			producerTemplate = null;
		}
		if (context != null) {
			context.close();
			context = null;
		}
	}

	protected void waitBreakpointNotification(int numberOfBreakpointNotifications) {
		await("Wait that breakpoint hit is notified")
			.atMost(Duration.ofSeconds(3))
			.until(() -> {
				return clientProxy.getStoppedEventArguments().size() == numberOfBreakpointNotifications;
			});
	}

	protected void attachWithPid(CamelDebugAdapterServer server) {
		Map<String, Object> paramMap = Collections.singletonMap(BacklogDebuggerConnectionManager.ATTACH_PARAM_PID, Long.toString(ProcessHandle.current().pid()));
		attach(server, paramMap);
	}
	
	protected void attachWithJMXURL(CamelDebugAdapterServer server, String jmxUrl) {
		Map<String, Object> paramMap = Collections.singletonMap(BacklogDebuggerConnectionManager.ATTACH_PARAM_JMX_URL, jmxUrl);
		attach(server, paramMap);
	}
	
	/**
	 * It is attaching with no parameter provided. So using default JMX url.
	 * 
	 * @param server
	 */
	protected void attach(CamelDebugAdapterServer server) {
		attach(server, Collections.emptyMap());
	}

	protected void attach(CamelDebugAdapterServer server, Map<String, Object> paramMap) {
		assertThat(clientProxy.hasReceivedInitializedEvent())
			.as("The initialized event should not have been sent yet. The debugger has been attached and thus cannot handle the setBreakpoint requests.")
			.isFalse();
		server.attach(paramMap);
		assertThat(clientProxy.hasReceivedInitializedEvent())
			.as("The initialized event should have been sent right after the attach method is successful. The debugger is ready to receive the setBreakpoint requests.")
			.isTrue();
		BacklogDebuggerConnectionManager connectionManager = server.getConnectionManager();
		assertThat(connectionManager.getMbeanConnection()).as("The MBeanConnection has not been established.").isNotNull();
		assertThat(connectionManager.getBacklogDebugger()).as("The BacklogDebugger has not been found.").isNotNull();
		assertThat(clientProxy.getTelemetryEvents()).hasSize(1);
		assertThat(clientProxy.getTelemetryEvents().get(0).getData())
			.usingRecursiveComparison()
			.isEqualTo(new TelemetryEvent("camel.dap.attach", Collections.singletonMap("success", true)));
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

	/**
	 * @param markersToPutBreakpoint
	 * 
	 * It is using the current class as sourceFile
	 * 
	 * @return
	 * @throws FileNotFoundException
	 */
	protected SetBreakpointsArguments createSetBreakpointArgument(String... markersToPutBreakpoint)
			throws FileNotFoundException {
		File sourceFile = new File("src/test/java/" + getClass().getName().replaceAll("\\.", "/") + ".java");
		return createSetBreakpointArgument(sourceFile, markersToPutBreakpoint);
	}

	protected SetBreakpointsArguments createSetBreakpointArgument(File sourceFile, String... markersToPutBreakpoint)
			throws FileNotFoundException {
		SetBreakpointsArguments setBreakpointsArguments = new SetBreakpointsArguments();
		Source source = new Source();
		String pathToItself = sourceFile.getAbsolutePath();
		source.setPath(pathToItself);
		setBreakpointsArguments.setSource(source);
		SourceBreakpoint[] breakpoints = new SourceBreakpoint[markersToPutBreakpoint.length];
		for (int i = 0; i < markersToPutBreakpoint.length; i++) {
			String markerToPutBreakpoint = markersToPutBreakpoint[i];
			int lineNumberToPutBreakpoint = findLineNumber(sourceFile, markerToPutBreakpoint);
			SourceBreakpoint sourceBreakpoint = new SourceBreakpoint();
			sourceBreakpoint.setLine(lineNumberToPutBreakpoint);
			breakpoints[i] = sourceBreakpoint;
		}
		setBreakpointsArguments.setBreakpoints(breakpoints);
		return setBreakpointsArguments;
	}

	protected int findLineNumber(File sourceFile, String markerToPutBreakpoint) throws FileNotFoundException {
		int line = 1;
		try (Scanner scanner = new Scanner(sourceFile);) {
			while (scanner.hasNextLine()) {
				String lineContent = scanner.nextLine();
				if (lineContent.contains(markerToPutBreakpoint)) {
					return line;
				}
				line++;
			}
		}
		return -1;
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
