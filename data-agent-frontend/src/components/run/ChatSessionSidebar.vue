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
  <el-aside width="320px" style="background-color: white; border-right: 1px solid #e8e8e8">
    <!-- 顶部操作栏 -->
    <div class="sidebar-header">
      <div class="header-controls">
        <el-button type="primary" @click="goBack" circle>
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <el-avatar :src="getAvatarUrl(agent.avatar)" size="large" @error="handleImageError">{{ agent.name }}</el-avatar>
        <el-button type="danger" @click="clearAllSessions" circle>
          <el-icon><Delete /></el-icon>
        </el-button>
      </div>
      <div class="new-session-section">
        <el-button type="primary" @click="createNewSession" style="width: 100%">
          <el-icon><Plus /></el-icon>
          新建会话
        </el-button>
      </div>
    </div>

    <el-divider style="margin: 0" />

    <!-- Filter section -->
    <div class="filter-section">
      <el-input
        v-model="filterTitle"
        placeholder="搜索标题"
        clearable
        size="small"
        :prefix-icon="Search"
        @input="handleFilterChange"
      />
      <el-date-picker
        v-model="filterDateRange"
        type="daterange"
        range-separator="-"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        size="small"
        style="width: 100%; margin-top: 8px"
        @change="handleFilterChange"
        value-format="YYYY-MM-DD"
      />
      <div class="filter-info" v-if="totalCount > 0">
        共 {{ totalCount }} 条会话
      </div>
    </div>

    <!-- 会话列表 -->
    <div class="session-list">
      <div
        v-for="session in sessions"
        :key="session.id"
        :class="[
          'session-item',
          { active: handleGetCurrentSession()?.id === session.id, pinned: session.isPinned },
        ]"
        @click="handleSelectSession(session)"
      >
        <!-- Title row -->
        <div class="session-title-row">
          <span
            class="session-title"
            @dblclick="startEditSessionTitle(session)"
            v-if="!session.editing"
          >
            {{ session.title || '新会话' }}
          </span>
          <el-input
            v-else
            v-model="session.editingTitle"
            size="small"
            @blur="saveSessionTitle(session)"
            @keyup.enter="saveSessionTitle(session)"
            @keyup.esc="cancelEditSessionTitle(session)"
            ref="sessionTitleInputRef"
          />
        </div>
        <!-- Time and actions row -->
        <div class="session-footer">
          <div class="session-time">
            {{ formatTime(session.updateTime || session.createTime) }}
          </div>
          <div class="session-actions">
            <el-button type="text" size="small" @click.stop="startEditSessionTitle(session)">
              <el-icon><Edit /></el-icon>
            </el-button>
            <el-button type="text" size="small" @click.stop="togglePinSession(session)">
              <el-icon>
                <StarFilled v-if="session.isPinned" />
                <Star v-else />
              </el-icon>
            </el-button>
            <el-button type="text" size="small" @click.stop="deleteSession(session)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
      <!-- Empty state -->
      <div v-if="sessions.length === 0 && !isLoading" class="empty-state">
        暂无会话记录
      </div>
      <div v-if="isLoading" class="empty-state">
        加载中...
      </div>
    </div>

    <!-- Pagination -->
    <div class="pagination-section" v-if="totalPages > 1">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="totalCount"
        layout="prev, pager, next"
        small
        @current-change="handlePageChange"
      />
    </div>
  </el-aside>
</template>

