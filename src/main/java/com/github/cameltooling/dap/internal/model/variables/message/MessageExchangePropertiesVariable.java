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
import com.github.cameltooling.dap.internal.types.ExchangeProperty;

/**
 * This is used pre-Camel 4.2 only (not included)
 */
public class MessageExchangePropertiesVariable extends Variable {
	
	private final List<ExchangeProperty> exchangeProperties;
	private final String breakpointId;

	public MessageExchangePropertiesVariable(int parentVariablesReference, List<ExchangeProperty> exchangeProperties, String breakpointId) {
		this.exchangeProperties = exchangeProperties;
		this.breakpointId = breakpointId;
		setName("Properties");
		setValue("");
		int headerVarRefId = IdUtils.getPositiveIntFromHashCode((parentVariablesReference+"@ExchangeProperties@").hashCode());
		setVariablesReference(headerVarRefId);
	}

	public Collection<Variable> createVariables() {
		Collection<Variable> variables = new ArrayList<>();
		if (exchangeProperties != null) {
			for (ExchangeProperty exchangeProperty : exchangeProperties) {
				variables.add(createVariable(exchangeProperty.getKey(), exchangeProperty.getContent()));
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
			debugger.setExchangePropertyOnBreakpoint(breakpointId, args.getName(), args.getValue());
			if (exchangeProperties != null) {
				for (ExchangeProperty exchangeProperty : exchangeProperties) {
					if (exchangeProperty.getKey().equals(args.getName())) {
						exchangeProperty.setContent(args.getValue());
					}
				}
			}
			SetVariableResponse response = new SetVariableResponse();
			response.setValue(args.getValue());
			return response;
		}
		return null;
	}

	public List<ExchangeProperty> getExchangeProperties() {
		return exchangeProperties;
	}
}
