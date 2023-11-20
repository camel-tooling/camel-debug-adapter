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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnmarshallerEventMessageTest {

	@Test
	void testGetUnmarshalledEventMessage() {
		// it is the event message when calling ManagedBacklogDebuggerMBean.dumpTracedMessagesAsXml
		String eventMessageGeneratedWithCamel4_2 = """
<backlogTracerEventMessage>
  <uid>1</uid>
  <first>false</first>
  <last>false</last>
  <rest>false</rest>
  <template>false</template>
  <timestamp>2023-11-20T14:20:26.971+0100</timestamp>
  <elapsed>1077</elapsed>
  <threadName>Camel (camel-1) thread #2 - ProducerTemplate</threadName>
  <done>false</done>
  <failed>false</failed>
  <location>basic.yaml:23</location>
  <routeId>a-route-id</routeId>
  <toNode>testBasicFlow-log-id</toNode>
  <exchangeId>7F4C7BF7F3898E7-0000000000000000</exchangeId>
  <message exchangeId="7F4C7BF7F3898E7-0000000000000000" exchangePattern="InOnly" exchangeType="org.apache.camel.support.DefaultExchange" messageType="org.apache.camel.support.DefaultMessage">
    <exchangeProperties>
      <exchangeProperty key="CamelToEndpoint" type="java.lang.String">direct://testSetBreakpoint</exchangeProperty>
      <exchangeProperty key="property1" type="java.lang.String">value of property 1</exchangeProperty>
      <exchangeProperty key="property2" type="java.lang.String">value of property 2</exchangeProperty>
    </exchangeProperties>
    <headers>
      <header key="header1" type="java.lang.String">value of header 1</header>
      <header key="header2" type="java.lang.String">value of header 2</header>
    </headers>
    <body type="java.lang.String">a body for test</body>
  </message>
</backlogTracerEventMessage>
""";
		EventMessage message = new UnmarshallerEventMessage().getUnmarshalledEventMessage(eventMessageGeneratedWithCamel4_2);
		assertThat(message).isNotNull();
		assertThat(message.getUid()).isEqualTo(1);
		assertThat(message.getMessage().getHeaders()).hasSize(2);
		assertThat(message.getMessage().getExchangeProperties()).hasSize(3);
		assertThat(message.getMessage().getBody()).isEqualTo("a body for test");
	}

}
