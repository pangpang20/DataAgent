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

import com.audaque.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.audaque.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;

/**
 */
@Slf4j
@Component
@AllArgsConstructor
public class SqlGenerateDispatcher implements EdgeAction {

	private final DataAgentProperties properties;

	@Override
	public String apply(OverAllState state) {
		Optional<Object> optional = state.value(SQL_GENERATE_OUTPUT);
		if (optional.isEmpty()) {
			int currentCount = state.value(SQL_GENERATE_COUNT, properties.getMaxSqlRetryCount());
			// 生成失败，重新生成
			if (currentCount < properties.getMaxSqlRetryCount()) {
				log.info("SQL generation failed, starting retry, current attempt: {}", currentCount);
				return SQL_GENERATE_NODE;
			}
			log.error("SQL generation failed, reached maximum retry count, ending workflow");
			return END;
		}
		String sqlGenerateOutput = (String) optional.get();
		log.info("SQL generation result: {}", sqlGenerateOutput);

		if (END.equals(sqlGenerateOutput)) {
			log.info("Detected workflow end flag: {}", END);
			return END;
		}
		else {
			log.info("SQL generation successful, entering semantic consistency check node: {}", SEMANTIC_CONSISTENCY_NODE);
			return SEMANTIC_CONSISTENCY_NODE;
		}
	}

}
