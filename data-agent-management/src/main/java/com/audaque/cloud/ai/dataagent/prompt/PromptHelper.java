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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.audaque.cloud.ai.dataagent.util.ReportTemplateUtil.cleanJsonExample;

@Slf4j
public class PromptHelper {

	/**
	 * Schema compression options
	 */
	public static class SchemaCompressionOptions {
		private boolean removeDescription = false;
		private boolean removeForeignKeys = false;
		private boolean removeExamples = false;
		private boolean enableSmartFilter = false;
		private String executionDescription = null;

		public static SchemaCompressionOptions none() {
			return new SchemaCompressionOptions();
		}

		public static SchemaCompressionOptions smart(String executionDescription) {
			SchemaCompressionOptions options = new SchemaCompressionOptions();
			options.enableSmartFilter = true;
			options.executionDescription = executionDescription;
			options.removeDescription = true; // Smart mode enables description removal
			options.removeExamples = false; // Keep examples for better SQL generation
			return options;
		}

		public static SchemaCompressionOptions full() {
			SchemaCompressionOptions options = new SchemaCompressionOptions();
			options.removeDescription = true;
			options.removeForeignKeys = true;
			options.removeExamples = true;
			return options;
		}

		public SchemaCompressionOptions withRemoveDescription(boolean remove) {
			this.removeDescription = remove;
			return this;
		}

		public SchemaCompressionOptions withRemoveForeignKeys(boolean remove) {
			this.removeForeignKeys = remove;
			return this;
		}

		public SchemaCompressionOptions withRemoveExamples(boolean remove) {
			this.removeExamples = remove;
			return this;
		}
	}

	/**
	 * Clean quotes from string to avoid ST4 template parsing conflicts
	 * 
	 * @param str original string
	 * @return cleaned string
	 */
	private static String cleanQuotes(String str) {
		if (str == null) {
			return null;
		}
		// Remove leading and trailing double quotes
		return str.replaceAll("^\"", "").replaceAll("\"$", "");
	}

	/**
	 * Extract referenced table and column names from execution description
	 * 
	 * @param executionDescription the current execution step description
	 * @return set of referenced entity names (table names and
	 *         "tableName.columnName")
	 */
	private static Set<String> extractReferencedEntities(String executionDescription) {
		if (StringUtils.isBlank(executionDescription)) {
			log.debug("No execution description provided for entity extraction");
			return Collections.emptySet();
		}

		log.debug("Extracting referenced entities from execution description: {}", executionDescription);
		Set<String> referencedEntities = new HashSet<>();

		// Pattern 1: Extract words enclosed in backticks or quotes (common in natural
		// language)
		// Example: "从 `orders` 表查询" or 'from "products" table'
		Pattern quotedPattern = Pattern.compile("[`'\"]([a-zA-Z0-9_一-龥]+)[`'\"]");
		Matcher quotedMatcher = quotedPattern.matcher(executionDescription);
		while (quotedMatcher.find()) {
			String entity = quotedMatcher.group(1).toLowerCase();
			referencedEntities.add(entity);
			log.debug("Extracted quoted entity: {}", entity);
		}

		// Pattern 2: Extract table.column references
		// Example: "使用 orders.order_date" or "join products.product_id"
		Pattern tableColumnPattern = Pattern.compile("\\b([a-zA-Z0-9_]+)\\.([a-zA-Z0-9_]+)\\b");
		Matcher tableColumnMatcher = tableColumnPattern.matcher(executionDescription);
		while (tableColumnMatcher.find()) {
			String tableName = tableColumnMatcher.group(1).toLowerCase();
			String columnName = tableColumnMatcher.group(2).toLowerCase();
			referencedEntities.add(tableName);
			referencedEntities.add(tableName + "." + columnName);
			log.debug("Extracted table.column reference: {}.{}", tableName, columnName);
		}

		// Pattern 3: Common SQL keywords followed by table/column names
		// Example: "FROM orders", "JOIN products", "GROUP BY category"
		Pattern sqlKeywordPattern = Pattern.compile("\\b(?:FROM|JOIN|INTO|UPDATE|TABLE)\\s+([a-zA-Z0-9_]+)\\b",
				Pattern.CASE_INSENSITIVE);
		Matcher sqlKeywordMatcher = sqlKeywordPattern.matcher(executionDescription);
		while (sqlKeywordMatcher.find()) {
			String entity = sqlKeywordMatcher.group(1).toLowerCase();
			// Filter out common SQL keywords
			if (!isSqlKeyword(entity)) {
				referencedEntities.add(entity);
				log.debug("Extracted entity after SQL keyword: {}", entity);
			}
		}

		// Pattern 4: Chinese patterns like "订单表" (table name + '表' suffix)
		Pattern chineseTablePattern = Pattern.compile("([a-zA-Z0-9_一-龥]+)[表]");
		Matcher chineseTableMatcher = chineseTablePattern.matcher(executionDescription);
		while (chineseTableMatcher.find()) {
			String entity = chineseTableMatcher.group(1).toLowerCase();
			referencedEntities.add(entity);
			log.debug("Extracted Chinese table reference: {}", entity);
		}

		log.info("Extracted {} referenced entities from execution description: {}",
				referencedEntities.size(), referencedEntities);
		return referencedEntities;
	}

