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
package com.audaque.cloud.ai.dataagent.service.nl2sql;

import com.audaque.cloud.ai.dataagent.bo.DbConfigBO;
import com.audaque.cloud.ai.dataagent.dto.prompt.SemanticConsistencyDTO;
import com.audaque.cloud.ai.dataagent.dto.prompt.SqlGenerationDTO;
import com.audaque.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.audaque.cloud.ai.dataagent.prompt.PromptHelper;
import com.audaque.cloud.ai.dataagent.service.aimodelconfig.AiModelRegistry;
import com.audaque.cloud.ai.dataagent.service.llm.LlmService;
import com.audaque.cloud.ai.dataagent.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.audaque.cloud.ai.dataagent.prompt.PromptHelper.buildMixMacSqlDbPrompt;
import static com.audaque.cloud.ai.dataagent.prompt.PromptHelper.buildMixSelectorPrompt;

@Slf4j
@Service
@AllArgsConstructor
public class Nl2SqlServiceImpl implements Nl2SqlService {

	public final LlmService llmService;

	private final JsonParseUtil jsonParseUtil;

	private final AiModelRegistry aiModelRegistry;

	@Override
	public Flux<ChatResponse> performSemanticConsistency(SemanticConsistencyDTO semanticConsistencyDTO) {
		String semanticConsistencyPrompt = PromptHelper.buildSemanticConsistenPrompt(semanticConsistencyDTO);
		log.debug("semanticConsistencyPrompt as follows \n {} \n", semanticConsistencyPrompt);
		return llmService.callUser(semanticConsistencyPrompt);
	}

