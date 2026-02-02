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
package com.audaque.cloud.ai.dataagent.util;

import com.audaque.cloud.ai.dataagent.prompt.PromptConstant;
import com.audaque.cloud.ai.dataagent.service.llm.LlmService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON解析工具类，支持自动修复格式错误的JSON
 */
@Slf4j
@Component
@AllArgsConstructor
public class JsonParseUtil {

	private LlmService llmService;

	private static final int MAX_RETRY_COUNT = 3;

	private static final String THINK_END_TAG = "</think>";

	/**
	 * SQL关键字和DataAgent系统表名 - 用于过滤混乱的LLM输出
	 */
	private static final Set<String> SQL_KEYWORDS = Set.of(
		"select", "from", "where", "and", "or", "join", "on", "group", "by", 
		"order", "having", "limit", "offset", "insert", "update", "delete",
		"sum", "count", "avg", "max", "min", "between", "in", "like",
		"as", "left", "right", "inner", "outer", "full", "cross"
	);
	
	private static final Set<String> SYSTEM_TABLE_NAMES = Set.of(
		"agents", "agent_datasources", "agent_datasource_tables",
		"databases", "schemas", "chat_messages", "chat_sessions",
		"model_configs", "preset_questions", "user_prompt_configs",
		"semantic_models", "logical_relations", "business_terms"
	);

	public <T> T tryConvertToObject(String json, Class<T> clazz) {
		Assert.hasText(json, "Input JSON string cannot be null or empty");
		Assert.notNull(clazz, "Target class cannot be null");

		return tryConvertToObjectInternal(json, (mapper, currentJson) -> mapper.readValue(currentJson, clazz));
	}

	/**
	 * 尝试将JSON字符串转换为指定类型，支持TypeReference（如List<String>等复杂类型）
	 * @param json JSON字符串
	 * @param typeReference 类型引用
	 * @return 转换后的对象
	 */
	public <T> T tryConvertToObject(String json, TypeReference<T> typeReference) {
		Assert.hasText(json, "Input JSON string cannot be null or empty");
		Assert.notNull(typeReference, "TypeReference cannot be null");

		return tryConvertToObjectInternal(json, (mapper, currentJson) -> mapper.readValue(currentJson, typeReference));
	}

	/**
	 * 内部通用方法，用于JSON解析和修复
	 * @param json JSON字符串
	 * @param parser 解析器函数
	 * @return 转换后的对象
	 */
	private <T> T tryConvertToObjectInternal(String json, JsonParserFunction<T> parser) {
		log.info("Trying to convert JSON to object: {}", json);
		String currentJson = removeThinkTags(json);
		
		// 首先尝试预处理常见的自然语言格式
		currentJson = preprocessNaturalLanguage(currentJson);
		log.debug("After preprocessing: {}", currentJson);
		
		Exception lastException = null;
		ObjectMapper objectMapper = JsonUtil.getObjectMapper();

		try {
			return parser.parse(objectMapper, currentJson);
		}
		catch (JsonProcessingException e) {
			log.warn("Initial parsing failed, preparing to call LLM: {}", e.getMessage());
			lastException = e;
		}

		for (int i = 0; i < MAX_RETRY_COUNT; i++) {
			try {
				currentJson = callLlmToFix(currentJson,
						lastException != null ? lastException.getMessage() : "Unknown error");

				return parser.parse(objectMapper, currentJson);
			}
			catch (JsonProcessingException e) {
				lastException = e;
				log.warn("Still failed after {} fix attempt: {}", i + 1, e.getMessage());

				if (i == MAX_RETRY_COUNT - 1) {
					log.error("Finally failed after {} fix attempts", MAX_RETRY_COUNT);
					log.warn("Last fix result: {}", currentJson);
				}
			}
		}

		throw new IllegalArgumentException(
				String.format("Failed to parse JSON after %d LLM fix attempts", MAX_RETRY_COUNT), lastException);
	}

	/**
	 * 函数式接口，用于JSON解析
	 */
	@FunctionalInterface
	private interface JsonParserFunction<T> {

