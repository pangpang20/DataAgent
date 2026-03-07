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

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DescribeCollectionResponse;
import io.milvus.grpc.FieldSchema;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.DescribeCollectionParam;
import io.milvus.param.collection.DropCollectionParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 向量维度检测服务
 * 用于检测 Milvus collection 的向量维度，以及与新 Embedding 模型的维度兼容性
 */
@Slf4j
@Service
public class VectorDimensionService {

	private final Optional<MilvusServiceClient> milvusClient;

	@Value("${spring.ai.vectorstore.milvus.collection-name:data_agent_vector}")
	private String collectionName;

	@Value("${spring.ai.vectorstore.milvus.embedding-dimension:1024}")
	private int configuredDimension;

	public VectorDimensionService(Optional<MilvusServiceClient> milvusClient) {
		this.milvusClient = milvusClient;
	}

	/**
	 * 获取 Milvus collection 的实际向量维度
	 * @return 向量维度，如果 collection 不存在或无法获取则返回配置的维度
	 */
	public int getCollectionDimension() {
		if (milvusClient.isEmpty()) {
			log.debug("MilvusClient not available, returning configured dimension: {}", configuredDimension);
			return configuredDimension;
		}

		try {
			DescribeCollectionParam param = DescribeCollectionParam.newBuilder()
					.withCollectionName(collectionName)
					.build();

			R<DescribeCollectionResponse> response = milvusClient.get().describeCollection(param);
			if (response.getStatus() != R.Status.Success.getCode()) {
				log.warn("Failed to describe collection: {}, returning configured dimension: {}",
						response.getMessage(), configuredDimension);
				return configuredDimension;
			}

			// 查找 embedding 字段的维度
			for (FieldSchema field : response.getData().getSchema().getFieldsList()) {
				if ("embedding".equals(field.getName())) {
					int dimension = field.getTypeParamsList().stream()
							.filter(p -> "dim".equals(p.getKey()))
							.map(p -> Integer.parseInt(p.getValue()))
							.findFirst()
							.orElse(configuredDimension);
					log.info("Collection '{}' has embedding dimension: {}", collectionName, dimension);
					return dimension;
				}
			}

			log.warn("No embedding field found in collection '{}', returning configured dimension: {}",
					collectionName, configuredDimension);
			return configuredDimension;
		} catch (Exception e) {
			log.error("Error getting collection dimension: {}", e.getMessage(), e);
			return configuredDimension;
		}
	}

	/**
	 * 检查新模型的维度是否与当前 collection 兼容
	 * @param newModelDimension 新 Embedding 模型的维度
	 * @return 维度兼容性检查结果
	 */
	public DimensionCheckResult checkDimensionCompatibility(int newModelDimension) {
		int collectionDimension = getCollectionDimension();

		if (newModelDimension == collectionDimension) {
			return new DimensionCheckResult(true, collectionDimension, newModelDimension,
					"维度匹配，可以正常使用");
		} else {
			String message = String.format(
					"维度不匹配！Collection 维度: %d, 新模型维度: %d。请选择以下解决方案之一：\n" +
							"1. 删除现有 collection 并重新创建（会丢失所有向量数据）\n" +
							"2. 修改配置使用新的 collection 名称（保留旧数据）\n" +
							"3. 使用维度匹配的 Embedding 模型",
					collectionDimension, newModelDimension);
			return new DimensionCheckResult(false, collectionDimension, newModelDimension, message);
		}
	}

	/**
	 * 删除当前的 Milvus collection
	 * 用于在切换不同维度的 Embedding 模型时重建 collection
	 * @return 是否删除成功
	 */
	public boolean dropCurrentCollection() {
		if (milvusClient.isEmpty()) {
			log.warn("MilvusClient not available, cannot drop collection");
			return false;
		}

		try {
			DropCollectionParam param = DropCollectionParam.newBuilder()
					.withCollectionName(collectionName)
					.build();

			R<RpcStatus> response = milvusClient.get().dropCollection(param);
			if (response.getStatus() == R.Status.Success.getCode()) {
				log.info("Successfully dropped collection: {}", collectionName);
				return true;
			} else {
				log.error("Failed to drop collection '{}': {}", collectionName, response.getMessage());
				return false;
			}
		} catch (Exception e) {
			log.error("Error dropping collection '{}': {}", collectionName, e.getMessage(), e);
			return false;
		}
	}

	/**
	 * 维度检查结果
	 */
	public static class DimensionCheckResult {
		private final boolean compatible;
		private final int collectionDimension;
		private final int modelDimension;
		private final String message;

		public DimensionCheckResult(boolean compatible, int collectionDimension, int modelDimension, String message) {
			this.compatible = compatible;
			this.collectionDimension = collectionDimension;
			this.modelDimension = modelDimension;
			this.message = message;
		}

		public boolean isCompatible() {
			return compatible;
		}

		public int getCollectionDimension() {
			return collectionDimension;
		}

		public int getModelDimension() {
			return modelDimension;
		}

		public String getMessage() {
			return message;
		}
	}
}
