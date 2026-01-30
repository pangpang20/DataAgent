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
package com.audaque.cloud.ai.dataagent.event;

import com.audaque.cloud.ai.dataagent.constant.Constant;
import com.audaque.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.audaque.cloud.ai.dataagent.enums.EmbeddingStatus;
import com.audaque.cloud.ai.dataagent.entity.BusinessKnowledge;
import com.audaque.cloud.ai.dataagent.mapper.BusinessKnowledgeMapper;
import com.audaque.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.audaque.cloud.ai.dataagent.util.DocumentConverterUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class BusinessKnowledgeEventListener {

	private final BusinessKnowledgeMapper businessKnowledgeMapper;

	private final AgentVectorStoreService agentVectorStoreService;

	/**
	 * 处理业务知识向量化事件
	 * phase = TransactionPhase.AFTER_COMMIT 核心作用：只有当 Service 层的主事务提交成功后，才会执行这个方法。
	 */
	@Async("dbOperationExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleEmbeddingEvent(BusinessKnowledgeEmbeddingEvent event) {
		log.info("Received BusinessKnowledgeEmbeddingEvent. businessKnowledgeId: {}", event.getKnowledgeId());
		Long id = event.getKnowledgeId();

		// 1. 查询数据
		BusinessKnowledge knowledge = businessKnowledgeMapper.selectById(id);
		if (knowledge == null) {
			log.error("BusinessKnowledge not found during async processing. Id: {}", id);
			return;
		}

		try {
			// 2. 更新状态为 PROCESSING
			updateStatus(knowledge, EmbeddingStatus.PROCESSING, null);

			// 3. 执行向量化逻辑：先删除旧向量，再添加新向量
			doSyncToVectorStore(knowledge);

			// 4. 更新状态为 COMPLETED
			updateStatus(knowledge, EmbeddingStatus.COMPLETED, null);

			log.info("Successfully embedded BusinessKnowledge. Id: {}", id);

		}
		catch (Exception e) {
			log.error("Failed to embed BusinessKnowledge. Id: {}", id, e);
			// 5. 失败处理
			updateStatus(knowledge, EmbeddingStatus.FAILED, e.getMessage());
		}
		log.info("Finished processing BusinessKnowledgeEmbeddingEvent. businessKnowledgeId: {}",
				event.getKnowledgeId());

	}

	/**
	 * 处理业务知识删除事件
	 */
	@Async("dbOperationExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleDeletionEvent(BusinessKnowledgeDeletionEvent event) {
		Long id = event.getKnowledgeId();
		log.info("Starting async vector cleanup for businessKnowledgeId: {}", id);

		// 1. 重新查询
		BusinessKnowledge knowledge = businessKnowledgeMapper.selectById(id);
		if (knowledge == null) {
			log.warn("BusinessKnowledge record not found or already deleted, skipping cleanup. ID: {}", id);
			return;
		}

		try {
			// 2. 删除向量数据
			doDelVector(knowledge);
			log.info("Vector deleted successfully for BusinessKnowledgeID: {}", id);

		}
		catch (Exception e) {
			log.error("Exception during async vector cleanup for businessKnowledgeId: {}", id, e);
		}
	}

	/**
	 * 更新业务知识状态
	 */
	private void updateStatus(BusinessKnowledge knowledge, EmbeddingStatus status, String errorMsg) {
		knowledge.setEmbeddingStatus(status);
		knowledge.setUpdatedTime(LocalDateTime.now());
		if (errorMsg != null) {
			// 截断错误信息防止数据库报错
			knowledge.setErrorMsg(errorMsg.length() > 250 ? errorMsg.substring(0, 250) : errorMsg);
		}
		else {
			knowledge.setErrorMsg(null);
		}
		businessKnowledgeMapper.updateById(knowledge);
	}

	/**
	 * 同步到向量库：先删除旧向量，再添加新向量
	 */
	private void doSyncToVectorStore(BusinessKnowledge knowledge) {
		// 先删除旧的向量数据
		doDelVector(knowledge);

		// 添加新的向量数据
		Document newDocument = DocumentConverterUtil.convertBusinessKnowledgeToDocument(knowledge);
		agentVectorStoreService.addDocuments(knowledge.getAgentId().toString(), List.of(newDocument));

		log.info("Successfully synced BusinessKnowledge to vector store. Id: {}", knowledge.getId());
	}

	/**
	 * 删除向量数据
	 */
	private void doDelVector(BusinessKnowledge knowledge) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(Constant.AGENT_ID, knowledge.getAgentId().toString());
		metadata.put(DocumentMetadataConstant.DB_BUSINESS_TERM_ID, knowledge.getId());
		metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.BUSINESS_TERM);
		agentVectorStoreService.deleteDocumentsByMetedata(knowledge.getAgentId().toString(), metadata);
	}

}
