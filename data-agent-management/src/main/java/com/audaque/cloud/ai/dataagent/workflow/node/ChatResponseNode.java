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
package com.audaque.cloud.ai.dataagent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.audaque.cloud.ai.dataagent.util.ChatResponseUtil;
import com.audaque.cloud.ai.dataagent.util.FluxUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.audaque.cloud.ai.dataagent.constant.Constant.CHAT_RESPONSE_NODE;

/**
 * Node for handling chat or irrelevant intent responses.
 * Returns a friendly message when the user's input is not a data analysis
 * request.
 */
@Slf4j
@Component
public class ChatResponseNode implements NodeAction {

    private static final String DEFAULT_CHAT_RESPONSE = "您好！我是 AI 问数助手，专门帮助您进行数据查询和分析。" +
            "请尝试提出与数据相关的问题，例如：\n" +
            "- 查询某个时间段的销售数据\n" +
            "- 统计某个指标的趋势变化\n" +
            "- 分析数据之间的关联关系\n\n" +
            "如果您有具体的数据分析需求，请告诉我！";

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        log.info("ChatResponseNode: Generating response for chat/irrelevant intent");

        // Create display flux with friendly response
        Flux<ChatResponse> displayFlux = Flux.create(emitter -> {
            emitter.next(ChatResponseUtil.createResponse(DEFAULT_CHAT_RESPONSE));
            emitter.complete();
        });

        // Create generator using utility class
        Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                this.getClass(), state, v -> Map.of(), displayFlux);

        return Map.of(CHAT_RESPONSE_NODE, generator);
    }

}
