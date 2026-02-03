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

import com.audaque.cloud.ai.dataagent.enums.ModelType;
import com.audaque.cloud.ai.dataagent.converter.ModelConfigConverter;
import com.audaque.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.audaque.cloud.ai.dataagent.entity.ModelConfig;
import com.audaque.cloud.ai.dataagent.mapper.ModelConfigMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static com.audaque.cloud.ai.dataagent.converter.ModelConfigConverter.toDTO;
import static com.audaque.cloud.ai.dataagent.converter.ModelConfigConverter.toEntity;

@Slf4j
@Service
@AllArgsConstructor
public class ModelConfigDataServiceImpl implements ModelConfigDataService {

	private final ModelConfigMapper modelConfigMapper;

	@Override
	public ModelConfig findById(Integer id) {
		log.debug("Finding model config by id: {}", id);
		ModelConfig config = modelConfigMapper.findById(id);
		if (config == null) {
			log.warn("Model config not found for id: {}", id);
		} else {
			log.debug("Found model config: {} (type: {})", config.getModelName(), config.getModelType());
		}
		return config;
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void switchActiveStatus(Integer id, ModelType type) {
		log.info("Switching active status for model config id: {} to type: {}", id, type);

		// 1. Deactivate other configs of same type
		modelConfigMapper.deactivateOthers(type.getCode(), id);
		log.debug("Deactivated other configs of type: {}", type);

		// 2. Activate current config
		ModelConfig entity = modelConfigMapper.findById(id);
		if (entity != null) {
			entity.setIsActive(true);
			entity.setUpdatedTime(LocalDateTime.now());
			modelConfigMapper.updateById(entity);
			log.info("Successfully activated model config: {} (id: {}) for type: {}",
					entity.getModelName(), id, type);
		} else {
			log.warn("Model config not found for id: {} when switching active status", id);
		}
	}

	@Override
	public List<ModelConfigDTO> listConfigs() {
		log.debug("Listing all model configs");
		List<ModelConfigDTO> configs = modelConfigMapper.findAll().stream()
				.map(ModelConfigConverter::toDTO)
				.collect(Collectors.toList());
		log.debug("Found {} model configs", configs.size());
		return configs;
	}

	@Override
	public void addConfig(ModelConfigDTO dto) {
		log.info("Adding new model config: {} (provider: {}, type: {})",
				dto.getModelName(), dto.getProvider(), dto.getModelType());
		clean(dto);
		modelConfigMapper.insert(toEntity(dto));
		log.info("Successfully added model config: {}", dto.getModelName());
	}

	private void clean(ModelConfigDTO dto) {
		log.debug("Cleaning model config DTO: {}", dto.getModelName());
		dto.setModelName(dto.getModelName().trim());
		dto.setBaseUrl(dto.getBaseUrl().trim());
		dto.setApiKey(dto.getApiKey().trim());
		if (dto.getCompletionsPath() != null) {
			dto.setCompletionsPath(dto.getCompletionsPath().trim());
		}
		if (dto.getEmbeddingsPath() != null) {
			dto.setEmbeddingsPath(dto.getEmbeddingsPath().trim());
		}
		log.debug("Cleaned model config DTO successfully");
	}

	/**
	 * Update config in database (without hot switch)
	 * Returns updated entity for upper layer to decide whether to refresh memory
	 */
	@Transactional(rollbackFor = Exception.class)
	@Override
	public ModelConfig updateConfigInDb(ModelConfigDTO dto) {
		log.info("Updating model config in database, id: {}", dto.getId());
		clean(dto);

		// 1. Query old data
		ModelConfig entity = modelConfigMapper.findById(dto.getId());
		if (entity == null) {
			log.error("Model config not found for id: {}", dto.getId());
			throw new RuntimeException("Config not found");
		}
		log.debug("Found existing config: {} (type: {})", entity.getModelName(), entity.getModelType());

		// Model type cannot be changed
		if (!entity.getModelType().getCode().equals(dto.getModelType())) {
			log.error("Attempted to change model type from {} to {} for id: {}",
					entity.getModelType(), dto.getModelType(), dto.getId());
			throw new RuntimeException("Model type cannot be modified");
		}

		// 2. Merge fields
		mergeDtoToEntity(dto, entity);
		entity.setUpdatedTime(LocalDateTime.now());

		// 3. Update database
		modelConfigMapper.updateById(entity);
		log.info("Successfully updated model config: {} (id: {})", entity.getModelName(), dto.getId());

		return entity;
	}

	private static void mergeDtoToEntity(ModelConfigDTO dto, ModelConfig oldEntity) {
		oldEntity.setProvider(dto.getProvider());
		oldEntity.setBaseUrl(dto.getBaseUrl());
		oldEntity.setModelName(dto.getModelName());
		oldEntity.setTemperature(dto.getTemperature());
		oldEntity.setMaxTokens(dto.getMaxTokens()); // 新增字段
		oldEntity.setCompletionsPath(dto.getCompletionsPath()); // 路径字段
		oldEntity.setEmbeddingsPath(dto.getEmbeddingsPath()); // 路径字段
		oldEntity.setUpdatedTime(LocalDateTime.now()); // 更新时间

		// 只有当前端传来的 Key 不包含 "****" 时，才说明用户真的改了 Key，否则保持原样
		if (dto.getApiKey() != null && !dto.getApiKey().contains("****")) {
			oldEntity.setApiKey(dto.getApiKey());
		}
	}

	@Override
	public void deleteConfig(Integer id) {
		log.info("Deleting model config id: {}", id);

		// 1. Check if exists
		ModelConfig entity = modelConfigMapper.findById(id);
		if (entity == null) {
			log.error("Model config not found for id: {}", id);
			throw new RuntimeException("Config not found");
		}
		log.debug("Found config to delete: {} (active: {})", entity.getModelName(), entity.getIsActive());

		// 2. Cannot delete if active
		if (Boolean.TRUE.equals(entity.getIsActive())) {
			log.error("Cannot delete active config: {} (id: {})", entity.getModelName(), id);
			throw new RuntimeException("Cannot delete active config. Please activate another config first.");
		}

		// 3. Perform logical deletion
		entity.setIsDeleted(1);
		entity.setUpdatedTime(LocalDateTime.now());
		int updated = modelConfigMapper.updateById(entity);
		if (updated == 0) {
			log.error("Failed to delete config id: {}", id);
			throw new RuntimeException("Delete failed");
		}
		log.info("Successfully deleted model config: {} (id: {})", entity.getModelName(), id);
	}

	@Override
	public ModelConfigDTO getActiveConfigByType(ModelType modelType) {
		log.debug("Getting active config for model type: {}", modelType);
		ModelConfig entity = modelConfigMapper.selectActiveByType(modelType.getCode());
		if (entity == null) {
			log.warn("Active model configuration of type [{}] not found", modelType);
			return null;
		}
		log.debug("Found active config: {} for type: {}", entity.getModelName(), modelType);
		return toDTO(entity);
	}

}