	@Override
	public Flux<String> generateSql(SqlGenerationDTO sqlGenerationDTO) {
		String sql = sqlGenerationDTO.getSql();
		log.info("Generating SQL for query: {}, hasExistingSql: {}, dialect: {}",
				sqlGenerationDTO.getExecutionDescription(), StringUtils.hasText(sql), sqlGenerationDTO.getDialect());

		Flux<ChatResponse> chatResponseFlux;
		if (sql != null && !sql.isEmpty()) {
			// Use professional SQL error repair prompt
			log.debug("Using SQL error fixer for existing SQL: {}", sql);
			String errorFixerPrompt = PromptHelper.buildSqlErrorFixerPrompt(sqlGenerationDTO);
			log.debug("SQL error fixer prompt as follows \n {} \n", errorFixerPrompt);
			chatResponseFlux = llmService.callUser(errorFixerPrompt);
		} else {
			// Normal SQL generation process
			log.debug("Generating new SQL from scratch");
			String prompt = PromptHelper.buildNewSqlGeneratorPrompt(sqlGenerationDTO);
			log.debug("New SQL generator prompt as follows \n {} \n", prompt);
			chatResponseFlux = llmService.callSystem(prompt);
		}

		// Add detailed logging to diagnose LLM response
		return chatResponseFlux
				.doOnNext(response -> {
					if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
						String text = response.getResult().getOutput().getText();
						log.debug("LLM ChatResponse chunk: text=[{}], textLength={}", text,
								text != null ? text.length() : -1);
					} else {
						log.warn("LLM ChatResponse chunk is null or has null result/output: response={}", response);
					}
				})
				.doOnComplete(() -> log.info("LLM SQL generation stream completed"))
				.doOnError(e -> log.error("LLM SQL generation stream error", e))
				.map(ChatResponseUtil::getText);
	}

	private Flux<ChatResponse> fineSelect(SchemaDTO schemaDTO, String sqlGenerateSchemaMissingAdvice,
			Consumer<Set<String>> resultConsumer) {
		log.debug("Fine selecting tables based on advice: {}", sqlGenerateSchemaMissingAdvice);
		String schemaInfo = buildMixMacSqlDbPrompt(schemaDTO, true);
		String prompt = " 建议：" + sqlGenerateSchemaMissingAdvice
				+ " \n 请按照建议进行返回相关表的名称，只返回建议中提到的表名，返回格式为：[\"a\",\"b\",\"c\"] \n " + schemaInfo;
		log.debug("Built table selection with advice prompt as follows \n {} \n", prompt);
		StringBuilder sb = new StringBuilder();
		return llmService.callUser(prompt).doOnNext(r -> {
			String text = r.getResult().getOutput().getText();
			sb.append(text);
		}).doOnComplete(() -> {
			String content = sb.toString();
			if (!content.trim().isEmpty()) {
				String jsonContent = MarkdownParserUtil.extractText(content);
				List<String> tableList;
				try {
					tableList = JsonUtil.getObjectMapper().readValue(jsonContent, new TypeReference<List<String>>() {
					});
				} catch (Exception e) {
					log.error("Failed to parse table selection response: {}", jsonContent, e);
					throw new IllegalStateException(jsonContent);
				}
				if (tableList != null && !tableList.isEmpty()) {
					Set<String> selectedTables = tableList.stream()
							.map(String::toLowerCase)
							.collect(Collectors.toSet());
					log.debug("Selected {} tables based on advice: {}", selectedTables.size(), selectedTables);
					resultConsumer.accept(selectedTables);
				}
			}
			log.debug("No tables selected based on advice");
			resultConsumer.accept(new HashSet<>());
		});
	}

	@Override
	public Flux<ChatResponse> fineSelect(SchemaDTO schemaDTO, String query, String evidence,
			String sqlGenerateSchemaMissingAdvice, DbConfigBO specificDbConfig, Consumer<SchemaDTO> dtoConsumer) {
		log.debug("Fine selecting schema for query: {} with evidences and specificDbConfig: {}", query,
				specificDbConfig != null ? specificDbConfig.getUrl() : "default");

		String prompt = buildMixSelectorPrompt(evidence, query, schemaDTO);
		log.debug("Built schema fine selection prompt as follows \n {} \n", prompt);

		Set<String> selectedTables = new HashSet<>();

		// 使用非流式调用 + temperature=0 + JSON严格模式
		// 这是内部控制逻辑，不需要流式输出，避免90%的解析异常
		return Mono.fromCallable(() -> {
			log.debug("Calling LLM for fine selection with non-streaming mode and temperature=0");
			
			// 非流式调用，直接获取完整响应
			ChatResponse response = aiModelRegistry.getChatClient()
					.prompt()
					.user(prompt)
					.options(OpenAiChatOptions.builder()
							.temperature(0.0)  // 温度为0，确保结果稳定
							.build())
					.call()
					.chatResponse();
			
			String content = response.getResult().getOutput().getText();
			log.debug("LLM fine selection response: {}", content);
			
			// 处理响应
			if (!content.trim().isEmpty()) {
				String jsonContent = MarkdownParserUtil.extractText(content);
				List<String> tableList;
				try {
					tableList = jsonParseUtil.tryConvertToObject(jsonContent, new TypeReference<List<String>>() {});
				} catch (Exception e) {
					log.error("Failed to parse fine selection response: {}", jsonContent, e);
					throw new IllegalStateException(jsonContent);
				}
				
				if (tableList != null && !tableList.isEmpty()) {
					selectedTables.addAll(tableList.stream().map(String::toLowerCase).collect(Collectors.toSet()));
					if (schemaDTO.getTable() != null) {
						int originalTableCount = schemaDTO.getTable().size();
						schemaDTO.getTable()
								.removeIf(table -> !selectedTables.contains(table.getName().toLowerCase()));
						int finalTableCount = schemaDTO.getTable().size();
						log.debug("Fine selection completed: {} -> {} tables, selected tables: {}",
								originalTableCount, finalTableCount, selectedTables);
					}
				}
			}
			
			// 处理schema missing advice(如果有)
			if (sqlGenerateSchemaMissingAdvice != null) {
				log.debug("Adding tables from schema missing advice");
				this.fineSelect(schemaDTO, sqlGenerateSchemaMissingAdvice, selectedTables::addAll).blockLast();
			}
			
			dtoConsumer.accept(schemaDTO);
			return response;
		}).flux();
	}

}
