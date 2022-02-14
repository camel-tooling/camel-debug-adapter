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

import java.io.FileNotFoundException;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;

public class BasicDebugForJavaTest extends BasicDebugFlowTest {

	@Override
	protected void registerRouteToTest(CamelContext context, String routeId, String logEndpointId) throws Exception {
		context.addRoutes(new RouteBuilder() {
		
			@Override
			public void configure() throws Exception {
				from("direct:testSetBreakpoint")
					.routeId(routeId)
					.setHeader("header1", constant("value of header 1"))
					.setHeader("header2", constant("value of header 2"))
					.setProperty("property1", constant("value of property 1"))
					.setProperty("property2", constant("value of property 2"))
					.log("Log from test").id(logEndpointId); // XXX-breakpoint-XXX
			}
		});
	}
	
	@Override
	protected SetBreakpointsArguments createSetBreakpointArgument() throws FileNotFoundException {
		return createSetBreakpointArgument("XXX-breakpoint-XXX");
	}

}
