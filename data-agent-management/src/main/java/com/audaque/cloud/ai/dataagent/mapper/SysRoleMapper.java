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

import com.audaque.cloud.ai.dataagent.entity.SysRole;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SysRoleMapper {

	@Select("""
			SELECT * FROM sys_role WHERE id = #{id} AND is_deleted = 0
			""")
	SysRole findById(Long id);

	@Select("""
			SELECT * FROM sys_role WHERE role_key = #{roleKey} AND is_deleted = 0
			""")
	SysRole findByRoleKey(String roleKey);

	@Select("""
			SELECT * FROM sys_role WHERE is_deleted = 0 ORDER BY sort_order
			""")
	List<SysRole> findAll();

	@Select("""
			SELECT r.* FROM sys_role r
			INNER JOIN sys_user_role ur ON r.id = ur.role_id
			WHERE ur.user_id = #{userId} AND r.is_deleted = 0
			""")
	List<SysRole> findByUserId(Long userId);

	@Insert("""
			INSERT INTO sys_role (role_name, role_key, description, sort_order, status, tenant_id, create_time, update_time)
			VALUES (#{roleName}, #{roleKey}, #{description}, #{sortOrder}, #{status}, #{tenantId}, #{createTime}, #{updateTime})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(SysRole role);

	@Update("""
			<script>
			UPDATE sys_role
			<trim prefix="SET" suffixOverrides=",">
				<if test="roleName != null">role_name = #{roleName},</if>
				<if test="description != null">description = #{description},</if>
				<if test="sortOrder != null">sort_order = #{sortOrder},</if>
				<if test="status != null">status = #{status},</if>
				update_time = #{updateTime}
			</trim>
			WHERE id = #{id}
			</script>
			""")
	int updateById(SysRole role);

	@Update("""
			UPDATE sys_role SET is_deleted = 1, update_time = NOW() WHERE id = #{id} AND is_deleted = 0
			""")
	int logicalDeleteById(Long id);

	@Select("""
			SELECT COUNT(*) FROM sys_user_role WHERE role_id = #{roleId}
			""")
	Long countUsersByRoleId(Long roleId);

}
