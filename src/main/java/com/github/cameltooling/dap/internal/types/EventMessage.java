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

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.camel.spi.BacklogTracerEventMessage;

@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name = "backlogTracerEventMessage")
public class EventMessage implements BacklogTracerEventMessage {
	
	private static final long serialVersionUID = 2559418843237642923L;
	private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern(TIMESTAMP_FORMAT, Locale.ROOT);
	
	private long uid;
	private boolean first;
	private boolean last;
	private boolean rest;
	private boolean template;
	@XmlElement(name = "timestamp")
	@XmlJavaTypeAdapter(TimestampAdapter.class)
	private Long timestamp;
	private long elapsed;
	private boolean done;
	private boolean failed;
	private String location;
	private String processingThreadName;
	private String routeId;
	private String fromRouteId;
	private String toNode;
	private String toNodeParentId;
	private String toNodeParentWhenId;
	private String toNodeParentWhenLabel;
	private String toNodeShortName;
	private String toNodeLabel;
	private int toNodeLevel;
	private String exchangeId;
	private String correlationExchangeId;
	private Message message;
	private String endpointUri;
	private boolean remoteEndpoint;
	private String endpointServiceUrl;
	private String endpointServiceProtocol;
	private Map<String, String> endpointServiceMetadata;
	private Throwable exception;
	
	@XmlElement(name = "uid")
	public long getUid() {
		return uid;
	}
	public void setUid(long uid) {
		this.uid = uid;
	}
	
	@XmlElement(name = "first")
	public boolean isFirst() {
		return first;
	}
	public void setFirst(boolean first) {
		this.first = first;
	}
	
	@XmlElement(name = "last")
	public boolean isLast() {
		return last;
	}
	public void setLast(boolean last) {
		this.last = last;
	}
	
	@XmlElement(name = "rest")
	public boolean isRest() {
		return rest;
	}
	public void setRest(boolean rest) {
		this.rest = rest;
	}
	
	@XmlElement(name = "template")
	public boolean isTemplate() {
		return template;
	}
	public void setTemplate(boolean template) {
		this.template = template;
	}
	
	@XmlTransient
	public long getTimestamp() {
		return timestamp != null ? timestamp.longValue() : 0L;
	}
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	
	@XmlElement(name = "elapsed")
	public long getElapsed() {
		return elapsed;
	}
	public void setElapsed(long elapsed) {
		this.elapsed = elapsed;
	}
	
	@XmlElement(name = "done")
	public boolean isDone() {
		return done;
	}
	public void setDone(boolean done) {
		this.done = done;
	}
	
	@XmlElement(name = "failed")
	public boolean isFailed() {
		return failed;
	}
	public void setFailed(boolean failed) {
		this.failed = failed;
	}
	
	@XmlElement(name = "location")
	public String getLocation() {
		return location;
	}
	public void setLocation(String location) {
		this.location = location;
	}
	
	@XmlElement(name = "threadName")
	public String getProcessingThreadName() {
		return processingThreadName;
	}
	public void setProcessingThreadName(String processingThreadName) {
		this.processingThreadName = processingThreadName;
	}
	
	@XmlElement(name = "routeId")
	public String getRouteId() {
		return routeId;
	}
	public void setRouteId(String routeId) {
		this.routeId = routeId;
	}
	
	@XmlElement(name = "fromRouteId")
	public String getFromRouteId() {
		return fromRouteId;
	}
	public void setFromRouteId(String fromRouteId) {
		this.fromRouteId = fromRouteId;
	}
	
	@XmlElement(name = "toNode")
	public String getToNode() {
		return toNode;
	}
	public void setToNode(String toNode) {
		this.toNode = toNode;
	}
	
	@XmlElement(name = "toNodeParentId")
	public String getToNodeParentId() {
		return toNodeParentId;
	}
	public void setToNodeParentId(String toNodeParentId) {
		this.toNodeParentId = toNodeParentId;
	}
	
	@XmlElement(name = "toNodeParentWhenId")
	public String getToNodeParentWhenId() {
		return toNodeParentWhenId;
	}
	public void setToNodeParentWhenId(String toNodeParentWhenId) {
		this.toNodeParentWhenId = toNodeParentWhenId;
	}
	
