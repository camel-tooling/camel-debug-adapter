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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.api.management.mbean.ManagedBacklogDebuggerMBean;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.Variable;

import com.github.cameltooling.dap.internal.model.scopes.CamelDebuggerScope;
import com.github.cameltooling.dap.internal.model.scopes.CamelEndpointScope;
import com.github.cameltooling.dap.internal.model.scopes.CamelExchangeScope;
import com.github.cameltooling.dap.internal.model.scopes.CamelMessageScope;
import com.github.cameltooling.dap.internal.model.scopes.CamelProcessorScope;

public class CamelStackFrame extends StackFrame {

	private volatile Set<CamelScope> scopes = Collections.unmodifiableSet(new HashSet<>());

	public CamelStackFrame(int frameId, String breakpointId, Source source, Integer line) {
		setId(frameId);
		setName(breakpointId);
		setSource(source);
		setLine(line);
	}
	
	public Set<CamelScope> createScopes() {
		final Set<CamelScope> allScopes = new HashSet<>();
		allScopes.add(new CamelDebuggerScope(this));
		allScopes.add(new CamelEndpointScope(this));
		allScopes.add(new CamelProcessorScope(this));
		allScopes.add(new CamelExchangeScope(this));
		allScopes.add(new CamelMessageScope(this));
		this.scopes = Collections.unmodifiableSet(allScopes);
		return allScopes;
	}

	public Set<Variable> createVariables(int variablesReference, ManagedBacklogDebuggerMBean debugger) {
		Set<Variable> variables = new HashSet<>();
		for (CamelScope camelScope : getScopes()) {
			variables.addAll(camelScope.createVariables(variablesReference, debugger));
		}
		return variables;
	}

	public Set<CamelScope> getScopes() {
		return scopes;
	}

}
