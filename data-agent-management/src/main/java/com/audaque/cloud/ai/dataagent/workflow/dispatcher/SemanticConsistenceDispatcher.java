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
import lombok.extern.slf4j.Slf4j;

import static com.audaque.cloud.ai.dataagent.constant.Constant.*;

/**
 */
@Slf4j
public class SemanticConsistenceDispatcher implements EdgeAction {

	@Override
	public String apply(OverAllState state) {
		Boolean validate = (Boolean) state.value(SEMANTIC_CONSISTENCY_NODE_OUTPUT).orElse(false);
		log.info("Semantic consistency validation result: {}, routing to next node", validate);
		if (validate) {
			log.info("Semantic consistency validation passed, routing to SQL execution node");
			return SQL_EXECUTE_NODE;
		}
		else {
			log.info("Semantic consistency validation failed, routing to SQL generation node");
			return SQL_GENERATE_NODE;
		}
	}

}
