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

// 定义通用响应结构
export interface ApiResponse<T = unknown> {
  success: boolean;
  message: string;
  data?: T;
}

export interface PageResponse<T = unknown> {
  success: boolean;
  message: string;
  data: T;
  total: number;
  pageNum: number;
  pageSize: number;
  totalPages: number;
}

// Base page query interface
export interface BasePageQuery {
  pageNum: number;
  pageSize: number;
}

// Agent page query interface
export interface AgentPageQuery extends BasePageQuery {
  keyword?: string;
  status?: string;
}

// Business knowledge page query interface
export interface BusinessKnowledgePageQuery extends BasePageQuery {
  agentId: number;
  keyword?: string;
  embeddingStatus?: string;
}

// Semantic model page query interface
export interface SemanticModelPageQuery extends BasePageQuery {
  agentId?: number;
  keyword?: string;
  status?: number;
  tableName?: string;
}

// Datasource page query interface
export interface DatasourcePageQuery extends BasePageQuery {
  keyword?: string;
  type?: string;
  status?: string;
}
