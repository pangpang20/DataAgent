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

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Qwen Embedding 模型包装类
 *
 * 修复 qwen3-embedding API 返回的 data 数组中 index 不连续的问题。
 * qwen3-embedding 返回的 index 可能是 [0, 2] 而不是 [0, 1]，
 * 导致 Spring AI 的 EmbeddingModel.embed() 方法访问越界。
 *
 * 此包装类会对返回的 embedding 列表按 index 排序并重新编号。
 */
@Slf4j
public class QwenEmbeddingModel implements EmbeddingModel {

	private final EmbeddingModel delegate;

	public QwenEmbeddingModel(EmbeddingModel delegate) {
		this.delegate = delegate;
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		int inputSize = request.getInstructions().size();
		log.info("QwenEmbeddingModel.call() called with {} texts", inputSize);

		// Qwen Embedding API 有批量处理限制，单次最多返回 8 个结果
		// 将输入拆分为多个批次处理
		int batchSize = 8;
		List<Embedding> allEmbeddings = new ArrayList<>();

		for (int i = 0; i < inputSize; i += batchSize) {
			int endIndex = Math.min(i + batchSize, inputSize);
			List<String> batch = request.getInstructions().subList(i, endIndex);
			EmbeddingRequest batchRequest = new EmbeddingRequest(batch, request.getOptions());

			log.debug("Processing batch {}-{} of {}", i + 1, endIndex, inputSize);
			EmbeddingResponse response = delegate.call(batchRequest);
			EmbeddingResponse fixedResponse = fixEmbeddingResponse(response);
			allEmbeddings.addAll(fixedResponse.getResults());
		}

		log.info("Completed processing {} texts, got {} embeddings", inputSize, allEmbeddings.size());
		return new EmbeddingResponse(allEmbeddings);
	}

	@Override
	public float[] embed(Document document) {
		log.debug("QwenEmbeddingModel.embed(Document) called");
		return delegate.embed(document);
	}

	@Override
	public float[] embed(String text) {
		log.debug("QwenEmbeddingModel.embed(String) called");
		return delegate.embed(text);
	}

	/**
	 * 重写 embed(List<String>) 方法，确保修复 index 问题
	 * 这是 MilvusVectorStore 调用的方法
	 */
	@Override
	public List<float[]> embed(List<String> texts) {
		int inputSize = texts.size();
		log.info("QwenEmbeddingModel.embed(List<String>) called with {} texts", inputSize);

		// Qwen Embedding API 有批量处理限制，单次最多返回 8 个结果
		// 将输入拆分为多个批次处理
		int batchSize = 8;
		List<Embedding> allEmbeddings = new ArrayList<>();

		for (int i = 0; i < inputSize; i += batchSize) {
			int endIndex = Math.min(i + batchSize, inputSize);
			List<String> batch = texts.subList(i, endIndex);

			log.debug("Processing batch {}-{} of {}", i + 1, endIndex, inputSize);
			EmbeddingResponse response = delegate.embedForResponse(batch);
			EmbeddingResponse fixedResponse = fixEmbeddingResponse(response);
			allEmbeddings.addAll(fixedResponse.getResults());
		}

		log.info("Completed processing {} texts, got {} embeddings", inputSize, allEmbeddings.size());

		// 按 index 顺序提取 embedding
		List<float[]> result = new ArrayList<>();
		for (Embedding embedding : allEmbeddings) {
			result.add(embedding.getOutput());
		}

		log.info("Returning {} embeddings", result.size());
		return result;
	}

	@Override
	public EmbeddingResponse embedForResponse(List<String> texts) {
		log.debug("QwenEmbeddingModel.embedForResponse() called with {} texts", texts.size());
		EmbeddingResponse response = delegate.embedForResponse(texts);
		return fixEmbeddingResponse(response);
	}

	@Override
	public int dimensions() {
		return delegate.dimensions();
	}

	/**
	 * 修复 EmbeddingResponse 中的 index 问题
	 *
	 * qwen3-embedding 返回的 data 数组中，index 可能不连续（如 [0, 2]），
	 * 此方法会按 index 排序并重新编号为连续的 [0, 1, 2, ...]
	 *
	 * 同时检测返回的 embedding 数量是否与输入数量匹配
	 */
	private EmbeddingResponse fixEmbeddingResponse(EmbeddingResponse response) {
		if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
			log.warn("Embedding response is null or empty");
			return response;
		}

		List<Embedding> embeddings = new ArrayList<>(response.getResults());

		log.info("Embedding response contains {} results", embeddings.size());
		log.info("Embedding indices: {}", embeddings.stream().map(Embedding::getIndex).toList());

		// 检查是否需要修复（index 是否连续）
		boolean needsFix = false;
		for (int i = 0; i < embeddings.size(); i++) {
			if (embeddings.get(i).getIndex() != i) {
				needsFix = true;
				break;
			}
		}

		if (!needsFix) {
			log.debug("Embedding indices are consecutive, no fix needed");
			return response;
		}

		log.info("Fixing non-consecutive embedding indices. Original indices: {}",
				embeddings.stream().map(Embedding::getIndex).toList());

		// 按 index 排序
		embeddings.sort(Comparator.comparingInt(Embedding::getIndex));

		// 重新编号 - 使用构造函数创建新的 Embedding
		// Spring AI 1.1.0 Embedding 构造函数: Embedding(float[] output, int index)
		List<Embedding> fixedEmbeddings = new ArrayList<>();
		for (int i = 0; i < embeddings.size(); i++) {
			Embedding original = embeddings.get(i);
			Embedding fixed = new Embedding(original.getOutput(), i);
			fixedEmbeddings.add(fixed);
		}

		log.info("Fixed embedding indices to: {}",
				fixedEmbeddings.stream().map(Embedding::getIndex).toList());

		return new EmbeddingResponse(fixedEmbeddings);
	}
}
