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

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.api.management.mbean.BacklogTracerEventMessage;

@XmlRootElement(name = "backlogTracerEventMessage")
public class EventMessage implements BacklogTracerEventMessage {
	
	private static final long serialVersionUID = 2559418843237642923L;
	
	private long uid;
	//TODO: use a real date for the timestamp
	//@XmlJavaTypeAdapter(DateAdapter.class)
	private long timestamp;
	private String routeId;
	private String toNode;
	private String exchangeId;
	private Message message;
	private List<ExchangeProperty> exchangeProperties;
	
	@XmlElement(name = "uid")
	public long getUid() {
		return uid;
	}
	public void setUid(long uid) {
		this.uid = uid;
	}
	
	@XmlElement(name = "timestamp")
	public long getTimestamp() {
		return timestamp;
	}
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	
	@XmlElement(name = "routeId")
	public String getRouteId() {
		return routeId;
	}
	public void setRouteId(String routeId) {
		this.routeId = routeId;
	}
	
	@XmlElement(name = "toNode")
	public String getToNode() {
		return toNode;
	}
	public void setToNode(String toNode) {
		this.toNode = toNode;
	}
	
	@XmlElement(name = "exchangeId")
	public String getExchangeId() {
		return exchangeId;
	}
	public void setExchangeId(String exchangeId) {
		this.exchangeId = exchangeId;
	}
	
	@XmlElement(name = "message")
	public Message getMessage() {
		return message;
	}
	public void setMessage(Message message) {
		this.message = message;
	}
	
	@XmlElementWrapper(name = "exchangeProperties")
	@XmlElement(name = "exchangeProperty")
	public List<ExchangeProperty> getExchangeProperties() {
		return exchangeProperties;
	}
	public void setExchangeProperties(List<ExchangeProperty> exchangeProperties) {
		this.exchangeProperties = exchangeProperties;
	}
	
	@Override
	public String getMessageAsXml() {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	@Override
	public String toXml(int indent) {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}

}
