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

import lombok.Data;

/**
 * 通用响应DTO
 */
@Data
public class CommonResponseDTO {

    /**
     * 响应消息
     */
    private String message;

    /**
     * 错误信息（可选）
     */
    private String error;

    /**
     * 构造成功响应
     * 
     * @param message 响应消息
     * @return CommonResponseDTO
     */
    public static CommonResponseDTO success(String message) {
        CommonResponseDTO response = new CommonResponseDTO();
        response.setMessage(message);
        return response;
    }

    /**
     * 构造错误响应
     * 
     * @param error 错误信息
     * @return CommonResponseDTO
     */
    public static CommonResponseDTO error(String error) {
        CommonResponseDTO response = new CommonResponseDTO();
        response.setError(error);
        return response;
    }

}

