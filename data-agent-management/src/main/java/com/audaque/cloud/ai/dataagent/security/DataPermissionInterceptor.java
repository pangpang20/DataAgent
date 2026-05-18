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
package com.audaque.cloud.ai.dataagent.security;

import com.audaque.cloud.ai.dataagent.entity.DataPermission;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Properties;

/**
 * MyBatis interceptor for data-level permission filtering. Automatically appends
 * permission conditions to agent queries based on the current user's role.
 */
@Slf4j
@Component
@Intercepts({
		@Signature(type = StatementHandler.class, method = "prepare", args = { Connection.class, Integer.class }) })
public class DataPermissionInterceptor implements Interceptor {

	private static final String AGENT_TABLE_ALIAS = "a";

	private static final String AGENT_TABLE = "agent";

	@Override
	public Object intercept(Invocation invocation) throws Throwable {
		StatementHandler handler = unwrapProxy(invocation.getTarget());
		MappedStatement ms = getMappedStatement(handler);

		if (ms == null || ms.getSqlCommandType() != SqlCommandType.SELECT) {
			return invocation.proceed();
		}

		String originalSql = handler.getBoundSql().getSql();

		if (!isAgentQuery(originalSql)) {
			return invocation.proceed();
		}

		DataPermission permission = SecurityUtils.getDataPermission();

		if (permission == null || permission.isAdmin()) {
			return invocation.proceed();
		}

		String condition = buildPermissionCondition(permission);
		if (!StringUtils.hasText(condition)) {
			return invocation.proceed();
		}

		String newSql = appendCondition(originalSql, condition);
		BoundSql boundSql = handler.getBoundSql();
		MetaObject metaObject = SystemMetaObject.forObject(boundSql);
		metaObject.setValue("sql", newSql);

		log.debug("Data permission applied: {}", condition);

		return invocation.proceed();
	}

	private StatementHandler unwrapProxy(Object target) {
		if (Proxy.isProxyClass(target.getClass())) {
			MetaObject metaObject = SystemMetaObject.forObject(target);
			return (StatementHandler) metaObject.getValue("h.target");
		}
		return (StatementHandler) target;
	}

	private MappedStatement getMappedStatement(StatementHandler handler) {
		MetaObject metaObject = SystemMetaObject.forObject(handler);
		return (MappedStatement) metaObject.getValue("delegate.mappedStatement");
	}

	private boolean isAgentQuery(String sql) {
		String lowerSql = sql.toLowerCase();
		return lowerSql.contains(AGENT_TABLE) && !lowerSql.contains("sys_");
	}

	private String buildPermissionCondition(DataPermission permission) {
		Long userId = permission.getUserId();

		if (permission.isCreatorOnly()) {
			return String.format("%s.creator_id = %d", AGENT_TABLE_ALIAS, userId);
		}

		if (permission.isDepartmentOnly()) {
			return String.format(
					"(%s.creator_id = %d OR %s.creator_id IN (SELECT id FROM sys_user WHERE department_id = (SELECT department_id FROM sys_user WHERE id = %d)))",
					AGENT_TABLE_ALIAS, userId, AGENT_TABLE_ALIAS, userId);
		}

		return null;
	}

	private String appendCondition(String originalSql, String condition) {
		String lowerSql = originalSql.toLowerCase();
		int whereIndex = lowerSql.lastIndexOf("where");

		if (whereIndex >= 0) {
			return originalSql.substring(0, whereIndex + 5) + " (" + condition + ") AND "
					+ originalSql.substring(whereIndex + 5);
		}

		int orderByIndex = lowerSql.lastIndexOf("order by");
		if (orderByIndex >= 0) {
			return originalSql.substring(0, orderByIndex) + " WHERE " + condition + " "
					+ originalSql.substring(orderByIndex);
		}

		return originalSql + " WHERE " + condition;
	}

	@Override
	public Object plugin(Object target) {
		return Plugin.wrap(target, this);
	}

	@Override
	public void setProperties(Properties properties) {
	}

}
