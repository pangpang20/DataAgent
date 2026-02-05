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
package com.audaque.cloud.ai.dataagent.dto.semantic;

import com.audaque.cloud.ai.dataagent.dto.common.BasePageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * DTO for SemanticModel pagination query
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SemanticModelQueryDTO extends BasePageQueryDTO {

    /**
     * Agent ID (required)
     */
    private Long agentId;

    /**
     * Fuzzy search by table name, column name, business name, or synonyms
     */
    private String keyword;

    /**
     * Filter by table name
     */
    private String tableName;

    /**
     * Filter by status: 0-disabled, 1-enabled
     */
    private Integer status;

    /**
     * Date range filter - start
     */
    private String createTimeStart;

    /**
     * Date range filter - end
     */
    private String createTimeEnd;

}
