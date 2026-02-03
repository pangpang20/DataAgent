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
package com.audaque.cloud.ai.dataagent.service.datasource.impl;

import com.audaque.cloud.ai.dataagent.bo.DbConfigBO;
import com.audaque.cloud.ai.dataagent.dto.datasource.SchemaInitRequest;
import com.audaque.cloud.ai.dataagent.entity.AgentDatasource;
import com.audaque.cloud.ai.dataagent.entity.Datasource;
import com.audaque.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.audaque.cloud.ai.dataagent.mapper.AgentDatasourceTablesMapper;
import com.audaque.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.audaque.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.audaque.cloud.ai.dataagent.service.schema.SchemaService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@AllArgsConstructor
public class AgentDatasourceServiceImpl implements AgentDatasourceService {

	private final DatasourceService datasourceService;

	private final SchemaService schemaService;

	private final AgentDatasourceMapper agentDatasourceMapper;

	private final AgentDatasourceTablesMapper tablesMapper;

	@Override
	public Boolean initializeSchemaForAgentWithDatasource(Long agentId, Integer datasourceId, List<String> tables) {
		Assert.notNull(agentId, "Agent ID cannot be null");
		Assert.notNull(datasourceId, "Datasource ID cannot be null");
		Assert.notEmpty(tables, "Tables cannot be empty");
		try {
			String agentIdStr = String.valueOf(agentId);
			log.info("Initializing schema for agent: {} with datasource: {}, tables: {}", agentIdStr, datasourceId,
					tables);

			// Get data source information
			Datasource datasource = datasourceService.getDatasourceById(datasourceId);
			if (datasource == null) {
				throw new RuntimeException("Datasource not found with id: " + datasourceId);
			}

			// Create database configuration
			DbConfigBO dbConfig = datasourceService.getDbConfig(datasource);

			// Create SchemaInitRequest
			SchemaInitRequest schemaInitRequest = new SchemaInitRequest();
			schemaInitRequest.setDbConfig(dbConfig);
			schemaInitRequest.setTables(tables);

			log.info("Created SchemaInitRequest for agent: {}, dbConfig: {}, tables: {}", agentIdStr, dbConfig, tables);

			// Call the original initialization method
			return schemaService.schema(agentIdStr, schemaInitRequest);

		} catch (Exception e) {
			log.error("Failed to initialize schema for agent: {} with datasource: {}", agentId, datasourceId, e);
			throw new RuntimeException("Failed to initialize schema for agent " + agentId + ": " + e.getMessage(), e);
		}
	}

	@Override
	public List<AgentDatasource> getAgentDatasource(Integer agentId) {
		Assert.notNull(agentId, "Agent ID cannot be null");
		log.debug("Getting datasources for agentId: {}", agentId);

		List<AgentDatasource> agentDatasources = agentDatasourceMapper.selectByAgentIdWithDatasource(agentId);
		log.debug("Found {} datasource relations for agentId: {}", agentDatasources.size(), agentId);

		// Manually fill in the data source information
		for (AgentDatasource agentDatasource : agentDatasources) {
			if (agentDatasource.getDatasourceId() != null) {
				Datasource datasource = datasourceService.getDatasourceById(agentDatasource.getDatasourceId());
				agentDatasource.setDatasource(datasource);
				log.debug("Filled datasource info for relation id: {}, datasource: {}",
						agentDatasource.getId(), datasource != null ? datasource.getName() : "null");
			}
			// Get selected tables
			int id = agentDatasource.getId();
			List<String> tables = tablesMapper.getAgentDatasourceTables(id);
			agentDatasource.setSelectTables(Optional.ofNullable(tables).orElse(List.of()));
			log.debug("Loaded {} selected tables for relation id: {}", agentDatasource.getSelectTables().size(), id);
		}

		return agentDatasources;
	}

	@Override
	@Transactional
	public AgentDatasource addDatasourceToAgent(Integer agentId, Integer datasourceId) {
		log.info("Adding datasource: {} to agent: {}", datasourceId, agentId);

		// First, disable other data sources for this agent
		int disabled = agentDatasourceMapper.disableAllByAgentId(agentId);
		log.debug("Disabled {} existing datasources for agent: {}", disabled, agentId);

		// Check if an association already exists
		AgentDatasource existing = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);

