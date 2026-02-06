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

import com.audaque.cloud.ai.dataagent.entity.ConversationTurn;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ConversationTurnMapper {

    /**
     * 插入对话记录
     */
    @Insert("INSERT INTO conversation_turn (thread_id, user_question, plan, create_time, update_time, sequence_number) " +
            "VALUES (#{threadId}, #{userQuestion}, #{plan}, #{createTime}, #{updateTime}, #{sequenceNumber})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ConversationTurn conversationTurn);

    /**
     * 根据线程ID查询对话历史
     */
    @Select("SELECT * FROM conversation_turn WHERE thread_id = #{threadId} ORDER BY sequence_number ASC")
    List<ConversationTurn> selectByThreadId(String threadId);

    /**
     * 根据线程ID和序号查询特定记录
     */
    @Select("SELECT * FROM conversation_turn WHERE thread_id = #{threadId} AND sequence_number = #{sequenceNumber}")
    ConversationTurn selectByThreadIdAndSequence(@Param("threadId") String threadId, @Param("sequenceNumber") Integer sequenceNumber);

    /**
     * 更新对话记录
     */
    @Update("UPDATE conversation_turn SET user_question = #{userQuestion}, plan = #{plan}, " +
            "update_time = #{updateTime} WHERE id = #{id}")
    int updateById(ConversationTurn conversationTurn);

    /**
     * 删除指定线程的所有对话记录
     */
    @Delete("DELETE FROM conversation_turn WHERE thread_id = #{threadId}")
    int deleteByThreadId(String threadId);

    /**
     * 删除指定线程的最后一条记录
     */
    @Delete("DELETE FROM conversation_turn WHERE thread_id = #{threadId} AND sequence_number = " +
            "(SELECT MAX(sequence_number) FROM conversation_turn WHERE thread_id = #{threadId})")
    int deleteLastTurnByThreadId(String threadId);

    /**
     * 获取指定线程的最大序号
     */
    @Select("SELECT COALESCE(MAX(sequence_number), 0) FROM conversation_turn WHERE thread_id = #{threadId}")
    int getMaxSequenceNumberByThreadId(String threadId);

    /**
     * 清理超过最大历史记录数的旧记录
     */
    @Delete("DELETE FROM conversation_turn WHERE thread_id = #{threadId} AND sequence_number <= " +
            "(SELECT sequence_number FROM (SELECT sequence_number FROM conversation_turn WHERE thread_id = #{threadId} " +
            "ORDER BY sequence_number DESC LIMIT 1 OFFSET #{maxHistory}) AS sub)")
    int cleanupOldTurns(@Param("threadId") String threadId, @Param("maxHistory") int maxHistory);
}