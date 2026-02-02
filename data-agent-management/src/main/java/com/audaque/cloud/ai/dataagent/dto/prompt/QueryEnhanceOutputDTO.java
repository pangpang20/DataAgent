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
package com.audaque.cloud.ai.dataagent.dto.prompt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 查询增强输出 DTO
 * 对应 query-enhancement.txt 模板的输出
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)  // 忽略LLM返回的未知字段
public class QueryEnhanceOutputDTO {

	// 经LLM重写后的 规范化查询
	@JsonProperty("canonical_query")
	private String canonicalQuery;

	// 基于canonicalQuery的扩展查询
	@JsonProperty("expanded_queries")
	private List<String> expandedQueries;

}