<script lang="ts">
  import { defineComponent, PropType } from 'vue';
  import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue';
  import { useRouter, useRoute } from 'vue-router';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import ChatService, { type ChatSession, type ChatSessionPageQuery } from '../../services/chat';
  import { ArrowLeft, Plus, Delete, Star, StarFilled, Edit, Search } from '@element-plus/icons-vue';
  import { type Agent } from '../../services/agent';
  import { generateFallbackAvatar, getAvatarUrl } from '../../services/avatar';

  // 扩展ChatSession接口以包含编辑相关属性
  interface ExtendedChatSession extends ChatSession {
    editing?: boolean;
    editingTitle?: string;
  }

  interface SessionUpdateEvent {
    type: string;
    sessionId: string;
    title: string;
  }

  export default defineComponent({
    name: 'ChatSessionSidebar',
    components: {
      ArrowLeft,
      Plus,
      Delete,
      Star,
      StarFilled,
      Edit,
      Search,
    },
    props: {
      agent: {
        type: Object as PropType<Agent>,
        required: true,
      },
      handleSetCurrentSession: {
        type: Function as PropType<(session: ChatSession | null) => Promise<void>>,
        required: true,
      },
      handleGetCurrentSession: {
        type: Function as PropType<() => ChatSession | null>,
        required: true,
      },
      handleSelectSession: {
        type: Function as PropType<(session: ChatSession) => Promise<void>>,
        required: true,
      },
      handleDeleteSessionState: {
        type: Function as PropType<(sessionId: string) => void>,
        required: true,
      },
    },
    setup(props) {
      const sessions = ref<ExtendedChatSession[]>([]);
      const sessionEventSource = ref<EventSource | null>(null);
      let reconnectTimer: number | null = null;
      let isComponentActive = true;

      // Filter and pagination state
      const filterTitle = ref('');
      const filterDateRange = ref<[string, string] | null>(null);
      const currentPage = ref(1);
      const pageSize = ref(10);
      const totalCount = ref(0);
      const totalPages = ref(0);
      const isLoading = ref(false);
      let filterDebounceTimer: number | null = null;

      const router = useRouter();
      const route = useRoute();

      const formatTime = (time: Date | string | undefined) => {
        if (!time) return '';
        const date = new Date(time);
        return date.toLocaleString('zh-CN');
      };

      const clearReconnectTimer = () => {
        if (reconnectTimer) {
          window.clearTimeout(reconnectTimer);
          reconnectTimer = null;
        }
      };

      const handleTitleUpdate = (eventData: SessionUpdateEvent) => {
        if (!eventData?.sessionId) {
          return;
        }
        const target = sessions.value.find(session => session.id === eventData.sessionId);
        if (target) {
          target.title = eventData.title;
          target.editingTitle = eventData.title;
        }
        const current = props.handleGetCurrentSession();
        if (current && current.id === eventData.sessionId) {
          current.title = eventData.title;
        }
      };

      const connectSessionStream = () => {
        clearReconnectTimer();
        const currentAgentId = agentId.value;
        if (!currentAgentId) {
          return;
        }
        if (sessionEventSource.value) {
          sessionEventSource.value.close();
        }
        const source = new EventSource(`/api/agent/${currentAgentId}/sessions/stream`);
        source.addEventListener('title-updated', event => {
          try {
            const data = JSON.parse((event as MessageEvent<string>).data) as SessionUpdateEvent;
            handleTitleUpdate(data);
          } catch (error) {
            console.error('解析会话标题更新失败', error);
          }
        });
        source.onerror = error => {
          console.error('会话推送连接异常:', error);
          source.close();
          sessionEventSource.value = null;
          if (isComponentActive) {
            reconnectTimer = window.setTimeout(() => connectSessionStream(), 3000);
          }
        };
        sessionEventSource.value = source;
      };

      // 开始编辑会话标题
      const startEditSessionTitle = (session: ExtendedChatSession) => {
        session.editing = true;
        session.editingTitle = session.title || '新会话';
        nextTick(() => {
          const input = document.querySelector('.el-input__inner') as HTMLInputElement;
          if (input) {
            input.focus();
            input.select();
          }
        });
      };

      // 保存会话标题
      const saveSessionTitle = async (session: ExtendedChatSession) => {
        if (!session.editingTitle || session.editingTitle.trim() === '') {
          ElMessage.warning('会话标题不能为空');
          return;
        }

        const newTitle = session.editingTitle.trim();
        if (newTitle === session.title) {
          session.editing = false;
          return;
        }

        try {
          await ChatService.renameSession(session.id, newTitle);
          session.title = newTitle;
          session.editing = false;
          ElMessage.success('会话标题已更新');
        } catch (error) {
          ElMessage.error('更新会话标题失败');
          console.error('更新会话标题失败:', error);
        }
      };

      // 取消编辑会话标题
      const cancelEditSessionTitle = (session: ExtendedChatSession) => {
        session.editing = false;
      };

      // Computed properties
      const agentId = computed(() => route.params.id as string);

      // Load sessions from backend with pagination
      const loadSessionsPage = async (selectFirst = false) => {
        if (isLoading.value) return;
        isLoading.value = true;

        try {
          const query: ChatSessionPageQuery = {
            pageNum: currentPage.value,
            pageSize: pageSize.value,
          };

          // Add keyword filter
          if (filterTitle.value.trim()) {
            query.keyword = filterTitle.value.trim();
          }

          // Add date range filter
          if (filterDateRange.value && filterDateRange.value[0] && filterDateRange.value[1]) {
            query.startDate = filterDateRange.value[0];
            query.endDate = filterDateRange.value[1];
          }

          const response = await ChatService.getAgentSessionsPage(parseInt(agentId.value), query);

          if (response.success) {
            sessions.value = response.data || [];
            totalCount.value = response.total || 0;
            totalPages.value = response.totalPages || 0;

            // Select first session if requested
            if (selectFirst && sessions.value.length > 0) {
              await props.handleSelectSession(sessions.value[0]);
            }
          } else {
            ElMessage.error(response.message || '加载会话列表失败');
          }
        } catch (error) {
          ElMessage.error('加载会话列表失败');
          console.error('加载会话列表失败:', error);
        } finally {
          isLoading.value = false;
        }
      };

      // Handle filter change with debounce
      const handleFilterChange = () => {
        if (filterDebounceTimer) {
          window.clearTimeout(filterDebounceTimer);
        }
        filterDebounceTimer = window.setTimeout(() => {
          currentPage.value = 1; // Reset to first page when filter changes
          loadSessionsPage();
        }, 500);
      };

      // Handle page change
      const handlePageChange = (page: number) => {
        currentPage.value = page;
        loadSessionsPage();
      };

      // 方法
      const goBack = () => {
        router.push(`/agent/${agentId.value}`);
      };

      const createNewSession = async () => {
        try {
          const newSession = await ChatService.createSession(parseInt(agentId.value), '新会话');
          // Reload page to show new session
          currentPage.value = 1;
          await loadSessionsPage();
          await props.handleSelectSession(newSession);
          ElMessage.success('新会话创建成功');
        } catch (error) {
          ElMessage.error('创建会话失败');
          console.error('创建会话失败:', error);
        }
      };

      const togglePinSession = async (session: ChatSession) => {
        try {
          await ChatService.pinSession(session.id, !session.isPinned);
          session.isPinned = !session.isPinned;
          ElMessage.success(session.isPinned ? '会话已置顶' : '会话已取消置顶');
        } catch (error) {
          ElMessage.error('操作失败');
          console.error('置顶会话失败:', error);
        }
      };

      const deleteSession = async (session: ChatSession) => {
        try {
          await ElMessageBox.confirm('确定要删除这个会话吗？', '确认删除', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning',
          });
          await ChatService.deleteSession(session.id);
          props.handleDeleteSessionState(session.id);
          if (props.handleGetCurrentSession()?.id === session.id) {
            await props.handleSetCurrentSession(null);
          }
          // Reload current page
          await loadSessionsPage();
          ElMessage.success('会话删除成功');
        } catch (error) {
          if (error !== 'cancel') {
            ElMessage.error('删除会话失败');
            console.error('删除会话失败:', error);
          }
        }
      };

      // 图片加载失败处理
      const handleImageError = () => {
        console.debug('Avatar image load failed, using fallback');
        // Generate fallback avatar when image load fails (404 or other errors)
        props.agent.avatar = generateFallbackAvatar();
      };

      const clearAllSessions = async () => {
        try {
          await ElMessageBox.confirm('确定要清空所有会话吗？此操作不可恢复。', '确认清空', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning',
          });
          await ChatService.clearAgentSessions(parseInt(agentId.value));
          sessions.value.forEach((session: ChatSession) => {
            props.handleDeleteSessionState(session.id);
          });
          sessions.value = [];
          await props.handleSetCurrentSession(null);
          ElMessage.success('所有会话已清空');
        } catch (error) {
          if (error !== 'cancel') {
            ElMessage.error('清空会话失败');
            console.error('清空会话失败:', error);
          }
        }
      };

      // 生命周期
      onMounted(async () => {
        connectSessionStream();
        await loadSessionsPage(true); // Select first session on initial load
      });

      onUnmounted(() => {
        isComponentActive = false;
        clearReconnectTimer();
        if (sessionEventSource.value) {
          sessionEventSource.value.close();
          sessionEventSource.value = null;
        }
      });

      return {
        sessions,
        formatTime,
        goBack,
        createNewSession,
        togglePinSession,
        deleteSession,
        handleImageError,
        clearAllSessions,
        startEditSessionTitle,
        saveSessionTitle,
        cancelEditSessionTitle,
        getAvatarUrl,
        // Filter and pagination
        filterTitle,
        filterDateRange,
        currentPage,
        pageSize,
        totalCount,
        totalPages,
        isLoading,
        handleFilterChange,
        handlePageChange,
        Search,
      };
    },
  });
