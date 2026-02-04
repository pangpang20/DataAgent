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
package com.audaque.cloud.ai.dataagent.controller;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Configuration Controller - Provides backend configuration for frontend
 */
@RestController
@RequestMapping("/api/config")
@CrossOrigin(origins = "*")
public class ConfigController {

    @Value("${server.port:8065}")
    private Integer serverPort;

    @Value("${server.address:0.0.0.0}")
    private String serverAddress;

    /**
     * Get backend base URL configuration
     * Returns the actual backend URL based on request
     */
    @GetMapping("/baseUrl")
    public BaseUrlResponse getBaseUrl(HttpServletRequest request) {
        // Get host from request (preserves IP/domain from client's perspective)
        String scheme = request.getScheme(); // http or https
        String serverName = request.getServerName(); // IP or domain
        int port = request.getServerPort(); // actual port

        // Build base URL
        String baseUrl = scheme + "://" + serverName;
        if ((scheme.equals("http") && port != 80) || (scheme.equals("https") && port != 443)) {
            baseUrl += ":" + port;
        }

        BaseUrlResponse response = new BaseUrlResponse();
        response.setBaseUrl(baseUrl);
        response.setPort(port);
        response.setScheme(scheme);
        response.setHost(serverName);

        return response;
    }

    @Data
    public static class BaseUrlResponse {

        private String baseUrl;

        private int port;

        private String scheme;

        private String host;

    }

}
