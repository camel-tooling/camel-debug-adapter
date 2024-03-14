package com.github.cameltooling.dap.internal.model.variables.message;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.lsp4j.debug.Variable;

import com.github.cameltooling.dap.internal.IdUtils;
import com.github.cameltooling.dap.internal.types.ExchangeVariable;

public class MessageExchangeVariablesVariable extends Variable {

	private final List<ExchangeVariable> exchangeVariables;

	public MessageExchangeVariablesVariable(int parentVariablesReference, List<ExchangeVariable> exchangeVariables) {
		this.exchangeVariables = exchangeVariables;
		setName("Variables");
		setValue("");
		int headerVarRefId = IdUtils.getPositiveIntFromHashCode((parentVariablesReference+"@ExchangeVariables@").hashCode());
		setVariablesReference(headerVarRefId);
	}

	public Collection<Variable> createVariables() {
		Collection<Variable> variables = new ArrayList<>();
		if (exchangeVariables != null) {
			for (ExchangeVariable exchangeVariable : exchangeVariables) {
				variables.add(createVariable(exchangeVariable.getKey(), exchangeVariable.getValue()));
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

}
