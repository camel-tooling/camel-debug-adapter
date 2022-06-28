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
package com.github.cameltooling.dap.internal.model;

import java.util.Objects;
import java.util.Set;

import org.apache.camel.api.management.mbean.ManagedBacklogDebuggerMBean;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.SetVariableArguments;
import org.eclipse.lsp4j.debug.SetVariableResponse;
import org.eclipse.lsp4j.debug.Variable;

public abstract class CamelScope extends Scope {

	private final String breakpointId;

	protected CamelScope(String name, String breakpointId, int variableRefId) {
		setName(name);
		setVariablesReference(variableRefId);
		this.breakpointId = breakpointId;
	}

	public String getBreakpointId() {
		return breakpointId;
	}
	
	public abstract Set<? extends Variable> createVariables(int variablesReference, ManagedBacklogDebuggerMBean debugger);
	
	protected Variable createVariable(String variableName, String variableValue) {
		Variable variable = new Variable();
		variable.setName(variableName);
		variable.setValue(variableValue);
		return variable;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!super.equals(obj)) {
			return false;
		}
		CamelScope that = (CamelScope) obj;
		return Objects.equals(this.breakpointId, that.breakpointId);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), breakpointId);
	}

	public abstract SetVariableResponse setVariableIfInScope(SetVariableArguments args, ManagedBacklogDebuggerMBean backlogDebugger);

}
