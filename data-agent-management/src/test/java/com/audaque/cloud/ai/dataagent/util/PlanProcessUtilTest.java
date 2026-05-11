/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.audaque.cloud.ai.dataagent.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanProcessUtilTest {

	@Test
	@DisplayName("Truncated mid-string should be repaired by closing the string and objects")
	void repairTruncatedJson_midString() {
		String truncated = "{\"thought_process\":\"分析需求\",\"execution_plan\":[{\"step\":1,\"tool_to_use\":\"SQL_GENERATE_NODE\",\"tool_parameters\":{\"instruction\":\"查询2025年销售数据，按产品分组";
		String repaired = PlanProcessUtil.repairTruncatedJson(truncated);

		// Should be valid JSON after repair
		assertDoesNotThrow(() -> com.fasterxml.jackson.databind.ObjectMapper.class.getDeclaredConstructor()
			.newInstance().readTree(repaired));
		assertTrue(repaired.endsWith("}]}") || repaired.endsWith("\"}]"));
	}

	@Test
	@DisplayName("Complete JSON should not be modified")
	void repairTruncatedJson_complete() {
		String complete = "{\"key\":\"value\"}";
		String repaired = PlanProcessUtil.repairTruncatedJson(complete);
		assertEquals(complete, repaired);
	}

	@Test
	@DisplayName("Truncated array should be closed")
	void repairTruncatedJson_unclosedArray() {
		String truncated = "{\"items\":[1,2,3";
		String repaired = PlanProcessUtil.repairTruncatedJson(truncated);
		assertEquals("{\"items\":[1,2,3]}", repaired);
	}

	@Test
	@DisplayName("Truncated nested object should be closed")
	void repairTruncatedJson_unclosedNestedObject() {
		String truncated = "{\"outer\":{\"inner\":{\"key\":\"val";
		String repaired = PlanProcessUtil.repairTruncatedJson(truncated);
		assertEquals("{\"outer\":{\"inner\":{\"key\":\"val\"}}}", repaired);
	}

	@Test
	@DisplayName("Empty string should return empty")
	void repairTruncatedJson_empty() {
		assertEquals("", PlanProcessUtil.repairTruncatedJson(""));
	}

	@Test
	@DisplayName("Null should return null")
	void repairTruncatedJson_null() {
		assertNull(PlanProcessUtil.repairTruncatedJson(null));
	}

	@Test
	@DisplayName("Truncated with escaped quotes should be handled")
	void repairTruncatedJson_escapedQuotes() {
		String truncated = "{\"key\":\"value with \\\"quotes\\\" and more text";
		String repaired = PlanProcessUtil.repairTruncatedJson(truncated);
		// Should close the string and object
		assertTrue(repaired.endsWith("}"));
	}

	@Test
	@DisplayName("Truncated after comma in object should close properly")
	void repairTruncatedJson_afterComma() {
		String truncated = "{\"a\":\"hello\",\"b\":\"world";
		String repaired = PlanProcessUtil.repairTruncatedJson(truncated);
		assertEquals("{\"a\":\"hello\",\"b\":\"world\"}", repaired);
	}

}
