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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EventMessageTest {

	private final EventMessage eventMessage = new EventMessage();

	@Test
	void testIsStubEndpointThrowsUnsupportedOperationException() {
		assertThatThrownBy(() -> eventMessage.isStubEndpoint()).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void testGetBreadcrumbIdThrowsUnsupportedOperationException() {
		assertThatThrownBy(() -> eventMessage.getBreadcrumbId()).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void testGetFromRouteIdThrowsUnsupportedOperationException() {
		assertThatThrownBy(() -> eventMessage.getFromRouteId()).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void testGetToNodeParentIdThrowsUnsupportedOperationException() {
		assertThatThrownBy(() -> eventMessage.getToNodeParentId()).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void testGetToNodeParentWhenIdThrowsUnsupportedOperationException() {
		assertThatThrownBy(() -> eventMessage.getToNodeParentWhenId()).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void testGetToNodeParentWhenLabelThrowsUnsupportedOperationException() {
		assertThatThrownBy(() -> eventMessage.getToNodeParentWhenLabel()).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void testGetToNodeShortNameThrowsUnsupportedOperationException() {
		assertThatThrownBy(() -> eventMessage.getToNodeShortName()).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void testGetToNodeLabelThrowsUnsupportedOperationException() {
		assertThatThrownBy(() -> eventMessage.getToNodeLabel()).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void testGetToNodeLevelThrowsUnsupportedOperationException() {
		assertThatThrownBy(() -> eventMessage.getToNodeLevel()).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void testGetCorrelationExchangeIdThrowsUnsupportedOperationException() {
		assertThatThrownBy(() -> eventMessage.getCorrelationExchangeId()).isInstanceOf(UnsupportedOperationException.class);
	}
}
