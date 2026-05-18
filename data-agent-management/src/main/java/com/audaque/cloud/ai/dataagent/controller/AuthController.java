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

import com.audaque.cloud.ai.dataagent.dto.auth.CaptchaResponse;
import com.audaque.cloud.ai.dataagent.dto.auth.ChangePasswordRequest;
import com.audaque.cloud.ai.dataagent.dto.auth.LoginRequest;
import com.audaque.cloud.ai.dataagent.dto.auth.LoginResponse;
import com.audaque.cloud.ai.dataagent.dto.auth.RefreshTokenRequest;
import com.audaque.cloud.ai.dataagent.dto.auth.UserInfoDTO;
import com.audaque.cloud.ai.dataagent.security.CaptchaService;
import com.audaque.cloud.ai.dataagent.service.auth.AuthService;
import com.audaque.cloud.ai.dataagent.vo.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	private final CaptchaService captchaService;

	@PostMapping("/login")
	public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		String ip = getClientIp(httpRequest);
		LoginResponse response = authService.login(request, ip);
		return ApiResponse.success("登录成功", response);
	}

	@PostMapping("/logout")
	public ApiResponse<Void> logout(HttpServletRequest request) {
		String token = request.getHeader("Authorization");
		authService.logout(token);
		return ApiResponse.success("退出成功");
	}

	@PostMapping("/refresh")
	public ApiResponse<LoginResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
		LoginResponse response = authService.refreshToken(request.getRefreshToken());
		return ApiResponse.success("刷新成功", response);
	}

	@GetMapping("/captcha")
	public ApiResponse<CaptchaResponse> getCaptcha() {
		CaptchaResponse response = captchaService.generateCaptcha();
		return ApiResponse.success("获取验证码成功", response);
	}

	@GetMapping("/userinfo")
	public ApiResponse<UserInfoDTO> getUserInfo() {
		UserInfoDTO userInfo = authService.getCurrentUserInfo();
		return ApiResponse.success("获取用户信息成功", userInfo);
	}

	@PostMapping("/change-password")
	public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
		authService.changePassword(request);
		return ApiResponse.success("密码修改成功");
	}

	private String getClientIp(HttpServletRequest request) {
		String ip = request.getHeader("X-Forwarded-For");
		if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("X-Real-IP");
		}
		if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getRemoteAddr();
		}
		if (ip != null && ip.contains(",")) {
			ip = ip.split(",")[0].trim();
		}
		return ip;
	}

}
