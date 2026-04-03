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
package com.audaque.cloud.ai.dataagent.service.aimodelconfig;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 模型特征探测器
 *
 * 根据模型名称或提供商自动识别模型特征，用于自动路由到合适的提示词模式。
 *
 * 设计原则：
 * 1. 开源模型/本地部署模型 -> 使用简化提示词（lite prompt）
 * 2. 商业闭源模型 -> 使用完整提示词（full prompt）
 *
 * 判定逻辑：
 * - 开源模型通常上下文有限、推理能力较弱，需要更多 few-shot 示例和简化结构
 * - 商业模型如 GPT-4、Claude 等上下文大、能力强，可使用完整提示词
 */
@Slf4j
@Component
public class ModelCharacterDetector {

	/**
	 * 已知开源模型/本地部署模型的提供商列表
	 * 这些提供商的模型通常：
	 * 1. 上下文窗口有限
	 * 2. 需要更多示例引导
	 * 3. 对复杂指令遵循能力较弱
	 */
	private static final Set<String> OPEN_SOURCE_PROVIDERS = Set.of(
			// 阿里通义千问系列
			"qwen", "qwen-local", "aliyun", "dashscope",
			// GLM 系列（智谱 AI）
			"glm", "zhipu", "chatglm", "coglm",
			// Llama 系列（Meta）
			"llama", "llama2", "llama3", "llama3.1", "llama4",
			// DeepSeek 深度求索
			"deepseek",
			// Baichuan 百川智能
			"baichuan",
			// Yi 零一万物
			"yi", "01ai",
			// InternLM 书生·浦语
			"internlm", "xverse",
			// ChatGLM
			"chatglm", "chatglm2", "chatglm3",
			// Ollama（本地运行开源模型）
			"ollama",
			// LocalAI / vLLM 等本地部署
			"localai", "vllm", "local");

	/**
	 * 已知开源模型/本地部署模型的模型名称关键词
	 * 用于补充判断，特别是当提供商名称不标准时
	 */
	private static final Set<String> OPEN_SOURCE_MODEL_KEYWORDS = Set.of(
			// Qwen 系列
			"qwen", "qwq",
			// Llama 系列
			"llama",
			// GLM 系列
			"glm", "chatglm",
			// DeepSeek 系列
			"deepseek",
			// Baichuan 系列
			"baichuan",
			// Yi 系列
			"yi-", "yi_",
			// InternLM 系列
			"internlm",
			// Xverse 系列
			"xverse",
			// MiniCPM
			"minicpm",
			// Phi 系列（微软）
			"phi",
			// Mistral 系列
			"mistral", "mixtral",
			// Falcon 系列
			"falcon");

	/**
	 * 已知商业闭源模型的提供商列表
	 * 这些提供商的模型通常：
	 * 1. 上下文窗口大
	 * 2. 指令遵循能力强
	 * 3. 不需要额外的 few-shot 示例
	 */
	private static final Set<String> COMMERCIAL_PROVIDERS = Set.of(
			// OpenAI
			"openai",
			// Anthropic (Claude)
			"anthropic", "claude",
			// Google (Gemini)
			"google", "gemini",
			// Microsoft (Azure OpenAI)
			"azure", "microsoft");

	/**
	 * 已知商业闭源模型的模型名称关键词
	 */
	private static final Set<String> COMMERCIAL_MODEL_KEYWORDS = Set.of(
			// GPT 系列
			"gpt-4", "gpt-3.5", "gpt-3", "gpt4", "gpt35",
			// Claude 系列
			"claude",
			// Gemini 系列
			"gemini",
			// o1 系列
			"o1-preview", "o1-mini", "o1-");

	/**
	 * 检测是否为开源模型/本地部署模型
	 *
	 * @param provider 模型提供商
	 * @param modelName 模型名称
	 * @return true 如果是开源模型，需要轻量级提示词；false 如果是商业模型，可使用完整提示词
	 */
	public boolean isOpenSourceModel(String provider, String modelName) {
		if (provider == null || provider.isBlank()) {
			provider = "";
		}
		if (modelName == null || modelName.isBlank()) {
			modelName = "";
		}

		String providerLower = provider.toLowerCase().trim();
		String modelLower = modelName.toLowerCase().trim();

		log.debug("Detecting model character - provider: {}, model: {}", provider, modelName);

		// 1. 先检查提供商是否在开源列表中
		if (isOpenSourceProvider(providerLower)) {
			log.info("Detected open-source provider: {}, using lite prompt", provider);
			return true;
		}

		// 2. 再检查提供商是否在商业列表中
		if (isCommercialProvider(providerLower)) {
			log.info("Detected commercial provider: {}, using full prompt", provider);
			return false;
		}

		// 3. 提供商未知时，检查模型名称关键词
		if (containsOpenSourceKeyword(modelLower)) {
			log.info("Detected open-source model keyword in '{}', using lite prompt", modelName);
			return true;
		}

		if (containsCommercialKeyword(modelLower)) {
			log.info("Detected commercial model keyword in '{}', using full prompt", modelName);
			return false;
		}

		// 4. 都无法匹配时，默认使用完整提示词（保守策略）
		log.info("Unknown provider/model '{}'/ '{}', defaulting to full prompt (conservative strategy)",
				provider, modelName);
		return false;
	}

	/**
	 * 检查提供商是否为已知开源提供商
	 */
	private boolean isOpenSourceProvider(String providerLower) {
		return OPEN_SOURCE_PROVIDERS.contains(providerLower) ||
				OPEN_SOURCE_PROVIDERS.stream().anyMatch(providerLower::contains);
	}

	/**
	 * 检查提供商是否为已知商业提供商
	 */
	private boolean isCommercialProvider(String providerLower) {
		return COMMERCIAL_PROVIDERS.contains(providerLower) ||
				COMMERCIAL_PROVIDERS.stream().anyMatch(providerLower::contains);
	}

	/**
	 * 检查模型名称是否包含开源模型关键词
	 */
	private boolean containsOpenSourceKeyword(String modelLower) {
		return OPEN_SOURCE_MODEL_KEYWORDS.stream().anyMatch(modelLower::contains);
	}

	/**
	 * 检查模型名称是否包含商业模型关键词
	 */
	private boolean containsCommercialKeyword(String modelLower) {
		return COMMERCIAL_MODEL_KEYWORDS.stream().anyMatch(modelLower::contains);
	}

}
