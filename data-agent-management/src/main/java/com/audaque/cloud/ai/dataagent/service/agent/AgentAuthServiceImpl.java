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
import com.audaque.cloud.ai.dataagent.entity.SysAgentAuth;
import com.audaque.cloud.ai.dataagent.entity.SysUser;
import com.audaque.cloud.ai.dataagent.exception.BizException;
import com.audaque.cloud.ai.dataagent.mapper.SysAgentAuthMapper;
import com.audaque.cloud.ai.dataagent.mapper.SysUserMapper;
import com.audaque.cloud.ai.dataagent.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentAuthServiceImpl implements AgentAuthService {

	private final SysAgentAuthMapper sysAgentAuthMapper;

	private final SysUserMapper sysUserMapper;

	@Override
	public void grantAccess(AgentAuthGrantRequest request) {
		SysAgentAuth existing = sysAgentAuthMapper.findByAgentIdAndUserId(request.getAgentId(), request.getUserId());
		if (existing != null) {
			throw new BizException(400020, "该用户已有此Agent的访问权限");
		}

		Long currentUserId = SecurityUtils.getCurrentUserId();

		SysAgentAuth auth = SysAgentAuth.builder()
			.agentId(request.getAgentId())
			.userId(request.getUserId())
			.permissionLevel(request.getPermissionLevel())
			.grantedBy(currentUserId)
			.grantedTime(LocalDateTime.now())
			.createTime(LocalDateTime.now())
			.updateTime(LocalDateTime.now())
			.build();

		sysAgentAuthMapper.insert(auth);
	}

	@Override
	public void revokeAccess(Long agentId, Long userId) {
		sysAgentAuthMapper.deleteByAgentIdAndUserId(agentId, userId);
	}

	@Override
	public void updateAccess(AgentAuthGrantRequest request) {
		Long currentUserId = SecurityUtils.getCurrentUserId();
		sysAgentAuthMapper.updatePermissionLevel(request.getAgentId(), request.getUserId(),
				request.getPermissionLevel(), currentUserId, LocalDateTime.now());
	}

	@Override
	public List<AgentAuthResponse> listAgentAuth(Long agentId) {
		List<SysAgentAuth> auths = sysAgentAuthMapper.findByAgentId(agentId);

		return auths.stream().map(auth -> {
			SysUser user = sysUserMapper.findById(auth.getUserId());
			SysUser granter = auth.getGrantedBy() != null ? sysUserMapper.findById(auth.getGrantedBy()) : null;

			return AgentAuthResponse.builder()
				.id(auth.getId())
				.agentId(auth.getAgentId())
				.userId(auth.getUserId())
				.username(user != null ? user.getUsername() : null)
				.nickname(user != null ? user.getNickname() : null)
				.permissionLevel(auth.getPermissionLevel())
				.grantedBy(auth.getGrantedBy())
				.grantedByName(granter != null ? granter.getNickname() : null)
				.grantedTime(auth.getGrantedTime())
				.build();
		}).collect(Collectors.toList());
	}

	@Override
	public String checkAccess(Long agentId, Long userId) {
		SysAgentAuth auth = sysAgentAuthMapper.findByAgentIdAndUserId(agentId, userId);
		return auth != null ? auth.getPermissionLevel() : null;
	}

}
