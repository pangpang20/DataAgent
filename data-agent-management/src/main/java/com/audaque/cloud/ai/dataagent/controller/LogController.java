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
package com.audaque.cloud.ai.dataagent.controller;

import com.audaque.cloud.ai.dataagent.dto.log.LoginLogQueryDTO;
import com.audaque.cloud.ai.dataagent.dto.log.OperationLogQueryDTO;
import com.audaque.cloud.ai.dataagent.entity.SysLoginLog;
import com.audaque.cloud.ai.dataagent.entity.SysOperationLog;
import com.audaque.cloud.ai.dataagent.mapper.SysLoginLogMapper;
import com.audaque.cloud.ai.dataagent.mapper.SysOperationLogMapper;
import com.audaque.cloud.ai.dataagent.vo.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/log")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('system:log')")
public class LogController {

	private final SysLoginLogMapper sysLoginLogMapper;

	private final SysOperationLogMapper sysOperationLogMapper;

	@PostMapping("/login/page")
	public PageResponse<List<SysLoginLog>> queryLoginLogs(@RequestBody LoginLogQueryDTO queryDTO) {
		int offset = (queryDTO.getPageNum() - 1) * queryDTO.getPageSize();
		List<SysLoginLog> list = sysLoginLogMapper.findByConditions(queryDTO.getUsername(), queryDTO.getStatus(),
				offset, queryDTO.getPageSize());
		Long total = sysLoginLogMapper.countByConditions(queryDTO.getUsername(), queryDTO.getStatus());
		int totalPages = (int) ((total + queryDTO.getPageSize() - 1) / queryDTO.getPageSize());
		return PageResponse.success(list, total, queryDTO.getPageNum(), queryDTO.getPageSize(), totalPages);
	}

	@PostMapping("/operation/page")
	public PageResponse<List<SysOperationLog>> queryOperationLogs(@RequestBody OperationLogQueryDTO queryDTO) {
		int offset = (queryDTO.getPageNum() - 1) * queryDTO.getPageSize();
		List<SysOperationLog> list = sysOperationLogMapper.findByConditions(queryDTO.getModule(),
				queryDTO.getUsername(), queryDTO.getStatus(), offset, queryDTO.getPageSize());
		Long total = sysOperationLogMapper.countByConditions(queryDTO.getModule(), queryDTO.getUsername(),
				queryDTO.getStatus());
		int totalPages = (int) ((total + queryDTO.getPageSize() - 1) / queryDTO.getPageSize());
		return PageResponse.success(list, total, queryDTO.getPageNum(), queryDTO.getPageSize(), totalPages);
	}

}
