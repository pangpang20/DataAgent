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

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RedisTokenBlacklistServiceImpl implements TokenBlacklistService {

	private static final String BLACKLIST_PREFIX = "token:blacklist:";

	private final RedisTemplate<String, Object> redisTemplate;

	public RedisTokenBlacklistServiceImpl(RedisTemplate<String, Object> redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void addToBlacklist(String token, long expirationSeconds) {
		try {
			String hash = hashToken(token);
			redisTemplate.opsForValue().set(BLACKLIST_PREFIX + hash, "1", expirationSeconds, TimeUnit.SECONDS);
		}
		catch (Exception e) {
			log.warn("Failed to add token to blacklist, Redis may be unavailable: {}", e.getMessage());
		}
	}

	@Override
	public boolean isBlacklisted(String token) {
		try {
			String hash = hashToken(token);
			Boolean exists = redisTemplate.hasKey(BLACKLIST_PREFIX + hash);
			return Boolean.TRUE.equals(exists);
		}
		catch (Exception e) {
			log.warn("Redis unavailable for blacklist check, degrading to pass-through: {}", e.getMessage());
			return false;
		}
	}

	private String hashToken(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
			StringBuilder hexString = new StringBuilder();
			for (byte b : hash) {
				String hex = Integer.toHexString(0xff & b);
				if (hex.length() == 1) {
					hexString.append('0');
				}
				hexString.append(hex);
			}
			return hexString.toString();
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("SHA-256 not available", e);
		}
	}

}
