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
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.Variable;

import com.github.cameltooling.dap.internal.IdUtils;
import com.github.cameltooling.dap.internal.types.EventMessage;

public class CamelThread extends Thread {

	private final String breakpointId;
	private final CamelStackFrame stackFrame;
	private final EventMessage eventMessage;

	public CamelThread(int threadId, String breakpointId, EventMessage eventMessage, CamelBreakpoint camelBreakpoint) {
		setId(threadId);
		setName(eventMessage.getExchangeId());
		this.breakpointId = breakpointId;
		this.eventMessage = eventMessage;
		// TODO: provide a better hashcode for stackframe containing the camelcontext
		// too
		int frameId = IdUtils.getPositiveIntFromHashCode(threadId + breakpointId.hashCode());
		Source source = null;
		Integer line = null;
		if (camelBreakpoint != null) {
			source = camelBreakpoint.getSource();
			line = camelBreakpoint.getLine();
		} else {
			// TODO: the breakpoint was surely not set through UI, must search the source
		}
		this.stackFrame = new CamelStackFrame(frameId, breakpointId, source, line);
	}

	@Override
	public boolean equals(Object obj) {
		if (!super.equals(obj)) {
			return false;
		}
		CamelThread that = (CamelThread) obj;
		return Objects.equals(this.breakpointId, that.breakpointId)
				&& Objects.equals(this.eventMessage, that.eventMessage)
				&& Objects.equals(this.stackFrame, that.stackFrame);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), breakpointId, eventMessage, stackFrame);
	}

	public String getBreakPointId() {
		return breakpointId;
	}

	public CamelStackFrame getStackFrame() {
		return stackFrame;
	}

	public String getExchangeId() {
		return eventMessage != null ? eventMessage.getExchangeId() : null;
	}

	public Set<Variable> createVariables(int variablesReference, ManagedBacklogDebuggerMBean debugger) {
		return stackFrame.createVariables(variablesReference, debugger);
	}

}
