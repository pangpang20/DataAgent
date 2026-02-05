<!--
 * Copyright 2025 the original author or authors.
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
-->
<template>
  <BaseLayout>
    <div class="agent-list-page">
      <!-- 主内容区域 -->
      <main class="main-content">
        <!-- 内容头部 -->
        <div class="content-header">
          <div class="header-info">
            <h1 class="content-title">智能体管理中心</h1>
            <p class="content-subtitle">创建和管理您的AI智能体，让数据分析更智能</p>
          </div>
          <div class="header-stats">
            <div class="stat-item">
              <div class="stat-number">{{ agents.length }}</div>
              <div class="stat-label">总数量</div>
            </div>
            <div class="stat-item">
              <div class="stat-number">{{ publishedCount }}</div>
              <div class="stat-label">已发布</div>
            </div>
            <div class="stat-item">
              <div class="stat-number">{{ draftCount }}</div>
              <div class="stat-label">草稿</div>
            </div>
            <div class="stat-item">
              <div class="stat-number">{{ offlineCount }}</div>
              <div class="stat-label">已下线</div>
            </div>
          </div>
        </div>

        <!-- 过滤和搜索区域 -->
        <div class="filter-section">
          <el-card>
            <div class="filter-content">
              <div class="filter-tabs-row">
                <div class="filter-tabs">
                  <el-radio-group v-model="activeFilter" size="large">
                    <el-radio-button value="all">
                      <el-icon><Grid /></el-icon>
                      <span>全部智能体</span>
                      <span class="tab-count">{{ total }}</span>
                    </el-radio-button>
                    <el-radio-button value="published">
                      <el-icon><Check /></el-icon>
                      <span>已发布</span>
                      <span class="tab-count">{{ publishedCount }}</span>
                    </el-radio-button>
                    <el-radio-button value="draft">
                      <el-icon><Edit /></el-icon>
                      <span>草稿</span>
                      <span class="tab-count">{{ draftCount }}</span>
                    </el-radio-button>
                    <el-radio-button value="offline">
                      <el-icon><VideoPause /></el-icon>
                      <span>已下线</span>
                      <span class="tab-count">{{ offlineCount }}</span>
                    </el-radio-button>
                  </el-radio-group>
                </div>

                <div class="search-and-actions">
                  <el-input
                    v-model="searchKeyword"
                    placeholder="搜索智能体名称、ID或描述..."
                    size="large"
                    :prefix-icon="Search"
                    clearable
                    style="width: 350px"
                    @change="handleSearchChange"
                  />
                  <div class="action-buttons">
                    <el-button :icon="Refresh" @click="loadAgents" size="large">刷新</el-button>
                    <el-button type="primary" :icon="Plus" @click="goToCreateAgent" size="large">
                      创建智能体
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </el-card>
        </div>

        <!-- 智能体网格 -->
        <div class="agents-grid" v-if="!loading">
          <el-row :gutter="20">
            <el-col
              v-for="agent in paginatedAgents"
              :key="agent.id"
              :xs="24"
              :sm="12"
              :md="8"
              :lg="6"
            >
              <el-card
                class="agent-card"
                :body-style="{ padding: '20px' }"
                @click="enterAgent(agent.id)"
              >
                <div class="agent-content">
                  <!-- 删除按钮 -->
                  <div class="delete-button" @click.stop="handleDeleteAgent(agent)">
                    <el-icon><Delete /></el-icon>
                  </div>

                  <!-- 头像区域 -->
                  <div class="agent-avatar">
                    <el-avatar :size="48" :src="getAvatarUrl(agent.avatar)" @error="handleImageError(agent)">
                      {{ agent.name }}
                    </el-avatar>
                  </div>

                  <!-- 信息区域 -->
                  <div class="agent-info">
                    <h3 class="agent-name">{{ agent.name }}</h3>
                    <p class="agent-description">{{ agent.description }}</p>
                    <div class="agent-meta">
                      <span class="agent-id">ID: {{ agent.id }}</span>
                      <span class="agent-time">{{ formatTime(agent.updateTime) }}</span>
                    </div>
                  </div>

                  <!-- 状态标签 -->
                  <div class="agent-status">
                    <el-tag :type="getStatusTagType(agent.status)" size="small" effect="light">
                      {{ getStatusText(agent.status) }}
                    </el-tag>
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>
          
          <!-- 分页控件 -->
          <div class="pagination-container" v-if="total > 0">
            <el-pagination
              v-model:current-page="currentPage"
              v-model:page-size="pageSize"
              :page-sizes="[12, 24, 36, 48]"
              :total="total"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="handleSizeChange"
              @current-change="handleCurrentChange"
              background
            />
          </div>
        </div>

        <!-- 加载状态 -->
        <div v-if="loading" class="loading-state">
          <el-skeleton :rows="6" animated />
        </div>

        <!-- 空状态 -->
        <div v-if="!loading && filteredAgents.length === 0" class="empty-state">
          <el-empty description="暂无智能体">
            <template #image>
              <el-icon size="60"><Grid /></el-icon>
            </template>
            <el-button type="primary" :icon="Plus" @click="goToCreateAgent">创建智能体</el-button>
          </el-empty>
        </div>
      </main>
    </div>
  </BaseLayout>
