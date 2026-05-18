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
package com.audaque.cloud.ai.dataagent.service.role;

import com.audaque.cloud.ai.dataagent.dto.role.RoleCreateRequest;
import com.audaque.cloud.ai.dataagent.dto.role.RoleDetailResponse;
import com.audaque.cloud.ai.dataagent.dto.role.RoleUpdateRequest;
import com.audaque.cloud.ai.dataagent.entity.SysRole;
import com.audaque.cloud.ai.dataagent.exception.BizException;
import com.audaque.cloud.ai.dataagent.mapper.SysRoleMapper;
import com.audaque.cloud.ai.dataagent.mapper.SysRoleMenuMapper;
import com.audaque.cloud.ai.dataagent.mapper.SysRolePermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

	private final SysRoleMapper sysRoleMapper;

	private final SysRoleMenuMapper sysRoleMenuMapper;

	private final SysRolePermissionMapper sysRolePermissionMapper;

	@Override
	@Transactional
	public RoleDetailResponse createRole(RoleCreateRequest request) {
		SysRole existing = sysRoleMapper.findByRoleKey(request.getRoleKey());
		if (existing != null) {
			throw new BizException(400011, "角色标识已存在");
		}

		SysRole role = SysRole.builder()
			.roleName(request.getRoleName())
			.roleKey(request.getRoleKey())
			.description(request.getDescription())
			.sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
			.status(1)
			.tenantId(1L)
			.createTime(LocalDateTime.now())
			.updateTime(LocalDateTime.now())
			.isDeleted(0)
			.build();

		sysRoleMapper.insert(role);

		if (request.getMenuIds() != null) {
			sysRoleMenuMapper.batchInsert(role.getId(), request.getMenuIds());
		}
		if (request.getPermissionIds() != null) {
			sysRolePermissionMapper.batchInsert(role.getId(), request.getPermissionIds());
		}

		return getRoleById(role.getId());
	}

	@Override
	@Transactional
	public RoleDetailResponse updateRole(RoleUpdateRequest request) {
		SysRole role = sysRoleMapper.findById(request.getId());
		if (role == null) {
			throw new BizException(404002, "角色不存在");
		}

		SysRole update = SysRole.builder()
			.id(request.getId())
			.roleName(request.getRoleName())
			.description(request.getDescription())
			.sortOrder(request.getSortOrder())
			.status(request.getStatus())
			.updateTime(LocalDateTime.now())
			.build();

		sysRoleMapper.updateById(update);

		if (request.getMenuIds() != null) {
			sysRoleMenuMapper.deleteByRoleId(request.getId());
			sysRoleMenuMapper.batchInsert(request.getId(), request.getMenuIds());
		}
		if (request.getPermissionIds() != null) {
			sysRolePermissionMapper.deleteByRoleId(request.getId());
			sysRolePermissionMapper.batchInsert(request.getId(), request.getPermissionIds());
		}

		return getRoleById(request.getId());
	}

	@Override
	@Transactional
	public void deleteRole(Long id) {
		SysRole role = sysRoleMapper.findById(id);
		if (role == null) {
			throw new BizException(404002, "角色不存在");
		}

		Long userCount = sysRoleMapper.countUsersByRoleId(id);
		if (userCount > 0) {
			throw new BizException(400012, "该角色下还有" + userCount + "个用户，不能删除");
		}

		sysRoleMapper.logicalDeleteById(id);
		sysRoleMenuMapper.deleteByRoleId(id);
		sysRolePermissionMapper.deleteByRoleId(id);
	}

	@Override
	public RoleDetailResponse getRoleById(Long id) {
		SysRole role = sysRoleMapper.findById(id);
		if (role == null) {
			throw new BizException(404002, "角色不存在");
		}

		List<Long> menuIds = sysRoleMenuMapper.findMenuIdsByRoleId(id);
		List<Long> permissionIds = sysRolePermissionMapper.findPermissionIdsByRoleId(id);
		Long userCount = sysRoleMapper.countUsersByRoleId(id);

		return RoleDetailResponse.builder()
			.id(role.getId())
			.roleName(role.getRoleName())
			.roleKey(role.getRoleKey())
			.description(role.getDescription())
			.sortOrder(role.getSortOrder())
			.status(role.getStatus())
			.createTime(role.getCreateTime())
			.menuIds(menuIds)
			.permissionIds(permissionIds)
			.userCount(userCount)
			.build();
	}

	@Override
	public List<RoleDetailResponse> listRoles() {
		return sysRoleMapper.findAll().stream().map(r -> {
			List<Long> menuIds = sysRoleMenuMapper.findMenuIdsByRoleId(r.getId());
			List<Long> permissionIds = sysRolePermissionMapper.findPermissionIdsByRoleId(r.getId());
			Long userCount = sysRoleMapper.countUsersByRoleId(r.getId());
			return RoleDetailResponse.builder()
				.id(r.getId())
				.roleName(r.getRoleName())
				.roleKey(r.getRoleKey())
				.description(r.getDescription())
				.sortOrder(r.getSortOrder())
				.status(r.getStatus())
				.createTime(r.getCreateTime())
				.menuIds(menuIds)
				.permissionIds(permissionIds)
				.userCount(userCount)
				.build();
		}).collect(Collectors.toList());
	}

}
