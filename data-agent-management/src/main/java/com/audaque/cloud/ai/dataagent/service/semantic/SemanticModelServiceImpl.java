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
import com.audaque.cloud.ai.dataagent.dto.schema.SemanticModelImportItem;
import com.audaque.cloud.ai.dataagent.entity.AgentDatasource;
import com.audaque.cloud.ai.dataagent.entity.SemanticModel;
import com.audaque.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.audaque.cloud.ai.dataagent.mapper.SemanticModelMapper;
import com.audaque.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.audaque.cloud.ai.dataagent.util.DocumentConverterUtil;
import com.audaque.cloud.ai.dataagent.vo.BatchImportResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
@Slf4j
public class SemanticModelServiceImpl implements SemanticModelService {

	private final SemanticModelMapper semanticModelMapper;

	private final AgentDatasourceMapper agentDatasourceMapper;

	private final SemanticModelExcelService excelService;

	private final AgentVectorStoreService agentVectorStoreService;

	@Override
	public List<SemanticModel> getAll() {
		return semanticModelMapper.selectAll();
	}

	@Override
	public List<SemanticModel> getEnabledByAgentId(Long agentId) {
		return semanticModelMapper.selectEnabledByAgentId(agentId);
	}

	@Override
	public List<SemanticModel> getByAgentIdAndTableNames(Long agentId, List<String> tableNames) {
		Integer datasourceId = findDatasourceIdByAgentId(agentId);

		if (datasourceId == null || tableNames == null || tableNames.isEmpty()) {
			return List.of();
		}

		return semanticModelMapper.selectByDatasourceIdAndTableNames(datasourceId, tableNames);
	}

	@Override
	public SemanticModel getById(Long id) {
		return semanticModelMapper.selectById(id);
	}

	@Override
	public void addSemanticModel(SemanticModel semanticModel) {
		LocalDateTime now = LocalDateTime.now();
		semanticModel.setCreatedTime(now);
		semanticModel.setUpdatedTime(now);
		semanticModelMapper.insert(semanticModel);
	}

	@Override
	public boolean addSemanticModel(SemanticModelAddDTO dto) {
		// 根据agentId查询关联的datasourceId
		Integer datasourceId = findDatasourceIdByAgentId(dto.getAgentId().longValue());

		// 转换DTO为Entity
		SemanticModel semanticModel = SemanticModel.builder()
				.agentId(dto.getAgentId())
				.datasourceId(datasourceId)
				.tableName(dto.getTableName())
				.columnName(dto.getColumnName())
				.businessName(dto.getBusinessName())
				.synonyms(dto.getSynonyms())
				.businessDescription(dto.getBusinessDescription())
				.columnComment(dto.getColumnComment())
				.dataType(dto.getDataType())
				.status(1) // 默认启用状态
				.build();

		// 保存到数据库
		LocalDateTime now = LocalDateTime.now();
		semanticModel.setCreatedTime(now);
		semanticModel.setUpdatedTime(now);
		semanticModelMapper.insert(semanticModel);

		// 同步到向量数据库
		syncSemanticModelToVectorStore(semanticModel);

		return true;
	}

	/**
	 * 根据agentId查找关联的datasourceId 如果有多个数据源，返回第一个启用的数据源
	 */
	private Integer findDatasourceIdByAgentId(Long agentId) {
		List<AgentDatasource> agentDatasources = agentDatasourceMapper.selectByAgentId(agentId.intValue());

		if (agentDatasources.isEmpty()) {
			throw new RuntimeException("No datasource found for Agent ID " + agentId);
		}

		// 优先返回启用的数据源
		for (AgentDatasource ad : agentDatasources) {
			if (ad.getIsActive() != null && ad.getIsActive() == 1) {
				return ad.getDatasourceId();
			}
		}

		// 如果没有启用的数据源，返回第一个
		return agentDatasources.get(0).getDatasourceId();
	}

	@Override
	public void enableSemanticModel(Long id) {
		semanticModelMapper.enableById(id);
	}

	@Override
	public void disableSemanticModel(Long id) {
		semanticModelMapper.disableById(id);
	}

	@Override
	public List<SemanticModel> getByAgentId(Long agentId) {
		return semanticModelMapper.selectByAgentId(agentId);
	}

	@Override
	public List<SemanticModel> search(String keyword) {
		return semanticModelMapper.searchByKeyword(keyword);
	}

	@Override
	public void deleteSemanticModel(Long id) {
		SemanticModel semanticModel = semanticModelMapper.selectById(id);
		if (semanticModel != null) {
			// 从向量数据库删除
			deleteSemanticModelFromVectorStore(semanticModel);
		}

		// 从关系数据库删除
		semanticModelMapper.deleteById(id);
	}

	@Override
	public void updateSemanticModel(Long id, SemanticModel semanticModel) {
		semanticModel.setId(id);
		semanticModel.setUpdatedTime(LocalDateTime.now());
		semanticModelMapper.updateById(semanticModel);

		// 重新同步到向量数据库
		syncSemanticModelToVectorStore(semanticModel);
	}

