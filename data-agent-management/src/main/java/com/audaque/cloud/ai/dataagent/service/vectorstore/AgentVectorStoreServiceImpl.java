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
package com.audaque.cloud.ai.dataagent.service.vectorstore;

import com.audaque.cloud.ai.dataagent.constant.Constant;
import com.audaque.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.audaque.cloud.ai.dataagent.properties.DataAgentProperties;
import com.audaque.cloud.ai.dataagent.dto.search.AgentSearchRequest;
import com.audaque.cloud.ai.dataagent.dto.search.HybridSearchRequest;
import com.audaque.cloud.ai.dataagent.service.hybrid.retrieval.HybridRetrievalStrategy;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.R;
import io.milvus.param.collection.FlushParam;
import io.milvus.grpc.FlushResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.*;

import static com.audaque.cloud.ai.dataagent.service.vectorstore.DynamicFilterService.buildFilterExpressionString;

@Slf4j
@Service
public class AgentVectorStoreServiceImpl implements AgentVectorStoreService {

	private static final String DEFAULT = "default";

	private final VectorStore vectorStore;

	private final Optional<HybridRetrievalStrategy> hybridRetrievalStrategy;

	private final DataAgentProperties dataAgentProperties;

	private final DynamicFilterService dynamicFilterService;

	private final Optional<MilvusServiceClient> milvusClient;

	@Value("${spring.ai.vectorstore.milvus.collection-name:data_agent_vector}")
	private String collectionName;

	@Value("${spring.ai.vectorstore.milvus.flush-delay-ms:1000}")
	private int flushDelayMs;

	@Value("${spring.ai.vectorstore.milvus.flush-retry-count:3}")
	private int flushRetryCount;

	@Value("${spring.ai.vectorstore.milvus.flush-initial-backoff-ms:1000}")
	private int flushInitialBackoffMs;

	public AgentVectorStoreServiceImpl(VectorStore vectorStore,
			Optional<HybridRetrievalStrategy> hybridRetrievalStrategy, DataAgentProperties dataAgentProperties,
			DynamicFilterService dynamicFilterService, Optional<MilvusServiceClient> milvusClient) {
		this.vectorStore = vectorStore;
		this.hybridRetrievalStrategy = hybridRetrievalStrategy;
		this.dataAgentProperties = dataAgentProperties;
		this.dynamicFilterService = dynamicFilterService;
		this.milvusClient = milvusClient;
		log.info("VectorStore type: {}, MilvusClient present: {}",
				vectorStore.getClass().getSimpleName(), milvusClient.isPresent());
	}

	@Override
	public List<Document> search(AgentSearchRequest searchRequest) {
		Assert.hasText(searchRequest.getAgentId(), "AgentId cannot be empty");
		Assert.hasText(searchRequest.getDocVectorType(), "DocVectorType cannot be empty");

		Filter.Expression filter = dynamicFilterService.buildDynamicFilter(searchRequest.getAgentId(),
				searchRequest.getDocVectorType());
		// 根据agentId vectorType找不到要 召回 的业务知识或者智能体知识
		if (filter == null) {
			log.warn(
					"Dynamic filter returned null (no valid ids), returning empty result directly.AgentId: {}, VectorType: {}",
					searchRequest.getAgentId(), searchRequest.getDocVectorType());
			return Collections.emptyList();
		}

		HybridSearchRequest hybridRequest = HybridSearchRequest.builder()
				.query(searchRequest.getQuery())
				.topK(searchRequest.getTopK())
				.similarityThreshold(searchRequest.getSimilarityThreshold())
				.filterExpression(filter)
				.build();

		if (dataAgentProperties.getVectorStore().isEnableHybridSearch() && hybridRetrievalStrategy.isPresent()) {
			return hybridRetrievalStrategy.get().retrieve(hybridRequest);
		}
		log.debug("Hybrid search is not enabled. use vector-search only");
		List<Document> results = vectorStore.similaritySearch(hybridRequest.toVectorSearchRequest());
		log.debug("Search completed with vectorType: {}, found {} documents for SearchRequest: {}",
				searchRequest.getDocVectorType(), results.size(), searchRequest);
		return results;

	}

