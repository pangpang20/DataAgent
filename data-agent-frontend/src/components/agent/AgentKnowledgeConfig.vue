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
  <div style="padding: 20px">
    <div style="margin-bottom: 20px">
      <h2>智能体知识库</h2>
      <p style="color: #909399; font-size: 14px; margin-top: 5px">
        管理用于增强智能体能力的知识源。
      </p>
    </div>
    <el-divider />

    <div style="margin-bottom: 30px">
      <el-row style="display: flex; justify-content: space-between; align-items: center">
        <el-col :span="10">
          <h3 style="display: inline-block; margin-right: 20px">知识列表</h3>
          <!-- 批量操作按钮 -->
          <el-button
            @click="handleBatchDelete"
            size="default"
            type="danger"
            plain
            :icon="Delete"
            :disabled="selectedKnowledge.length === 0"
          >
            批量删除 ({{ selectedKnowledge.length }})
          </el-button>
          <el-button
            @click="handleBatchRecall(true)"
            size="default"
            type="success"
            plain
            :disabled="selectedKnowledge.length === 0"
          >
            批量召回 ({{ selectedKnowledge.length }})
          </el-button>
          <el-button
            @click="handleBatchRecall(false)"
            size="default"
            type="warning"
            plain
            :disabled="selectedKnowledge.length === 0"
          >
            批量取消召回 ({{ selectedKnowledge.length }})
          </el-button>
        </el-col>
        <el-col :span="14" style="text-align: right">
          <el-input
            v-model="queryParams.title"
            placeholder="请输入知识标题搜索"
            style="width: 400px; margin-right: 10px"
            clearable
            @clear="handleSearch"
            @keyup.enter="handleSearch"
            @input="handleSearchInput"
            size="large"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button
            @click="toggleFilter"
            size="large"
            :type="filterVisible ? 'primary' : ''"
            round
            :icon="FilterIcon"
          >
            筛选
          </el-button>
          <el-button @click="openCreateDialog" size="large" type="primary" round :icon="Plus">
            添加知识
          </el-button>
        </el-col>
      </el-row>
    </div>

    <!-- 筛选面板 -->
    <el-collapse-transition>
      <div v-show="filterVisible" style="margin-bottom: 20px">
        <el-card shadow="never">
          <el-form :inline="true" :model="queryParams">
            <el-form-item label="知识类型">
              <el-select
                v-model="queryParams.type"
                placeholder="全部类型"
                clearable
                @change="handleSearch"
                style="width: 150px"
              >
                <el-option label="文档" value="DOCUMENT" />
                <el-option label="问答对" value="QA" />
                <el-option label="常见问题" value="FAQ" />
              </el-select>
            </el-form-item>
            <el-form-item label="处理状态">
              <el-select
                v-model="queryParams.embeddingStatus"
                placeholder="全部状态"
                clearable
                @change="handleSearch"
                style="width: 150px"
              >
                <el-option label="COMPLETED" value="COMPLETED" />
                <el-option label="PROCESSING" value="PROCESSING" />
                <el-option label="FAILED" value="FAILED" />
                <el-option label="PENDING" value="PENDING" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button @click="clearFilters" :icon="RefreshLeft">清空筛选</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </div>
    </el-collapse-transition>

    <!-- 表格区域 -->
    <el-table 
      :data="knowledgeList" 
      style="width: 100%" 
      border 
      v-loading="loading"
      :header-cell-style="{ textAlign: 'center', fontWeight: 'bold' }"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="50px" align="center" />
      <el-table-column prop="id" label="ID" min-width="50px" align="center" />
      <el-table-column prop="title" label="标题" min-width="250px" />
      <el-table-column prop="type" label="类型" min-width="50px" align="center">
        <template #default="scope">
          <span v-if="scope.row.type === 'DOCUMENT'">文档</span>
          <span v-else-if="scope.row.type === 'QA'">问答对</span>
          <span v-else-if="scope.row.type === 'FAQ'">常见问题</span>
          <span v-else>{{ scope.row.type }}</span>
        </template>
      </el-table-column>
      <el-table-column label="向量化状态" min-width="80px" align="center">
        <template #default="scope">
          <el-tag v-if="scope.row.embeddingStatus === 'COMPLETED'" type="success" round>
            {{ scope.row.embeddingStatus }}
          </el-tag>
          <el-tag v-else-if="scope.row.embeddingStatus === 'PROCESSING'" type="primary" round>
            {{ scope.row.embeddingStatus }}
          </el-tag>
          <el-tag v-else-if="scope.row.embeddingStatus === 'FAILED'" type="danger" round>
            <el-tooltip v-if="scope.row.errorMsg" :content="scope.row.errorMsg" placement="top">
              <span style="display: flex; align-items: center">
                <el-icon style="margin-right: 4px"><Warning /></el-icon>
                {{ scope.row.embeddingStatus }}
              </span>
            </el-tooltip>
            <span v-else>{{ scope.row.embeddingStatus }}</span>
          </el-tag>
          <el-tag v-else type="info" round>
            {{ scope.row.embeddingStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="召回状态" min-width="60px" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.isRecall ? 'success' : 'info'" round>
            {{ scope.row.isRecall ? '已召回' : '未召回' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="180px" align="center">
        <template #default="scope">
          <el-button @click="editKnowledge(scope.row)" size="small" type="primary" round plain>
            管理
          </el-button>
          <el-button
            v-if="scope.row.embeddingStatus === 'FAILED'"
            @click="handleRetry(scope.row)"
            size="small"
            type="info"
            round
            plain
          >
            重试
          </el-button>
          <el-button
            v-if="scope.row.isRecall"
            @click="toggleStatus(scope.row)"
            size="small"
            type="warning"
            round
            plain
          >
            取消召回
          </el-button>
          <el-button
            v-else
            @click="toggleStatus(scope.row)"
            size="small"
            type="success"
            round
            plain
          >
            召回
          </el-button>
          <el-button @click="deleteKnowledge(scope.row)" size="small" type="danger" round plain>
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页组件 -->
    <div style="margin-top: 20px; display: flex; justify-content: flex-end">
      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>

  <!-- 添加/编辑知识弹窗 -->
  <el-dialog
    v-model="dialogVisible"
    :title="isEdit ? '编辑知识' : '添加新知识'"
    width="800"
    :close-on-click-modal="false"
  >
    <el-form :model="knowledgeForm" label-width="100px" ref="knowledgeFormRef">
      <!-- 知识类型 -->
      <el-form-item label="知识类型" prop="type" required>
        <el-select
          v-model="knowledgeForm.type"
          placeholder="请选择知识类型"
          @change="handleTypeChange"
          :disabled="isEdit"
          style="width: 100%"
        >
          <el-option label="文档 (文件上传)" value="DOCUMENT" />
          <el-option label="问答对 (Q&A)" value="QA" />
          <el-option label="常见问题 (FAQ)" value="FAQ" />
        </el-select>
      </el-form-item>

      <!-- 知识类型说明 -->
      <el-form-item v-if="knowledgeForm.type === 'QA'">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px">
          <template #title>
            <div style="line-height: 1.6">
              请录入具体的'分析需求'作为问题,并在答案中写出详细的'思考步骤'与'数据查找逻辑'(而非直接给结果),以此教会
              AI 如何拆解任务。
            </div>
          </template>
        </el-alert>
      </el-form-item>

      <el-form-item v-if="knowledgeForm.type === 'FAQ'">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px">
          <template #title>
            <div style="line-height: 1.6">
              请针对特定的'业务术语'、'指标口径'或'常见歧义'进行提问和定义(例如:'什么是有效日活'),以此统一
              AI 的判断标准。
            </div>
          </template>
        </el-alert>
      </el-form-item>

      <el-form-item v-if="knowledgeForm.type === 'DOCUMENT'">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px">
          <template #title>
            <div style="line-height: 1.6">
              请上传完整的'数据库表结构'、'码表映射字典'或'业务背景说明',供 AI
              在分析时检索字段含义和数据关系。
            </div>
          </template>
        </el-alert>
      </el-form-item>

      <!-- 知识标题 -->
      <el-form-item label="知识标题" prop="title" required>
        <el-input v-model="knowledgeForm.title" placeholder="为这份知识起一个易于识别的名称" />
      </el-form-item>

      <!-- 文本切割方式选择 -->
      <el-form-item 
        v-if="knowledgeForm.type === 'DOCUMENT'" 
        label="文本切割方式" 
        prop="splitterType"
      >
        <el-select
          v-model="knowledgeForm.splitterType"
          placeholder="请选择文本切割方式"
          style="width: 100%"
        >
          <el-option label="Token 切割（推荐）" value="token" />
          <el-option label="递归切割" value="recursive" />
        </el-select>
        <div style="margin-top: 5px; color: #909399; font-size: 12px; line-height: 1.4">
          <strong>说明：</strong><br>
          • <strong>Token 切割</strong>：按照 token 数量进行切割，适合大多数文档<br>
          • <strong>递归切割</strong>：按照段落结构进行切割，适合结构化文档
        </div>
      </el-form-item>

      <!-- 文件上传区域 -->
      <el-form-item v-if="knowledgeForm.type === 'DOCUMENT'" label="上传文件" required>
        <div v-if="!isEdit" style="width: 100%">
          <el-upload
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-exceed="handleFileExceed"
            :on-remove="handleFileRemove"
            :file-list="fileList"
            :accept="'.pdf,.doc,.docx,.txt,.md'"
            drag
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              拖拽文件到此处或
              <em>点击选择文件</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">支持 PDF, DOC, DOCX, TXT, MD 格式，仅保留最后一个上传的文件</div>
              <div v-if="fileList.length > 0" class="el-upload__tip" style="color: #409eff">
                当前文件: {{ fileList[0].name }} ({{ formatFileSize(fileList[0].size) }})
              </div>
            </template>
          </el-upload>
        </div>
        <div v-else>
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="文档类型知识不支持修改文件内容，如需修改请删除后重新创建"
          />
        </div>
      </el-form-item>

      <!-- Q&A / FAQ 输入区域 -->
      <template v-if="knowledgeForm.type === 'QA' || knowledgeForm.type === 'FAQ'">
        <el-form-item label="问题" prop="question" required>
          <el-input
            v-model="knowledgeForm.question"
            type="textarea"
            :rows="2"
            placeholder="输入用户可能会问的问题..."
          />
        </el-form-item>
        <el-form-item label="答案" prop="answer" required>
          <el-input
            v-model="knowledgeForm.answer"
            type="textarea"
            :rows="5"
            placeholder="输入标准答案..."
          />
        </el-form-item>
      </template>
    </el-form>

    <template #footer>
      <div style="text-align: right">
        <el-button @click="closeDialog">取消</el-button>
        <el-button type="primary" @click="saveKnowledge" :loading="saveLoading">
          {{ isEdit ? '更新' : '添加并处理' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script lang="ts">
  import { defineComponent, ref, onMounted, Ref, reactive } from 'vue';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import {
    Plus,
    Search,
    Filter as FilterIcon,
    RefreshLeft,
    UploadFilled,
    Warning,
    Delete,
  } from '@element-plus/icons-vue';
  import axios from 'axios';
  import agentKnowledgeService, {
    AgentKnowledge,
    AgentKnowledgeQueryDTO,
  } from '@/services/agentKnowledge';

  export default defineComponent({
    name: 'AgentKnowledgeConfig',
    components: {
      Search,
      Warning,
      UploadFilled,
    },
    props: {
      agentId: {
        type: Number,
        required: true,
      },
    },
    setup(props) {
      const knowledgeList: Ref<AgentKnowledge[]> = ref([]);
      const total: Ref<number> = ref(0);
      const loading: Ref<boolean> = ref(false);
      const dialogVisible: Ref<boolean> = ref(false);
      const isEdit: Ref<boolean> = ref(false);
      const saveLoading: Ref<boolean> = ref(false);
      const currentEditId: Ref<number | null> = ref(null);
      const fileList: Ref<{ name: string; size: number; raw: File }[]> = ref([]);
      const filterVisible: Ref<boolean> = ref(false);
      const selectedKnowledge: Ref<AgentKnowledge[]> = ref([]);

      // 查询参数
      const queryParams = reactive<AgentKnowledgeQueryDTO>({
        agentId: props.agentId,
        title: '',
        type: '',
        embeddingStatus: '',
        pageNum: 1,
        pageSize: 10,
      });

      // 表单数据
      const knowledgeForm: Ref<
        AgentKnowledge & { question?: string; answer?: string; file?: File }
      > = ref({
        agentId: props.agentId,
        title: '',
        content: '',
        type: 'DOCUMENT',
        isRecall: true,
        question: '',
        answer: '',
        splitterType: 'token', // 默认使用 token 切割
      } as AgentKnowledge & { question?: string; answer?: string });

      // 切换筛选面板
      const toggleFilter = () => {
        filterVisible.value = !filterVisible.value;
      };

      // 清空筛选条件
      const clearFilters = () => {
        queryParams.type = '';
        queryParams.embeddingStatus = '';
        handleSearch();
      };

      // 加载知识列表
      const loadKnowledgeList = async () => {
        loading.value = true;
        try {
          const queryDTO = {
            ...queryParams,
            type: queryParams.type ? queryParams.type : '',
            embeddingStatus: queryParams.embeddingStatus ? queryParams.embeddingStatus : '',
          };
          const result = await agentKnowledgeService.queryByPage(queryDTO);
          if (result.success) {
            knowledgeList.value = result.data;
            total.value = result.total;
          } else {
            ElMessage.error(result.message || '加载知识列表失败');
          }
        } catch (error) {
          ElMessage.error('加载知识列表失败');
          console.error('Failed to load knowledge list:', error);
        } finally {
          loading.value = false;
        }
      };

      // 防抖定时器
      let searchTimer: any = null;

      // 处理搜索输入（带防抖）
      const handleSearchInput = () => {
        // 清除之前的定时器
        if (searchTimer) {
          clearTimeout(searchTimer);
        }
        
        // 设置新的定时器，延迟500ms执行搜索
        searchTimer = setTimeout(() => {
          queryParams.pageNum = 1;
          loadKnowledgeList();
        }, 500);
      };

      // 搜索
      const handleSearch = () => {
        // 清除防抖定时器
        if (searchTimer) {
          clearTimeout(searchTimer);
        }
        queryParams.pageNum = 1;
        loadKnowledgeList();
      };

      // 分页处理
      const handleSizeChange = (val: number) => {
        queryParams.pageSize = val;
        loadKnowledgeList();
      };

      const handleCurrentChange = (val: number) => {
        queryParams.pageNum = val;
        loadKnowledgeList();
      };

      // 打开创建对话框
      const openCreateDialog = () => {
        isEdit.value = false;
        dialogVisible.value = true;
        resetForm();
      };

      // 关闭对话框
      const closeDialog = () => {
        dialogVisible.value = false;
        resetForm();
      };

      // 编辑知识
      const editKnowledge = (knowledge: AgentKnowledge) => {
        isEdit.value = true;
        currentEditId.value = knowledge.id || null;
        knowledgeForm.value = {
          ...knowledge,
          type: knowledge.type,
          splitterType: knowledge.splitterType || 'token', // 确保有默认值
        };

        if (knowledge.type === 'QA' || knowledge.type === 'FAQ') {
          knowledgeForm.value.answer = knowledge.content;
        }

        dialogVisible.value = true;
      };

      // 切换状态（召回/取消召回）
      const toggleStatus = (knowledge: AgentKnowledge) => {
        if (!knowledge.id) return;
        const newStatus = !knowledge.isRecall;
        const actionName = newStatus ? '召回' : '取消召回';

        ElMessageBox.confirm(`确定要${actionName}知识 "${knowledge.title}" 吗？`, '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
        })
          .then(async () => {
            try {
              const result = await agentKnowledgeService.updateRecallStatus(
                knowledge.id!,
                newStatus,
              );
              if (result) {
                knowledge.isRecall = newStatus;
                ElMessage.success(`${actionName}成功`);
              } else {
                ElMessage.error(`${actionName}失败`);
              }
            } catch (error) {
              ElMessage.error(`${actionName}失败`);
              console.error(`Failed to ${actionName} knowledge:`, error);
            }
          })
          .catch(() => {});
      };

      // 重试向量化
      const handleRetry = async (knowledge: AgentKnowledge) => {
        if (!knowledge.id) return;
        try {
          const success = await agentKnowledgeService.retryEmbedding(knowledge.id);
          if (success) {
            ElMessage.success('重试请求已发送');
            loadKnowledgeList();
          } else {
            ElMessage.error('重试失败');
          }
        } catch (error) {
          ElMessage.error('重试失败');
        }
      };

      // 删除知识
      const deleteKnowledge = (knowledge: AgentKnowledge) => {
        if (!knowledge.id) return;

        ElMessageBox.confirm(`确定要删除知识 "${knowledge.title}" 吗？`, '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
        })
          .then(async () => {
            try {
              const result = await agentKnowledgeService.delete(knowledge.id!);
              if (result) {
                ElMessage.success('删除成功');
                await loadKnowledgeList();
              } else {
                ElMessage.error('删除失败');
              }
            } catch (error) {
              ElMessage.error('删除失败');
              console.error('Failed to delete knowledge:', error);
            }
          })
          .catch(() => {});
      };

      // 处理类型变化
      const handleTypeChange = () => {
        knowledgeForm.value.content = '';
        knowledgeForm.value.question = '';
        knowledgeForm.value.answer = '';
        fileList.value = [];
      };

      // 处理文件变化
      const handleFileChange = (file: { name: string; size: number; raw: File }) => {
        // 验证文件类型
        const allowedExtensions = ['.pdf', '.doc', '.docx', '.txt', '.md'];
        const fileName = file.name.toLowerCase();
        const fileExtension = fileName.substring(fileName.lastIndexOf('.'));
        
        if (!allowedExtensions.includes(fileExtension)) {
          ElMessage.error(`不支持的文件类型。仅支持: ${allowedExtensions.join(', ')}`);
          return false;
        }
        
        // 清空之前的文件列表，只保留最新的文件
        fileList.value = [file];
        knowledgeForm.value.file = file.raw;
        return true;
      };

      // 处理文件超出限制
      const handleFileExceed = (files: File[]) => {
        ElMessage.warning('只能上传一个文件，新文件将替换原有文件');
        // 自动替换为最新上传的文件
        if (files.length > 0) {
          const file = files[files.length - 1]; // 取最后一个文件
          const fileObj = {
            name: file.name,
            size: file.size,
            raw: file
          };
          handleFileChange(fileObj);
        }
      };

      // 处理文件移除
      const handleFileRemove = () => {
        fileList.value = [];
        knowledgeForm.value.file = undefined;
      };

      // 处理表格选择变化
      const handleSelectionChange = (selection: AgentKnowledge[]) => {
        selectedKnowledge.value = selection;
      };

      // 批量删除
      const handleBatchDelete = async () => {
        if (selectedKnowledge.value.length === 0) {
          ElMessage.warning('请先选择要删除的知识');
          return;
        }

        try {
          await ElMessageBox.confirm(
            `确定要删除选中的 ${selectedKnowledge.value.length} 条知识吗？此操作为逻辑删除。`,
            '确认批量删除',
            {
              confirmButtonText: '确定',
              cancelButtonText: '取消',
              type: 'warning',
            },
          );

          const ids = selectedKnowledge.value.map(item => item.id!);
          // 假设 agentKnowledgeService 有 batchDelete 方法
          // const result = await agentKnowledgeService.batchDelete(props.agentId, ids);
          
          // 临时实现：逐个删除（后续需要后端支持批量删除接口）
          let successCount = 0;
          for (const id of ids) {
            try {
              const result = await agentKnowledgeService.delete(id);
              if (result) {
                successCount++;
              }
            } catch (error) {
              console.error(`Failed to delete knowledge ${id}:`, error);
            }
          }
          
          if (successCount > 0) {
            ElMessage.success(`成功删除 ${successCount} 条知识`);
            selectedKnowledge.value = [];
            await loadKnowledgeList();
          } else {
            ElMessage.error('批量删除失败');
          }
        } catch (error) {
          if (error !== 'cancel') {
            ElMessage.error('批量删除失败');
            console.error('Failed to batch delete:', error);
          }
        }
      };

      // 批量召回/取消召回
      const handleBatchRecall = async (isRecall: boolean) => {
        if (selectedKnowledge.value.length === 0) {
          ElMessage.warning(`请先选择要${isRecall ? '召回' : '取消召回'}的知识`);
          return;
        }

        try {
          await ElMessageBox.confirm(
            `确定要${isRecall ? '召回' : '取消召回'}选中的 ${selectedKnowledge.value.length} 条知识吗？`,
            `确认批量${isRecall ? '召回' : '取消召回'}`,
            {
              confirmButtonText: '确定',
              cancelButtonText: '取消',
              type: 'warning',
            },
          );

          const ids = selectedKnowledge.value.map(item => item.id!);
          let successCount = 0;
          
          // 逐个更新召回状态（后续需要后端支持批量更新接口）
          for (const id of ids) {
            try {
              const result = await agentKnowledgeService.updateRecallStatus(id, isRecall);
              if (result) {
                successCount++;
              }
            } catch (error) {
              console.error(`Failed to update recall status for knowledge ${id}:`, error);
            }
          }
          
          if (successCount > 0) {
            ElMessage.success(`成功${isRecall ? '召回' : '取消召回'} ${successCount} 条知识`);
            selectedKnowledge.value = [];
            await loadKnowledgeList();
          } else {
            ElMessage.error(`批量${isRecall ? '召回' : '取消召回'}失败`);
          }
        } catch (error) {
          if (error !== 'cancel') {
            ElMessage.error(`批量${isRecall ? '召回' : '取消召回'}失败`);
            console.error('Failed to batch update recall status:', error);
          }
        }
      };

      // 格式化文件大小
      const formatFileSize = (bytes: number): string => {
        if (!bytes) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
      };

      // 保存知识
      const saveKnowledge = async () => {
        // 表单验证
        if (!knowledgeForm.value.title || !knowledgeForm.value.title.trim()) {
          ElMessage.warning('请输入知识标题');
          return;
        }

        if (knowledgeForm.value.type === 'DOCUMENT') {
          if (!isEdit.value && !knowledgeForm.value.file && fileList.value.length === 0) {
            ElMessage.warning('请上传文件');
            return;
          }
        } else if (knowledgeForm.value.type === 'QA' || knowledgeForm.value.type === 'FAQ') {
          if (!knowledgeForm.value.question || !knowledgeForm.value.question.trim()) {
            ElMessage.warning('请输入问题');
            return;
          }
          if (!knowledgeForm.value.answer || !knowledgeForm.value.answer.trim()) {
            ElMessage.warning('请输入答案');
            return;
          }
          knowledgeForm.value.content = knowledgeForm.value.answer;
        }

        saveLoading.value = true;
        try {
          if (isEdit.value && currentEditId.value) {
            const updateData = {
              ...knowledgeForm.value,
              type: knowledgeForm.value.type?.toUpperCase(),
            };
            const result = await agentKnowledgeService.update(currentEditId.value, updateData);
            if (result) {
              ElMessage.success('更新成功');
            } else {
              ElMessage.error('更新失败');
              return;
            }
          } else {
            const formData = new FormData();
            formData.append('agentId', String(knowledgeForm.value.agentId));
            formData.append('title', knowledgeForm.value.title);
            formData.append('type', knowledgeForm.value.type || 'DOCUMENT');
            formData.append('isRecall', knowledgeForm.value.isRecall ? '1' : '0');
            formData.append('splitterType', knowledgeForm.value.splitterType || 'token');

            if (knowledgeForm.value.type === 'DOCUMENT' && knowledgeForm.value.file) {
              formData.append('file', knowledgeForm.value.file);
            } else {
              if (knowledgeForm.value.content) {
                formData.append('content', knowledgeForm.value.content);
              }
              if (knowledgeForm.value.question) {
                formData.append('question', knowledgeForm.value.question);
              }
            }

            const response = await axios.post('/api/agent-knowledge/create', formData, {
              headers: {
                'Content-Type': 'multipart/form-data',
              },
            });

            if (response.data.success) {
              ElMessage.success('创建成功');
            } else {
              ElMessage.error(response.data.message || '创建失败');
              return;
            }
          }

          dialogVisible.value = false;
          await loadKnowledgeList();
        } catch (error) {
          ElMessage.error(`${isEdit.value ? '更新' : '创建'}失败`);
          console.error('Failed to save knowledge:', error);
        } finally {
          saveLoading.value = false;
        }
      };

      // 重置表单
      const resetForm = () => {
        knowledgeForm.value = {
          agentId: props.agentId,
          title: '',
          content: '',
          type: 'DOCUMENT',
          isRecall: true,
          question: '',
          answer: '',
          splitterType: 'token', // 默认使用 token 切割
        } as AgentKnowledge & { question?: string; answer?: string };
        currentEditId.value = null;
        fileList.value = [];
      };

      onMounted(() => {
        loadKnowledgeList();
      });

      return {
        Plus,
        Search,
        FilterIcon,
        RefreshLeft,
        UploadFilled,
        Warning,
        Delete,
        knowledgeList,
        total,
        loading,
        dialogVisible,
        isEdit,
        saveLoading,
        queryParams,
        knowledgeForm,
        fileList,
        filterVisible,
        selectedKnowledge,
        toggleFilter,
        clearFilters,
        loadKnowledgeList,
        handleSearch,
        handleSizeChange,
        handleCurrentChange,
        openCreateDialog,
        closeDialog,
        editKnowledge,
        deleteKnowledge,
        saveKnowledge,
        resetForm,
        handleTypeChange,
        handleFileChange,
        handleFileExceed,
        handleFileRemove,
        toggleStatus,
        handleRetry,
        formatFileSize,
        handleSelectionChange,
        handleBatchDelete,
        handleBatchRecall,
        handleSearchInput,
      };
    },
  });
</script>

<style scoped>
  /* 无需额外样式，使用 ElementPlus 默认样式 */
</style>
