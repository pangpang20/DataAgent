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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

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

	@Value("${spring.ai.vectorstore.milvus.flush-max-concurrency:1}")
	private int flushMaxConcurrency;

	@Value("${spring.ai.vectorstore.milvus.flush-auto:false}")
	private boolean flushAuto;

	private final Semaphore flushSemaphore = new Semaphore(flushMaxConcurrency);

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
		log.info("Milvus configuration - Collection: {}, Auto Flush: {}, Max Concurrency: {}, Delay: {}ms",
				collectionName, flushAuto, flushMaxConcurrency, flushDelayMs);
	}

	@Override
	public List<Document> search(AgentSearchRequest searchRequest) {
		log.info("=== Starting search operation ===");
		log.info("Search parameters - agentId: {}, vectorType: {}, query: {}, topK: {}, similarityThreshold: {}",
				searchRequest.getAgentId(),
				searchRequest.getDocVectorType(),
				searchRequest.getQuery(),
				searchRequest.getTopK(),
				searchRequest.getSimilarityThreshold());

		Assert.hasText(searchRequest.getAgentId(), "AgentId cannot be empty");
		Assert.hasText(searchRequest.getDocVectorType(), "DocVectorType cannot be empty");

		log.debug("Building dynamic filter for agentId: {}, vectorType: {}",
				searchRequest.getAgentId(), searchRequest.getDocVectorType());
		Filter.Expression filter = dynamicFilterService.buildDynamicFilter(searchRequest.getAgentId(),
				searchRequest.getDocVectorType());

		// 根据agentId vectorType找不到要 召回 的业务知识或者智能体知识
		if (filter == null) {
			log.warn(
					"Dynamic filter returned null (no valid ids), returning empty result directly.AgentId: {}, VectorType: {}",
					searchRequest.getAgentId(), searchRequest.getDocVectorType());
			log.info("=== Search operation completed (no valid filter) ===");
			return Collections.emptyList();
		}
		log.debug("Dynamic filter built successfully: {}", filter);

		HybridSearchRequest hybridRequest = HybridSearchRequest.builder()
				.query(searchRequest.getQuery())
				.topK(searchRequest.getTopK())
				.similarityThreshold(searchRequest.getSimilarityThreshold())
				.filterExpression(filter)
				.build();
		log.debug("Hybrid search request built: {}", hybridRequest);

		if (dataAgentProperties.getVectorStore().isEnableHybridSearch() && hybridRetrievalStrategy.isPresent()) {
			log.info("Using hybrid search strategy for agentId: {}, vectorType: {}",
					searchRequest.getAgentId(), searchRequest.getDocVectorType());
			List<Document> results = hybridRetrievalStrategy.get().retrieve(hybridRequest);
			log.info("=== Search operation completed with hybrid search, found {} documents ===", results.size());
			return results;
		}
		log.debug("Hybrid search is not enabled. use vector-search only");
		log.debug("Executing vector similarity search with topK: {}, similarityThreshold: {}",
				searchRequest.getTopK(), searchRequest.getSimilarityThreshold());
		List<Document> results = vectorStore.similaritySearch(hybridRequest.toVectorSearchRequest());
		log.info("=== Search operation completed with vector search, found {} documents ===", results.size());
		log.debug("Search results details - vectorType: {}, topK: {}, similarityThreshold: {}, actual count: {}",
				searchRequest.getDocVectorType(),
				searchRequest.getTopK(),
				searchRequest.getSimilarityThreshold(),
				results.size());
		return results;

	}

	@Override
	public Boolean deleteDocumentsByVectorType(String agentId, String vectorType) throws Exception {
		log.info("=== Starting deleteDocumentsByVectorType operation ===");
		log.info("Delete parameters - agentId: {}, vectorType: {}", agentId, vectorType);

		Assert.notNull(agentId, "AgentId cannot be null.");
		Assert.notNull(vectorType, "VectorType cannot be null.");

		Map<String, Object> metadata = new HashMap<>(Map.ofEntries(Map.entry(Constant.AGENT_ID, agentId),
				Map.entry(DocumentMetadataConstant.VECTOR_TYPE, vectorType)));
		log.debug("Built metadata for deletion: {}", metadata);

		Boolean result = this.deleteDocumentsByMetedata(agentId, metadata);
		log.info("=== DeleteDocumentsByVectorType operation completed, result: {} ===", result);
		return result;
	}

	@Override
	public void addDocuments(String agentId, List<Document> documents) {
		Assert.notNull(agentId, "AgentId cannot be null.");
		Assert.notEmpty(documents, "Documents cannot be empty.");

		log.info("Preparing to insert {} documents into Milvus, agentId: {}", documents.size(), agentId);

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
				log.info("Document[{}] - id: {}, contentLength: {}, metadata keys: {}, vectorType: {}",
						i,
						document.getId(),
						document.getText() != null ? document.getText().length() : 0,
						document.getMetadata().keySet(),
						document.getMetadata().get(DocumentMetadataConstant.VECTOR_TYPE));

				// 输出原始文档内容的预览(前50字符...后50字符)
				if (document.getText() != null && !document.getText().isEmpty()) {
					String text = document.getText();
					String preview;
					if (text.length() > 100) {
						preview = text.substring(0, 50) + "..." + text.substring(text.length() - 50);
					} else {
						preview = text;
					}
					log.info("Document[{}] - content preview: {}", i, preview);
				} else {
					log.info("Document[{}] - content is empty", i);
				}
			}

			// 检查 id 字段
			if (document.getId() == null || document.getId().isEmpty()) {
				log.error("Document[{}] missing id field! metadata: {}", i, document.getMetadata());
				throw new IllegalArgumentException("Document id cannot be null or empty at index " + i);
			}
		}

		log.info("Starting VectorStore.add() to insert documents...");
		try {
			vectorStore.add(documents);
			log.info("Successfully inserted {} documents into Milvus", documents.size());

			// 根据配置决定是否执行异步 flush
			if (flushAuto) {
				log.info("Auto flush is enabled, triggering async flush operation");
				flushMilvusAsync();
			} else {
				log.info("Auto flush is disabled, documents inserted but not immediately searchable. " +
						"Use manualFlush() API or enable auto flush for immediate availability.");
			}
		} catch (Exception e) {
			log.error("Failed to insert documents into Milvus: {}", e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * 手动触发 Milvus flush 操作
	 * 当 auto flush 被禁用时，可以通过此方法手动执行 flush
	 */
	public void manualFlush() {
		if (!flushAuto) {
			log.info("Manual flush triggered, executing flush operation");
			flushMilvus();
		} else {
			log.info("Auto flush is enabled, manual flush not needed");
		}
	}

	/**
	 * 异步 flush Milvus 集合，确保数据持久化并立即可搜索，不阻塞主线程
	 */
	@Async
	public CompletableFuture<Void> flushMilvusAsync() {
		try {
			flushMilvus();
			return CompletableFuture.completedFuture(null);
		} catch (Exception e) {
			log.error("Async flush Milvus failed: {}", e.getMessage(), e);
			return CompletableFuture.failedFuture(e);
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

		// 使用信号量控制并发 flush 操作，避免超出 Milvus 速率限制
		try {
			if (!flushSemaphore.tryAcquire(10, TimeUnit.SECONDS)) {
				log.warn("Cannot acquire flush semaphore, timed out after 10 seconds. " +
						"This may indicate high concurrent load or Milvus rate limiting. " +
						"Consider enabling auto flush or reducing concurrent operations.");
				return;
			}
		} catch (InterruptedException e) {
			log.warn("Acquiring flush semaphore interrupted: {}", e.getMessage());
			Thread.currentThread().interrupt();
			return;
		}

		try {
			// 先等待基础延迟时间
			try {
				log.debug("Waiting {} ms before flush operation to avoid rate limiting", flushDelayMs);
				Thread.sleep(flushDelayMs);
			} catch (InterruptedException e) {
				log.warn("Initial delay wait interrupted: {}", e.getMessage());
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
						log.info("Milvus flush successful, collection: {}, attempt: {}", collectionName, attempt + 1);
						return; // 成功则直接返回
					} else {
						log.warn("Milvus flush returned non-success status: {}, message: {}, attempt: {}",
								response.getStatus(), response.getMessage(), attempt + 1);
					}
				} catch (Exception e) {
					String errorMessage = e.getMessage();
					if (errorMessage != null && errorMessage.contains("rate limit exceeded")) {
						log.warn("Rate limit error detected, waiting longer before retry: {}", errorMessage);
						// 如果是速率限制错误，使用更长的退避时间
						backoffMs = Math.max(backoffMs, 15000); // 至少等待15秒
					}
					log.warn("Milvus flush attempt {} failed: {}", attempt + 1, e.getMessage());
				}

				attempt++;
				if (attempt < flushRetryCount) {
					try {
						log.debug("Waiting {} ms before retry attempt {}", backoffMs, attempt + 1);
						Thread.sleep(backoffMs);
						backoffMs = (int) (backoffMs * 2.5); // 更激进的指数退避（从2倍改为2.5倍）
					} catch (InterruptedException ie) {
						log.warn("Flush retry wait interrupted: {}", ie.getMessage());
						Thread.currentThread().interrupt(); // 重新设置中断状态
						return;
					}
				}
			}

			log.error("Milvus flush reached maximum retry count {}, operation failed", flushRetryCount);
		} finally {
			// 释放信号量
			flushSemaphore.release();
		}
	}

	@Override
	public Boolean deleteDocumentsByMetedata(String agentId, Map<String, Object> metadata) {
		log.info("=== Starting deleteDocumentsByMetedata operation ===");
		log.info("Delete parameters - agentId: {}, metadata: {}", agentId, metadata);

		Assert.hasText(agentId, "AgentId cannot be empty.");
		Assert.notNull(metadata, "Metadata cannot be null.");

		// 添加agentId元数据过滤条件, 用于删除指定agentId下的所有数据，因为metadata中用户调用可能忘记添加agentId
		metadata.put(Constant.AGENT_ID, agentId);
		String filterExpression = buildFilterExpressionString(metadata);
		log.debug("Built filter expression for deletion: {}", filterExpression);

		// es的可以直接元数据删除
		if (vectorStore instanceof SimpleVectorStore) {
			log.info("Using SimpleVectorStore, proceeding with batch deletion by filter");
			// 目前SimpleVectorStore不支持通过元数据删除，使用会抛出UnsupportedOperationException,现在是通过id删除
			batchDelDocumentsWithFilter(filterExpression);
		} else {
			log.info("Using vectorStore.delete() with filter expression");
			vectorStore.delete(filterExpression);
		}

		log.info("=== DeleteDocumentsByMetedata operation completed successfully ===");
		return true;
	}

	private void batchDelDocumentsWithFilter(String filterExpression) {
		log.info("=== Starting batch delete with filter ===");
		log.info("Filter expression: {}", filterExpression);

		Set<String> seenDocumentIds = new HashSet<>();
		// 分批获取，因为Milvus等向量数据库的topK有限制
		List<Document> batch;
		int newDocumentsCount;
		int totalDeleted = 0;
		int batchNumber = 0;

		do {
			batchNumber++;
			log.debug("Processing batch #{} for filter: {}", batchNumber, filterExpression);
			log.debug("Fetching documents with topK limit: {}",
					dataAgentProperties.getVectorStore().getBatchDelTopkLimit());

			batch = vectorStore.similaritySearch(org.springframework.ai.vectorstore.SearchRequest.builder()
					.query(DEFAULT)// 使用默认的查询字符串，因为有的嵌入模型不支持空字符串
					.filterExpression(filterExpression)
					.similarityThreshold(0.0)// 设置最低相似度阈值以获取元数据匹配的所有文档
					.topK(dataAgentProperties.getVectorStore().getBatchDelTopkLimit())
					.build());

			log.debug("Batch #{} fetched {} documents", batchNumber, batch.size());

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

			log.debug("Batch #{} - {} new documents to delete, {} duplicates skipped",
					batchNumber, newDocumentsCount, batch.size() - newDocumentsCount);

			// 删除这批新文档
			if (!idsToDelete.isEmpty()) {
				log.info("Deleting {} documents in batch #{}", idsToDelete.size(), batchNumber);
				vectorStore.delete(idsToDelete);
				totalDeleted += idsToDelete.size();
				log.info("Batch #{} deletion completed, total deleted so far: {}",
						batchNumber, totalDeleted);
			}

		} while (newDocumentsCount > 0); // 只有当获取到新文档时才继续循环

		log.info("=== Batch delete completed - total {} documents deleted with filter: {} ===",
				totalDeleted, filterExpression);
	}

	@Override
	public List<Document> getDocumentsForAgent(String agentId, String query, String vectorType) {
		log.info("=== Starting getDocumentsForAgent with default parameters ===");
		log.info("Parameters - agentId: {}, query: {}, vectorType: {}", agentId, query, vectorType);

		// 使用全局默认配置
		int defaultTopK = dataAgentProperties.getVectorStore().getDefaultTopkLimit();
		double defaultThreshold = dataAgentProperties.getVectorStore().getDefaultSimilarityThreshold();

		log.debug("Using default parameters - topK: {}, threshold: {}", defaultTopK, defaultThreshold);

		List<Document> result = getDocumentsForAgent(agentId, query, vectorType, defaultTopK, defaultThreshold);
		log.info("=== getDocumentsForAgent completed, found {} documents ===", result.size());
		return result;
	}

	@Override
	public List<Document> getDocumentsForAgent(String agentId, String query, String vectorType, int topK,
			double threshold) {
		log.info("=== Starting getDocumentsForAgent with custom parameters ===");
		log.info("Parameters - agentId: {}, query: {}, vectorType: {}, topK: {}, threshold: {}",
				agentId, query, vectorType, topK, threshold);

		AgentSearchRequest searchRequest = AgentSearchRequest.builder()
				.agentId(agentId)
				.docVectorType(vectorType)
				.query(query)
				.topK(topK) // 使用传入的参数
				.similarityThreshold(threshold) // 使用传入的参数
				.build();
		log.debug("Built search request: {}", searchRequest);

		List<Document> result = search(searchRequest);
		log.info("=== getDocumentsForAgent completed, found {} documents ===", result.size());
		return result;
	}

	@Override
	public List<Document> getDocumentsOnlyByFilter(Filter.Expression filterExpression, Integer topK) {
		log.info("=== Starting getDocumentsOnlyByFilter operation ===");
		log.info("Parameters - filterExpression: {}, topK: {}", filterExpression, topK);

		Assert.notNull(filterExpression, "filterExpression cannot be null.");

		if (topK == null) {
			topK = dataAgentProperties.getVectorStore().getDefaultTopkLimit();
			log.debug("topK not provided, using default: {}", topK);
		}

		log.debug("Building search request with query: '{}', topK: {}, similarityThreshold: 0.0", DEFAULT);
		SearchRequest searchRequest = SearchRequest.builder()
				.query(DEFAULT)
				.topK(topK)
				.filterExpression(filterExpression)
				.similarityThreshold(0.0)
				.build();

		log.debug("Executing similarity search with filter only");
		List<Document> result = vectorStore.similaritySearch(searchRequest);
		log.info("=== getDocumentsOnlyByFilter completed, found {} documents ===", result.size());
		return result;
	}

	@Override
	public boolean hasDocuments(String agentId) {
		log.info("=== Starting hasDocuments check ===");
		log.info("Checking if documents exist for agentId: {}", agentId);

		// 类似 MySQL 的 LIMIT 1,只检查是否存在文档
		String filterExpression = buildFilterExpressionString(Map.of(Constant.AGENT_ID, agentId));
		log.debug("Built filter expression for check: {}", filterExpression);

		log.debug("Executing similarity search with topK=1 to check document existence");
		List<Document> docs = vectorStore.similaritySearch(org.springframework.ai.vectorstore.SearchRequest.builder()
				.query(DEFAULT)// 使用默认的查询字符串，因为有的嵌入模型不支持空字符串
				.filterExpression(filterExpression)
				.topK(1) // 只获取1个文档
				.similarityThreshold(0.0)
				.build());

		boolean hasDocuments = !docs.isEmpty();
		log.info("=== hasDocuments check completed for agentId: {}, result: {} ===", agentId, hasDocuments);
		return hasDocuments;
	}

}
