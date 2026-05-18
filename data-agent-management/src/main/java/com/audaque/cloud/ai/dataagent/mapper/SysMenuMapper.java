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

import com.audaque.cloud.ai.dataagent.entity.SysMenu;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SysMenuMapper {

	@Select("""
			SELECT * FROM sys_menu WHERE id = #{id}
			""")
	SysMenu findById(Long id);

	@Select("""
			SELECT * FROM sys_menu ORDER BY sort_order
			""")
	List<SysMenu> findAll();

	@Select("""
			SELECT DISTINCT m.* FROM sys_menu m
			INNER JOIN sys_role_menu rm ON m.id = rm.menu_id
			INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id
			WHERE ur.user_id = #{userId} AND m.status = 1
			ORDER BY m.sort_order
			""")
	List<SysMenu> findByUserId(Long userId);

	@Select("""
			SELECT DISTINCT m.* FROM sys_menu m
			INNER JOIN sys_role_menu rm ON m.id = rm.menu_id
			WHERE rm.role_id = #{roleId}
			ORDER BY m.sort_order
			""")
	List<SysMenu> findByRoleId(Long roleId);

	@Insert("""
			INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, icon, permission, sort_order, visible, status, create_time, update_time)
			VALUES (#{parentId}, #{menuName}, #{menuType}, #{path}, #{component}, #{icon}, #{permission}, #{sortOrder}, #{visible}, #{status}, #{createTime}, #{updateTime})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(SysMenu menu);

	@Update("""
			<script>
			UPDATE sys_menu
			<trim prefix="SET" suffixOverrides=",">
				<if test="menuName != null">menu_name = #{menuName},</if>
				<if test="parentId != null">parent_id = #{parentId},</if>
				<if test="menuType != null">menu_type = #{menuType},</if>
				<if test="path != null">path = #{path},</if>
				<if test="component != null">component = #{component},</if>
				<if test="icon != null">icon = #{icon},</if>
				<if test="permission != null">permission = #{permission},</if>
				<if test="sortOrder != null">sort_order = #{sortOrder},</if>
				<if test="visible != null">visible = #{visible},</if>
				<if test="status != null">status = #{status},</if>
				update_time = #{updateTime}
			</trim>
			WHERE id = #{id}
			</script>
			""")
	int updateById(SysMenu menu);

	@Delete("""
			DELETE FROM sys_menu WHERE id = #{id}
			""")
	int deleteById(Long id);

	@Select("""
			SELECT COUNT(*) FROM sys_menu WHERE parent_id = #{parentId}
			""")
	Long countByParentId(Long parentId);

}
