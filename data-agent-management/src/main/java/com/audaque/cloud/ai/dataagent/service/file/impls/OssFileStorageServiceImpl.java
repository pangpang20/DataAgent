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
package com.audaque.cloud.ai.dataagent.service.file.impls;

import com.audaque.cloud.ai.dataagent.properties.FileStorageProperties;
import com.audaque.cloud.ai.dataagent.properties.OssStorageProperties;
import com.audaque.cloud.ai.dataagent.service.file.FileStorageService;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * 阿里云OSS文件存储服务实现
 */
@Slf4j
public class OssFileStorageServiceImpl implements FileStorageService {

	private final FileStorageProperties fileStorageProperties;

	private final OssStorageProperties ossProperties;

	private OSS ossClient;

	public OssFileStorageServiceImpl(FileStorageProperties fileStorageProperties, OssStorageProperties ossProperties) {
		this.fileStorageProperties = fileStorageProperties;
		this.ossProperties = ossProperties;
	}

	@PostConstruct
	public void init() {
		this.ossClient = new OSSClientBuilder().build(ossProperties.getEndpoint(), ossProperties.getAccessKeyId(),
				ossProperties.getAccessKeySecret());
		log.info("OSS client initialized, endpoint: {}, bucket: {}", ossProperties.getEndpoint(),
				ossProperties.getBucketName());
	}

	@PreDestroy
	public void destroy() {
		if (ossClient != null) {
			ossClient.shutdown();
			log.info("OSS client shutdown complete");
		}
	}

	@Override
	public String storeFile(MultipartFile file, String subPath) {
		try {
			if (file == null || file.isEmpty()) {
				log.warn("File is empty, cannot upload to OSS");
				return null;
			}

			String originalFilename = file.getOriginalFilename();
			String extension = "";
			if (originalFilename != null && originalFilename.contains(".")) {
				extension = originalFilename.substring(originalFilename.lastIndexOf("."));
			}
			String filename = UUID.randomUUID().toString() + extension;

			String objectKey = buildObjectKey(subPath, filename);

			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentLength(file.getSize());
			metadata.setContentType(file.getContentType());
			metadata.setCacheControl("no-cache");

			try (InputStream inputStream = file.getInputStream()) {
				ossClient.putObject(ossProperties.getBucketName(), objectKey, inputStream, metadata);
				log.info("File uploaded successfully: {}", objectKey);
				return objectKey;
			} catch (IOException e) {
				log.error("File storage failed, input stream error", e);
				throw new RuntimeException("文件存储失败: " + e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error("File storage failed, OSS upload error", e);
			throw new RuntimeException("文件存储失败: " + e.getMessage(), e);
		}
	}

	@Override
	public boolean deleteFile(String filePath) {
		if (!StringUtils.hasText(filePath)) {
			log.info("Delete file failed, path is empty");
			return false;
		}
		try {
			if (ossClient.doesObjectExist(ossProperties.getBucketName(), filePath)) {
				ossClient.deleteObject(ossProperties.getBucketName(), filePath);
				log.info("File deleted from OSS successfully: {}", filePath);
			} else {
				// Deletion is idempotent, treat non-existence as success
				log.info("File not found in OSS, skipping deletion (idempotent): {}", filePath);
			}
			return true;
		} catch (Exception e) {
			log.error("Failed to delete file from OSS: {}", filePath, e);
			return false;
		}
	}

	@Override
	public String getFileUrl(String filePath) {
		try {
			if (StringUtils.hasText(ossProperties.getCustomDomain())) {
				return ossProperties.getCustomDomain() + "/" + filePath;
			}

			String bucketDomain = String.format("https://%s.%s", ossProperties.getBucketName(),
					ossProperties.getEndpoint().replace("https://", "").replace("http://", ""));
			return bucketDomain + "/" + filePath;

		} catch (Exception e) {
			log.error("Failed to generate OSS file URL: {}", filePath, e);
			return filePath;
		}
	}

	@Override
	public Resource getFileResource(String filePath) {
		// TODO 实现
		log.error("Getting resource from OSS not implemented");
		return null;
	}

	/**
	 * 构建OSS对象键
	 */
	private String buildObjectKey(String subPath, String filename) {
		StringBuilder keyBuilder = new StringBuilder();

		if (StringUtils.hasText(fileStorageProperties.getPathPrefix())) {
			keyBuilder.append(fileStorageProperties.getPathPrefix()).append("/");
		}

		if (StringUtils.hasText(subPath)) {
			keyBuilder.append(subPath).append("/");
		}

		keyBuilder.append(filename);

		return keyBuilder.toString();
	}

}
