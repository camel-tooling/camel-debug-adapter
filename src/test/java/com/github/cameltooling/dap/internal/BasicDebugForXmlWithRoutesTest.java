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

import java.io.File;
import java.io.FileNotFoundException;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;

class BasicDebugForXmlWithRoutesTest extends BasicDebugFlowTest {
	
	File routeForTest;
	
	@Override
	protected void registerRouteToTest(CamelContext context, String routeId, String logEndpointId) throws Exception {
		Resource resource = PluginHelper.getResourceLoader(context).resolveResource("/basic-withroutes.xml");
		routeForTest = new File(resource.getURI());
		PluginHelper.getRoutesLoader(context).loadRoutes(resource);
	}
	
	@Override
	protected SetBreakpointsArguments createSetBreakpointArgument() throws FileNotFoundException {
		return createSetBreakpointArgument(routeForTest, "XXX-breakpoint-XXX");
	}
	
}
