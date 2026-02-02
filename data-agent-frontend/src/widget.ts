/*
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
 */

import { createApp } from 'vue';
import ChatWidget from './components/widget/ChatWidget.vue';

// 等待 DOM 加载完成
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initWidget);
} else {
    initWidget();
}

function initWidget() {
    // 获取配置
    const config = (window as any).DataAgentConfig;

    if (!config) {
        console.error('DataAgent Widget: Configuration not found. Please define window.DataAgentConfig before loading the widget.');
        return;
    }

    // 验证必需配置
    if (!config.agentId || !config.apiKey) {
        console.error('DataAgent Widget: agentId and apiKey are required in configuration.');
        return;
    }

    // 创建容器
    const widgetContainer = document.createElement('div');
    widgetContainer.id = 'data-agent-widget-container';
    document.body.appendChild(widgetContainer);

    // 挂载 Widget
    const app = createApp(ChatWidget, {
        config: {
            agentId: config.agentId,
            apiKey: config.apiKey,
            title: config.title || 'AI 助手',
            position: config.position || 'bottom-right',
            primaryColor: config.primaryColor || '#409EFF',
            welcomeMessage: config.welcomeMessage || '您好！我是 AI 助手，有什么可以帮您的吗？',
            baseUrl: config.baseUrl,
        },
    });

    app.mount(widgetContainer);

    console.log('DataAgent Widget initialized successfully');
}
