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
package com.audaque.cloud.ai.dataagent.service.graph.Context;

import com.audaque.cloud.ai.dataagent.entity.ConversationTurn;
import com.audaque.cloud.ai.dataagent.mapper.ConversationTurnMapper;
import com.audaque.cloud.ai.dataagent.properties.DataAgentProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages multi-turn dialogue context for each thread. The context keeps a
 * lightweight
 * history of user questions and the corresponding planner outputs so downstream
 * prompts
 * can reference prior turns.
 */
@Slf4j
@Component
@AllArgsConstructor
public class MultiTurnContextManager {

	private final DataAgentProperties properties;
	
	private final ConversationTurnMapper conversationTurnMapper;

	private final Map<String, Deque<ConversationTurn>> history = new ConcurrentHashMap<>();
	
	// 缓存标志，标记哪些线程的历史已从数据库加载到内存
	private final Map<String, Boolean> loadedFlags = new ConcurrentHashMap<>();

	private final Map<String, PendingTurn> pendingTurns = new ConcurrentHashMap<>();

	/**
	 * Start tracking a new turn for the given thread.
	 * 
	 * @param threadId     conversation thread id
	 * @param userQuestion latest user question
	 */
	public void beginTurn(String threadId, String userQuestion) {
		if (StringUtils.isAnyBlank(threadId, userQuestion)) {
			return;
		}
		pendingTurns.put(threadId, new PendingTurn(userQuestion.trim()));
	}

	/**
	 * Append planner output chunk for the current turn.
	 * 
	 * @param threadId conversation thread id
	 * @param chunk    planner streaming chunk
	 */
	public void appendPlannerChunk(String threadId, String chunk) {
		if (StringUtils.isAnyBlank(threadId, chunk)) {
			return;
		}
		PendingTurn pending = pendingTurns.get(threadId);
		if (pending != null) {
			pending.planBuilder.append(chunk);
		}
	}

	/**
	 * Finalize current turn and add to history if planner output is available.
	 * 
	 * @param threadId conversation thread id
	 */
	public void finishTurn(String threadId) {
		PendingTurn pending = pendingTurns.remove(threadId);
		if (pending == null) {
			return;
		}
		String plan = StringUtils.trimToEmpty(pending.planBuilder.toString());
		if (StringUtils.isBlank(plan)) {
			log.debug("No planner output recorded for thread {}, skipping history update", threadId);
			return;
		}

		String trimmedPlan = StringUtils.abbreviate(plan, properties.getMaxplanlength());
		
		// 确保该线程的历史已加载
		ensureHistoryLoaded(threadId);
		
		Deque<ConversationTurn> deque = history.computeIfAbsent(threadId, k -> new ArrayDeque<>());
		synchronized (deque) {
			// 持久化到数据库
			persistTurnToDatabase(threadId, pending.userQuestion, trimmedPlan);
			
			// 内存中维护历史记录
			while (deque.size() >= properties.getMaxturnhistory()) {
				deque.pollFirst();
			}
			deque.addLast(new ConversationTurn(pending.userQuestion, trimmedPlan));
		}
	}

	/**
	 * Remove any pending turn data without touching persisted history. Typically
	 * used
	 * when a run is aborted.
	 * 
	 * @param threadId conversation thread id
	 */
	public void discardPending(String threadId) {
		pendingTurns.remove(threadId);
	}

	/**
	 * Restart the latest turn so a new planner output can replace it (e.g. after
	 * human
	 * feedback). The last stored turn will be removed and its question reused.
	 * 
	 * @param threadId conversation thread id
	 */
	public void restartLastTurn(String threadId) {
		// 确保该线程的历史已加载
		ensureHistoryLoaded(threadId);
		
		Deque<ConversationTurn> deque = history.get(threadId);
		if (deque == null || deque.isEmpty()) {
			return;
		}
		ConversationTurn lastTurn;
		synchronized (deque) {
			lastTurn = deque.pollLast();
		}
		if (lastTurn != null) {
			// 从数据库中删除最后一条记录
			conversationTurnMapper.deleteLastTurnByThreadId(threadId);
			pendingTurns.put(threadId, new PendingTurn(lastTurn.getUserQuestion()));
		}
	}

