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
package com.audaque.cloud.ai.dataagent.service.prompt;

import com.audaque.cloud.ai.dataagent.dto.prompt.PromptConfigDTO;
import com.audaque.cloud.ai.dataagent.entity.UserPromptConfig;
import com.audaque.cloud.ai.dataagent.mapper.UserPromptConfigMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * User Prompt Configuration Management Service Provides CRUD functionality for
 * prompt
 * configurations, supports runtime configuration updates
 *
 */
@Slf4j
@Service
@AllArgsConstructor
public class UserPromptServiceImpl implements UserPromptService {

	private final UserPromptConfigMapper userPromptConfigMapper;

	@Override
	public UserPromptConfig saveOrUpdateConfig(PromptConfigDTO configDTO) {
		log.info("Saving or updating prompt config: name={}, type={}, agentId={}",
				configDTO.name(), configDTO.promptType(), configDTO.agentId());

		UserPromptConfig config;
		if (configDTO.id() != null) {
			// Update existing configuration
			config = userPromptConfigMapper.selectById(configDTO.id());
			if (config != null) {
				config.setName(configDTO.name());
				config.setAgentId(configDTO.agentId());
				config.setSystemPrompt(configDTO.optimizationPrompt());
				config.setEnabled(configDTO.enabled());
				config.setDescription(configDTO.description());
				config.setPriority(configDTO.priority() != null ? configDTO.priority() : 0);
				config.setDisplayOrder(configDTO.displayOrder() != null ? configDTO.displayOrder() : 0);
				config.setUpdateTime(LocalDateTime.now());
				userPromptConfigMapper.updateById(config);
			} else {
				// ID不存在，创建新配置
				config = new UserPromptConfig();
				config.setId(configDTO.id());
				config.setName(configDTO.name());
				config.setPromptType(configDTO.promptType());
				config.setAgentId(configDTO.agentId());
				config.setSystemPrompt(configDTO.optimizationPrompt());
				config.setEnabled(configDTO.enabled());
				config.setDescription(configDTO.description());
				config.setCreator(configDTO.creator());
				config.setPriority(configDTO.priority() != null ? configDTO.priority() : 0);
				config.setDisplayOrder(configDTO.displayOrder() != null ? configDTO.displayOrder() : 0);

				LocalDateTime now = LocalDateTime.now();
				config.setCreateTime(now);
				config.setUpdateTime(now);

				userPromptConfigMapper.insert(config);
			}
		} else {
			// Create new configuration
			config = new UserPromptConfig();
			config.setId(UUID.randomUUID().toString());
			config.setName(configDTO.name());
			config.setPromptType(configDTO.promptType());
			config.setAgentId(configDTO.agentId());
			config.setSystemPrompt(configDTO.optimizationPrompt());
			config.setEnabled(configDTO.enabled());
			config.setDescription(configDTO.description());
			config.setCreator(configDTO.creator());
			config.setPriority(configDTO.priority() != null ? configDTO.priority() : 0);
			config.setDisplayOrder(configDTO.displayOrder() != null ? configDTO.displayOrder() : 0);

			LocalDateTime now = LocalDateTime.now();
			config.setCreateTime(now);
			config.setUpdateTime(now);

			userPromptConfigMapper.insert(config);
		}

		// Enable config if enabled flag is set
		if (Boolean.TRUE.equals(config.getEnabled())) {
			userPromptConfigMapper.enableById(config.getId());
			log.info("Enabled prompt config [{}]: id={}", config.getPromptType(), config.getId());
		}

		log.info("Successfully saved prompt config: id={}, name={}", config.getId(), config.getName());

		return config;
	}

	@Override
	public UserPromptConfig getConfigById(String id) {
		log.debug("Getting prompt config by id: {}", id);
		UserPromptConfig config = userPromptConfigMapper.selectById(id);
		if (config == null) {
			log.warn("Prompt config not found for id: {}", id);
		} else {
			log.debug("Found prompt config: {} (type: {})", config.getName(), config.getPromptType());
		}
		return config;
	}

	@Override
	public List<UserPromptConfig> getActiveConfigsByType(String promptType, Long agentId) {
		log.debug("Getting active prompt configs for type: {}, agentId: {}", promptType, agentId);
		List<UserPromptConfig> configs = userPromptConfigMapper.getActiveConfigsByType(promptType, agentId);
		log.debug("Found {} active configs for type: {}", configs.size(), promptType);
		return configs;
	}

