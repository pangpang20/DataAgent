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
package com.audaque.cloud.ai.dataagent.dto.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Base DTO for pagination queries
 */
@Data
public class BasePageQueryDTO {

    @NotNull(message = "pageNum cannot be null")
    @Min(value = 1, message = "pageNum must be greater than 0")
    private Integer pageNum = 1;

    @NotNull(message = "pageSize cannot be null")
    @Min(value = 1, message = "pageSize must be greater than 0")
    @Max(value = 100, message = "pageSize cannot exceed 100")
    private Integer pageSize = 10;

    /**
     * Calculate offset for pagination
     * 
     * @return offset value
     */
    public int calculateOffset() {
        return (pageNum - 1) * pageSize;
    }

}
