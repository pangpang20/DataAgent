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
package com.audaque.cloud.ai.dataagent.node;

import com.audaque.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.audaque.cloud.ai.dataagent.service.code.CodePoolExecutorService;
import com.audaque.cloud.ai.dataagent.service.file.FileStorageService;
import com.audaque.cloud.ai.dataagent.service.llm.LlmService;
import com.audaque.cloud.ai.dataagent.util.JsonParseUtil;
import com.audaque.cloud.ai.dataagent.workflow.node.PythonExecuteNode;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Base64;
import java.util.Map;

import static com.audaque.cloud.ai.dataagent.constant.Constant.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PythonExecuteNodeTest {

	private PythonExecuteNode node;

	private CodePoolExecutorService codePoolExecutor;

	private FileStorageService fileStorageService;

	private OverAllState state;

	@BeforeEach
	void setUp() {
		codePoolExecutor = mock(CodePoolExecutorService.class);
		fileStorageService = mock(FileStorageService.class);
		CodeExecutorProperties properties = new CodeExecutorProperties();
		properties.setPythonMaxTriesCount(5);

		JsonParseUtil jsonParseUtil = new JsonParseUtil(mock(LlmService.class));

		node = new PythonExecuteNode(codePoolExecutor, jsonParseUtil, properties, fileStorageService);

		state = new OverAllState();
		state.registerKeyAndStrategy(PYTHON_GENERATE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(SQL_RESULT_LIST_MEMORY, new ReplaceStrategy());
		state.registerKeyAndStrategy(PYTHON_TRIES_COUNT, new ReplaceStrategy());
		state.registerKeyAndStrategy(PYTHON_EXECUTE_NODE_OUTPUT, new ReplaceStrategy());
		state.registerKeyAndStrategy(PYTHON_IS_SUCCESS, new ReplaceStrategy());
		state.registerKeyAndStrategy(PYTHON_FALLBACK_MODE, new ReplaceStrategy());
	}

	@Test
	@DisplayName("Chart image should be persisted and replaced with URL in output")
	@SuppressWarnings("unchecked")
	void chartImage_shouldBePersistedAndReplacedWithUrl() throws Exception {
		// Arrange
		byte[] fakePngBytes = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
		String base64Image = Base64.getEncoder().encodeToString(fakePngBytes);
		String pythonOutput = String.format("{\"chart_image\":\"%s\",\"summary\":\"test summary\"}", base64Image);

		state.updateState(Map.of(
				PYTHON_GENERATE_NODE_OUTPUT, "import matplotlib; matplotlib.use('Agg')",
				PYTHON_TRIES_COUNT, 0));

		CodePoolExecutorService.TaskResponse successResponse = new CodePoolExecutorService.TaskResponse(
				true, false, pythonOutput, "", null);
		when(codePoolExecutor.runTask(any())).thenReturn(successResponse);

		String expectedUrl = "/api/upload/data-agent/charts/chart_test.png";
		when(fileStorageService.storeFile(any(byte[].class), anyString(), eq("charts")))
				.thenReturn("data-agent/charts/chart_test.png");
		when(fileStorageService.getFileUrl("data-agent/charts/chart_test.png"))
				.thenReturn(expectedUrl);

		// Act
		Map<String, Object> result = node.apply(state);

		// Get the Flux and subscribe to trigger execution
		Flux<GraphResponse<StreamingOutput>> flux = (Flux<GraphResponse<StreamingOutput>>) result
				.get(PYTHON_EXECUTE_NODE_OUTPUT);

		// Collect the done response (last element)
		GraphResponse<StreamingOutput> doneResponse = flux
				.filter(GraphResponse::isDone)
				.blockLast(java.time.Duration.ofSeconds(5));

		assertNotNull(doneResponse, "Should have a DONE response");

		// Get the state map from the done response
		Map<String, Object> stateData = (Map<String, Object>) doneResponse.resultValue().orElse(null);
		assertNotNull(stateData, "Done response should have result data");

		// Verify file storage was called
		verify(fileStorageService).storeFile(any(byte[].class), anyString(), eq("charts"));
		verify(fileStorageService).getFileUrl("data-agent/charts/chart_test.png");

		// Verify the output JSON contains URL instead of base64
		String outputJson = (String) stateData.get(PYTHON_EXECUTE_NODE_OUTPUT);
		assertNotNull(outputJson);
		assertTrue(outputJson.contains(expectedUrl), "Output should contain the URL");
		assertFalse(outputJson.contains(base64Image), "Output should not contain raw base64");
		assertTrue(outputJson.contains("\"summary\":\"test summary\""), "Summary should be preserved");
		assertTrue((Boolean) stateData.get(PYTHON_IS_SUCCESS), "Should be successful");
	}

	@Test
	@DisplayName("Chart image fallback to base64 when storage fails")
	@SuppressWarnings("unchecked")
	void chartImage_shouldFallbackToBase64_whenStorageFails() throws Exception {
		// Arrange
		byte[] fakePngBytes = { (byte) 0x89, 0x50, 0x4E, 0x47 };
		String base64Image = Base64.getEncoder().encodeToString(fakePngBytes);
		String pythonOutput = String.format("{\"chart_image\":\"%s\",\"summary\":\"test\"}", base64Image);

		state.updateState(Map.of(
				PYTHON_GENERATE_NODE_OUTPUT, "print('hello')",
				PYTHON_TRIES_COUNT, 0));

		CodePoolExecutorService.TaskResponse successResponse = new CodePoolExecutorService.TaskResponse(
				true, false, pythonOutput, "", null);
		when(codePoolExecutor.runTask(any())).thenReturn(successResponse);

		// Storage throws exception
		when(fileStorageService.storeFile(any(byte[].class), anyString(), anyString()))
				.thenThrow(new RuntimeException("Disk full"));

		// Act
		Map<String, Object> result = node.apply(state);

		Flux<GraphResponse<StreamingOutput>> flux = (Flux<GraphResponse<StreamingOutput>>) result
				.get(PYTHON_EXECUTE_NODE_OUTPUT);

		GraphResponse<StreamingOutput> doneResponse = flux
				.filter(GraphResponse::isDone)
				.blockLast(java.time.Duration.ofSeconds(5));

		assertNotNull(doneResponse);

		Map<String, Object> stateData = (Map<String, Object>) doneResponse.resultValue().orElse(null);
		assertNotNull(stateData);

		// Output should still contain the original base64 (fallback)
		String outputJson = (String) stateData.get(PYTHON_EXECUTE_NODE_OUTPUT);
		assertNotNull(outputJson);
		assertTrue(outputJson.contains(base64Image), "Should fallback to base64 when storage fails");
		assertTrue((Boolean) stateData.get(PYTHON_IS_SUCCESS), "Should still be successful");
	}

	@Test
	@DisplayName("No chart image should not trigger storage")
	@SuppressWarnings("unchecked")
	void noChartImage_shouldNotTriggerStorage() throws Exception {
		// Arrange
		String pythonOutput = "{\"summary\":\"analysis complete\"}";

		state.updateState(Map.of(
				PYTHON_GENERATE_NODE_OUTPUT, "print('hello')",
				PYTHON_TRIES_COUNT, 0));

		CodePoolExecutorService.TaskResponse successResponse = new CodePoolExecutorService.TaskResponse(
				true, false, pythonOutput, "", null);
		when(codePoolExecutor.runTask(any())).thenReturn(successResponse);

		// Act
		Map<String, Object> result = node.apply(state);

		Flux<GraphResponse<StreamingOutput>> flux = (Flux<GraphResponse<StreamingOutput>>) result
				.get(PYTHON_EXECUTE_NODE_OUTPUT);

		GraphResponse<StreamingOutput> doneResponse = flux
				.filter(GraphResponse::isDone)
				.blockLast(java.time.Duration.ofSeconds(5));

		assertNotNull(doneResponse);

		// Verify file storage was NOT called
		verify(fileStorageService, never()).storeFile(any(byte[].class), anyString(), anyString());
	}

}
