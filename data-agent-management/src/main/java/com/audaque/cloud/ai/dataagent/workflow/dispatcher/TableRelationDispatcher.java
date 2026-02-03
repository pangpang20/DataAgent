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
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.audaque.cloud.ai.dataagent.util.StateUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static com.audaque.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;

@Slf4j
public class TableRelationDispatcher implements EdgeAction {

	private static final int MAX_RETRY_COUNT = 3;

	@Override
	public String apply(OverAllState state) throws Exception {
		log.debug("[TableRelationDispatcher] Processing state: {}", state);

		String errorFlag = StateUtil.getStringValue(state, TABLE_RELATION_EXCEPTION_OUTPUT, null);
		Integer retryCount = StateUtil.getObjectValue(state, TABLE_RELATION_RETRY_COUNT, Integer.class, 0);

		if (errorFlag != null && !errorFlag.isEmpty()) {
			if (isRetryableError(errorFlag) && retryCount < MAX_RETRY_COUNT) {
				log.info("[TableRelationDispatcher] Retryable error detected, retrying (attempt {}/{})", 
					retryCount + 1, MAX_RETRY_COUNT);
				return TABLE_RELATION_NODE;
			}
			else {
				log.warn("[TableRelationDispatcher] Non-retryable error or max retries exceeded, ending workflow");
				return END;
			}
		}

		Optional<String> tableRelationOutput = state.value(TABLE_RELATION_OUTPUT);
		if (tableRelationOutput.isPresent()) {
			log.info("[TableRelationDispatcher] Table relation output found, proceeding to feasibility assessment");
			return FEASIBILITY_ASSESSMENT_NODE;
		}

		// no output, end
		log.info("[TableRelationDispatcher] No table relation output, ending workflow");
		return END;
	}

	private boolean isRetryableError(String errorMessage) {
		return errorMessage.startsWith("RETRYABLE:");
	}

}
