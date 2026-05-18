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

import com.audaque.cloud.ai.dataagent.dto.auth.CaptchaResponse;
import com.wf.captcha.SpecCaptcha;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {

	private static final String CAPTCHA_PREFIX = "captcha:";

	private static final int CAPTCHA_EXPIRE_MINUTES = 5;

	private final RedisTemplate<String, Object> redisTemplate;

	@Override
	public CaptchaResponse generateCaptcha() {
		SpecCaptcha captcha = new SpecCaptcha(130, 48, 5);
		String code = captcha.text().toLowerCase();
		String key = UUID.randomUUID().toString().replace("-", "");

		try {
			redisTemplate.opsForValue().set(CAPTCHA_PREFIX + key, code, CAPTCHA_EXPIRE_MINUTES, TimeUnit.MINUTES);
		}
		catch (Exception e) {
			log.warn("Failed to store captcha in Redis: {}", e.getMessage());
		}

		return CaptchaResponse.builder().captchaKey(key).captchaImage(captcha.toBase64()).build();
	}

	@Override
	public boolean verifyCaptcha(String captchaKey, String captchaCode) {
		if (captchaKey == null || captchaCode == null) {
			return false;
		}

		try {
			String key = CAPTCHA_PREFIX + captchaKey;
			Object stored = redisTemplate.opsForValue().get(key);
			if (stored != null) {
				redisTemplate.delete(key);
				return captchaCode.equalsIgnoreCase(stored.toString());
			}
		}
		catch (Exception e) {
			log.warn("Failed to verify captcha from Redis: {}", e.getMessage());
		}

		return false;
	}

}
