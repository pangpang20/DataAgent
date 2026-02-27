# Widget 嵌入系统实现指南（基于 PostMessage 通信）

## 1. 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    第三方网站 (Host Website)                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  widget.js (嵌入脚本)                                    │   │
│  │  - 创建浮动按钮                                          │   │
│  │  - 创建 iframe                                          │   │
│  │  - 处理拖拽交互                                          │   │
│  │  - 通过 postMessage 通信                                 │   │
│  └─────────────────────────────────────────────────────────┘   │
│                              │                                   │
│                              ▼                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  iframe (chat-widget-window)                            │   │
│  │  src: https://your-domain.com/widget/{id}               │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼ postMessage
┌─────────────────────────────────────────────────────────────────┐
│                    你的服务器 (Your Server)                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  /widget/[id] 页面                                       │   │
│  │  - ChatWidget 组件                                       │   │
│  │  - 处理 postMessage 通信                                 │   │
│  │  - 渲染聊天界面                                          │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## 2. 嵌入脚本 (widget.js)

**文件**: `public/widget.js`

```javascript
/**
 * Widget 嵌入脚本
 * 在 HTML 的 <body> 标签后引入
 * 
 * 配置方式：
 * <script>
 *   window.chatWidgetConfig = {
 *     id: 'your-widget-id',
 *     baseUrl: 'https://your-domain.com',
 *     draggable: true,
 *     position: 'bottom-right', // bottom-right, bottom-left, top-right, top-left
 *     primaryColor: '#155EEF',
 *     title: '智能问数',
 *     welcomeMessage: '您好，有什么可以帮助您？'
 *   };
 * </script>
 * <script src="https://your-domain.com/widget.js" defer></script>
 */

(function () {
  'use strict';

  // 配置常量
  const CONFIG_KEY = 'chatWidgetConfig';
  const BUTTON_ID = 'chat-widget-button';
  const IFRAME_ID = 'chat-widget-window';
  const CONTAINER_ID = 'chat-widget-container';

  // 获取配置
  const config = window[CONFIG_KEY];

  // 验证配置
  if (!config || !config.id) {
    console.error(`[ChatWidget] ${CONFIG_KEY} 未配置或缺少 id 字段`);
    return;
  }

  // 默认配置
  const defaultConfig = {
    baseUrl: '',
    draggable: true,
    position: 'bottom-right',
    primaryColor: '#155EEF',
    title: '智能问数',
    welcomeMessage: '',
    width: '380px',
    height: '600px',
    buttonSize: '56px',
    zIndex: 999999,
  };

  // 合并配置
  const finalConfig = { ...defaultConfig, ...config };

  // 状态
  let isOpen = false;
  let isDragging = false;
  let dragStartX = 0;
  let dragStartY = 0;
  let buttonX = 0;
  let buttonY = 0;
  let targetOrigin = '';

  // SVG 图标
  const ICONS = {
    open: `<svg width="28" height="28" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M8 12H8.01M12 12H12.01M16 12H16.01M21 12C21 16.4183 16.9706 20 12 20C10.4607 20 9.01172 19.6565 7.74467 19.0511L3 20L4.39499 16.28C3.51156 15.0423 3 13.5743 3 12C3 7.58172 7.02944 4 12 4C16.9706 4 21 7.58172 21 12Z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
    </svg>`,
    close: `<svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M18 6L6 18M6 6L18 18" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
    </svg>`
  };

  // 创建样式
  function createStyles() {
    const style = document.createElement('style');
    style.textContent = `
      #${CONTAINER_ID} {
        position: fixed;
        ${getPositionStyles(finalConfig.position)}
        z-index: ${finalConfig.zIndex};
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
      }

      #${BUTTON_ID} {
        width: ${finalConfig.buttonSize};
        height: ${finalConfig.buttonSize};
        border-radius: 50%;
        background-color: ${finalConfig.primaryColor};
        border: none;
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
        transition: transform 0.2s ease, box-shadow 0.2s ease;
        position: relative;
      }

      #${BUTTON_ID}:hover {
        transform: scale(1.05);
        box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
      }

      #${BUTTON_ID}:active {
        transform: scale(0.95);
      }

      #${BUTTON_ID}.dragging {
        cursor: grabbing;
        transition: none;
      }

      #${IFRAME_ID} {
        position: absolute;
        width: ${finalConfig.width};
        height: ${finalConfig.height};
        border: none;
        border-radius: 16px;
        box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
        background: white;
        display: none;
        overflow: hidden;
        ${getIframePositionStyles(finalConfig.position)}
      }

      #${IFRAME_ID}.open {
        display: block;
        animation: widgetSlideIn 0.3s ease;
      }

      @keyframes widgetSlideIn {
        from {
          opacity: 0;
          transform: translateY(20px) scale(0.95);
        }
        to {
          opacity: 1;
          transform: translateY(0) scale(1);
        }
      }

      @media (max-width: 480px) {
        #${IFRAME_ID} {
          width: calc(100vw - 32px);
          height: 70vh;
          right: 16px !important;
          left: 16px !important;
          bottom: calc(${finalConfig.buttonSize} + 24px) !important;
        }
      }
    `;
    document.head.appendChild(style);
  }

  // 获取位置样式
  function getPositionStyles(position) {
    const positions = {
      'bottom-right': 'right: 24px; bottom: 24px;',
      'bottom-left': 'left: 24px; bottom: 24px;',
      'top-right': 'right: 24px; top: 24px;',
      'top-left': 'left: 24px; top: 24px;',
    };
    return positions[position] || positions['bottom-right'];
  }

  // 获取 iframe 位置样式
  function getIframePositionStyles(position) {
    const positions = {
      'bottom-right': 'right: 0; bottom: calc(100% + 16px);',
      'bottom-left': 'left: 0; bottom: calc(100% + 16px);',
      'top-right': 'right: 0; top: calc(100% + 16px);',
      'top-left': 'left: 0; top: calc(100% + 16px);',
    };
    return positions[position] || positions['bottom-right'];
  }

  // 创建容器
  function createContainer() {
    const container = document.createElement('div');
    container.id = CONTAINER_ID;
    document.body.appendChild(container);
    return container;
  }

  // 创建 iframe
  function createIframe() {
    const iframe = document.createElement('iframe');
    iframe.id = IFRAME_ID;
    iframe.title = finalConfig.title;
    
    // 构建 URL
    const baseUrl = finalConfig.baseUrl || window.location.origin;
    const params = new URLSearchParams({
      title: encodeURIComponent(finalConfig.title),
      welcomeMessage: encodeURIComponent(finalConfig.welcomeMessage),
      primaryColor: finalConfig.primaryColor,
    });
    iframe.src = `${baseUrl}/widget/${finalConfig.id}?${params}`;
    
    // 设置允许权限
    iframe.allow = 'microphone; camera; fullscreen';
    
    return iframe;
  }

  // 创建按钮
  function createButton() {
    const button = document.createElement('button');
    button.id = BUTTON_ID;
    button.innerHTML = ICONS.open;
    button.setAttribute('aria-label', '打开聊天窗口');
    
    // 点击事件
    button.addEventListener('click', handleButtonClick);
    
    // 拖拽事件
    if (finalConfig.draggable) {
      enableDragging(button);
    }
    
    return button;
  }

  // 处理按钮点击
  function handleButtonClick(e) {
    if (isDragging) return;
    toggleChat();
  }

  // 切换聊天窗口
  function toggleChat() {
    const iframe = document.getElementById(IFRAME_ID);
    const button = document.getElementById(BUTTON_ID);
    
    isOpen = !isOpen;
    
    if (isOpen) {
      iframe.classList.add('open');
      button.innerHTML = ICONS.close;
      button.setAttribute('aria-label', '关闭聊天窗口');
      
      // 发送打开消息
      sendMessageToIframe({ type: 'WIDGET_OPEN' });
    } else {
      iframe.classList.remove('open');
      button.innerHTML = ICONS.open;
      button.setAttribute('aria-label', '打开聊天窗口');
      
      // 发送关闭消息
      sendMessageToIframe({ type: 'WIDGET_CLOSE' });
    }
  }

  // 启用拖拽
  function enableDragging(element) {
    let startX, startY, initialX, initialY;
    
    element.addEventListener('mousedown', startDrag);
    element.addEventListener('touchstart', startDrag, { passive: false });
    
    function startDrag(e) {
      if (isOpen) return; // 打开时不允许拖拽
      
      isDragging = false;
      const clientX = e.type === 'touchstart' ? e.touches[0].clientX : e.clientX;
      const clientY = e.type === 'touchstart' ? e.touches[0].clientY : e.clientY;
      
      startX = clientX;
      startY = clientY;
      
      const rect = element.getBoundingClientRect();
      initialX = rect.left;
      initialY = rect.top;
      
      document.addEventListener('mousemove', drag);
      document.addEventListener('touchmove', drag, { passive: false });
      document.addEventListener('mouseup', stopDrag);
      document.addEventListener('touchend', stopDrag);
    }
    
    function drag(e) {
      const clientX = e.type === 'touchmove' ? e.touches[0].clientX : e.clientX;
      const clientY = e.type === 'touchmove' ? e.touches[0].clientY : e.clientY;
      
      const deltaX = Math.abs(clientX - startX);
      const deltaY = Math.abs(clientY - startY);
      
      // 移动超过 5px 认为是拖拽
      if (deltaX > 5 || deltaY > 5) {
        isDragging = true;
        element.classList.add('dragging');
      }
      
      if (!isDragging) return;
      
      e.preventDefault();
      
      const newX = initialX + (clientX - startX);
      const newY = initialY + (clientY - startY);
      
      // 边界限制
      const maxX = window.innerWidth - element.offsetWidth;
      const maxY = window.innerHeight - element.offsetHeight;
      
      element.style.left = `${Math.max(0, Math.min(newX, maxX))}px`;
      element.style.top = `${Math.max(0, Math.min(newY, maxY))}px`;
      element.style.right = 'auto';
      element.style.bottom = 'auto';
    }
    
    function stopDrag() {
      setTimeout(() => {
        isDragging = false;
        element.classList.remove('dragging');
      }, 0);
      
      document.removeEventListener('mousemove', drag);
      document.removeEventListener('touchmove', drag);
      document.removeEventListener('mouseup', stopDrag);
      document.removeEventListener('touchend', stopDrag);
    }
  }

  // 发送消息到 iframe
  function sendMessageToIframe(message) {
    const iframe = document.getElementById(IFRAME_ID);
    if (!iframe || !iframe.contentWindow) return;
    
    const origin = targetOrigin || '*';
    iframe.contentWindow.postMessage(message, origin);
  }

  // 接收 iframe 消息
  function handleMessage(event) {
    // 验证来源
    if (targetOrigin && event.origin !== targetOrigin) return;
    
    const iframe = document.getElementById(IFRAME_ID);
    if (!iframe || event.source !== iframe.contentWindow) return;
    
    const { type, payload } = event.data;
    
    switch (type) {
      case 'WIDGET_READY':
        // 保存目标 origin
        targetOrigin = event.origin;
        // 发送初始化配置
        sendMessageToIframe({
          type: 'WIDGET_INIT',
          payload: {
            id: finalConfig.id,
            title: finalConfig.title,
            primaryColor: finalConfig.primaryColor,
            welcomeMessage: finalConfig.welcomeMessage,
          }
        });
        break;
        
      case 'WIDGET_CLOSE_REQUEST':
        // iframe 请求关闭
        if (isOpen) toggleChat();
        break;
        
      case 'WIDGET_NEW_MESSAGE':
        // 收到新消息，可以显示通知
        showNotification(payload);
        break;
        
      default:
        break;
    }
  }

  // 显示通知
  function showNotification(payload) {
    // 如果窗口未打开，可以显示红点或通知
    if (!isOpen) {
      const button = document.getElementById(BUTTON_ID);
      // 添加红点提示
      if (!button.querySelector('.notification-badge')) {
        const badge = document.createElement('span');
        badge.className = 'notification-badge';
        badge.style.cssText = `
          position: absolute;
          top: -2px;
          right: -2px;
          width: 12px;
          height: 12px;
          background: #ff4d4f;
          border-radius: 50%;
          border: 2px solid white;
        `;
        button.appendChild(badge);
      }
    }
  }

  // 清除通知
  function clearNotification() {
    const button = document.getElementById(BUTTON_ID);
    const badge = button?.querySelector('.notification-badge');
    if (badge) badge.remove();
  }

  // ESC 键关闭
  function handleKeyDown(e) {
    if (e.key === 'Escape' && isOpen) {
      toggleChat();
    }
  }

  // 初始化
  function init() {
    // 防止重复初始化
    if (document.getElementById(CONTAINER_ID)) return;
    
    createStyles();
    const container = createContainer();
    const iframe = createIframe();
    const button = createButton();
    
    container.appendChild(iframe);
    container.appendChild(button);
    
    // 监听消息
    window.addEventListener('message', handleMessage);
    
    // 监听键盘
    document.addEventListener('keydown', handleKeyDown);
    
    console.log('[ChatWidget] 初始化完成');
  }

  // 页面加载完成后初始化
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
```

