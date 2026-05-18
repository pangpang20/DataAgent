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

import com.audaque.cloud.ai.dataagent.entity.SysUser;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SysUserMapper {

	@Select("""
			SELECT * FROM sys_user WHERE id = #{id} AND is_deleted = 0
			""")
	SysUser findById(Long id);

	@Select("""
			SELECT * FROM sys_user WHERE username = #{username} AND is_deleted = 0
			""")
	SysUser findByUsername(String username);

	@Select("""
			<script>
			SELECT * FROM sys_user
			WHERE is_deleted = 0
			<if test="keyword != null and keyword != ''">
				AND (username LIKE CONCAT('%', #{keyword}, '%')
					 OR nickname LIKE CONCAT('%', #{keyword}, '%')
					 OR email LIKE CONCAT('%', #{keyword}, '%'))
			</if>
			<if test="status != null">
				AND status = #{status}
			</if>
			ORDER BY create_time DESC
			${@com.audaque.cloud.ai.dataagent.util.SqlDialectResolver@limit(offset, pageSize)}
			</script>
			""")
	List<SysUser> findByConditions(@Param("keyword") String keyword, @Param("status") Integer status,
			@Param("offset") Integer offset, @Param("pageSize") Integer pageSize);

	@Select("""
			<script>
			SELECT COUNT(*) FROM sys_user
			WHERE is_deleted = 0
			<if test="keyword != null and keyword != ''">
				AND (username LIKE CONCAT('%', #{keyword}, '%')
					 OR nickname LIKE CONCAT('%', #{keyword}, '%')
					 OR email LIKE CONCAT('%', #{keyword}, '%'))
			</if>
			<if test="status != null">
				AND status = #{status}
			</if>
			</script>
			""")
	Long countByConditions(@Param("keyword") String keyword, @Param("status") Integer status);

	@Insert("""
			INSERT INTO sys_user (username, password, nickname, email, phone, avatar, status, tenant_id, create_time, update_time)
			VALUES (#{username}, #{password}, #{nickname}, #{email}, #{phone}, #{avatar}, #{status}, #{tenantId}, #{createTime}, #{updateTime})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(SysUser user);

	@Update("""
			<script>
			UPDATE sys_user
			<trim prefix="SET" suffixOverrides=",">
				<if test="nickname != null">nickname = #{nickname},</if>
				<if test="email != null">email = #{email},</if>
				<if test="phone != null">phone = #{phone},</if>
				<if test="avatar != null">avatar = #{avatar},</if>
				<if test="status != null">status = #{status},</if>
				update_time = #{updateTime}
			</trim>
			WHERE id = #{id}
			</script>
			""")
	int updateById(SysUser user);

	@Update("""
			UPDATE sys_user SET password = #{password}, password_update_time = #{updateTime}, update_time = #{updateTime}
			WHERE id = #{id}
			""")
	int updatePassword(@Param("id") Long id, @Param("password") String password,
			@Param("updateTime") LocalDateTime updateTime);

	@Update("""
			UPDATE sys_user SET login_fail_count = #{count}, lock_time = #{lockTime}, update_time = #{updateTime}
			WHERE id = #{id}
			""")
	int updateLoginFailCount(@Param("id") Long id, @Param("count") Integer count,
			@Param("lockTime") LocalDateTime lockTime, @Param("updateTime") LocalDateTime updateTime);

	@Update("""
			UPDATE sys_user SET last_login_time = #{loginTime}, last_login_ip = #{ip}, login_fail_count = 0, lock_time = NULL, update_time = #{loginTime}
			WHERE id = #{id}
			""")
	int updateLoginSuccess(@Param("id") Long id, @Param("loginTime") LocalDateTime loginTime, @Param("ip") String ip);

	@Update("""
			UPDATE sys_user SET is_deleted = 1, update_time = NOW() WHERE id = #{id} AND is_deleted = 0
			""")
	int logicalDeleteById(Long id);

	@Select("""
			SELECT u.* FROM sys_user u
			INNER JOIN sys_user_role ur ON u.id = ur.user_id
			WHERE ur.role_id = #{roleId} AND u.is_deleted = 0
			""")
	List<SysUser> findByRoleId(Long roleId);

}
