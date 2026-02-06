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
package com.audaque.cloud.ai.dataagent.workflow.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.audaque.cloud.ai.dataagent.util.StateUtil;
import lombok.extern.slf4j.Slf4j;

import static com.audaque.cloud.ai.dataagent.constant.Constant.*;

/**
 * Dispatcher for PlanValidatorNode that routes based on validation results.
 */
@Slf4j
public class PlanValidatorDispatcher implements EdgeAction {

	private static final int MAX_REPAIR_ATTEMPTS = 2;

	@Override
	public String apply(OverAllState state) {
		boolean validationPassed = StateUtil.getObjectValue(state, PLAN_VALIDATION_STATUS, Boolean.class, false);

		if (validationPassed) {
			log.info("Plan validation passed.");
			
			// Check if human review is enabled
			Boolean humanReviewEnabled = state.value(HUMAN_REVIEW_ENABLED, false);
			if (Boolean.TRUE.equals(humanReviewEnabled)) {
				log.info("Human review enabled: routing to human_feedback node");
				return HUMAN_FEEDBACK_NODE;
			}
			
			// Route to the first execution node
			String nextNode = state.value(PLAN_NEXT_NODE, StateGraph.END);
			log.info("Routing to first execution node: {}", nextNode);
			return nextNode;
		} else {
			// Plan validation failed, check repair count and decide whether to retry or end.
			int repairCount = StateUtil.getObjectValue(state, PLAN_REPAIR_COUNT, Integer.class, 0);

			if (repairCount > MAX_REPAIR_ATTEMPTS) {
				log.error("Plan repair attempts exceeded the limit of {}. Terminating execution.", MAX_REPAIR_ATTEMPTS);
				return StateGraph.END;
			}

			log.warn("Plan validation failed. Routing back to PlannerNode for repair. Attempt count: {}.", repairCount);
			return PLANNER_NODE;
		}
	}
}