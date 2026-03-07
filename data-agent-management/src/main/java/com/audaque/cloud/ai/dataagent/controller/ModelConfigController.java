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

import com.audaque.cloud.ai.dataagent.enums.ModelType;
import com.audaque.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.audaque.cloud.ai.dataagent.service.aimodelconfig.ModelConfigDataService;
import com.audaque.cloud.ai.dataagent.service.aimodelconfig.ModelConfigOpsService;
import com.audaque.cloud.ai.dataagent.service.vectorstore.VectorDimensionService;
import com.audaque.cloud.ai.dataagent.vo.ApiResponse;
import com.audaque.cloud.ai.dataagent.vo.ModelCheckVo;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@RestController
@RequestMapping("/api/model-config")
public class ModelConfigController {

	private final ModelConfigDataService modelConfigDataService;

	private final ModelConfigOpsService modelConfigOpsService;

	private final VectorDimensionService vectorDimensionService;

	// 1. 获取列表
	@GetMapping("/list")
	public ApiResponse<List<ModelConfigDTO>> list() {
		try {
			return ApiResponse.success("获取模型配置列表成功", modelConfigDataService.listConfigs());
		}
		catch (Exception e) {
			return ApiResponse.error("获取模型配置列表失败: " + e.getMessage());
		}
	}

	// 2. 新增配置
	@PostMapping("/add")
	public ApiResponse<String> add(@Valid @RequestBody ModelConfigDTO config) {
		try {
			modelConfigDataService.addConfig(config);
			return ApiResponse.success("配置已保存");
		}
		catch (Exception e) {
			return ApiResponse.error("保存失败: " + e.getMessage());
		}
	}

	// 3. 修改配置
	@PutMapping("/update")
	public ApiResponse<String> update(@Valid @RequestBody ModelConfigDTO config) {
		try {
			modelConfigOpsService.updateAndRefresh(config);
			return ApiResponse.success("配置已更新");
		}
		catch (Exception e) {
			return ApiResponse.error("更新失败: " + e.getMessage());
		}
	}

	// 4. 删除配置
	@DeleteMapping("/{id}")
	public ApiResponse<String> delete(@PathVariable Integer id) {
		try {
			modelConfigDataService.deleteConfig(id);
			return ApiResponse.success("配置已删除");
		}
		catch (Exception e) {
			return ApiResponse.error("删除失败: " + e.getMessage());
		}
	}

	// 5. 启用/切换配置
	@PostMapping("/activate/{id}")
	public ApiResponse<String> activate(@PathVariable Integer id) {
		try {
			modelConfigOpsService.activateConfig(id);
			return ApiResponse.success("模型切换成功！");
		}
		catch (Exception e) {
			return ApiResponse.error("切换失败，请检查配置是否正确: " + e.getMessage());
		}
	}

	/**
	 * 5.1 强制启用/切换配置（用于切换不同维度的 Embedding 模型）
	 * 会删除现有的 Milvus collection 并重新创建，所有向量数据将丢失
	 */
	@PostMapping("/force-activate/{id}")
	public ApiResponse<String> forceActivate(@PathVariable Integer id) {
		try {
			modelConfigOpsService.forceActivateConfig(id);
			return ApiResponse.success("模型强制切换成功！向量库已删除，维度配置已更新。请重启应用以创建新的向量库。");
		}
		catch (Exception e) {
			return ApiResponse.error("强制切换失败: " + e.getMessage());
		}
	}

	/**
	 * 6. 连通性测试 接收前端表单里的配置参数，尝试发起一次真实调用
	 */
	@PostMapping("/test")
	public ApiResponse<String> testConnection(@Valid @RequestBody ModelConfigDTO config) {
		try {
			modelConfigOpsService.testConnection(config);
			return ApiResponse.success("连接测试成功！模型可用。");
		}
		catch (Exception e) {
			// 捕获具体的错误信息（如 401 Invalid Key, 404 Not Found 等）返回给前端
			return ApiResponse.error("连接测试失败: " + e.getMessage());
		}
	}

	/**
	 * 7. 检查模型配置是否就绪（聊天模型和嵌入模型都需要配置）
	 */
	@GetMapping("/check-ready")
	public ApiResponse<ModelCheckVo> checkReady() {
		// 检查聊天模型是否已配置且启用
		ModelConfigDTO chatModel = modelConfigDataService.getActiveConfigByType(ModelType.CHAT);
		// 检查嵌入模型是否已配置且启用
		ModelConfigDTO embeddingModel = modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING);

		boolean chatModelReady = chatModel != null;
		boolean embeddingModelReady = embeddingModel != null;
		boolean ready = chatModelReady && embeddingModelReady;

		return ApiResponse.success("模型配置检查完成",
				ModelCheckVo.builder()
					.chatModelReady(chatModelReady)
					.embeddingModelReady(embeddingModelReady)
					.ready(ready)
					.build());
	}

	/**
	 * 8. 获取向量库维度信息
	 * 用于检查当前 Embedding 模型与向量库的维度兼容性
	 */
	@GetMapping("/vector-dimension")
	public ApiResponse<Map<String, Object>> getVectorDimension() {
		try {
			int collectionDimension = vectorDimensionService.getCollectionDimension();

			Map<String, Object> result = new HashMap<>();
			result.put("collectionDimension", collectionDimension);
			result.put("message", "当前向量库 collection 的向量维度为: " + collectionDimension);

			return ApiResponse.success("获取向量库维度成功", result);
		} catch (Exception e) {
			return ApiResponse.error("获取向量库维度失败: " + e.getMessage());
		}
	}

}
