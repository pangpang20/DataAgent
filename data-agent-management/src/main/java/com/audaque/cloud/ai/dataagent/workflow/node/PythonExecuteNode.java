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
package com.audaque.cloud.ai.dataagent.workflow.node;

import com.audaque.cloud.ai.dataagent.enums.TextType;
import com.audaque.cloud.ai.dataagent.util.JsonParseUtil;
import com.audaque.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.audaque.cloud.ai.dataagent.service.code.CodePoolExecutorService;
import com.audaque.cloud.ai.dataagent.util.ChatResponseUtil;
import com.audaque.cloud.ai.dataagent.util.FluxUtil;
import com.audaque.cloud.ai.dataagent.util.JsonUtil;
import com.audaque.cloud.ai.dataagent.util.StateUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.audaque.cloud.ai.dataagent.constant.Constant.*;

/**
 * 根据 SQL 查询结果生成 Python 代码，并运行 Python 代码获取运行结果。
 *
 */
@Slf4j
@Component
public class PythonExecuteNode implements NodeAction {

	private final CodePoolExecutorService codePoolExecutor;

	private final ObjectMapper objectMapper;

	private final JsonParseUtil jsonParseUtil;

	private final CodeExecutorProperties codeExecutorProperties;

	public PythonExecuteNode(CodePoolExecutorService codePoolExecutor, JsonParseUtil jsonParseUtil,
			CodeExecutorProperties codeExecutorProperties) {
		this.codePoolExecutor = codePoolExecutor;
		this.objectMapper = JsonUtil.getObjectMapper();
		this.jsonParseUtil = jsonParseUtil;
		this.codeExecutorProperties = codeExecutorProperties;
	}

