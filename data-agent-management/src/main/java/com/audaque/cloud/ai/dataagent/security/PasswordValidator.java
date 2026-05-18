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

public final class PasswordValidator {

	private PasswordValidator() {
	}

	public static ValidationResult validate(String password) {
		if (password == null || password.isEmpty()) {
			return new ValidationResult(false, "密码不能为空");
		}
		if (password.length() < 8) {
			return new ValidationResult(false, "密码长度不能少于8位");
		}
		if (!password.matches(".*[A-Z].*")) {
			return new ValidationResult(false, "密码必须包含大写字母");
		}
		if (!password.matches(".*[a-z].*")) {
			return new ValidationResult(false, "密码必须包含小写字母");
		}
		if (!password.matches(".*[0-9].*")) {
			return new ValidationResult(false, "密码必须包含数字");
		}
		if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
			return new ValidationResult(false, "密码必须包含特殊字符");
		}
		return new ValidationResult(true, "密码强度合格");
	}

	public record ValidationResult(boolean valid, String message) {
	}

}
