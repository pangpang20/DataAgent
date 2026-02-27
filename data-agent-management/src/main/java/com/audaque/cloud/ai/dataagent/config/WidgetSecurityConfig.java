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
package com.audaque.cloud.ai.dataagent.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.io.IOException;

/**
 * Widget iframe embedding security configuration.
 * Allows specific paths to be embedded in iframes from any origin.
 */
@Slf4j
@Configuration
public class WidgetSecurityConfig {

    /**
     * Filter to handle X-Frame-Options and CSP headers for widget embedding.
     */
    @Bean
    public FilterRegistrationBean<Filter> widgetFrameOptionsFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();

        registration.setFilter(new Filter() {
            @Override
            public void init(FilterConfig filterConfig) throws ServletException {
                log.info("Widget frame options filter initialized");
            }

            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {

                HttpServletRequest httpRequest = (HttpServletRequest) request;
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                String path = httpRequest.getRequestURI();

                // Allow iframe embedding for widget-related paths and static resources
                if (isWidgetPath(path)) {
                    // Remove X-Frame-Options to allow embedding from any origin
                    // Alternatively, use frame-ancestors in CSP for more control
                    httpResponse.setHeader("Content-Security-Policy", "frame-ancestors *;");
                    // Some browsers still check X-Frame-Options, so don't set it (or set to
                    // ALLOWALL)
                    // Not setting X-Frame-Options allows embedding by default
                } else {
                    // For other paths, deny framing to prevent clickjacking
                    httpResponse.setHeader("X-Frame-Options", "SAMEORIGIN");
                }

                chain.doFilter(request, response);
            }

            @Override
            public void destroy() {
                // No cleanup needed
            }

            /**
             * Check if the request path is widget-related and should allow iframe
             * embedding.
             */
            private boolean isWidgetPath(String path) {
                return path != null && (path.startsWith("/widget") || path.equals("/widget.js")
                        || path.startsWith("/assets/") || path.equals("/logo.png") || path.equals("/favicon.png")
                        || path.startsWith("/nl2sql/") || path.startsWith("/api/"));
            }
        });

        registration.addUrlPatterns("/*");
        registration.setName("widgetFrameOptionsFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);

        return registration;
    }

}
