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
  <div class="layout-container">
    <!-- 顶部导航栏 -->
    <header class="top-nav">
      <div class="top-nav-brand">
        <img src="/logo.png" alt="Audaque Logo" class="top-nav-logo" />
        <span class="top-nav-title">Audaque Data Agent</span>
      </div>

      <nav class="top-nav-menu">
        <a
          class="nav-item"
          :class="{ active: isAgentPage() }"
          @click.prevent="goToAgentList"
          href="#"
        >
          智能体列表
        </a>
        <a
          class="nav-item"
          :class="{ active: isModelConfigPage() }"
          @click.prevent="goToModelConfig"
          href="#"
        >
          模型配置
        </a>
      </nav>

      <div class="top-nav-actions">
        <div class="nav-user">
          <div class="nav-user-avatar">A</div>
          <span class="nav-user-name">管理员</span>
        </div>
      </div>
    </header>

    <!-- 页面内容区域 -->
    <div class="layout-body">
      <main class="content-container">
        <slot></slot>
      </main>
    </div>
  </div>
</template>

<script>
  import { useRouter } from 'vue-router';

  export default {
    name: 'BaseLayout',
    setup() {
      const router = useRouter();

      // 导航方法
      const goToAgentList = () => {
        router.push('/agents');
      };

      const goToModelConfig = () => {
        router.push('/model-config');
      };

      const isAgentPage = () => {
        return (
          router.currentRoute.value.name === 'AgentList' ||
          router.currentRoute.value.name === 'AgentDetail' ||
          router.currentRoute.value.name === 'AgentCreate' ||
          router.currentRoute.value.name === 'AgentRun'
        );
      };

      const isModelConfigPage = () => {
        return router.currentRoute.value.name === 'ModelConfig';
      };

      return {
        goToAgentList,
        goToModelConfig,
        isAgentPage,
        isModelConfigPage,
      };
    },
  };
</script>

<style scoped>
  /* 使用全局设计系统样式，无需额外样式 */
</style>
