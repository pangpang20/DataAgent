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

import com.audaque.cloud.ai.dataagent.dto.user.UserDetailResponse;
import com.audaque.cloud.ai.dataagent.security.SecurityUtils;
import com.audaque.cloud.ai.dataagent.service.user.UserService;
import com.audaque.cloud.ai.dataagent.vo.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

	private final UserService userService;

	@GetMapping
	public ApiResponse<UserDetailResponse> getProfile() {
		Long userId = SecurityUtils.getCurrentUserId();
		UserDetailResponse user = userService.getUserById(userId);
		return ApiResponse.success("获取个人信息成功", user);
	}

	@PutMapping
	public ApiResponse<UserDetailResponse> updateProfile(@RequestBody UserDetailResponse request) {
		Long userId = SecurityUtils.getCurrentUserId();
		com.audaque.cloud.ai.dataagent.dto.user.UserUpdateRequest updateRequest = new com.audaque.cloud.ai.dataagent.dto.user.UserUpdateRequest();
		updateRequest.setId(userId);
		updateRequest.setNickname(request.getNickname());
		updateRequest.setEmail(request.getEmail());
		updateRequest.setPhone(request.getPhone());
		UserDetailResponse user = userService.updateUser(updateRequest);
		return ApiResponse.success("更新个人信息成功", user);
	}

}
