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
    <!-- 浮动按钮 - 支持拖拽 -->
    <div 
      v-if="!isOpen" 
      class="chat-button" 
      :style="buttonPositionStyle"
      @mousedown.prevent="startDrag"
      @touchstart.prevent="startDragTouch"
    >
      <img src="/logo.png" alt="AI助手" class="chat-button-logo" draggable="false" />
    </div>

    <!-- 遮罩层 -->
    <div v-if="isOpen && !isMaximized" class="overlay" @click="toggleChat"></div>

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
  import { defineComponent, ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue';
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
      
      // Drag support - 拖拽支持
      const isDragging = ref(false);
      const hasDragged = ref(false);
      const buttonPosition = ref({ x: 0, y: 0 });
      const dragStart = ref({ x: 0, y: 0 });
      
      // Initialize button position from localStorage or default to bottom-right
      const initButtonPosition = () => {
        const saved = localStorage.getItem('widget-button-position');
        console.log('[Widget] Initializing button position, saved:', saved);
        if (saved) {
          try {
            buttonPosition.value = JSON.parse(saved);
            console.log('[Widget] Loaded saved position:', buttonPosition.value);
          } catch (e) {
            // Default to bottom-right
            console.warn('[Widget] Failed to parse saved position, using default');
            buttonPosition.value = { 
              x: window.innerWidth - 70, 
              y: window.innerHeight - 70 
            };
          }
        } else {
          // Default to bottom-right (20px from edges)
          console.log('[Widget] No saved position, using default bottom-right');
          buttonPosition.value = { 
            x: window.innerWidth - 70, 
            y: window.innerHeight - 70 
          };
        }
        console.log('[Widget] Button position set to:', buttonPosition.value);
      };
      
      const baseUrl = computed(() => props.config.baseUrl || window.location.origin);

      const positionStyle = computed(() => {
        const pos = props.config.position || 'bottom-right';
        if (pos === 'bottom-left') {
          return { left: '20px', right: 'auto', bottom: '20px' };
        }
        return { right: '20px', left: 'auto', bottom: '20px' };
      });

      const buttonStyle = computed(() => ({}));

      // Button position style for draggable button
      const buttonPositionStyle = computed(() => ({
        left: `${buttonPosition.value.x}px`,
        top: `${buttonPosition.value.y}px`,
        right: 'auto',
        bottom: 'auto',
        cursor: isDragging.value ? 'grabbing' : 'grab',
      }));

      // Drag event handlers
      const startDrag = (e: MouseEvent) => {
        console.log('[Widget] Mouse down - starting drag tracking');
        console.log('[Widget] Current button position:', buttonPosition.value);
        console.log('[Widget] Mouse position:', { clientX: e.clientX, clientY: e.clientY });
        
        isDragging.value = true;
        hasDragged.value = false;
        dragStart.value = {
          x: e.clientX - buttonPosition.value.x,
          y: e.clientY - buttonPosition.value.y,
        };
        console.log('[Widget] Drag offset calculated:', dragStart.value);
        
        document.addEventListener('mousemove', onDrag);
        document.addEventListener('mouseup', stopDrag);
        console.log('[Widget] Event listeners attached (mousemove, mouseup)');
      };

      // Touch support for mobile
      const startDragTouch = (e: TouchEvent) => {
        if (e.touches.length !== 1) return;
        const touch = e.touches[0];
        console.log('[Widget] Touch start - starting drag tracking');
        console.log('[Widget] Touch position:', { clientX: touch.clientX, clientY: touch.clientY });
        
        isDragging.value = true;
        hasDragged.value = false;
        dragStart.value = {
          x: touch.clientX - buttonPosition.value.x,
          y: touch.clientY - buttonPosition.value.y,
        };
        
        document.addEventListener('touchmove', onDragTouch, { passive: false });
        document.addEventListener('touchend', stopDragTouch);
        console.log('[Widget] Touch event listeners attached');
      };

      const onDrag = (e: MouseEvent) => {
        if (!isDragging.value) return;
        
        if (!hasDragged.value) {
          console.log('[Widget] First mouse move detected - marking as dragged');
        }
        hasDragged.value = true;
        const newX = Math.max(0, Math.min(window.innerWidth - 50, e.clientX - dragStart.value.x));
        const newY = Math.max(0, Math.min(window.innerHeight - 50, e.clientY - dragStart.value.y));
        
        buttonPosition.value = { x: newX, y: newY };
      };

      const onDragTouch = (e: TouchEvent) => {
        if (!isDragging.value || e.touches.length !== 1) return;
        e.preventDefault();
        
        if (!hasDragged.value) {
          console.log('[Widget] First touch move detected - marking as dragged');
        }
        const touch = e.touches[0];
        hasDragged.value = true;
        const newX = Math.max(0, Math.min(window.innerWidth - 50, touch.clientX - dragStart.value.x));
        const newY = Math.max(0, Math.min(window.innerHeight - 50, touch.clientY - dragStart.value.y));
        
        buttonPosition.value = { x: newX, y: newY };
      };

      const stopDrag = () => {
        console.log('[Widget] Mouse up - stopping drag');
        console.log('[Widget] hasDragged:', hasDragged.value);
        console.log('[Widget] Final position:', buttonPosition.value);
        
        const wasDragging = isDragging.value;
        isDragging.value = false;
        document.removeEventListener('mousemove', onDrag);
        document.removeEventListener('mouseup', stopDrag);
        
        // Save position to localStorage
        localStorage.setItem('widget-button-position', JSON.stringify(buttonPosition.value));
        console.log('[Widget] Position saved to localStorage');
        
        // If not dragged, open chat (click behavior)
        if (!hasDragged.value && wasDragging) {
          console.log('[Widget] No drag detected - treating as click, opening chat');
          toggleChat();
        } else {
          console.log('[Widget] Drag completed - chat will not open');
        }
      };

      const stopDragTouch = () => {
        console.log('[Widget] Touch end - stopping drag');
        console.log('[Widget] hasDragged:', hasDragged.value);
        
        const wasDragging = isDragging.value;
        isDragging.value = false;
        document.removeEventListener('touchmove', onDragTouch);
        document.removeEventListener('touchend', stopDragTouch);
        
        // Save position to localStorage
        localStorage.setItem('widget-button-position', JSON.stringify(buttonPosition.value));
        
        // If not dragged, open chat (tap behavior)
        if (!hasDragged.value && wasDragging) {
          console.log('[Widget] No drag detected - treating as tap, opening chat');
          toggleChat();
        }
      };

      const handleButtonClick = () => {
        // Only toggle chat if not dragged
        if (!hasDragged.value) {
          toggleChat();
        }
        hasDragged.value = false;
      };

      const headerStyle = computed(() => ({
        backgroundColor: props.config.primaryColor || '#409EFF',
      }));

      const windowStyle = computed(() => {
        const baseStyle: Record<string, string> = {
          borderColor: props.config.primaryColor || '#409EFF',
        };
        
        if (isMaximized.value) {
          return {
            ...baseStyle,
            position: 'fixed' as const,
            top: '0',
            left: '0',
            right: '0',
            bottom: '0',
            width: '100vw',
            height: '100vh',
            borderRadius: '0',
            zIndex: '2147483647',
          };
        }
        
        // Centered modal window
        return {
          ...baseStyle,
          position: 'fixed' as const,
          top: '50%',
          left: '50%',
          transform: 'translate(-50%, -50%)',
          zIndex: '2147483647',
        };
      });

      const sendButtonStyle = computed(() => ({
        backgroundColor: props.config.primaryColor || '#409EFF',
      }));

      const toggleChat = () => {
        console.log('[Widget] toggleChat called, current isOpen:', isOpen.value);
        isOpen.value = !isOpen.value;
        console.log('[Widget] Chat is now:', isOpen.value ? 'OPEN' : 'CLOSED');
        if (isOpen.value) {
          isMaximized.value = false;
          if (!sessionId.value) {
            console.log('[Widget] No session, creating new session...');
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

          const eventSource = new EventSource(
            `${baseUrl.value}/api/stream/search?agentId=${props.config.agentId}&query=${encodeURIComponent(messageContent)}&nl2sqlOnly=true`
          );

          const nodeMap: Record<string, StreamNodeData[]> = {};
          
          eventSource.onmessage = (event) => {
            try {
              const data = JSON.parse(event.data);
              
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

      // Initialize on mount
      onMounted(() => {
        console.log('[Widget] Component mounted');
        console.log('[Widget] Window size:', { width: window.innerWidth, height: window.innerHeight });
        initButtonPosition();
        // Handle window resize
        window.addEventListener('resize', initButtonPosition);
        console.log('[Widget] Resize listener attached');
      });

      onUnmounted(() => {
        window.removeEventListener('resize', initButtonPosition);
        document.removeEventListener('mousemove', onDrag);
        document.removeEventListener('mouseup', stopDrag);
        document.removeEventListener('touchmove', onDragTouch);
        document.removeEventListener('touchend', stopDragTouch);
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
        buttonPositionStyle,
        headerStyle,
        windowStyle,
        sendButtonStyle,
        toggleChat,
        toggleMaximize,
        sendMessage,
        sendPresetQuestion,
        toggleNodeVisibility,
        startDrag,
        startDragTouch,
        handleButtonClick,
      };
    },
  });
</script>

<style scoped>
  .chat-widget {
    position: fixed;
    z-index: 2147483647;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  }

  /* 遮罩层样式 */
  .overlay {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.5);
    z-index: 2147483646;
    animation: fadeIn 0.2s ease-in-out;
  }

  @keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
  }

  .chat-button {
    position: fixed;
    width: 50px;
    height: 50px;
    border-radius: 50%;
    background-color: white;
    color: white;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: grab;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    transition: box-shadow 0.2s;
    overflow: hidden;
    z-index: 2147483647;
    user-select: none;
  }

  .chat-button:active {
    cursor: grabbing;
  }

  .chat-button-logo {
    width: 50px;
    height: 50px;
    object-fit: contain;
  }

  .chat-button:hover {
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
  }

  .chat-window {
    position: fixed;
    width: 500px;
    height: 600px;
    max-width: 90vw;
    max-height: 90vh;
    background: white;
    border-radius: 12px;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
    display: flex;
    flex-direction: column;
    overflow: hidden;
    border: 2px solid #409EFF;
    z-index: 2147483647;
    animation: slideIn 0.3s ease-out;
  }

  @keyframes slideIn {
    from {
      opacity: 0;
      transform: translate(-50%, -45%);
    }
    to {
      opacity: 1;
      transform: translate(-50%, -50%);
    }
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
    padding: 20px;
    background: #f8f9fa;
  }

  .welcome-section {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 24px;
    padding: 40px 20px;
  }

  .welcome-message {
    text-align: center;
    color: #606266;
    font-size: 15px;
    line-height: 1.6;
  }

  .preset-questions {
    width: 100%;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .preset-question-item {
    background: white;
    border: 1px solid #e8e8e8;
    border-radius: 8px;
    padding: 14px 18px;
    cursor: pointer;
    transition: all 0.3s ease;
    color: #303133;
    font-size: 14px;
    line-height: 1.5;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  }

  .preset-question-item:hover {
    background: #ecf5ff;
    border-color: #409EFF;
    color: #409EFF;
    transform: translateY(-2px);
    box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
  }

  .message-wrapper {
    margin-bottom: 16px;
  }

  .result-set-message,
  .markdown-report-message {
    background: white;
    border: 1px solid #e8e8e8;
    border-radius: 12px;
    padding: 16px;
    margin-bottom: 16px;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  }

  /* 流式响应样式 - 参考 AgentRun */
  .streaming-response {
    background: white;
    border: 1px solid #e8e8e8;
    border-radius: 8px;
    padding: 16px;
    margin-bottom: 16px;
  }

  .streaming-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
    padding-bottom: 8px;
    border-bottom: 1px solid #f0f0f0;
    font-weight: 500;
    color: #409EFF;
  }

  .loading-dot {
    animation: spin 1s linear infinite;
  }

  @keyframes spin {
    from {
      transform: rotate(0deg);
    }
    to {
      transform: rotate(360deg);
    }
  }

  /* 节点样式 - 参考 AgentRun */
  .node-block {
    background: #f8f9fa;
    border: 1px solid #e8e8e8;
    border-radius: 8px;
    overflow: hidden;
    margin-bottom: 12px;
    transition: all 0.3s ease;
  }

  .node-block:hover {
    border-color: #409EFF;
    box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
  }

  .node-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: #ecf5ff;
    padding: 12px 16px;
    font-weight: 600;
    color: #409EFF;
    border-bottom: 1px solid #e8e8e8;
    font-size: 14px;
    cursor: pointer;
    user-select: none;
  }

  .node-title {
    flex: 1;
    margin-right: 10px;
  }

  .node-toggle {
    font-size: 12px;
    color: #409EFF;
  }

  .node-content {
    padding: 16px;
    line-height: 1.6;
    font-size: 14px;
    background: white;
  }

  .node-text {
    color: #303133;
    white-space: pre-wrap;
    word-wrap: break-word;
  }

  .message {
    display: flex;
    gap: 12px;
    margin-bottom: 16px;
  }

  .message.user {
    justify-content: flex-end;
  }

  .message.assistant {
    justify-content: flex-start;
  }

  .message-content {
    max-width: 75%;
    padding: 12px 16px;
    border-radius: 12px;
    word-wrap: break-word;
    line-height: 1.5;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  }

  .message.user .message-content {
    background: #409EFF;
    color: white;
  }

  .message.assistant .message-content {
    background: white;
    color: #303133;
    border: 1px solid #e8e8e8;
  }

  .typing-indicator {
    display: flex;
    gap: 4px;
    padding: 8px 0;
  }

  .typing-indicator span {
    width: 8px;
    height: 8px;
    background-color: #409EFF;
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
    gap: 12px;
    padding: 16px;
    background: white;
    border-top: 1px solid #e8e8e8;
  }

  .chat-input input {
    flex: 1;
    padding: 12px 16px;
    border: 1px solid #dcdfe6;
    border-radius: 20px;
    outline: none;
    font-size: 14px;
    transition: all 0.3s;
  }

  .chat-input input:focus {
    border-color: #409EFF;
    box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.1);
  }

  .chat-input input:disabled {
    background-color: #f5f7fa;
    cursor: not-allowed;
  }

  .chat-input button {
    width: 44px;
    height: 44px;
    border-radius: 50%;
    background-color: #409EFF;
    color: white;
    border: none;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.3s;
    box-shadow: 0 2px 6px rgba(64, 158, 255, 0.3);
  }

  .chat-input button:hover:not(:disabled) {
    background-color: #66b1ff;
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(64, 158, 255, 0.4);
  }

  .chat-input button:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    box-shadow: none;
  }

  .chat-messages::-webkit-scrollbar {
    width: 6px;
  }

  .chat-messages::-webkit-scrollbar-track {
    background: #f1f1f1;
    border-radius: 3px;
  }

  .chat-messages::-webkit-scrollbar-thumb {
    background: #c1c1c1;
    border-radius: 3px;
    transition: background 0.3s;
  }

  .chat-messages::-webkit-scrollbar-thumb:hover {
    background: #a8a8a8;
  }
</style>
