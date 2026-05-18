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

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRolePermissionMapper {

	@Insert("""
			INSERT INTO sys_role_permission (role_id, permission_id) VALUES (#{roleId}, #{permissionId})
			""")
	int insert(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

	@Delete("""
			DELETE FROM sys_role_permission WHERE role_id = #{roleId}
			""")
	int deleteByRoleId(Long roleId);

	@Delete("""
			DELETE FROM sys_role_permission WHERE role_id = #{roleId} AND permission_id = #{permissionId}
			""")
	int deleteByRoleIdAndPermissionId(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

	@Select("""
			SELECT permission_id FROM sys_role_permission WHERE role_id = #{roleId}
			""")
	List<Long> findPermissionIdsByRoleId(Long roleId);

	@Insert("""
			<script>
			INSERT INTO sys_role_permission (role_id, permission_id) VALUES
			<foreach collection="permissionIds" item="permissionId" separator=",">
				(#{roleId}, #{permissionId})
			</foreach>
			</script>
			""")
	int batchInsert(@Param("roleId") Long roleId, @Param("permissionIds") List<Long> permissionIds);

}
