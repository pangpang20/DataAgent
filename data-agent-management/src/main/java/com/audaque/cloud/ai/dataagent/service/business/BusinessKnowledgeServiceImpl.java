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
package com.audaque.cloud.ai.dataagent.service.business;

import com.audaque.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.audaque.cloud.ai.dataagent.enums.EmbeddingStatus;
import com.audaque.cloud.ai.dataagent.event.BusinessKnowledgeDeletionEvent;
import com.audaque.cloud.ai.dataagent.event.BusinessKnowledgeEmbeddingEvent;
import com.audaque.cloud.ai.dataagent.util.DocumentConverterUtil;
import com.audaque.cloud.ai.dataagent.converter.BusinessKnowledgeConverter;
import com.audaque.cloud.ai.dataagent.dto.knowledge.BusinessKnowledgeQueryDTO;
import com.audaque.cloud.ai.dataagent.dto.knowledge.businessknowledge.CreateBusinessKnowledgeDTO;
import com.audaque.cloud.ai.dataagent.dto.knowledge.businessknowledge.UpdateBusinessKnowledgeDTO;
import com.audaque.cloud.ai.dataagent.entity.BusinessKnowledge;
import com.audaque.cloud.ai.dataagent.mapper.BusinessKnowledgeMapper;
import com.audaque.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.audaque.cloud.ai.dataagent.vo.BusinessKnowledgeVO;
import com.audaque.cloud.ai.dataagent.vo.PageResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class BusinessKnowledgeServiceImpl implements BusinessKnowledgeService {

	private final BusinessKnowledgeMapper businessKnowledgeMapper;

	private final AgentVectorStoreService agentVectorStoreService;

	private final BusinessKnowledgeConverter businessKnowledgeConverter;

	private final ApplicationEventPublisher eventPublisher;

	@Override
	public List<BusinessKnowledgeVO> getKnowledge(Long agentId) {
		List<BusinessKnowledge> businessKnowledges = businessKnowledgeMapper.selectByAgentId(agentId);
		if (CollectionUtils.isEmpty(businessKnowledges)) {
			return Collections.emptyList();
		}
		return businessKnowledges.stream().map(businessKnowledgeConverter::toVo).toList();
	}

	@Override
	public List<BusinessKnowledgeVO> getAllKnowledge() {
		List<BusinessKnowledge> businessKnowledges = businessKnowledgeMapper.selectAll();
		if (CollectionUtils.isEmpty(businessKnowledges)) {
			return Collections.emptyList();
		}
		return businessKnowledges.stream().map(businessKnowledgeConverter::toVo).toList();
	}

	@Override
	public List<BusinessKnowledgeVO> searchKnowledge(Long agentId, String keyword) {
		List<BusinessKnowledge> businessKnowledges = businessKnowledgeMapper.searchInAgent(agentId, keyword);
		if (CollectionUtils.isEmpty(businessKnowledges)) {
			return Collections.emptyList();
		}
		return businessKnowledges.stream().map(businessKnowledgeConverter::toVo).toList();
	}

	@Override
	public BusinessKnowledgeVO getKnowledgeById(Long id) {
		BusinessKnowledge businessKnowledge = businessKnowledgeMapper.selectById(id);
		if (businessKnowledge == null) {
			return null;
		}
		return businessKnowledgeConverter.toVo(businessKnowledge);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public BusinessKnowledgeVO addKnowledge(CreateBusinessKnowledgeDTO knowledgeDTO) {
		BusinessKnowledge entity = businessKnowledgeConverter.toEntityForCreate(knowledgeDTO);

		// 插入数据库
		if (businessKnowledgeMapper.insert(entity) <= 0) {
			throw new RuntimeException("Failed to add knowledge to database");
		}

		// 发布向量化事件，异步处理
		eventPublisher.publishEvent(new BusinessKnowledgeEmbeddingEvent(this, entity.getId()));
		log.info("Published BusinessKnowledgeEmbeddingEvent for id: {}", entity.getId());

		return businessKnowledgeConverter.toVo(entity);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public BusinessKnowledgeVO updateKnowledge(Long id, UpdateBusinessKnowledgeDTO knowledgeDTO) {
		// 从数据库获取原始数据
		BusinessKnowledge knowledge = businessKnowledgeMapper.selectById(id);
		if (knowledge == null) {
			throw new RuntimeException("Knowledge not found with id: " + id);
		}
		// 更新属性
		knowledge.setBusinessTerm(knowledgeDTO.getBusinessTerm());
		knowledge.setDescription(knowledgeDTO.getDescription());
		if (StringUtils.hasText(knowledgeDTO.getSynonyms()))
			knowledge.setSynonyms(knowledgeDTO.getSynonyms());

		// 设置初始状态为 PENDING，异步任务会将其改为 PROCESSING
		knowledge.setEmbeddingStatus(EmbeddingStatus.PENDING);
		knowledge.setUpdatedTime(LocalDateTime.now());

		// 先更新数据库
		if (businessKnowledgeMapper.updateById(knowledge) <= 0) {
			throw new RuntimeException("Failed to update knowledge in database");
		}

		// 发布向量化事件，异步处理
		eventPublisher.publishEvent(new BusinessKnowledgeEmbeddingEvent(this, knowledge.getId()));
		log.info("Published BusinessKnowledgeEmbeddingEvent for id: {}", knowledge.getId());

		return businessKnowledgeConverter.toVo(knowledge);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void deleteKnowledge(Long id) {
		// 从数据库获取原始数据
		BusinessKnowledge knowledge = businessKnowledgeMapper.selectById(id);
		if (knowledge == null) {
			log.warn("Knowledge not found with id: " + id);
			return;
		}

		// 执行逻辑删除
		if (businessKnowledgeMapper.logicalDelete(id, 1, LocalDateTime.now()) <= 0) {
			throw new RuntimeException("Failed to logically delete knowledge from database");
		}

		// 发布删除事件，异步清理向量数据
		eventPublisher.publishEvent(new BusinessKnowledgeDeletionEvent(this, id));
		log.info("Published BusinessKnowledgeDeletionEvent for id: {}", id);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void recallKnowledge(Long id, Boolean isRecall) {
		// 从数据库获取原始数据
		BusinessKnowledge knowledge = businessKnowledgeMapper.selectById(id);
		if (knowledge == null) {
			throw new RuntimeException("Knowledge not found with id: " + id);
		}

		// 更新数据库即可，不需要更新向量库，混合检索的的时候DynamicFilterService会根据 isRecall 字段过滤了
		knowledge.setIsRecall(isRecall ? 1 : 0);
		businessKnowledgeMapper.updateById(knowledge);

	}

	@Override
	public void refreshAllKnowledgeToVectorStore(String agentId) throws Exception {
		agentVectorStoreService.deleteDocumentsByVectorType(agentId, DocumentMetadataConstant.BUSINESS_TERM);

		// 获取所有 isRecall 等于 1 且未逻辑删除的 BusinessKnowledge
		List<BusinessKnowledge> allKnowledge = businessKnowledgeMapper.selectAll();
		List<BusinessKnowledge> recalledKnowledge = allKnowledge.stream()
				.filter(knowledge -> knowledge.getIsRecall() != null && knowledge.getIsRecall() == 1)
				.filter(knowledge -> knowledge.getIsDeleted() == null || knowledge.getIsDeleted() == 0)
				.filter(knowledge -> agentId.equals(knowledge.getAgentId().toString()))
				.toList();

		// 转换为 Document 并插入到 vectorStore
		if (!recalledKnowledge.isEmpty()) {
			List<Document> documents = recalledKnowledge.stream()
					.map(DocumentConverterUtil::convertBusinessKnowledgeToDocument)
					.toList();
			agentVectorStoreService.addDocuments(agentId, documents);
		}
	}

	@Override
	public void retryEmbedding(Long id) {
		BusinessKnowledge knowledge = businessKnowledgeMapper.selectById(id);
		if (knowledge == null) {
			throw new RuntimeException("BusinessKnowledge not found with id: " + id);
		}

		if (knowledge.getEmbeddingStatus().equals(EmbeddingStatus.PROCESSING)) {
			throw new RuntimeException("BusinessKnowledge is processing, please wait.");
		}

		// 非召回的不处理
		if (knowledge.getIsRecall() == null || knowledge.getIsRecall() == 0) {
			throw new RuntimeException("BusinessKnowledge is not recalled, please recall it first.");
		}

		// 重置状态为 PENDING，异步任务会将其改为 PROCESSING
		knowledge.setEmbeddingStatus(EmbeddingStatus.PENDING);
		knowledge.setErrorMsg(null);
		knowledge.setUpdatedTime(LocalDateTime.now());
		businessKnowledgeMapper.updateById(knowledge);

		// 发布向量化事件，异步处理
		eventPublisher.publishEvent(new BusinessKnowledgeEmbeddingEvent(this, id));
		log.info("Published retry BusinessKnowledgeEmbeddingEvent for id: {}", id);
	}

	@Override
	public PageResult<BusinessKnowledgeVO> queryByConditionsWithPage(BusinessKnowledgeQueryDTO queryDTO) {
		log.info("Page query business knowledge: agentId={}, pageNum={}, pageSize={}, keyword={}",
				queryDTO.getAgentId(), queryDTO.getPageNum(), queryDTO.getPageSize(), queryDTO.getKeyword());

		if (queryDTO.getAgentId() == null) {
			throw new IllegalArgumentException("agentId cannot be null");
		}

		int offset = queryDTO.calculateOffset();

		Long total = businessKnowledgeMapper.countByConditions(queryDTO);
		List<BusinessKnowledge> dataList = businessKnowledgeMapper.selectByConditionsWithPage(queryDTO, offset);

		List<BusinessKnowledgeVO> voList = dataList.stream()
				.map(businessKnowledgeConverter::toVo)
				.toList();

		PageResult<BusinessKnowledgeVO> pageResult = new PageResult<>();
		pageResult.setData(voList);
		pageResult.setTotal(total);
		pageResult.setPageNum(queryDTO.getPageNum());
		pageResult.setPageSize(queryDTO.getPageSize());
		pageResult.calculateTotalPages();

		return pageResult;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int batchDelete(Long agentId, List<Long> ids) {
		log.info("Batch delete business knowledge: agentId={}, count={}", agentId, ids.size());

		if (ids == null || ids.isEmpty()) {
			throw new IllegalArgumentException("IDs cannot be empty");
		}

		int affected = businessKnowledgeMapper.batchDeleteByIds(agentId, ids);

		// Publish deletion events for each deleted record
		ids.forEach(id -> eventPublisher.publishEvent(new BusinessKnowledgeDeletionEvent(this, id)));

		log.info("Batch delete completed: requested={}, affected={}", ids.size(), affected);
		return affected;
	}

}
