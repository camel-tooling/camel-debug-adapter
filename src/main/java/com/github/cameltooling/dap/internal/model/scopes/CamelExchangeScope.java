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

import java.util.HashSet;
import java.util.Set;

import org.apache.camel.api.management.mbean.ManagedBacklogDebuggerMBean;
import org.eclipse.lsp4j.debug.SetVariableArguments;
import org.eclipse.lsp4j.debug.SetVariableResponse;
import org.eclipse.lsp4j.debug.Variable;

import com.github.cameltooling.dap.internal.IdUtils;
import com.github.cameltooling.dap.internal.model.CamelScope;
import com.github.cameltooling.dap.internal.model.CamelStackFrame;
import com.github.cameltooling.dap.internal.types.EventMessage;
import com.github.cameltooling.dap.internal.types.UnmarshallerEventMessage;

public class CamelExchangeScope extends CamelScope {
	
	public static final String NAME = "Exchange";

	public CamelExchangeScope(CamelStackFrame stackframe) {
		super(NAME, stackframe.getName(), IdUtils.getPositiveIntFromHashCode((stackframe.getId()+"@Exchange@" + stackframe.getName()).hashCode()));
	}

	@Override
	public Set<Variable> createVariables(int variablesReference, ManagedBacklogDebuggerMBean debugger) {
		Set<Variable> variables = new HashSet<>();
		if (variablesReference == getVariablesReference()) {
			// Keep using deprecated method to have it still working with 4.1-
			String xml = debugger.dumpTracedMessagesAsXml(getBreakpointId(), true);
			EventMessage eventMessage = new UnmarshallerEventMessage().getUnmarshalledEventMessage(xml);
			if (eventMessage != null) {
				variables.add(createVariable("ID", eventMessage.getExchangeId()));
				variables.add(createVariable("To node", eventMessage.getToNode()));
				variables.add(createVariable("Route ID", eventMessage.getRouteId()));
			}
		}
		return variables;
	}

	@Override
	public SetVariableResponse setVariableIfInScope(SetVariableArguments args, ManagedBacklogDebuggerMBean backlogDebugger) {
		return null;
	}
}
