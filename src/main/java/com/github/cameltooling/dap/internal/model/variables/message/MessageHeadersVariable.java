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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.camel.api.management.mbean.ManagedBacklogDebuggerMBean;
import org.eclipse.lsp4j.debug.SetVariableArguments;
import org.eclipse.lsp4j.debug.SetVariableResponse;
import org.eclipse.lsp4j.debug.Variable;

import com.github.cameltooling.dap.internal.IdUtils;
import com.github.cameltooling.dap.internal.types.Header;

public class MessageHeadersVariable extends Variable {
	
	private final List<Header> headers;
	private final String breakpointId;

	public MessageHeadersVariable(int parentVariablesReference, List<Header> headers, String breakpointId) {
		this.headers = headers;
		this.breakpointId = breakpointId;
		setName("Headers");
		setValue("");
		int headerVarRefId = IdUtils.getPositiveIntFromHashCode((parentVariablesReference+"@Headers@").hashCode());
		setVariablesReference(headerVarRefId);
	}

	public Collection<Variable> createVariables() {
		Collection<Variable> variables = new ArrayList<>();
		if (headers != null) {
			for (Header header : headers) {
				variables.add(createVariable(header.getKey(), header.getValue()));
			}
		}
		return variables;
	}

	private Variable createVariable(String key, String value) {
		Variable variable = new Variable();
		variable.setName(key);
		variable.setValue(value);
		return variable ;
	}

	public SetVariableResponse setVariableIfInScope(SetVariableArguments args, ManagedBacklogDebuggerMBean debugger) {
		if (args.getVariablesReference() == getVariablesReference()) {
			debugger.setMessageHeaderOnBreakpoint(breakpointId, args.getName(), args.getValue());
			if (headers != null) {
				for (Header header : headers) {
					if (header.getKey().equals(args.getName())) {
						header.setValue(args.getValue());
						break;
					}
				}
			}
			SetVariableResponse response = new SetVariableResponse();
			response.setValue(args.getValue());
			return response;
		}
		return null;
	}
}
