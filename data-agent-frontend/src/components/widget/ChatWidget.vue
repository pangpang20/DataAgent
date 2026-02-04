<template>
  <div class="chat-widget" :class="{ open: isOpen }" :style="positionStyle">
    <!-- 浮动按钮 -->
    <div v-if="!isOpen" class="chat-button" @click="toggleChat" :style="buttonStyle">
      <img src="/logo.png" alt="AI助手" class="chat-button-logo" />
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
        </div>

        <!-- 消息显示 -->
        <div v-for="(msg, index) in messages" :key="msg.id || index" class="message-wrapper">
          <div v-if="msg.messageType === 'result-set'" class="result-set-message">
            <ResultSetDisplay v-if="msg.content" :resultData="JSON.parse(msg.content)" :pageSize="10" />
          </div>
          <div v-else-if="msg.messageType === 'markdown-report'" class="markdown-report-message">
            <Markdown>{{ msg.content }}</Markdown>
          </div>
          <div v-else :class="['message', msg.role]">
            <div class="message-content" v-html="msg.content"></div>
          </div>
        </div>

        <!-- 流式响应 -->
        <div v-if="isStreaming" class="streaming-response">
          <div class="streaming-header">
            <span class="loading-dot">●</span>
            <span>AI 正在思考...</span>
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
        <input v-model="userInput" type="text" placeholder="输入消息..." @keypress.enter="sendMessage" :disabled="isLoading" />
        <button @click="sendMessage" :disabled="!userInput.trim() || isLoading" :style="sendButtonStyle">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
            <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
  import { defineComponent, ref, computed, nextTick, watch } from 'vue';
  import axios from 'axios';
  import ResultSetDisplay from '../run/ResultSetDisplay.vue';
  import Markdown from '../run/Markdown.vue';

  export default defineComponent({
    name: 'ChatWidget',
    components: {
      ResultSetDisplay,
      Markdown,
    },
    props: {
      config: {
        type: Object as () => { title: string; primaryColor: string; welcomeMessage: string },
        required: true,
      },
    },
    setup(props) {
      const isOpen = ref(false);
      const isMaximized = ref(false);
      const messages = ref([]);
      const userInput = ref('');
      const isLoading = ref(false);

      const positionStyle = computed(() => ({
        right: '20px',
        left: 'auto',
        bottom: '20px',
      }));

      const headerStyle = computed(() => ({
        backgroundColor: props.config.primaryColor || '#409EFF',
      }));

      const windowStyle = computed(() => ({
        position: 'fixed',
        top: '50%',
        left: '50%',
        transform: 'translate(-50%, -50%)',
        width: '400px',
        height: '500px',
        background: 'white',
        borderRadius: '12px',
        boxShadow: '0 8px 24px rgba(0, 0, 0, 0.2)',
        zIndex: 2147483647,
        borderColor: props.config.primaryColor || '#409EFF',
      }));

      const sendButtonStyle = computed(() => ({
        backgroundColor: props.config.primaryColor || '#409EFF',
      }));

      const toggleChat = () => {
        isOpen.value = !isOpen.value;
      };

      const toggleMaximize = () => {
        isMaximized.value = !isMaximized.value;
      };

      const sendMessage = async () => {
        if (!userInput.value.trim() || isLoading.value) return;

        const messageContent = userInput.value.trim();
        userInput.value = '';

        messages.value.push({
          role: 'user',
          content: messageContent,
        });

        isLoading.value = true;
        try {
          await axios.post('/api/send-message', { message: messageContent });

          // Simulate receiving AI response
          setTimeout(() => {
            messages.value.push({
              role: 'assistant',
              content: '这是AI助手的回复。',
            });
            isLoading.value = false;
          }, 1000);
        } catch (error) {
          console.error('Error sending message:', error);
          isLoading.value = false;
        }
      };

      return {
        isOpen,
        isMaximized,
        messages,
        userInput,
        isLoading,
        positionStyle,
        headerStyle,
        windowStyle,
        sendButtonStyle,
        toggleChat,
        toggleMaximize,
        sendMessage,
      };
    },
  });
</script>

<style scoped>
  .chat-widget {
    position: fixed;
    z-index: 2147483647;
    font-family: Arial, sans-serif;
  }

  .chat-button {
    width: 60px;
    height: 60px;
    border-radius: 50%;
    background-color: #409EFF;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
    cursor: pointer;
    transition: transform 0.2s, box-shadow 0.2s;
  }

  .chat-button-logo {
    width: 30px;
    height: 30px;
  }

  .chat-button:hover {
    transform: scale(1.1);
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.25);
  }

  .overlay {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.5);
    z-index: 2147483646;
  }

  .chat-window {
    width: 400px;
    height: 500px;
    position: fixed;
    background-color: white;
    border-radius: 12px;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  .chat-header {
    background-color: #409EFF;
    color: white;
    padding: 20px;
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .chat-title {
    font-size: 16px;
  }

  .header-buttons {
    display: flex;
    gap: 8px;
  }

  .chat-messages {
    flex: 1;
    padding: 20px;
    overflow-y: auto;
    background-color: #f5f7fa;
  }

  .message {
    margin-bottom: 12px;
  }

  .message-content {
    max-width: 80%;
    padding: 12px;
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

  .chat-input {
    padding: 20px;
    background-color: white;
    border-top: 1px solid #e5e7eb;
    display: flex;
    gap: 10px;
    align-items: center;
  }

  .chat-input input {
    flex: 1;
    padding: 10px 14px;
    border: 1px solid #dcdfe6;
    border-radius: 20px;
    outline: none;
    font-size: 14px;
  }

  .chat-input button {
    background-color: #409EFF;
    color: white;
    border: none;
    cursor: pointer;
    padding: 12px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
  }
</style>
