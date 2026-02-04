package com.audaque.cloud.ai.dataagent.dto.agent;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresetQuestionQueryDTO {

	@NotNull(message = "agentId cannot be null")
	private Long agentId;

	// Search by question content (fuzzy search)
	private String keyword;

	// Filter by active status: null-all, true-active only, false-inactive only
	private Boolean isActive;

	// Date range filter
	private String createTimeStart;

	private String createTimeEnd;

	// Pagination parameters
	@NotNull(message = "pageNum cannot be null")
	@Min(value = 1, message = "pageNum must be greater than 0")
	private Integer pageNum = 1;

	@NotNull(message = "pageSize cannot be null")
	@Min(value = 1, message = "pageSize must be greater than 0")
	private Integer pageSize = 10;

}
