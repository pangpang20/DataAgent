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
package com.audaque.cloud.ai.dataagent.enums;

/**
 * 智能体状态枚举
 */
public enum AgentStatus {
    
    /**
     * 草稿状态 - 待发布
     */
    DRAFT("draft", "草稿"),
    
    /**
     * 已发布状态
     */
    PUBLISHED("published", "已发布"),
    
    /**
     * 离线状态
     */
    OFFLINE("offline", "离线");
    
    private final String code;
    private final String description;
    
    AgentStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据代码获取枚举值
     * @param code 状态代码
     * @return 对应的枚举值，如果未找到则返回null
     */
    public static AgentStatus fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        
        for (AgentStatus status : AgentStatus.values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }
    
    /**
     * 判断是否为有效状态
     * @param code 状态代码
     * @return 是否有效
     */
    public static boolean isValidStatus(String code) {
        return fromCode(code) != null;
    }
    
    @Override
    public String toString() {
        return code;
    }
}