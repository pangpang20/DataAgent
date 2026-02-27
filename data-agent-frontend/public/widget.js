/**
 * DataAgent Widget 嵌入脚本 (PostMessage 通信版)
 * 
 * Copyright 2024-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0
 * 
 * 使用方式:
 * <script>
 *   window.DataAgentConfig = {
 *     agentId: 'your-agent-id',
 *     apiKey: 'your-api-key',
 *     baseUrl: 'https://your-domain.com',
 *     title: 'AI 问数助手',
 *     welcomeMessage: '您好，有什么可以帮助您？',
 *     primaryColor: '#409EFF',
 *     position: 'bottom-right',
 *     draggable: true
 *   };
 * </script>
 * <script src="https://your-domain.com/widget.js" defer></script>
 */

(function () {
    'use strict';

    // Configuration constants
    const CONFIG_KEY = 'DataAgentConfig';
    const BUTTON_ID = 'data-agent-widget-button';
    const IFRAME_ID = 'data-agent-widget-iframe';
    const CONTAINER_ID = 'data-agent-widget-container';

    // Get configuration
    const config = window[CONFIG_KEY];

    // Validate configuration
    if (!config || !config.agentId) {
        console.error('[DataAgent Widget] Configuration not found or missing agentId');
        return;
    }

    // Auto-detect baseUrl from the script src if not configured
    function detectBaseUrl() {
        // First check if baseUrl is explicitly configured
        if (config.baseUrl) {
            return config.baseUrl;
        }
        // Try to extract from the widget.js script src
        const scripts = document.querySelectorAll('script[src*="widget.js"]');
        for (const script of scripts) {
            const src = script.src;
            if (src) {
                try {
                    const url = new URL(src);
                    return url.origin;
                } catch (e) {
                    // Ignore parse errors
                }
            }
        }
        // Fallback to current page origin
        return window.location.origin;
    }

    // Default configuration
    const defaultConfig = {
        baseUrl: detectBaseUrl(),
        draggable: true,
        position: 'bottom-right',
        primaryColor: '#409EFF',
        title: 'AI 问数助手',
        welcomeMessage: '您好！我是 AI 问数助手，有什么可以帮您的吗？',
        width: '420px',
        height: '620px',
        buttonSize: '56px',
        zIndex: 2147483647,
        apiKey: ''
    };

    // Merge configuration
    const finalConfig = { ...defaultConfig, ...config };

    // State
    let isOpen = false;
    let isDragging = false;
    let hasDragged = false;
    let targetOrigin = '';

    // SVG Icons
    const ICONS = {
        chat: `<svg width="28" height="28" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M8 12H8.01M12 12H12.01M16 12H16.01M21 12C21 16.4183 16.9706 20 12 20C10.4607 20 9.01172 19.6565 7.74467 19.0511L3 20L4.39499 16.28C3.51156 15.0423 3 13.5743 3 12C3 7.58172 7.02944 4 12 4C16.9706 4 21 7.58172 21 12Z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
    </svg>`,
        close: `<svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M18 6L6 18M6 6L18 18" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
    </svg>`
    };

    // Create styles
    function createStyles() {
        const style = document.createElement('style');
        style.id = 'data-agent-widget-styles';
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
        outline: none;
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

      #${BUTTON_ID} .notification-badge {
        position: absolute;
        top: -2px;
        right: -2px;
        width: 14px;
        height: 14px;
        background: #ff4d4f;
        border-radius: 50%;
        border: 2px solid white;
        animation: pulse 2s infinite;
      }

      @keyframes pulse {
        0% { transform: scale(1); }
        50% { transform: scale(1.1); }
        100% { transform: scale(1); }
      }

      #${IFRAME_ID} {
        position: absolute;
        width: ${finalConfig.width};
        height: ${finalConfig.height};
        max-width: calc(100vw - 32px);
        max-height: calc(100vh - 100px);
        border: none;
        border-radius: 16px;
        box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
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

      /* Mobile responsive */
      @media (max-width: 480px) {
        #${IFRAME_ID} {
          width: calc(100vw - 32px);
          height: 70vh;
          left: 16px !important;
          right: 16px !important;
          bottom: calc(${finalConfig.buttonSize} + 24px) !important;
          top: auto !important;
        }
      }
    `;
        document.head.appendChild(style);
    }

    // Get position styles for container
    function getPositionStyles(position) {
        const positions = {
            'bottom-right': 'right: 24px; bottom: 24px;',
            'bottom-left': 'left: 24px; bottom: 24px;',
            'top-right': 'right: 24px; top: 24px;',
            'top-left': 'left: 24px; top: 24px;',
        };
        return positions[position] || positions['bottom-right'];
    }

    // Get position styles for iframe
    function getIframePositionStyles(position) {
        const positions = {
            'bottom-right': 'right: 0; bottom: calc(100% + 16px);',
            'bottom-left': 'left: 0; bottom: calc(100% + 16px);',
            'top-right': 'right: 0; top: calc(100% + 16px);',
            'top-left': 'left: 0; top: calc(100% + 16px);',
        };
        return positions[position] || positions['bottom-right'];
    }

    // Create container
    function createContainer() {
        const container = document.createElement('div');
        container.id = CONTAINER_ID;
        document.body.appendChild(container);
        return container;
    }

    // Create iframe
    function createIframe() {
        const iframe = document.createElement('iframe');
        iframe.id = IFRAME_ID;
        iframe.title = finalConfig.title;

        // Build URL with parameters
        const baseUrl = finalConfig.baseUrl.replace(/\/$/, '');
        const params = new URLSearchParams({
            title: finalConfig.title,
            welcomeMessage: finalConfig.welcomeMessage,
            primaryColor: finalConfig.primaryColor,
            apiKey: finalConfig.apiKey
        });
        iframe.src = `${baseUrl}/widget/${finalConfig.agentId}?${params}`;

        // Set permissions
        iframe.allow = 'microphone; camera; fullscreen; clipboard-write';
        iframe.setAttribute('allowfullscreen', 'true');

        return iframe;
    }

    // Create button
    function createButton() {
        const button = document.createElement('button');
        button.id = BUTTON_ID;
        button.innerHTML = ICONS.chat;
        button.setAttribute('aria-label', '打开聊天窗口');

        // Click event
        button.addEventListener('click', handleButtonClick);

        // Dragging events
        if (finalConfig.draggable) {
            enableDragging(button);
        }

        return button;
    }

    // Handle button click
    function handleButtonClick(e) {
        if (hasDragged) {
            hasDragged = false;
            return;
        }
        toggleChat();
    }

    // Toggle chat window
    function toggleChat() {
        const iframe = document.getElementById(IFRAME_ID);
        const button = document.getElementById(BUTTON_ID);

        isOpen = !isOpen;

        if (isOpen) {
            iframe.classList.add('open');
            button.innerHTML = ICONS.close;
            button.setAttribute('aria-label', '关闭聊天窗口');
            clearNotification();

            // Send open message to iframe
            sendMessageToIframe({ type: 'WIDGET_OPEN' });
        } else {
            iframe.classList.remove('open');
            button.innerHTML = ICONS.chat;
            button.setAttribute('aria-label', '打开聊天窗口');

            // Send close message to iframe
            sendMessageToIframe({ type: 'WIDGET_CLOSE' });
        }
    }

    // Enable dragging
    function enableDragging(element) {
        let startX, startY, initialX, initialY;

        element.addEventListener('mousedown', startDrag);
        element.addEventListener('touchstart', startDrag, { passive: false });

        function startDrag(e) {
            if (isOpen) return; // Disable dragging when open

            isDragging = false;
            hasDragged = false;

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

            // Consider it dragging if moved more than 5px
            if (deltaX > 5 || deltaY > 5) {
                isDragging = true;
                hasDragged = true;
                element.classList.add('dragging');
            }

            if (!isDragging) return;

            e.preventDefault();

            const container = document.getElementById(CONTAINER_ID);
            const newX = initialX + (clientX - startX);
            const newY = initialY + (clientY - startY);

            // Boundary limits
            const maxX = window.innerWidth - element.offsetWidth;
            const maxY = window.innerHeight - element.offsetHeight;

            container.style.left = `${Math.max(0, Math.min(newX, maxX))}px`;
            container.style.top = `${Math.max(0, Math.min(newY, maxY))}px`;
            container.style.right = 'auto';
            container.style.bottom = 'auto';
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

            // Save position to localStorage
            const container = document.getElementById(CONTAINER_ID);
            if (container.style.left) {
                localStorage.setItem('data-agent-widget-position', JSON.stringify({
                    left: container.style.left,
                    top: container.style.top
                }));
            }
        }
    }

    // Restore saved position
    function restorePosition() {
        try {
            const saved = localStorage.getItem('data-agent-widget-position');
            if (saved) {
                const pos = JSON.parse(saved);
                const container = document.getElementById(CONTAINER_ID);
                if (container && pos.left && pos.top) {
                    container.style.left = pos.left;
                    container.style.top = pos.top;
                    container.style.right = 'auto';
                    container.style.bottom = 'auto';
                }
            }
        } catch (e) {
            console.warn('[DataAgent Widget] Failed to restore position:', e);
        }
    }

    // Send message to iframe
    function sendMessageToIframe(message) {
        const iframe = document.getElementById(IFRAME_ID);
        if (!iframe || !iframe.contentWindow) return;

        const origin = targetOrigin || '*';
        iframe.contentWindow.postMessage(message, origin);
    }

    // Handle message from iframe
    function handleMessage(event) {
        const iframe = document.getElementById(IFRAME_ID);
        if (!iframe || event.source !== iframe.contentWindow) return;

        // Validate origin in production
        if (targetOrigin && event.origin !== targetOrigin) return;

        const { type, payload } = event.data || {};

        switch (type) {
            case 'WIDGET_READY':
                // Save target origin
                targetOrigin = event.origin;
                // Send initialization config
                sendMessageToIframe({
                    type: 'WIDGET_INIT',
                    payload: {
                        agentId: finalConfig.agentId,
                        apiKey: finalConfig.apiKey,
                        title: finalConfig.title,
                        primaryColor: finalConfig.primaryColor,
                        welcomeMessage: finalConfig.welcomeMessage,
                        baseUrl: finalConfig.baseUrl
                    }
                });
                console.log('[DataAgent Widget] iframe ready, config sent');
                break;

            case 'WIDGET_CLOSE_REQUEST':
                // iframe requests to close
                if (isOpen) toggleChat();
                break;

            case 'WIDGET_NEW_MESSAGE':
                // New message notification
                showNotification(payload);
                break;

            case 'WIDGET_RESIZE':
                // Handle resize request from iframe
                const iframeEl = document.getElementById(IFRAME_ID);
                const buttonEl = document.getElementById(BUTTON_ID);
                const containerEl = document.getElementById(CONTAINER_ID);
                if (iframeEl && payload) {
                    if (payload.maximized) {
                        // Maximize: 50% width, 80% height, centered
                        const maxWidth = Math.floor(window.innerWidth * 0.5);
                        const maxHeight = Math.floor(window.innerHeight * 0.8);
                        iframeEl.style.width = maxWidth + 'px';
                        iframeEl.style.height = maxHeight + 'px';
                        iframeEl.style.position = 'fixed';
                        iframeEl.style.left = '50%';
                        iframeEl.style.top = '50%';
                        iframeEl.style.transform = 'translate(-50%, -50%)';
                        iframeEl.style.right = 'auto';
                        iframeEl.style.bottom = 'auto';
                        iframeEl.classList.add('maximized');

                        // Move button to bottom-right of iframe
                        if (buttonEl && containerEl) {
                            containerEl.style.position = 'fixed';
                            containerEl.style.left = `calc(50% + ${maxWidth / 2}px - 28px)`;
                            containerEl.style.top = `calc(50% + ${maxHeight / 2}px + 16px)`;
                            containerEl.style.right = 'auto';
                            containerEl.style.bottom = 'auto';
                        }
                    } else {
                        // Restore to default size
                        iframeEl.style.width = finalConfig.width;
                        iframeEl.style.height = finalConfig.height;
                        iframeEl.style.position = 'absolute';
                        iframeEl.style.left = '';
                        iframeEl.style.top = '';
                        iframeEl.style.transform = '';
                        iframeEl.style.right = '0';
                        iframeEl.style.bottom = 'calc(100% + 16px)';
                        iframeEl.classList.remove('maximized');

                        // Restore button to default position
                        if (containerEl) {
                            containerEl.style.position = 'fixed';
                            containerEl.style.left = '';
                            containerEl.style.top = '';
                            containerEl.style.right = '24px';
                            containerEl.style.bottom = '24px';
                        }
                    }
                }
                break;

            default:
                break;
        }
    }

    // Show notification badge
    function showNotification(payload) {
        if (!isOpen) {
            const button = document.getElementById(BUTTON_ID);
            if (button && !button.querySelector('.notification-badge')) {
                const badge = document.createElement('span');
                badge.className = 'notification-badge';
                button.appendChild(badge);
            }
        }
    }

    // Clear notification badge
    function clearNotification() {
        const button = document.getElementById(BUTTON_ID);
        const badge = button?.querySelector('.notification-badge');
        if (badge) badge.remove();
    }

    // ESC key to close
    function handleKeyDown(e) {
        if (e.key === 'Escape' && isOpen) {
            toggleChat();
        }
    }

    // Initialize widget
    function init() {
        // Prevent duplicate initialization
        if (document.getElementById(CONTAINER_ID)) {
            console.warn('[DataAgent Widget] Already initialized');
            return;
        }

        createStyles();
        const container = createContainer();
        const iframe = createIframe();
        const button = createButton();

        container.appendChild(iframe);
        container.appendChild(button);

        // Restore saved position
        restorePosition();

        // Listen for messages from iframe
        window.addEventListener('message', handleMessage);

        // Listen for ESC key
        document.addEventListener('keydown', handleKeyDown);

        console.log('[DataAgent Widget] Initialized successfully');
        console.log('[DataAgent Widget] Config:', {
            agentId: finalConfig.agentId,
            baseUrl: finalConfig.baseUrl,
            position: finalConfig.position
        });
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
