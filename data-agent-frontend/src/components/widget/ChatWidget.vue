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
  <div class="chat-widget" :class="{ open: isOpen }" :style="positionStyle">
    <!-- 浮动按钮 -->
    <div v-if="!isOpen" class="chat-button" @click="toggleChat" :style="buttonStyle">
      <img src="/logo.png" alt="AI助手" class="chat-button-logo" />
    </div>

    <!-- 聊天窗口 -->
    <div v-if="isOpen" class="chat-window" :style="windowStyle">
      <!-- 头部 -->
      <div class="chat-header" :style="headerStyle">
        <div class="chat-title">{{ config.title }}</div>
        <div class="header-buttons">
          <button class="header-button" @click="toggleMaximize" :title="isMaximized ? '还原' : '最大化'">
            <svg v-if="!isMaximized" viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
              <path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
              <path d="M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z"/>
            </svg>
          </button>
          <button class="header-button close-button" @click="toggleChat">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
              <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
            </svg>
          </button>
        </div>
      </div>

      <!-- 消息区域 -->
      <div class="chat-messages" ref="messagesContainer">
        <div v-if="messages.length === 0 && !isStreaming" class="welcome-section">
          <div class="welcome-message">
            {{ config.welcomeMessage }}
          </div>
          <!-- 预设问题 -->
          <div v-if="presetQuestions.length > 0" class="preset-questions">
            <div 
              v-for="question in presetQuestions" 
              :key="question.id"
              class="preset-question-item"
              @click="sendPresetQuestion(question.question)"
            >
              {{ question.question }}
            </div>
          </div>
        </div>
        
        <!-- 历史消息 -->
        <div 
          v-for="(msg, index) in messages" 
          :key="msg.id || index" 
          class="message-wrapper"
        >
          <!-- Result Set 消息 -->
          <div v-if="msg.messageType === 'result-set'" class="result-set-message">
            <ResultSetDisplay
              v-if="msg.content"
              :resultData="JSON.parse(msg.content)"
              :pageSize="10"
            />
          </div>
          <!-- Markdown 报告消息 -->
          <div v-else-if="msg.messageType === 'markdown-report'" class="markdown-report-message">
            <Markdown>
              {{ msg.content }}
            </Markdown>
          </div>
          <!-- 普通文本消息 -->
          <div v-else :class="['message', msg.role]">
            <div class="message-content" v-html="msg.content"></div>
          </div>
        </div>
        
        <!-- 流式响应显示 -->
        <div v-if="isStreaming" class="streaming-response">
          <div class="streaming-header">
            <span class="loading-dot">●</span>
            <span>AI 正在思考...</span>
          </div>
          <div v-for="(nodeBlock, index) in nodeBlocks" :key="index" class="node-block">
            <div class="node-header" @click="toggleNodeVisibility(index)">
              <span class="node-title">{{ nodeBlock[0]?.nodeName || 'Processing' }}</span>
              <span class="node-toggle">{{ isNodeVisible[index] ? '▼' : '▶' }}</span>
            </div>
            <div v-show="isNodeVisible[index]" class="node-content">
              <!-- Result Set 节点 -->
              <ResultSetDisplay
                v-if="nodeBlock[0]?.textType === 'RESULT_SET' && nodeBlock[0]?.text"
                :resultData="JSON.parse(nodeBlock[0].text)"
                :pageSize="10"
              />
              <!-- Markdown 节点 -->
              <Markdown
                v-else-if="nodeBlock[0]?.textType === 'MARK_DOWN' && nodeBlock[0]?.text"
                :generating="isStreaming"
              >
                {{ nodeBlock[0].text }}
              </Markdown>
              <!-- 普通文本节点 -->
              <div v-else class="node-text" v-html="nodeBlock[0]?.text || ''"></div>
            </div>
          </div>
        </div>
        
        <div v-if="isLoading && !isStreaming" class="message assistant">
          <div class="message-content">
            <div class="typing-indicator">
              <span></span>
              <span></span>
              <span></span>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="chat-input">
        <input
          v-model="userInput"
          type="text"
          placeholder="输入消息..."
          @keypress.enter="sendMessage"
          :disabled="isLoading"
        />
        <button 
          @click="sendMessage" 
          :disabled="!userInput.trim() || isLoading"
          :style="sendButtonStyle"
        >
          <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
            <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
  import { defineComponent, ref, computed, onMounted, nextTick, watch } from 'vue';
  import axios from 'axios';
  import ResultSetDisplay from '../run/ResultSetDisplay.vue';
  import Markdown from '../run/Markdown.vue';
  import type { ResultData } from '@/services/resultSet';

  interface Message {
    id?: number;
    role: 'user' | 'assistant';
    content: string;
    messageType?: string;
  }

  interface PresetQuestion {
    id?: number;
    question: string;
    isActive?: boolean;
  }

  interface StreamNodeData {
    nodeName: string;
    text: string;
    textType: string;
  }

  interface WidgetConfig {
    agentId: number;
    apiKey: string;
    title: string;
    position: 'bottom-right' | 'bottom-left';
    primaryColor: string;
    welcomeMessage: string;
    baseUrl?: string;
  }

  export default defineComponent({
    name: 'ChatWidget',
    components: {
      ResultSetDisplay,
      Markdown,
    },
    props: {
      config: {
        type: Object as () => WidgetConfig,
        required: true,
      },
    },
    setup(props) {
      const isOpen = ref(false);
      const isMaximized = ref(false);
      const messages = ref<Message[]>([]);
      const userInput = ref('');
      const isLoading = ref(false);
      const sessionId = ref<string | null>(null);
      const messagesContainer = ref<HTMLElement | null>(null);
      const presetQuestions = ref<PresetQuestion[]>([]);
      
      // Streaming response support
      const isStreaming = ref(false);
      const nodeBlocks = ref<StreamNodeData[][]>([]);
      const isNodeVisible = ref<Record<number, boolean>>({});
      
      const baseUrl = computed(() => props.config.baseUrl || window.location.origin);

      const positionStyle = computed(() => {
        const pos = props.config.position || 'bottom-right';
        if (pos === 'bottom-left') {
          return { left: '20px', right: 'auto' };
        }
        return { right: '20px', left: 'auto' };
      });

      const buttonStyle = computed(() => ({}));

      const headerStyle = computed(() => ({
        backgroundColor: props.config.primaryColor || '#409EFF',
      }));

      const windowStyle = computed(() => {
        const baseStyle: Record<string, any> = {
          borderColor: props.config.primaryColor || '#409EFF',
        };
        
        if (isMaximized.value) {
          return {
            ...baseStyle,
            position: 'fixed',
            top: '0',
            left: '0',
            right: '0',
            bottom: '0',
            width: '100vw',
            height: '100vh',
            borderRadius: '0',
            zIndex: '10000',
          };
        }
        
        return baseStyle;
      });

      const sendButtonStyle = computed(() => ({
        backgroundColor: props.config.primaryColor || '#409EFF',
      }));

      const toggleChat = () => {
        isOpen.value = !isOpen.value;
        if (isOpen.value) {
          isMaximized.value = false; // 重新打开时重置为非最大化
          if (!sessionId.value) {
            createSession();
            loadPresetQuestions();
          }
        }
      };

      const toggleMaximize = () => {
        isMaximized.value = !isMaximized.value;
      };

      const loadPresetQuestions = async () => {
        try {
          const response = await axios.get(
            `${baseUrl.value}/api/agent/${props.config.agentId}/preset-questions`,
            {
              headers: {
                'X-API-Key': props.config.apiKey,
              },
            }
          );
          presetQuestions.value = response.data.filter((q: PresetQuestion) => q.isActive);
        } catch (error) {
          console.error('Failed to load preset questions:', error);
        }
      };

      const sendPresetQuestion = (question: string) => {
        userInput.value = question;
        sendMessage();
      };

      const toggleNodeVisibility = (index: number) => {
        isNodeVisible.value[index] = !isNodeVisible.value[index];
      };

      const scrollToBottom = () => {
        nextTick(() => {
          if (messagesContainer.value) {
            messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
          }
        });
      };

      const createSession = async () => {
        try {
          const response = await axios.post(
            `${baseUrl.value}/api/agent/${props.config.agentId}/sessions`,
            { title: 'Widget Chat' },
            {
              headers: {
                'Content-Type': 'application/json',
                'X-API-Key': props.config.apiKey,
              },
            }
          );
          sessionId.value = response.data.id;
        } catch (error) {
          console.error('Failed to create session:', error);
          messages.value.push({
            role: 'assistant',
            content: '抱歉，无法连接到服务器，请稍后再试。',
          });
        }
      };

      const sendMessage = async () => {
        if (!userInput.value.trim() || isLoading.value) return;
        if (!sessionId.value) {
          await createSession();
          if (!sessionId.value) return;
        }

        const messageContent = userInput.value.trim();
        userInput.value = '';

        // 添加用户消息
        messages.value.push({
          role: 'user',
          content: messageContent,
        });
        scrollToBottom();

        isLoading.value = true;
        isStreaming.value = true;
        nodeBlocks.value = [];
        isNodeVisible.value = {};

        try {
          // 保存用户消息到后端
          await axios.post(
            `${baseUrl.value}/api/sessions/${sessionId.value}/messages`,
            {
              role: 'user',
              content: messageContent,
              messageType: 'text',
            },
            {
              headers: {
                'Content-Type': 'application/json',
                'X-API-Key': props.config.apiKey,
              },
            }
          );

          // 获取AI响应 (通过SSE)
          const eventSource = new EventSource(
            `${baseUrl.value}/api/stream/search?agentId=${props.config.agentId}&query=${encodeURIComponent(messageContent)}&nl2sqlOnly=true`
          );

          const nodeMap: Record<string, StreamNodeData[]> = {};
          
          eventSource.onmessage = (event) => {
            try {
              const data = JSON.parse(event.data);
              
              // Handle GraphNodeResponse structure
              if (data.nodeName && data.text) {
                const nodeName = data.nodeName || 'Unknown';
                if (!nodeMap[nodeName]) {
                  nodeMap[nodeName] = [];
                  isNodeVisible.value[Object.keys(nodeMap).length - 1] = true;
                }
                nodeMap[nodeName].push({
                  nodeName: data.nodeName,
                  text: data.text || '',
                  textType: data.textType || 'TEXT',
                });
                nodeBlocks.value = Object.values(nodeMap);
                scrollToBottom();
              }
            } catch (e) {
              console.error('Parse SSE error:', e);
            }
          };

          eventSource.onerror = () => {
            eventSource.close();
            isLoading.value = false;
            isStreaming.value = false;
            loadHistoryMessages();
          };

          eventSource.addEventListener('complete', () => {
            eventSource.close();
            isLoading.value = false;
            isStreaming.value = false;
            loadHistoryMessages();
          });
          
          eventSource.addEventListener('error', () => {
            eventSource.close();
            isLoading.value = false;
            isStreaming.value = false;
          });

        } catch (error) {
          console.error('Send message error:', error);
          isLoading.value = false;
          isStreaming.value = false;
          messages.value.push({
            role: 'assistant',
            content: '抱歉，发送消息失败，请稍后再试。',
          });
          scrollToBottom();
        }
      };

      const loadHistoryMessages = async () => {
        if (!sessionId.value) return;
        try {
          const response = await axios.get(
            `${baseUrl.value}/api/sessions/${sessionId.value}/messages`,
            {
              headers: {
                'X-API-Key': props.config.apiKey,
              },
            }
          );
          messages.value = response.data || [];
          nodeBlocks.value = [];
          scrollToBottom();
        } catch (error) {
          console.error('Load history messages error:', error);
        }
      };

      watch(() => messages.value.length, () => {
        scrollToBottom();
      });

      return {
        isOpen,
        isMaximized,
        messages,
        userInput,
        isLoading,
        messagesContainer,
        presetQuestions,
        isStreaming,
        nodeBlocks,
        isNodeVisible,
        positionStyle,
        buttonStyle,
        headerStyle,
        windowStyle,
        sendButtonStyle,
        toggleChat,
        toggleMaximize,
        sendMessage,
        sendPresetQuestion,
        toggleNodeVisibility,
      };
    },
  });