</template>

<script lang="ts">
  import { defineComponent, ref, computed, onMounted } from 'vue';
  import { useRouter } from 'vue-router';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import {
    Grid,
    Check,
    Edit,
    VideoPause,
    Delete,
    Search,
    Refresh,
    Plus,
  } from '@element-plus/icons-vue';
  import BaseLayout from '@/layouts/BaseLayout.vue';
  import agentService from '@/services/agent';
  import type { Agent } from '@/services/agent';
  import { generateFallbackAvatar, getAvatarUrl } from '@/services/avatar';

  export default defineComponent({
    name: 'AgentList',
    components: {
      BaseLayout,
      Grid,
      Check,
      Edit,
      VideoPause,
      Delete,
    },
    setup() {
      const router = useRouter();
      const loading = ref(true);
      const activeFilter = ref('all');
      const searchKeyword = ref('');
      const agents = ref<Agent[]>([]);
      
      // 分页相关数据
      const currentPage = ref(1);
      const pageSize = ref(12);
      const total = ref(0);
      
      // 状态统计
      const statusStats = ref({
        all: 0,
        published: 0,
        draft: 0,
        offline: 0
      });

      // 计算属性
      const publishedCount = computed(() => statusStats.value.published);
      const draftCount = computed(() => statusStats.value.draft);
      const offlineCount = computed(() => statusStats.value.offline);

      const filteredAgents = computed(() => {
        // 后端已处理过滤，这里直接返回所有数据
        return agents.value;
      });

      // 直接使用后端分页数据，不再进行前端二次分页
      const paginatedAgents = computed(() => agents.value);

      const setFilter = (filter: string) => {
        activeFilter.value = filter;
        currentPage.value = 1; // 重置到第一页
        loadAgents();
      };

      const handleCurrentChange = (val: number) => {
        currentPage.value = val;
        loadAgents();
      };

      const handleSizeChange = (val: number) => {
        pageSize.value = val;
        currentPage.value = 1; // 重置到第一页
        loadAgents();
      };

      const loadAgents = async () => {
        loading.value = true;
        try {
          const query = {
            pageNum: currentPage.value,
            pageSize: pageSize.value,
            keyword: searchKeyword.value.trim() || undefined,
            status: activeFilter.value === 'all' ? undefined : activeFilter.value,
          };
          
          const response = await agentService.queryPage(query);
          agents.value = response.data || [];
          total.value = response.total || 0;
          
          // 更新状态统计（请求全部数据的统计信息）
          await updateStatusStats();
        } catch (error) {
          ElMessage.error('获取智能体列表失败，请检查网络！');
          agents.value = [];
          total.value = 0;
          // 重置状态统计
          statusStats.value = {
            all: 0,
            published: 0,
            draft: 0,
            offline: 0
          };
        } finally {
          loading.value = false;
        }
      };

      // 更新状态统计数据
      const updateStatusStats = async () => {
        try {
          // 请求不带状态过滤的统计数据
          const statsQuery = {
            pageNum: 1,
            pageSize: 100, // 使用合理的页大小获取统计数据
            keyword: searchKeyword.value.trim() || undefined
          };
          
          const response = await agentService.queryPage(statsQuery);
          const allAgents = response.data || [];
          
          statusStats.value = {
            all: response.total || 0,
            published: allAgents.filter((a: Agent) => a.status === 'published').length,
            draft: allAgents.filter((a: Agent) => a.status === 'draft').length,
            offline: allAgents.filter((a: Agent) => a.status === 'offline').length
          };
        } catch (error) {
          console.error('获取状态统计失败:', error);
          // 保持现有统计或重置为0
        }
      };

      const enterAgent = (agentId: string) => {
        router.push(`/agent/${agentId}`);
      };

      const getStatusText = (status: string) => {
        const statusMap: Record<string, string> = {
          published: '已发布',
          draft: '草稿',
          offline: '已下线',
        };
        return statusMap[status] || status;
      };

      const getStatusTagType = (status: string) => {
        const typeMap: Record<string, 'success' | 'warning' | 'info'> = {
          published: 'success',
          draft: 'warning',
          offline: 'info',
        };
        return typeMap[status] || 'info';
      };

      const formatTime = (time: string) => {
        if (!time) return '';
        return time.replace(/\//g, '/');
      };

      const goToCreateAgent = () => {
        router.push('/agent/create');
      };

      // 监听搜索关键词变化
      const handleSearchChange = () => {
        currentPage.value = 1; // 重置到第一页
        loadAgents();
      };

      // 图片加载失败处理
      const handleImageError = (agent: Agent) => {
        console.debug('Avatar image load failed, using fallback for:', agent.name);
        // Generate fallback avatar when image load fails (404 or other errors)
        agent.avatar = generateFallbackAvatar();
      };

      // 删除智能体
      const handleDeleteAgent = async (agent: Agent) => {
        try {
          await ElMessageBox.confirm(
            `确定要删除智能体 "${agent.name}" 吗？此操作不可恢复。`,
            '删除确认',
            {
              confirmButtonText: '确定删除',
              cancelButtonText: '取消',
              type: 'warning',
            },
          );

          const success = await agentService.delete(agent.id!);
          if (success) {
            ElMessage.success('智能体删除成功');
            // 重新加载数据以获取最新的总数和状态统计
            await loadAgents();
          } else {
            ElMessage.error('智能体删除失败');
          }
        } catch (error) {
          // 用户取消了删除操作
          console.log('删除操作已取消');
        }
      };

      onMounted(() => {
        loadAgents();
      });

      return {
        loading,
        activeFilter,
        searchKeyword,
        agents,
        filteredAgents,
        paginatedAgents,
        publishedCount,
        draftCount,
        offlineCount,
        total,
        currentPage,
        pageSize,
        statusStats,
        setFilter,
        loadAgents,
        enterAgent,
        getStatusText,
        getStatusTagType,
        formatTime,
        goToCreateAgent,
        handleDeleteAgent,
        handleImageError,
        getAvatarUrl,
        handleCurrentChange,
        handleSizeChange,
        handleSearchChange,
        Search,
        Refresh,
        Plus,
      };
    },
  });
</script>

<style scoped>
  .agent-list-page {
    min-height: 100vh;
    background: #f8fafc;
    font-family:
      -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  }

  /* 主内容区域 */
  .main-content {
    width: 100%;
    margin: 0 auto;
    padding: 2rem;
  }

  /* 内容头部 */
  .content-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 2rem;
  }

  .header-info h1 {
    font-size: 2rem;
    font-weight: 600;
    color: #1f2937;
    margin: 0 0 0.5rem 0;
  }

  .header-info p {
    color: #6b7280;
    margin: 0;
    font-size: 1.1rem;
  }

  .header-stats {
    display: flex;
    gap: 2rem;
  }

  .stat-item {
    text-align: center;
  }

  .stat-number {
    font-size: 2rem;
    font-weight: 700;
    color: #3b82f6;
    line-height: 1;
  }

  .stat-label {
    font-size: 0.875rem;
    color: #6b7280;
    margin-top: 0.25rem;
  }

  /* 过滤和搜索区域 */
  .filter-section {
    margin-bottom: 2rem;
  }

  .filter-content {
    padding: 20px;
  }

  .filter-tabs-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .filter-tabs {
    display: flex;
  }

  .search-and-actions {
    display: flex;
    gap: 1rem;
    align-items: center;
  }

  .action-buttons {
    display: flex;
    gap: 0.5rem;
  }

  .tab-count {
    background: #f3f4f6;
    color: #6b7280;
    padding: 0.25rem 0.5rem;
    border-radius: 4px;
    font-size: 0.75rem;
    font-weight: 600;
    margin-left: 0.5rem;
  }

  /* 智能体网格 */
  .agents-grid {
    margin-bottom: 2rem;
  }

  .pagination-container {
    display: flex;
    justify-content: center;
    margin-top: 2rem;
    padding: 1rem 0;
  }

  .agent-card {
    cursor: pointer;
    transition: all 0.2s ease;
    border-radius: 12px;
  }

  .agent-card:hover {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    transform: translateY(-2px);
  }

  .agent-content {
    position: relative;
  }

  .agent-avatar {
    display: flex;
    justify-content: center;
    margin-bottom: 1rem;
  }

  .agent-info {
    text-align: center;
    margin-bottom: 1rem;
  }

  .agent-name {
    font-size: 1.125rem;
    font-weight: 600;
    color: #1f2937;
    margin: 0 0 0.5rem 0;
  }

  .agent-description {
    color: #6b7280;
    font-size: 0.875rem;
    line-height: 1.5;
    margin: 0 0 0.75rem 0;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .agent-meta {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 0.75rem;
    color: #9ca3af;
  }

  .agent-status {
    position: absolute;
    top: 1rem;
    right: 1rem;
  }

  /* 删除按钮 */
  .delete-button {
    position: absolute;
    top: 1rem;
    left: 1rem;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: rgba(239, 68, 68, 0.9);
    color: white;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    opacity: 0;
    transition: all 0.2s ease;
    z-index: 10;
  }

  .delete-button:hover {
    background: rgba(220, 38, 38, 0.9);
    transform: scale(1.1);
  }

  .agent-card:hover .delete-button {
    opacity: 1;
  }

  /* 加载状态 */
  .loading-state {
    padding: 4rem 2rem;
  }

  /* 空状态 */
  .empty-state {
    padding: 4rem 2rem;
  }

  /* 响应式设计 */
  @media (max-width: 768px) {
    .main-content {
      padding: 1rem;
    }

    .content-header {
      flex-direction: column;
      align-items: flex-start;
      gap: 1rem;
    }

    .header-stats {
      gap: 1rem;
    }

    .filter-tabs-row {
      flex-direction: column;
      align-items: stretch;
      gap: 1rem;
    }

    .filter-tabs {
      justify-content: center;
    }

    .search-and-actions {
      flex-direction: column;
      gap: 1rem;
    }

    .search-and-actions .el-input {
      width: 100% !important;
    }

    .action-buttons {
      width: 100%;
    }

    .action-buttons .el-button {
      flex: 1;
    }
  }
</style>
