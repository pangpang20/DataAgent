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
import java.util.Map;
import java.util.Objects;
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
		log.info("Starting semantic consistency check - user_query: {}, dialect: {}, SQL length: {}",
				semanticConsistencyDTO.getUserQuery(),
				semanticConsistencyDTO.getDialect(),
				semanticConsistencyDTO.getSql() != null ? semanticConsistencyDTO.getSql().length() : 0);
		
		String semanticConsistencyPrompt = PromptHelper.buildSemanticConsistenPrompt(semanticConsistencyDTO);
		log.debug("Semantic consistency prompt built, length: {} chars", semanticConsistencyPrompt.length());
		log.debug("semanticConsistencyPrompt as follows \n {} \n", semanticConsistencyPrompt);
		
		Flux<ChatResponse> responseFlux = llmService.callUser(semanticConsistencyPrompt);
		log.debug("Semantic consistency LLM call initiated");
		
		return responseFlux
				.doOnNext(response -> log.debug("Received semantic consistency response chunk"))
				.doOnComplete(() -> log.info("Semantic consistency check completed"))
				.doOnError(e -> log.error("Semantic consistency check failed", e));
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
		log.info("Starting fine selection with advice - advice: {}, available tables: {}",
				sqlGenerateSchemaMissingAdvice,
				schemaDTO != null && schemaDTO.getTable() != null ? schemaDTO.getTable().size() : 0);
		
		String schemaInfo = buildMixMacSqlDbPrompt(schemaDTO, true);
		log.debug("Schema info built for advice-based selection, length: {} chars", schemaInfo.length());
		
		String prompt = " 建议：" + sqlGenerateSchemaMissingAdvice
				+ " \n 请按照建议进行返回相关表的名称，只返回建议中提到的表名，返回格式为：[\"a\",\"b\",\"c\"] \n " + schemaInfo;
		log.debug("Built table selection with advice prompt, total length: {} chars", prompt.length());
		log.debug("Built table selection with advice prompt as follows \n {} \n", prompt);
		
		StringBuilder sb = new StringBuilder();
		return llmService.callUser(prompt)
				.doOnNext(r -> {
					String text = r.getResult().getOutput().getText();
					log.debug("Received advice-based selection chunk: {}", text);
					sb.append(text);
				})
				.doOnComplete(() -> {
					String content = sb.toString();
					log.debug("Advice-based selection stream completed, full content length: {} chars", content.length());
					
					if (!content.trim().isEmpty()) {
						String jsonContent = MarkdownParserUtil.extractText(content);
						log.debug("Extracted JSON content: {}", jsonContent);
						
						List<String> tableList;
						try {
							tableList = JsonUtil.getObjectMapper().readValue(jsonContent, new TypeReference<List<String>>() {});
							log.debug("Successfully parsed table list: {}", tableList);
						} catch (Exception e) {
							log.error("Failed to parse table selection response: {}", jsonContent, e);
							throw new IllegalStateException(jsonContent);
						}
						
						if (tableList != null && !tableList.isEmpty()) {
							Set<String> selectedTables = tableList.stream()
									.map(String::toLowerCase)
									.collect(Collectors.toSet());
							log.info("Advice-based selection completed: selected {} tables: {}", selectedTables.size(), selectedTables);
							resultConsumer.accept(selectedTables);
							return;
						}
					}
					
					log.debug("No tables selected based on advice");
					resultConsumer.accept(new HashSet<>());
				})
				.doOnError(e -> log.error("Advice-based selection failed", e));
	}

	@Override
	public Flux<ChatResponse> fineSelect(SchemaDTO schemaDTO, String query, String evidence,
			String sqlGenerateSchemaMissingAdvice, DbConfigBO specificDbConfig, Consumer<SchemaDTO> dtoConsumer) {
		log.info("=== Starting fine selection (non-streaming) ===");
		log.info("Query: {}", query);
		log.info("Evidence: {}", evidence != null ? evidence : "(none)");
		log.info("Schema missing advice: {}", sqlGenerateSchemaMissingAdvice != null ? sqlGenerateSchemaMissingAdvice : "(none)");
		log.info("DB config: {}", specificDbConfig != null ? specificDbConfig.getUrl() : "default");
		log.info("Available tables count: {}", schemaDTO != null && schemaDTO.getTable() != null ? schemaDTO.getTable().size() : 0);

		// 构建 Prompt
		String prompt = buildMixSelectorPrompt(evidence, query, schemaDTO);
		log.info("Mix selector prompt built successfully, length: {} chars", prompt.length());
		log.debug("Built schema fine selection prompt as follows \n {} \n", prompt);

		Set<String> selectedTables = new HashSet<>();

		// 使用非流式调用 + temperature=0 + JSON严格模式
		// 这是内部控制逻辑，不需要流式输出，避免90%的解析异常
		return Mono.fromCallable(() -> {
			log.info("Calling LLM for fine selection with non-streaming mode and temperature=0");
			
			try {
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
				log.info("LLM fine selection response received, length: {} chars", content.length());
				log.debug("LLM fine selection response: {}", content);
				
				// 处理响应
				if (!content.trim().isEmpty()) {
					String jsonContent = MarkdownParserUtil.extractText(content);
					log.debug("Extracted JSON content: {}", jsonContent);
					
					List<String> tableList;
					try {
						// 尝试直接解析为 List<String> (旧格式)
						tableList = jsonParseUtil.tryConvertToObject(jsonContent, new TypeReference<List<String>>() {});
						log.info("Successfully parsed table list (old format), count: {}", tableList != null ? tableList.size() : 0);
						log.debug("Parsed table list: {}", tableList);
					} catch (Exception e) {
						// 如果解析失败,尝试解析为新格式: List<Map<String, Object>>
						log.warn("Failed to parse as List<String>, trying new format with table objects");
						try {
							List<Map<String, Object>> tableObjects = jsonParseUtil.tryConvertToObject(jsonContent, new TypeReference<List<Map<String, Object>>>() {});
							log.info("Successfully parsed table objects (new format), count: {}", tableObjects != null ? tableObjects.size() : 0);
							log.debug("Parsed table objects: {}", tableObjects);
							
							// 从对象中提取 table 字段
							if (tableObjects != null) {
								tableList = tableObjects.stream()
										.map(obj -> (String) obj.get("table"))
										.filter(Objects::nonNull)
										.collect(Collectors.toList());
								log.info("Extracted table names from objects: {}", tableList);
							}
						} catch (Exception e2) {
							log.error("Failed to parse fine selection response as both formats: {}", jsonContent, e2);
							throw new IllegalStateException("JSON parse failed: " + jsonContent, e2);
						}
					}
					
					if (tableList != null && !tableList.isEmpty()) {
						selectedTables.addAll(tableList.stream().map(String::toLowerCase).collect(Collectors.toSet()));
						log.info("Selected tables (lowercase): {}", selectedTables);
						
						if (schemaDTO.getTable() != null) {
							int originalTableCount = schemaDTO.getTable().size();
							log.debug("Filtering schema tables from {} tables", originalTableCount);
							
							schemaDTO.getTable()
									.removeIf(table -> {
										boolean shouldRemove = !selectedTables.contains(table.getName().toLowerCase());
										if (shouldRemove) {
											log.debug("Removing table: {}", table.getName());
										}
										return shouldRemove;
									});
							
							int finalTableCount = schemaDTO.getTable().size();
							log.info("Fine selection filter completed: {} -> {} tables", originalTableCount, finalTableCount);
							log.info("Remaining tables: {}", 
									schemaDTO.getTable().stream().map(t -> t.getName()).collect(Collectors.toList()));
						}
					} else {
						log.warn("Table list is null or empty after parsing");
					}
				} else {
					log.warn("LLM response content is empty");
				}
				
				// 处理schema missing advice(如果有)
				if (sqlGenerateSchemaMissingAdvice != null) {
					log.info("Processing schema missing advice: {}", sqlGenerateSchemaMissingAdvice);
					this.fineSelect(schemaDTO, sqlGenerateSchemaMissingAdvice, additionalTables -> {
						log.info("Adding {} additional tables from advice: {}", additionalTables.size(), additionalTables);
						selectedTables.addAll(additionalTables);
					}).blockLast();
					log.info("Schema missing advice processing completed");
				}
				
				log.info("=== Fine selection completed successfully ===");
				log.info("Final selected tables count: {}", selectedTables.size());
				log.info("Final selected tables: {}", selectedTables);
				
				dtoConsumer.accept(schemaDTO);
				return response;
				
			} catch (Exception e) {
				log.error("=== Fine selection failed with exception ===", e);
				log.error("Exception type: {}", e.getClass().getName());
				log.error("Exception message: {}", e.getMessage());
				throw e;
			}
		}).flux();
	}

}
