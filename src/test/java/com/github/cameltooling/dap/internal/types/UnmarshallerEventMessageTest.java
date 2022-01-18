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
		String eventMessageGeneratedWithCamel3_15 = "<backlogTracerEventMessage>\\n  <uid>1</uid>\\n  <timestamp>2022-01-17T14:33:04.001+0100</timestamp>\\n  <routeId>foo</routeId>\\n  <toNode>bean1</toNode>\\n  <exchangeId>8740E46AD27758F-000000000000000C</exchangeId>\\n  <message exchangeId=\"8740E46AD27758F-000000000000000C\">\\n    <headers>\\n      <header key=\"CamelMessageTimestamp\" type=\"java.lang.Long\">1642426384000</header>\\n      <header key=\"calendar\"></header>\\n      <header key=\"fireTime\" type=\"java.util.Date\">Mon Jan 17 14:33:04 CET 2022</header>\\n      <header key=\"jobDetail\" type=\"org.quartz.impl.JobDetailImpl\">JobDetail 'Camel_MyCoolCamel.foo':  jobClass: 'org.apache.camel.component.quartz.CamelJob concurrentExectionDisallowed: false persistJobDataAfterExecution: false isDurable: false requestsRecovers: false</header>\\n      <header key=\"jobInstance\" type=\"org.apache.camel.component.quartz.CamelJob\">org.apache.camel.component.quartz.CamelJob@7e21921c</header>\\n      <header key=\"jobRunTime\" type=\"java.lang.Long\">-1</header>\\n      <header key=\"mergedJobDataMap\" type=\"org.quartz.JobDataMap\">org.quartz.JobDataMap@e5e7e077</header>\\n      <header key=\"nextFireTime\" type=\"java.util.Date\">Mon Jan 17 14:33:06 CET 2022</header>\\n      <header key=\"previousFireTime\" type=\"java.util.Date\">Mon Jan 17 14:33:02 CET 2022</header>\\n      <header key=\"refireCount\" type=\"java.lang.Integer\">0</header>\\n      <header key=\"result\"></header>\\n      <header key=\"scheduledFireTime\" type=\"java.util.Date\">Mon Jan 17 14:33:04 CET 2022</header>\\n      <header key=\"scheduler\" type=\"org.quartz.impl.StdScheduler\">org.quartz.impl.StdScheduler@2b09f4a4</header>\\n      <header key=\"trigger\" type=\"org.quartz.impl.triggers.CronTriggerImpl\">Trigger 'Camel_MyCoolCamel.foo':  triggerClass: 'org.quartz.impl.triggers.CronTriggerImpl calendar: 'null' misfireInstruction: 1 nextFireTime: Mon Jan 17 14:33:06 CET 2022</header>\\n      <header key=\"triggerGroup\" type=\"java.lang.String\">Camel_MyCoolCamel</header>\\n      <header key=\"triggerName\" type=\"java.lang.String\">foo</header>\\n    </headers>\\n    <body>[Body is null]</body>\\n  </message>\\n  <exchangeProperties>\\n    <exchangeProperty name=\"CamelMessageHistory\" type=\"java.util.concurrent.CopyOnWriteArrayList\">[DefaultMessageHistory[routeId=foo, node=bean1]]</exchangeProperty>\\n  </exchangeProperties>\\n</backlogTracerEventMessage>	";
		EventMessage message = new UnmarshallerEventMessage().getUnmarshalledEventMessage(eventMessageGeneratedWithCamel3_15);
		assertThat(message).isNotNull();
		assertThat(message.getUid()).isEqualTo(1);
		assertThat(message.getMessage().getHeaders()).hasSize(16);
		assertThat(message.getExchangeProperties()).hasSize(1);
		assertThat(message.getMessage().getBody()).isEqualTo("[Body is null]");
	}

}
