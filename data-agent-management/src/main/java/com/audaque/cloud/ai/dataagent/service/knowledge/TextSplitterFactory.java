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
package com.audaque.cloud.ai.dataagent.service.knowledge;

import com.audaque.cloud.ai.dataagent.enums.SplitterType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TextSplitterFactory {

	private final Map<String, TextSplitter> splitterMap;

	/**
	 * 根据类型字符串获取对应的 Splitter
	 * 
	 * @param type 前端传入的类型，例如 "token", "recursive"
	 * @return 对应的 TextSplitter 实例
	 */
	public TextSplitter getSplitter(String type) {
		log.debug("Attempting to get splitter for type: '{}'", type);
		log.debug("Available splitters in map: {}", splitterMap.keySet());
		
		// 1. 尝试直接获取
		TextSplitter splitter = splitterMap.get(type);
		log.debug("Direct lookup result for '{}': {}", type, splitter != null ? "found" : "null");

		// 2. 如果没找到，尝试返回默认的 token splitter
		if (splitter == null) {
			log.warn("Splitter type '{}' not found, falling back to default 'token' splitter", type);
			
			// 先尝试通过 SplitterType.TOKEN.getValue() 获取
			String defaultValue = SplitterType.TOKEN.getValue();
			log.debug("Trying to get default splitter with key: '{}'", defaultValue);
			splitter = splitterMap.get(defaultValue);
			log.debug("Default splitter lookup result: {}", splitter != null ? "found" : "null");
			
			// 如果还是找不到，尝试直接获取 "textSplitter" 这个 key
			if (splitter == null) {
				log.debug("Trying alternative key 'textSplitter'");
				splitter = splitterMap.get("textSplitter");
				log.debug("Alternative splitter lookup result: {}", splitter != null ? "found" : "null");
			}
			
			// 如果仍然找不到，抛出异常
			if (splitter == null) {
				log.error("No TextSplitter available! Available keys: {}", splitterMap.keySet());
				throw new IllegalStateException("No TextSplitter available. Available types: " + splitterMap.keySet());
			}
		}

		return splitter;
	}

}