	@Override
	public Boolean deleteDocumentsByVectorType(String agentId, String vectorType) throws Exception {
		Assert.notNull(agentId, "AgentId cannot be null.");
		Assert.notNull(vectorType, "VectorType cannot be null.");

		Map<String, Object> metadata = new HashMap<>(Map.ofEntries(Map.entry(Constant.AGENT_ID, agentId),
				Map.entry(DocumentMetadataConstant.VECTOR_TYPE, vectorType)));

		return this.deleteDocumentsByMetedata(agentId, metadata);
	}

	@Override
	public void addDocuments(String agentId, List<Document> documents) {
		Assert.notNull(agentId, "AgentId cannot be null.");
		Assert.notEmpty(documents, "Documents cannot be empty.");

		log.info("准备向 Milvus 插入 {} 个文档，agentId: {}", documents.size(), agentId);

		// 校验文档中metadata中包含的agentId
		for (int i = 0; i < documents.size(); i++) {
			Document document = documents.get(i);
			Assert.notNull(document.getMetadata(), "Document metadata cannot be null.");
			Assert.isTrue(document.getMetadata().containsKey(Constant.AGENT_ID),
					"Document metadata must contain agentId.");
			Assert.isTrue(document.getMetadata().get(Constant.AGENT_ID).equals(agentId),
					"Document metadata agentId does not match.");

			// 详细日志：打印每个文档的关键信息
			if (i == 0 || log.isDebugEnabled()) {
				log.info("文档[{}] - id: {}, content长度: {}, metadata keys: {}, vectorType: {}",
						i,
						document.getId(),
						document.getText() != null ? document.getText().length() : 0,
						document.getMetadata().keySet(),
						document.getMetadata().get(DocumentMetadataConstant.VECTOR_TYPE));
			}

			// 检查 id 字段
			if (document.getId() == null || document.getId().isEmpty()) {
				log.error("文档[{}] 缺少 id 字段！metadata: {}", i, document.getMetadata());
				throw new IllegalArgumentException("Document id cannot be null or empty at index " + i);
			}
		}

		log.info("开始调用 VectorStore.add() 插入文档...");
		try {
			vectorStore.add(documents);
			log.info("成功向 Milvus 插入 {} 个文档", documents.size());

			// 显式 flush 确保数据持久化并立即可搜索
			flushMilvus();
		} catch (Exception e) {
			log.error("向 Milvus 插入文档失败: {}", e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * 显式 flush Milvus 集合，确保数据持久化并立即可搜索
	 */
	private void flushMilvus() {
		if (milvusClient.isEmpty()) {
			log.debug("MilvusClient not available, skip flush");
			return;
		}

		// 先等待基础延迟时间
		try {
			log.debug("等待 {} ms 后执行 flush 操作以避免速率限制", flushDelayMs);
			Thread.sleep(flushDelayMs);
		} catch (InterruptedException e) {
			log.warn("初始延迟等待被中断: {}", e.getMessage());
			Thread.currentThread().interrupt(); // 重新设置中断状态
			return;
		}

		// 执行带重试的 flush 操作
		int attempt = 0;
		int backoffMs = flushInitialBackoffMs;
		while (attempt < flushRetryCount) {
			try {
				FlushParam flushParam = FlushParam.newBuilder()
						.addCollectionName(collectionName)
						.build();
				R<FlushResponse> response = milvusClient.get().flush(flushParam);
				if (response.getStatus() == R.Status.Success.getCode()) {
					log.info("Milvus flush 成功，collection: {}，尝试次数: {}", collectionName, attempt + 1);
					return; // 成功则直接返回
				} else {
					log.warn("Milvus flush 返回非成功状态: {}, message: {}，尝试次数: {}",
							response.getStatus(), response.getMessage(), attempt + 1);
				}
			} catch (Exception e) {
				log.warn("Milvus flush 尝试 {} 失败: {}", attempt + 1, e.getMessage());
			}

			attempt++;
			if (attempt < flushRetryCount) {
				try {
					log.debug("等待 {} ms 后进行第 {} 次重试", backoffMs, attempt + 1);
					Thread.sleep(backoffMs);
					backoffMs *= 2; // 指数退避
				} catch (InterruptedException ie) {
					log.warn("Flush 重试等待被中断: {}", ie.getMessage());
					Thread.currentThread().interrupt(); // 重新设置中断状态
					return;
				}
			}
		}

		log.error("Milvus flush 达到最大重试次数 {}，操作失败", flushRetryCount);
	}

	@Override
	public Boolean deleteDocumentsByMetedata(String agentId, Map<String, Object> metadata) {
		Assert.hasText(agentId, "AgentId cannot be empty.");
		Assert.notNull(metadata, "Metadata cannot be null.");
		// 添加agentId元数据过滤条件, 用于删除指定agentId下的所有数据，因为metadata中用户调用可能忘记添加agentId
		metadata.put(Constant.AGENT_ID, agentId);
		String filterExpression = buildFilterExpressionString(metadata);

		// es的可以直接元数据删除
		if (vectorStore instanceof SimpleVectorStore) {
			// 目前SimpleVectorStore不支持通过元数据删除，使用会抛出UnsupportedOperationException,现在是通过id删除
			batchDelDocumentsWithFilter(filterExpression);
		} else {
			vectorStore.delete(filterExpression);
		}

		return true;
	}

	private void batchDelDocumentsWithFilter(String filterExpression) {
		Set<String> seenDocumentIds = new HashSet<>();
		// 分批获取，因为Milvus等向量数据库的topK有限制
		List<Document> batch;
		int newDocumentsCount;
		int totalDeleted = 0;

		do {
			batch = vectorStore.similaritySearch(org.springframework.ai.vectorstore.SearchRequest.builder()
					.query(DEFAULT)// 使用默认的查询字符串，因为有的嵌入模型不支持空字符串
					.filterExpression(filterExpression)
					.similarityThreshold(0.0)// 设置最低相似度阈值以获取元数据匹配的所有文档
					.topK(dataAgentProperties.getVectorStore().getBatchDelTopkLimit())
					.build());

			// 过滤掉已经处理过的文档，只删除未处理的文档
			List<String> idsToDelete = new ArrayList<>();
			newDocumentsCount = 0;

			for (Document doc : batch) {
				if (seenDocumentIds.add(doc.getId())) {
					// 如果add返回true，表示这是一个新的文档ID
					idsToDelete.add(doc.getId());
					newDocumentsCount++;
				}
			}

			// 删除这批新文档
			if (!idsToDelete.isEmpty()) {
				vectorStore.delete(idsToDelete);
				totalDeleted += idsToDelete.size();
			}

		} while (newDocumentsCount > 0); // 只有当获取到新文档时才继续循环

		log.info("Deleted {} documents with filter expression: {}", totalDeleted, filterExpression);
	}

	@Override
	public List<Document> getDocumentsForAgent(String agentId, String query, String vectorType) {
		// 使用全局默认配置
		int defaultTopK = dataAgentProperties.getVectorStore().getDefaultTopkLimit();
		double defaultThreshold = dataAgentProperties.getVectorStore().getDefaultSimilarityThreshold();

		return getDocumentsForAgent(agentId, query, vectorType, defaultTopK, defaultThreshold);
	}

	@Override
	public List<Document> getDocumentsForAgent(String agentId, String query, String vectorType, int topK,
			double threshold) {
		AgentSearchRequest searchRequest = AgentSearchRequest.builder()
				.agentId(agentId)
				.docVectorType(vectorType)
				.query(query)
				.topK(topK) // 使用传入的参数
				.similarityThreshold(threshold) // 使用传入的参数
				.build();
		return search(searchRequest);
	}

	@Override
	public List<Document> getDocumentsOnlyByFilter(Filter.Expression filterExpression, Integer topK) {
		Assert.notNull(filterExpression, "filterExpression cannot be null.");
		if (topK == null)
			topK = dataAgentProperties.getVectorStore().getDefaultTopkLimit();
		SearchRequest searchRequest = SearchRequest.builder()
				.query(DEFAULT)
				.topK(topK)
				.filterExpression(filterExpression)
				.similarityThreshold(0.0)
				.build();
		return vectorStore.similaritySearch(searchRequest);
	}

	@Override
	public boolean hasDocuments(String agentId) {
		// 类似 MySQL 的 LIMIT 1,只检查是否存在文档
		List<Document> docs = vectorStore.similaritySearch(org.springframework.ai.vectorstore.SearchRequest.builder()
				.query(DEFAULT)// 使用默认的查询字符串，因为有的嵌入模型不支持空字符串
				.filterExpression(buildFilterExpressionString(Map.of(Constant.AGENT_ID, agentId)))
				.topK(1) // 只获取1个文档
				.similarityThreshold(0.0)
				.build());
		return !docs.isEmpty();
	}

}
