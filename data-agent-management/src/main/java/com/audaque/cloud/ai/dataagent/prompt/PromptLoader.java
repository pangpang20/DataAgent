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

import com.audaque.cloud.ai.dataagent.properties.DataAgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt loader, used to load prompt templates from file system
 * 支持从外部目录或JAR内部加载Prompt模板
 *
 */
@Slf4j
public class PromptLoader {

	/**
	 * 外部Prompt目录路径，通过DataAgentProperties配置
	 * 优先级: application.yml > 环境变量 DATAAGENT_PROMPT_DIR > 系统属性 dataagent.prompt.dir
	 */
	private static String externalPromptDir;
	
	/**
	 * 初始化外部Prompt目录配置
	 * 此方法由Spring容器启动时调用
	 * @param properties DataAgent配置属性
	 */
	public static void initialize(DataAgentProperties properties) {
		if (properties != null && properties.getPrompt() != null) {
			String configuredDir = properties.getPrompt().getExternalDir();
			
			// 优先使用application.yml中的配置
			if (StringUtils.hasText(configuredDir)) {
				externalPromptDir = configuredDir.trim();
				log.info("Using external prompt directory from application.yml: {}", externalPromptDir);
				return;
			}
		}
		
		// 回退到环境变量
		String envDir = System.getenv("DATAAGENT_PROMPT_DIR");
		if (StringUtils.hasText(envDir)) {
			externalPromptDir = envDir.trim();
			log.info("Using external prompt directory from environment variable: {}", externalPromptDir);
			return;
		}
		
		// 最后尝试系统属性
		String sysDir = System.getProperty("dataagent.prompt.dir");
		if (StringUtils.hasText(sysDir)) {
			externalPromptDir = sysDir.trim();
			log.info("Using external prompt directory from system property: {}", externalPromptDir);
			return;
		}
		
		// 未配置任何外部目录
		externalPromptDir = null;
		log.debug("No external prompt directory configured, will use internal resources");
	}
	
	private static final String PROMPT_PATH_PREFIX = "prompts/";

	private static final ConcurrentHashMap<String, String> promptCache = new ConcurrentHashMap<>();

	/**
	 * Load prompt template from file
	 * 加载顺序:
	 * 1. 如果配置了外部Prompt目录，优先从外部目录加载
	 * 2. 如果外部文件不存在，回退到JAR内部资源
	 * @param promptName prompt file name (without path and extension)
	 * @return prompt content
	 */
	public static String loadPrompt(String promptName) {
		return promptCache.computeIfAbsent(promptName, name -> {
			// 1. 尝试从外部目录加载
			if (externalPromptDir != null) {
				String externalContent = loadFromExternalDir(name);
				if (externalContent != null) {
					return externalContent;
				}
			}
			
			// 2. 回退到JAR内部资源
			return loadFromInternalResource(name);
		});
	}
	
	/**
	 * 从外部目录加载Prompt文件
	 * @param promptName prompt名称
	 * @return prompt内容，如果文件不存在则返回null
	 */
	private static String loadFromExternalDir(String promptName) {
		try {
			Path externalFile = Paths.get(externalPromptDir, promptName + ".txt");
			
			if (Files.exists(externalFile) && Files.isRegularFile(externalFile)) {
				String content = Files.readString(externalFile, StandardCharsets.UTF_8);
				log.info("Successfully loaded prompt '{}' from external directory: {}", 
						promptName, externalFile.toAbsolutePath());
				return content;
			} else {
				log.debug("External prompt file not found: {}, will fallback to internal resource", 
						externalFile.toAbsolutePath());
				return null;
			}
		} catch (IOException e) {
			log.warn("Failed to load external prompt '{}': {}, will fallback to internal resource", 
					promptName, e.getMessage());
			return null;
		}
	}
	
	/**
	 * 从JAR内部资源加载Prompt文件
	 * @param promptName prompt名称
	 * @return prompt内容
	 */
	private static String loadFromInternalResource(String promptName) {
		String fileName = PROMPT_PATH_PREFIX + promptName + ".txt";
		// 使用本类的类加载器获取资源（避免jar包中无法获取资源）
		try (InputStream inputStream = PromptLoader.class.getClassLoader().getResourceAsStream(fileName)) {
			if (inputStream == null) {
				throw new IOException("Resource not found: " + fileName);
			}
			String content = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
			log.debug("Successfully loaded prompt '{}' from internal resource: {}", promptName, fileName);
			return content;
		}
		catch (IOException e) {
			log.error("加载提示词失败！{}", e.getMessage(), e);
			throw new RuntimeException("加载提示词失败: " + promptName, e);
		}
	}

	/**
	 * Clear prompt cache
	 * 清空缓存后，下次加载将重新读取文件（用于热更新Prompt）
	 */
	public static void clearCache() {
		int cacheSize = promptCache.size();
		promptCache.clear();
		log.info("Prompt cache cleared, {} prompts removed", cacheSize);
	}
	
	/**
	 * Reload specific prompt (clear cache and reload)
	 * @param promptName prompt name to reload
	 */
	public static void reloadPrompt(String promptName) {
		promptCache.remove(promptName);
		log.info("Prompt '{}' cache cleared, will reload on next access", promptName);
	}

	/**
	 * Get cache size
	 * @return number of prompts in cache
	 */
	public static int getCacheSize() {
		return promptCache.size();
	}
	
	/**
	 * Get external prompt directory path
	 * @return external directory path, null if not configured
	 */
	public static String getExternalPromptDir() {
		return externalPromptDir;
	}

}
