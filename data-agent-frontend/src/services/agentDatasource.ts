/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import axios from 'axios';
import { ApiResponse } from '@/services/common';
import { AgentDatasource } from '@/services/datasource';

interface ToggleDatasourceDto {
  datasourceId: number;
  isActive: boolean;
}

interface UpdateDatasourceTablesDto {
  datasourceId?: number;
  tables?: string[];
}

const BASE_URL_FUNC = (agentId: string) => `/api/agent/${agentId}/datasources`;

class AgentDatasourceService {
  /**
   * 初始化数据源Schema
   * @param agentId 智能体ID
   */
  async initSchema(agentId: string): Promise<ApiResponse<null>> {
    try {
      const response = await axios.post<ApiResponse<null>>(`${BASE_URL_FUNC(agentId)}/init`);
      return response.data;
    } catch (error) {
      throw new Error(`初始化Schema失败: ${error}`);
    }
  }

  /**
   * 获取智能体的数据源列表
   * @param agentId 智能体ID
   */
  async getAgentDatasource(agentId: number): Promise<AgentDatasource[]> {
    try {
      const response = await axios.get<ApiResponse<AgentDatasource[]>>(
        BASE_URL_FUNC(String(agentId)),
      );
      if (response.data.success) {
        return response.data.data || [];
      }
      throw new Error(response.data.message);
    } catch (error) {
      throw new Error(`获取数据源列表失败: ${error}`);
    }
  }

  /**
   * 获取当前激活的智能体
   * @param agentId 智能体ID
   */
  async getActiveAgentDatasource(agentId: number): Promise<AgentDatasource> {
    try {
      const response = await axios.get<ApiResponse<AgentDatasource>>(
        BASE_URL_FUNC(String(agentId)) + '/active',
      );
      if (response.data.success) {
        if (response.data.data === undefined) {
          throw new Error('后端错误');
        }
        return response.data.data;
      }
      throw new Error(response.data.message);
    } catch (error) {
      throw new Error(`获取数据源列表失败: ${error}`);
    }
  }

  /**
   * 为智能体添加数据源
   * @param agentId 智能体ID
   * @param datasourceId 数据源ID
   */
  async addDatasourceToAgent(
    agentId: string,
    datasourceId: number,
  ): Promise<ApiResponse<AgentDatasource>> {
    try {
      const response = await axios.post<ApiResponse<AgentDatasource>>(
        `${BASE_URL_FUNC(agentId)}/${datasourceId}`,
      );
      return response.data;
    } catch (error) {
      throw new Error(`添加数据源失败: ${error}`);
    }
  }

  /**
   * 从智能体移除数据源
   * @param agentId 智能体ID
   * @param datasourceId 数据源ID
   */
  async removeDatasourceFromAgent(
    agentId: string,
    datasourceId: number,
  ): Promise<ApiResponse<null>> {
    try {
      const response = await axios.delete<ApiResponse<null>>(
        `${BASE_URL_FUNC(agentId)}/${datasourceId}`,
      );
      return response.data;
    } catch (error) {
      throw new Error(`移除数据源失败: ${error}`);
    }
  }

  /**
   * 启用/禁用智能体的数据源
   * @param agentId 智能体ID
   * @param dto 切换参数
   */
  async toggleDatasourceForAgent(
    agentId: string,
    dto: ToggleDatasourceDto,
  ): Promise<ApiResponse<AgentDatasource>> {
    try {
      const response = await axios.put<ApiResponse<AgentDatasource>>(
        `${BASE_URL_FUNC(agentId)}/toggle`,
        dto,
      );
      return response.data;
    } catch (error: any) {
      // 根据不同错误类型提供具体提示
      if (error.response) {
        // 服务器返回了错误响应
        const status = error.response.status;
        const message = error.response.data?.message || error.response.statusText;

        switch (status) {
          case 400:
            throw new Error(`请求参数错误: ${message}`);
          case 404:
            throw new Error(`资源未找到: ${message}`);
          case 500:
            throw new Error(`服务器内部错误: ${message}`);
          default:
            throw new Error(`HTTP ${status} 错误: ${message}`);
        }
      } else if (error.request) {
        // 请求已发出但没有收到响应
        throw new Error('网络连接失败，请检查网络或服务器状态');
      } else {
        // 其他错误
        throw new Error(`请求失败: ${error.message}`);
      }
    }
  }

  /**
   * 更新数据源的表列表
   * @param agentId 智能体ID
   * @param dto 更新参数
   */
  async updateDatasourceTables(
    agentId: string,
    dto: UpdateDatasourceTablesDto,
  ): Promise<ApiResponse<null>> {
    try {
      const response = await axios.post<ApiResponse<null>>(`${BASE_URL_FUNC(agentId)}/tables`, dto);
      return response.data;
    } catch (error) {
      throw new Error(`更新数据源表列表失败: ${error}`);
    }
  }
}

export default new AgentDatasourceService();