		T parse(ObjectMapper mapper, String json) throws JsonProcessingException;

	}

	private String callLlmToFix(String json, String errorMessage) {
		try {
			String prompt = PromptConstant.getJsonFixPromptTemplate()
				.render(Map.of("json_string", json, "error_message", errorMessage));

			Flux<ChatResponse> responseFlux = llmService.callUser(prompt);
			String fixedJson = llmService.toStringFlux(responseFlux)
				.collect(StringBuilder::new, StringBuilder::append)
				.map(StringBuilder::toString)
				.block();

			// 检查fixedJson是否为null
			if (fixedJson == null) {
				log.warn("LLM fix returned null, using original JSON");
				return json;
			}

			log.debug("LLM original return content: {}", fixedJson);

			// 移除think标签
			String cleanedJson = removeThinkTags(fixedJson);
			log.debug("Content after removing think tags: {}", cleanedJson);

			// 提取可能输出在Markdown代码块中的内容
			cleanedJson = MarkdownParserUtil.extractRawText(cleanedJson);
			log.debug("Content after extracting Markdown code blocks: {}", cleanedJson);

			// 确保返回的JSON不为null
			return cleanedJson != null ? cleanedJson : json;
		}
		catch (Exception e) {
			log.error("Exception occurred while calling LLM fix service", e);
			return json;
		}
	}

