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

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.audaque.cloud.ai.dataagent.dto.planner.ExecutionStep;
import com.audaque.cloud.ai.dataagent.dto.planner.Plan;
import com.audaque.cloud.ai.dataagent.util.PlanProcessUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.audaque.cloud.ai.dataagent.constant.Constant.*;

/**
 * Plan execution node that handles step-by-step execution.
 * Plan validation is performed once in PlanValidatorNode.
 * 
 * Safety measures:
 * 1. Checks PLAN_VALIDATION_STATUS before execution (defensive programming)
 * 2. Only executes when validation has passed
 * 3. Prevents execution of invalid plans
 */
@Slf4j
@Component
public class PlanExecutorNode implements NodeAction {

	// Supported node types
	private static final Set<String> SUPPORTED_NODES = Set.of(SQL_GENERATE_NODE, PYTHON_GENERATE_NODE,
			REPORT_GENERATOR_NODE);

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		log.debug("Executing plan step...");

		// 1. 首先检查计划校验状态（防御性编程）
		Boolean validationPassed = state.value(PLAN_VALIDATION_STATUS, false);
		if (!Boolean.TRUE.equals(validationPassed)) {
			String validationError = state.value(PLAN_VALIDATION_ERROR, "Unknown validation error");
			log.error("Plan validation failed, cannot execute. Error: {}", validationError);
			// 校验失败的情况下，不应该进入执行阶段
			return Map.of(
				PLAN_VALIDATION_STATUS, false,
				PLAN_VALIDATION_ERROR, "Plan validation failed before execution: " + validationError
			);
		}

		// 2. Get current step and plan
		int currentStep = PlanProcessUtil.getCurrentStepNumber(state);
		Plan plan = PlanProcessUtil.getPlan(state);
		List<ExecutionStep> executionPlan = plan.getExecutionPlan();

		boolean isOnlyNl2Sql = state.value(IS_ONLY_NL2SQL, false);

		// 3. Check if the plan is completed
		if (currentStep > executionPlan.size()) {
			log.info("Plan completed, current step: {}, total steps: {}", currentStep, executionPlan.size());
			return Map.of(
				PLAN_CURRENT_STEP, 1, 
				PLAN_NEXT_NODE, isOnlyNl2Sql ? StateGraph.END : REPORT_GENERATOR_NODE,
				PLAN_VALIDATION_STATUS, true
			);
		}

		// 4. Get current step and determine next node
		ExecutionStep executionStep = executionPlan.get(currentStep - 1);
		String toolToUse = executionStep.getToolToUse();

		log.info("Executing step {}: {}", currentStep, toolToUse);
		return determineNextNode(toolToUse);
	}

	/**
	 * Determine the next node to execute
	 */
	private Map<String, Object> determineNextNode(String toolToUse) {
		if (SUPPORTED_NODES.contains(toolToUse)) {
			log.info("Determined next execution node: {}", toolToUse);
			return Map.of(PLAN_NEXT_NODE, toolToUse, PLAN_VALIDATION_STATUS, true);
		} else if (HUMAN_FEEDBACK_NODE.equals(toolToUse)) {
			log.info("Determined next execution node: {}", toolToUse);
			return Map.of(PLAN_NEXT_NODE, toolToUse, PLAN_VALIDATION_STATUS, true);
		} else {
			// This case should ideally not be reached if validation is done correctly
			// before in PlanValidatorNode
			return Map.of(
				PLAN_VALIDATION_STATUS, false, 
				PLAN_VALIDATION_ERROR, "Unsupported node type: " + toolToUse
			);
		}
	}
}