		AgentDatasource result;
		if (existing != null) {
			log.debug("Existing relation found, activating relation id: {}", existing.getId());
			// If it exists, activate the association
			agentDatasourceMapper.enableRelation(agentId, datasourceId);

			// Remove existing tables
			tablesMapper.removeAllTables(existing.getId());
			log.debug("Removed all tables from existing relation id: {}", existing.getId());

			// Query and return the updated association
			result = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);
			log.info("Activated existing datasource relation for agent: {}, datasource: {}", agentId, datasourceId);
		} else {
			log.debug("Creating new datasource relation for agent: {}, datasource: {}", agentId, datasourceId);
			// If it does not exist, create a new association
			AgentDatasource agentDatasource = new AgentDatasource(agentId, datasourceId);
			agentDatasource.setIsActive(1);
			agentDatasourceMapper.createNewRelationEnabled(agentId, datasourceId);
			result = agentDatasource;
			log.info("Created new datasource relation for agent: {}, datasource: {}", agentId, datasourceId);
		}
		result.setSelectTables(List.of());
		return result;
	}

	@Override
	public void removeDatasourceFromAgent(Integer agentId, Integer datasourceId) {
		log.info("Removing datasource: {} from agent: {}", datasourceId, agentId);
		agentDatasourceMapper.removeRelation(agentId, datasourceId);
		log.info("Successfully removed datasource: {} from agent: {}", datasourceId, agentId);
	}

	@Override
	public AgentDatasource toggleDatasourceForAgent(Integer agentId, Integer datasourceId, Boolean isActive) {
		log.info("Toggling datasource: {} for agent: {} to active: {}", datasourceId, agentId, isActive);

		// If enabling data source, first check if there are other enabled data sources
		if (isActive) {
			int activeCount = agentDatasourceMapper.countActiveByAgentIdExcluding(agentId, datasourceId);
			log.debug("Found {} other active datasources for agent: {}", activeCount, agentId);
			if (activeCount > 0) {
				log.error("Cannot enable datasource: {} for agent: {} - other datasources are active", datasourceId,
						agentId);
				throw new RuntimeException(
						"Only one datasource can be active per agent. Please disable other datasources first.");
			}
		}

		// Update data source status
		int updated = agentDatasourceMapper.updateRelation(agentId, datasourceId, isActive ? 1 : 0);

		if (updated == 0) {
			log.error("Datasource relation not found for agent: {}, datasource: {}", agentId, datasourceId);
			throw new RuntimeException("Datasource relation not found");
		}

		AgentDatasource result = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);
		log.info("Successfully toggled datasource: {} for agent: {} to active: {}", datasourceId, agentId, isActive);
		return result;
	}

	@Override
	@Transactional
	public void updateDatasourceTables(Integer agentId, Integer datasourceId, List<String> tables) {
		log.info("Updating datasource tables for agent: {}, datasource: {}, table count: {}",
				agentId, datasourceId, tables != null ? tables.size() : 0);

		if (agentId == null || datasourceId == null || tables == null) {
			log.error("Invalid parameters - agentId: {}, datasourceId: {}, tables: {}", agentId, datasourceId, tables);
			throw new IllegalArgumentException("Parameters cannot be null");
		}

		AgentDatasource datasource = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);
		if (datasource == null) {
			log.error("Datasource relation not found for agent: {}, datasource: {}", agentId, datasourceId);
			throw new IllegalArgumentException("Datasource relation not found");
		}

		if (tables.isEmpty()) {
			log.debug("Removing all tables from relation id: {}", datasource.getId());
			tablesMapper.removeAllTables(datasource.getId());
		} else {
			log.debug("Updating {} tables for relation id: {}", tables.size(), datasource.getId());
			tablesMapper.updateAgentDatasourceTables(datasource.getId(), tables);
		}
		log.info("Successfully updated datasource tables for agent: {}, datasource: {}", agentId, datasourceId);
	}

}