	@XmlElement(name = "toNodeParentWhenLabel")
	public String getToNodeParentWhenLabel() {
		return toNodeParentWhenLabel;
	}
	public void setToNodeParentWhenLabel(String toNodeParentWhenLabel) {
		this.toNodeParentWhenLabel = toNodeParentWhenLabel;
	}
	
	@XmlElement(name = "toNodeShortName")
	public String getToNodeShortName() {
		return toNodeShortName;
	}
	public void setToNodeShortName(String toNodeShortName) {
		this.toNodeShortName = toNodeShortName;
	}
	
	@XmlElement(name = "toNodeLabel")
	public String getToNodeLabel() {
		return toNodeLabel;
	}
	public void setToNodeLabel(String toNodeLabel) {
		this.toNodeLabel = toNodeLabel;
	}
	
	@XmlElement(name = "toNodeLevel")
	public int getToNodeLevel() {
		return toNodeLevel;
	}
	public void setToNodeLevel(int toNodeLevel) {
		this.toNodeLevel = toNodeLevel;
	}
	
	@XmlElement(name = "exchangeId")
	public String getExchangeId() {
		return exchangeId;
	}
	public void setExchangeId(String exchangeId) {
		this.exchangeId = exchangeId;
	}
	
	@XmlElement(name = "correlationExchangeId")
	public String getCorrelationExchangeId() {
		return correlationExchangeId;
	}
	public void setCorrelationExchangeId(String correlationExchangeId) {
		this.correlationExchangeId = correlationExchangeId;
	}
	
	@XmlElement(name = "message")
	public Message getMessage() {
		return message;
	}
	public void setMessage(Message message) {
		this.message = message;
	}
	
	public String getMessageAsXml() {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	public String toXml(int indent) {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	public String getMessageAsJSon() {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	public String toJSon(int indent) {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	public Map<String, Object> asJSon() {
		throw new UnsupportedOperationException("This class is used only to read message sent from Camel server through JMX");
	}
	public boolean hasException() {
		return exception != null;
	}
	public String getExceptionAsXml() {
		return null;
	}
	public String getExceptionAsJSon() {
		return null;
	}
	public String getEndpointUri() {
		return endpointUri;
	}
	public void setEndpointUri(String endpointUri) {
		this.endpointUri = endpointUri;
	}

	public boolean isRemoteEndpoint() {
		return remoteEndpoint;
	}
	public void setRemoteEndpoint(boolean remoteEndpoint) {
		this.remoteEndpoint = remoteEndpoint;
	}
	public String getEndpointServiceUrl() {
		return endpointServiceUrl;
	}
	public void setEndpointServiceUrl(String endpointServiceUrl) {
		this.endpointServiceUrl = endpointServiceUrl;
	}
	public String getEndpointServiceProtocol() {
		return endpointServiceProtocol;
	}
	public void setEndpointServiceProtocol(String endpointServiceProtocol) {
		this.endpointServiceProtocol = endpointServiceProtocol;
	}
	public Map<String, String> getEndpointServiceMetadata() {
		return endpointServiceMetadata;
	}
	public void setEndpointServiceMetadata(Map<String, String> endpointServiceMetadata) {
		this.endpointServiceMetadata = endpointServiceMetadata;
	}
	public void setException(Throwable cause) {
		this.exception = cause;
	}

	private static final class TimestampAdapter extends XmlAdapter<String, Long> {

		@Override
		public Long unmarshal(String value) {
			if (value == null || value.isBlank()) {
				return 0L;
			}
			try {
				return Long.valueOf(value);
			} catch (NumberFormatException ex) {
				try {
					return OffsetDateTime.parse(value, TIMESTAMP_FORMATTER).toInstant().toEpochMilli();
				} catch (DateTimeParseException e) {
					throw new IllegalArgumentException("Cannot parse backlog tracer timestamp: " + value, e);
				}
			}
		}

		@Override
		public String marshal(Long value) {
			if (value == null) {
				return null;
			}
			return TIMESTAMP_FORMATTER.format(OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(value), java.time.ZoneOffset.UTC));
		}
	}

}
