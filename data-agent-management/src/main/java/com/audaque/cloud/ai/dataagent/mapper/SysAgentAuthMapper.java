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

import com.audaque.cloud.ai.dataagent.entity.SysAgentAuth;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SysAgentAuthMapper {

	@Select("""
			SELECT * FROM sys_agent_auth WHERE agent_id = #{agentId} AND user_id = #{userId}
			""")
	SysAgentAuth findByAgentIdAndUserId(@Param("agentId") Long agentId, @Param("userId") Long userId);

	@Select("""
			SELECT * FROM sys_agent_auth WHERE agent_id = #{agentId}
			""")
	List<SysAgentAuth> findByAgentId(Long agentId);

	@Select("""
			SELECT * FROM sys_agent_auth WHERE user_id = #{userId}
			""")
	List<SysAgentAuth> findByUserId(Long userId);

	@Select("""
			SELECT agent_id FROM sys_agent_auth WHERE user_id = #{userId} AND permission_level IN ('admin', 'write')
			""")
	List<Long> findWritableAgentIdsByUserId(Long userId);

	@Insert("""
			INSERT INTO sys_agent_auth (agent_id, user_id, permission_level, granted_by, granted_time, create_time, update_time)
			VALUES (#{agentId}, #{userId}, #{permissionLevel}, #{grantedBy}, #{grantedTime}, #{createTime}, #{updateTime})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(SysAgentAuth auth);

	@Update("""
			UPDATE sys_agent_auth SET permission_level = #{permissionLevel}, granted_by = #{grantedBy}, update_time = #{updateTime}
			WHERE agent_id = #{agentId} AND user_id = #{userId}
			""")
	int updatePermissionLevel(@Param("agentId") Long agentId, @Param("userId") Long userId,
			@Param("permissionLevel") String permissionLevel, @Param("grantedBy") Long grantedBy,
			@Param("updateTime") java.time.LocalDateTime updateTime);

	@Delete("""
			DELETE FROM sys_agent_auth WHERE agent_id = #{agentId} AND user_id = #{userId}
			""")
	int deleteByAgentIdAndUserId(@Param("agentId") Long agentId, @Param("userId") Long userId);

	@Delete("""
			DELETE FROM sys_agent_auth WHERE agent_id = #{agentId}
			""")
	int deleteByAgentId(Long agentId);

}
