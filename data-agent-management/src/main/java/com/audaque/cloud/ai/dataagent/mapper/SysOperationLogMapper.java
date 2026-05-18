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

import com.audaque.cloud.ai.dataagent.entity.SysOperationLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysOperationLogMapper {

	@Insert("""
			INSERT INTO sys_operation_log (user_id, username, module, operation, method, request_url, request_params, response_result, ip, status, error_msg, cost_time, create_time)
			VALUES (#{userId}, #{username}, #{module}, #{operation}, #{method}, #{requestUrl}, #{requestParams}, #{responseResult}, #{ip}, #{status}, #{errorMsg}, #{costTime}, #{createTime})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(SysOperationLog log);

	@Select("""
			<script>
			SELECT * FROM sys_operation_log
			WHERE 1=1
			<if test="module != null and module != ''">
				AND module = #{module}
			</if>
			<if test="username != null and username != ''">
				AND username LIKE CONCAT('%', #{username}, '%')
			</if>
			<if test="status != null">
				AND status = #{status}
			</if>
			ORDER BY create_time DESC
			${@com.audaque.cloud.ai.dataagent.util.SqlDialectResolver@limit(offset, pageSize)}
			</script>
			""")
	List<SysOperationLog> findByConditions(@Param("module") String module, @Param("username") String username,
			@Param("status") Integer status, @Param("offset") Integer offset, @Param("pageSize") Integer pageSize);

	@Select("""
			<script>
			SELECT COUNT(*) FROM sys_operation_log
			WHERE 1=1
			<if test="module != null and module != ''">
				AND module = #{module}
			</if>
			<if test="username != null and username != ''">
				AND username LIKE CONCAT('%', #{username}, '%')
			</if>
			<if test="status != null">
				AND status = #{status}
			</if>
			</script>
			""")
	Long countByConditions(@Param("module") String module, @Param("username") String username,
			@Param("status") Integer status);

}
