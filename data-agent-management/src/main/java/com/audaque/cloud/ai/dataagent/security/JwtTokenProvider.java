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

import com.audaque.cloud.ai.dataagent.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class JwtTokenProvider {

	private final JwtProperties jwtProperties;

	private final SecretKey secretKey;

	public JwtTokenProvider(JwtProperties jwtProperties) {
		this.jwtProperties = jwtProperties;
		this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
	}

	public String generateAccessToken(Long userId, String username, List<String> roles, List<String> permissions) {
		Date now = new Date();
		Date expiration = new Date(now.getTime() + jwtProperties.getAccessTokenExpiration() * 1000);
		return Jwts.builder()
			.subject(String.valueOf(userId))
			.claim("username", username)
			.claim("roles", roles)
			.claim("permissions", permissions)
			.issuedAt(now)
			.expiration(expiration)
			.signWith(secretKey)
			.compact();
	}

	public String generateRefreshToken(Long userId, String username, boolean rememberMe) {
		Date now = new Date();
		long expirationMs = rememberMe ? jwtProperties.getRememberMeExpiration() * 1000
				: jwtProperties.getRefreshTokenExpiration() * 1000;
		Date expiration = new Date(now.getTime() + expirationMs);
		return Jwts.builder()
			.subject(String.valueOf(userId))
			.claim("username", username)
			.issuedAt(now)
			.expiration(expiration)
			.signWith(secretKey)
			.compact();
	}

	public Claims parseToken(String token) {
		return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
	}

	public boolean validateToken(String token) {
		try {
			if (token == null || token.isBlank()) {
				return false;
			}
			parseToken(token);
			return true;
		}
		catch (ExpiredJwtException | MalformedJwtException | SecurityException | IllegalArgumentException e) {
			log.debug("Token validation failed: {}", e.getMessage());
			return false;
		}
	}

	public Long getUserIdFromToken(String token) {
		return Long.parseLong(parseToken(token).getSubject());
	}

	public String getUsernameFromToken(String token) {
		return parseToken(token).get("username", String.class);
	}

	@SuppressWarnings("unchecked")
	public List<String> getRolesFromToken(String token) {
		return parseToken(token).get("roles", List.class);
	}

	@SuppressWarnings("unchecked")
	public List<String> getPermissionsFromToken(String token) {
		return parseToken(token).get("permissions", List.class);
	}

	public boolean isTokenExpired(String token) {
		try {
			Date expiration = parseToken(token).getExpiration();
			return expiration.before(new Date());
		}
		catch (ExpiredJwtException e) {
			return true;
		}
	}

	public long getTokenExpiration(String token) {
		Date expiration = parseToken(token).getExpiration();
		return (expiration.getTime() - System.currentTimeMillis()) / 1000;
	}

}
