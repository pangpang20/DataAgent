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

import com.audaque.cloud.ai.dataagent.dto.datasource.SqlRetryDto;
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
			// Get fine-grained error counters
			int syntaxErrorCount = state.value(SQL_SYNTAX_ERROR_COUNT, 0);
			int semanticErrorCount = state.value(SQL_SEMANTIC_ERROR_COUNT, 0);
			int executionErrorCount = state.value(SQL_EXECUTION_ERROR_COUNT, 0);
			int totalCount = state.value(SQL_GENERATE_COUNT, 0);

			log.info(
					"SQL generation failed - Total attempts: {}, Syntax errors: {}, Semantic errors: {}, Execution errors: {}",
					totalCount, syntaxErrorCount, semanticErrorCount, executionErrorCount);

			// Get the last retry context to determine error type
			Optional<Object> retryReasonObj = state.value(SQL_REGENERATE_REASON);
			SqlRetryDto.ErrorType errorType = SqlRetryDto.ErrorType.UNKNOWN;
			if (retryReasonObj.isPresent() && retryReasonObj.get() instanceof SqlRetryDto) {
				SqlRetryDto retryDto = (SqlRetryDto) retryReasonObj.get();
				errorType = retryDto.errorType();
				log.debug("Detected error type from retry context: {}", errorType);
			}

			// Check fine-grained thresholds based on error type
			boolean shouldRetry = false;
			String reachedLimitReason = "";

			switch (errorType) {
				case SYNTAX:
					if (syntaxErrorCount < properties.getMaxSqlSyntaxErrorRetry()) {
						shouldRetry = true;
						log.info("SQL syntax error retry allowed: {}/{}", syntaxErrorCount,
								properties.getMaxSqlSyntaxErrorRetry());
					} else {
						reachedLimitReason = "syntax error";
						log.warn("SQL syntax error retry limit reached: {}/{}", syntaxErrorCount,
								properties.getMaxSqlSyntaxErrorRetry());
					}
					break;
				case SEMANTIC:
					if (semanticErrorCount < properties.getMaxSqlSemanticErrorRetry()) {
						shouldRetry = true;
						log.info("SQL semantic error retry allowed: {}/{}", semanticErrorCount,
								properties.getMaxSqlSemanticErrorRetry());
					} else {
						reachedLimitReason = "semantic error";
						log.warn("SQL semantic error retry limit reached: {}/{}", semanticErrorCount,
								properties.getMaxSqlSemanticErrorRetry());
					}
					break;
				case EXECUTION:
					if (executionErrorCount < properties.getMaxSqlExecutionErrorRetry()) {
						shouldRetry = true;
						log.info("SQL execution error retry allowed: {}/{}", executionErrorCount,
								properties.getMaxSqlExecutionErrorRetry());
					} else {
						reachedLimitReason = "execution error";
						log.warn("SQL execution error retry limit reached: {}/{}", executionErrorCount,
								properties.getMaxSqlExecutionErrorRetry());
					}
					break;
				default:
					// For unknown error types, use total count as fallback
					if (totalCount < properties.getMaxSqlRetryCount()) {
						shouldRetry = true;
						log.info("SQL unknown error retry allowed (fallback): {}/{}", totalCount,
								properties.getMaxSqlRetryCount());
					} else {
						reachedLimitReason = "total retry count";
						log.warn("SQL total retry limit reached: {}/{}", totalCount, properties.getMaxSqlRetryCount());
					}
			}

			// Decide whether to retry or end
			if (shouldRetry) {
				log.info("Starting SQL generation retry for {} error, attempt #{}", errorType, totalCount + 1);
				return SQL_GENERATE_NODE;
			} else {
				log.error(
						"SQL generation failed permanently - reached {} limit. Total: {}, Syntax: {}, Semantic: {}, Execution: {}",
						reachedLimitReason, totalCount, syntaxErrorCount, semanticErrorCount, executionErrorCount);
				return END;
			}
		}

		String sqlGenerateOutput = (String) optional.get();
		log.info("SQL generation result: {}", sqlGenerateOutput);

		if (END.equals(sqlGenerateOutput)) {
			log.info("Detected workflow end flag: {}", END);
			return END;
		} else {
			log.info("SQL generation successful, entering semantic consistency check node: {}",
					SEMANTIC_CONSISTENCY_NODE);
			return SEMANTIC_CONSISTENCY_NODE;
		}
	}

}
