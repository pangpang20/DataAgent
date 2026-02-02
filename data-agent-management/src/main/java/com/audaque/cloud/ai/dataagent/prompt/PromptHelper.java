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
package com.audaque.cloud.ai.dataagent.prompt;

import com.audaque.cloud.ai.dataagent.dto.prompt.SemanticConsistencyDTO;
import com.audaque.cloud.ai.dataagent.dto.prompt.SqlGenerationDTO;
import com.audaque.cloud.ai.dataagent.dto.schema.ColumnDTO;
import com.audaque.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.audaque.cloud.ai.dataagent.dto.schema.TableDTO;
import com.audaque.cloud.ai.dataagent.entity.SemanticModel;
import com.audaque.cloud.ai.dataagent.entity.UserPromptConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.audaque.cloud.ai.dataagent.util.ReportTemplateUtil.cleanJsonExample;

@Slf4j
public class PromptHelper {

	/**
	 * 清理字符串中的引号，避免 ST4 模板解析冲突
	 * @param str 原始字符串
	 * @return 清理后的字符串
	 */
	private static String cleanQuotes(String str) {
		if (str == null) {
			return null;
		}
		// 移除头尾的双引号
		return str.replaceAll("^\"", "").replaceAll("\"$", "");
	}

	public static String buildMixSelectorPrompt(String evidence, String question, SchemaDTO schemaDTO) {
		log.debug("Building mix selector prompt - question: {}, tables count: {}", 
				question, schemaDTO != null ? schemaDTO.getTable().size() : 0);
		
		// 构建 Schema 信息（会清理表名/列名中的引号）
		String schemaInfo = buildMixMacSqlDbPrompt(schemaDTO, true);
		log.debug("Schema info built, length: {} chars", schemaInfo.length());
		
		// 准备模板参数
		Map<String, Object> params = new HashMap<>();
		params.put("schema_info", schemaInfo);
		params.put("question", question);
		
		if (StringUtils.isBlank(evidence)) {
			params.put("evidence", "无");
			log.debug("No evidence provided, using default value");
		}
		else {
			params.put("evidence", evidence);
			log.debug("Evidence provided, length: {} chars", evidence.length());
		}
		
		// 渲染模板
		String renderedPrompt = PromptConstant.getMixSelectorPromptTemplate().render(params);
		log.debug("Mix selector prompt rendered successfully, total length: {} chars", renderedPrompt.length());
		
		return renderedPrompt;
	}

	public static String buildMixMacSqlDbPrompt(SchemaDTO schemaDTO, Boolean withColumnType) {
		log.debug("Building mix mac sql db prompt - DB: {}, tables count: {}, withColumnType: {}",
				schemaDTO.getName(), schemaDTO.getTable().size(), withColumnType);
		
		StringBuilder sb = new StringBuilder();
		sb.append("【DB_ID】 ").append(schemaDTO.getName() == null ? "" : schemaDTO.getName()).append("\n");
		
		// 遍历每个表，构建 Schema 信息
		for (int i = 0; i < schemaDTO.getTable().size(); i++) {
			TableDTO tableDTO = schemaDTO.getTable().get(i);
			log.debug("Processing table {}/{}: name='{}', columns count: {}",
					i + 1, schemaDTO.getTable().size(), tableDTO.getName(), tableDTO.getColumn().size());
			
			sb.append(buildMixMacSqlTablePrompt(tableDTO, withColumnType)).append("\n");
		}
		
		// 处理外键
		if (CollectionUtils.isNotEmpty(schemaDTO.getForeignKeys())) {
			log.debug("Adding {} foreign keys to schema", schemaDTO.getForeignKeys().size());
			sb.append("【Foreign keys】\n").append(StringUtils.join(schemaDTO.getForeignKeys(), "\n"));
		}
		else {
			log.debug("No foreign keys in schema");
		}
		
		String result = sb.toString();
		log.debug("Schema prompt built successfully, total length: {} chars", result.length());
		return result;
	}

