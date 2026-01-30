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

import com.audaque.cloud.ai.dataagent.dto.ModelConfigDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Service
public class DynamicModelFactory {

	@Value("${spring.ai.retry.max-attempts:5}")
	private int maxAttempts;

	@Value("${spring.ai.retry.initial-interval:2000}")
	private long initialInterval;

	@Value("${spring.ai.retry.multiplier:2.0}")
	private double multiplier;

	/**
	 * 统一使用 OpenAiChatModel，通过 baseUrl 实现多厂商兼容
	 */
	public ChatModel createChatModel(ModelConfigDTO config) {

		log.info("Creating NEW ChatModel instance. Provider: {}, Model: {}, BaseUrl: {}", config.getProvider(),
				config.getModelName(), config.getBaseUrl());
		// 1. 验证参数
		checkBasic(config);

		// 2. 构建 OpenAiApi (核心通讯对象)
		OpenAiApi.Builder apiBuilder = OpenAiApi.builder().apiKey(config.getApiKey()).baseUrl(config.getBaseUrl());

		if (StringUtils.hasText(config.getCompletionsPath())) {
			apiBuilder.completionsPath(config.getCompletionsPath());
		}
		OpenAiApi openAiApi = apiBuilder.build();

		// 3. 构建运行时选项 (设置默认的模型名称，如 "deepseek-chat" 或 "gpt-4")
		OpenAiChatOptions openAiChatOptions = OpenAiChatOptions.builder()
			.model(config.getModelName())
			.temperature(config.getTemperature())
			.maxTokens(config.getMaxTokens())
			.build();

		// 4. 创建自定义重试模板，支持 429 错误重试
		RetryTemplate retryTemplate = createRetryTemplate();

		// 5. 返回统一的 OpenAiChatModel，配置重试机制
		return OpenAiChatModel.builder()
				.openAiApi(openAiApi)
				.defaultOptions(openAiChatOptions)
				.retryTemplate(retryTemplate)
				.build();
	}

	private static void checkBasic(ModelConfigDTO config) {
		Assert.hasText(config.getBaseUrl(), "baseUrl must not be empty");
		Assert.hasText(config.getApiKey(), "apiKey must not be empty");
		Assert.hasText(config.getModelName(), "modelName must not be empty");
	}

	/**
	 * Embedding 同理
	 */
	public EmbeddingModel createEmbeddingModel(ModelConfigDTO config) {
		log.info("Creating NEW EmbeddingModel instance. Provider: {}, Model: {}, BaseUrl: {}", config.getProvider(),
				config.getModelName(), config.getBaseUrl());
		checkBasic(config);

		OpenAiApi.Builder apiBuilder = OpenAiApi.builder().apiKey(config.getApiKey()).baseUrl(config.getBaseUrl());

		if (StringUtils.hasText(config.getEmbeddingsPath())) {
			apiBuilder.embeddingsPath(config.getEmbeddingsPath());
		}

		OpenAiApi openAiApi = apiBuilder.build();
		// 使用自定义重试模板
		RetryTemplate retryTemplate = createRetryTemplate();
		return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED,
				OpenAiEmbeddingOptions.builder().model(config.getModelName()).build(),
				retryTemplate);
	}

	/**
	 * 创建自定义重试模板，支持 429 (Too Many Requests) 和其他可重试错误
	 */
	private RetryTemplate createRetryTemplate() {
		return RetryTemplate.builder()
				.maxAttempts(maxAttempts)
				.exponentialBackoff(initialInterval, multiplier, initialInterval * (long) Math.pow(multiplier, maxAttempts - 1))
				.retryOn(WebClientResponseException.TooManyRequests.class)
				.retryOn(WebClientResponseException.ServiceUnavailable.class)
				.retryOn(WebClientResponseException.GatewayTimeout.class)
				.retryOn(WebClientResponseException.InternalServerError.class)
				.withListener(new org.springframework.retry.listener.RetryListenerSupport() {
					@Override
					public <T, E extends Throwable> void onError(
							org.springframework.retry.RetryContext context,
							org.springframework.retry.RetryCallback<T, E> callback,
							Throwable throwable) {
						if (throwable instanceof WebClientResponseException.TooManyRequests) {
							log.warn("LLM API 速率限制 (429)，正在进行第 {} 次重试，退避时间: {} ms",
									context.getRetryCount() + 1,
									initialInterval * (long) Math.pow(multiplier, context.getRetryCount()));
						} else {
							log.warn("LLM API 调用失败，正在进行第 {} 次重试: {}",
									context.getRetryCount() + 1, throwable.getMessage());
						}
					}
				})
				.build();
	}

}
