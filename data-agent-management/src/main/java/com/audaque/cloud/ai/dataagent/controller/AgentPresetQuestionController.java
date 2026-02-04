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
import com.audaque.cloud.ai.dataagent.dto.agent.BatchUpdateStatusDTO;
import com.audaque.cloud.ai.dataagent.dto.agent.PresetQuestionQueryDTO;
import com.audaque.cloud.ai.dataagent.entity.AgentPresetQuestion;
import com.audaque.cloud.ai.dataagent.service.agent.AgentPresetQuestionService;
import com.audaque.cloud.ai.dataagent.vo.PageResponse;
import com.audaque.cloud.ai.dataagent.vo.PageResult;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = "*")
@AllArgsConstructor
// todo: 部分返回值和参数需要定义DTO
public class AgentPresetQuestionController {

	private final AgentPresetQuestionService presetQuestionService;

	/**
	 * Get preset question list of agent
	 */
	@GetMapping("/{agentId}/preset-questions")
	public ResponseEntity<List<AgentPresetQuestion>> getPresetQuestions(@PathVariable(value = "agentId") Long agentId) {
		try {
			List<AgentPresetQuestion> questions = presetQuestionService.findAllByAgentId(agentId);
			return ResponseEntity.ok(questions);
		} catch (Exception e) {
			log.error("Error getting preset questions for agent {}", agentId, e);
			return ResponseEntity.internalServerError().build();
		}
	}

	/**
	 * Batch save preset questions of agent
	 */
	@PostMapping("/{agentId}/preset-questions")
	public ResponseEntity<Map<String, String>> savePresetQuestions(@PathVariable(value = "agentId") Long agentId,
			@RequestBody List<Map<String, Object>> questionsData) {
		try {
			List<AgentPresetQuestion> questions = questionsData.stream().map(data -> {
				AgentPresetQuestion question = new AgentPresetQuestion();
				question.setQuestion((String) data.get("question"));
				Object isActiveObj = data.get("isActive");
				if (isActiveObj instanceof Boolean) {
					question.setIsActive((Boolean) isActiveObj);
				} else if (isActiveObj != null) {
					question.setIsActive(Boolean.parseBoolean(isActiveObj.toString()));
				} else {
					question.setIsActive(true);
				}
				return question;
			}).toList();

			presetQuestionService.batchSave(agentId, questions);
			return ResponseEntity.ok(Map.of("message", "预设问题保存成功"));
		} catch (Exception e) {
			log.error("Error saving preset questions for agent {}", agentId, e);
			return ResponseEntity.internalServerError().body(Map.of("error", "保存预设问题失败: " + e.getMessage()));
		}
	}

	/**
	 * Delete preset question
	 */
	@DeleteMapping("/{agentId}/preset-questions/{questionId}")
	public ResponseEntity<Map<String, String>> deletePresetQuestion(@PathVariable(value = "agentId") Long agentId,
			@PathVariable Long questionId) {
		try {
			presetQuestionService.deleteById(questionId);
			return ResponseEntity.ok(Map.of("message", "预设问题删除成功"));
		} catch (Exception e) {
			log.error("Error deleting preset question {} for agent {}", questionId, agentId, e);
			return ResponseEntity.internalServerError().body(Map.of("error", "删除预设问题失败: " + e.getMessage()));
		}
	}

	/**
	 * Page query preset questions with filters
	 */
	@PostMapping("/{agentId}/preset-questions/page")
	public PageResponse<List<AgentPresetQuestion>> queryPresetQuestionsPage(
			@PathVariable(value = "agentId") Long agentId, @Valid @RequestBody PresetQuestionQueryDTO queryDTO) {
		try {
			log.info("Page query request: agentId={}, pageNum={}, pageSize={}", agentId, queryDTO.getPageNum(),
					queryDTO.getPageSize());

			// Set agentId from path variable
			queryDTO.setAgentId(agentId);

			PageResult<AgentPresetQuestion> pageResult = presetQuestionService.queryByConditionsWithPage(queryDTO);

			return PageResponse.success(pageResult.getData(), pageResult.getTotal(), pageResult.getPageNum(),
					pageResult.getPageSize(), pageResult.getTotalPages());
		} catch (IllegalArgumentException e) {
			log.error("Invalid query parameters for agent {}: {}", agentId, e.getMessage());
			return PageResponse.pageError("Invalid parameters: " + e.getMessage());
		} catch (Exception e) {
			log.error("Error querying preset questions page for agent {}", agentId, e);
			return PageResponse.pageError("Query failed: " + e.getMessage());
		}
	}

	/**
	 * Batch delete preset questions
	 */
	@DeleteMapping("/{agentId}/preset-questions/batch")
	public ResponseEntity<Map<String, String>> batchDeletePresetQuestions(@PathVariable(value = "agentId") Long agentId,
			@Valid @RequestBody BatchDeleteDTO deleteDTO) {
		try {
			log.info("Batch delete request: agentId={}, count={}", agentId, deleteDTO.getIds().size());

			// Set agentId from path variable
			deleteDTO.setAgentId(agentId);

			boolean success = presetQuestionService.batchDelete(deleteDTO);

			if (success) {
				return ResponseEntity.ok(Map.of("message", "Batch delete successful"));
			} else {
				return ResponseEntity.internalServerError()
						.body(Map.of("error", "Batch delete failed: no records deleted"));
			}
		} catch (IllegalArgumentException e) {
			log.error("Invalid batch delete parameters for agent {}: {}", agentId, e.getMessage());
			return ResponseEntity.badRequest().body(Map.of("error", "Invalid parameters: " + e.getMessage()));
		} catch (Exception e) {
			log.error("Error batch deleting preset questions for agent {}", agentId, e);
			return ResponseEntity.internalServerError()
					.body(Map.of("error", "Batch delete failed: " + e.getMessage()));
		}
	}

	/**
	 * Batch update preset questions status (enable/disable)
	 */
	@PutMapping("/{agentId}/preset-questions/batch/status")
	public ResponseEntity<Map<String, String>> batchUpdateStatus(@PathVariable(value = "agentId") Long agentId,
			@Valid @RequestBody BatchUpdateStatusDTO updateStatusDTO) {
		try {
			log.info("Batch update status request: agentId={}, count={}, isActive={}", agentId,
					updateStatusDTO.getIds().size(), updateStatusDTO.getIsActive());

			// Set agentId from path variable
			updateStatusDTO.setAgentId(agentId);

			boolean success = presetQuestionService.batchUpdateStatus(updateStatusDTO);

			if (success) {
				String action = updateStatusDTO.getIsActive() ? "enabled" : "disabled";
				return ResponseEntity.ok(Map.of("message", "Batch " + action + " successful"));
			} else {
				return ResponseEntity.internalServerError()
						.body(Map.of("error", "Batch update status failed: no records updated"));
			}
		} catch (IllegalArgumentException e) {
			log.error("Invalid batch update status parameters for agent {}: {}", agentId, e.getMessage());
			return ResponseEntity.badRequest().body(Map.of("error", "Invalid parameters: " + e.getMessage()));
		} catch (Exception e) {
			log.error("Error batch updating status for agent {}", agentId, e);
			return ResponseEntity.internalServerError()
					.body(Map.of("error", "Batch update status failed: " + e.getMessage()));
		}
	}

}
