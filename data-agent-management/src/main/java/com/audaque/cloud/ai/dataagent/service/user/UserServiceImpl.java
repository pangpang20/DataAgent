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
package com.audaque.cloud.ai.dataagent.service.user;

import com.audaque.cloud.ai.dataagent.dto.user.UserCreateRequest;
import com.audaque.cloud.ai.dataagent.dto.user.UserDetailResponse;
import com.audaque.cloud.ai.dataagent.dto.user.UserQueryDTO;
import com.audaque.cloud.ai.dataagent.dto.user.UserUpdateRequest;
import com.audaque.cloud.ai.dataagent.entity.SysRole;
import com.audaque.cloud.ai.dataagent.entity.SysUser;
import com.audaque.cloud.ai.dataagent.exception.BizException;
import com.audaque.cloud.ai.dataagent.mapper.SysRoleMapper;
import com.audaque.cloud.ai.dataagent.mapper.SysUserMapper;
import com.audaque.cloud.ai.dataagent.mapper.SysUserRoleMapper;
import com.audaque.cloud.ai.dataagent.security.PasswordValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

	private final SysUserMapper sysUserMapper;

	private final SysRoleMapper sysRoleMapper;

	private final SysUserRoleMapper sysUserRoleMapper;

	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public UserDetailResponse createUser(UserCreateRequest request) {
		SysUser existing = sysUserMapper.findByUsername(request.getUsername());
		if (existing != null) {
			throw new BizException(400010, "用户名已存在");
		}

		PasswordValidator.ValidationResult validation = PasswordValidator.validate(request.getPassword());
		if (!validation.valid()) {
			throw new BizException(400003, validation.message());
		}

		SysUser user = SysUser.builder()
			.username(request.getUsername())
			.password(passwordEncoder.encode(request.getPassword()))
			.nickname(request.getNickname() != null ? request.getNickname() : request.getUsername())
			.email(request.getEmail())
			.phone(request.getPhone())
			.status(1)
			.tenantId(1L)
			.createTime(LocalDateTime.now())
			.updateTime(LocalDateTime.now())
			.isDeleted(0)
			.build();

		sysUserMapper.insert(user);

		if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
			for (Long roleId : request.getRoleIds()) {
				sysUserRoleMapper.insert(user.getId(), roleId);
			}
		}

		return getUserById(user.getId());
	}

	@Override
	@Transactional
	public UserDetailResponse updateUser(UserUpdateRequest request) {
		SysUser user = sysUserMapper.findById(request.getId());
		if (user == null) {
			throw new BizException(404001, "用户不存在");
		}

		SysUser update = SysUser.builder()
			.id(request.getId())
			.nickname(request.getNickname())
			.email(request.getEmail())
			.phone(request.getPhone())
			.status(request.getStatus())
			.updateTime(LocalDateTime.now())
			.build();

		sysUserMapper.updateById(update);

		if (request.getRoleIds() != null) {
			sysUserRoleMapper.deleteByUserId(request.getId());
			for (Long roleId : request.getRoleIds()) {
				sysUserRoleMapper.insert(request.getId(), roleId);
			}
		}

		return getUserById(request.getId());
	}

	@Override
	@Transactional
	public void deleteUser(Long id) {
		SysUser user = sysUserMapper.findById(id);
		if (user == null) {
			throw new BizException(404001, "用户不存在");
		}
		sysUserMapper.logicalDeleteById(id);
		sysUserRoleMapper.deleteByUserId(id);
	}

	@Override
	public UserDetailResponse getUserById(Long id) {
		SysUser user = sysUserMapper.findById(id);
		if (user == null) {
			throw new BizException(404001, "用户不存在");
		}

		List<SysRole> roles = sysRoleMapper.findByUserId(id);

		return UserDetailResponse.builder()
			.id(user.getId())
			.username(user.getUsername())
			.nickname(user.getNickname())
			.email(user.getEmail())
			.phone(user.getPhone())
			.avatar(user.getAvatar())
			.status(user.getStatus())
			.tenantId(user.getTenantId())
			.lastLoginTime(user.getLastLoginTime())
			.lastLoginIp(user.getLastLoginIp())
			.createTime(user.getCreateTime())
			.roles(roles)
			.build();
	}

	@Override
	public List<UserDetailResponse> listUsers(UserQueryDTO queryDTO) {
		int offset = (queryDTO.getPageNum() - 1) * queryDTO.getPageSize();
		List<SysUser> users = sysUserMapper.findByConditions(queryDTO.getKeyword(), queryDTO.getStatus(), offset,
				queryDTO.getPageSize());

		return users.stream().map(u -> {
			List<SysRole> roles = sysRoleMapper.findByUserId(u.getId());
			return UserDetailResponse.builder()
				.id(u.getId())
				.username(u.getUsername())
				.nickname(u.getNickname())
				.email(u.getEmail())
				.phone(u.getPhone())
				.avatar(u.getAvatar())
				.status(u.getStatus())
				.tenantId(u.getTenantId())
				.lastLoginTime(u.getLastLoginTime())
				.lastLoginIp(u.getLastLoginIp())
				.createTime(u.getCreateTime())
				.roles(roles)
				.build();
		}).collect(Collectors.toList());
	}

	@Override
	@Transactional
	public void resetPassword(Long userId, String newPassword) {
		SysUser user = sysUserMapper.findById(userId);
		if (user == null) {
			throw new BizException(404001, "用户不存在");
		}

		PasswordValidator.ValidationResult validation = PasswordValidator.validate(newPassword);
		if (!validation.valid()) {
			throw new BizException(400003, validation.message());
		}

		sysUserMapper.updatePassword(userId, passwordEncoder.encode(newPassword), LocalDateTime.now());
	}

	@Override
	@Transactional
	public void assignRoles(Long userId, List<Long> roleIds) {
		SysUser user = sysUserMapper.findById(userId);
		if (user == null) {
			throw new BizException(404001, "用户不存在");
		}

		sysUserRoleMapper.deleteByUserId(userId);
		if (roleIds != null) {
			for (Long roleId : roleIds) {
				sysUserRoleMapper.insert(userId, roleId);
			}
		}
	}

}
