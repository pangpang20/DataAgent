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
package com.audaque.cloud.ai.dataagent.service.agent;

import com.audaque.cloud.ai.dataagent.dto.agent.AgentAuthGrantRequest;
import com.audaque.cloud.ai.dataagent.dto.agent.AgentAuthResponse;

import java.util.List;

public interface AgentAuthService {

	void grantAccess(AgentAuthGrantRequest request);

	void revokeAccess(Long agentId, Long userId);

	void updateAccess(AgentAuthGrantRequest request);

	List<AgentAuthResponse> listAgentAuth(Long agentId);

	String checkAccess(Long agentId, Long userId);

}