	private static final int MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5MB 最大图片限制

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		try {
			// Get context
			String pythonCode = StateUtil.getStringValue(state, PYTHON_GENERATE_NODE_OUTPUT);
			List<Map<String, String>> sqlResults = StateUtil.hasValue(state, SQL_RESULT_LIST_MEMORY)
					? StateUtil.getListValue(state, SQL_RESULT_LIST_MEMORY) : new ArrayList<>();

			// 检查重试次数
			int triesCount = StateUtil.getObjectValue(state, PYTHON_TRIES_COUNT, Integer.class, 0);

			CodePoolExecutorService.TaskRequest taskRequest = new CodePoolExecutorService.TaskRequest(pythonCode,
					objectMapper.writeValueAsString(sqlResults), null);

			// Run Python code
			CodePoolExecutorService.TaskResponse taskResponse = this.codePoolExecutor.runTask(taskRequest);
			if (!taskResponse.isSuccess()) {
				String errorMsg = "Python Execute Failed!\nStdOut: " + taskResponse.stdOut() + "\nStdErr: "
						+ taskResponse.stdErr() + "\nExceptionMsg: " + taskResponse.exceptionMsg();
				log.error(errorMsg);

				// 检查是否超过最大重试次数
				if (triesCount >= codeExecutorProperties.getPythonMaxTriesCount()) {
					log.error("Python execution failed and exceeded maximum retry count (attempts: {}), activating fallback logic. Error: {}", triesCount, errorMsg);

					String fallbackOutput = "{}";

					Flux<ChatResponse> fallbackDisplayFlux = Flux.create(emitter -> {
						emitter.next(ChatResponseUtil.createResponse("开始执行 Python 代码..."));
						emitter.next(ChatResponseUtil.createResponse("Python 代码执行失败已超过最大重试次数，采用降级策略继续处理。"));
						emitter.complete();
					});

					Flux<GraphResponse<StreamingOutput>> fallbackGenerator = FluxUtil
						.createStreamingGeneratorWithMessages(this.getClass(), state,
								v -> Map.of(PYTHON_EXECUTE_NODE_OUTPUT, fallbackOutput, PYTHON_IS_SUCCESS, false,
										PYTHON_FALLBACK_MODE, true),
								fallbackDisplayFlux);

					return Map.of(PYTHON_EXECUTE_NODE_OUTPUT, fallbackGenerator);
				}

				throw new RuntimeException(errorMsg);
			}

			// Python 输出的 JSON 字符串可能有 Unicode 转义形式，需要解析回汉字
			String stdout = taskResponse.stdOut();
			Object value = jsonParseUtil.tryConvertToObject(stdout, Object.class);
			if (value != null) {
				stdout = objectMapper.writeValueAsString(value);
			}
			String finalStdout = stdout;

			log.info("Python Execute Success! StdOut: {}", finalStdout);

			// Check if result contains chart image (base64 encoded)
			JsonNode jsonNode = null;
			String chartImageBase64 = null;
			try {
				jsonNode = objectMapper.readTree(stdout);
				if (jsonNode.has("chart_image")) {
					chartImageBase64 = jsonNode.get("chart_image").asText();
					log.info("Chart image detected, length: {} chars", chartImageBase64.length());
				}
			} catch (Exception e) {
				log.warn("Failed to parse JSON for chart image detection: {}", e.getMessage());
			}
			String finalChartImageBase64 = chartImageBase64;

			// Create display flux for user experience only
			Flux<ChatResponse> displayFlux = Flux.create(emitter -> {
				emitter.next(ChatResponseUtil.createResponse("开始执行 Python 代码..."));

				// If chart image exists, display it first with HTML img tag
				if (finalChartImageBase64 != null && !finalChartImageBase64.isEmpty()) {
					// Security validation for base64 image
					if (isValidBase64Image(finalChartImageBase64)) {
						// Use HTML img tag directly instead of Markdown syntax for better browser compatibility
						String imgTag = String.format("<img src=\"data:image/png;base64,%s\" alt=\"chart\" style=\"max-width: 100%%; height: auto;\" />", finalChartImageBase64);
						emitter.next(ChatResponseUtil.createPureResponse(imgTag));
					} else {
						log.warn("Invalid or oversized chart image, skipping display");
					}
				}

				emitter.next(ChatResponseUtil.createResponse("标准输出："));
				emitter.next(ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign()));
				emitter.next(ChatResponseUtil.createResponse(finalStdout));
				emitter.next(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()));
				emitter.next(ChatResponseUtil.createResponse("Python 代码执行成功！"));
				emitter.complete();
			});

			// Create generator using utility class, returning pre-computed business logic
			// result
			Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(
					this.getClass(), state,
					v -> Map.of(PYTHON_EXECUTE_NODE_OUTPUT, finalStdout, PYTHON_IS_SUCCESS, true), displayFlux);

			return Map.of(PYTHON_EXECUTE_NODE_OUTPUT, generator);
		}
		catch (Exception e) {
			String errorMessage = e.getMessage();
			log.error("Python Execute Exception: {}", errorMessage);

			// Prepare error result
			Map<String, Object> errorResult = Map.of(PYTHON_EXECUTE_NODE_OUTPUT, errorMessage, PYTHON_IS_SUCCESS,
					false);

			// Create error display flux
			Flux<ChatResponse> errorDisplayFlux = Flux.create(emitter -> {
				emitter.next(ChatResponseUtil.createResponse("开始执行 Python 代码..."));
				emitter.next(ChatResponseUtil.createResponse("Python 代码执行失败：" + errorMessage));
				emitter.complete();
			});

			// Create error generator using utility class
			var generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(), state, v -> errorResult,
					errorDisplayFlux);

			return Map.of(PYTHON_EXECUTE_NODE_OUTPUT, generator);
		}
	}

	/**
	 * Validates base64 image string for security.
	 * Checks: 1) Valid base64 format, 2) Size limit, 3) No dangerous patterns
	 */
	private boolean isValidBase64Image(String base64) {
		if (base64 == null || base64.isEmpty()) {
			return false;
		}

		// Check size limit (5MB)
		if (base64.length() > MAX_IMAGE_SIZE) {
			log.warn("Base64 image exceeds size limit: {} > {}", base64.length(), MAX_IMAGE_SIZE);
			return false;
		}

		// Check for valid base64 characters only
		if (!base64.matches("^[A-Za-z0-9+/=]+$")) {
			log.warn("Base64 image contains invalid characters");
			return false;
		}

		return true;
	}

}
