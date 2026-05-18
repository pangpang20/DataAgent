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

import com.audaque.cloud.ai.dataagent.dto.user.UserCreateRequest;
import com.audaque.cloud.ai.dataagent.dto.user.UserDetailResponse;
import com.audaque.cloud.ai.dataagent.dto.user.UserQueryDTO;
import com.audaque.cloud.ai.dataagent.dto.user.UserUpdateRequest;
import com.audaque.cloud.ai.dataagent.service.user.UserService;
import com.audaque.cloud.ai.dataagent.vo.ApiResponse;
import com.audaque.cloud.ai.dataagent.vo.PageResponse;
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
@RequestMapping("/api/system/user")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@PostMapping
	@PreAuthorize("hasAuthority('system:user:create')")
	public ApiResponse<UserDetailResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
		UserDetailResponse user = userService.createUser(request);
		return ApiResponse.success("创建用户成功", user);
	}

	@PutMapping
	@PreAuthorize("hasAuthority('system:user:edit')")
	public ApiResponse<UserDetailResponse> updateUser(@Valid @RequestBody UserUpdateRequest request) {
		UserDetailResponse user = userService.updateUser(request);
		return ApiResponse.success("更新用户成功", user);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('system:user:delete')")
	public ApiResponse<Void> deleteUser(@PathVariable Long id) {
		userService.deleteUser(id);
		return ApiResponse.success("删除用户成功");
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('system:user:list')")
	public ApiResponse<UserDetailResponse> getUserById(@PathVariable Long id) {
		UserDetailResponse user = userService.getUserById(id);
		return ApiResponse.success("获取用户成功", user);
	}

	@GetMapping("/list")
	@PreAuthorize("hasAuthority('system:user:list')")
	public PageResponse<List<UserDetailResponse>> listUsers(UserQueryDTO queryDTO) {
		List<UserDetailResponse> users = userService.listUsers(queryDTO);
		long total = users.size();
		int totalPages = (int) Math.ceil((double) total / queryDTO.getPageSize());
		return PageResponse.success(users, total, queryDTO.getPageNum(), queryDTO.getPageSize(), totalPages);
	}

	@PostMapping("/{id}/reset-password")
	@PreAuthorize("hasAuthority('system:user:reset-password')")
	public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody String newPassword) {
		userService.resetPassword(id, newPassword);
		return ApiResponse.success("重置密码成功");
	}

	@PostMapping("/{id}/assign-roles")
	@PreAuthorize("hasAuthority('system:user:assign-role')")
	public ApiResponse<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
		userService.assignRoles(id, roleIds);
		return ApiResponse.success("分配角色成功");
	}

}
