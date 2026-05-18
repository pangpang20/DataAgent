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
package com.audaque.cloud.ai.dataagent.aop;

import com.audaque.cloud.ai.dataagent.exception.BizException;
import com.audaque.cloud.ai.dataagent.vo.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class ExceptionAdvice {

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException e) {
		log.debug("Authentication failed: {}", e.getMessage());
		return ResponseEntity.status(401).body(ApiResponse.error(401003, "未认证，请先登录"));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException e) {
		log.debug("Access denied: {}", e.getMessage());
		return ResponseEntity.status(403).body(ApiResponse.error(403001, "无权限访问该资源"));
	}

	@ExceptionHandler(BizException.class)
	public ResponseEntity<ApiResponse<Void>> handleBizException(BizException e) {
		log.warn("Business exception: code={}, message={}", e.getCode(), e.getMessage());
		return ResponseEntity.badRequest().body(ApiResponse.error(e.getCode(), e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
		String message = e.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(err -> err.getField() + ": " + err.getDefaultMessage())
			.collect(Collectors.joining("; "));
		log.debug("Validation failed: {}", message);
		return ResponseEntity.badRequest().body(ApiResponse.error(400001, message));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
		// 忽略客户端断开连接的异常(用户刷新页面、关闭标签页等)
		if (isClientDisconnectException(e)) {
			log.debug("Client disconnected during streaming: {}", e.getMessage());
			return null; // 不返回响应,因为客户端已断开
		}

		log.error("An error occurred: ", e);
		return ResponseEntity.internalServerError().body(ApiResponse.error("An error occurred: " + e.getMessage()));
	}

	/**
	 * 判断是否为客户端断开连接导致的异常
	 */
	private boolean isClientDisconnectException(Exception e) {
		// 检查异常本身
		if (e instanceof ClientAbortException || e instanceof AsyncRequestNotUsableException) {
			return true;
		}

		// 检查异常消息
		String message = e.getMessage();
		if (message != null && (message.contains("Broken pipe") || message.contains("ClientAbortException")
				|| message.contains("An I/O error occurred"))) {
			return true;
		}

		// 检查cause链
		Throwable cause = e.getCause();
		while (cause != null) {
			if (cause instanceof ClientAbortException) {
				return true;
			}
			if (cause.getMessage() != null && cause.getMessage().contains("Broken pipe")) {
				return true;
			}
			cause = cause.getCause();
		}

		return false;
	}

}
