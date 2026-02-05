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
package com.audaque.cloud.ai.dataagent.service.knowledge;

import com.audaque.cloud.ai.dataagent.constant.Constant;
import com.audaque.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.audaque.cloud.ai.dataagent.enums.KnowledgeType;
import com.audaque.cloud.ai.dataagent.util.DocumentConverterUtil;
import com.audaque.cloud.ai.dataagent.entity.AgentKnowledge;
import com.audaque.cloud.ai.dataagent.service.file.FileStorageService;
import com.audaque.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 智能体知识的向量资源和文件资源管理
@Slf4j
@Component
public class AgentKnowledgeResourceManager {

	private final TextSplitter textSplitter;

	private final FileStorageService fileStorageService;

	private final AgentVectorStoreService agentVectorStoreService;

	public AgentKnowledgeResourceManager(TextSplitter textSplitter, FileStorageService fileStorageService,
			AgentVectorStoreService agentVectorStoreService) {
		this.textSplitter = textSplitter;
		this.fileStorageService = fileStorageService;
		this.agentVectorStoreService = agentVectorStoreService;
	}

	public void doEmbedingToVectorStore(AgentKnowledge agentKnowledge) throws Exception {
		// delete old data
		this.deleteFromVectorStore(agentKnowledge.getAgentId(), agentKnowledge.getId());

		if (KnowledgeType.QA.equals(agentKnowledge.getType()) || KnowledgeType.FAQ.equals(agentKnowledge.getType())) {
			processQaKnowledge(agentKnowledge);
		} else if (KnowledgeType.DOCUMENT.equals(agentKnowledge.getType())) {
			processDocumentKnowledge(agentKnowledge);
		} else {
			throw new RuntimeException("Unsupported KnowledgeType: " + agentKnowledge.getType());
		}
	}

	private void processQaKnowledge(AgentKnowledge knowledge) {
		Document document = DocumentConverterUtil.convertQaFaqKnowledgeToDocument(knowledge);
		agentVectorStoreService.addDocuments(knowledge.getAgentId().toString(), List.of(document));
		log.info("Successfully vectorized AgentKnowledge: id={}, type={}", knowledge.getId(), knowledge.getType());
	}

	private void processDocumentKnowledge(AgentKnowledge knowledge) {

		// 处理文档
		List<Document> documents = getAndSplitDocument(knowledge.getFilePath());
		if (documents == null || documents.isEmpty()) {
			log.error("No documents extracted from file: knowledgeId={}, filePath={}", knowledge.getId(),
					knowledge.getFilePath());
			throw new RuntimeException("No documents extracted from file");
		}

		// 使用工具类为文档添加元数据
		List<Document> documentsWithMetadata = DocumentConverterUtil
				.convertAgentKnowledgeDocumentsWithMetadata(documents, knowledge);

		// 添加到向量存储
		agentVectorStoreService.addDocuments(knowledge.getAgentId().toString(), documentsWithMetadata);
		log.info("Successfully vectorized DOCUMENT knowledge: id={}, filePath={}, documentCount={}", knowledge.getId(),
				knowledge.getFilePath(), documentsWithMetadata.size());

	}

	private List<Document> getAndSplitDocument(String filePath) {
		// 使用FileStorageService获取文件资源对象
		Resource resource = fileStorageService.getFileResource(filePath);

		// 使用TikaDocumentReader读取文件
		TikaDocumentReader tikaDocumentReader = new TikaDocumentReader(resource);
		List<Document> documents;
		try {
			documents = tikaDocumentReader.read();
		} catch (StackOverflowError e) {
			log.error(
					"TikaDocumentReader read failed due to StackOverflowError, possibly caused by problematic regex in Tika when processing file: {}",
					filePath, e);
			// 尝试使用简化的读取方式作为后备方案
			log.warn("Attempting fallback document reading method for file: {}", filePath);
			try {
				documents = readDocumentWithFallback(resource, filePath);
			} catch (Exception fallbackException) {
				log.error("Fallback document reading also failed for file: {}", filePath, fallbackException);
				throw new RuntimeException(
						"File processing failed due to stack overflow and fallback also failed: " + filePath,
						fallbackException);
			}
		} catch (Exception e) {
			log.error("TikaDocumentReader read failed for file: {}", filePath, e);
			throw new RuntimeException("File processing failed: " + filePath, e);
		}

		try {
			return textSplitter.apply(documents);
		} catch (StackOverflowError e) {
			log.error("TextSplitter apply failed due to StackOverflowError for file: {}", filePath, e);
			throw new RuntimeException(
					"Text splitting failed due to stack overflow, possibly caused by complex document content: "
							+ filePath,
					e);
		} catch (Exception e) {
			log.error("TextSplitter apply failed for file: {}", filePath, e);
			throw new RuntimeException("Text splitting failed: " + filePath, e);
		}
	}

	/**
	 * 后备文档读取方法，用于处理Tika失败的情况
	 * 使用简化的文本提取方式，避免复杂的正则表达式匹配
	 */
	private List<Document> readDocumentWithFallback(Resource resource, String filePath) throws Exception {
		log.info("Using fallback method to read document: {}", filePath);

		// 检查文件扩展名
		String fileName = resource.getFilename();
		if (fileName == null) {
			throw new IllegalArgumentException("Filename is null for resource");
		}

		String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

		// 对于docx文件，使用Apache POI直接读取
		if ("docx".equals(extension)) {
			return readDocxWithPOI(resource, filePath);
		}

		// 对于其他文件，尝试简单文本提取
		return extractSimpleText(resource, filePath);
	}

