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

import com.audaque.cloud.ai.dataagent.dto.agent.PresetQuestionQueryDTO;
import com.audaque.cloud.ai.dataagent.entity.AgentPresetQuestion;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AgentPresetQuestionMapper {

	@Select("""
			SELECT * FROM agent_preset_question
			         WHERE agent_id = #{agentId} AND is_active = 1 AND is_delete = 0
			ORDER BY sort_order ASC, id ASC
			""")
	List<AgentPresetQuestion> selectByAgentId(@Param("agentId") Long agentId);

	@Select("""
			SELECT * FROM agent_preset_question
			         WHERE agent_id = #{agentId} AND is_delete = 0
			ORDER BY sort_order ASC, id ASC
			""")
	List<AgentPresetQuestion> selectAllByAgentId(@Param("agentId") Long agentId);

	/**
	 * Query by id
	 */
	@Select("""
			SELECT * FROM agent_preset_question WHERE id = #{id} AND is_delete = 0
			""")
	AgentPresetQuestion selectById(@Param("id") Long id);

	@Insert("""
			INSERT INTO agent_preset_question (agent_id, question, sort_order, is_active, is_delete, create_time, update_time)
			VALUES (#{agentId}, #{question}, #{sortOrder}, #{isActive}, #{isDelete}, #{createTime}, #{updateTime})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(AgentPresetQuestion question);

	@Update("""
			<script>
			UPDATE agent_preset_question
			<set>
				<if test="question != null">question = #{question},</if>
				<if test="sortOrder != null">sort_order = #{sortOrder},</if>
				<if test="isActive != null">is_active = #{isActive},</if>
				<if test="isDelete != null">is_delete = #{isDelete},</if>
				update_time = #{updateTime}
			</set>
			WHERE id = #{id}
			</script>
			""")
	int update(AgentPresetQuestion question);

	/**
	 * Logical delete by id
	 */
	@Update("""
			UPDATE agent_preset_question SET is_delete = 1, update_time = NOW() WHERE id = #{id}
			""")
	int deleteById(@Param("id") Long id);

	/**
	 * Logical delete all by agentId
	 */
	@Update("""
			UPDATE agent_preset_question SET is_delete = 1, update_time = NOW() WHERE agent_id = #{agentId}
			""")
	int deleteByAgentId(@Param("agentId") Long agentId);

	/**
	 * Page query preset questions with filters
	 */
	@Select("""
			<script>
			SELECT * FROM agent_preset_question
			WHERE agent_id = #{queryDTO.agentId} AND is_delete = 0
			<if test="queryDTO.keyword != null and queryDTO.keyword != ''">
				AND question LIKE CONCAT('%', #{queryDTO.keyword}, '%')
			</if>
			<if test="queryDTO.isActive != null">
				AND is_active = #{queryDTO.isActive}
			</if>
			<if test="queryDTO.createTimeStart != null and queryDTO.createTimeStart != ''">
				AND create_time &gt;= #{queryDTO.createTimeStart}
			</if>
			<if test="queryDTO.createTimeEnd != null and queryDTO.createTimeEnd != ''">
				AND create_time &lt;= #{queryDTO.createTimeEnd}
			</if>
			ORDER BY sort_order ASC, id ASC
			${@com.audaque.cloud.ai.dataagent.util.SqlDialectResolver@limit(offset, queryDTO.pageSize)}
			</script>
			""")
	List<AgentPresetQuestion> selectByConditionsWithPage(@Param("queryDTO") PresetQuestionQueryDTO queryDTO,
			@Param("offset") Integer offset);

	/**
	 * Count total records by conditions
	 */
	@Select("""
			<script>
			SELECT COUNT(*) FROM agent_preset_question
			WHERE agent_id = #{queryDTO.agentId} AND is_delete = 0
			<if test="queryDTO.keyword != null and queryDTO.keyword != ''">
				AND question LIKE CONCAT('%', #{queryDTO.keyword}, '%')
			</if>
			<if test="queryDTO.isActive != null">
				AND is_active = #{queryDTO.isActive}
			</if>
			<if test="queryDTO.createTimeStart != null and queryDTO.createTimeStart != ''">
				AND create_time &gt;= #{queryDTO.createTimeStart}
			</if>
			<if test="queryDTO.createTimeEnd != null and queryDTO.createTimeEnd != ''">
				AND create_time &lt;= #{queryDTO.createTimeEnd}
			</if>
			</script>
			""")
	Long countByConditions(@Param("queryDTO") PresetQuestionQueryDTO queryDTO);

	/**
	 * Batch delete preset questions by ids (logical delete)
	 */
	@Update("""
			<script>
			UPDATE agent_preset_question
			SET is_delete = 1, update_time = NOW()
			WHERE agent_id = #{agentId}
			AND id IN
			<foreach collection="ids" item="id" open="(" close=")" separator=",">
				#{id}
			</foreach>
			</script>
			""")
	int batchDeleteByIds(@Param("agentId") Long agentId, @Param("ids") List<Long> ids);

	/**
	 * Batch update isActive status by ids
	 */
	@Update("""
			<script>
			UPDATE agent_preset_question
			SET is_active = #{isActive}, update_time = NOW()
			WHERE agent_id = #{agentId}
			AND id IN
			<foreach collection="ids" item="id" open="(" close=")" separator=",">
				#{id}
			</foreach>
			</script>
			""")
	int batchUpdateStatus(@Param("agentId") Long agentId, @Param("ids") List<Long> ids,
			@Param("isActive") Boolean isActive);

}
