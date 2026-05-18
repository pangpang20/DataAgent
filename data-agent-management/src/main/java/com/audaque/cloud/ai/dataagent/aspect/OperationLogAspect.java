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
package com.audaque.cloud.ai.dataagent.aspect;

import com.audaque.cloud.ai.dataagent.annotation.OperationLog;
import com.audaque.cloud.ai.dataagent.entity.SysOperationLog;
import com.audaque.cloud.ai.dataagent.security.SecurityUtils;
import com.audaque.cloud.ai.dataagent.service.log.AsyncLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

	private final AsyncLogService asyncLogService;

	private final ObjectMapper objectMapper;

	@Around("@annotation(operationLog)")
	public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
		long startTime = System.currentTimeMillis();
		Object result = null;
		String errorMsg = null;
		int status = 1;

		try {
			result = joinPoint.proceed();
			return result;
		}
		catch (Throwable ex) {
			status = 0;
			errorMsg = ex.getMessage();
			throw ex;
		}
		finally {
			try {
				long duration = System.currentTimeMillis() - startTime;
				saveLog(joinPoint, operationLog, result, errorMsg, status, duration);
			}
			catch (Exception e) {
				log.error("Failed to save operation log", e);
			}
		}
	}

	private void saveLog(ProceedingJoinPoint joinPoint, OperationLog operationLog, Object result, String errorMsg,
			int status, long duration) {
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		String methodName = signature.getDeclaringTypeName() + "." + signature.getName();

		Long userId = null;
		String username = "unknown";
		try {
			userId = SecurityUtils.getCurrentUserId();
			username = SecurityUtils.getCurrentUsername();
		}
		catch (Exception ignored) {
		}

		String ip = getClientIp();
		String params = "";
		try {
			params = objectMapper.writeValueAsString(joinPoint.getArgs());
		}
		catch (Exception ignored) {
		}

		String resultStr = "";
		try {
			if (result != null) {
				resultStr = objectMapper.writeValueAsString(result);
				if (resultStr.length() > 2000) {
					resultStr = resultStr.substring(0, 2000);
				}
			}
		}
		catch (Exception ignored) {
		}

		SysOperationLog logEntity = SysOperationLog.builder()
			.userId(userId)
			.username(username)
			.module(operationLog.module())
			.operation(operationLog.operation())
			.method(methodName)
			.requestParams(params.length() > 2000 ? params.substring(0, 2000) : params)
			.responseResult(resultStr)
			.ip(ip)
			.status(status)
			.errorMsg(errorMsg)
			.costTime(duration)
			.createTime(LocalDateTime.now())
			.build();

		asyncLogService.saveOperationLog(logEntity);
	}

	private String getClientIp() {
		try {
			ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
				.getRequestAttributes();
			if (attributes != null) {
				HttpServletRequest request = attributes.getRequest();
				String ip = request.getHeader("X-Forwarded-For");
				if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
					ip = request.getHeader("Proxy-Client-IP");
				}
				if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
					ip = request.getHeader("WL-Proxy-Client-IP");
				}
				if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
					ip = request.getRemoteAddr();
				}
				if (ip != null && ip.contains(",")) {
					ip = ip.split(",")[0].trim();
				}
				return ip;
			}
		}
		catch (Exception ignored) {
		}
		return "unknown";
	}

}
