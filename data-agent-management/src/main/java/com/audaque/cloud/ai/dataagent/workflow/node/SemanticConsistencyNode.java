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

import com.audaque.cloud.ai.dataagent.util.FluxUtil;
import com.audaque.cloud.ai.dataagent.util.StateUtil;
import com.audaque.cloud.ai.dataagent.dto.datasource.SqlRetryDto;
import com.audaque.cloud.ai.dataagent.dto.prompt.SemanticConsistencyDTO;
import com.audaque.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.audaque.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.audaque.cloud.ai.dataagent.constant.Constant.*;
import static com.audaque.cloud.ai.dataagent.util.PlanProcessUtil.getCurrentExecutionStepInstruction;
import static com.audaque.cloud.ai.dataagent.prompt.PromptHelper.buildMixMacSqlDbPrompt;

/**
 * Semantic consistency validation node that checks SQL query semantic consistency.
 *
 * This node is responsible for: - Validating SQL query semantic consistency against
 * schema and evidence - Providing validation results for query refinement - Handling
 * validation failures with recommendations - Managing step progression in execution plan
 *
 */
@Slf4j
@Component
@AllArgsConstructor
public class SemanticConsistencyNode implements NodeAction {

	private final Nl2SqlService nl2SqlService;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// Get necessary input parameters
		String evidence = StateUtil.getStringValue(state, EVIDENCE);
		SchemaDTO schemaDTO = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);
		String dialect = StateUtil.getStringValue(state, DB_DIALECT_TYPE);
		// Get current execution step and SQL query
		String sql = StateUtil.getStringValue(state, SQL_GENERATE_OUTPUT);
		String userQuery = StateUtil.getCanonicalQuery(state);
		
		log.debug("[SemanticConsistencyNode] Input SQL for validation: [{}]", sql);
		log.debug("[SemanticConsistencyNode] Dialect: {}, UserQuery: {}", dialect, userQuery);

		SemanticConsistencyDTO semanticConsistencyDTO = SemanticConsistencyDTO.builder()
			.dialect(dialect)
			.sql(sql)
			.executionDescription(getCurrentExecutionStepInstruction(state))
			.schemaInfo(buildMixMacSqlDbPrompt(schemaDTO, true))
			.userQuery(userQuery)
			.evidence(evidence)
			.build();
		log.info("Starting semantic consistency validation - SQL: {}", sql);
		log.debug("[SemanticConsistencyNode] Execution description: {}", getCurrentExecutionStepInstruction(state));
		Flux<ChatResponse> validationResultFlux = nl2SqlService.performSemanticConsistency(semanticConsistencyDTO);

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, "开始语义一致性校验", "语义一致性校验完成", validationResult -> {
					log.debug("[SemanticConsistencyNode] Raw LLM validation result: [{}]", validationResult);
					log.debug("[SemanticConsistencyNode] Validation result length: {}", 
						validationResult != null ? validationResult.length() : "null");
					
					// 更严格的判定逻辑：只有明确包含 "通过" 且不包含 "不通过" 才视为通过
					boolean isPassed = parseValidationResult(validationResult);
					
					Map<String, Object> result = buildValidationResult(isPassed, validationResult);
					log.info("[{}] Semantic consistency validation result: {}, passed: {}",
							this.getClass().getSimpleName(), validationResult, isPassed);
					return result;
				}, validationResultFlux);

		return Map.of(SEMANTIC_CONSISTENCY_NODE_OUTPUT, generator);
	}

	/**
	 * Parse validation result from LLM response
	 * Only returns true if result clearly indicates "通过" and does NOT contain "不通过"
	 * All other cases (including SQL statements, unclear responses) return false
	 * @param validationResult The raw validation result from LLM
	 * @return true if passed, false otherwise
	 */
	private boolean parseValidationResult(String validationResult) {
		if (validationResult == null || validationResult.trim().isEmpty()) {
			log.warn("[SemanticConsistencyNode] Validation result is null or empty, treating as failed");
			return false;
		}
		
		String trimmed = validationResult.trim();
		
		// Check if result contains "不通过" - definitely failed
		if (trimmed.contains("不通过")) {
			log.debug("[SemanticConsistencyNode] Result contains '不通过', validation failed");
			return false;
		}
		
		// Check if result contains "通过" - might be passed
		if (trimmed.contains("通过")) {
			// But if it looks like SQL (contains SELECT, FROM, WHERE, etc.), treat as failed
			if (containsSqlKeywords(trimmed)) {
				log.warn("[SemanticConsistencyNode] Result contains '通过' but also SQL keywords, " +
						"LLM returned SQL instead of validation result. Treating as failed.");
				log.warn("[SemanticConsistencyNode] Suspicious result: {}", 
					trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed);
				return false;
			}
			
			log.debug("[SemanticConsistencyNode] Result contains '通过' and no SQL keywords, validation passed");
			return true;
		}
		
		// If result doesn't contain "通过" or "不通过", treat as failed
		log.warn("[SemanticConsistencyNode] Result does not contain '通过' or '不通过', treating as failed");
		log.warn("[SemanticConsistencyNode] Unexpected result format: {}", 
			trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed);
		return false;
	}
	
	/**
	 * Check if text contains SQL keywords
	 * @param text Text to check
	 * @return true if contains SQL keywords
	 */
	private boolean containsSqlKeywords(String text) {
		String upperText = text.toUpperCase();
		return upperText.contains("SELECT ") || 
			   upperText.contains(" FROM ") || 
			   upperText.contains(" WHERE ") ||
			   upperText.contains(" GROUP BY ") ||
			   upperText.contains(" ORDER BY ");
	}

	/**
	 * Build validation result
	 */
	private Map<String, Object> buildValidationResult(boolean passed, String validationResult) {
		if (passed) {
			return Map.of(SEMANTIC_CONSISTENCY_NODE_OUTPUT, true);
		}
		else {
			return Map.of(SEMANTIC_CONSISTENCY_NODE_OUTPUT, false, SQL_REGENERATE_REASON,
					SqlRetryDto.semantic(validationResult));
		}
	}

}
