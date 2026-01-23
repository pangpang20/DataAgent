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
package com.audaque.cloud.ai.dataagent.mapper;

import com.audaque.cloud.ai.dataagent.entity.ModelConfig;
import com.audaque.cloud.ai.dataagent.enums.ModelType;
import com.audaque.cloud.ai.dataagent.util.SqlDialectResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.test.context.jdbc.Sql;

/**
 * ModelConfigMapper 测试 - MySQL 和达梦数据库兼容性验证
 * 测试 @sqlDialectResolver@now() OGNL 表达式和 LIMIT/CONCAT 语法
 *
 * @author DataAgent Team
 * @since 2026/01/22
 */
@MybatisTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@Sql(scripts = "classpath:sql/schema-test.sql")
@Import(SqlDialectResolver.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@DisplayName("ModelConfigMapper 数据库兼容性测试")
class ModelConfigMapperTest {

    @Autowired
    private ModelConfigMapper modelConfigMapper;

    @BeforeEach
    void setUp() {
        List<ModelConfig> all = modelConfigMapper.findAll();
        for (ModelConfig config : all) {
            modelConfigMapper.deleteById(config.getId());
        }
    }

    @Test
    @DisplayName("insert - 应正确保存 Java 设置的时间戳")
    void testInsert() {
        ModelConfig config = createConfig("openai", "gpt-4", ModelType.CHAT);

        int result = modelConfigMapper.insert(config);

        assertEquals(1, result);
        assertNotNull(config.getId());
        assertNotNull(config.getCreatedTime(), "created_time 应已设置");
        assertNotNull(config.getUpdatedTime(), "updated_time 应已设置");
    }

    @Test
    @DisplayName("updateById - 应正确更新 Java 设置的时间戳")
    void testUpdate() {
        ModelConfig config = createConfig("openai", "gpt-4", ModelType.CHAT);
        modelConfigMapper.insert(config);

        config.setModelName("gpt-4-turbo");
        int result = modelConfigMapper.updateById(config);

        assertEquals(1, result);
        ModelConfig updated = modelConfigMapper.findById(config.getId());
        assertEquals("gpt-4-turbo", updated.getModelName());
    }

    @Test
    @DisplayName("selectActiveByType - LIMIT 1 兼容测试")
    void testSelectActiveByTypeLimitOne() {
        ModelConfig config1 = createConfig("openai", "gpt-4", ModelType.CHAT);
        config1.setIsActive(true);
        modelConfigMapper.insert(config1);

        ModelConfig config2 = createConfig("openai", "gpt-3.5", ModelType.CHAT);
        config2.setIsActive(true);
        modelConfigMapper.insert(config2);

        ModelConfig result = modelConfigMapper.selectActiveByType(ModelType.CHAT.name());

        assertNotNull(result);
        assertEquals(ModelType.CHAT.name(), result.getModelType().name());
        assertTrue(result.getIsActive());
    }

    @Test
    @DisplayName("findByConditions - CONCAT 函数兼容测试")
    void testFindByConditionsWithConcat() {
        ModelConfig config = createConfig("openai", "gpt-4-turbo", ModelType.CHAT);
        modelConfigMapper.insert(config);

        List<ModelConfig> result = modelConfigMapper.findByConditions(
                null, "turbo", null, null, null);

        assertFalse(result.isEmpty());
        assertTrue(result.get(0).getModelName().contains("turbo"));
    }

    @Test
    @DisplayName("deactivateOthers - 批量更新测试")
    void testDeactivateOthers() {
        ModelConfig config1 = createConfig("openai", "gpt-4", ModelType.CHAT);
        config1.setIsActive(true);
        modelConfigMapper.insert(config1);

        ModelConfig config2 = createConfig("openai", "gpt-3.5", ModelType.CHAT);
        config2.setIsActive(true);
        modelConfigMapper.insert(config2);

        modelConfigMapper.deactivateOthers(ModelType.CHAT.name(), config1.getId());

        ModelConfig updated1 = modelConfigMapper.findById(config1.getId());
        ModelConfig updated2 = modelConfigMapper.findById(config2.getId());

        assertTrue(updated1.getIsActive());
        assertFalse(updated2.getIsActive());
    }

    @Test
    @DisplayName("deleteById - 逻辑删除测试")
    void testDeleteById() {
        ModelConfig config = createConfig("openai", "gpt-4", ModelType.CHAT);
        modelConfigMapper.insert(config);

        int result = modelConfigMapper.deleteById(config.getId());

        assertEquals(1, result);
        ModelConfig deleted = modelConfigMapper.findById(config.getId());
        assertNull(deleted, "逻辑删除后应无法通过 findById 找到");
    }

    private ModelConfig createConfig(String provider, String modelName, ModelType modelType) {
        ModelConfig config = new ModelConfig();
        config.setProvider(provider);
        config.setBaseUrl("https://api.test.com/v1");
        config.setApiKey("sk-test-key");
        config.setModelName(modelName);
        config.setTemperature(0.7);
        config.setIsActive(false);
        config.setMaxTokens(4096);
        config.setModelType(modelType);
        config.setCompletionsPath("/chat/completions");
        config.setEmbeddingsPath("/embeddings");
        config.setIsDeleted(0);

        LocalDateTime now = LocalDateTime.now();
        config.setCreatedTime(now);
        config.setUpdatedTime(now);
        return config;
    }

}
