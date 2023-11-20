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
package com.github.cameltooling.dap.internal.types;

import java.io.Serializable;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "message")
public class Message implements Serializable {
	
	private static final long serialVersionUID = 8675590428386259917L;
	
	private String exchangeId;
	private List<Header> headers;
	private String body;
	private List<ExchangeProperty> exchangeProperties;
	
	@XmlAttribute(name = "exchangeId")
	public String getExchangeId() {
		return this.exchangeId;
	}

	public void setExchangeId(String exchangeId) {
		this.exchangeId = exchangeId;
	}
	
	@XmlElementWrapper(name = "headers")
	@XmlElement(name = "header")
	public List<Header> getHeaders() {
		return this.headers;
	}

	public void setHeaders(List<Header> headers) {
		this.headers = headers;
	}
	
	@XmlElement(name = "body")
	public String getBody() {
		return this.body;
	}

	public void setBody(String body) {
		this.body = body;
	}
	
	@XmlElementWrapper(name = "exchangeProperties")
	@XmlElement(name = "exchangeProperty")
	public List<ExchangeProperty> getExchangeProperties() {
		return exchangeProperties;
	}
	public void setExchangeProperties(List<ExchangeProperty> exchangeProperties) {
		this.exchangeProperties = exchangeProperties;
	}
}
