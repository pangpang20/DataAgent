package com.audaque.cloud.ai.dataagent.dto.agent;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BatchDeleteDTO {

	@NotNull(message = "agentId cannot be null")
	private Long agentId;

	@NotEmpty(message = "ids cannot be empty")
	private List<Long> ids;

}