	/**
	 * Check if a word is a common SQL keyword
	 */
	private static boolean isSqlKeyword(String word) {
		Set<String> keywords = Set.of("select", "where", "and", "or", "group", "order",
				"having", "limit", "offset", "as", "on", "by", "in", "like", "between",
				"sum", "count", "avg", "max", "min", "distinct", "all");
		return keywords.contains(word.toLowerCase());
	}

	/**
	 * Filter schema based on referenced entities
	 * 
	 * @param schemaDTO          original schema
	 * @param referencedEntities referenced entity names
	 * @return filtered schema (creates a copy)
	 */
	private static SchemaDTO filterSchemaByReferences(SchemaDTO schemaDTO, Set<String> referencedEntities) {
		if (referencedEntities.isEmpty()) {
			log.warn("No referenced entities found, returning original schema");
			return schemaDTO;
		}

		log.debug("Filtering schema with {} tables based on {} referenced entities",
				schemaDTO.getTable().size(), referencedEntities.size());

		// Find all referenced tables (direct references)
		Set<String> referencedTables = new HashSet<>();
		for (TableDTO table : schemaDTO.getTable()) {
			String tableLower = table.getName().toLowerCase();
			if (referencedEntities.contains(tableLower)) {
				referencedTables.add(tableLower);
				log.debug("Table directly referenced: {}", table.getName());
			}
		}

		// Add related tables via foreign keys
		Set<String> relatedTables = findRelatedTablesByForeignKeys(schemaDTO, referencedTables);
		referencedTables.addAll(relatedTables);
		log.info("Total tables after foreign key expansion: {} (added {} related tables)",
				referencedTables.size(), relatedTables.size());

		// Create filtered schema
		SchemaDTO filteredSchema = new SchemaDTO();
		filteredSchema.setName(schemaDTO.getName());

		List<TableDTO> filteredTables = new ArrayList<>();
		for (TableDTO table : schemaDTO.getTable()) {
			if (referencedTables.contains(table.getName().toLowerCase())) {
				// Filter columns within the table
				TableDTO filteredTable = filterTableColumns(table, referencedEntities);
				filteredTables.add(filteredTable);
				log.debug("Included table: {} with {} columns",
						filteredTable.getName(), filteredTable.getColumn().size());
			}
		}

		filteredSchema.setTable(filteredTables);

		// Filter foreign keys (only keep those between included tables)
		List<String> filteredForeignKeys = filterForeignKeys(schemaDTO.getForeignKeys(), referencedTables);
		filteredSchema.setForeignKeys(filteredForeignKeys);

		log.info("Schema filtering completed: {} -> {} tables, {} -> {} foreign keys",
				schemaDTO.getTable().size(), filteredTables.size(),
				schemaDTO.getForeignKeys() != null ? schemaDTO.getForeignKeys().size() : 0,
				filteredForeignKeys.size());

		return filteredSchema;
	}