	/**
	 * 预处理常见的自然语言格式，尝试转换为JSON格式
	 * 处理类似 "表：ORDERS,PRODUCT_CATEGORIES  条件：..." 的格式
	 * 以及 "[Answer] \"orders\", \"products\"  【说明】..." 的格式
	 * 以及 LLM返回的混乱JSON格式
	 */
	private String preprocessNaturalLanguage(String text) {
		if (text == null || text.trim().isEmpty()) {
			return text;
		}
		
		String trimmed = text.trim();
		
		// 已经是JSON格式，直接返回
		if (trimmed.startsWith("[") && !trimmed.startsWith("[Answer]")) {
			// 检查是否是混乱的JSON格式（如 [{"QUERY_BUILDER","select...  ）
			if (trimmed.startsWith("[{") && !isValidJsonArrayOfObjects(trimmed)) {
				log.debug("Detected malformed JSON array with objects, attempting extraction");
				String extracted = extractTableNamesFromMalformedJson(trimmed);
				if (extracted != null) {
					log.info("Extracted table names from malformed JSON: {} -> {}", 
						trimmed.substring(0, Math.min(100, trimmed.length())) + "...", extracted);
					return extracted;
				}
			}
			return trimmed;
		}
		if (trimmed.startsWith("{")) {
			return trimmed;
		}
		
		// 处理 "[Answer] \"table1\", \"table2\"  【说明】..." 的格式
		if (trimmed.matches("(?i).*?\\[answer\\].*")) {
			log.debug("Detected [Answer] format");
			
			// 提取 [Answer] 和 【说明】 之间的内容
			String answerPart = trimmed.replaceFirst("(?i).*?\\[answer\\]\\s*", "").trim();
			
			// 移除后面的说明部分（如果存在）
			answerPart = answerPart.split("[【\\[]?[说明Explanation].*")[0].trim();
			
			// 提取所有带引号的值
			Pattern pattern = Pattern.compile("\"([^\"]+)\"");
			Matcher matcher = pattern.matcher(answerPart);
			List<String> tables = new ArrayList<>();
			while (matcher.find()) {
				tables.add(matcher.group(1));
			}
			
			if (!tables.isEmpty()) {
				// 构建JSON数组
				StringBuilder jsonArray = new StringBuilder("[");
				for (int i = 0; i < tables.size(); i++) {
					if (i > 0) {
						jsonArray.append(", ");
					}
					jsonArray.append("\"").append(tables.get(i)).append("\"");
				}
				jsonArray.append("]");
				
				String result = jsonArray.toString();
				log.info("Converted [Answer] format to JSON: {} -> {}", trimmed, result);
				return result;
			}
		}
		
		// 处理 "表：A,B,C" 或 "Tables: A, B, C" 的格式
		// 匹配中文或英文的 "表：" 或 "tables:" （不区分大小写）
		if (trimmed.matches("(?i).*?(表：|tables?:|table\\s*:).*")) {
			log.debug("Detected natural language format with table prefix");
			
			// 提取 "表：" 或 "tables:" 后面的部分
			String tablesPart = trimmed.replaceFirst("(?i).*?(表：|tables?:|table\\s*:)", "").trim();
			
			// 移除后面的条件部分（如果存在）
			tablesPart = tablesPart.split("(条件：|条件:|?条件|conditions?:)")[0].trim();
			
			// 按逗号分割表名
			String[] tables = tablesPart.split("[,，\\s]+");
			
			// 构建JSON数组
			StringBuilder jsonArray = new StringBuilder("[");
			for (int i = 0; i < tables.length; i++) {
				String table = tables[i].trim();
				if (!table.isEmpty()) {
					if (jsonArray.length() > 1) {
						jsonArray.append(", ");
					}
					jsonArray.append("\"").append(table).append("\"");
				}
			}
			jsonArray.append("]");
			
			String result = jsonArray.toString();
			log.info("Converted natural language format to JSON: {} -> {}", trimmed, result);
			return result;
		}
		
		// 处理逗号分隔的混合格式（如 "station, order_items, [station.map_to_table('province_city')], [products]"）
		// 这是最后的兜底处理：如果包含逗号且没有明显的JSON结构标记
		if (trimmed.contains(",") && !trimmed.contains(":") && !trimmed.contains("{")) {
			log.debug("Detected comma-separated mixed format");
			
			// 按逗号分割
			String[] parts = trimmed.split(",");
			List<String> tables = new ArrayList<>();
			
			for (String part : parts) {
				String cleaned = part.trim();
				
				// 移除外层方括号（如果存在）
				if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
					cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
				}
				
				// 提取函数调用中的表名（如 "station.map_to_table('province_city')" -> "station"）
				if (cleaned.contains(".") && cleaned.contains("(")) {
					// 提取点号前面的表名
					String tableName = cleaned.split("\\.")[0].trim();
					if (!tableName.isEmpty() && !tables.contains(tableName)) {
						tables.add(tableName);
					}
				} else if (!cleaned.isEmpty()) {
					// 普通表名
					tables.add(cleaned);
				}
			}
			
			if (!tables.isEmpty()) {
				// 构建JSON数组
				StringBuilder jsonArray = new StringBuilder("[");
				for (int i = 0; i < tables.size(); i++) {
					if (i > 0) {
						jsonArray.append(", ");
					}
					jsonArray.append("\"").append(tables.get(i)).append("\"");
				}
				jsonArray.append("]");
				
				String result = jsonArray.toString();
				log.info("Converted comma-separated mixed format to JSON: {} -> {}", trimmed, result);
				return result;
			}
		}
		
		// 处理单个表名的情况（如 "ORDERS" 或 "orders"）
		// 表名特征：字母和下划线组成，不包含空格、特殊字符
		if (trimmed.matches("^[A-Za-z][A-Za-z0-9_]*$")) {
			log.debug("Detected single table name format: {}", trimmed);
			String result = "[\"" + trimmed + "\"]";
			log.info("Converted single table name to JSON array: {} -> {}", trimmed, result);
			return result;
		}
		
