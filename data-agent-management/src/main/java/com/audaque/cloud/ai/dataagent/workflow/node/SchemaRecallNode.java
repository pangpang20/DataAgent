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

import com.audaque.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.audaque.cloud.ai.dataagent.service.schema.SchemaService;
import com.audaque.cloud.ai.dataagent.util.ChatResponseUtil;
import com.audaque.cloud.ai.dataagent.util.FluxUtil;
import com.audaque.cloud.ai.dataagent.util.StateUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.audaque.cloud.ai.dataagent.constant.Constant.*;

/**
 * Schema recall node that retrieves relevant database schema information based on
 * keywords and intent.
 *
 * This node is responsible for: - Recalling relevant tables based on user input -
 * Retrieving column documents based on extracted keywords - Organizing schema information
 * for subsequent processing - Providing streaming feedback during recall process
 *
 */
@Slf4j
@Component
@AllArgsConstructor
public class SchemaRecallNode implements NodeAction {

	private final SchemaService schemaService;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// get input information
		QueryEnhanceOutputDTO queryEnhanceOutputDTO = StateUtil.getObjectValue(state, QUERY_ENHANCE_NODE_OUTPUT,
				QueryEnhanceOutputDTO.class);
		String input = queryEnhanceOutputDTO.getCanonicalQuery();
		String agentId = StateUtil.getStringValue(state, AGENT_ID);

		// Execute business logic first - recall schema information immediately
		List<Document> rawTableDocuments = new ArrayList<>(schemaService.getTableDocumentsForAgent(agentId, input));
		
		// 过滤系统表并提取表名
		List<Document> tableDocuments = filterSystemTableDocuments(rawTableDocuments);
		List<String> recalledTableNames = extractTableNames(tableDocuments);
		
		List<Document> columnDocuments = schemaService.getColumnDocumentsByTableName(agentId, recalledTableNames);

		String failMessage = """
				\n 未检索到相关数据表

				这可能是因为：
				1. 数据源尚未初始化。
				2. 您的提问与当前数据库中的表结构无关。
				3. 请尝试点击“初始化数据源”或换一个与业务相关的问题。
				4. 如果你用A嵌入模型初始化数据源，却更换为B嵌入模型，请重新初始化数据源
				流程已终止。
				""";

		Flux<ChatResponse> displayFlux = Flux.create(emitter -> {
			emitter.next(ChatResponseUtil.createResponse("开始初步召回Schema信息..."));
			emitter.next(ChatResponseUtil.createResponse(
					"初步表信息召回完成，数量: " + tableDocuments.size() + "，表名: " + String.join(", ", recalledTableNames)));
			if (tableDocuments.isEmpty()) {
				emitter.next(ChatResponseUtil.createResponse(failMessage));
			}
			emitter.next(ChatResponseUtil.createResponse("初步Schema信息召回完成."));
			emitter.complete();
		});

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, currentState -> {
					return Map.of(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, tableDocuments,
							COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT, columnDocuments);
				}, displayFlux);

		// Return the processing result
		return Map.of(SCHEMA_RECALL_NODE_OUTPUT, generator);
	}

	/**
	 * DataAgent系统表列表 - 这些表不应该出现在用户查询的Schema中
	 * 当用户的数据源连接到DataAgent同一数据库时，需要过滤掉这些系统表
	 */
	private static final Set<String> SYSTEM_TABLES = Set.of(
		// DataAgent核心表
		"agents", "agent_datasources", "agent_datasource_tables",
		"agent_knowledges", "agent_knowledge_files",
		// 数据源相关
		"datasources", "databases", "schemas",
		// 聊天相关
		"chat_messages", "chat_sessions",
		// 配置相关
		"model_configs", "preset_questions", "user_prompt_configs",
		// 语义模型和逻辑关系
		"semantic_models", "logical_relations",
		// 业务知识
		"business_terms"
	);

	/**
	 * 过滤系统表Document - 移除DataAgent系统表的文档
	 */
	private static List<Document> filterSystemTableDocuments(List<Document> tableDocuments) {
		List<Document> filteredDocs = new ArrayList<>();
		List<String> filteredSystemTables = new ArrayList<>();
		
		for (Document document : tableDocuments) {
			String name = (String) document.getMetadata().get("name");
			if (name != null && !name.isEmpty()) {
				if (SYSTEM_TABLES.contains(name.toLowerCase())) {
					filteredSystemTables.add(name);
				} else {
					filteredDocs.add(document);
				}
			}
		}
		
		if (!filteredSystemTables.isEmpty()) {
			log.warn("Filtered {} DataAgent system tables from recall: {}", 
				filteredSystemTables.size(), filteredSystemTables);
		}
		
		return filteredDocs;
	}

	/**
	 * 从过滤后的Document中提取表名
	 */
	private static List<String> extractTableNames(List<Document> tableDocuments) {
		List<String> tableNames = new ArrayList<>();
		for (Document document : tableDocuments) {
			String name = (String) document.getMetadata().get("name");
			if (name != null && !name.isEmpty()) {
				tableNames.add(name);
			}
		}
		log.info("At this SchemaRecallNode, Recall tables are: {}", tableNames);
		return tableNames;
	}

}
