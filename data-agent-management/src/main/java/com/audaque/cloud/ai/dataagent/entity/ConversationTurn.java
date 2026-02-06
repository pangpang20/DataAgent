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
package com.audaque.cloud.ai.dataagent.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 多轮对话历史记录实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationTurn {

    private Long id;

    /**
     * 会话线程ID
     */
    private String threadId;

    /**
     * 用户问题
     */
    private String userQuestion;

    /**
     * AI规划输出
     */
    private String plan;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 序号（用于排序）
     */
    private Integer sequenceNumber;

    public ConversationTurn(String userQuestion, String plan) {
        this.userQuestion = userQuestion;
        this.plan = plan;
    }
}