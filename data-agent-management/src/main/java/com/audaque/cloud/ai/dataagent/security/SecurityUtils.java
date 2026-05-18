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
package com.audaque.cloud.ai.dataagent.security;

import com.audaque.cloud.ai.dataagent.entity.DataPermission;
import com.audaque.cloud.ai.dataagent.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

public final class SecurityUtils {

	private SecurityUtils() {
	}

	public static Long getCurrentUserId() {
		DataAgentUserDetails userDetails = getCurrentUserDetails();
		if (userDetails == null) {
			throw new BizException(401003, "未认证");
		}
		return userDetails.getUserId();
	}

	public static String getCurrentUsername() {
		DataAgentUserDetails userDetails = getCurrentUserDetails();
		if (userDetails == null) {
			throw new BizException(401003, "未认证");
		}
		return userDetails.getUsername();
	}

	public static List<String> getCurrentRoles() {
		DataAgentUserDetails userDetails = getCurrentUserDetails();
		if (userDetails == null) {
			return Collections.emptyList();
		}
		return userDetails.getRoles();
	}

	public static List<String> getCurrentPermissions() {
		DataAgentUserDetails userDetails = getCurrentUserDetails();
		if (userDetails == null) {
			return Collections.emptyList();
		}
		return userDetails.getPermissions();
	}

	public static boolean hasPermission(String permission) {
		List<String> permissions = getCurrentPermissions();
		return permissions.contains(permission);
	}

	public static boolean hasRole(String role) {
		List<String> roles = getCurrentRoles();
		return roles.contains(role);
	}

	public static DataAgentUserDetails getCurrentUserDetails() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return null;
		}
		Object principal = authentication.getPrincipal();
		if (principal instanceof DataAgentUserDetails userDetails) {
			return userDetails;
		}
		return null;
	}

	public static DataPermission getDataPermission() {
		DataAgentUserDetails userDetails = getCurrentUserDetails();
		if (userDetails == null) {
			return null;
		}

		List<String> roles = userDetails.getRoles();
		Long userId = userDetails.getUserId();

		if (roles.contains("SUPER_ADMIN")) {
			return DataPermission.builder().userId(userId).role("SUPER_ADMIN").admin(true).build();
		}

		if (roles.contains("TENANT_ADMIN") || roles.contains("OPS_ADMIN")) {
			return DataPermission.builder().userId(userId).role(roles.get(0)).departmentOnly(true).build();
		}

		if (roles.contains("AUDITOR")) {
			return DataPermission.builder().userId(userId).role("AUDITOR").admin(true).build();
		}

		return DataPermission.builder()
			.userId(userId)
			.role(roles.isEmpty() ? "UNKNOWN" : roles.get(0))
			.creatorOnly(true)
			.build();
	}

}
