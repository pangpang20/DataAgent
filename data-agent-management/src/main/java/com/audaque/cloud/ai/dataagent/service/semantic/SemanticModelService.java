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
package com.audaque.cloud.ai.dataagent.service.semantic;

import com.audaque.cloud.ai.dataagent.dto.schema.SemanticModelAddDTO;
import com.audaque.cloud.ai.dataagent.dto.schema.SemanticModelBatchImportDTO;
import com.audaque.cloud.ai.dataagent.dto.semantic.SemanticModelQueryDTO;
import com.audaque.cloud.ai.dataagent.entity.SemanticModel;
import com.audaque.cloud.ai.dataagent.vo.BatchImportResult;
import com.audaque.cloud.ai.dataagent.vo.PageResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SemanticModelService {

	List<SemanticModel> getAll();

	List<SemanticModel> getEnabledByAgentId(Long agentId);

	List<SemanticModel> getByAgentIdAndTableNames(Long agentId, List<String> tableNames);

	SemanticModel getById(Long id);

	void addSemanticModel(SemanticModel semanticModel);

	boolean addSemanticModel(SemanticModelAddDTO dto);

	void enableSemanticModel(Long id);

	void disableSemanticModel(Long id);

	List<SemanticModel> getByAgentId(Long agentId);

	List<SemanticModel> search(String keyword);

	void deleteSemanticModel(Long id);

	void updateSemanticModel(Long id, SemanticModel semanticModel);

	default void addSemanticModels(List<SemanticModel> semanticModels) {
		semanticModels.forEach(this::addSemanticModel);
	}

	default void enableSemanticModels(List<Long> ids) {
		ids.forEach(this::enableSemanticModel);
	}

	default void disableSemanticModels(List<Long> ids) {
		ids.forEach(this::disableSemanticModel);
	}

	default void deleteSemanticModels(List<Long> ids) {
		ids.forEach(this::deleteSemanticModel);
	}

	BatchImportResult batchImport(SemanticModelBatchImportDTO dto);

	/**
	 * 从Excel文件导入语义模型
	 * 
	 * @param file    Excel文件
	 * @param agentId 智能体ID
	 * @return 导入结果
	 */
	BatchImportResult importFromExcel(MultipartFile file, Long agentId);

	/**
	 * Page query semantic models with filters
	 * 
	 * @param queryDTO query parameters
	 * @return page result
	 */
	PageResult<SemanticModel> queryByConditionsWithPage(SemanticModelQueryDTO queryDTO);

	/**
	 * Batch delete semantic models by ids
	 * 
	 * @param agentId agent id
	 * @param ids     id list
	 * @return affected count
	 */
	int batchDelete(Long agentId, List<Long> ids);

}
