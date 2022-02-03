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

import java.util.HashSet;
import java.util.Set;

import org.apache.camel.api.management.mbean.ManagedBacklogDebuggerMBean;
import org.eclipse.lsp4j.debug.Variable;

import com.github.cameltooling.dap.internal.IdUtils;

public class CamelDebuggerScope extends CamelScope {

	public CamelDebuggerScope(CamelStackFrame stackframe) {
		super("Debugger", stackframe.getName(), IdUtils.getPositiveIntFromHashCode((stackframe.getId()+"@Debugger@" + stackframe.getName()).hashCode()));
	}
	
	public Set<Variable> createVariables(int variablesReference, ManagedBacklogDebuggerMBean debugger) {
		Set<Variable> variables = new HashSet<>();
		if(variablesReference == getVariablesReference()) {
			variables.add(createVariable("Logging level", debugger.getLoggingLevel()));
			variables.add(createVariable("Max chars for body", Integer.toString(debugger.getBodyMaxChars())));
			variables.add(createVariable("Debug counter", Long.toString(debugger.getDebugCounter())));
			variables.add(createVariable("Fallback timeout", Long.toString(debugger.getFallbackTimeout())));
			variables.add(createVariable("Body include files", Boolean.toString(debugger.isBodyIncludeFiles())));
			variables.add(createVariable("Body include streams", Boolean.toString(debugger.isBodyIncludeStreams())));
		}
		return variables;
	}

}