	public static String buildMixMacSqlTablePrompt(TableDTO tableDTO, Boolean withColumnType) {
		// 记录原始表名（清理前）
		String originalTableName = tableDTO.getName();
		log.debug("Building table prompt - original table name: '{}', columns: {}",
				originalTableName, tableDTO.getColumn().size());
		
		StringBuilder sb = new StringBuilder();
		// 清理表名中的引号，避免 ST4 模板解析冲突
		String cleanTableName = cleanQuotes(tableDTO.getName());
		String cleanTableDesc = cleanQuotes(tableDTO.getDescription());
		
		// 如果表名被清理了，记录清理前后对比
		if (!originalTableName.equals(cleanTableName)) {
			log.debug("Table name cleaned: '{}' -> '{}'", originalTableName, cleanTableName);
		}
		
		sb.append("# Table: ").append(cleanTableName);
		if (!StringUtils.equals(cleanTableName, cleanTableDesc)) {
			sb.append(StringUtils.isBlank(cleanTableDesc) ? "" : ", " + cleanTableDesc)
				.append("\n");
		}
		else {
			sb.append("\n");
		}
		
		sb.append("[\n");
		List<String> columnLines = new ArrayList<>();
		int cleanedColumnsCount = 0;
		
		for (ColumnDTO columnDTO : tableDTO.getColumn()) {
			StringBuilder line = new StringBuilder();
			// 清理列名中的引号
			String originalColumnName = columnDTO.getName();
			String cleanColumnName = cleanQuotes(columnDTO.getName());
			String cleanColumnDesc = cleanQuotes(columnDTO.getDescription());
			
			// 如果列名被清理了，记录清理前后对比
			if (!originalColumnName.equals(cleanColumnName)) {
				log.debug("  Column name cleaned in table '{}': '{}' -> '{}'",
						cleanTableName, originalColumnName, cleanColumnName);
				cleanedColumnsCount++;
			}
			
			line.append("(")
				.append(cleanColumnName)
				.append(BooleanUtils.isTrue(withColumnType)
						? ":" + StringUtils.defaultString(columnDTO.getType(), "").toUpperCase(Locale.ROOT) : "");
			if (!StringUtils.equals(cleanColumnDesc, cleanColumnName)) {
				line.append(", ").append(StringUtils.defaultString(cleanColumnDesc, ""));
			}
			if (CollectionUtils.isNotEmpty(tableDTO.getPrimaryKeys())
					&& tableDTO.getPrimaryKeys().contains(columnDTO.getName())) {
				line.append(", Primary Key");
			}
			List<String> enumData = Optional.ofNullable(columnDTO.getData())
				.orElse(new ArrayList<>())
				.stream()
				.filter(d -> !StringUtils.isEmpty(d))
				.collect(Collectors.toList());
			if (CollectionUtils.isNotEmpty(enumData) && !"id".equals(cleanColumnName)) {
				line.append(", Examples: [");
				List<String> data = new ArrayList<>(enumData.subList(0, Math.min(3, enumData.size())));
				line.append(StringUtils.join(data, ",")).append("]");
			}

			line.append(")");
			columnLines.add(line.toString());
		}
		
		sb.append(StringUtils.join(columnLines, ",\n"));
		sb.append("\n]");
		
		String result = sb.toString();
		if (cleanedColumnsCount > 0) {
			log.debug("Table '{}' prompt built: cleaned {} column names, total length: {} chars",
					cleanTableName, cleanedColumnsCount, result.length());
		}
		else {
			log.debug("Table '{}' prompt built: no column names needed cleaning, total length: {} chars",
					cleanTableName, result.length());
		}
		
		return result;
	}

	public static String buildNewSqlGeneratorPrompt(SqlGenerationDTO sqlGenerationDTO) {
		log.debug("Building new SQL generator prompt - dialect: {}, query: {}",
				sqlGenerationDTO.getDialect(), sqlGenerationDTO.getQuery());
		
		String schemaInfo = buildMixMacSqlDbPrompt(sqlGenerationDTO.getSchemaDTO(), true);
		log.debug("Schema info built for SQL generation, length: {} chars", schemaInfo.length());
		
		Map<String, Object> params = new HashMap<>();
		params.put("dialect", sqlGenerationDTO.getDialect());
		params.put("question", sqlGenerationDTO.getQuery());
		params.put("schema_info", schemaInfo);
		params.put("evidence", sqlGenerationDTO.getEvidence());
		params.put("execution_description", sqlGenerationDTO.getExecutionDescription());
		
		log.debug("SQL generator prompt params - evidence: {}, execution_description: {}",
				sqlGenerationDTO.getEvidence() != null ? "provided" : "null",
				sqlGenerationDTO.getExecutionDescription() != null ? "provided" : "null");
		
		String result = PromptConstant.getNewSqlGeneratorPromptTemplate().render(params);
		log.debug("SQL generator prompt rendered, total length: {} chars", result.length());
		return result;
	}

