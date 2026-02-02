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
package com.audaque.cloud.ai.dataagent.config;

import com.audaque.cloud.ai.dataagent.prompt.PromptLoader;
import com.audaque.cloud.ai.dataagent.properties.DataAgentProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Prompt加载器配置类
 * 在Spring容器启动时初始化PromptLoader的外部目录配置
 *
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(DataAgentProperties.class)
@RequiredArgsConstructor
public class PromptLoaderConfig {

	private final DataAgentProperties dataAgentProperties;

	@PostConstruct
	public void initializePromptLoader() {
		log.info("Initializing PromptLoader with DataAgentProperties...");
		PromptLoader.initialize(dataAgentProperties);
	}

}
