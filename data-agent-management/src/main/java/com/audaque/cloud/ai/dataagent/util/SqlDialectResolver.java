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
package com.audaque.cloud.ai.dataagent.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 元数据存储数据库 SQL 方言解析器
 */
@Component
public class SqlDialectResolver {

    @Value("${spring.datasource.platform:mysql}")
    private String platform;

    /**
     * 获取当前时间函数
     */
    public String now() {
        if ("dameng".equalsIgnoreCase(platform) || "dm".equalsIgnoreCase(platform)) {
            return "SYSDATE";
        }
        return "NOW()";
    }

    /**
     * 获取分页查询的 LIMIT 语法部分
     * MySQL: LIMIT offset, size
     * Dameng: LIMIT size OFFSET offset
     */
    public String limit(int offset, int size) {
        if ("dameng".equalsIgnoreCase(platform) || "dm".equalsIgnoreCase(platform)) {
            return "LIMIT " + size + " OFFSET " + offset;
        }
        return "LIMIT " + offset + ", " + size;
    }

    /**
     * 判断是否为达梦数据库
     */
    public boolean isDameng() {
        return "dameng".equalsIgnoreCase(platform) || "dm".equalsIgnoreCase(platform);
    }

}