	public static String buildSemanticConsistenPrompt(SemanticConsistencyDTO semanticConsistencyDTO) {
		log.debug("Building semantic consistency prompt - dialect: {}, user_query: {}",
				semanticConsistencyDTO.getDialect(), semanticConsistencyDTO.getUserQuery());
		
		Map<String, Object> params = new HashMap<>();
		params.put("dialect", semanticConsistencyDTO.getDialect());
		params.put("execution_description", semanticConsistencyDTO.getExecutionDescription());
		params.put("user_query", semanticConsistencyDTO.getUserQuery());
		params.put("evidence", semanticConsistencyDTO.getEvidence());
		params.put("schema_info", semanticConsistencyDTO.getSchemaInfo());
		params.put("sql", semanticConsistencyDTO.getSql());
		
		log.debug("Semantic consistency params - SQL length: {}, schema_info length: {}",
				semanticConsistencyDTO.getSql() != null ? semanticConsistencyDTO.getSql().length() : 0,
				semanticConsistencyDTO.getSchemaInfo() != null ? semanticConsistencyDTO.getSchemaInfo().length() : 0);
		
		String result = PromptConstant.getSemanticConsistencyPromptTemplate().render(params);
		log.debug("Semantic consistency prompt rendered, total length: {} chars", result.length());
		return result;
	}

	/**
	 * Build report generation prompt with custom prompt
	 * @param userRequirementsAndPlan user requirements and plan
	 * @param analysisStepsAndData analysis steps and data
	 * @param summaryAndRecommendations summary and recommendations
	 * @return built prompt
	 */
	public static String buildReportGeneratorPromptWithOptimization(String userRequirementsAndPlan,
			String analysisStepsAndData, String summaryAndRecommendations, List<UserPromptConfig> optimizationConfigs,
			boolean plainReport) {

		Map<String, Object> params = new HashMap<>();
		params.put("user_requirements_and_plan", userRequirementsAndPlan);
		params.put("analysis_steps_and_data", analysisStepsAndData);
		params.put("summary_and_recommendations", summaryAndRecommendations);
		// html 模板的echarts示例
		if (!plainReport)
			params.put("json_example", cleanJsonExample);

		// Build optional optimization section content from user configs
		String optimizationSection = buildOptimizationSection(optimizationConfigs, params);
		params.put("optimization_section", optimizationSection);

		// Render using the chosen report generator template
		return (plainReport ? PromptConstant.getReportGeneratorPlainPromptTemplate()
				: PromptConstant.getReportGeneratorPromptTemplate())
			.render(params);
	}

	public static String buildSqlErrorFixerPrompt(SqlGenerationDTO sqlGenerationDTO) {
		log.debug("Building SQL error fixer prompt - dialect: {}, error SQL length: {}",
				sqlGenerationDTO.getDialect(),
				sqlGenerationDTO.getSql() != null ? sqlGenerationDTO.getSql().length() : 0);
		
		String schemaInfo = buildMixMacSqlDbPrompt(sqlGenerationDTO.getSchemaDTO(), true);
		log.debug("Schema info built for SQL error fixing, length: {} chars", schemaInfo.length());

		Map<String, Object> params = new HashMap<>();
		params.put("dialect", sqlGenerationDTO.getDialect());
		params.put("question", sqlGenerationDTO.getQuery());
		params.put("schema_info", schemaInfo);
		params.put("evidence", sqlGenerationDTO.getEvidence());
		params.put("error_sql", sqlGenerationDTO.getSql());
		params.put("error_message", sqlGenerationDTO.getExceptionMessage());
		params.put("execution_description", sqlGenerationDTO.getExecutionDescription());
		
		log.debug("SQL error fixer params - error_message: {}",
				sqlGenerationDTO.getExceptionMessage() != null ? "provided" : "null");

		String result = PromptConstant.getSqlErrorFixerPromptTemplate().render(params);
		log.debug("SQL error fixer prompt rendered, total length: {} chars", result.length());
		return result;
	}

	public static String buildBusinessKnowledgePrompt(String businessTerms) {
		Map<String, Object> params = new HashMap<>();
		if (StringUtils.isNotBlank(businessTerms))
			params.put("businessKnowledge", businessTerms);
		else
			params.put("businessKnowledge", "无");
		return PromptConstant.getBusinessKnowledgePromptTemplate().render(params);
	}

	// agentKnowledge
	public static String buildAgentKnowledgePrompt(String agentKnowledge) {
		Map<String, Object> params = new HashMap<>();
		if (StringUtils.isNotBlank(agentKnowledge))
			params.put("agentKnowledge", agentKnowledge);
		else
			params.put("agentKnowledge", "无");
		return PromptConstant.getAgentKnowledgePromptTemplate().render(params);
	}

	public static String buildSemanticModelPrompt(List<SemanticModel> semanticModels) {
		Map<String, Object> params = new HashMap<>();
		String semanticModel = CollectionUtils.isEmpty(semanticModels) ? ""
				: semanticModels.stream().map(SemanticModel::getPromptInfo).collect(Collectors.joining(";\n"));
		params.put("semanticModel", semanticModel);
		return PromptConstant.getSemanticModelPromptTemplate().render(params);
	}

