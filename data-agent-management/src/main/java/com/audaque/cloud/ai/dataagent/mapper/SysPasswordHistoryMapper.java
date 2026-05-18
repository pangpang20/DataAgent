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

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysPasswordHistoryMapper {

	@Insert("""
			INSERT INTO sys_password_history (user_id, password) VALUES (#{userId}, #{password})
			""")
	int insert(@Param("userId") Long userId, @Param("password") String password);

	@Select("""
			SELECT password FROM sys_password_history
			WHERE user_id = #{userId}
			ORDER BY create_time DESC
			LIMIT #{limit}
			""")
	List<String> findRecentPasswords(@Param("userId") Long userId, @Param("limit") int limit);

}
