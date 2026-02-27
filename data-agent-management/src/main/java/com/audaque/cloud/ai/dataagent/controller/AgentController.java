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

import com.audaque.cloud.ai.dataagent.dto.agent.AgentQueryDTO;
import com.audaque.cloud.ai.dataagent.entity.Agent;
import com.audaque.cloud.ai.dataagent.enums.AgentStatus;
import com.audaque.cloud.ai.dataagent.service.agent.AgentService;
import com.audaque.cloud.ai.dataagent.vo.ApiKeyResponse;
import com.audaque.cloud.ai.dataagent.vo.ApiResponse;
import com.audaque.cloud.ai.dataagent.vo.PageResponse;
import com.audaque.cloud.ai.dataagent.vo.PageResult;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent Management Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class AgentController {

	private final AgentService agentService;

	/**
	 * Get agent list
	 */
	@GetMapping("/list")
	public ResponseEntity<List<Agent>> list(@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "keyword", required = false) String keyword) {
		List<Agent> result;
		if (keyword != null && !keyword.trim().isEmpty()) {
			result = agentService.search(keyword);
		} else if (status != null && !status.trim().isEmpty()) {
			result = agentService.findByStatus(status);
		} else {
			result = agentService.findAll();
		}
		return ResponseEntity.ok(result);
	}

	/**
	 * Get agent details by ID
	 */
	@GetMapping("/{id}")
	public ResponseEntity<Agent> get(@PathVariable(value = "id") Long id) {
		Agent agent = agentService.findById(id);
		if (agent == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(agent);
	}

	/**
	 * Create agent
	 */
	@PostMapping
	public ResponseEntity<Agent> create(@RequestBody Agent agent) {
		// Set default status
		if (agent.getStatus() == null) {
			agent.setStatus(AgentStatus.DRAFT);
		}
		Agent saved = agentService.save(agent);
		return ResponseEntity.ok(saved);
	}

	/**
	 * Update agent
	 */
	@PutMapping("/{id}")
	public ResponseEntity<Agent> update(@PathVariable(value = "id") Long id, @RequestBody Agent agent) {
		if (agentService.findById(id) == null) {
			return ResponseEntity.notFound().build();
		}
		agent.setId(id);
		Agent updated = agentService.save(agent);
		return ResponseEntity.ok(updated);
	}

	/**
	 * Delete agent
	 */
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable(value = "id") Long id) {
		if (agentService.findById(id) == null) {
			return ResponseEntity.notFound().build();
		}
		agentService.deleteById(id);
		return ResponseEntity.ok().build();
	}

	/**
	 * Publish agent
	 */
	@PostMapping("/{id}/publish")
	public ResponseEntity<Agent> publish(@PathVariable(value = "id") Long id) {
		Agent agent = agentService.findById(id);
		if (agent == null) {
			return ResponseEntity.notFound().build();
		}
		agent.setStatus(AgentStatus.PUBLISHED);
		Agent updated = agentService.save(agent);
		return ResponseEntity.ok(updated);
	}

	/**
	 * Offline agent
	 */
	@PostMapping("/{id}/offline")
	public ResponseEntity<Agent> offline(@PathVariable(value = "id") Long id) {
		Agent agent = agentService.findById(id);
		if (agent == null) {
			return ResponseEntity.notFound().build();
		}
		agent.setStatus(AgentStatus.OFFLINE);
		Agent updated = agentService.save(agent);
		return ResponseEntity.ok(updated);
	}

	/**
	 * Get masked API Key status
	 */
	@GetMapping("/{id}/api-key")
	public ResponseEntity<ApiResponse<ApiKeyResponse>> getApiKey(@PathVariable("id") Long id) {
		Agent agent = agentService.findById(id);
		if (agent == null) {
			return ResponseEntity.notFound().build();
		}
		String masked = agentService.getApiKeyMasked(id);
		return ResponseEntity.ok(buildApiKeyResponse(masked, agent.getApiKeyEnabled(), "获取 API Key 成功"));
	}

	/**
	 * Generate API Key
	 */
	@PostMapping("/{id}/api-key/generate")
	public ResponseEntity<ApiResponse<ApiKeyResponse>> generateApiKey(@PathVariable("id") Long id) {
		Agent existing = agentService.findById(id);
		if (existing == null) {
			return ResponseEntity.notFound().build();
		}
		Agent agent = agentService.generateApiKey(id);
		return ResponseEntity.ok(buildApiKeyResponse(agent.getApiKey(), agent.getApiKeyEnabled(), "生成 API Key 成功"));
	}

	/**
	 * Reset API Key
	 */
	@PostMapping("/{id}/api-key/reset")
	public ResponseEntity<ApiResponse<ApiKeyResponse>> resetApiKey(@PathVariable("id") Long id) {
		Agent existing = agentService.findById(id);
		if (existing == null) {
			return ResponseEntity.notFound().build();
		}
		Agent agent = agentService.resetApiKey(id);
		return ResponseEntity.ok(buildApiKeyResponse(agent.getApiKey(), agent.getApiKeyEnabled(), "重置 API Key 成功"));
	}

	/**
	 * Reveal full API Key (show unmasked key)
	 */
	@PostMapping("/{id}/api-key/reveal")
	public ResponseEntity<ApiResponse<ApiKeyResponse>> revealApiKey(@PathVariable("id") Long id) {
		Agent agent = agentService.findById(id);
		if (agent == null) {
			return ResponseEntity.notFound().build();
		}
		if (agent.getApiKey() == null) {
			return ResponseEntity.ok(buildApiKeyResponse(null, agent.getApiKeyEnabled(), "API Key 不存在"));
		}
		return ResponseEntity.ok(buildApiKeyResponse(agent.getApiKey(), agent.getApiKeyEnabled(), "获取完整 API Key 成功"));
	}

	/**
	 * Delete API Key
	 */
	@DeleteMapping("/{id}/api-key")
	public ResponseEntity<ApiResponse<ApiKeyResponse>> deleteApiKey(@PathVariable("id") Long id) {
		Agent existing = agentService.findById(id);
		if (existing == null) {
			return ResponseEntity.notFound().build();
		}
		Agent agent = agentService.deleteApiKey(id);
		return ResponseEntity.ok(buildApiKeyResponse(agent.getApiKey(), agent.getApiKeyEnabled(), "删除 API Key 成功"));
	}

	/**
	 * Toggle API Key enable flag
	 */
	@PostMapping("/{id}/api-key/enable")
	public ResponseEntity<ApiResponse<ApiKeyResponse>> toggleApiKey(@PathVariable("id") Long id,
			@RequestParam("enabled") boolean enabled) {
		Agent existing = agentService.findById(id);
		if (existing == null) {
			return ResponseEntity.notFound().build();
		}
		Agent agent = agentService.toggleApiKey(id, enabled);
		return ResponseEntity.ok(buildApiKeyResponse(agent.getApiKey() == null ? null : "****",
				agent.getApiKeyEnabled(), "更新 API Key 状态成功"));
	}

	private ApiResponse<ApiKeyResponse> buildApiKeyResponse(String apiKey, Integer apiKeyEnabled, String message) {
		return ApiResponse.success(message, new ApiKeyResponse(apiKey, apiKeyEnabled));
	}

	/**
	 * Page query agents with filters
	 */
	@PostMapping("/page")
	public PageResponse<List<Agent>> queryByPage(@Valid @RequestBody AgentQueryDTO queryDTO) {
		try {
			log.info("Page query request: pageNum={}, pageSize={}, keyword={}, status={}",
					queryDTO.getPageNum(), queryDTO.getPageSize(), queryDTO.getKeyword(), queryDTO.getStatus());

			PageResult<Agent> pageResult = agentService.queryByConditionsWithPage(queryDTO);

			return PageResponse.success(pageResult.getData(), pageResult.getTotal(),
					pageResult.getPageNum(), pageResult.getPageSize(), pageResult.getTotalPages());
		} catch (Exception e) {
			log.error("Error querying agent page", e);
			return PageResponse.pageError("Query failed: " + e.getMessage());
		}
	}

}
