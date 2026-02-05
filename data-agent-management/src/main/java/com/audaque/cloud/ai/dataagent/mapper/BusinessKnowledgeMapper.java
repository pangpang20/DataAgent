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

import com.audaque.cloud.ai.dataagent.dto.knowledge.BusinessKnowledgeQueryDTO;
import com.audaque.cloud.ai.dataagent.entity.BusinessKnowledge;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BusinessKnowledgeMapper {

	/**
	 * Query business knowledge list by agent ID
	 */
	@Select("""
			SELECT * FROM business_knowledge
			WHERE agent_id = #{agentId} AND is_deleted = 0
			ORDER BY created_time DESC
			""")
	List<BusinessKnowledge> selectByAgentId(@Param("agentId") Long agentId);

	/**
	 * Query all business knowledge list
	 */
	@Select("SELECT * FROM business_knowledge WHERE is_deleted = 0 ORDER BY created_time DESC")
	List<BusinessKnowledge> selectAll();

	/**
	 * Search in a specific agent scope by keyword
	 */
	@Select("""
			SELECT * FROM business_knowledge
			WHERE agent_id = #{agentId} AND is_deleted = 0
			  AND (business_term LIKE CONCAT('%', #{keyword}, '%')
			    OR description LIKE CONCAT('%', #{keyword}, '%')
			    OR synonyms LIKE CONCAT('%', #{keyword}, '%'))
			ORDER BY created_time DESC
			""")
	List<BusinessKnowledge> searchInAgent(@Param("agentId") Long agentId, @Param("keyword") String keyword);

	@Insert("""
			INSERT INTO business_knowledge (business_term, description, synonyms, is_recall, agent_id, created_time, updated_time, embedding_status, is_deleted)
			VALUES (#{businessTerm}, #{description}, #{synonyms}, #{isRecall}, #{agentId}, #{createdTime}, #{updatedTime}, #{embeddingStatus}, #{isDeleted})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(BusinessKnowledge knowledge);

	@Update("""
			<script>
			UPDATE business_knowledge
			<set>
				<if test="businessTerm != null">business_term = #{businessTerm},</if>
				<if test="description != null">description = #{description},</if>
				<if test="synonyms != null">synonyms = #{synonyms},</if>
				<if test="isRecall != null">is_recall = #{isRecall},</if>
				<if test="agentId != null">agent_id = #{agentId},</if>
				<if test="embeddingStatus != null">embedding_status = #{embeddingStatus},</if>
				<if test="errorMsg != null">error_msg = #{errorMsg},</if>
				<if test="isDeleted != null">is_deleted = #{isDeleted},</if>
				updated_time = #{updatedTime}
			</set>
			WHERE id = #{id}
			</script>
			""")
	int updateById(BusinessKnowledge knowledge);

	@Delete("""
			DELETE FROM business_knowledge
			WHERE id = #{id}
			""")
	int deleteById(@Param("id") Long id);

	@Select("""
			SELECT * FROM business_knowledge
			WHERE id = #{id} AND is_deleted = 0
			""")
	BusinessKnowledge selectById(Long id);

	@Select("""
			SELECT id FROM business_knowledge
			WHERE agent_id = #{agentId} AND is_recall = 1 AND is_deleted = 0
			""")
	List<Long> selectRecalledKnowledgeIds(@Param("agentId") Long agentId);

	@Update("""
			UPDATE business_knowledge
			SET is_deleted = #{isDeleted}, updated_time = #{updatedTime}
			WHERE id = #{id}
			""")
	int logicalDelete(@Param("id") Long id, @Param("isDeleted") Integer isDeleted,
			@Param("updatedTime") LocalDateTime updatedTime);

	/**
	 * Page query business knowledge with filters
	 */
	@Select("""
			<script>
			SELECT * FROM business_knowledge
			WHERE agent_id = #{queryDTO.agentId} AND is_deleted = 0
			<if test="queryDTO.keyword != null and queryDTO.keyword != ''">
				AND (business_term LIKE CONCAT('%', #{queryDTO.keyword}, '%')
					 OR description LIKE CONCAT('%', #{queryDTO.keyword}, '%')
					 OR synonyms LIKE CONCAT('%', #{queryDTO.keyword}, '%'))
			</if>
			<if test="queryDTO.isRecall != null">
				AND is_recall = #{queryDTO.isRecall}
			</if>
			<if test="queryDTO.embeddingStatus != null and queryDTO.embeddingStatus != ''">
				AND embedding_status = #{queryDTO.embeddingStatus}
			</if>
			<if test="queryDTO.createTimeStart != null and queryDTO.createTimeStart != ''">
				AND created_time &gt;= #{queryDTO.createTimeStart}
			</if>
			<if test="queryDTO.createTimeEnd != null and queryDTO.createTimeEnd != ''">
				AND created_time &lt;= #{queryDTO.createTimeEnd}
			</if>
			ORDER BY created_time DESC
			${@com.audaque.cloud.ai.dataagent.util.SqlDialectResolver@limit(offset, queryDTO.pageSize)}
			</script>
			""")
	List<BusinessKnowledge> selectByConditionsWithPage(@Param("queryDTO") BusinessKnowledgeQueryDTO queryDTO,
			@Param("offset") Integer offset);

	/**
	 * Count total records by conditions
	 */
	@Select("""
			<script>
			SELECT COUNT(*) FROM business_knowledge
			WHERE agent_id = #{queryDTO.agentId} AND is_deleted = 0
			<if test="queryDTO.keyword != null and queryDTO.keyword != ''">
				AND (business_term LIKE CONCAT('%', #{queryDTO.keyword}, '%')
					 OR description LIKE CONCAT('%', #{queryDTO.keyword}, '%')
					 OR synonyms LIKE CONCAT('%', #{queryDTO.keyword}, '%'))
			</if>
			<if test="queryDTO.isRecall != null">
				AND is_recall = #{queryDTO.isRecall}
			</if>
			<if test="queryDTO.embeddingStatus != null and queryDTO.embeddingStatus != ''">
				AND embedding_status = #{queryDTO.embeddingStatus}
			</if>
			<if test="queryDTO.createTimeStart != null and queryDTO.createTimeStart != ''">
				AND created_time &gt;= #{queryDTO.createTimeStart}
			</if>
			<if test="queryDTO.createTimeEnd != null and queryDTO.createTimeEnd != ''">
				AND created_time &lt;= #{queryDTO.createTimeEnd}
			</if>
			</script>
			""")
	Long countByConditions(@Param("queryDTO") BusinessKnowledgeQueryDTO queryDTO);

	/**
	 * Batch delete business knowledge by ids (logical delete)
	 */
	@Update("""
			<script>
			UPDATE business_knowledge
			SET is_deleted = 1, updated_time = NOW()
			WHERE agent_id = #{agentId} AND is_deleted = 0
			AND id IN
			<foreach collection="ids" item="id" open="(" close=")" separator=",">
				#{id}
			</foreach>
			</script>
			""")
	int batchDeleteByIds(@Param("agentId") Long agentId, @Param("ids") List<Long> ids);

}
