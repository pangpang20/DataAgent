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
package com.audaque.cloud.ai.dataagent.mapper;

import com.audaque.cloud.ai.dataagent.entity.SysPermission;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SysPermissionMapper {

	@Select("""
			SELECT * FROM sys_permission WHERE id = #{id}
			""")
	SysPermission findById(Long id);

	@Select("""
			SELECT * FROM sys_permission ORDER BY id
			""")
	List<SysPermission> findAll();

	@Select("""
			SELECT DISTINCT p.* FROM sys_permission p
			INNER JOIN sys_role_permission rp ON p.id = rp.permission_id
			INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id
			WHERE ur.user_id = #{userId}
			""")
	List<SysPermission> findByUserId(Long userId);

	@Select("""
			SELECT DISTINCT p.* FROM sys_permission p
			INNER JOIN sys_role_permission rp ON p.id = rp.permission_id
			WHERE rp.role_id = #{roleId}
			""")
	List<SysPermission> findByRoleId(Long roleId);

	@Insert("""
			INSERT INTO sys_permission (permission_name, permission_key, description, resource_type, create_time, update_time)
			VALUES (#{permissionName}, #{permissionKey}, #{description}, #{resourceType}, #{createTime}, #{updateTime})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(SysPermission permission);

	@Delete("""
			DELETE FROM sys_permission WHERE id = #{id}
			""")
	int deleteById(Long id);

}
