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
package com.audaque.cloud.ai.dataagent.service.agent;

import com.audaque.cloud.ai.dataagent.entity.AgentPresetQuestion;
import com.audaque.cloud.ai.dataagent.mapper.AgentPresetQuestionMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AgentPresetQuestion Service Implementation
 */
@Slf4j
@Service
@AllArgsConstructor
public class AgentPresetQuestionServiceImpl implements AgentPresetQuestionService {

	private final AgentPresetQuestionMapper agentPresetQuestionMapper;

	@Override
	public List<AgentPresetQuestion> findByAgentId(Long agentId) {
		log.debug("Finding preset questions for agentId: {}", agentId);
		List<AgentPresetQuestion> questions = agentPresetQuestionMapper.selectByAgentId(agentId);
		log.debug("Found {} preset questions for agentId: {}", questions != null ? questions.size() : 0, agentId);
		return questions;
	}

	@Override
	public List<AgentPresetQuestion> findAllByAgentId(Long agentId) {
		log.debug("Finding all preset questions for agentId: {}", agentId);
		List<AgentPresetQuestion> questions = agentPresetQuestionMapper.selectAllByAgentId(agentId);
		log.debug("Found {} preset questions (including inactive) for agentId: {}",
				questions != null ? questions.size() : 0, agentId);
		return questions;
	}

	@Override
	public AgentPresetQuestion create(AgentPresetQuestion question) {
		log.info("Creating preset question for agentId: {}, question: {}",
				question.getAgentId(), question.getQuestion());

		// Ensure default values
		if (question.getSortOrder() == null) {
			question.setSortOrder(0);
			log.debug("Set default sortOrder to 0");
		}
		if (question.getIsActive() == null) {
			question.setIsActive(true);
			log.debug("Set default isActive to true");
		}

		LocalDateTime now = LocalDateTime.now();
		question.setCreateTime(now);
		question.setUpdateTime(now);

		agentPresetQuestionMapper.insert(question);
		log.info("Successfully created preset question with id: {} for agentId: {}",
				question.getId(), question.getAgentId());
		return question;
	}

	@Override
	public void update(Long id, AgentPresetQuestion question) {
		log.info("Updating preset question id: {} for agentId: {}", id, question.getAgentId());
		question.setId(id);
		question.setUpdateTime(LocalDateTime.now());
		agentPresetQuestionMapper.update(question);
		log.info("Successfully updated preset question id: {}", id);
	}

	@Override
	public void deleteById(Long id) {
		log.info("Deleting preset question id: {}", id);
		agentPresetQuestionMapper.deleteById(id);
		log.info("Successfully deleted preset question id: {}", id);
	}

	@Override
	public void deleteByAgentId(Long agentId) {
		log.info("Deleting all preset questions for agentId: {}", agentId);
		agentPresetQuestionMapper.deleteByAgentId(agentId);
		log.info("Successfully deleted all preset questions for agentId: {}", agentId);
	}

	@Override
	public void batchSave(Long agentId, List<AgentPresetQuestion> questions) {
		log.info("Batch saving {} preset questions for agentId: {}", questions != null ? questions.size() : 0, agentId);

		// Step 1: Delete all existing preset questions for the agent
		deleteByAgentId(agentId);

		// Step 2: Insert new questions with proper order and active status
		if (questions != null) {
			for (int i = 0; i < questions.size(); i++) {
				AgentPresetQuestion question = questions.get(i);
				question.setAgentId(agentId);
				question.setSortOrder(i);
				if (question.getIsActive() == null) {
					question.setIsActive(true);
				}
				create(question);
			}
		}
		log.info("Successfully batch saved preset questions for agentId: {}", agentId);
	}

}