## 3. iframe 内页面 (Widget Page)

**文件**: `app/widget/[id]/page.tsx` (Next.js 示例)

```typescript
'use client';

import React, { useEffect, useRef, useState } from 'react';
import { useParams, useSearchParams } from 'next/navigation';

// 消息类型定义
interface WidgetMessage {
  type: 'WIDGET_READY' | 'WIDGET_INIT' | 'WIDGET_OPEN' | 'WIDGET_CLOSE' | 
        'WIDGET_CLOSE_REQUEST' | 'WIDGET_NEW_MESSAGE' | 'CHAT_MESSAGE';
  payload?: any;
}

export default function WidgetPage() {
  const params = useParams();
  const searchParams = useSearchParams();
  const iframeRef = useRef<HTMLDivElement>(null);
  const [parentOrigin, setParentOrigin] = useState<string>('');
  const [isReady, setIsReady] = useState(false);
  const [config, setConfig] = useState<any>(null);
  const [messages, setMessages] = useState<any[]>([]);
  const [inputValue, setInputValue] = useState('');
  
  const widgetId = params.id as string;
  const title = searchParams.get('title') || '智能问数';
  const welcomeMessage = searchParams.get('welcomeMessage') || '';
  const primaryColor = searchParams.get('primaryColor') || '#155EEF';

  // 发送消息到父页面
  const sendToParent = (message: WidgetMessage) => {
    if (!parentOrigin) return;
    window.parent.postMessage(message, parentOrigin);
  };

  // 接收父页面消息
  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      // 安全：验证来源
      if (parentOrigin && event.origin !== parentOrigin) return;
      
      const { type, payload } = event.data;
      
      switch (type) {
        case 'WIDGET_INIT':
          setConfig(payload);
          setIsReady(true);
          break;
          
        case 'WIDGET_OPEN':
          // 窗口打开时的处理
          console.log('Widget opened');
          break;
          
        case 'WIDGET_CLOSE':
          // 窗口关闭时的处理
          console.log('Widget closed');
          break;
          
        default:
          break;
      }
    };

    window.addEventListener('message', handleMessage);
    
    // 发送就绪信号
    const referrer = document.referrer;
    const origin = referrer ? new URL(referrer).origin : '*';
    setParentOrigin(origin);
    
    sendToParent({ type: 'WIDGET_READY' });
    
    return () => {
      window.removeEventListener('message', handleMessage);
    };
  }, [parentOrigin]);

  // 发送消息
  const handleSend = () => {
    if (!inputValue.trim()) return;
    
    const newMessage = {
      id: Date.now(),
      content: inputValue,
      sender: 'user',
      timestamp: new Date().toISOString(),
    };
    
    setMessages(prev => [...prev, newMessage]);
    setInputValue('');
    
    // 通知父页面有新消息
    sendToParent({
      type: 'WIDGET_NEW_MESSAGE',
      payload: newMessage
    });
    
    // 模拟回复
    setTimeout(() => {
      const reply = {
        id: Date.now() + 1,
        content: '收到您的消息，客服会尽快回复。',
        sender: 'agent',
        timestamp: new Date().toISOString(),
      };
      setMessages(prev => [...prev, reply]);
    }, 1000);
  };

  // 关闭按钮
  const handleClose = () => {
    sendToParent({ type: 'WIDGET_CLOSE_REQUEST' });
  };

  return (
    <div 
      ref={iframeRef}
      style={{
        width: '100%',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        backgroundColor: '#fff',
        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      }}
    >
      {/* 头部 */}
      <div style={{
        padding: '16px 20px',
        backgroundColor: primaryColor,
        color: 'white',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
      }}>
        <div style={{ fontWeight: 600, fontSize: '16px' }}>
          {decodeURIComponent(title)}
        </div>
        <button
          onClick={handleClose}
          style={{
            background: 'none',
            border: 'none',
            color: 'white',
            cursor: 'pointer',
            padding: '4px',
            display: 'flex',
            alignItems: 'center',
          }}
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M18 6L6 18M6 6L18 18" />
          </svg>
        </button>
      </div>

      {/* 消息区域 */}
      <div style={{
        flex: 1,
        overflowY: 'auto',
        padding: '20px',
        backgroundColor: '#f5f5f5',
      }}>
        {/* 欢迎消息 */}
        {welcomeMessage && (
          <div style={{
            backgroundColor: 'white',
            padding: '12px 16px',
            borderRadius: '12px',
            marginBottom: '16px',
            boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
          }}>
            {decodeURIComponent(welcomeMessage)}
          </div>
        )}
        
        {/* 消息列表 */}
        {messages.map(msg => (
          <div
            key={msg.id}
            style={{
              display: 'flex',
              justifyContent: msg.sender === 'user' ? 'flex-end' : 'flex-start',
              marginBottom: '12px',
            }}
          >
            <div style={{
              maxWidth: '70%',
              padding: '12px 16px',
              borderRadius: msg.sender === 'user' ? '16px 16px 4px 16px' : '16px 16px 16px 4px',
              backgroundColor: msg.sender === 'user' ? primaryColor : 'white',
              color: msg.sender === 'user' ? 'white' : '#333',
              boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
            }}>
              {msg.content}
            </div>
          </div>
        ))}
      </div>

      {/* 输入区域 */}
      <div style={{
        padding: '16px 20px',
        borderTop: '1px solid #e8e8e8',
        backgroundColor: 'white',
        display: 'flex',
        gap: '12px',
      }}>
        <input
          type="text"
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyPress={(e) => e.key === 'Enter' && handleSend()}
          placeholder="输入消息..."
          style={{
            flex: 1,
            padding: '12px 16px',
            border: '1px solid #d9d9d9',
            borderRadius: '24px',
            outline: 'none',
            fontSize: '14px',
          }}
        />
        <button
          onClick={handleSend}
          style={{
            width: '44px',
            height: '44px',
            borderRadius: '50%',
            backgroundColor: primaryColor,
            border: 'none',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2">
            <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" />
          </svg>
        </button>
      </div>
    </div>
  );
}
```

