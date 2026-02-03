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

import com.audaque.cloud.ai.dataagent.entity.ChatSession;
import com.audaque.cloud.ai.dataagent.mapper.ChatSessionMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@AllArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {

	private final ChatSessionMapper chatSessionMapper;

	/**
	 * Get session list by agent ID
	 */
	@Override
	public List<ChatSession> findByAgentId(Integer agentId) {
		log.debug("Finding sessions for agentId: {}", agentId);
		List<ChatSession> sessions = chatSessionMapper.selectByAgentId(agentId);
		log.debug("Found {} sessions for agentId: {}", sessions != null ? sessions.size() : 0, agentId);
		return sessions;
	}

	@Override
	public ChatSession findBySessionId(String sessionId) {
		log.debug("Finding session by sessionId: {}", sessionId);
		ChatSession session = chatSessionMapper.selectBySessionId(sessionId);
		if (session == null) {
			log.warn("Session not found for sessionId: {}", sessionId);
		} else {
			log.debug("Found session: {} for agentId: {}", sessionId, session.getAgentId());
		}
		return session;
	}

	/**
	 * Create a new session
	 */
	@Override
	public ChatSession createSession(Integer agentId, String title, Long userId) {
		String sessionId = UUID.randomUUID().toString();
		log.info("Creating new chat session for agentId: {}, userId: {}, title: {}", agentId, userId, title);

		ChatSession session = new ChatSession(sessionId, agentId, title != null ? title : "New Session", "active",
				userId);
		LocalDateTime now = LocalDateTime.now();
		session.setCreateTime(now);
		session.setUpdateTime(now);
		chatSessionMapper.insert(session);

		log.info("Successfully created chat session: {} for agent: {}", sessionId, agentId);
		return session;
	}

	/**
	 * Clear all sessions for an agent
	 */
	@Override
	public void clearSessionsByAgentId(Integer agentId) {
		log.info("Clearing all sessions for agentId: {}", agentId);
		LocalDateTime now = LocalDateTime.now();
		int updated = chatSessionMapper.softDeleteByAgentId(agentId, now);
		log.info("Successfully cleared {} sessions for agent: {}", updated, agentId);
	}

	/**
	 * Update the last activity time of a session
	 */
	@Override
	public void updateSessionTime(String sessionId) {
		log.debug("Updating session time for sessionId: {}", sessionId);
		LocalDateTime now = LocalDateTime.now();
		chatSessionMapper.updateSessionTime(sessionId, now);
		log.debug("Successfully updated session time for sessionId: {}", sessionId);
	}

	/**
	 * Pin/Unpin session
	 */
	@Override
	public void pinSession(String sessionId, boolean isPinned) {
		log.info("Updating pin status for session: {} to: {}", sessionId, isPinned);
		LocalDateTime now = LocalDateTime.now();
		chatSessionMapper.updatePinStatus(sessionId, isPinned, now);
		log.info("Successfully updated pin status for session: {}", sessionId);
	}

	/**
	 * Rename session
	 */
	@Override
	public void renameSession(String sessionId, String newTitle) {
		log.info("Renaming session: {} to: {}", sessionId, newTitle);
		LocalDateTime now = LocalDateTime.now();
		chatSessionMapper.updateTitle(sessionId, newTitle, now);
		log.info("Successfully renamed session: {}", sessionId);
	}

	/**
	 * Delete a single session
	 */
	@Override
	public void deleteSession(String sessionId) {
		log.info("Deleting session: {}", sessionId);
		LocalDateTime now = LocalDateTime.now();
		chatSessionMapper.softDeleteById(sessionId, now);
		log.info("Successfully deleted session: {}", sessionId);
	}

}