	/**
	 * 使用Apache POI直接读取DOCX文件，避免Tika的正则表达式问题
	 */
	private List<Document> readDocxWithPOI(Resource resource, String filePath) throws Exception {
		log.info("Reading DOCX file using Apache POI: {}", filePath);

		try (InputStream inputStream = resource.getInputStream()) {
			// 创建XWPFDocument对象
			org.apache.poi.xwpf.usermodel.XWPFDocument document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
					inputStream);

			// 提取所有段落文本
			StringBuilder content = new StringBuilder();
			for (org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph : document.getParagraphs()) {
				String text = paragraph.getText();
				if (StringUtils.hasText(text)) {
					content.append(text).append("\n");
				}
			}

			// 提取表格内容
			for (org.apache.poi.xwpf.usermodel.XWPFTable table : document.getTables()) {
				for (org.apache.poi.xwpf.usermodel.XWPFTableRow row : table.getRows()) {
					StringBuilder rowText = new StringBuilder();
					for (org.apache.poi.xwpf.usermodel.XWPFTableCell cell : row.getTableCells()) {
						String cellText = cell.getText();
						if (StringUtils.hasText(cellText)) {
							rowText.append(cellText).append(" | ");
						}
					}
					if (rowText.length() > 0) {
						content.append(rowText.toString()).append("\n");
					}
				}
			}

			document.close();

			String textContent = content.toString().trim();
			if (!StringUtils.hasText(textContent)) {
				log.warn("No content extracted from DOCX file: {}", filePath);
				return List.of();
			}

			// 创建Document对象
			Document documentObj = new Document(textContent);
			log.info("Successfully extracted {} characters from DOCX file: {}", textContent.length(), filePath);

			return List.of(documentObj);
		}
	}

	/**
	 * 简单文本提取方法，用于其他文件类型
	 */
	private List<Document> extractSimpleText(Resource resource, String filePath) throws Exception {
		log.info("Extracting simple text from file: {}", filePath);

		try (InputStream inputStream = resource.getInputStream()) {
			String content = new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			content = content.trim();

			if (!StringUtils.hasText(content)) {
				log.warn("No content extracted from file: {}", filePath);
				return List.of();
			}

			Document document = new Document(content);
			log.info("Successfully extracted {} characters from file: {}", content.length(), filePath);

			return List.of(document);
		}
	}

	/**
	 * 从向量存储中删除知识
	 * 
	 * @param agentId     代理ID
	 * @param knowledgeId 知识ID
	 * @return 是否删除成功（如果资源不存在也视为成功，实现等幂操作）
	 */
	public boolean deleteFromVectorStore(Integer agentId, Integer knowledgeId) {
		try {

			Map<String, Object> metadata = new HashMap<>();
			metadata.put(Constant.AGENT_ID, agentId.toString());
			metadata.put(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID, knowledgeId);

			agentVectorStoreService.deleteDocumentsByMetedata(agentId.toString(), metadata);
			log.info("Successfully deleted knowledge from vector store, knowledgeId: {}", knowledgeId);
			return true;

		} catch (Exception e) {
			// 检查是否是资源不存在的错误，如果是则视为删除成功（等幂操作）
			if (e.getMessage() != null && (e.getMessage().contains("not found")
					|| e.getMessage().contains("does not exist") || e.getMessage().contains("already deleted"))) {
				log.info("Vector data already deleted or not found for knowledgeId: {}, treating as success",
						knowledgeId);
				return true;
			} else {
				log.error("Failed to delete knowledge from vector store, knowledgeId: {}", knowledgeId, e);
				return false;
			}
		}
	}

	/**
	 * 删除知识文件
	 * 
	 * @param knowledge 知识对象
	 * @return 是否删除成功（如果不是文档类型或文件不存在也视为成功）
	 */
	public boolean deleteKnowledgeFile(AgentKnowledge knowledge) {
		// 只有文档类型且有文件路径的知识才需要删除文件
		if (!KnowledgeType.DOCUMENT.equals(knowledge.getType()) || !StringUtils.hasText(knowledge.getFilePath())) {
			log.info("Not a document type or no file path, knowledgeId: {}, treating as success", knowledge.getId());
			return true;
		}

		try {
			boolean fileDeleted = fileStorageService.deleteFile(knowledge.getFilePath());
			if (fileDeleted) {
				log.info("Successfully deleted knowledge file, filePath: {}", knowledge.getFilePath());
				return true;
			} else {
				log.error("Failed to delete knowledge file, filePath: {}", knowledge.getFilePath());
				return false;
			}

		} catch (Exception e) {
			// 检查是否是文件不存在的错误，如果是则视为删除成功（等幂操作）
			if (e.getMessage() != null
					&& (e.getMessage().contains("not found") || e.getMessage().contains("does not exist")
							|| e.getMessage().contains("already deleted") || e.getMessage().contains("No such file"))) {
				log.info("File already deleted or not found, filePath: {}, treating as success",
						knowledge.getFilePath());
				return true;
			} else {
				log.error("Exception when deleting knowledge file, filePath: {}", knowledge.getFilePath(), e);
				return false;
			}
		}
	}

}
