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

import com.audaque.cloud.ai.dataagent.dto.agent.BatchDeleteDTO;
import com.audaque.cloud.ai.dataagent.dto.knowledge.BusinessKnowledgeQueryDTO;
import com.audaque.cloud.ai.dataagent.dto.knowledge.businessknowledge.CreateBusinessKnowledgeDTO;
import com.audaque.cloud.ai.dataagent.dto.knowledge.businessknowledge.UpdateBusinessKnowledgeDTO;
import com.audaque.cloud.ai.dataagent.service.business.BusinessKnowledgeService;
import com.audaque.cloud.ai.dataagent.vo.ApiResponse;
import com.audaque.cloud.ai.dataagent.vo.BusinessKnowledgeVO;
import com.audaque.cloud.ai.dataagent.vo.PageResponse;
import com.audaque.cloud.ai.dataagent.vo.PageResult;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/business-knowledge")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class BusinessKnowledgeController {

	private final BusinessKnowledgeService businessKnowledgeService;

	@GetMapping
	public ApiResponse<List<BusinessKnowledgeVO>> list(@RequestParam(value = "agentId") String agentIdStr,
			@RequestParam(value = "keyword", required = false) String keyword) {
		List<BusinessKnowledgeVO> result;
		Long agentId = Long.parseLong(agentIdStr);

		if (StringUtils.hasText(keyword)) {
			result = businessKnowledgeService.searchKnowledge(agentId, keyword);
		} else {
			result = businessKnowledgeService.getKnowledge(agentId);
		}
		return ApiResponse.success("success list businessKnowledge", result);
	}

	@GetMapping("/{id}")
	public ApiResponse<BusinessKnowledgeVO> get(@PathVariable(value = "id") Long id) {
		BusinessKnowledgeVO vo = businessKnowledgeService.getKnowledgeById(id);
		if (vo == null) {
			return ApiResponse.error("businessKnowledge not found");
		}
		return ApiResponse.success("success get businessKnowledge", vo);
	}

	@PostMapping
	public ApiResponse<BusinessKnowledgeVO> create(@RequestBody @Validated CreateBusinessKnowledgeDTO knowledge) {
		return ApiResponse.success("success create businessKnowledge",
				businessKnowledgeService.addKnowledge(knowledge));
	}

	@PutMapping("/{id}")
	public ApiResponse<BusinessKnowledgeVO> update(@PathVariable(value = "id") Long id,
			@RequestBody UpdateBusinessKnowledgeDTO knowledge) {

		return ApiResponse.success("success update businessKnowledge",
				businessKnowledgeService.updateKnowledge(id, knowledge));
	}

	@DeleteMapping("/{id}")
	public ApiResponse<Boolean> delete(@PathVariable(value = "id") Long id) {
		if (businessKnowledgeService.getKnowledgeById(id) == null) {
			return ApiResponse.error("businessKnowledge not found");
		}
		businessKnowledgeService.deleteKnowledge(id);
		return ApiResponse.success("success delete businessKnowledge");
	}

	@PostMapping("/recall/{id}")
	public ApiResponse<Boolean> recallKnowledge(@PathVariable(value = "id") Long id,
			@RequestParam(value = "isRecall") Boolean isRecall) {
		businessKnowledgeService.recallKnowledge(id, isRecall);
		return ApiResponse.success("success update recall businessKnowledge");
	}

	@PostMapping("/refresh-vector-store")
	public ApiResponse<Boolean> refreshAllKnowledgeToVectorStore(@RequestParam(value = "agentId") String agentId) {
		// 校验 agentId 不为空和空字符串
		if (!StringUtils.hasText(agentId)) {
			return ApiResponse.error("agentId cannot be empty");
		}

		try {
			businessKnowledgeService.refreshAllKnowledgeToVectorStore(agentId);
			return ApiResponse.success("success refresh vector store");
		} catch (Exception e) {
			log.error("Failed to refresh vector store for agentId: {}", agentId, e);
			return ApiResponse.error("Failed to refresh vector store");
		}
	}

	@PostMapping("/retry-embedding/{id}")
	public ApiResponse<Boolean> retryEmbedding(@PathVariable(value = "id") Long id) {
		businessKnowledgeService.retryEmbedding(id);
		return ApiResponse.success("success retry embedding");
	}

	/**
	 * Page query business knowledge with filters
	 */
	@PostMapping("/page")
	public PageResponse<List<BusinessKnowledgeVO>> queryByPage(@Valid @RequestBody BusinessKnowledgeQueryDTO queryDTO) {
		try {
			log.info("Page query request: agentId={}, pageNum={}, pageSize={}",
					queryDTO.getAgentId(), queryDTO.getPageNum(), queryDTO.getPageSize());

			PageResult<BusinessKnowledgeVO> pageResult = businessKnowledgeService.queryByConditionsWithPage(queryDTO);

			return PageResponse.success(pageResult.getData(), pageResult.getTotal(),
					pageResult.getPageNum(), pageResult.getPageSize(), pageResult.getTotalPages());
		} catch (IllegalArgumentException e) {
			log.error("Invalid query parameters: {}", e.getMessage());
			return PageResponse.pageError("Invalid parameters: " + e.getMessage());
		} catch (Exception e) {
			log.error("Error querying business knowledge page", e);
			return PageResponse.pageError("Query failed: " + e.getMessage());
		}
	}

	/**
	 * Batch delete business knowledge (logical delete)
	 */
	@DeleteMapping("/batch")
	public ApiResponse<Boolean> batchDelete(@Valid @RequestBody BatchDeleteDTO deleteDTO) {
		try {
			log.info("Batch delete request: agentId={}, count={}",
					deleteDTO.getAgentId(), deleteDTO.getIds().size());

			int affected = businessKnowledgeService.batchDelete(deleteDTO.getAgentId(), deleteDTO.getIds());

			if (affected > 0) {
				return ApiResponse.success("Batch delete successful", true);
			} else {
				return ApiResponse.error("No records deleted");
			}
		} catch (IllegalArgumentException e) {
			log.error("Invalid batch delete parameters: {}", e.getMessage());
			return ApiResponse.error("Invalid parameters: " + e.getMessage());
		} catch (Exception e) {
			log.error("Error batch deleting business knowledge", e);
			return ApiResponse.error("Batch delete failed: " + e.getMessage());
		}
	}

}
