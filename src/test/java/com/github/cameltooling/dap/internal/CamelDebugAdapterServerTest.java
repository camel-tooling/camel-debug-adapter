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

import java.util.Collections;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CamelDebugAdapterServerTest extends BaseTest {
	
	@Test
	void testInitialize() throws Exception {
		assertThat(initDebugger()).isNotNull();
		assertThat(clientProxy.hasReceivedInitializedEvent()).isFalse();
	}
	
	@Test
	void testAttachToCamelWithPid() throws Exception {
		context = new DefaultCamelContext();
		startBasicRoute(context);
		attachWithPid(server);
		checkConnectionEstablished();
	}

	@Test
	void testAttachToCamelWithDefaultJMX() throws Exception {
		context = new DefaultCamelContext();
		startBasicRoute(context);
		attach(server);
		checkConnectionEstablished();
	}
	
	@Test
	void testAttachToCamelWithProvidedJMXURL() throws Exception {
		context = new DefaultCamelContext();
		startBasicRoute(context);
		attachWithJMXURL(server, BacklogDebuggerConnectionManager.DEFAULT_JMX_URI);
		checkConnectionEstablished();
	}
	
	@Test
	void testFailToAttach() throws Exception {
		context = new DefaultCamelContext();
		startBasicRoute(context);
		server.attach(Collections.singletonMap(BacklogDebuggerConnectionManager.ATTACH_PARAM_JMX_URL, "invalidUrl")).get();
		assertThat(clientProxy.getOutputEventArguments().get(0).getOutput()).contains("Please check that the Camel application under debug has the following requirements:");
	}
	
	private void checkConnectionEstablished() {
		BacklogDebuggerConnectionManager connectionManager = server.getConnectionManager();
		assertThat(connectionManager.getBacklogDebugger().isEnabled()).isTrue();
		assertThat(connectionManager.getRoutesDOMDocument()).as("Routes instantiated.").isNotNull();
	}

	private void startBasicRoute(CamelContext context) throws Exception {
		context.addRoutes(new RouteBuilder() {
		
			@Override
			public void configure() throws Exception {
				from("direct:test")
					.log("Log from test");
			}
		});
		context.start();
		assertThat(context.isStarted()).isTrue();
		initDebugger();
	}
}
