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
package com.audaque.cloud.ai.dataagent.dto.agent;

import com.audaque.cloud.ai.dataagent.dto.common.BasePageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * DTO for Agent pagination query
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentQueryDTO extends BasePageQueryDTO {

    /**
     * Fuzzy search by name or description
     */
    private String keyword;

    /**
     * Filter by status: draft, published, offline
     */
    private String status;

    /**
     * Filter by category
     */
    private String category;

}
