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

import com.alibaba.cloud.ai.graph.OverAllState;
import com.audaque.cloud.ai.dataagent.dto.planner.ExecutionStep;
import com.audaque.cloud.ai.dataagent.dto.planner.Plan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.audaque.cloud.ai.dataagent.constant.Constant.PLANNER_NODE_OUTPUT;
import static com.audaque.cloud.ai.dataagent.constant.Constant.PLAN_CURRENT_STEP;

/**
 * util class for plan-based execution nodes Provides common functionality for nodes that
 * execute based on predefined plans
 *
 */
@Slf4j
public final class PlanProcessUtil {

	private static final BeanOutputConverter<Plan> converter;

	private static final String STEP_PREFIX = "step_";

	static {
		converter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
		});
	}

	private PlanProcessUtil() {

	}

	/**
	 * Get the current execution step from the plan
	 * @param state the overall state containing plan information
	 * @return the current execution step
	 * @throws IllegalStateException if plan output is empty, plan parsing fails, or step
	 * index is out of range
	 */
	public static ExecutionStep getCurrentExecutionStep(OverAllState state) {
		Plan plan = getPlan(state);
		int currentStep = getCurrentStepNumber(state);
		return getCurrentExecutionStep(plan, currentStep);
	}

	public static String getCurrentExecutionStepInstruction(OverAllState state) {
		String instruction;
		ExecutionStep.ToolParameters currentStepParams = PlanProcessUtil.getCurrentExecutionStep(state)
			.getToolParameters();
		instruction = currentStepParams != null ? currentStepParams.getInstruction() : "无";
		return instruction;
	}

	/**
	 * Get the current execution step from the plan
	 * @param plan the plan object
	 * @param currentStep current step
	 * @return the current execution step
	 * @throws IllegalStateException if plan output is empty, plan parsing fails, or step
	 * index is out of range
	 */
	public static ExecutionStep getCurrentExecutionStep(Plan plan, Integer currentStep) {
		List<ExecutionStep> executionPlan = plan.getExecutionPlan();
		if (executionPlan == null || executionPlan.isEmpty()) {
			throw new IllegalStateException("执行计划为空");
		}

		int stepIndex = currentStep - 1;
		if (stepIndex < 0 || stepIndex >= executionPlan.size()) {
			throw new IllegalStateException("当前步骤索引超出范围: " + stepIndex);
		}

		return executionPlan.get(stepIndex);
	}

	/**
	 * Get the plan object from state. Attempts to repair truncated JSON if initial
	 * parsing fails (e.g., when LLM output is cut off due to maxTokens limit).
	 * @param state the overall state containing plan information
	 * @return the parsed plan object
	 * @throws IllegalStateException if plan output is empty or plan parsing fails
	 */
	public static Plan getPlan(OverAllState state) {
		String plannerNodeOutput = (String) state.value(PLANNER_NODE_OUTPUT)
			.orElseThrow(() -> new IllegalStateException("计划节点输出为空"));
		try {
			Plan plan = converter.convert(plannerNodeOutput);
			if (plan == null) {
				throw new IllegalStateException("计划解析失败");
			}
			return plan;
		}
		catch (Exception e) {
			log.warn("Initial plan JSON parsing failed, attempting truncation repair: {}", e.getMessage());
			String repaired = repairTruncatedJson(plannerNodeOutput);
			if (!repaired.equals(plannerNodeOutput)) {
				try {
					Plan plan = converter.convert(repaired);
					if (plan != null) {
						log.info("Truncated plan JSON repaired successfully");
						return plan;
					}
				}
				catch (Exception repairError) {
					log.error("Repaired JSON still failed to parse: {}", repairError.getMessage());
				}
			}
			throw new IllegalStateException("计划解析失败: " + e.getMessage(), e);
		}
	}

	/**
	 * Attempt to repair truncated JSON by closing unclosed strings, arrays, and objects.
	 * Handles the common case where LLM output is cut off mid-string due to maxTokens.
	 */
	static String repairTruncatedJson(String json) {
		if (json == null || json.isEmpty()) {
			return json;
		}

		StringBuilder sb = new StringBuilder(json);
		Deque<Character> stack = new ArrayDeque<>();
		boolean inString = false;
		boolean escaped = false;

		for (int i = 0; i < sb.length(); i++) {
			char c = sb.charAt(i);

			if (escaped) {
				escaped = false;
				continue;
			}

			if (c == '\\' && inString) {
				escaped = true;
				continue;
			}

			if (c == '"') {
				inString = !inString;
				if (inString) {
					stack.push('"');
				}
				else {
					if (!stack.isEmpty() && stack.peek() == '"') {
						stack.pop();
					}
				}
				continue;
			}

			if (inString) {
				continue;
			}

			if (c == '{' || c == '[') {
				stack.push(c);
			}
			else if (c == '}') {
				if (!stack.isEmpty() && stack.peek() == '{') {
					stack.pop();
				}
			}
			else if (c == ']') {
				if (!stack.isEmpty() && stack.peek() == '[') {
					stack.pop();
				}
			}
		}

		// Close unclosed string
		if (inString) {
			sb.append('"');
		}

		// Close unclosed arrays and objects in reverse order
		while (!stack.isEmpty()) {
			char open = stack.pop();
			if (open == '"') {
				// unmatched quote — already handled by inString flag
			}
			else if (open == '{') {
				sb.append('}');
			}
			else if (open == '[') {
				sb.append(']');
			}
		}

		return sb.toString();
	}

	/**
	 * Get the current step number from state
	 * @param state the overall state
	 * @return the current step number (defaults to 1 if not set)
	 */
	public static int getCurrentStepNumber(OverAllState state) {
		return state.value(PLAN_CURRENT_STEP, 1);
	}

	/**
	 * Add step result
	 * @param existingResults existing result collection
	 * @param stepNumber step number
	 * @param result result content
	 * @return updated result collection
	 */
	public static Map<String, String> addStepResult(Map<String, String> existingResults, Integer stepNumber,
			String result) {
		Map<String, String> updatedResults = new HashMap<>(existingResults);
		updatedResults.put(STEP_PREFIX + stepNumber, result);
		return updatedResults;
	}

}
