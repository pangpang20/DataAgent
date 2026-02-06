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

import com.audaque.cloud.ai.dataagent.properties.OssStorageProperties;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.model.OSSObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.AbstractResource;
import org.springframework.util.StringUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

/**
 * 阿里云OSS资源实现
 */
@Slf4j
public class OssResource extends AbstractResource {

    private final OSS ossClient;
    private final String bucketName;
    private final String objectKey;
    private final String description;
    private final OssStorageProperties ossProperties;

    public OssResource(OSS ossClient, String bucketName, String objectKey, OssStorageProperties ossProperties) {
        this.ossClient = ossClient;
        this.bucketName = bucketName;
        this.objectKey = objectKey;
        this.ossProperties = ossProperties;
        this.description = "OSS resource [bucket='" + bucketName + "', key='" + objectKey + "']";
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        try {
            log.debug("Getting input stream for OSS object: bucket={}, key={}", bucketName, objectKey);
            OSSObject ossObject = ossClient.getObject(bucketName, objectKey);
            return ossObject.getObjectContent();
        } catch (OSSException e) {
            log.error("Failed to get OSS object due to OSS error: bucket={}, key={}, errorCode={}, errorMessage={}", 
                     bucketName, objectKey, e.getErrorCode(), e.getErrorMessage(), e);
            throw new IOException("Failed to get OSS object: " + e.getErrorMessage(), e);
        } catch (ClientException e) {
            log.error("Failed to get OSS object due to client error: bucket={}, key={}, errorMessage={}", 
                     bucketName, objectKey, e.getMessage(), e);
            throw new IOException("Failed to get OSS object: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to get OSS object: bucket={}, key={}", bucketName, objectKey, e);
            throw new IOException("Failed to get OSS object: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists() {
        try {
            boolean exists = ossClient.doesObjectExist(bucketName, objectKey);
            log.debug("OSS object existence check: bucket={}, key={}, exists={}", bucketName, objectKey, exists);
            return exists;
        } catch (Exception e) {
            log.warn("Failed to check OSS object existence: bucket={}, key={}, error={}", 
                    bucketName, objectKey, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isReadable() {
        return exists();
    }

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public URL getURL() throws IOException {
        try {
            // 如果配置了自定义域名，返回自定义域名URL
            String customDomain = ossProperties.getCustomDomain();
            if (StringUtils.hasText(customDomain)) {
                String url = customDomain + "/" + objectKey;
                log.debug("Using custom domain URL: {}", url);
                return new URL(url);
            }
            
            // 否则构造标准OSS URL
            String endpoint = ossProperties.getEndpoint();
            String cleanEndpoint = endpoint.replace("https://", "").replace("http://", "");
            String url = String.format("https://%s.%s/%s", bucketName, cleanEndpoint, objectKey);
            log.debug("Generated OSS URL: {}", url);
            return new URL(url);
        } catch (MalformedURLException e) {
            log.error("Failed to generate URL for OSS object: bucket={}, key={}", bucketName, objectKey, e);
            throw new IOException("Failed to generate URL for OSS object", e);
        }
    }

    @Override
    public URI getURI() throws IOException {
        try {
            return getURL().toURI();
        } catch (Exception e) {
            throw new IOException("Failed to convert URL to URI for OSS object", e);
        }
    }

    @Override
    public java.io.File getFile() throws IOException {
        throw new FileNotFoundException("OSS resource cannot be resolved to absolute file path");
    }

    @Override
    public long contentLength() throws IOException {
        try {
            OSSObject ossObject = ossClient.getObject(bucketName, objectKey);
            long length = ossObject.getObjectMetadata().getContentLength();
            ossObject.getObjectContent().close();
            log.debug("Got content length for OSS object: bucket={}, key={}, length={}", bucketName, objectKey, length);
            return length;
        } catch (OSSException e) {
            log.error("Failed to get OSS object content length due to OSS error: bucket={}, key={}, errorCode={}, errorMessage={}", 
                     bucketName, objectKey, e.getErrorCode(), e.getErrorMessage(), e);
            throw new IOException("Failed to get OSS object content length: " + e.getErrorMessage(), e);
        } catch (ClientException e) {
            log.error("Failed to get OSS object content length due to client error: bucket={}, key={}, errorMessage={}", 
                     bucketName, objectKey, e.getMessage(), e);
            throw new IOException("Failed to get OSS object content length: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to get OSS object content length: bucket={}, key={}", bucketName, objectKey, e);
            throw new IOException("Failed to get OSS object content length: " + e.getMessage(), e);
        }
    }

    /**
     * 获取对象键
     * @return 对象键
     */
    public String getObjectKey() {
        return objectKey;
    }

    /**
     * 获取存储桶名称
     * @return 存储桶名称
     */
    public String getBucketName() {
        return bucketName;
    }
}

