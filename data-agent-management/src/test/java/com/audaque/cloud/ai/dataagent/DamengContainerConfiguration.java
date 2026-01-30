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
package com.audaque.cloud.ai.dataagent;

import org.springframework.boot.test.context.TestConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 达梦数据库测试容器配置类
 * 
 * 注意：由于达梦数据库的官方 Docker 镜像需要特殊授权和配置，
 * 此配置类作为框架预留，实际使用时需要：
 * 1. 确保有合法的达梦数据库 Docker 镜像
 * 2. 根据实际镜像调整配置参数
 * 3. 或者使用本地安装的达梦数据库进行集成测试
 *
 */
@TestConfiguration
public class DamengContainerConfiguration {

	public static final int DAMENG_PORT = 5236;

	public static final String DATABASE_NAME = "data_agent";

	public static final String USER_NAME = "SYSDBA";

	public static final String PASSWORD = "SYSDBA";

	/**
	 * 创建达梦数据库测试容器
	 * 
	 * 注意：此方法当前被注释，因为达梦数据库 Docker 镜像需要特殊配置
	 * 如需启用，请：
	 * 1. 取消下面的注释
	 * 2. 替换为实际可用的达梦数据库镜像名称
	 * 3. 配置正确的初始化脚本路径
	 */
	// @Bean
	// @ServiceConnection
	public GenericContainer<?> damengContainer() {
		// 这是一个示例配置，实际使用需要替换为真实的达梦镜像
		return new GenericContainer<>(DockerImageName.parse("dameng/dm8:latest"))
			.withExposedPorts(DAMENG_PORT)
			.withEnv("SYSDBA_PWD", PASSWORD)
			.withEnv("INSTANCE_NAME", DATABASE_NAME)
			// 达梦数据库的初始化脚本需要特殊处理
			// .withCopyFileToContainer(...)
			.withReuse(true);
	}

	/**
	 * 获取达梦数据库 JDBC URL
	 * 
	 * @param container 达梦容器实例
	 * @return JDBC 连接字符串
	 */
	public static String getJdbcUrl(GenericContainer<?> container) {
		return "jdbc:dm://" + container.getHost() + ":" + container.getMappedPort(DAMENG_PORT) 
				+ "?zeroDateTimeBehavior=convertToNull&useUnicode=true&characterEncoding=UTF-8";
	}

	/**
	 * 用于本地达梦数据库测试的配置
	 * 如果本地已安装达梦数据库，可使用此方法获取连接信息
	 */
	public static class LocalDamengConfig {
		public static final String JDBC_URL = "jdbc:dm://localhost:5236?zeroDateTimeBehavior=convertToNull&useUnicode=true&characterEncoding=UTF-8";
		public static final String USERNAME = "SYSDBA";
		public static final String PASSWORD = "SYSDBA";
		public static final String DRIVER_CLASS = "dm.jdbc.driver.DmDriver";
		public static final String PLATFORM = "dameng";
	}

}
