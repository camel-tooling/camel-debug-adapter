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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.ScopesResponse;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StackTraceArguments;
import org.eclipse.lsp4j.debug.StackTraceResponse;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.ThreadsResponse;
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.eclipse.lsp4j.debug.VariablesResponse;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;

public class DummyCamelDebugClient implements IDebugProtocolClient {
	
	private boolean hasReceivedInitializedEvent;
	private List<StoppedEventArguments> stoppedEventArguments = new ArrayList<>();
	private List<StackAndVarOnStopEvent> wholeStackAndVars = new ArrayList<>();
	private CamelDebugAdapterServer server;

	public DummyCamelDebugClient(CamelDebugAdapterServer server) {
		this.server = server;
	}

	@Override
	public void initialized() {
		hasReceivedInitializedEvent = true;
	}
	
	public boolean hasReceivedInitializedEvent() {
		return hasReceivedInitializedEvent;
	}
	
	/**
	 * Current implementation is storing all stack frame, scope and data at the same level
	 */
	@Override
	public void stopped(StoppedEventArguments args) {
		stoppedEventArguments.add(args);
		StackAndVarOnStopEvent allData = new StackAndVarOnStopEvent();
		getAllStacksAndVars().add(allData);
		try {
			ThreadsResponse threadsResponse = server.threads().get();
			Thread[] threads = threadsResponse.getThreads();
			allData.setThreads(Arrays.asList(threads));
			for (org.eclipse.lsp4j.debug.Thread thread : threads) {
				StackTraceArguments stackTraceArgs = new StackTraceArguments();
				stackTraceArgs.setThreadId(thread.getId());
				StackTraceResponse stackTraceResponse = server.stackTrace(stackTraceArgs).get();
				StackFrame[] stackFrames = stackTraceResponse.getStackFrames();
				allData.addStackframes(Arrays.asList(stackFrames));
				for(StackFrame stackFrame : stackFrames) {
					ScopesArguments scopeArguments = new ScopesArguments();
					scopeArguments.setFrameId(stackFrame.getId());
					ScopesResponse scopesResponse = server.scopes(scopeArguments).get();
					Scope[] scopes = scopesResponse.getScopes();
					allData.addScopes(Arrays.asList(scopes));
					for(Scope scope : scopes) {
						VariablesArguments varArguments = new VariablesArguments();
						int scopeVarReference = scope.getVariablesReference();
						varArguments.setVariablesReference(scopeVarReference);
						//System.out.println("scopeVarReference " + scopeVarReference);
						VariablesResponse variablesResponse = server.variables(varArguments).get();
						//System.out.println("variables: " + variablesResponse.getVariables().length);
						allData.addVariables(Arrays.asList(variablesResponse.getVariables()));
					}
				}
			}
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		
		
	}

	public List<StoppedEventArguments> getStoppedEventArguments() {
		return stoppedEventArguments;
	}

	public List<StackAndVarOnStopEvent> getAllStacksAndVars() {
		return wholeStackAndVars;
	}

}
