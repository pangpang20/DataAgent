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
package com.audaque.cloud.ai.dataagent.connector.ddl;

import com.audaque.cloud.ai.dataagent.bo.DbConfigBO;
import com.audaque.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DdlFactory {

	private final Map<String, Ddl> ddlExecutorSet = new ConcurrentHashMap<>();

	// 缓存 BizDataSourceTypeEnum 到 Ddl 的映射，避免重复流式查找
	private final Map<BizDataSourceTypeEnum, Ddl> enumDdlCache = new ConcurrentHashMap<>();

	public DdlFactory(List<Ddl> ddls) {
		ddls.forEach(this::registry);
	}

	public void registry(Ddl ddlExecutor) {
		ddlExecutorSet.put(ddlExecutor.getDdlType(), ddlExecutor);
	}

	public boolean isRegistered(String type) {
		return ddlExecutorSet.containsKey(type);
	}

	public Ddl getDdlExecutorByDbConfig(DbConfigBO dbConfig) {
		BizDataSourceTypeEnum type = BizDataSourceTypeEnum.fromTypeName(dbConfig.getDialectType());
		if (type == null) {
			throw new RuntimeException("unknown db type");
		}
		return getDdlExecutorByDbType(type);
	}

	public Ddl getDdlExecutorByDbType(BizDataSourceTypeEnum type) {
		// 先从缓存中查找
		return enumDdlCache.computeIfAbsent(type, this::findDdlExecutorByTypeEnum);
	}

	/**
	 * 根据枚举类型查找对应的DDL执行器（无缓存版本）
	 * 
	 * @param type 数据源类型枚举
	 * @return 对应的DDL执行器
	 */
	private Ddl findDdlExecutorByTypeEnum(BizDataSourceTypeEnum type) {
		return ddlExecutorSet.values()
				.stream()
				.filter(d -> d.supportedDataSourceType(type))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("no ddl executor found for " + type));
	}

	public Ddl getDdlExecutorByType(String type) {
		return ddlExecutorSet.get(type);
	}

}
