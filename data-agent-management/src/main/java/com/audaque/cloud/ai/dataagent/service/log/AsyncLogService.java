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
package com.audaque.cloud.ai.dataagent.service.log;

import com.audaque.cloud.ai.dataagent.entity.SysLoginLog;
import com.audaque.cloud.ai.dataagent.entity.SysOperationLog;
import com.audaque.cloud.ai.dataagent.mapper.SysLoginLogMapper;
import com.audaque.cloud.ai.dataagent.mapper.SysOperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncLogService {

	private final SysLoginLogMapper sysLoginLogMapper;

	private final SysOperationLogMapper sysOperationLogMapper;

	@Async
	public void saveLoginLog(SysLoginLog loginLog) {
		try {
			sysLoginLogMapper.insert(loginLog);
		}
		catch (Exception e) {
			log.error("Failed to save login log", e);
		}
	}

	@Async
	public void saveOperationLog(SysOperationLog operationLog) {
		try {
			sysOperationLogMapper.insert(operationLog);
		}
		catch (Exception e) {
			log.error("Failed to save operation log", e);
		}
	}

}
