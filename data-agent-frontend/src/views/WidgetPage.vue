<!--
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
-->

<template>
  <div class="widget-page" :style="{ '--primary-color': primaryColor }">
    <!-- Header -->
    <div class="widget-header">
      <div class="header-title">{{ title }}</div>
      <div class="header-actions">
        <button class="header-btn maximize-btn" @click="toggleMaximize" :title="isMaximized ? '还原' : '最大化'">
          <svg v-if="!isMaximized" viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
            <path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
            <path d="M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z"/>
          </svg>
        </button>
        <button class="header-btn close-btn" @click="requestClose">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
            <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
          </svg>
        </button>
      </div>
    </div>

    <!-- Messages Area -->
    <div class="widget-messages" ref="messagesContainer">
      <!-- Welcome Section -->
      <div v-if="messages.length === 0 && !isStreaming" class="welcome-section">
        <div class="welcome-message">{{ welcomeMessage }}</div>
        <!-- Preset Questions -->
        <div v-if="presetQuestions.length > 0" class="preset-questions">
          <div 
            v-for="question in presetQuestions" 
            :key="question.id"
            class="preset-item"
            @click="sendPresetQuestion(question.question)"
          >
            {{ question.question }}
          </div>
        </div>
      </div>

      <!-- Message List -->
      <div 
        v-for="(msg, index) in messages" 
        :key="msg.id || index" 
        class="message-wrapper"
      >
        <!-- Result Set Message -->
        <div v-if="msg.messageType === 'result-set'" class="result-set-message">
          <ResultSetDisplay
            v-if="msg.content"
            :resultData="JSON.parse(msg.content)"
            :pageSize="10"
          />
        </div>
        <!-- Markdown Report Message -->
        <div v-else-if="msg.messageType === 'markdown-report'" class="markdown-message">
          <Markdown>{{ msg.content }}</Markdown>
        </div>
        <!-- Normal Text Message -->
        <div v-else :class="['message', msg.role]">
          <div class="message-content" v-html="msg.content"></div>
        </div>
      </div>

      <!-- Streaming Response -->
      <div v-if="isStreaming" class="streaming-response">
        <div class="streaming-header">
          <span class="loading-dot"></span>
          <span>AI 正在思考...</span>
        </div>
        <div v-for="(nodeBlock, index) in nodeBlocks" :key="index" class="node-block">
          <div class="node-header" @click="toggleNodeVisibility(index)">
            <span class="node-title">{{ nodeBlock[0]?.nodeName || 'Processing' }}</span>
            <span class="node-toggle">{{ isNodeVisible[index] ? '▼' : '▶' }}</span>
          </div>
          <div v-show="isNodeVisible[index]" class="node-content">
            <ResultSetDisplay
              v-if="nodeBlock[0]?.textType === 'RESULT_SET' && nodeBlock[0]?.text"
              :resultData="JSON.parse(nodeBlock[0].text)"
              :pageSize="10"
            />
            <Markdown
              v-else-if="nodeBlock[0]?.textType === 'MARK_DOWN' && nodeBlock[0]?.text"
              :generating="isStreaming"
            >
              {{ nodeBlock[0].text }}
            </Markdown>
            <div v-else class="node-text" v-html="nodeBlock[0]?.text || ''"></div>
          </div>
        </div>
      </div>

      <!-- Loading Indicator -->
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

    <!-- Input Area -->
    <div class="widget-input">
      <input
        v-model="userInput"
        type="text"
        :placeholder="inputPlaceholder"
        @keypress.enter="sendMessage"
        :disabled="isLoading"
        ref="inputRef"
      />
      <button 
        class="send-btn"
        @click="sendMessage" 
        :disabled="!userInput.trim() || isLoading"
      >
        <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
          <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
        </svg>
      </button>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue';
import { useRoute } from 'vue-router';
import axios from 'axios';
import ResultSetDisplay from '@/components/run/ResultSetDisplay.vue';
import Markdown from '@/components/run/Markdown.vue';

interface Message {
  id?: number;
  role: 'user' | 'assistant';
  content: string;
  messageType?: string;
}

interface PresetQuestion {
  id?: number;
  question: string;
}

interface StreamNodeData {
  nodeName: string;
  text: string;
  textType: string;
}

