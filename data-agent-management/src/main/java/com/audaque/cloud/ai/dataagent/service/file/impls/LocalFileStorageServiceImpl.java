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
import com.audaque.cloud.ai.dataagent.service.file.FileStorageService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@AllArgsConstructor
public class LocalFileStorageServiceImpl implements FileStorageService {

	private final FileStorageProperties fileStorageProperties;

	@Override
	public String storeFile(MultipartFile file, String subPath) {
		try {
			String originalFilename = file.getOriginalFilename();
			String extension = "";
			if (originalFilename != null && originalFilename.contains(".")) {
				extension = originalFilename.substring(originalFilename.lastIndexOf("."));
			}
			String filename = UUID.randomUUID().toString() + extension;

			String storagePath = buildStoragePath(subPath, filename);

			Path uploadDir = Paths.get(fileStorageProperties.getPath(), storagePath).getParent();
			if (uploadDir != null && !Files.exists(uploadDir)) {
				Files.createDirectories(uploadDir);
			}

			Path filePath = Paths.get(fileStorageProperties.getPath(), storagePath);
			Files.copy(file.getInputStream(), filePath);

			log.info("文件存储成功: {}", storagePath);
			return storagePath;

		} catch (IOException e) {
			log.error("文件存储失败", e);
			throw new RuntimeException("文件存储失败: " + e.getMessage(), e);
		}
	}

	@Override
	public boolean deleteFile(String filePath) {
		try {
			String internalPath = resolveInternalPath(filePath);
			Path fullPath = Paths.get(fileStorageProperties.getPath(), internalPath);
			if (Files.exists(fullPath)) {
				Files.deleteIfExists(fullPath);
				log.info("成功删除文件: {}", internalPath);
			} else {
				// 删除是个等幂的操作，不存在也是当做被删除了
				log.info("文件不存在，跳过删除，视为成功: {}", internalPath);
			}
			return true;
		} catch (IOException e) {
			log.error("删除文件失败: {}", filePath, e);
			return false;
		}
	}

	@Override
	public String getFileUrl(String filePath) {
		String prefix = fileStorageProperties.getUrlPrefix();
		if (!prefix.endsWith("/") && !filePath.startsWith("/")) {
			return prefix + "/" + filePath;
		}
		return prefix + filePath;
	}

	@Override
	public Resource getFileResource(String filePath) {
		String internalPath = resolveInternalPath(filePath);
		Path fullPath = Paths.get(fileStorageProperties.getPath(), internalPath);
		if (Files.exists(fullPath)) {
			return new FileSystemResource(fullPath);
		} else {
			throw new RuntimeException("File is not exist: " + internalPath);
		}
	}

	/**
	 * 从URL或完整路径中解析出内部存储路径
	 */
	private String resolveInternalPath(String filePathOrUrl) {
		if (filePathOrUrl == null) {
			return "";
		}

		String path = filePathOrUrl;

		// 如果是绝对URL，去掉协议和主机部分
		if (path.startsWith("http://") || path.startsWith("https://")) {
			try {
				java.net.URL url = new java.net.URL(path);
				path = url.getPath();
			} catch (java.net.MalformedURLException e) {
				log.warn("解析URL失败: {}", path);
			}
		}

		// 去掉 urlPrefix
		String urlPrefix = fileStorageProperties.getUrlPrefix();
		if (path.startsWith(urlPrefix)) {
			path = path.substring(urlPrefix.length());
		}

		// 去掉开头的斜杠
		while (path.startsWith("/")) {
			path = path.substring(1);
		}

		return path;
	}

	/**
	 * 构建本地存储路径
	 */
	private String buildStoragePath(String subPath, String filename) {
		StringBuilder pathBuilder = new StringBuilder();

		if (StringUtils.hasText(fileStorageProperties.getPathPrefix())) {
			pathBuilder.append(fileStorageProperties.getPathPrefix()).append("/");
		}

		if (StringUtils.hasText(subPath)) {
			pathBuilder.append(subPath).append("/");
		}

		pathBuilder.append(filename);

		return pathBuilder.toString();
	}

}
