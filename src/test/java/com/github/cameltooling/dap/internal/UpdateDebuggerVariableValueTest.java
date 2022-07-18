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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultProducerTemplate;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.NextArguments;
import org.eclipse.lsp4j.debug.OutputEventArguments;
import org.eclipse.lsp4j.debug.OutputEventArgumentsCategory;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetVariableArguments;
import org.eclipse.lsp4j.debug.SetVariableResponse;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.cameltooling.dap.internal.model.scopes.CamelDebuggerScope;
import com.github.cameltooling.dap.internal.model.scopes.CamelExchangeScope;
import com.github.cameltooling.dap.internal.model.scopes.CamelMessageScope;
import com.github.cameltooling.dap.internal.model.variables.debugger.BodyIncludeFilesCamelVariable;
import com.github.cameltooling.dap.internal.model.variables.debugger.BodyIncludeStreamsCamelVariable;
import com.github.cameltooling.dap.internal.model.variables.debugger.DebugCounterCamelVariable;
import com.github.cameltooling.dap.internal.model.variables.debugger.FallbackTimeoutCamelVariable;
import com.github.cameltooling.dap.internal.model.variables.debugger.LoggingLevelCamelVariable;
import com.github.cameltooling.dap.internal.model.variables.debugger.MaxCharsForBodyCamelVariable;
import com.github.cameltooling.dap.internal.model.variables.message.MessageBodyCamelVariable;
import com.github.cameltooling.dap.internal.types.EventMessage;
import com.github.cameltooling.dap.internal.types.UnmarshallerEventMessage;

class UpdateDebuggerVariableValueTest extends BaseTest {
	
	private static final String LOG_ID = "log-id";
	protected static final String SECOND_LOG_ID = "second-log-id";
	private static final int NUMBER_OF_HEADER = 1;
	private static final int NUMBER_OF_EXCHANGE_PROPERTY = 1;
	private Scope debuggerScope;
	private CompletableFuture<Object> asyncSendBody;

	@BeforeEach
	void beforeEach() throws Exception {
		context = new DefaultCamelContext();
		
		String startEndpointUri = "direct:testResume";
		context.addRoutes(new RouteBuilder() {
		
			@Override
			public void configure() throws Exception {
				from(startEndpointUri)
					.setHeader("header1", constant("initial header value"))
					.setProperty("property1", constant("initial exchange property value"))
					.log("Log from test").id(LOG_ID) // XXX-breakpoint-XXX
					.log("Another log").id(SECOND_LOG_ID); 
			}
		});
		context.start();
		assertThat(context.isStarted()).isTrue();
		initDebugger();
		attach(server);
		SetBreakpointsArguments setBreakpointsArguments = createSetBreakpointArgument("XXX-breakpoint-XXX");
		
		server.setBreakpoints(setBreakpointsArguments).get();
		
		producerTemplate = DefaultProducerTemplate.newInstance(context, startEndpointUri);
		producerTemplate.start();
		
		asyncSendBody = producerTemplate.asyncSendBody(startEndpointUri, "a body");
		
		waitBreakpointNotification(1);
		StoppedEventArguments stoppedEventArgument = clientProxy.getStoppedEventArguments().get(0);
		assertThat(stoppedEventArgument.getThreadId()).isEqualTo(1);
		assertThat(stoppedEventArgument.getReason()).isEqualTo(StoppedEventArgumentsReason.BREAKPOINT);
		assertThat(asyncSendBody.isDone()).isFalse();
		awaitAllVariablesFilled(0, DEFAULT_VARIABLES_NUMBER + NUMBER_OF_HEADER + NUMBER_OF_EXCHANGE_PROPERTY);
		
		debuggerScope = clientProxy.getAllStacksAndVars().get(0).getScopes().stream().filter(scope -> CamelDebuggerScope.NAME.equals(scope.getName())).findAny().get();
	}
	
	@AfterEach
	void afterEach() {
		server.continue_(new ContinueArguments());
		waitRouteIsDone(asyncSendBody);
	}

	@Test
	void updateLoggingLevel() throws Exception {
		SetVariableArguments args = new SetVariableArguments();
		args.setName(LoggingLevelCamelVariable.NAME);
		args.setValue("TRACE");
		args.setVariablesReference(debuggerScope.getVariablesReference());

		SetVariableResponse response = server.setVariable(args).get();

		assertThat(response.getValue()).isEqualTo("TRACE");
		assertThat(response.getValue()).isEqualTo(server.getConnectionManager().getBacklogDebugger().getLoggingLevel());
	}
	
	@Test
	void updateDebugCounter() throws Exception {
		SetVariableArguments args = new SetVariableArguments();
		args.setName(DebugCounterCamelVariable.NAME);
		args.setValue("0");
		args.setVariablesReference(debuggerScope.getVariablesReference());

		SetVariableResponse response = server.setVariable(args).get();

		assertThat(response.getValue()).isEqualTo("0");
		assertThat(Long.valueOf(response.getValue())).isEqualTo(server.getConnectionManager().getBacklogDebugger().getDebugCounter());
	}
	
	@Test
	void updateBodyIncludeFiles() throws Exception {
		SetVariableArguments args = new SetVariableArguments();
		args.setName(BodyIncludeFilesCamelVariable.NAME);
		args.setValue("true");
		args.setVariablesReference(debuggerScope.getVariablesReference());

		SetVariableResponse response = server.setVariable(args).get();

		assertThat(response.getValue()).isEqualTo("true");
		assertThat(Boolean.valueOf(response.getValue())).isEqualTo(server.getConnectionManager().getBacklogDebugger().isBodyIncludeFiles());
	}
	
