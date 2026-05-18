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

import com.audaque.cloud.ai.dataagent.entity.SysLoginLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysLoginLogMapper {

	@Insert("""
			INSERT INTO sys_login_log (username, ip, user_agent, status, message, login_time)
			VALUES (#{username}, #{ip}, #{userAgent}, #{status}, #{message}, #{loginTime})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(SysLoginLog log);

	@Select("""
			<script>
			SELECT * FROM sys_login_log
			WHERE 1=1
			<if test="username != null and username != ''">
				AND username LIKE CONCAT('%', #{username}, '%')
			</if>
			<if test="status != null">
				AND status = #{status}
			</if>
			ORDER BY login_time DESC
			${@com.audaque.cloud.ai.dataagent.util.SqlDialectResolver@limit(offset, pageSize)}
			</script>
			""")
	List<SysLoginLog> findByConditions(@Param("username") String username, @Param("status") Integer status,
			@Param("offset") Integer offset, @Param("pageSize") Integer pageSize);

	@Select("""
			<script>
			SELECT COUNT(*) FROM sys_login_log
			WHERE 1=1
			<if test="username != null and username != ''">
				AND username LIKE CONCAT('%', #{username}, '%')
			</if>
			<if test="status != null">
				AND status = #{status}
			</if>
			</script>
			""")
	Long countByConditions(@Param("username") String username, @Param("status") Integer status);

}