interface WidgetConfig {
  agentId: string | number;
  apiKey: string;
  title: string;
  primaryColor: string;
  welcomeMessage: string;
  baseUrl: string;
}

export default defineComponent({
  name: 'WidgetPage',
  components: {
    ResultSetDisplay,
    Markdown,
  },
  setup() {
    const route = useRoute();
    
    // State
    const isReady = ref(false);
    const parentOrigin = ref('');
    const config = ref<WidgetConfig | null>(null);
    const messages = ref<Message[]>([]);
    const userInput = ref('');
    const isLoading = ref(false);
    const isStreaming = ref(false);
    const isMaximized = ref(false);
    const sessionId = ref<string | null>(null);
    const presetQuestions = ref<PresetQuestion[]>([]);
    const nodeBlocks = ref<StreamNodeData[][]>([]);
    const isNodeVisible = ref<Record<number, boolean>>({});
    const messagesContainer = ref<HTMLElement | null>(null);
    const inputRef = ref<HTMLInputElement | null>(null);

    // Get initial values from URL params
    const agentId = computed(() => route.params.id as string);
    const title = ref(decodeURIComponent(route.query.title as string || 'AI 问数助手'));
    const welcomeMessage = ref(decodeURIComponent(route.query.welcomeMessage as string || ''));
    const primaryColor = ref(route.query.primaryColor as string || '#409EFF');
    const apiKey = ref(route.query.apiKey as string || '');
    const inputPlaceholder = ref('输入消息...');

    // Computed base URL
    const baseUrl = computed(() => {
      if (config.value?.baseUrl) {
        return config.value.baseUrl.replace(/\/$/, '');
      }
      return window.location.origin;
    });

    // Send message to parent window
    const sendToParent = (message: { type: string; payload?: any }) => {
      if (window.parent === window) return; // Not in iframe
      const origin = parentOrigin.value || '*';
      window.parent.postMessage(message, origin);
    };

    // Handle message from parent
    const handleMessage = (event: MessageEvent) => {
      // Validate origin in production
      if (parentOrigin.value && event.origin !== parentOrigin.value) return;
      
      const { type, payload } = event.data || {};
      
      switch (type) {
        case 'WIDGET_INIT':
          // Receive config from parent
          if (payload) {
            config.value = payload;
            if (payload.title) title.value = payload.title;
            if (payload.welcomeMessage) welcomeMessage.value = payload.welcomeMessage;
            if (payload.primaryColor) primaryColor.value = payload.primaryColor;
            if (payload.apiKey) apiKey.value = payload.apiKey;
            isReady.value = true;
            // Initialize session after receiving config
            initSession();
          }
          break;
          
        case 'WIDGET_OPEN':
          // Widget opened - focus input
          nextTick(() => {
            inputRef.value?.focus();
          });
          break;
          
        case 'WIDGET_CLOSE':
          // Widget closed
          console.log('[Widget Page] Widget closed');
          break;
          
        default:
          break;
      }
    };

    // Request parent to close widget
    const requestClose = () => {
      sendToParent({ type: 'WIDGET_CLOSE_REQUEST' });
    };

    // Toggle maximize
    const toggleMaximize = () => {
      isMaximized.value = !isMaximized.value;
      sendToParent({
        type: 'WIDGET_RESIZE',
        payload: {
          maximized: isMaximized.value,
          height: isMaximized.value ? window.innerHeight - 100 : 620
        }
      });
    };

    // Scroll to bottom
    const scrollToBottom = () => {
      nextTick(() => {
        if (messagesContainer.value) {
          messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
        }
      });
    };

    // Initialize session
    const initSession = async () => {
      if (!agentId.value || !apiKey.value) {
        console.warn('[Widget Page] Missing agentId or apiKey');
        return;
      }
      
      try {
        const response = await axios.post(
          `${baseUrl.value}/api/agent/${agentId.value}/sessions`,
          { title: 'Widget Session' },
          {
            headers: {
              'Content-Type': 'application/json',
              'X-API-Key': apiKey.value,
            },
          }
        );
        sessionId.value = response.data?.id;
        console.log('[Widget Page] Session created:', sessionId.value);
        
        // Load preset questions
        loadPresetQuestions();
      } catch (error) {
        console.error('[Widget Page] Failed to create session:', error);
      }
    };

    // Load preset questions
    const loadPresetQuestions = async () => {
      if (!agentId.value || !apiKey.value) return;
      
      try {
        const response = await axios.get(
          `${baseUrl.value}/api/agent/${agentId.value}/preset-questions`,
          {
            headers: { 'X-API-Key': apiKey.value },
          }
        );
        presetQuestions.value = (response.data || []).filter((q: any) => q.isActive);
      } catch (error) {
        console.warn('[Widget Page] Failed to load preset questions:', error);
      }
    };

    // Send preset question
    const sendPresetQuestion = (question: string) => {
      userInput.value = question;
      sendMessage();
    };

    // Toggle node visibility
    const toggleNodeVisibility = (index: number) => {
      isNodeVisible.value[index] = !isNodeVisible.value[index];
    };

    // Send message
    const sendMessage = async () => {
      if (!userInput.value.trim() || isLoading.value || !sessionId.value) return;
      
      const messageContent = userInput.value.trim();
      userInput.value = '';
      
      // Add user message
      messages.value.push({
        role: 'user',
        content: messageContent,
      });
      scrollToBottom();
      
      isLoading.value = true;
      isStreaming.value = true;
      nodeBlocks.value = [];
      
      try {
        // Save user message
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
              'X-API-Key': apiKey.value,
            },
          }
        );
        
        // Start streaming
        const eventSource = new EventSource(
          `${baseUrl.value}/nl2sql/stream?sessionId=${sessionId.value}&question=${encodeURIComponent(messageContent)}&apiKey=${encodeURIComponent(apiKey.value)}`
        );
        
        let currentNodeName = '';
        let currentNodeIndex = -1;
        
        eventSource.onmessage = (event) => {
          try {
            const data: StreamNodeData = JSON.parse(event.data);
            
            if (data.nodeName !== currentNodeName) {
              currentNodeName = data.nodeName;
              currentNodeIndex = nodeBlocks.value.length;
              nodeBlocks.value.push([data]);
              isNodeVisible.value[currentNodeIndex] = true;
            } else if (currentNodeIndex >= 0) {
              nodeBlocks.value[currentNodeIndex] = [data];
            }
            
            scrollToBottom();
          } catch (e) {
            console.warn('[Widget Page] Parse error:', e);
          }
        };
        
        eventSource.onerror = () => {
          eventSource.close();
          isLoading.value = false;
          isStreaming.value = false;
          
          // Convert streaming content to final message
          if (nodeBlocks.value.length > 0) {
            const lastBlock = nodeBlocks.value[nodeBlocks.value.length - 1];
            if (lastBlock && lastBlock[0]) {
              const finalData = lastBlock[0];
              let messageType = 'text';
              if (finalData.textType === 'RESULT_SET') {
                messageType = 'result-set';
              } else if (finalData.textType === 'MARK_DOWN') {
                messageType = 'markdown-report';
              }
              
              messages.value.push({
                role: 'assistant',
                content: finalData.text || '',
                messageType,
              });
            }
          }
          
          nodeBlocks.value = [];
          scrollToBottom();
          
          // Notify parent of new message
          sendToParent({
            type: 'WIDGET_NEW_MESSAGE',
            payload: { hasNewMessage: true }
          });
        };
        
        eventSource.addEventListener('complete', () => {
          eventSource.close();
          isLoading.value = false;
          isStreaming.value = false;
        });
        
      } catch (error) {
        console.error('[Widget Page] Send message error:', error);
        isLoading.value = false;
        isStreaming.value = false;
        messages.value.push({
          role: 'assistant',
          content: '抱歉，发送消息失败，请稍后再试。',
        });
        scrollToBottom();
      }
    };

    // Watch messages length for auto-scroll
    watch(() => messages.value.length, () => {
      scrollToBottom();
    });

    // Lifecycle
    onMounted(() => {
      console.log('[Widget Page] Mounted, agentId:', agentId.value);
      
      // Get referrer origin
      const referrer = document.referrer;
      if (referrer) {
        try {
          parentOrigin.value = new URL(referrer).origin;
        } catch (e) {
          parentOrigin.value = '*';
        }
      }
      
      // Listen for messages from parent
      window.addEventListener('message', handleMessage);
      
      // Send ready signal to parent
      sendToParent({ type: 'WIDGET_READY' });
      
      // If not in iframe (direct access), initialize directly
      if (window.parent === window) {
        isReady.value = true;
        initSession();
      }
    });

    onUnmounted(() => {
      window.removeEventListener('message', handleMessage);
    });

    return {
      // State
      title,
      welcomeMessage,
      primaryColor,
      messages,
      userInput,
      isLoading,
      isStreaming,
      isMaximized,
      presetQuestions,
      nodeBlocks,
      isNodeVisible,
      messagesContainer,
      inputRef,
      inputPlaceholder,
      // Methods
      requestClose,
      toggleMaximize,
      sendMessage,
      sendPresetQuestion,
      toggleNodeVisibility,
    };
  },
});
</script>

