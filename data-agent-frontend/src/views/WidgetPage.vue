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
        <!-- Result Set Message (with AI avatar) -->
        <div v-if="msg.messageType === 'result-set'" class="message assistant history-message">
          <div class="message-avatar">
            <span class="avatar assistant-avatar">AI</span>
          </div>
          <div class="message-content">
            <ResultSetDisplay
              v-if="msg.content"
              :resultData="JSON.parse(msg.content)"
              :pageSize="10"
            />
          </div>
        </div>
        <!-- Markdown Report Message (with AI avatar) -->
        <div v-else-if="msg.messageType === 'markdown-report'" class="message assistant history-message">
          <div class="message-avatar">
            <span class="avatar assistant-avatar">AI</span>
          </div>
          <div class="message-content">
            <div class="markdown-report-block">
              <div class="markdown-report-header">
                <div class="report-info">
                  <svg viewBox="0 0 24 24" width="18" height="18" fill="#409EFF">
                    <path d="M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z"/>
                  </svg>
                  <span>Markdown 报告已生成</span>
                </div>
                <button class="download-btn primary" @click="downloadMarkdown(msg.content)">
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
                    <path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
                  </svg>
                  下载Markdown报告
                </button>
              </div>
              <div class="markdown-report-content">
                <Markdown>{{ msg.content }}</Markdown>
              </div>
            </div>
          </div>
        </div>
        <!-- Normal Text Message -->
        <div v-else :class="['message', msg.role]">
          <div class="message-avatar">
            <span v-if="msg.role === 'user'" class="avatar user-avatar">我</span>
            <span v-else class="avatar assistant-avatar">AI</span>
          </div>
          <div class="message-content">
            <div class="message-text" v-html="msg.content"></div>
          </div>
        </div>
      </div>

      <!-- Streaming Response / Process Info Display -->
      <div v-if="isStreaming || nodeBlocks.length > 0" class="message-wrapper streaming-wrapper">
        <div class="message assistant streaming-message">
          <div class="message-avatar">
            <span class="avatar assistant-avatar">AI</span>
          </div>
          <div class="message-content streaming-response">
            <div v-if="isStreaming" class="streaming-header">
              <span class="loading-dot"></span>
              <span>AI 正在思考...</span>
            </div>
            <div v-for="(nodeBlock, index) in nodeBlocks" :key="index" class="node-block">
              <div class="node-header" @click="toggleNodeVisibility(index)">
                <span class="node-title">{{ nodeBlock[0]?.nodeName || 'Processing' }}</span>
                <span class="node-toggle">{{ isNodeVisible[index] ? '▼' : '▶' }}</span>
              </div>
              <div v-show="isNodeVisible[index]" class="node-content">
                <!-- Result Set -->
                <ResultSetDisplay
                  v-if="nodeBlock[0]?.textType === 'RESULT_SET' && nodeBlock[0]?.text"
                  :resultData="JSON.parse(nodeBlock[0].text)"
                  :pageSize="10"
                />
                <!-- Markdown Report -->
                <div v-else-if="nodeBlock[0]?.textType === 'MARK_DOWN' && nodeBlock[0]?.text" class="markdown-report-block">
                  <div class="markdown-report-header">
                    <div class="report-info">
                      <svg viewBox="0 0 24 24" width="18" height="18" fill="#409EFF">
                        <path d="M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z"/>
                      </svg>
                      <span>Markdown 报告已生成</span>
                    </div>
                    <button class="download-btn primary" @click="downloadMarkdown(nodeBlock[0].text)">
                      <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
                        <path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
                      </svg>
                      下载Markdown报告
                    </button>
                  </div>
                  <div class="markdown-report-content">
                    <Markdown :generating="isStreaming">
                      {{ nodeBlock[0].text }}
                    </Markdown>
                  </div>
                </div>
                <!-- Python/SQL/JSON Code -->
                <div 
                  v-else-if="['PYTHON', 'SQL', 'JSON'].includes(nodeBlock[0]?.textType) && nodeBlock[0]?.text" 
                  class="code-block"
                >
                  <div class="code-header">
                    <span class="code-lang">{{ nodeBlock[0].textType }}</span>
                    <button class="copy-btn" @click="copyCode(nodeBlock[0].text)">复制</button>
                  </div>
                  <pre class="code-content"><code>{{ nodeBlock[0].text }}</code></pre>
                </div>
                <!-- Other content -->
                <div v-else class="node-text" v-html="nodeBlock[0]?.text || ''"></div>
              </div>
            </div>
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
    // Content accumulators for streaming data (like AgentRun.vue)
    const markdownReportContent = ref<string>('');
    const resultSetContent = ref<string>('');
    const chatResponseContent = ref<string>('');

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
        payload: { maximized: isMaximized.value }
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
      
      // Try to restore session from sessionStorage (each tab has independent session)
      const storageKey = `widget_session_${agentId.value}`;
      const savedSessionId = sessionStorage.getItem(storageKey);
      
      if (savedSessionId) {
        // Verify session exists and load history
        try {
          const response = await axios.get(
            `${baseUrl.value}/api/sessions/${savedSessionId}/messages`,
            {
              headers: { 'X-API-Key': apiKey.value },
            }
          );
          // Session is valid, restore it
          sessionId.value = savedSessionId;
          console.log('[Widget Page] Session restored:', sessionId.value);
          
          // Load history messages
          loadHistoryMessages(response.data || []);
          loadPresetQuestions();
          return;
        } catch (e) {
          console.warn('[Widget Page] Saved session invalid, creating new one');
          sessionStorage.removeItem(storageKey);
        }
      }
      
      // Create new session
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
        
        // Save sessionId to sessionStorage (survives refresh, but new tab gets new session)
        if (sessionId.value) {
          sessionStorage.setItem(storageKey, sessionId.value.toString());
          
          try {
            await axios.put(
              `${baseUrl.value}/api/sessions/${sessionId.value}/rename`,
              null,
              {
                params: { title: `Widget Session ${sessionId.value}` },
                headers: { 'X-API-Key': apiKey.value },
              }
            );
          } catch (e) {
            console.warn('[Widget Page] Failed to update session title:', e);
          }
        }
        
        // Load preset questions
        loadPresetQuestions();
      } catch (error) {
        console.error('[Widget Page] Failed to create session:', error);
      }
    };

    // Load history messages from database
    const loadHistoryMessages = (historyMessages: any[]) => {
      if (!historyMessages || historyMessages.length === 0) return;
      
      console.log('[Widget Page] Loading history messages:', historyMessages.length);
      
      // Clear current messages
      messages.value = [];
      
      // Process each message
      historyMessages.forEach((msg: any) => {
        messages.value.push({
          id: msg.id,
          role: msg.role,
          content: msg.content,
          messageType: msg.messageType || 'text',
        });
      });
      
      scrollToBottom();
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

    // Save assistant message to database
    const saveAssistantMessage = async (content: string, messageType: string) => {
      if (!sessionId.value || !content) return;
      try {
        await axios.post(
          `${baseUrl.value}/api/sessions/${sessionId.value}/messages`,
          {
            role: 'assistant',
            content,
            messageType,
          },
          {
            headers: {
              'Content-Type': 'application/json',
              'X-API-Key': apiKey.value,
            },
          }
        );
      } catch (e) {
        console.warn('[Widget Page] Failed to save assistant message:', e);
      }
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
        let isCompleted = false;  // Flag to prevent duplicate processing
        // Reset content accumulators
        markdownReportContent.value = '';
        resultSetContent.value = '';
        chatResponseContent.value = '';
        
        eventSource.onmessage = (event) => {
          try {
            const data: StreamNodeData = JSON.parse(event.data);
            console.log(`[Widget Page] Received: node=${data.nodeName}, type=${data.textType}, text=${(data.text || '').substring(0, 50)}...`);
            
            // Accumulate content for MARK_DOWN and RESULT_SET types (like AgentRun.vue)
            if (data.textType === 'MARK_DOWN') {
              markdownReportContent.value += data.text || '';
              console.log(`[Widget Page] MARK_DOWN accumulated: ${markdownReportContent.value.length} chars`);
              // Update display block
              const existingBlock = nodeBlocks.value.find(
                (block) => block[0]?.nodeName === data.nodeName && block[0]?.textType === 'MARK_DOWN'
              );
              if (existingBlock) {
                existingBlock[0].text = markdownReportContent.value;
              } else {
                currentNodeName = data.nodeName;
                currentNodeIndex = nodeBlocks.value.length;
                nodeBlocks.value.push([{ ...data, text: markdownReportContent.value }]);
                isNodeVisible.value[currentNodeIndex] = true;
              }
            } else if (data.textType === 'RESULT_SET') {
              // RESULT_SET is usually sent as a complete JSON, not chunked
              resultSetContent.value = data.text || '';
              console.log(`[Widget Page] RESULT_SET received: ${resultSetContent.value.length} chars`);
              const existingBlock = nodeBlocks.value.find(
                (block) => block[0]?.nodeName === data.nodeName && block[0]?.textType === 'RESULT_SET'
              );
              if (existingBlock) {
                existingBlock[0].text = resultSetContent.value;
              } else {
                currentNodeName = data.nodeName;
                currentNodeIndex = nodeBlocks.value.length;
                nodeBlocks.value.push([{ ...data, text: resultSetContent.value }]);
                isNodeVisible.value[currentNodeIndex] = true;
              }
            } else {
              // For other types (including ChatResponseNode), accumulate and display
              // Check if this is ChatResponseNode for saving
              if (data.nodeName === 'ChatResponseNode') {
                chatResponseContent.value += data.text || '';
                console.log(`[Widget Page] ChatResponseNode accumulated: ${chatResponseContent.value.length} chars`);
              }
              
              // Update display
              if (data.nodeName !== currentNodeName) {
                currentNodeName = data.nodeName;
                currentNodeIndex = nodeBlocks.value.length;
                nodeBlocks.value.push([data]);
                isNodeVisible.value[currentNodeIndex] = true;
              } else if (currentNodeIndex >= 0) {
                // Append text content instead of replacing
                const currentBlock = nodeBlocks.value[currentNodeIndex];
                if (currentBlock && currentBlock[0]) {
                  currentBlock[0].text = (currentBlock[0].text || '') + (data.text || '');
                }
              }
            }
            
            scrollToBottom();
          } catch (e) {
            console.warn('[Widget Page] Parse error:', e);
          }
        };
        
        // Helper function to handle stream completion
        const handleStreamComplete = (source: string) => {
          if (isCompleted) {
            console.log(`[Widget Page] Stream already completed, ignoring ${source} event`);
            return;
          }
          isCompleted = true;
          console.log(`[Widget Page] Stream completed via ${source}, markdownReport: ${markdownReportContent.value.length} chars, resultSet: ${resultSetContent.value.length} chars, chatResponse: ${chatResponseContent.value.length} chars`);
          
          eventSource.close();
          isLoading.value = false;
          isStreaming.value = false;
          
          // Save MARK_DOWN content to database only (already displayed in nodeBlocks)
          if (markdownReportContent.value) {
            console.log('[Widget Page] Saving markdown report to database (not adding to messages to avoid duplicate)');
            saveAssistantMessage(markdownReportContent.value, 'markdown-report');
          }
          
          // Save RESULT_SET content to database only (already displayed in nodeBlocks)
          if (resultSetContent.value) {
            console.log('[Widget Page] Saving result set to database (not adding to messages to avoid duplicate)');
            saveAssistantMessage(resultSetContent.value, 'result-set');
          }
          
          // Save ChatResponseNode content (for chat/casual responses)
          if (chatResponseContent.value && !markdownReportContent.value && !resultSetContent.value) {
            console.log('[Widget Page] Saving chat response to database');
            saveAssistantMessage(chatResponseContent.value, 'text');
          }
          
          // Do not clear nodeBlocks, keep process info visible like AgentRun.vue
          console.log(`[Widget Page] Keeping ${nodeBlocks.value.length} nodeBlocks visible`);
          scrollToBottom();
          
          // Notify parent of new message
          sendToParent({
            type: 'WIDGET_NEW_MESSAGE',
            payload: { hasNewMessage: true }
          });
        };
        
        eventSource.onerror = () => {
          handleStreamComplete('onerror');
        };
        
        eventSource.addEventListener('complete', () => {
          handleStreamComplete('complete');
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

    // Download markdown report
    const downloadMarkdown = (content: string) => {
      if (!content) return;
      const blob = new Blob([content], { type: 'text/markdown' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `report_${new Date().getTime()}.md`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    };

    // Copy code to clipboard
    const copyCode = async (code: string) => {
      if (!code) return;
      try {
        await navigator.clipboard.writeText(code);
      } catch (e) {
        console.warn('[Widget Page] Failed to copy:', e);
      }
    };

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
      downloadMarkdown,
      copyCode,
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
  width: 100%;
  margin-bottom: 16px;
}

.message {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
  max-width: 85%;
}

.message.user {
  margin-left: auto;
  flex-direction: row-reverse;
}

.message.assistant {
  margin-right: auto;
}

.message-avatar {
  flex-shrink: 0;
}

.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 500;
}

.user-avatar {
  background: var(--primary-color, #409EFF);
  color: white;
}

.assistant-avatar {
  background: #f0f0f0;
  color: #666;
}

.message-content {
  flex: 1;
  min-width: 0;
}

.message.user .message-content {
  flex: none;  /* User message content should not expand */
}

.message-text {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.message.user .message-text {
  background: var(--primary-color, #409EFF);
  color: white;
  border-bottom-right-radius: 4px;
}

.message.assistant .message-text {
  background: white;
  color: #303133;
  border: 1px solid #e8e8e8;
  border-bottom-left-radius: 4px;
}

/* Streaming */
.streaming-wrapper {
  width: 100%;
}

.streaming-message {
  max-width: 100% !important;
  width: 100%;
}

.history-message {
  max-width: 100% !important;
  width: 100%;
}

.history-message .message-avatar {
  align-self: flex-start;
  margin-top: 8px;
}

.history-message .message-content {
  flex: 1;
  width: 100%;
}

.streaming-message .message-avatar {
  align-self: flex-start;
  margin-top: 8px;
}

.streaming-response {
  display: flex;
  flex-direction: column;
  width: 100%;
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
  width: 100%;
  box-sizing: border-box;
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
.result-set-message {
  background: white;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

/* Markdown Report Message */
.markdown-report-message {
  background: white;
  border: 1px solid #e8e8e8;
  border-radius: 12px;
  padding: 16px;
}

.markdown-report-block {
  width: 100%;
  box-sizing: border-box;
  background: white;
  border: 1px solid #e8e8e8;
  border-radius: 12px;
  padding: 16px;
}

.markdown-report-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.report-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #409eff;
  font-size: 14px;
  font-weight: 500;
}

.markdown-report-content {
  margin-top: 12px;
}

.download-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: #f0f0f0;
  color: #666;
  border: none;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.download-btn:hover {
  background: #e0e0e0;
}

.download-btn.primary {
  background: var(--primary-color, #409EFF);
  color: white;
}

.download-btn.primary:hover {
  opacity: 0.9;
}

/* Code Block */
.code-block {
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  border-radius: 8px;
  overflow: hidden;
}

.code-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f1f3f5;
  border-bottom: 1px solid #e1e4e8;
}

.code-lang {
  font-size: 12px;
  font-weight: 500;
  color: #666;
  text-transform: uppercase;
}

.copy-btn {
  padding: 4px 10px;
  background: transparent;
  border: 1px solid #d1d5da;
  border-radius: 4px;
  font-size: 11px;
  color: #24292e;
  cursor: pointer;
  transition: all 0.2s;
}

.copy-btn:hover {
  background: #f3f4f6;
  border-color: #c6cbd1;
}

.code-content {
  margin: 0;
  padding: 12px;
  overflow-x: auto;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 12px;
  line-height: 1.5;
  color: #24292e;
}

.code-content code {
  background: transparent;
  padding: 0;
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