## 4. 代理配置 (允许 iframe 嵌入)

**文件**: `middleware.ts` (Next.js)

```typescript
import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

export function middleware(request: NextRequest) {
  const response = NextResponse.next();
  const { pathname } = request.nextUrl;
  
  // 允许 widget 页面被嵌入 iframe
  if (pathname.startsWith('/widget')) {
    // 移除 X-Frame-Options 或设置为允许
    response.headers.delete('X-Frame-Options');
    // 或者使用 CSP frame-ancestors
    response.headers.set(
      'Content-Security-Policy',
      "frame-ancestors *;"
    );
  } else {
    // 其他页面禁止嵌入（防止点击劫持）
    response.headers.set('X-Frame-Options', 'DENY');
  }
  
  return response;
}

export const config = {
  matcher: ['/((?!api|_next/static|_next/image|favicon.ico).*)'],
};
```

## 5. 使用示例

### 5.1 基础用法

```html
<!DOCTYPE html>
<html>
<head>
  <title>我的网站</title>
</head>
<body>
  <h1>欢迎访问</h1>
  
  <!-- Widget 配置 -->
  <script>
    window.chatWidgetConfig = {
      id: 'support-001',
      baseUrl: 'https://your-domain.com',
      title: '智能问数',
      welcomeMessage: '您好！有什么可以帮助您？',
      primaryColor: '#155EEF',
      draggable: true,
    };
  </script>
  <script src="https://your-domain.com/widget.js" defer></script>
</body>
</html>
```

