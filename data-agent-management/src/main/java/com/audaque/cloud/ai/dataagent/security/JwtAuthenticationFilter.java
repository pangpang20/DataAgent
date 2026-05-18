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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String AUTHORIZATION_HEADER = "Authorization";

	private static final String BEARER_PREFIX = "Bearer ";

	private static final List<String> WHITELIST_PATHS = List.of("/api/auth/login", "/api/auth/refresh",
			"/api/auth/captcha", "/api/auth/reset-password", "/actuator/health");

	private final JwtTokenProvider jwtTokenProvider;

	private final TokenBlacklistService tokenBlacklistService;

	private final AntPathMatcher pathMatcher = new AntPathMatcher();

	public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, TokenBlacklistService tokenBlacklistService) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.tokenBlacklistService = tokenBlacklistService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String path = request.getRequestURI();

		if (isWhitelisted(path)) {
			filterChain.doFilter(request, response);
			return;
		}

		String token = extractToken(request);
		if (token != null && jwtTokenProvider.validateToken(token) && !tokenBlacklistService.isBlacklisted(token)) {
			try {
				Long userId = jwtTokenProvider.getUserIdFromToken(token);
				String username = jwtTokenProvider.getUsernameFromToken(token);
				List<String> roles = jwtTokenProvider.getRolesFromToken(token);
				List<String> permissions = jwtTokenProvider.getPermissionsFromToken(token);

				DataAgentUserDetails userDetails = new DataAgentUserDetails();
				userDetails.setUserId(userId);
				userDetails.setUsername(username);
				userDetails.setRoles(roles != null ? roles : List.of());
				userDetails.setPermissions(permissions != null ? permissions : List.of());
				userDetails.setStatus(1);

				UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
						userDetails, null, userDetails.getAuthorities());
				authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
				SecurityContextHolder.getContext().setAuthentication(authentication);
			}
			catch (Exception e) {
				log.debug("Failed to set authentication from token: {}", e.getMessage());
			}
		}

		filterChain.doFilter(request, response);
	}

	private boolean isWhitelisted(String path) {
		return WHITELIST_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
	}

	private String extractToken(HttpServletRequest request) {
		String header = request.getHeader(AUTHORIZATION_HEADER);
		if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
			return header.substring(BEARER_PREFIX.length());
		}
		return null;
	}

}