	/**
	 * Find related tables through foreign key relationships
	 */
	private static Set<String> findRelatedTablesByForeignKeys(SchemaDTO schemaDTO, Set<String> baseTables) {
		Set<String> relatedTables = new HashSet<>();
		if (CollectionUtils.isEmpty(schemaDTO.getForeignKeys())) {
			return relatedTables;
		}

		for (String fk : schemaDTO.getForeignKeys()) {
			// Foreign key format: "table1.column1 = table2.column2"
			String[] parts = fk.split("=");
			if (parts.length != 2)
				continue;

			String[] left = parts[0].trim().split("\\.");
			String[] right = parts[1].trim().split("\\.");
			if (left.length != 2 || right.length != 2)
				continue;

			String leftTable = left[0].toLowerCase();
			String rightTable = right[0].toLowerCase();

			// If one side is referenced, include the other side
			if (baseTables.contains(leftTable) && !baseTables.contains(rightTable)) {
				relatedTables.add(rightTable);
				log.debug("Added related table via foreign key: {} (from {})", rightTable, leftTable);
			} else if (baseTables.contains(rightTable) && !baseTables.contains(leftTable)) {
				relatedTables.add(leftTable);
				log.debug("Added related table via foreign key: {} (from {})", leftTable, rightTable);
			}
		}

		return relatedTables;
	}

	/**
	 * Filter columns within a table based on referenced entities
	 */
	private static TableDTO filterTableColumns(TableDTO originalTable, Set<String> referencedEntities) {
		TableDTO filteredTable = new TableDTO();
		filteredTable.setName(originalTable.getName());
		filteredTable.setDescription(originalTable.getDescription());
		filteredTable.setPrimaryKeys(originalTable.getPrimaryKeys());

		String tableLower = originalTable.getName().toLowerCase();
		List<ColumnDTO> filteredColumns = new ArrayList<>();

		for (ColumnDTO column : originalTable.getColumn()) {
			String columnLower = column.getName().toLowerCase();
			String fullName = tableLower + "." + columnLower;

			// Include column if:
			// 1. It's a primary key (always important)
			// 2. It's explicitly referenced
			// 3. No specific column references for this table (include all columns)
			boolean isPrimaryKey = originalTable.getPrimaryKeys() != null &&
					originalTable.getPrimaryKeys().contains(column.getName());
			boolean isReferenced = referencedEntities.contains(columnLower) ||
					referencedEntities.contains(fullName);
			boolean hasTableLevelReference = referencedEntities.stream()
					.noneMatch(e -> e.startsWith(tableLower + "."));

			if (isPrimaryKey || isReferenced || hasTableLevelReference) {
				filteredColumns.add(column);
			}
		}

		// If no columns matched, include all columns (safety fallback)
		if (filteredColumns.isEmpty()) {
			log.warn("No columns matched for table {}, including all columns", originalTable.getName());
			filteredColumns.addAll(originalTable.getColumn());
		}

		filteredTable.setColumn(filteredColumns);
		return filteredTable;
	}

	/**
	 * Filter foreign keys to only include relationships between included tables
	 */
	private static List<String> filterForeignKeys(List<String> foreignKeys, Set<String> includedTables) {
		if (CollectionUtils.isEmpty(foreignKeys)) {
			return new ArrayList<>();
		}

		List<String> filtered = new ArrayList<>();
		for (String fk : foreignKeys) {
			// Foreign key format: "table1.column1 = table2.column2"
			String[] parts = fk.split("=");
			if (parts.length != 2)
				continue;

			String[] left = parts[0].trim().split("\\.");
			String[] right = parts[1].trim().split("\\.");
			if (left.length != 2 || right.length != 2)
				continue;

			String leftTable = left[0].toLowerCase();
			String rightTable = right[0].toLowerCase();

			// Only keep if both tables are included
			if (includedTables.contains(leftTable) && includedTables.contains(rightTable)) {
				filtered.add(fk);
			}
		}

		return filtered;
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
		} else {
			params.put("evidence", evidence);
			log.debug("Evidence provided, length: {} chars", evidence.length());
		}

		// 渲染模板
		String renderedPrompt = PromptConstant.getMixSelectorPromptTemplate().render(params);
		log.debug("Mix selector prompt rendered successfully, total length: {} chars", renderedPrompt.length());

