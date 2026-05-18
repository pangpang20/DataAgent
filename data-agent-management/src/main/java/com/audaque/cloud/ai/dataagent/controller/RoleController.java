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
package com.audaque.cloud.ai.dataagent.controller;

import com.audaque.cloud.ai.dataagent.dto.role.RoleCreateRequest;
import com.audaque.cloud.ai.dataagent.dto.role.RoleDetailResponse;
import com.audaque.cloud.ai.dataagent.dto.role.RoleUpdateRequest;
import com.audaque.cloud.ai.dataagent.service.role.RoleService;
import com.audaque.cloud.ai.dataagent.vo.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/role")
@RequiredArgsConstructor
public class RoleController {

	private final RoleService roleService;

	@PostMapping
	@PreAuthorize("hasAuthority('system:role:create')")
	public ApiResponse<RoleDetailResponse> createRole(@Valid @RequestBody RoleCreateRequest request) {
		RoleDetailResponse role = roleService.createRole(request);
		return ApiResponse.success("创建角色成功", role);
	}

	@PutMapping
	@PreAuthorize("hasAuthority('system:role:edit')")
	public ApiResponse<RoleDetailResponse> updateRole(@Valid @RequestBody RoleUpdateRequest request) {
		RoleDetailResponse role = roleService.updateRole(request);
		return ApiResponse.success("更新角色成功", role);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('system:role:delete')")
	public ApiResponse<Void> deleteRole(@PathVariable Long id) {
		roleService.deleteRole(id);
		return ApiResponse.success("删除角色成功");
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('system:role:list')")
	public ApiResponse<RoleDetailResponse> getRoleById(@PathVariable Long id) {
		RoleDetailResponse role = roleService.getRoleById(id);
		return ApiResponse.success("获取角色成功", role);
	}

	@GetMapping("/list")
	@PreAuthorize("hasAuthority('system:role:list')")
	public ApiResponse<List<RoleDetailResponse>> listRoles() {
		List<RoleDetailResponse> roles = roleService.listRoles();
		return ApiResponse.success("获取角色列表成功", roles);
	}

}
