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
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * ChatModel 装饰器：通过 Semaphore 限制对 LLM API 的并发请求数。 解决多个工作流节点同时调用 LLM 导致超出网关速率限制（429）的问题。
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
			return delegate.call(convertSystemMessages(prompt));
		}
		finally {
			releasePermit();
		}
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return Flux.defer(() -> {
			acquirePermit();
			return delegate.stream(convertSystemMessages(prompt)).doFinally(signal -> {
				if (signal == SignalType.ON_COMPLETE || signal == SignalType.ON_ERROR || signal == SignalType.CANCEL) {
					releasePermit();
				}
			});
		});
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return delegate.getDefaultOptions();
	}

	/**
	 * 将 SystemMessage 转换为 UserMessage，兼容不支持 system 角色的模型（如 Qwen3.6-27B）。 - 仅含
	 * SystemMessage 时：转为 UserMessage - 同时含 SystemMessage 和 UserMessage 时：将 system 内容前置到
	 * user 消息中
	 */
	private Prompt convertSystemMessages(Prompt prompt) {
		List<Message> messages = prompt.getInstructions();
		boolean hasSystemMessage = messages.stream().anyMatch(m -> m.getMessageType() == MessageType.SYSTEM);
		if (!hasSystemMessage) {
			return prompt;
		}

		List<Message> converted = new ArrayList<>();
		StringBuilder systemContent = new StringBuilder();

		for (Message message : messages) {
			if (message instanceof SystemMessage sm) {
				systemContent.append(sm.getText()).append("\n\n");
			}
			else {
				converted.add(message);
			}
		}

		if (systemContent.length() > 0) {
			if (converted.isEmpty()) {
				// 仅有 system 消息，转为 user 消息
				converted.add(new UserMessage(systemContent.toString().trim()));
				log.debug("Converted system-only prompt to user message");
			}
			else {
				// 同时有 system 和 user，将 system 内容前置到第一个 user 消息
				Message firstUser = converted.get(0);
				if (firstUser instanceof UserMessage um) {
					converted.set(0, new UserMessage(systemContent + um.getText()));
					log.debug("Prepended system content to user message");
				}
			}
		}

		return new Prompt(converted, prompt.getOptions());
	}

	private void acquirePermit() {
		try {
			if (!semaphore.tryAcquire(60, TimeUnit.SECONDS)) {
				log.warn("LLM rate limit: timed out waiting for permit (maxConcurrent={}), proceeding without permit",
						maxConcurrent);
			}
			else {
				log.debug("LLM rate limit: permit acquired, available={}", semaphore.availablePermits());
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.warn("LLM rate limit: interrupted while waiting for permit, proceeding without permit");
		}
	}

	private void releasePermit() {
		semaphore.release();
		log.debug("LLM rate limit: permit released, available={}", semaphore.availablePermits());
	}

}
