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

import java.util.Map;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.BacklogTracerEventMessage;


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
	
	@Override
	public String getMessageAsXml() {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	@Override
	public String toXml(int indent) {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	@Override
	public boolean isRest() {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	@Override
	public boolean isTemplate() {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	@Override
	public String getMessageAsJSon() {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	@Override
	public String toJSon(int indent) {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	@Override
	public Map<String, Object> asJSon() {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	@Override
	public boolean isFirst() {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	@Override
	public boolean isLast() {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	@Override
	public String getLocation() {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	@Override
	public String getProcessingThreadName() {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	@Override
	public long getElapsed() {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	@Override
	public boolean isDone() {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	@Override
	public boolean isFailed() {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	@Override
	public boolean hasException() {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	@Override
	public String getExceptionAsXml() {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	@Override
	public String getExceptionAsJSon() {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	@Override
	public String getEndpointUri() {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	
	@Override
	public void setExceptionAsXml(String exceptionAsXml) {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	@Override
	public void setExceptionAsJSon(String exceptionAsJSon) {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}

}
