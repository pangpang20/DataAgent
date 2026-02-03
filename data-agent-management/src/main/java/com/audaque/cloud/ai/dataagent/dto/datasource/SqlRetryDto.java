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
package com.audaque.cloud.ai.dataagent.dto.datasource;

/**
 * SQL retry context with error type classification
 * 
 * @param reason         error reason description
 * @param semanticFail   indicates semantic consistency check failure
 * @param sqlExecuteFail indicates SQL execution failure
 * @param errorType      specific error type: SYNTAX, SEMANTIC, EXECUTION
 */
public record SqlRetryDto(String reason, boolean semanticFail, boolean sqlExecuteFail, ErrorType errorType) {

	/**
	 * Error type enumeration for fine-grained retry control
	 */
	public enum ErrorType {
		SYNTAX, // SQL syntax error (e.g., LLM output format issues)
		SEMANTIC, // Semantic consistency check failure
		EXECUTION, // SQL execution error (e.g., non-existent fields, syntax conflicts)
		UNKNOWN // Unknown or unclassified error
	}

	public static SqlRetryDto semantic(String reason) {
		return new SqlRetryDto(reason, true, false, ErrorType.SEMANTIC);
	}

	public static SqlRetryDto sqlExecute(String reason) {
		return new SqlRetryDto(reason, false, true, ErrorType.EXECUTION);
	}

	public static SqlRetryDto sqlSyntax(String reason) {
		return new SqlRetryDto(reason, false, true, ErrorType.SYNTAX);
	}

	public static SqlRetryDto empty() {
		return new SqlRetryDto("", false, false, ErrorType.UNKNOWN);
	}

}