### 5.2 完整配置

```html
<script>
  window.chatWidgetConfig = {
    // 必需
    id: 'support-001',
    
    // 可选
    baseUrl: 'https://your-domain.com',
    draggable: true,
    position: 'bottom-right', // bottom-right | bottom-left | top-right | top-left
    primaryColor: '#155EEF',
    title: '智能问数',
    welcomeMessage: '您好，有什么可以帮助您？',
    width: '380px',
    height: '600px',
    buttonSize: '56px',
    zIndex: 999999,
  };
</script>
<script src="https://your-domain.com/widget.js" defer></script>
```

## 6. 消息协议

| 消息类型               | 方向            | 描述            |
| ---------------------- | --------------- | --------------- |
| `WIDGET_READY`         | iframe → Parent | iframe 加载完成 |
| `WIDGET_INIT`          | Parent → iframe | 发送初始化配置  |
| `WIDGET_OPEN`          | Parent → iframe | 窗口打开通知    |
| `WIDGET_CLOSE`         | Parent → iframe | 窗口关闭通知    |
| `WIDGET_CLOSE_REQUEST` | iframe → Parent | 请求关闭窗口    |
| `WIDGET_NEW_MESSAGE`   | iframe → Parent | 新消息通知      |

## 7. 文件结构

```
project/
├── public/
│   └── widget.js              # 嵌入脚本
├── app/
│   └── widget/
│       └── [id]/
│           └── page.tsx       # Widget 页面
├── middleware.ts              # 代理配置
└── ...
```

## 8. 安全建议

1. **验证来源**：生产环境应验证 `event.origin`
2. **使用 HTTPS**：确保通信安全
3. **限制域名**：CSP `frame-ancestors` 指定允许嵌入的域名
4. **Token 验证**：后端验证 widget ID 的有效性
