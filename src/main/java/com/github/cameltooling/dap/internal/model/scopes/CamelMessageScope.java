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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.api.management.mbean.ManagedBacklogDebuggerMBean;
import org.eclipse.lsp4j.debug.SetVariableArguments;
import org.eclipse.lsp4j.debug.SetVariableResponse;
import org.eclipse.lsp4j.debug.Variable;

import com.github.cameltooling.dap.internal.IdUtils;
import com.github.cameltooling.dap.internal.model.CamelScope;
import com.github.cameltooling.dap.internal.model.CamelStackFrame;
import com.github.cameltooling.dap.internal.model.variables.message.MessageBodyCamelVariable;
import com.github.cameltooling.dap.internal.types.EventMessage;
import com.github.cameltooling.dap.internal.types.Header;
import com.github.cameltooling.dap.internal.types.UnmarshallerEventMessage;

public class CamelMessageScope extends CamelScope {
	
	public static final String NAME = "Message";
	private Map<Integer, List<Header>> headersVariableReferenceToHeaders = new HashMap<>();
	private MessageBodyCamelVariable messageBody;

	public CamelMessageScope(CamelStackFrame stackframe) {
		super(NAME, stackframe.getName(), IdUtils.getPositiveIntFromHashCode((stackframe.getId()+"@Message@" + stackframe.getName()).hashCode()));
	}
	
	@Override
	public Set<Variable> createVariables(int variablesReference, ManagedBacklogDebuggerMBean debugger) {
		Set<Variable> variables = new HashSet<>();
		if (variablesReference == getVariablesReference()) {
			String xml = debugger.dumpTracedMessagesAsXml(getBreakpointId(), true);
			EventMessage eventMessage = new UnmarshallerEventMessage().getUnmarshalledEventMessage(xml);
			if(eventMessage != null) {
				variables.add(createVariable("Exchange ID", eventMessage.getExchangeId()));
				variables.add(createVariable("UID", Long.toString(eventMessage.getUid())));
				messageBody = new MessageBodyCamelVariable(getBreakpointId(), eventMessage.getMessage().getBody());
				variables.add(messageBody);
				Variable headersVariable = new Variable();
				headersVariable.setName("Headers");
				headersVariable.setValue("");
				int headerVarRefId = IdUtils.getPositiveIntFromHashCode((variablesReference+"@Headers@").hashCode());
				headersVariableReferenceToHeaders.put(headerVarRefId, eventMessage.getMessage().getHeaders());
				headersVariable.setVariablesReference(headerVarRefId);
				variables.add(headersVariable);
			}
		} else {
			List<Header> headers = headersVariableReferenceToHeaders.get(variablesReference);
			if(headers != null) {
				for (Header header : headers) {
					variables.add(createVariable(header.getKey(), header.getValue()));
				}
			}
		}
		return variables;
	}
	
	@Override
	public SetVariableResponse setVariableIfInScope(SetVariableArguments args, ManagedBacklogDebuggerMBean debugger) {
		if (getVariablesReference() == args.getVariablesReference()) {
			if (args.getName().equals(messageBody.getName())) {
				messageBody.updateValue(debugger, args.getValue());
				SetVariableResponse response = new SetVariableResponse();
				response.setValue(args.getValue());
				return response;
			} else {
				throw new UnsupportedOperationException("Not yet supported");
			}
		}
		return null;
	}

}