<style scoped>
.widget-page {
  width: 100%;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #fff;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  overflow: hidden;
}

/* Header */
.widget-header {
  padding: 16px 20px;
  background: var(--primary-color, #409EFF);
  color: white;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.header-btn {
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

.header-btn:hover {
  background-color: rgba(255, 255, 255, 0.2);
}

/* Messages Area */
.widget-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #f5f7fa;
}

.welcome-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  padding: 30px 10px;
}

.welcome-message {
  text-align: center;
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
  background: white;
  padding: 16px 20px;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.preset-questions {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.preset-item {
  padding: 12px 16px;
  background: white;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  color: #333;
  transition: all 0.2s;
}

.preset-item:hover {
  border-color: var(--primary-color, #409EFF);
  color: var(--primary-color, #409EFF);
  background: #f0f7ff;
}

/* Messages */
.message-wrapper {
  margin-bottom: 16px;
}

.message {
  display: flex;
  margin-bottom: 12px;
}

.message.user {
  justify-content: flex-end;
}

.message.assistant {
  justify-content: flex-start;
}

.message-content {
  max-width: 80%;
  padding: 12px 16px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.message.user .message-content {
  background: var(--primary-color, #409EFF);
  color: white;
  border-bottom-right-radius: 4px;
}

.message.assistant .message-content {
  background: white;
  color: #333;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

/* Streaming */
.streaming-response {
  margin-bottom: 16px;
}

.streaming-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  color: #909399;
  font-size: 13px;
}

.loading-dot {
  width: 8px;
  height: 8px;
  background: var(--primary-color, #409EFF);
  border-radius: 50%;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.4; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.2); }
}

.node-block {
  background: white;
  border-radius: 8px;
  margin-bottom: 8px;
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

.node-header {
  padding: 10px 14px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  font-size: 13px;
  color: #666;
}

.node-header:hover {
  background: #f5f5f5;
}

.node-title {
  font-weight: 500;
}

.node-toggle {
  font-size: 10px;
  color: #999;
}

.node-content {
  padding: 12px 14px;
}

.node-text {
  font-size: 13px;
  line-height: 1.6;
  color: #333;
  white-space: pre-wrap;
}

/* Typing Indicator */
.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 8px 0;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  background: var(--primary-color, #409EFF);
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
  0%, 60%, 100% { opacity: 0.3; transform: translateY(0); }
  30% { opacity: 1; transform: translateY(-4px); }
}

/* Input Area */
.widget-input {
  display: flex;
  gap: 12px;
  padding: 16px 20px;
  background: white;
  border-top: 1px solid #e8e8e8;
  flex-shrink: 0;
}

.widget-input input {
  flex: 1;
  padding: 12px 16px;
  border: 1px solid #dcdfe6;
  border-radius: 24px;
  outline: none;
  font-size: 14px;
  transition: all 0.2s;
}

.widget-input input:focus {
  border-color: var(--primary-color, #409EFF);
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.1);
}

.widget-input input:disabled {
  background: #f5f7fa;
  cursor: not-allowed;
}

.send-btn {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--primary-color, #409EFF);
  color: white;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  box-shadow: 0 2px 6px rgba(64, 158, 255, 0.3);
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  background: #66b1ff;
  transform: translateY(-1px);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: none;
}

/* Result Set & Markdown */
.result-set-message,
.markdown-message {
  background: white;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

/* Scrollbar */
.widget-messages::-webkit-scrollbar {
  width: 6px;
}

.widget-messages::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.widget-messages::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.widget-messages::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}
</style>
