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
      <h2>预设问题管理</h2>
    </div>
    <el-divider />

    <!-- 搜索和筛选区域 -->
    <el-form :inline="true" :model="searchForm" style="margin-bottom: 20px">
      <el-form-item label="关键词">
        <el-input
          v-model="searchForm.keyword"
          placeholder="搜索问题内容"
          clearable
          @clear="handleSearch"
          style="width: 200px"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select
          v-model="searchForm.isActive"
          placeholder="全部"
          clearable
          style="width: 120px"
        >
          <el-option label="启用" :value="true" />
          <el-option label="禁用" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item label="创建时间">
        <el-date-picker
          v-model="searchForm.dateRange"
          type="daterange"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 240px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <div style="margin-bottom: 30px">
      <el-row style="display: flex; justify-content: space-between; align-items: center">
        <el-col :span="12">
          <el-button
            v-if="selectedRows.length > 0"
            @click="handleBatchEnable"
            type="success"
            plain
          >
            批量启用 ({{ selectedRows.length }})
          </el-button>
          <el-button
            v-if="selectedRows.length > 0"
            @click="handleBatchDisable"
            type="warning"
            plain
          >
            批量禁用 ({{ selectedRows.length }})
          </el-button>
          <el-button
            v-if="selectedRows.length > 0"
            @click="handleBatchDelete"
            type="danger"
            plain
            :icon="Delete"
          >
            批量删除 ({{ selectedRows.length }})
          </el-button>
        </el-col>
        <el-col :span="12" style="text-align: right">
          <el-button @click="openCreateDialog" size="large" type="primary" round :icon="Plus">
            添加问题
          </el-button>
        </el-col>
      </el-row>
    </div>

    <el-table
      :data="presetQuestionList"
      @selection-change="handleSelectionChange"
      style="width: 100%"
      border
    >
      <el-table-column type="selection" width="55" />
      <el-table-column prop="id" label="ID" min-width="60px" />
      <el-table-column prop="question" label="问题" min-width="250px" show-overflow-tooltip />
      <el-table-column prop="sortOrder" label="排序" min-width="80px" />
      <el-table-column label="状态" min-width="80px">
        <template #default="scope">
          <el-tag :type="scope.row.isActive ? 'success' : 'info'" round>
            {{ scope.row.isActive ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="150px" />
      <el-table-column label="操作" min-width="200px">
        <template #default="scope">
          <el-button @click="editQuestion(scope.row)" size="small" type="primary" round plain>
            编辑
          </el-button>
          <el-button @click="deleteQuestion(scope.row)" size="small" type="danger" round plain>
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页组件 -->
    <Pagination
      v-model:page-num="pagination.pageNum"
      v-model:page-size="pagination.pageSize"
      :total="pagination.total"
      :page-sizes="[10, 20, 50, 100]"
      @change="loadPresetQuestions"
    />
  </div>

  <!-- 添加/编辑预设问题Dialog -->
  <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑预设问题' : '添加预设问题'" width="600">
    <el-form :model="questionForm" label-width="80px" ref="questionFormRef">
      <el-form-item label="问题" prop="question" required>
        <el-input
          v-model="questionForm.question"
          type="textarea"
          :rows="4"
          placeholder="请输入预设问题"
        />
      </el-form-item>

      <el-form-item label="排序" prop="sortOrder">
        <el-input-number v-model="questionForm.sortOrder" :min="0" controls-position="right" />
      </el-form-item>

      <el-form-item label="状态" prop="isActive">
        <el-switch v-model="questionForm.isActive" />
      </el-form-item>
    </el-form>

    <template #footer>
      <div style="text-align: right">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveQuestion">
          {{ isEdit ? '更新' : '创建' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script lang="ts">
  import { defineComponent, ref, onMounted, Ref } from 'vue';
  import { Plus, Delete } from '@element-plus/icons-vue';
  import presetQuestionService from '@/services/presetQuestion';
  import {
    PresetQuestion,
    PresetQuestionDTO,
    PresetQuestionQueryParams,
  } from '@/services/presetQuestion';
  import { ElMessage, ElMessageBox } from 'element-plus';
  import Pagination from '@/components/common/Pagination.vue';

  export default defineComponent({
    name: 'AgentPresetsConfig',
    components: {
      Pagination,
    },
    props: {
      agentId: {
        type: Number,
        required: true,
      },
    },
    setup(props) {
      const presetQuestionList: Ref<PresetQuestion[]> = ref([]);
      const dialogVisible: Ref<boolean> = ref(false);
      const isEdit: Ref<boolean> = ref(false);
      const questionForm: Ref<PresetQuestion> = ref({
        agentId: props.agentId,
        question: '',
        sortOrder: 0,
        isActive: true,
      });
      const currentEditId: Ref<number | null> = ref(null);

      // Search form
      const searchForm = ref({
        keyword: '',
        isActive: null as boolean | null,
        dateRange: [] as string[],
      });

      // Pagination
      const pagination = ref({
        pageNum: 1,
        pageSize: 10,
        total: 0,
      });

      // Selected rows for batch delete
      const selectedRows: Ref<PresetQuestion[]> = ref([]);

      const handleSelectionChange = (selection: PresetQuestion[]) => {
        selectedRows.value = selection;
      };

      const openCreateDialog = () => {
        isEdit.value = false;
        questionForm.value = {
          agentId: props.agentId,
          question: '',
          sortOrder: 0,
          isActive: true,
        };
        dialogVisible.value = true;
      };

      const loadPresetQuestions = async () => {
        try {
          const params: PresetQuestionQueryParams = {
            pageNum: pagination.value.pageNum,
            pageSize: pagination.value.pageSize,
            keyword: searchForm.value.keyword || undefined,
            isActive: searchForm.value.isActive ?? undefined,
          };

          if (searchForm.value.dateRange && searchForm.value.dateRange.length === 2) {
            params.createTimeStart = searchForm.value.dateRange[0];
            params.createTimeEnd = searchForm.value.dateRange[1];
          }

          const response = await presetQuestionService.queryPage(props.agentId, params);

          if (response.success) {
            presetQuestionList.value = response.data;
            pagination.value.total = response.total;
          } else {
            ElMessage.error(response.message || 'Failed to load preset questions');
          }
        } catch (error) {
          ElMessage.error('加载预设问题列表失败');
          console.error('Load preset questions failed:', error);
        }
      };

      const handleSearch = () => {
        pagination.value.pageNum = 1; // Reset to first page
        loadPresetQuestions();
      };

      const handleReset = () => {
        searchForm.value = {
          keyword: '',
          isActive: null,
          dateRange: [],
        };
        handleSearch();
      };

      const handleBatchDelete = async () => {
        if (selectedRows.value.length === 0) {
          ElMessage.warning('请选择要删除的记录');
          return;
        }

        try {
          await ElMessageBox.confirm(
            `确定要删除 ${selectedRows.value.length} 条预设问题吗？`,
            '确认批量删除',
            {
              confirmButtonText: '确定',
              cancelButtonText: '取消',
              type: 'warning',
            },
          );

          const ids = selectedRows.value
            .map(row => row.id)
            .filter(id => id !== undefined) as number[];
          const result = await presetQuestionService.batchDelete(props.agentId, ids);

          if (result) {
            ElMessage.success('批量删除成功');
            selectedRows.value = [];
            await loadPresetQuestions();
          } else {
            ElMessage.error('批量删除失败');
          }
        } catch (error: any) {
          if (error !== 'cancel') {
            ElMessage.error('批量删除失败');
            console.error('Batch delete error:', error);
          }
        }
      };

      const handleBatchEnable = async () => {
        if (selectedRows.value.length === 0) {
          ElMessage.warning('请选择要启用的记录');
          return;
        }

        try {
          await ElMessageBox.confirm(
            `确定要启用 ${selectedRows.value.length} 条预设问题吗？`,
            '确认批量启用',
            {
              confirmButtonText: '确定',
              cancelButtonText: '取消',
              type: 'success',
            },
          );

          const ids = selectedRows.value
            .map(row => row.id)
            .filter(id => id !== undefined) as number[];
          const result = await presetQuestionService.batchUpdateStatus(props.agentId, ids, true);

          if (result) {
            ElMessage.success('批量启用成功');
            selectedRows.value = [];
            await loadPresetQuestions();
          } else {
            ElMessage.error('批量启用失败');
          }
        } catch (error: any) {
          if (error !== 'cancel') {
            ElMessage.error('批量启用失败');
            console.error('Batch enable error:', error);
          }
        }
      };

      const handleBatchDisable = async () => {
        if (selectedRows.value.length === 0) {
          ElMessage.warning('请选择要禁用的记录');
          return;
        }

        try {
          await ElMessageBox.confirm(
            `确定要禁用 ${selectedRows.value.length} 条预设问题吗？`,
            '确认批量禁用',
            {
              confirmButtonText: '确定',
              cancelButtonText: '取消',
              type: 'warning',
            },
          );

          const ids = selectedRows.value
            .map(row => row.id)
            .filter(id => id !== undefined) as number[];
          const result = await presetQuestionService.batchUpdateStatus(props.agentId, ids, false);

          if (result) {
            ElMessage.success('批量禁用成功');
            selectedRows.value = [];
            await loadPresetQuestions();
          } else {
            ElMessage.error('批量禁用失败');
          }
        } catch (error: any) {
          if (error !== 'cancel') {
            ElMessage.error('批量禁用失败');
            console.error('Batch disable error:', error);
          }
        }
      };

      const editQuestion = (question: PresetQuestion) => {
        isEdit.value = true;
        currentEditId.value = question.id || null;
        questionForm.value = { ...question };
        dialogVisible.value = true;
      };

      const deleteQuestion = async (question: PresetQuestion) => {
        if (!question.id) return;

        try {
          await ElMessageBox.confirm(
            `确定要删除预设问题 "${question.question.substring(0, 50)}..." 吗？`,
            '确认删除',
            {
              confirmButtonText: '确定',
              cancelButtonText: '取消',
              type: 'warning',
            },
          );

          const result = await presetQuestionService.delete(props.agentId, question.id);
          if (result) {
            ElMessage.success('删除成功');
            await loadPresetQuestions();
          } else {
            ElMessage.error('删除失败');
          }
        } catch {
          // User cancelled, ignore error
        }
      };

      const saveQuestion = async () => {
        try {
          if (!questionForm.value.question || questionForm.value.question.trim() === '') {
            ElMessage.error('请输入预设问题');
            return;
          }

          // IMPORTANT: Load all questions (without filters) before save
          const allQuestionsResponse = await presetQuestionService.queryPage(props.agentId, {
            pageNum: 1,
            pageSize: 1000, // Get all questions
          });

          if (!allQuestionsResponse.success) {
            ElMessage.error('获取完整列表失败');
            return;
          }

          const allQuestions = allQuestionsResponse.data;
          let questionsToSave: PresetQuestionDTO[] = [];

          if (isEdit.value && currentEditId.value) {
            // Edit mode: update the specific question in the full list
            questionsToSave = allQuestions.map(q => {
              const dto: PresetQuestionDTO = {
                question: q.id === currentEditId.value ? questionForm.value.question : q.question,
              };
              if (q.id === currentEditId.value) {
                dto.isActive = questionForm.value.isActive === true;
              } else {
                dto.isActive = q.isActive === true;
              }
              return dto;
            });
          } else {
            // Add mode: append new question to the full list
            questionsToSave = [
              ...allQuestions.map(q => ({
                question: q.question,
                isActive: q.isActive === true,
              })),
              {
                question: questionForm.value.question,
                isActive: questionForm.value.isActive === true,
              },
            ];
          }

          console.log('发送的数据:', JSON.stringify(questionsToSave));

          const result = await presetQuestionService.batchSave(props.agentId, questionsToSave);
          if (result) {
            ElMessage.success(isEdit.value ? '更新成功' : '创建成功');
          } else {
            ElMessage.error(isEdit.value ? '更新失败' : '创建失败');
            return;
          }

          dialogVisible.value = false;
          await loadPresetQuestions();
        } catch (error) {
          ElMessage.error(`${isEdit.value ? '更新' : '创建'}失败`);
          console.error('保存预设问题失败:', error);
        }
      };

      onMounted(() => {
        loadPresetQuestions();
      });

      return {
        Plus,
        Delete,
        presetQuestionList,
        dialogVisible,
        isEdit,
        questionForm,
        searchForm,
        pagination,
        selectedRows,
        handleSelectionChange,
        openCreateDialog,
        editQuestion,
        deleteQuestion,
        saveQuestion,
        loadPresetQuestions,
        handleSearch,
        handleReset,
        handleBatchDelete,
        handleBatchEnable,
        handleBatchDisable,
      };
    },
  });
</script>

<style scoped></style>
