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
package com.audaque.cloud.ai.dataagent.service.llm.impls;

import com.audaque.cloud.ai.dataagent.service.aimodelconfig.AiModelRegistry;
import com.audaque.cloud.ai.dataagent.service.llm.LlmService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

@Slf4j
@AllArgsConstructor
public class StreamLlmService implements LlmService {

	private final AiModelRegistry registry;

	@Override
	public Flux<ChatResponse> call(String system, String user) {
		log.debug("StreamLlmService.call() - Sending request with system and user messages");
		log.debug("System message length: {}, User message length: {}", 
				system != null ? system.length() : 0, user != null ? user.length() : 0);
		return registry.getChatClient()
				.prompt()
				.system(system)
				.user(user)
				.stream()
				.chatResponse()
				.doOnSubscribe(s -> log.debug("LLM stream subscribed (system+user)"))
				.doOnNext(r -> log.debug("LLM response received: hasResult={}, hasOutput={}", 
						r != null && r.getResult() != null,
						r != null && r.getResult() != null && r.getResult().getOutput() != null))
				.doOnError(e -> log.error("LLM stream error (system+user): {}", e.getMessage(), e))
				.doOnComplete(() -> log.debug("LLM stream completed (system+user)"));
	}

	@Override
	public Flux<ChatResponse> callSystem(String system) {
		log.debug("StreamLlmService.callSystem() - Sending system message only");
		log.debug("System message length: {}", system != null ? system.length() : 0);
		return registry.getChatClient()
				.prompt()
				.system(system)
				.stream()
				.chatResponse()
				.doOnSubscribe(s -> log.debug("LLM stream subscribed (system only)"))
				.doOnNext(r -> log.debug("LLM response received: hasResult={}, hasOutput={}", 
						r != null && r.getResult() != null,
						r != null && r.getResult() != null && r.getResult().getOutput() != null))
				.doOnError(e -> log.error("LLM stream error (system only): {}", e.getMessage(), e))
				.doOnComplete(() -> log.debug("LLM stream completed (system only)"));
	}

	@Override
	public Flux<ChatResponse> callUser(String user) {
		log.debug("StreamLlmService.callUser() - Sending user message only");
		log.debug("User message length: {}", user != null ? user.length() : 0);
		return registry.getChatClient()
				.prompt()
				.user(user)
				.stream()
				.chatResponse()
				.doOnSubscribe(s -> log.debug("LLM stream subscribed (user only)"))
				.doOnNext(r -> log.debug("LLM response received: hasResult={}, hasOutput={}", 
						r != null && r.getResult() != null,
						r != null && r.getResult() != null && r.getResult().getOutput() != null))
				.doOnError(e -> log.error("LLM stream error (user only): {}", e.getMessage(), e))
				.doOnComplete(() -> log.debug("LLM stream completed (user only)"));
	}

}
