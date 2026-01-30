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

import com.audaque.cloud.ai.dataagent.bo.DbConfigBO;
import com.audaque.cloud.ai.dataagent.connector.accessor.Accessor;
import com.audaque.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.audaque.cloud.ai.dataagent.entity.AgentDatasource;
import com.audaque.cloud.ai.dataagent.entity.Datasource;
import com.audaque.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.audaque.cloud.ai.dataagent.service.datasource.DatasourceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DatabaseUtil 单元测试
 * 测试数据库工具类的核心功能，支持 MySQL 和达梦数据库
 *
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("数据库工具类测试")
class DatabaseUtilTest {

	@Mock
	private AccessorFactory accessorFactory;

	@Mock
	private AgentDatasourceService agentDatasourceService;

	@Mock
	private DatasourceService datasourceService;

	@InjectMocks
	private DatabaseUtil databaseUtil;

	@Test
	@DisplayName("getAgentDbConfig - MySQL 数据源应成功返回配置")
	void testGetAgentDbConfigForMySQL() {
		// Given
		Integer agentId = Integer.valueOf(1);
		
		Datasource datasource = new Datasource();
		datasource.setId(Integer.valueOf(100));
		datasource.setName("test_datasource");
		datasource.setType("mysql");

		AgentDatasource agentDatasource = new AgentDatasource(agentId, datasource.getId());
		agentDatasource.setDatasource(datasource);

		DbConfigBO dbConfig = new DbConfigBO();
		dbConfig.setUrl("jdbc:mysql://localhost:3306/test_db");
		dbConfig.setSchema("test_db");

		when(agentDatasourceService.getCurrentAgentDatasource(agentId))
			.thenReturn(agentDatasource);
		when(datasourceService.getDbConfig(datasource))
			.thenReturn(dbConfig);

		// When
		DbConfigBO result = databaseUtil.getAgentDbConfig(agentId);

		// Then
		assertNotNull(result);
		assertEquals("jdbc:mysql://localhost:3306/test_db", result.getUrl());
		assertEquals("test_db", result.getSchema());

		verify(agentDatasourceService, times(1)).getCurrentAgentDatasource(agentId);
		verify(datasourceService, times(1)).getDbConfig(datasource);
	}

	@Test
	@DisplayName("getAgentDbConfig - 达梦数据源应成功返回配置")
	void testGetAgentDbConfigForDameng() {
		// Given
		Integer agentId = Integer.valueOf(2);
		
		Datasource datasource = new Datasource();
		datasource.setId(Integer.valueOf(200));
		datasource.setName("dameng_datasource");
		datasource.setType("dameng");

		AgentDatasource agentDatasource = new AgentDatasource(agentId, datasource.getId());
		agentDatasource.setDatasource(datasource);

		DbConfigBO dbConfig = new DbConfigBO();
		dbConfig.setUrl("jdbc:dm://localhost:5236");
		dbConfig.setSchema("SYSDBA");

		when(agentDatasourceService.getCurrentAgentDatasource(agentId))
			.thenReturn(agentDatasource);
		when(datasourceService.getDbConfig(datasource))
			.thenReturn(dbConfig);

		// When
		DbConfigBO result = databaseUtil.getAgentDbConfig(agentId);

		// Then
		assertNotNull(result);
		assertEquals("jdbc:dm://localhost:5236", result.getUrl());
		assertEquals("SYSDBA", result.getSchema());

		verify(agentDatasourceService, times(1)).getCurrentAgentDatasource(agentId);
		verify(datasourceService, times(1)).getDbConfig(datasource);
	}

	@Test
	@DisplayName("getAgentAccessor - 应成功返回 Accessor")
	void testGetAgentAccessor() {
		// Given
		Integer agentId = Integer.valueOf(1);
		
		Datasource datasource = new Datasource();
		datasource.setId(Integer.valueOf(100));
		datasource.setType("mysql");

		AgentDatasource agentDatasource = new AgentDatasource(agentId, datasource.getId());
		agentDatasource.setDatasource(datasource);

		DbConfigBO dbConfig = new DbConfigBO();
		dbConfig.setUrl("jdbc:mysql://localhost:3306/test_db");

		Accessor mockAccessor = mock(Accessor.class);

		when(agentDatasourceService.getCurrentAgentDatasource(agentId))
			.thenReturn(agentDatasource);
		when(datasourceService.getDbConfig(datasource))
			.thenReturn(dbConfig);
		when(accessorFactory.getAccessorByDbConfig(dbConfig))
			.thenReturn(mockAccessor);

		// When
		Accessor result = databaseUtil.getAgentAccessor(agentId);

		// Then
		assertNotNull(result);
		assertSame(mockAccessor, result);

		verify(accessorFactory, times(1)).getAccessorByDbConfig(dbConfig);
	}

	@Test
	@DisplayName("getAgentDbConfig - 无活跃数据源应抛出异常")
	void testGetAgentDbConfigWithNoActiveDatasource() {
		// Given
		Integer agentId = Integer.valueOf(999);
		
		when(agentDatasourceService.getCurrentAgentDatasource(agentId))
			.thenThrow(new RuntimeException("No active datasource found"));

		// When & Then
		assertThrows(RuntimeException.class, () -> {
			databaseUtil.getAgentDbConfig(agentId);
		});

		verify(agentDatasourceService, times(1)).getCurrentAgentDatasource(agentId);
		verify(datasourceService, never()).getDbConfig(any());
	}

}