	/**
	 * 构建优化提示词部分内容
	 * @param optimizationConfigs 优化配置列表
	 * @param params 模板参数（不再使用，保留签名兼容性）
	 * @return 优化部分的内容
	 */
	private static String buildOptimizationSection(List<UserPromptConfig> optimizationConfigs,
			Map<String, Object> params) {

		if (optimizationConfigs == null || optimizationConfigs.isEmpty()) {
			return "";
		}

		StringBuilder result = new StringBuilder();
		result.append("## 优化要求\n");

		for (UserPromptConfig config : optimizationConfigs) {
			// 不再走 ST 编译，直接作为纯文本拼接
			String optimizationContent = renderOptimizationPrompt(config.getOptimizationPrompt());
			if (!optimizationContent.trim().isEmpty()) {
				result.append("- ").append(optimizationContent).append("\n");
			}
		}

		return result.toString().trim();
	}

	/**
	 * 构建意图识别提示词
	 * @param multiTurn 多轮对话历史
	 * @param latestQuery 最新用户输入
	 * @return 意图识别提示词
	 */
	public static String buildIntentRecognitionPrompt(String multiTurn, String latestQuery) {
		Map<String, Object> params = new HashMap<>();
		params.put("multi_turn", multiTurn != null ? multiTurn : "(无)");
		params.put("latest_query", latestQuery);
		return PromptConstant.getIntentRecognitionPromptTemplate().render(params);
	}

	/**
	 * 构建查询处理提示词
	 * @param multiTurn 多轮对话历史
	 * @param latestQuery 最新用户输入
	 * @return 查询处理提示词
	 */
	public static String buildQueryEnhancePrompt(String multiTurn, String latestQuery, String evidence) {
		log.debug("Building query enhance prompt - latest query: {}, multiTurn: {}, evidence: {}",
				latestQuery, multiTurn != null ? "provided" : "null", evidence != null ? "provided" : "null");
		
		Map<String, Object> params = new HashMap<>();
		params.put("multi_turn", multiTurn != null ? multiTurn : "(无)");
		params.put("latest_query", latestQuery);
		if (StringUtils.isEmpty(evidence))
			params.put("evidence", "无");
		else
			params.put("evidence", evidence);
		params.put("current_time_info", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
		
		String result = PromptConstant.getQueryEnhancementPromptTemplate().render(params);
		log.debug("Query enhance prompt rendered, total length: {} chars", result.length());
		return result;
	}

	/**
	 * 构建可行性评估提示词
	 * @param canonicalQuery 规范化查询
	 * @param recalledSchema 召回的数据库Schema
	 * @param evidence 参考信息
	 * @param multiTurn 多轮对话历史
	 * @return 可行性评估提示词
	 */
	public static String buildFeasibilityAssessmentPrompt(String canonicalQuery, SchemaDTO recalledSchema,
			String evidence, String multiTurn) {
		log.debug("Building feasibility assessment prompt - canonical query: {}, tables count: {}",
				canonicalQuery, recalledSchema != null ? recalledSchema.getTable().size() : 0);
		
		Map<String, Object> params = new HashMap<>();
		String schemaInfo = buildMixMacSqlDbPrompt(recalledSchema, true);
		log.debug("Schema info built for feasibility assessment, length: {} chars", schemaInfo.length());
		
		params.put("canonical_query", canonicalQuery != null ? canonicalQuery : "");
		params.put("recalled_schema", schemaInfo);
		params.put("evidence", evidence != null ? evidence : "");
		params.put("multi_turn", multiTurn != null ? multiTurn : "(无)");
		
		String result = PromptConstant.getFeasibilityAssessmentPromptTemplate().render(params);
		log.debug("Feasibility assessment prompt rendered, total length: {} chars", result.length());
		return result;
	}

	/**
	 * 构建查询重写提示词
	 * @param multiTurn 多轮对话历史
	 * @param latestQuery 最新用户输入
	 * @return 查询重写提示词
	 */
	public static String buildEvidenceQueryRewritePrompt(String multiTurn, String latestQuery) {
		Map<String, Object> params = new HashMap<>();
		params.put("multi_turn", multiTurn != null ? multiTurn : "(无)");
		params.put("latest_query", latestQuery);
		return PromptConstant.getEvidenceQueryRewritePromptTemplate().render(params);
	}

	/**
	 * 渲染优化提示词（纯文本，不再走 ST 编译）
	 * @param optimizationPrompt 优化提示词内容
	 * @return 原始内容
	 */
	private static String renderOptimizationPrompt(String optimizationPrompt) {
		return optimizationPrompt == null ? "" : optimizationPrompt;
	}

}
