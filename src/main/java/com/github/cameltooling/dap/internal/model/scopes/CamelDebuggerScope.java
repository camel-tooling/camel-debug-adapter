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
package com.github.cameltooling.dap.internal.model.scopes;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.api.management.mbean.ManagedBacklogDebuggerMBean;
import org.eclipse.lsp4j.debug.SetVariableArguments;
import org.eclipse.lsp4j.debug.SetVariableResponse;

import com.github.cameltooling.dap.internal.IdUtils;
import com.github.cameltooling.dap.internal.model.CamelScope;
import com.github.cameltooling.dap.internal.model.CamelStackFrame;
import com.github.cameltooling.dap.internal.model.variables.CamelVariable;
import com.github.cameltooling.dap.internal.model.variables.debugger.BodyIncludeFilesCamelVariable;
import com.github.cameltooling.dap.internal.model.variables.debugger.BodyIncludeStreamsCamelVariable;
import com.github.cameltooling.dap.internal.model.variables.debugger.DebugCounterCamelVariable;
import com.github.cameltooling.dap.internal.model.variables.debugger.FallbackTimeoutCamelVariable;
import com.github.cameltooling.dap.internal.model.variables.debugger.LoggingLevelCamelVariable;
import com.github.cameltooling.dap.internal.model.variables.debugger.MaxCharsForBodyCamelVariable;

public class CamelDebuggerScope extends CamelScope {

	public static final String NAME = "Debugger";
	private Set<CamelVariable> variables = Collections.unmodifiableSet(new HashSet<>());

	public CamelDebuggerScope(CamelStackFrame stackframe) {
		super(NAME, stackframe.getName(), IdUtils.getPositiveIntFromHashCode((stackframe.getId()+"@Debugger@" + stackframe.getName()).hashCode()));
	}
	
	public Set<CamelVariable> createVariables(int variablesReference, ManagedBacklogDebuggerMBean debugger) {
		if(variablesReference == getVariablesReference()) {
			final Set<CamelVariable> allVariables = new HashSet<>();
			allVariables.add(new LoggingLevelCamelVariable(debugger));
			allVariables.add(new MaxCharsForBodyCamelVariable(debugger));
			allVariables.add(new DebugCounterCamelVariable(debugger));
			allVariables.add(new FallbackTimeoutCamelVariable(debugger));
			allVariables.add(new BodyIncludeFilesCamelVariable(debugger));
			allVariables.add(new BodyIncludeStreamsCamelVariable(debugger));
			this.variables = Collections.unmodifiableSet(allVariables);
			return allVariables;
		}
		return Collections.emptySet();
	}

	@Override
	public SetVariableResponse setVariableIfInScope(SetVariableArguments args, ManagedBacklogDebuggerMBean backlogDebugger) {
		if (getVariablesReference() == args.getVariablesReference()) {
			for (CamelVariable variable : variables) {
				if(args.getName().equals(variable.getName())) {
					variable.updateValue(backlogDebugger, args.getValue());
					SetVariableResponse response = new SetVariableResponse();
					response.setValue(args.getValue());
					return response;
				}
			}
		}
		return null;
	}

}
