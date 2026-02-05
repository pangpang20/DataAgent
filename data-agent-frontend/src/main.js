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
import { createApp } from 'vue';
import App from '@/App.vue';
import router from '@/router';

// 引入全局样式
import '@/styles/design-system.css';
import '@/styles/components.css';
import '@/styles/layout.css';
import '@/styles/global.css';
import 'element-plus/dist/index.css';
import ElementPlus from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn'; // 导入Element Plus中文语言包

// 全局错误处理 - 过滤Chrome扩展错误
window.addEventListener('unhandledrejection', (event) => {
  // 过滤Chrome扩展通信错误
  if (event.reason && event.reason.message &&
    event.reason.message.includes('message channel closed')) {
    console.debug('Chrome extension communication error (ignored):', event.reason.message);
    event.preventDefault(); // 阻止错误显示在控制台
  }
});

// 创建应用实例
const app = createApp(App);
app.use(router);
app.use(ElementPlus, {
  locale: zhCn, // 设置Element Plus为中文
});
app.mount('#app');
