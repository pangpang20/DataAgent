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

public class MarkdownParserUtil {

	public static String extractText(String markdownCode) {
		String code = extractRawText(markdownCode);
		// Correctly handle various newline character types: \r\n, \n, \r, but maintain
		// compatibility with NewLineParser.format()
		return code.replaceAll("\r\n", " ").replaceAll("\n", " ").replaceAll("\r", " ");
	}

	public static String extractRawText(String markdownCode) {
		// Find the start of a code block (3 or more backticks)
		int startIndex = -1;
		int delimiterLength = 0;
	
		for (int i = 0; i <= markdownCode.length() - 3; i++) {
			if (markdownCode.substring(i, i + 3).equals("```")) {
				startIndex = i;
				delimiterLength = 3;
				// Count additional backticks
				while (i + delimiterLength < markdownCode.length() && markdownCode.charAt(i + delimiterLength) == '`') {
					delimiterLength++;
				}
				break;
			}
		}
	
		if (startIndex == -1) {
			// No triple backtick code block found, try single backtick
			return removeSingleBackticks(markdownCode);
		}
	
		// Skip the opening delimiter and optional language specification
		int contentStart = startIndex + delimiterLength;
		while (contentStart < markdownCode.length() && markdownCode.charAt(contentStart) != '\n') {
			contentStart++;
		}
		if (contentStart < markdownCode.length() && markdownCode.charAt(contentStart) == '\n') {
			contentStart++; // Skip the newline after language spec
		}
	
		// Find the closing delimiter
		String closingDelimiter = "`".repeat(delimiterLength);
		int endIndex = markdownCode.indexOf(closingDelimiter, contentStart);
	
		if (endIndex == -1) {
			// No closing delimiter found, return from content start to end
			return markdownCode.substring(contentStart);
		}
	
		// Extract just the content between delimiters
		return markdownCode.substring(contentStart, endIndex);
	}
	
	/**
	 * Remove single backticks from the beginning and end of the text.
	 * This handles cases where LLM wraps SQL with single backticks like `SELECT ...`
	 * @param text The text that may have single backticks
	 * @return The text with leading and trailing single backticks removed
	 */
	private static String removeSingleBackticks(String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}
			
		String trimmed = text.trim();
			
		// Check if text starts and ends with single backtick
		if (trimmed.startsWith("`") && trimmed.endsWith("`") && trimmed.length() > 1) {
			// Remove the first and last backtick
			return trimmed.substring(1, trimmed.length() - 1);
		}
			
		return trimmed;
	}

}
