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
package com.audaque.cloud.ai.dataagent.controller;

import com.audaque.cloud.ai.dataagent.dto.datasource.DatasourceQueryDTO;
import com.audaque.cloud.ai.dataagent.dto.schema.CreateLogicalRelationDTO;
import com.audaque.cloud.ai.dataagent.dto.schema.UpdateLogicalRelationDTO;
import com.audaque.cloud.ai.dataagent.entity.Datasource;
import com.audaque.cloud.ai.dataagent.entity.LogicalRelation;
import com.audaque.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.audaque.cloud.ai.dataagent.vo.ApiResponse;
import com.audaque.cloud.ai.dataagent.vo.PageResponse;
import com.audaque.cloud.ai.dataagent.vo.PageResult;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/datasource")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class DatasourceController {

	private final DatasourceService datasourceService;

	/**
	 * Get all data source list
	 */
	@GetMapping
	public ResponseEntity<List<Datasource>> getAllDatasource(
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "type", required = false) String type) {

		List<Datasource> datasources;

		if (status != null && !status.isEmpty()) {
			datasources = datasourceService.getDatasourceByStatus(status);
		} else if (type != null && !type.isEmpty()) {
			datasources = datasourceService.getDatasourceByType(type);
		} else {
			datasources = datasourceService.getAllDatasource();
		}

		return ResponseEntity.ok(datasources);
	}

	/**
	 * Paginated query for datasources
	 */
	@PostMapping("/page")
	public PageResponse<List<Datasource>> queryByPage(@Valid @RequestBody DatasourceQueryDTO queryDTO) {
		PageResult<Datasource> pageResult = datasourceService.queryByConditionsWithPage(queryDTO);
		return PageResponse.success(pageResult.getData(), pageResult.getTotal(),
				pageResult.getPageNum(), pageResult.getPageSize(), pageResult.getTotalPages());
	}

	/**
	 * Get data source details by ID
	 */
	@GetMapping("/{id}")
	public ResponseEntity<Datasource> getDatasourceById(@PathVariable(value = "id") Integer id) {
		Datasource datasource = datasourceService.getDatasourceById(id);
		if (datasource != null) {
			return ResponseEntity.ok(datasource);
		} else {
			return ResponseEntity.notFound().build();
		}
	}

	@GetMapping("/{id}/tables")
	public ResponseEntity<List<String>> getDatasourceTables(@PathVariable(value = "id") Integer id) throws Exception {
		List<String> tables = datasourceService.getDatasourceTables(id);
		return ResponseEntity.ok(tables);
	}

	/**
	 * Create data source
	 */
	@PostMapping
	public ResponseEntity<Datasource> createDatasource(@RequestBody @Valid Datasource datasource) {
		Datasource created = datasourceService.createDatasource(datasource);
		return ResponseEntity.ok(created);
	}

	/**
	 * Update data source
	 */
	@PutMapping("/{id}")
	public ResponseEntity<Datasource> updateDatasource(@PathVariable(value = "id") Integer id,
			@RequestBody @Valid Datasource datasource) {
		Datasource updated = datasourceService.updateDatasource(id, datasource);
		return ResponseEntity.ok(updated);
	}

	/**
	 * Delete data source
	 */
	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse> deleteDatasource(@PathVariable(value = "id") Integer id) {
		datasourceService.deleteDatasource(id);
		return ResponseEntity.ok(ApiResponse.success("数据源删除成功"));
	}

	/**
	 * Test data source connection
	 */
	@PostMapping("/{id}/test")
	public ResponseEntity<ApiResponse> testConnection(@PathVariable(value = "id") Integer id) {
		boolean success = datasourceService.testConnection(id);
		ApiResponse response = success ? ApiResponse.success("连接测试成功") : ApiResponse.error("连接测试失败");
		return ResponseEntity.ok(response);
	}

	/**
	 * 获取数据源表的字段列表
	 */
	@GetMapping("/{id}/tables/{tableName}/columns")
	public ApiResponse<List<String>> getTableColumns(@PathVariable(value = "id") Integer id,
			@PathVariable(value = "tableName") String tableName) throws Exception {
		List<String> columns = datasourceService.getTableColumns(id, tableName);
		return ApiResponse.success("获取字段列表成功", columns);
	}

	/**
	 * 获取数据源的逻辑外键列表
	 */
	@GetMapping("/{id}/logical-relations")
	public ApiResponse<List<LogicalRelation>> getLogicalRelations(@PathVariable(value = "id") Integer datasourceId) {
		List<LogicalRelation> logicalRelations = datasourceService.getLogicalRelations(datasourceId);
		return ApiResponse.success("success get logical relations", logicalRelations);
	}

	/**
	 * 添加逻辑外键
	 */
	@PostMapping("/{id}/logical-relations")
	public ApiResponse<LogicalRelation> addLogicalRelation(@PathVariable(value = "id") Integer datasourceId,
			@Valid @RequestBody CreateLogicalRelationDTO dto) {
		LogicalRelation logicalRelation = LogicalRelation.builder()
				.sourceTableName(dto.getSourceTableName())
				.sourceColumnName(dto.getSourceColumnName())
				.targetTableName(dto.getTargetTableName())
				.targetColumnName(dto.getTargetColumnName())
				.relationType(dto.getRelationType())
				.description(dto.getDescription())
				.build();

		LogicalRelation created = datasourceService.addLogicalRelation(datasourceId, logicalRelation);
		return ApiResponse.success("success create logical relation", created);
	}

	/**
	 * 更新逻辑外键
	 */
	@PutMapping("/{id}/logical-relations/{relationId}")
	public ApiResponse<LogicalRelation> updateLogicalRelation(@PathVariable(value = "id") Integer datasourceId,
			@PathVariable(value = "relationId") Integer relationId, @RequestBody UpdateLogicalRelationDTO dto) {
		LogicalRelation logicalRelation = LogicalRelation.builder()
				.sourceTableName(dto.getSourceTableName())
				.sourceColumnName(dto.getSourceColumnName())
				.targetTableName(dto.getTargetTableName())
				.targetColumnName(dto.getTargetColumnName())
				.relationType(dto.getRelationType())
				.description(dto.getDescription())
				.build();

		LogicalRelation updated = datasourceService.updateLogicalRelation(datasourceId, relationId,
				logicalRelation);
		return ApiResponse.success("success update logical relation", updated);
	}

	/**
	 * 删除逻辑外键
	 */
	@DeleteMapping("/{id}/logical-relations/{relationId}")
	public ApiResponse<Void> deleteLogicalRelation(@PathVariable(value = "id") Integer datasourceId,
			@PathVariable(value = "relationId") Integer relationId) {
		datasourceService.deleteLogicalRelation(datasourceId, relationId);
		return ApiResponse.success("success delete logical relation");
	}

	/**
	 * 批量保存逻辑外键（替换现有的所有外键）
	 */
	@PutMapping("/{id}/logical-relations")
	public ApiResponse<List<LogicalRelation>> saveLogicalRelations(@PathVariable(value = "id") Integer datasourceId,
			@RequestBody List<LogicalRelation> logicalRelations) {
		List<LogicalRelation> saved = datasourceService.saveLogicalRelations(datasourceId, logicalRelations);
		return ApiResponse.success("success save logical relations", saved);
	}

}
