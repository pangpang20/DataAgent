<template>
  <div v-if="total > 0" class="pagination-container">
    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="currentPageSize"
      :page-sizes="pageSizes"
      :total="total"
      :layout="layout"
      :background="background"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
    />
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, watch, PropType } from 'vue';

export default defineComponent({
  name: 'Pagination',
  props: {
    total: {
      type: Number,
      required: true,
      default: 0,
    },
    pageNum: {
      type: Number,
      default: 1,
    },
    pageSize: {
      type: Number,
      default: 10,
    },
    pageSizes: {
      type: Array as PropType<number[]>,
      default: () => [10, 20, 50, 100],
    },
    layout: {
      type: String,
      default: 'total, sizes, prev, pager, next, jumper',
    },
    background: {
      type: Boolean,
      default: true,
    },
  },
  emits: ['change', 'update:pageNum', 'update:pageSize'],
  setup(props, { emit }) {
    const currentPage = ref(props.pageNum);
    const currentPageSize = ref(props.pageSize);

    // Watch props changes
    watch(
      () => props.pageNum,
      (newVal) => {
        currentPage.value = newVal;
      }
    );

    watch(
      () => props.pageSize,
      (newVal) => {
        currentPageSize.value = newVal;
      }
    );

    const handleSizeChange = (size: number) => {
      currentPageSize.value = size;
      currentPage.value = 1; // Reset to first page
      emitChange();
    };

    const handleCurrentChange = (page: number) => {
      currentPage.value = page;
      emitChange();
    };

    const emitChange = () => {
      emit('update:pageNum', currentPage.value);
      emit('update:pageSize', currentPageSize.value);
      emit('change', {
        pageNum: currentPage.value,
        pageSize: currentPageSize.value,
      });
    };

    return {
      currentPage,
      currentPageSize,
      handleSizeChange,
      handleCurrentChange,
    };
  },
});
</script>

<style scoped>
.pagination-container {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px 0;
  margin-top: 20px;
}
</style>
