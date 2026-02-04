/*
 * Copyright 2026 the original author or authors.
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