		return renderedPrompt;
	}

	public static String buildMixMacSqlDbPrompt(SchemaDTO schemaDTO, Boolean withColumnType) {
		return buildMixMacSqlDbPrompt(schemaDTO, withColumnType, SchemaCompressionOptions.none());
	}

	/**
	 * Build schema information with compression options
	 * 
	 * @param schemaDTO          schema data
	 * @param withColumnType     whether to include column types
	 * @param compressionOptions compression options
	 * @return formatted schema string
	 */
	public static String buildMixMacSqlDbPrompt(SchemaDTO schemaDTO, Boolean withColumnType,
			SchemaCompressionOptions compressionOptions) {
		log.debug("Building schema prompt - DB: {}, tables: {}, withColumnType: {}, compression enabled: {}",
				schemaDTO.getName(), schemaDTO.getTable().size(), withColumnType,
				compressionOptions.enableSmartFilter);

		// Apply smart filtering if enabled
		SchemaDTO workingSchema = schemaDTO;
		if (compressionOptions.enableSmartFilter &&
				StringUtils.isNotBlank(compressionOptions.executionDescription)) {
			log.debug("Applying smart schema filtering based on execution description");
			Set<String> referencedEntities = extractReferencedEntities(compressionOptions.executionDescription);
			if (!referencedEntities.isEmpty()) {
				workingSchema = filterSchemaByReferences(schemaDTO, referencedEntities);
				log.info("Schema filtered: {} -> {} tables",
						schemaDTO.getTable().size(), workingSchema.getTable().size());
			} else {
				log.warn("No entities extracted, using original schema");
			}
		}

		StringBuilder sb = new StringBuilder();
		sb.append("【DB_ID】 ").append(workingSchema.getName() == null ? "" : workingSchema.getName()).append("\n");

		// Build table information
		for (int i = 0; i < workingSchema.getTable().size(); i++) {
			TableDTO tableDTO = workingSchema.getTable().get(i);
			log.debug("Processing table {}/{}: name='{}', columns count: {}",
					i + 1, workingSchema.getTable().size(), tableDTO.getName(), tableDTO.getColumn().size());

			sb.append(buildMixMacSqlTablePrompt(tableDTO, withColumnType, compressionOptions)).append("\n");
		}

		// Handle foreign keys
		if (!compressionOptions.removeForeignKeys && CollectionUtils.isNotEmpty(workingSchema.getForeignKeys())) {
			log.debug("Adding {} foreign keys to schema", workingSchema.getForeignKeys().size());
			sb.append("【Foreign keys】\n").append(StringUtils.join(workingSchema.getForeignKeys(), "\n"));
		} else if (compressionOptions.removeForeignKeys) {
			log.debug("Foreign keys removed by compression option");
		} else {
			log.debug("No foreign keys in schema");
		}

		String result = sb.toString();
		log.debug("Schema prompt built successfully, total length: {} chars", result.length());
		return result;
	}

	public static String buildMixMacSqlTablePrompt(TableDTO tableDTO, Boolean withColumnType) {
		return buildMixMacSqlTablePrompt(tableDTO, withColumnType, SchemaCompressionOptions.none());
	}

	/**
	 * Build table prompt with compression options
	 * 
	 * @param tableDTO           table data
	 * @param withColumnType     whether to include column types
	 * @param compressionOptions compression options
	 * @return formatted table string
	 */
	public static String buildMixMacSqlTablePrompt(TableDTO tableDTO, Boolean withColumnType,
			SchemaCompressionOptions compressionOptions) {
		// Record original table name (before cleaning)
		String originalTableName = tableDTO.getName();
		log.debug(
				"Building table prompt - original table name: '{}', columns: {}, compression: removeDesc={}, removeExamples={}",
				originalTableName, tableDTO.getColumn().size(),
				compressionOptions.removeDescription, compressionOptions.removeExamples);

		StringBuilder sb = new StringBuilder();
		// Clean quotes in table name to avoid ST4 template parsing conflicts
		String cleanTableName = cleanQuotes(tableDTO.getName());
		String cleanTableDesc = cleanQuotes(tableDTO.getDescription());

		// Log if table name was cleaned
		if (!originalTableName.equals(cleanTableName)) {
			log.debug("Table name cleaned: '{}' -> '{}'", originalTableName, cleanTableName);
		}

		sb.append("# Table: ").append(cleanTableName);

		// Conditionally add description based on compression options
		if (!compressionOptions.removeDescription &&
				!StringUtils.equals(cleanTableName, cleanTableDesc) &&
				StringUtils.isNotBlank(cleanTableDesc)) {
			sb.append(", ").append(cleanTableDesc);
			log.debug("Table description included");
		} else if (compressionOptions.removeDescription) {
			log.debug("Table description removed by compression option");
		}
		sb.append("\n");

		sb.append("[\n");
		List<String> columnLines = new ArrayList<>();
		int cleanedColumnsCount = 0;

		for (ColumnDTO columnDTO : tableDTO.getColumn()) {
			StringBuilder line = new StringBuilder();
			// Clean quotes in column name
			String originalColumnName = columnDTO.getName();
			String cleanColumnName = cleanQuotes(columnDTO.getName());
			String cleanColumnDesc = cleanQuotes(columnDTO.getDescription());

			// Log if column name was cleaned
			if (!originalColumnName.equals(cleanColumnName)) {
				log.debug("  Column name cleaned in table '{}': '{}' -> '{}'",
						cleanTableName, originalColumnName, cleanColumnName);
				cleanedColumnsCount++;
			}

			line.append("(")
					.append(cleanColumnName);

			// Add column type if requested
			if (BooleanUtils.isTrue(withColumnType)) {
				String columnType = StringUtils.isNotBlank(columnDTO.getType()) ? columnDTO.getType() : "";
				line.append(":").append(columnType.toUpperCase(Locale.ROOT));
			}

			// Conditionally add description based on compression options
			if (!compressionOptions.removeDescription &&
					!StringUtils.equals(cleanColumnDesc, cleanColumnName) &&
					StringUtils.isNotBlank(cleanColumnDesc)) {
				line.append(", ").append(cleanColumnDesc);
			}

			// Add primary key marker if applicable
			if (CollectionUtils.isNotEmpty(tableDTO.getPrimaryKeys())
					&& tableDTO.getPrimaryKeys().contains(columnDTO.getName())) {
				line.append(", Primary Key");
			}

			// Conditionally add example data based on compression options
			if (!compressionOptions.removeExamples) {
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
		} else {
			log.debug("Table '{}' prompt built: no column names needed cleaning, total length: {} chars",
					cleanTableName, result.length());
		}

		return result;
	}

	public static String buildNewSqlGeneratorPrompt(SqlGenerationDTO sqlGenerationDTO) {
		log.debug("Building new SQL generator prompt - dialect: {}, query: {}",
				sqlGenerationDTO.getDialect(), sqlGenerationDTO.getQuery());

		// Use smart schema filtering based on execution description
		SchemaCompressionOptions compressionOptions = StringUtils.isNotBlank(sqlGenerationDTO.getExecutionDescription())
				? SchemaCompressionOptions.smart(sqlGenerationDTO.getExecutionDescription())
				: SchemaCompressionOptions.none();

		String schemaInfo = buildMixMacSqlDbPrompt(sqlGenerationDTO.getSchemaDTO(), true, compressionOptions);
		log.debug("Schema info built for SQL generation with smart filtering, length: {} chars", schemaInfo.length());

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
	 * 
	 * @param userRequirementsAndPlan   user requirements and plan
	 * @param analysisStepsAndData      analysis steps and data
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

		// Use smart schema filtering based on execution description
		SchemaCompressionOptions compressionOptions = StringUtils.isNotBlank(sqlGenerationDTO.getExecutionDescription())
				? SchemaCompressionOptions.smart(sqlGenerationDTO.getExecutionDescription())
				: SchemaCompressionOptions.none();

		String schemaInfo = buildMixMacSqlDbPrompt(sqlGenerationDTO.getSchemaDTO(), true, compressionOptions);
		log.debug("Schema info built for SQL error fixing with smart filtering, length: {} chars", schemaInfo.length());

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
	 * 
	 * @param optimizationConfigs 优化配置列表
	 * @param params              模板参数（不再使用，保留签名兼容性）
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
	 * 
	 * @param multiTurn   多轮对话历史
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
	 * 
	 * @param multiTurn   多轮对话历史
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
	 * 
	 * @param canonicalQuery 规范化查询
	 * @param recalledSchema 召回的数据库Schema
	 * @param evidence       参考信息
	 * @param multiTurn      多轮对话历史
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
	 * 
	 * @param multiTurn   多轮对话历史
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
	 * 
	 * @param optimizationPrompt 优化提示词内容
	 * @return 原始内容
	 */
	private static String renderOptimizationPrompt(String optimizationPrompt) {
		return optimizationPrompt == null ? "" : optimizationPrompt;
	}

}
