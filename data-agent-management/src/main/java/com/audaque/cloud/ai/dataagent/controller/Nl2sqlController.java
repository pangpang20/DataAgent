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

import com.audaque.cloud.ai.dataagent.dto.GraphRequest;
import com.audaque.cloud.ai.dataagent.entity.Agent;
import com.audaque.cloud.ai.dataagent.service.agent.AgentService;
import com.audaque.cloud.ai.dataagent.service.graph.GraphService;
import com.audaque.cloud.ai.dataagent.vo.GraphNodeResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Sinks;

import static com.audaque.cloud.ai.dataagent.constant.Constant.STREAM_EVENT_COMPLETE;
import static com.audaque.cloud.ai.dataagent.constant.Constant.STREAM_EVENT_ERROR;

/**
 * NL2SQL Stream Controller for Widget integration.
 * Provides API Key based authentication for external widget embedding.
 */
@Slf4j
@RestController
@AllArgsConstructor
@CrossOrigin(origins = "*")
@RequestMapping("/nl2sql")
public class Nl2sqlController {

    private final AgentService agentService;

    private final GraphService graphService;

    /**
     * Stream endpoint for widget chat. Authenticates via API Key and streams
     * NL2SQL results.
     * 
     * @param sessionId The chat session ID (used as threadId)
     * @param question  The user's question
     * @param apiKey    The API key for authentication
     * @param response  HTTP response for setting headers
     * @return SSE stream of graph node responses
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Object streamSearch(@RequestParam("sessionId") String sessionId, @RequestParam("question") String question,
            @RequestParam("apiKey") String apiKey, HttpServletResponse response) {

        // Validate API Key
        Agent agent = agentService.findByApiKey(apiKey);
        if (agent == null) {
            log.warn("Invalid API Key attempt: {}", apiKey.substring(0, Math.min(10, apiKey.length())) + "...");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or disabled API Key");
        }

        log.info("Widget stream request - agentId: {}, sessionId: {}, question: {}", agent.getId(), sessionId,
                question);

        // Set SSE-related HTTP headers
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/event-stream");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Headers", "Cache-Control");

        Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().unicast().onBackpressureBuffer();

        GraphRequest request = GraphRequest.builder()
                .agentId(String.valueOf(agent.getId()))
                .threadId(sessionId)
                .query(question)
                .humanFeedback(false)
                .humanFeedbackContent(null)
                .rejectedPlan(false)
                .nl2sqlOnly(false)
                .plainReport(true)
                .build();

        graphService.graphStreamProcess(sink, request);

        return sink.asFlux().filter(sse -> {
            // Pass through complete/error events regardless of text content
            if (STREAM_EVENT_COMPLETE.equals(sse.event()) || STREAM_EVENT_ERROR.equals(sse.event())) {
                return true;
            }
            // Filter out empty text messages
            return sse.data() != null && sse.data().getText() != null && !sse.data().getText().isEmpty();
        })
                .doOnSubscribe(subscription -> log.info("Widget client subscribed, sessionId: {}", sessionId))
                .doOnCancel(() -> {
                    log.info("Widget client disconnected, sessionId: {}", sessionId);
                    if (sessionId != null) {
                        graphService.stopStreamProcessing(sessionId);
                    }
                })
                .doOnError(e -> {
                    log.error("Error during widget streaming, sessionId: {}: ", sessionId, e);
                    if (sessionId != null) {
                        graphService.stopStreamProcessing(sessionId);
                    }
                })
                .doOnComplete(() -> log.info("Widget stream completed, sessionId: {}", sessionId));
    }

}