		return trimmed;
	}

	/**
	 * 移除 </think> 标签及其之前的所有内容 逻辑：找到最后一个 </think> 结束标签，只保留它之后的部分
	 */
	private String removeThinkTags(String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}

		// 1. 查找最后一个结束标签的位置
		int lastEndTagIndex = text.lastIndexOf(THINK_END_TAG);

		if (lastEndTagIndex != -1) {
			log.debug("Found </think> tag, index position: {}", lastEndTagIndex);

			// 2. 计算截取点：结束标签的位置 + 标签本身的长度
			int contentStartIndex = lastEndTagIndex + THINK_END_TAG.length();

			// 3. 截取该点之后的所有内容
			String finalResult = text.substring(contentStartIndex).trim();

			log.debug("Content after truncating think tags: {}", finalResult);

			return finalResult;
		}

		// 如果没找到结束标签，说明可能没有思考过程，直接返回原文本（去除首尾空格）
		log.debug("Think end tag not found, returning original text");
		return text.trim();
	}

	/**
	 * 检查是否是有效的JSON数组（包含对象）
	 * 简单检查：确认格式类似 [{ "key": "value" }]
	 */
	private boolean isValidJsonArrayOfObjects(String json) {
		if (json == null || json.length() < 4) {
			return false;
		}
		
		// 快速检查：在第一个 { 后应该有 "key": 的模式
		// 如果是 [{"QUERY_BUILDER","select... 这种格式，就不是有效的
		int firstBrace = json.indexOf('{');
		if (firstBrace == -1) {
			return false;
		}
		
		// 查找第一个引号后是否跟着冒号（键值对标识）
		String afterBrace = json.substring(firstBrace + 1).trim();
		// 正常的JSON对象应该是 "key": value 的格式
		// 检查是否匹配 "xxx": 的模式
		Pattern validPattern = Pattern.compile("^\"[^\"]+\"\\s*:");
		return validPattern.matcher(afterBrace).find();
	}

	/**
	 * 从混乱的JSON格式中提取表名
	 * 处理类似 [{"QUERY_BUILDER","select...","STUDIES","products,agents..."}] 的格式
	 */
	private String extractTableNamesFromMalformedJson(String json) {
		try {
			// 提取所有看起来像表名的字符串
			// 表名特征：大写字母开头，可能包含下划线，不包含SQL关键字
			Pattern tablePattern = Pattern.compile("\"([A-Z][A-Z0-9_]*)\"(?!\\s*:)");
			Matcher matcher = tablePattern.matcher(json);
			
			List<String> potentialTables = new ArrayList<>();
			while (matcher.find()) {
				String candidate = matcher.group(1);
				// 过滤掉SQL关键字和系统表名
				if (!SQL_KEYWORDS.contains(candidate.toLowerCase()) 
					&& !SYSTEM_TABLE_NAMES.contains(candidate.toLowerCase())
					&& !candidate.contains("(") 
					&& candidate.length() > 1) {
					// 还要过滤一些明显不是表名的内容
					if (!candidate.equals("QUERY_BUILDER") 
						&& !candidate.equals("STUDIES") 
						&& !candidate.startsWith("INTER")
						&& !potentialTables.contains(candidate)) {
						potentialTables.add(candidate);
					}
				}
			}
			
			// 如果没找到大写表名，尝试查找小写表名
			if (potentialTables.isEmpty()) {
				Pattern lowerPattern = Pattern.compile("\"([a-z][a-z0-9_]*)\"(?!\\s*:)");
				Matcher lowerMatcher = lowerPattern.matcher(json);
				while (lowerMatcher.find()) {
					String candidate = lowerMatcher.group(1);
					if (!SQL_KEYWORDS.contains(candidate.toLowerCase()) 
						&& !SYSTEM_TABLE_NAMES.contains(candidate.toLowerCase())
						&& !candidate.contains("(")
						&& candidate.length() > 2
						&& !potentialTables.contains(candidate)) {
						potentialTables.add(candidate);
					}
				}
			}
			
			if (!potentialTables.isEmpty()) {
				StringBuilder jsonArray = new StringBuilder("[");
				for (int i = 0; i < potentialTables.size(); i++) {
					if (i > 0) {
						jsonArray.append(", ");
					}
					jsonArray.append("\"").append(potentialTables.get(i)).append("\"");
				}
				jsonArray.append("]");
				return jsonArray.toString();
			}
		} catch (Exception e) {
			log.debug("Failed to extract table names from malformed JSON", e);
		}
		return null;
	}

}
