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
package com.audaque.cloud.ai.dataagent.service.chat;

import com.audaque.cloud.ai.dataagent.entity.ChatMessage;
import com.audaque.cloud.ai.dataagent.mapper.ChatMessageMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Chat Message Service Class
 */
@Slf4j
@Service
@AllArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {

	private final ChatMessageMapper chatMessageMapper;

	@Override
	public List<ChatMessage> findBySessionId(String sessionId) {
		log.debug("Finding messages for sessionId: {}", sessionId);
		List<ChatMessage> messages = chatMessageMapper.selectBySessionId(sessionId);
		log.debug("Found {} messages for sessionId: {}", messages != null ? messages.size() : 0, sessionId);
		return messages;
	}

	@Override
	public ChatMessage saveMessage(ChatMessage message) {
		log.debug("Saving message for session: {}, role: {}", message.getSessionId(), message.getRole());
		if (message.getCreateTime() == null) {
			message.setCreateTime(LocalDateTime.now());
			log.debug("Set createTime to current time");
		}
		chatMessageMapper.insert(message);
		log.info("Successfully saved message id: {} for session: {}", message.getId(), message.getSessionId());
		return message;
	}

}
