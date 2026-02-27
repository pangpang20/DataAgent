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

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA Fallback Controller.
 * Handles frontend routes by forwarding to index.html for Vue Router to
 * process.
 */
@Controller
public class SpaController {

    /**
     * Forward widget routes to index.html for Vue Router.
     * This enables the /widget/{id} route to work properly in production.
     */
    @GetMapping("/widget/**")
    public String widget() {
        return "forward:/index.html";
    }

    /**
     * Forward agent run routes to index.html.
     */
    @GetMapping("/agent/**")
    public String agent() {
        return "forward:/index.html";
    }

    /**
     * Forward agents list route to index.html.
     */
    @GetMapping("/agents")
    public String agents() {
        return "forward:/index.html";
    }

    /**
     * Forward model-config route to index.html.
     */
    @GetMapping("/model-config")
    public String modelConfig() {
        return "forward:/index.html";
    }

}