</script>

<style scoped>
  .chat-widget {
    position: fixed;
    bottom: 20px;
    z-index: 2147483647; /* 使用最大的 z-index 值，确保在最顶层 */
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  }

  .chat-button {
    width: 50px;
    height: 50px;
    border-radius: 50%;
    background-color: white;
    color: white;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    transition: transform 0.2s, box-shadow 0.2s;
    overflow: hidden;
  }

  .chat-button-logo {
    width: 50px;
    height: 50px;
    object-fit: contain;
  }

  .chat-button:hover {
    transform: scale(1.05);
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
  }

  .chat-window {
    position: fixed; /* 添加固定定位 */
    width: 360px;
    height: 520px;
    background: white;
    border-radius: 12px;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
    display: flex;
    flex-direction: column;
    overflow: hidden;
    border: 2px solid #409EFF;
    z-index: 2147483647; /* 添加 z-index，确保在最顶层 */
  }

  .chat-header {
    background-color: #409EFF;
    color: white;
    padding: 16px 20px;
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .chat-title {
    font-size: 16px;
    font-weight: 600;
  }

  .header-buttons {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .header-button {
    background: none;
    border: none;
    color: white;
    cursor: pointer;
    padding: 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 4px;
    transition: background-color 0.2s;
  }

  .header-button:hover {
    background-color: rgba(255, 255, 255, 0.2);
  }

  .chat-messages {
    flex: 1;
    overflow-y: auto;
    padding: 16px;
    background-color: #f5f7fa;
  }

  .welcome-message {
    text-align: center;
    color: #666;
    padding: 20px;
    line-height: 1.6;
  }

  .welcome-section {
    padding: 20px;
  }

  .preset-questions {
    margin-top: 20px;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .preset-question-item {
    background-color: white;
    border: 1px solid #e5e7eb;
    border-radius: 8px;
    padding: 12px 16px;
    cursor: pointer;
    transition: all 0.2s;
    color: #333;
    font-size: 14px;
    line-height: 1.5;
  }

  .preset-question-item:hover {
    background-color: #f0f9ff;
    border-color: #409EFF;
    color: #409EFF;
    transform: translateX(4px);
  }

  .message-wrapper {
    margin-bottom: 12px;
  }

  .result-set-message,
  .markdown-report-message {
    margin: 8px 0;
    background: white;
    border-radius: 8px;
    padding: 12px;
    border: 1px solid #e5e7eb;
  }

  .streaming-response {
    margin: 12px 0;
  }

  .streaming-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    background: #f0f9ff;
    border-radius: 6px;
    margin-bottom: 12px;
    font-size: 14px;
    color: #409EFF;
  }

  .loading-dot {
    animation: pulse 1.5s ease-in-out infinite;
  }

  @keyframes pulse {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.5; }
  }

  .node-block {
    margin: 8px 0;
    background: white;
    border: 1px solid #e5e7eb;
    border-radius: 6px;
    overflow: hidden;
  }

  .node-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 14px;
    background: #f5f7fa;
    cursor: pointer;
    user-select: none;
    transition: background-color 0.2s;
  }

  .node-header:hover {
    background: #ebeef5;
  }

  .node-title {
    font-size: 13px;
    font-weight: 500;
    color: #606266;
  }

  .node-toggle {
    font-size: 12px;
    color: #909399;
  }

  .node-content {
    padding: 12px 14px;
    font-size: 14px;
    line-height: 1.6;
  }

  .node-text {
    color: #333;
  }

  .message {
    margin-bottom: 12px;
    display: flex;
  }

  .message.user {
    justify-content: flex-end;
  }

  .message.assistant {
    justify-content: flex-start;
  }

  .message-content {
    max-width: 70%;
    padding: 10px 14px;
    border-radius: 12px;
    word-wrap: break-word;
    line-height: 1.5;
  }

  .message.user .message-content {
    background-color: #409EFF;
    color: white;
  }

  .message.assistant .message-content {
    background-color: white;
    color: #333;
    border: 1px solid #e5e7eb;
  }

  .typing-indicator {
    display: flex;
    gap: 4px;
    padding: 8px 0;
  }

  .typing-indicator span {
    width: 6px;
    height: 6px;
    background-color: #999;
    border-radius: 50%;
    animation: typing 1.4s infinite;
  }

  .typing-indicator span:nth-child(2) {
    animation-delay: 0.2s;
  }

  .typing-indicator span:nth-child(3) {
    animation-delay: 0.4s;
  }

  @keyframes typing {
    0%, 60%, 100% {
      opacity: 0.3;
      transform: translateY(0);
    }
    30% {
      opacity: 1;
      transform: translateY(-6px);
    }
  }

  .chat-input {
    display: flex;
    gap: 8px;
    padding: 16px;
    background-color: white;
    border-top: 1px solid #e5e7eb;
  }

  .chat-input input {
    flex: 1;
    padding: 10px 14px;
    border: 1px solid #dcdfe6;
    border-radius: 20px;
    outline: none;
    font-size: 14px;
    transition: border-color 0.2s;
  }

  .chat-input input:focus {
    border-color: #409EFF;
  }

  .chat-input input:disabled {
    background-color: #f5f7fa;
    cursor: not-allowed;
  }

  .chat-input button {
    width: 40px;
    height: 40px;
    border-radius: 50%;
    background-color: #409EFF;
    color: white;
    border: none;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: opacity 0.2s;
  }

  .chat-input button:hover:not(:disabled) {
    opacity: 0.9;
  }

  .chat-input button:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  /* 滚动条样式 */
  .chat-messages::-webkit-scrollbar {
    width: 6px;
  }

  .chat-messages::-webkit-scrollbar-track {
    background: #f1f1f1;
  }

  .chat-messages::-webkit-scrollbar-thumb {
    background: #ccc;
    border-radius: 3px;
  }

  .chat-messages::-webkit-scrollbar-thumb:hover {
    background: #999;
  }
</style>