	@Override
	public UserPromptConfig getActiveConfigByType(String promptType, Long agentId) {
		log.debug("Getting active prompt config for type: {}, agentId: {}", promptType, agentId);
		UserPromptConfig config = userPromptConfigMapper.selectActiveByPromptType(promptType, agentId);
		if (config == null) {
			log.debug("No active config found for type: {}, agentId: {}", promptType, agentId);
		} else {
			log.debug("Found active config: {} for type: {}", config.getName(), promptType);
		}
		return config;
	}

	@Override
	public List<UserPromptConfig> getAllConfigs() {
		log.debug("Getting all prompt configs");
		List<UserPromptConfig> configs = userPromptConfigMapper.selectAll();
		log.debug("Found {} prompt configs", configs.size());
		return configs;
	}

	@Override
	public List<UserPromptConfig> getConfigsByType(String promptType, Long agentId) {
		log.debug("Getting prompt configs for type: {}, agentId: {}", promptType, agentId);
		List<UserPromptConfig> configs = userPromptConfigMapper.getConfigsByType(promptType, agentId);
		log.debug("Found {} configs for type: {}", configs.size(), promptType);
		return configs;
	}

	@Override
	public boolean deleteConfig(String id) {
		log.info("Deleting prompt config id: {}", id);
		UserPromptConfig config = userPromptConfigMapper.selectById(id);
		if (config != null) {
			int deleted = userPromptConfigMapper.deleteById(id);
			if (deleted > 0) {
				log.info("Successfully deleted prompt config: {} (name: {})", id, config.getName());
				return true;
			} else {
				log.error("Failed to delete prompt config: {}", id);
			}
		} else {
			log.warn("Prompt config not found for deletion: {}", id);
		}
		return false;
	}

	@Override
	public boolean enableConfig(String id) {
		log.info("Enabling prompt config id: {}", id);
		UserPromptConfig config = userPromptConfigMapper.selectById(id);
		if (config != null) {
			int updated = userPromptConfigMapper.enableById(id);
			if (updated > 0) {
				log.info("Successfully enabled prompt config: {} (name: {})", id, config.getName());
				return true;
			} else {
				log.error("Failed to enable prompt config: {}", id);
			}
		} else {
			log.warn("Prompt config not found for enabling: {}", id);
		}
		return false;
	}

	@Override
	public boolean disableConfig(String id) {
		log.info("Disabling prompt config id: {}", id);
		int updated = userPromptConfigMapper.disableById(id);
		if (updated > 0) {
			log.info("Successfully disabled prompt config: {}", id);
			return true;
		} else {
			log.warn("Failed to disable prompt config: {} - config not found or already disabled", id);
		}
		return false;
	}

	@Override
	public List<UserPromptConfig> getOptimizationConfigs(String promptType, Long agentId) {
		return getActiveConfigsByType(promptType, agentId);
	}

	@Override
	public boolean enableConfigs(List<String> ids) {
		log.info("Batch enabling {} prompt configs", ids.size());
		int successCount = 0;
		for (String id : ids) {
			int updated = userPromptConfigMapper.enableById(id);
			if (updated > 0) {
				successCount++;
			}
		}
		log.info("Batch enable completed: {}/{} configs enabled", successCount, ids.size());
		return successCount == ids.size();
	}

	@Override
	public boolean disableConfigs(List<String> ids) {
		log.info("Batch disabling {} prompt configs", ids.size());
		int successCount = 0;
		for (String id : ids) {
			int updated = userPromptConfigMapper.disableById(id);
			if (updated > 0) {
				successCount++;
			}
		}
		log.info("Batch disable completed: {}/{} configs disabled", successCount, ids.size());
		return successCount == ids.size();
	}

	@Override
	public boolean updatePriority(String id, Integer priority) {
		log.info("Updating priority for prompt config: {} to {}", id, priority);
		UserPromptConfig config = userPromptConfigMapper.selectById(id);
		if (config != null) {
			config.setPriority(priority);
			config.setUpdateTime(LocalDateTime.now());
			userPromptConfigMapper.updateById(config);
			log.info("Successfully updated priority for config: {} to {}", id, priority);
			return true;
		} else {
			log.warn("Prompt config not found for priority update: {}", id);
		}
		return false;
	}

	@Override
	public boolean updateDisplayOrder(String id, Integer displayOrder) {
		log.info("Updating display order for prompt config: {} to {}", id, displayOrder);
		UserPromptConfig config = userPromptConfigMapper.selectById(id);
		if (config != null) {
			config.setDisplayOrder(displayOrder);
			config.setUpdateTime(LocalDateTime.now());
			userPromptConfigMapper.updateById(config);
			log.info("Successfully updated display order for config: {} to {}", id, displayOrder);
			return true;
		} else {
			log.warn("Prompt config not found for display order update: {}", id);
		}
		return false;
	}

}