</script>

<style scoped>
  /* 左侧边栏样式 */
  .sidebar-header {
    padding: 20px;
  }

  .header-controls {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
  }

  /* Filter section */
  .filter-section {
    padding: 12px 20px;
    background: #fafafa;
    border-bottom: 1px solid #e8e8e8;
  }

  .filter-info {
    font-size: 12px;
    color: #909399;
    margin-top: 8px;
    text-align: right;
  }

  /* 会话列表样式 */
  .session-list {
    max-height: calc(100vh - 340px);
    overflow-y: auto;
    padding: 12px 20px;
  }

  .session-item {
    padding: 16px;
    border: 1px solid #e8e8e8;
    border-radius: 8px;
    margin-bottom: 12px;
    cursor: pointer;
    transition: all 0.3s ease;
    background: white;
  }

  .session-item:hover {
    border-color: #409eff;
    background-color: #f8fbff;
  }

  .session-item.active {
    border-color: #409eff;
    background-color: #ecf5ff;
  }

  .session-item.pinned {
    border-left: 4px solid #e6a23c;
  }

  .session-title-row {
    margin-bottom: 8px;
  }

  .session-title {
    font-weight: 600;
    font-size: 14px;
    color: #303133;
    display: block;
    word-break: break-all;
    line-height: 1.4;
  }

  .session-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .session-actions {
    display: flex;
    gap: 4px;
    flex-shrink: 0;
  }

  .session-time {
    font-size: 12px;
    color: #909399;
  }

  /* Empty state */
  .empty-state {
    text-align: center;
    color: #909399;
    font-size: 14px;
    padding: 40px 20px;
  }

  /* Pagination */
  .pagination-section {
    padding: 12px 20px;
    display: flex;
    justify-content: center;
    border-top: 1px solid #e8e8e8;
    background: #fafafa;
  }

  /* 响应式设计 */
  @media (max-width: 768px) {
    .el-aside {
      width: 250px !important;
    }
  }
</style>
