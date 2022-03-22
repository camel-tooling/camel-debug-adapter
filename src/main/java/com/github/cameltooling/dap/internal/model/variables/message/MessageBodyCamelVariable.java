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
package com.github.cameltooling.dap.internal.model.variables.message;

import org.apache.camel.api.management.mbean.ManagedBacklogDebuggerMBean;

import com.github.cameltooling.dap.internal.model.variables.CamelVariable;

public class MessageBodyCamelVariable extends CamelVariable {

	public static final String NAME = "Body";
	private String breakpointId;

	public MessageBodyCamelVariable(String breakpointId, String body) {
		this.breakpointId = breakpointId;
		setName(NAME);
		setValue(body);
	}

	@Override
	public void updateValue(ManagedBacklogDebuggerMBean debugger, String value) {
		debugger.setMessageBodyOnBreakpoint(breakpointId, value);
		setValue(value);
	}

}
