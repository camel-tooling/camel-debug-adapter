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

import java.io.StringReader;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnmarshallerEventMessage {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(UnmarshallerEventMessage.class);

	public EventMessage getUnmarshalledEventMessage(String xmlDump) {
		try {
			JAXBContext context = JAXBContext.newInstance(EventMessage.class, Message.class, Header.class, ExchangeProperty.class);
			Unmarshaller um = context.createUnmarshaller();
			return (EventMessage)um.unmarshal(new StringReader(xmlDump));
		} catch (JAXBException ex) {
			LOGGER.error("Cannot parse message from debugger", ex);
		}
		return null;
	}
}
