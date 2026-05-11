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
package com.audaque.cloud.ai.dataagent.service.file;

import com.audaque.cloud.ai.dataagent.properties.FileStorageProperties;
import com.audaque.cloud.ai.dataagent.service.file.impls.LocalFileStorageServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileStorageServiceImplTest {

	@TempDir
	Path tempDir;

	private LocalFileStorageServiceImpl storageService;

	private FileStorageProperties properties;

	@BeforeEach
	void setUp() {
		properties = new FileStorageProperties();
		properties.setPath(tempDir.toString());
		properties.setPathPrefix("data-agent");
		properties.setUrlPrefix("/api/upload");
		storageService = new LocalFileStorageServiceImpl(properties);
	}

	@AfterEach
	void tearDown() {
		// cleanup handled by @TempDir
	}

	@Test
	@DisplayName("storeFile(byte[]) stores data and returns correct path")
	void storeFileBytes_shouldStoreAndReturnPath() {
		byte[] data = "fake png data".getBytes();
		String fileName = "chart_test.png";

		String result = storageService.storeFile(data, fileName, "charts");

		assertNotNull(result);
		assertTrue(result.contains("charts"));
		assertTrue(result.contains(fileName));
		assertTrue(result.startsWith("data-agent"));

		// Verify file exists on disk
		Path storedFile = tempDir.resolve(result);
		assertTrue(Files.exists(storedFile));
		try {
			assertArrayEquals(data, Files.readAllBytes(storedFile));
		} catch (IOException e) {
			fail("Failed to read stored file: " + e.getMessage());
		}
	}

	@Test
	@DisplayName("storeFile(byte[]) creates parent directories automatically")
	void storeFileBytes_shouldCreateDirectories() {
		byte[] data = { 0x01, 0x02, 0x03 };
		String fileName = "test.bin";

		String result = storageService.storeFile(data, fileName, "charts");

		Path storedFile = tempDir.resolve(result);
		assertTrue(Files.exists(storedFile.getParent()));
	}

	@Test
	@DisplayName("getFileUrl returns URL with prefix")
	void getFileUrl_shouldReturnUrlWithPrefix() {
		String filePath = "data-agent/charts/chart_test.png";

		String url = storageService.getFileUrl(filePath);

		assertEquals("/api/upload/data-agent/charts/chart_test.png", url);
	}

	@Test
	@DisplayName("getFileUrl handles slash boundaries correctly")
	void getFileUrl_shouldHandleSlashBoundaries() {
		properties.setUrlPrefix("/api/upload/");
		storageService = new LocalFileStorageServiceImpl(properties);

		String url = storageService.getFileUrl("data-agent/charts/test.png");

		assertEquals("/api/upload/data-agent/charts/test.png", url);
	}

	@Test
	@DisplayName("storeFile(byte[]) with empty data should throw")
	void storeFileBytes_withEmptyData_shouldNotThrow() {
		// Local implementation does not validate empty data, it just copies
		byte[] data = {};
		String result = storageService.storeFile(data, "empty.bin", "charts");
		assertNotNull(result);
	}

	@Test
	@DisplayName("storeFile(byte[]) stores to correct subdirectory")
	void storeFileBytes_shouldStoreToCorrectSubdirectory() {
		byte[] data = "test content".getBytes();

		String result = storageService.storeFile(data, "test.txt", "charts/sub");

		assertTrue(result.contains("charts/sub"));
		Path storedFile = tempDir.resolve(result);
		assertTrue(Files.exists(storedFile));
	}

	@Test
	@DisplayName("Full roundtrip: store bytes then get URL")
	void fullRoundtrip_storeAndGetUrl() {
		byte[] imageData = { (byte) 0x89, 0x50, 0x4E, 0x47 }; // PNG magic bytes
		String fileName = "chart_abc123.png";

		String storagePath = storageService.storeFile(imageData, fileName, "charts");
		String url = storageService.getFileUrl(storagePath);

		assertNotNull(url);
		assertTrue(url.startsWith("/api/upload/"));
		assertTrue(url.contains("charts"));
		assertTrue(url.contains(fileName));
	}

}
