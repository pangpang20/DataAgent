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
package com.audaque.cloud.ai.dataagent.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SqlDialectResolver 单元测试
 * 测试 MySQL 和达梦数据库的 SQL 方言差异处理
 *
 * @author DataAgent Team
 * @since 2026/01/22
 */
@DisplayName("SQL方言解析器测试")
class SqlDialectResolverTest {

	@Test
	@DisplayName("MySQL - now() 函数应返回 NOW()")
	void testMysqlNowFunction() {
		SqlDialectResolver resolver = new SqlDialectResolver();
		ReflectionTestUtils.setField(resolver, "platform", "mysql");

		String result = resolver.now();

		assertEquals("NOW()", result, "MySQL 应使用 NOW() 函数");
	}

	@Test
	@DisplayName("达梦 - now() 函数应返回 SYSDATE")
	void testDamengNowFunction() {
		SqlDialectResolver resolver = new SqlDialectResolver();
		ReflectionTestUtils.setField(resolver, "platform", "dameng");

		String result = resolver.now();

		assertEquals("SYSDATE", result, "达梦数据库应使用 SYSDATE 函数");
	}

	@Test
	@DisplayName("达梦(dm) - now() 函数应返回 SYSDATE")
	void testDmNowFunction() {
		SqlDialectResolver resolver = new SqlDialectResolver();
		ReflectionTestUtils.setField(resolver, "platform", "dm");

		String result = resolver.now();

		assertEquals("SYSDATE", result, "达梦数据库(dm)应使用 SYSDATE 函数");
	}

	@Test
	@DisplayName("MySQL - limit() 应返回 LIMIT offset, size 格式")
	void testMysqlLimitFunction() {
		SqlDialectResolver resolver = new SqlDialectResolver();
		ReflectionTestUtils.setField(resolver, "platform", "mysql");

		String result = resolver.limit(10, 20);

		assertEquals("LIMIT 10, 20", result, "MySQL 应使用 LIMIT offset, size 格式");
	}

	@Test
	@DisplayName("达梦 - limit() 应返回 LIMIT size OFFSET offset 格式")
	void testDamengLimitFunction() {
		SqlDialectResolver resolver = new SqlDialectResolver();
		ReflectionTestUtils.setField(resolver, "platform", "dameng");

		String result = resolver.limit(10, 20);

		assertEquals("LIMIT 20 OFFSET 10", result, "达梦数据库应使用 LIMIT size OFFSET offset 格式");
	}

	@Test
	@DisplayName("MySQL - isDameng() 应返回 false")
	void testMysqlIsDameng() {
		SqlDialectResolver resolver = new SqlDialectResolver();
		ReflectionTestUtils.setField(resolver, "platform", "mysql");

		boolean result = resolver.isDameng();

		assertFalse(result, "MySQL 平台 isDameng() 应返回 false");
	}

	@Test
	@DisplayName("达梦 - isDameng() 应返回 true")
	void testDamengIsDameng() {
		SqlDialectResolver resolver = new SqlDialectResolver();
		ReflectionTestUtils.setField(resolver, "platform", "dameng");

		boolean result = resolver.isDameng();

		assertTrue(result, "达梦平台 isDameng() 应返回 true");
	}

	@Test
	@DisplayName("达梦(dm) - isDameng() 应返回 true")
	void testDmIsDameng() {
		SqlDialectResolver resolver = new SqlDialectResolver();
		ReflectionTestUtils.setField(resolver, "platform", "dm");

		boolean result = resolver.isDameng();

		assertTrue(result, "达梦平台(dm) isDameng() 应返回 true");
	}

	@Test
	@DisplayName("不区分大小写 - DAMENG 应被识别")
	void testCaseInsensitiveDameng() {
		SqlDialectResolver resolver = new SqlDialectResolver();
		ReflectionTestUtils.setField(resolver, "platform", "DAMENG");

		assertTrue(resolver.isDameng(), "DAMENG (大写) 应被识别为达梦");
		assertEquals("SYSDATE", resolver.now(), "DAMENG (大写) now() 应返回 SYSDATE");
	}

	@Test
	@DisplayName("不区分大小写 - DM 应被识别")
	void testCaseInsensitiveDm() {
		SqlDialectResolver resolver = new SqlDialectResolver();
		ReflectionTestUtils.setField(resolver, "platform", "DM");

		assertTrue(resolver.isDameng(), "DM (大写) 应被识别为达梦");
		assertEquals("SYSDATE", resolver.now(), "DM (大写) now() 应返回 SYSDATE");
	}

	@Test
	@DisplayName("默认值测试 - 未设置平台应使用 MySQL 默认值")
	void testDefaultPlatform() {
		SqlDialectResolver resolver = new SqlDialectResolver();
		// 不设置 platform，测试默认值

		String nowResult = resolver.now();
		String limitResult = resolver.limit(5, 10);

		assertEquals("NOW()", nowResult, "默认应使用 MySQL 的 NOW()");
		assertEquals("LIMIT 5, 10", limitResult, "默认应使用 MySQL 的 LIMIT 格式");
	}

	@Test
	@DisplayName("边界值测试 - limit offset=0, size=1")
	void testLimitBoundaryValues() {
		SqlDialectResolver mysqlResolver = new SqlDialectResolver();
		ReflectionTestUtils.setField(mysqlResolver, "platform", "mysql");

		SqlDialectResolver damengResolver = new SqlDialectResolver();
		ReflectionTestUtils.setField(damengResolver, "platform", "dameng");

		assertEquals("LIMIT 0, 1", mysqlResolver.limit(0, 1));
		assertEquals("LIMIT 1 OFFSET 0", damengResolver.limit(0, 1));
	}

	@Test
	@DisplayName("大数据量分页测试 - offset=1000, size=100")
	void testLimitLargeValues() {
		SqlDialectResolver mysqlResolver = new SqlDialectResolver();
		ReflectionTestUtils.setField(mysqlResolver, "platform", "mysql");

		SqlDialectResolver damengResolver = new SqlDialectResolver();
		ReflectionTestUtils.setField(damengResolver, "platform", "dameng");

		assertEquals("LIMIT 1000, 100", mysqlResolver.limit(1000, 100));
		assertEquals("LIMIT 100 OFFSET 1000", damengResolver.limit(1000, 100));
	}

}