	@Test
	void updateBodyIncludeStreams() throws Exception {
		SetVariableArguments args = new SetVariableArguments();
		args.setName(BodyIncludeStreamsCamelVariable.NAME);
		args.setValue("true");
		args.setVariablesReference(debuggerScope.getVariablesReference());

		SetVariableResponse response = server.setVariable(args).get();

		assertThat(response.getValue()).isEqualTo("true");
		assertThat(Boolean.valueOf(response.getValue())).isEqualTo(server.getConnectionManager().getBacklogDebugger().isBodyIncludeStreams());
	}
	
	@Test
	void updateFallBackTimeout() throws Exception {
		SetVariableArguments args = new SetVariableArguments();
		args.setName(FallbackTimeoutCamelVariable.NAME);
		args.setValue("45");
		args.setVariablesReference(debuggerScope.getVariablesReference());

		SetVariableResponse response = server.setVariable(args).get();

		assertThat(response.getValue()).isEqualTo("45");
		assertThat(Long.valueOf(response.getValue())).isEqualTo(server.getConnectionManager().getBacklogDebugger().getFallbackTimeout());
	}
	
	@Test
	void updatemaxBodyChars() throws Exception {
		SetVariableArguments args = new SetVariableArguments();
		args.setName(MaxCharsForBodyCamelVariable.NAME);
		args.setValue("46");
		args.setVariablesReference(debuggerScope.getVariablesReference());

		SetVariableResponse response = server.setVariable(args).get();

		assertThat(response.getValue()).isEqualTo("46");
		assertThat(Long.valueOf(response.getValue())).isEqualTo(server.getConnectionManager().getBacklogDebugger().getBodyMaxChars());
	}
	
	@Test
	void updateBody() throws Exception {
		SetVariableArguments args = new SetVariableArguments();
		args.setName(MessageBodyCamelVariable.NAME);
		args.setValue("an updated body");
		Scope messageScope = clientProxy.getAllStacksAndVars().get(0).getScopes().stream().filter(scope -> CamelMessageScope.NAME.equals(scope.getName())).findAny().get();
		args.setVariablesReference(messageScope.getVariablesReference());

		SetVariableResponse response = server.setVariable(args).get();

		assertThat(response.getValue()).isEqualTo("an updated body");
		
		EventMessage eventMessage = getMessageStateOnNextStep();
		assertThat(response.getValue()).isEqualTo(eventMessage.getMessage().getBody());
	}
	
	@Test
	void updateHeader() throws Exception {
		SetVariableArguments args = new SetVariableArguments();
		args.setName("header1");
		args.setValue("an updated header");
		CamelMessageScope messageScope = (CamelMessageScope) clientProxy.getAllStacksAndVars().get(0).getScopes().stream().filter(scope -> CamelMessageScope.NAME.equals(scope.getName())).findAny().get();
		args.setVariablesReference(messageScope.getHeadersVariable().getVariablesReference());

		SetVariableResponse response = server.setVariable(args).get();

		assertThat(response.getValue()).isEqualTo("an updated header");
		
		EventMessage eventMessage = getMessageStateOnNextStep();
		assertThat(response.getValue()).isEqualTo(eventMessage.getMessage().getHeaders().get(0).getValue());
	}
	
	@Test
	void updateExchangeProperty() throws Exception {
		SetVariableArguments args = new SetVariableArguments();
		args.setName("property1");
		args.setValue("an updated exchange property");
		CamelExchangeScope exchangeScope = (CamelExchangeScope) clientProxy.getAllStacksAndVars().get(0).getScopes().stream().filter(scope -> CamelExchangeScope.NAME.equals(scope.getName())).findAny().get();
		args.setVariablesReference(exchangeScope.getExchangePropertiesVariable().getVariablesReference());

		
		SetVariableResponse response = server.setVariable(args).get();

		assertThat(response.getValue()).isEqualTo("an updated exchange property");
		
		EventMessage eventMessage = getMessageStateOnNextStep();
		assertThat(response.getValue()).isEqualTo(eventMessage.getExchangeProperties().get(0).getContent());
	}
	
	@Test
	void updateToAnInvalidValueReportsAnOutputError() throws Exception {
		SetVariableArguments args = new SetVariableArguments();
		args.setName(FallbackTimeoutCamelVariable.NAME);
		args.setValue("invalid value");
		args.setVariablesReference(debuggerScope.getVariablesReference());
		
		assertThatThrownBy(() -> server.setVariable(args).get())
			.isInstanceOf(ExecutionException.class)
			.hasCauseInstanceOf(NumberFormatException.class);

		OutputEventArguments outputEventArguments = clientProxy.getOutputEventArguments().get(0);
		assertThat(outputEventArguments.getCategory()).isEqualTo(OutputEventArgumentsCategory.STDERR);
		assertThat(outputEventArguments.getOutput()).contains("Cannot set variable "+ FallbackTimeoutCamelVariable.NAME);
	}
	
	private EventMessage getMessageStateOnNextStep() {
		// Checking on the next value because the existing TracedMessage are not updated, so to check the value need to go to the next endpoint.
		NextArguments nextArgs = new NextArguments();
		nextArgs.setThreadId(1);
		server.next(nextArgs);
		
		waitBreakpointNotification(2);
		awaitAllVariablesFilled(1, DEFAULT_VARIABLES_NUMBER + NUMBER_OF_HEADER + NUMBER_OF_EXCHANGE_PROPERTY);
		
		String messagesAsXml = server.getConnectionManager().getBacklogDebugger().dumpTracedMessagesAsXml(SECOND_LOG_ID, true);
		EventMessage eventMessage = new UnmarshallerEventMessage().getUnmarshalledEventMessage(messagesAsXml);
		return eventMessage;
	}
	
}
