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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.Variable;

public class StackAndVarOnStopEvent {

	private List<Thread> threads;
	private List<Scope> scopes = new ArrayList<>();
	private List<Variable> variables = new ArrayList<>();
	private List<StackFrame> stackFrames = new ArrayList<>();

	public void setThreads(List<Thread> threads) {
		this.threads = threads;
	}

	public List<Thread> getThreads() {
		return threads;
	}

	public List<Scope> getScopes() {
		return scopes;
	}

	public List<Variable> getVariables() {
		return variables;
	}
	
	public List<StackFrame> getStackFrames() {
		return stackFrames;
	}

	public void addScopes(List<Scope> scopes) {
		this.scopes.addAll(scopes);
	}

	public void addVariables(List<Variable> variables) {
		this.variables.addAll(variables);
	}
	
	public void addStackframes(List<StackFrame> stackFrames) {
		this.stackFrames.addAll(stackFrames);
	}

}