	/**
	 * Build multi-turn context string for prompt injection.
	 * 
	 * @param threadId conversation thread id
	 * @return formatted history string
	 */
	public String buildContext(String threadId) {
		// 确保该线程的历史已加载
		ensureHistoryLoaded(threadId);
		
		Deque<ConversationTurn> deque = history.get(threadId);
		if (deque == null || deque.isEmpty()) {
			return "(无)";
		}
		return deque.stream()
				.map(turn -> "用户: " + turn.getUserQuestion() + "\nAI计划: " + turn.getPlan())
				.collect(Collectors.joining("\n"));
	}

	/**
	 * 确保指定线程的历史记录已从数据库加载到内存
	 * @param threadId 线程ID
	 */
	private void ensureHistoryLoaded(String threadId) {
		if (loadedFlags.putIfAbsent(threadId, Boolean.TRUE) == null) {
			// 首次访问该线程，从数据库加载历史
			loadHistoryFromDatabase(threadId);
		}
	}
	
	/**
	 * 从数据库加载历史记录到内存
	 * @param threadId 线程ID
	 */
	private void loadHistoryFromDatabase(String threadId) {
		try {
			List<ConversationTurn> turns = conversationTurnMapper.selectByThreadId(threadId);
			Deque<ConversationTurn> deque = new ArrayDeque<>(turns);
			history.put(threadId, deque);
			log.debug("Loaded {} conversation turns from database for thread {}", turns.size(), threadId);
		} catch (Exception e) {
			log.error("Failed to load conversation history from database for thread {}", threadId, e);
			history.putIfAbsent(threadId, new ArrayDeque<>());
		}
	}
	
	/**
	 * 将对话记录持久化到数据库
	 * @param threadId 线程ID
	 * @param userQuestion 用户问题
	 * @param plan AI规划
	 */
	private void persistTurnToDatabase(String threadId, String userQuestion, String plan) {
		try {
			int maxSequence = conversationTurnMapper.getMaxSequenceNumberByThreadId(threadId);
			
			ConversationTurn turn = ConversationTurn.builder()
					.threadId(threadId)
					.userQuestion(userQuestion)
					.plan(plan)
					.sequenceNumber(maxSequence + 1)
					.createTime(java.time.LocalDateTime.now())
					.updateTime(java.time.LocalDateTime.now())
					.build();
			
			conversationTurnMapper.insert(turn);
			
			// 清理超出最大历史记录数的旧记录
			int maxHistory = properties.getMaxturnhistory();
			conversationTurnMapper.cleanupOldTurns(threadId, maxHistory);
			
			log.debug("Persisted conversation turn to database: threadId={}, sequence={}", threadId, turn.getSequenceNumber());
		} catch (Exception e) {
			log.error("Failed to persist conversation turn to database: threadId={}", threadId, e);
		}
	}
	
	/**
	 * 清除指定线程的所有历史记录（内存和数据库）
	 * @param threadId 线程ID
	 */
	public void clearHistory(String threadId) {
		try {
			// 清除内存中的历史
			history.remove(threadId);
			loadedFlags.remove(threadId);
			pendingTurns.remove(threadId);
			
			// 清除数据库中的历史
			conversationTurnMapper.deleteByThreadId(threadId);
			
			log.info("Cleared all conversation history for thread: {}", threadId);
		} catch (Exception e) {
			log.error("Failed to clear conversation history for thread: {}", threadId, e);
		}
	}
	
	/**
	 * 获取指定线程的历史记录数量
	 * @param threadId 线程ID
	 * @return 历史记录数量
	 */
	public int getHistorySize(String threadId) {
		ensureHistoryLoaded(threadId);
		Deque<ConversationTurn> deque = history.get(threadId);
		return deque != null ? deque.size() : 0;
	}
	
	private static class PendingTurn {

		private final String userQuestion;

		private final StringBuilder planBuilder = new StringBuilder();

		private PendingTurn(String userQuestion) {
			this.userQuestion = userQuestion;
		}

	}
}