	@Override
	public BatchImportResult batchImport(SemanticModelBatchImportDTO dto) {
		BatchImportResult result = BatchImportResult.builder()
				.total(dto.getItems().size())
				.successCount(0)
				.failCount(0)
				.build();

		// 获取datasourceId
		Integer datasourceId;
		try {
			datasourceId = findDatasourceIdByAgentId(dto.getAgentId());
		} catch (Exception e) {
			log.error("获取数据源ID失败: agentId={}", dto.getAgentId(), e);
			result.setFailCount(dto.getItems().size());
			result.addError("获取数据源ID失败: " + e.getMessage());
			return result;
		}

		// 遍历导入项
		for (int i = 0; i < dto.getItems().size(); i++) {
			SemanticModelImportItem item = dto.getItems().get(i);
			try {
				// 检查是否已存在
				SemanticModel existing = semanticModelMapper.selectByAgentIdAndTableNameAndColumnName(
						dto.getAgentId().intValue(), item.getTableName(), item.getColumnName());

				if (existing != null) {
					// 更新已存在的记录
					existing.setBusinessName(item.getBusinessName());
					existing.setSynonyms(item.getSynonyms());
					existing.setBusinessDescription(item.getBusinessDescription());
					existing.setColumnComment(item.getColumnComment());
					existing.setDataType(item.getDataType());
					existing.setUpdatedTime(LocalDateTime.now());
					semanticModelMapper.updateById(existing);

					// 重新同步到向量数据库
					syncSemanticModelToVectorStore(existing);

					log.info("更新语义模型: agentId={}, tableName={}, columnName={}", dto.getAgentId(), item.getTableName(),
							item.getColumnName());
				} else {
					// 插入新记录
					SemanticModel newModel = SemanticModel.builder()
							.agentId(dto.getAgentId().intValue())
							.datasourceId(datasourceId)
							.tableName(item.getTableName())
							.columnName(item.getColumnName())
							.businessName(item.getBusinessName())
							.synonyms(item.getSynonyms())
							.businessDescription(item.getBusinessDescription())
							.columnComment(item.getColumnComment())
							.dataType(item.getDataType())
							.status(1) // 默认启用
							.createdTime(item.getCreateTime() != null ? item.getCreateTime() : LocalDateTime.now())
							.updatedTime(LocalDateTime.now())
							.build();
					semanticModelMapper.insert(newModel);

					// 同步到向量数据库
					syncSemanticModelToVectorStore(newModel);

					log.info("插入语义模型: agentId={}, tableName={}, columnName={}", dto.getAgentId(), item.getTableName(),
							item.getColumnName());
				}

				result.setSuccessCount(result.getSuccessCount() + 1);
			} catch (Exception e) {
				log.error("导入第{}条记录失败: tableName={}, columnName={}", i + 1, item.getTableName(), item.getColumnName(),
						e);
				result.setFailCount(result.getFailCount() + 1);
				result.addError(String.format("第%d条记录失败 (%s.%s): %s", i + 1, item.getTableName(), item.getColumnName(),
						e.getMessage()));
			}
		}

		return result;
	}

	/**
	 * 同步语义模型到向量数据库
	 * 
	 * @param semanticModel 语义模型对象
	 */
	private void syncSemanticModelToVectorStore(SemanticModel semanticModel) {
		try {
			// 转换为Document
			Document document = DocumentConverterUtil.convertSemanticModelToDocument(semanticModel);

			// 添加到向量数据库
			agentVectorStoreService.addDocuments(semanticModel.getAgentId().toString(), List.of(document));

			log.info("成功同步语义模型到向量数据库: id={}, agentId={}, tableName={}, columnName={}",
					semanticModel.getId(), semanticModel.getAgentId(),
					semanticModel.getTableName(), semanticModel.getColumnName());
		} catch (Exception e) {
			log.error("同步语义模型到向量数据库失败: id={}, agentId={}",
					semanticModel.getId(), semanticModel.getAgentId(), e);
			// 不抛出异常，允许数据库操作成功但向量化失败
		}
	}

	/**
	 * 从向量数据库删除语义模型
	 * 
	 * @param semanticModel 语义模型对象
	 */
	private void deleteSemanticModelFromVectorStore(SemanticModel semanticModel) {
		try {
			// 构建metadata过滤条件
			Map<String, Object> metadata = new HashMap<>();
			metadata.put("semanticModelId", semanticModel.getId());

			// 从向量数据库删除
			agentVectorStoreService.deleteDocumentsByMetedata(semanticModel.getAgentId().toString(), metadata);

			log.info("成功从向量数据库删除语义模型: id={}, agentId={}",
					semanticModel.getId(), semanticModel.getAgentId());
		} catch (Exception e) {
			log.error("从向量数据库删除语义模型失败: id={}, agentId={}",
					semanticModel.getId(), semanticModel.getAgentId(), e);
			// 不抛出异常，允许关系数据库删除成功
		}
	}

	@Override
	public BatchImportResult importFromExcel(MultipartFile file, Long agentId) {
		log.info("开始Excel导入: agentId={}, 文件名={}", agentId, file.getOriginalFilename());

		try {
			// 解析Excel文件
			List<SemanticModelImportItem> items = excelService.parseExcel(file);

			// 组装DTO
			SemanticModelBatchImportDTO dto = SemanticModelBatchImportDTO.builder()
					.agentId(agentId)
					.items(items)
					.build();

			// 执行批量导入
			BatchImportResult result = batchImport(dto);
			log.info("Excel导入完成: 总数={}, 成功={}, 失败={}", result.getTotal(), result.getSuccessCount(),
					result.getFailCount());

			return result;
		} catch (Exception e) {
			log.error("Excel导入失败", e);
			BatchImportResult result = BatchImportResult.builder().total(0).successCount(0).failCount(0).build();
			result.addError("Excel导入失败: " + e.getMessage());
			return result;
		}
	}

}
