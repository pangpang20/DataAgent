/*
 * Copyright 2026 the original author or authors.
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
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * ChatModel 装饰器：通过 Semaphore 限制对 LLM API 的并发请求数。
 * 解决多个工作流节点同时调用 LLM 导致超出网关速率限制（429）的问题。
 */
@Slf4j
public class RateLimitedChatModel implements ChatModel {

	private final ChatModel delegate;

	private final Semaphore semaphore;

	private final int maxConcurrent;

	public RateLimitedChatModel(ChatModel delegate, int maxConcurrent) {
		this.delegate = delegate;
		this.maxConcurrent = maxConcurrent;
		this.semaphore = new Semaphore(maxConcurrent, true);
		log.info("RateLimitedChatModel initialized with maxConcurrent={}", maxConcurrent);
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		acquirePermit();
		try {
			return delegate.call(prompt);
		} finally {
			releasePermit();
		}
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return Flux.defer(() -> {
			acquirePermit();
			return delegate.stream(prompt)
					.doFinally(signal -> {
						if (signal == SignalType.ON_COMPLETE || signal == SignalType.ON_ERROR
								|| signal == SignalType.CANCEL) {
							releasePermit();
						}
					});
		});
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return delegate.getDefaultOptions();
	}

	private void acquirePermit() {
		try {
			if (!semaphore.tryAcquire(60, TimeUnit.SECONDS)) {
				log.warn("LLM rate limit: timed out waiting for permit (maxConcurrent={}), proceeding without permit",
						maxConcurrent);
			} else {
				log.debug("LLM rate limit: permit acquired, available={}", semaphore.availablePermits());
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.warn("LLM rate limit: interrupted while waiting for permit, proceeding without permit");
		}
	}

	private void releasePermit() {
		semaphore.release();
		log.debug("LLM rate limit: permit released, available={}", semaphore.availablePermits());
	}
}
