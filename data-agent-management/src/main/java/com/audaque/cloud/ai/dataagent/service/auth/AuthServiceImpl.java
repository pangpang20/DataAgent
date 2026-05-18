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
package com.audaque.cloud.ai.dataagent.service.auth;

import com.audaque.cloud.ai.dataagent.dto.auth.ChangePasswordRequest;
import com.audaque.cloud.ai.dataagent.dto.auth.LoginRequest;
import com.audaque.cloud.ai.dataagent.dto.auth.LoginResponse;
import com.audaque.cloud.ai.dataagent.dto.auth.MenuTreeDTO;
import com.audaque.cloud.ai.dataagent.dto.auth.UserInfoDTO;
import com.audaque.cloud.ai.dataagent.entity.SysLoginLog;
import com.audaque.cloud.ai.dataagent.entity.SysMenu;
import com.audaque.cloud.ai.dataagent.entity.SysUser;
import com.audaque.cloud.ai.dataagent.exception.BizException;
import com.audaque.cloud.ai.dataagent.mapper.SysMenuMapper;
import com.audaque.cloud.ai.dataagent.mapper.SysPermissionMapper;
import com.audaque.cloud.ai.dataagent.mapper.SysRoleMapper;
import com.audaque.cloud.ai.dataagent.mapper.SysUserMapper;
import com.audaque.cloud.ai.dataagent.security.DataAgentUserDetails;
import com.audaque.cloud.ai.dataagent.security.JwtTokenProvider;
import com.audaque.cloud.ai.dataagent.security.PasswordValidator;
import com.audaque.cloud.ai.dataagent.security.SecurityUtils;
import com.audaque.cloud.ai.dataagent.security.TokenBlacklistService;
import com.audaque.cloud.ai.dataagent.service.log.AsyncLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

	private static final int MAX_LOGIN_FAIL_COUNT = 5;

	private static final int LOCK_MINUTES = 30;

	private final SysUserMapper sysUserMapper;

	private final SysRoleMapper sysRoleMapper;

	private final SysMenuMapper sysMenuMapper;

	private final SysPermissionMapper sysPermissionMapper;

	private final JwtTokenProvider jwtTokenProvider;

	private final TokenBlacklistService tokenBlacklistService;

	private final PasswordEncoder passwordEncoder;

	private final AsyncLogService asyncLogService;

	@Override
	public LoginResponse login(LoginRequest request, String ip) {
		SysUser user = sysUserMapper.findByUsername(request.getUsername());
		if (user == null) {
			saveLoginLog(null, request.getUsername(), ip, 0, "用户名或密码错误");
			throw new BizException(401001, "用户名或密码错误");
		}

		if (user.getStatus() != null && user.getStatus() == 0) {
			saveLoginLog(user.getId(), request.getUsername(), ip, 0, "账号已被禁用");
			throw new BizException(401002, "账号已被禁用");
		}

		if (user.getLockTime() != null && user.getLockTime().isAfter(LocalDateTime.now())) {
			saveLoginLog(user.getId(), request.getUsername(), ip, 0, "账号已被锁定");
			throw new BizException(401004, "账号已被锁定，请" + LOCK_MINUTES + "分钟后重试");
		}

		if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
			int failCount = (user.getLoginFailCount() != null ? user.getLoginFailCount() : 0) + 1;
			LocalDateTime lockTime = null;
			if (failCount >= MAX_LOGIN_FAIL_COUNT) {
				lockTime = LocalDateTime.now().plusMinutes(LOCK_MINUTES);
			}
			sysUserMapper.updateLoginFailCount(user.getId(), failCount, lockTime, LocalDateTime.now());
			saveLoginLog(user.getId(), request.getUsername(), ip, 0, "密码错误，第" + failCount + "次失败");
			throw new BizException(401001, "用户名或密码错误，连续失败" + failCount + "次后将锁定" + LOCK_MINUTES + "分钟");
		}

		sysUserMapper.updateLoginSuccess(user.getId(), LocalDateTime.now(), ip);
		saveLoginLog(user.getId(), request.getUsername(), ip, 1, "登录成功");

		List<String> roles = sysRoleMapper.findByUserId(user.getId())
			.stream()
			.map(r -> r.getRoleKey())
			.collect(Collectors.toList());

		List<String> permissions = sysPermissionMapper.findByUserId(user.getId())
			.stream()
			.map(p -> p.getPermissionKey())
			.collect(Collectors.toList());

		String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), roles, permissions);
		String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getUsername(),
				request.isRememberMe());

		UserInfoDTO userInfo = UserInfoDTO.builder()
			.userId(user.getId())
			.username(user.getUsername())
			.nickname(user.getNickname())
			.email(user.getEmail())
			.phone(user.getPhone())
			.avatar(user.getAvatar())
			.roles(roles)
			.permissions(permissions)
			.build();

		return LoginResponse.builder()
			.accessToken(accessToken)
			.refreshToken(refreshToken)
			.expiresIn(jwtTokenProvider.getTokenExpiration(accessToken))
			.userInfo(userInfo)
			.build();
	}

	@Override
	public void logout(String token) {
		if (token != null && token.startsWith("Bearer ")) {
			token = token.substring(7);
		}
		if (token != null && jwtTokenProvider.validateToken(token)) {
			long expiration = jwtTokenProvider.getTokenExpiration(token);
			if (expiration > 0) {
				tokenBlacklistService.addToBlacklist(token, expiration);
			}
		}
	}

	@Override
	public LoginResponse refreshToken(String refreshToken) {
		if (!jwtTokenProvider.validateToken(refreshToken)) {
			throw new BizException(401005, "refreshToken无效或已过期");
		}

		Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
		SysUser user = sysUserMapper.findById(userId);
		if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
			throw new BizException(401002, "用户不存在或已被禁用");
		}

		List<String> roles = sysRoleMapper.findByUserId(user.getId())
			.stream()
			.map(r -> r.getRoleKey())
			.collect(Collectors.toList());

		List<String> permissions = sysPermissionMapper.findByUserId(user.getId())
			.stream()
			.map(p -> p.getPermissionKey())
			.collect(Collectors.toList());

		String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), roles,
				permissions);
		String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getUsername(), false);

		long remaining = jwtTokenProvider.getTokenExpiration(refreshToken);
		if (remaining > 0) {
			tokenBlacklistService.addToBlacklist(refreshToken, remaining);
		}

		UserInfoDTO userInfo = UserInfoDTO.builder()
			.userId(user.getId())
			.username(user.getUsername())
			.nickname(user.getNickname())
			.email(user.getEmail())
			.phone(user.getPhone())
			.avatar(user.getAvatar())
			.roles(roles)
			.permissions(permissions)
			.build();

		return LoginResponse.builder()
			.accessToken(newAccessToken)
			.refreshToken(newRefreshToken)
			.expiresIn(jwtTokenProvider.getTokenExpiration(newAccessToken))
			.userInfo(userInfo)
			.build();
	}

	@Override
	public UserInfoDTO getCurrentUserInfo() {
		DataAgentUserDetails userDetails = SecurityUtils.getCurrentUserDetails();
		if (userDetails == null) {
			throw new BizException(401003, "未认证，请先登录");
		}

		SysUser user = sysUserMapper.findById(userDetails.getUserId());
		if (user == null) {
			throw new BizException(404001, "用户不存在");
		}

		List<SysMenu> menus = sysMenuMapper.findByUserId(user.getId());
		List<MenuTreeDTO> menuTree = buildMenuTree(menus, 0L);

		return UserInfoDTO.builder()
			.userId(user.getId())
			.username(user.getUsername())
			.nickname(user.getNickname())
			.email(user.getEmail())
			.phone(user.getPhone())
			.avatar(user.getAvatar())
			.roles(userDetails.getRoles())
			.permissions(userDetails.getPermissions())
			.menus(menuTree)
			.build();
	}

	@Override
	public void changePassword(ChangePasswordRequest request) {
		DataAgentUserDetails userDetails = SecurityUtils.getCurrentUserDetails();
		if (userDetails == null) {
			throw new BizException(401003, "未认证，请先登录");
		}

		SysUser user = sysUserMapper.findById(userDetails.getUserId());
		if (user == null) {
			throw new BizException(404001, "用户不存在");
		}

		if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
			throw new BizException(400002, "原密码不正确");
		}

		PasswordValidator.ValidationResult validation = PasswordValidator.validate(request.getNewPassword());
		if (!validation.valid()) {
			throw new BizException(400003, validation.message());
		}

		if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
			throw new BizException(400004, "新密码不能与当前密码相同");
		}

		String encodedPassword = passwordEncoder.encode(request.getNewPassword());
		sysUserMapper.updatePassword(user.getId(), encodedPassword, LocalDateTime.now());
	}

	private List<MenuTreeDTO> buildMenuTree(List<SysMenu> menus, Long parentId) {
		return menus.stream()
			.filter(m -> parentId.equals(m.getParentId()))
			.map(m -> MenuTreeDTO.builder()
				.id(m.getId())
				.parentId(m.getParentId())
				.menuName(m.getMenuName())
				.menuType(m.getMenuType())
				.path(m.getPath())
				.component(m.getComponent())
				.icon(m.getIcon())
				.permission(m.getPermission())
				.sortOrder(m.getSortOrder())
				.visible(m.getVisible())
				.children(buildMenuTree(menus, m.getId()))
				.build())
			.collect(Collectors.toList());
	}

	private void saveLoginLog(Long userId, String username, String ip, int status, String message) {
		SysLoginLog loginLog = SysLoginLog.builder()
			.username(username)
			.ip(ip)
			.status(status)
			.message(message)
			.loginTime(LocalDateTime.now())
			.build();
		asyncLogService.saveLoginLog(loginLog);
	}

}
