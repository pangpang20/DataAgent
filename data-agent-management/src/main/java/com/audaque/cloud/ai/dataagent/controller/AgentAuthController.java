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

import com.audaque.cloud.ai.dataagent.dto.agent.AgentAuthGrantRequest;
import com.audaque.cloud.ai.dataagent.dto.agent.AgentAuthResponse;
import com.audaque.cloud.ai.dataagent.service.agent.AgentAuthService;
import com.audaque.cloud.ai.dataagent.vo.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/agent/auth")
@RequiredArgsConstructor
public class AgentAuthController {

	private final AgentAuthService agentAuthService;

	@PostMapping("/grant")
	public ApiResponse<Void> grantAccess(@Valid @RequestBody AgentAuthGrantRequest request) {
		agentAuthService.grantAccess(request);
		return ApiResponse.success("授权成功");
	}

	@DeleteMapping("/revoke/{agentId}/{userId}")
	public ApiResponse<Void> revokeAccess(@PathVariable Long agentId, @PathVariable Long userId) {
		agentAuthService.revokeAccess(agentId, userId);
		return ApiResponse.success("撤销授权成功");
	}

	@PutMapping("/update")
	public ApiResponse<Void> updateAccess(@Valid @RequestBody AgentAuthGrantRequest request) {
		agentAuthService.updateAccess(request);
		return ApiResponse.success("更新授权成功");
	}

	@GetMapping("/list/{agentId}")
	public ApiResponse<List<AgentAuthResponse>> listAgentAuth(@PathVariable Long agentId) {
		List<AgentAuthResponse> auths = agentAuthService.listAgentAuth(agentId);
		return ApiResponse.success("获取授权列表成功", auths);
	}

}
