# 第一阶段：安全基础设施

> 目标：引入 Spring Security + JWT + Redis，建立认证与鉴权的基础框架。

---

## 任务 1.1：添加 Maven 依赖

**文件**: `data-agent-management/pom.xml`

**操作**: 在 `<dependencies>` 中新增以下依赖：

```xml
<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>

<!-- Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- Captcha -->
<dependency>
    <groupId>com.github.whvcse</groupId>
    <artifactId>easy-captcha</artifactId>
    <version>1.6.2</version>
</dependency>
```

**验证**: `./mvnw -B clean compile -DskipTests=true` 编译通过。

---

## 任务 1.2：JWT 配置属性

**新建文件**: `properties/JwtProperties.java`

**包路径**: `com.audaque.cloud.ai.dataagent.properties`

```java
@Data
@Component
@ConfigurationProperties(prefix = "dataagent.jwt")
public class JwtProperties {
    /** HMAC-SHA256 密钥，>= 256 位 */
    private String secret = "DataAgentDefaultSecretKey2026MustBeLongEnough!!";
    /** Access Token 有效期（秒），默认 2 小时 */
    private long accessTokenExpiration = 7200;
    /** Refresh Token 有效期（秒），默认 7 天 */
    private long refreshTokenExpiration = 604800;
    /** 记住我时 Refresh Token 有效期（秒），默认 30 天 */
    private long rememberMeExpiration = 2592000;
}
```

**修改文件**: `application.yml`

```yaml
dataagent:
  jwt:
    secret: ${JWT_SECRET:DataAgentDefaultSecretKey2026MustBeLongEnough!!}
    access-token-expiration: 7200
    refresh-token-expiration: 604800
    remember-me-expiration: 2592000
```

**单元测试**: 验证 `@ConfigurationProperties` 绑定正确，各字段有默认值。

---

## 任务 1.3：Redis 配置

**新建文件**: `config/RedisConfig.java`

**包路径**: `com.audaque.cloud.ai.dataagent.config`

```java
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
```

**修改文件**: `application.yml` — 添加 Redis 连接配置：

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0
      timeout: 3000ms
```

**单元测试**: 验证 `RedisTemplate` Bean 正确注入。

---

## 任务 1.4：JWT Token 工具类

**新建文件**: `security/JwtTokenProvider.java`

**包路径**: `com.audaque.cloud.ai.dataagent.security`

**职责**:
- `generateAccessToken(Long userId, String username, List<String> roles, List<String> permissions)` — 生成 Access Token
- `generateRefreshToken(Long userId, String username, boolean rememberMe)` — 生成 Refresh Token
- `parseToken(String token)` — 解析 Token，返回 Claims
- `validateToken(String token)` — 校验 Token 有效性（签名 + 过期时间）
- `getUserIdFromToken(String token)` — 从 Token 提取 userId
- `getUsernameFromToken(String token)` — 从 Token 提取 username
- `getRolesFromToken(String token)` — 从 Token 提取角色列表
- `getPermissionsFromToken(String token)` — 从 Token 提取权限列表
- `isTokenExpired(String token)` — 判断 Token 是否过期

**Token Payload 结构**:
```json
{
  "sub": "1",
  "username": "admin",
  "roles": ["SUPER_ADMIN"],
  "permissions": ["agent:create", "agent:delete"],
  "iat": 1716000000,
  "exp": 1716007200
}
```

**关键实现细节**:
- 使用 HMAC-SHA256 签名算法
- 密钥从 `JwtProperties` 注入
- Access Token 和 Refresh Token 使用不同的有效期
- `parseToken` 需捕获 `ExpiredJwtException`、`MalformedJwtException`、`SignatureException` 等异常

**单元测试** (`JwtTokenProviderTest.java`):
- 测试生成 Access Token，解析后字段（sub, username, roles, permissions）正确
- 测试生成 Refresh Token，解析后字段正确
- 测试过期 Token 校验返回 false
- 测试篡改 Token 校验返回 false
- 测试空/null Token 校验返回 false
- 测试 rememberMe 标记影响 Refresh Token 有效期

---

## 任务 1.5：Token 黑名单服务

**新建文件**: `security/TokenBlacklistService.java`（接口）

```java
public interface TokenBlacklistService {
    void addToBlacklist(String token, long expirationSeconds);
    boolean isBlacklisted(String token);
}
```

**新建文件**: `security/RedisTokenBlacklistServiceImpl.java`

**职责**:
- `addToBlacklist` — 将 Token 存入 Redis 黑名单，key 格式 `token:blacklist:{tokenHash}`，TTL 设为 Token 剩余有效期
- `isBlacklisted` — 查询 Redis 判断 Token 是否在黑名单中
- Redis 不可用时降级：`isBlacklisted` 返回 false（仅依赖 Token 过期时间校验），记录 WARN 日志

**单元测试** (`RedisTokenBlacklistServiceImplTest.java`):
- 测试添加黑名单后 `isBlacklisted` 返回 true
- 测试未添加的 Token `isBlacklisted` 返回 false
- 测试 Redis 不可用时降级逻辑（Mock RedisTemplate 抛异常）

---

## 任务 1.6：JWT 认证过滤器

**新建文件**: `security/JwtAuthenticationFilter.java`

**继承**: `OncePerRequestFilter`

**职责**:
1. 从请求头 `Authorization: Bearer {token}` 提取 Token
2. 调用 `JwtTokenProvider.validateToken()` 校验 Token 有效性
3. 调用 `TokenBlacklistService.isBlacklisted()` 检查黑名单
4. 校验通过后，从 Token 解析用户信息，构建 `UsernamePasswordAuthenticationToken`
5. 设置 `SecurityContextHolder.getContext().setAuthentication(authentication)`
6. 放行请求到下一个过滤器

**关键细节**:
- 白名单路径直接放行，不做 Token 校验
- Token 无效/过期/在黑名单中时，不设置 Authentication，由后续 `JwtAuthenticationEntryPoint` 处理 401
- 异常捕获后不抛出，静默放行（交给 EntryPoint 处理）

**单元测试** (`JwtAuthenticationFilterTest.java`):
- 测试白名单路径不校验 Token
- 测试有效 Token 设置 Authentication 到 SecurityContext
- 测试无效 Token 不设置 Authentication
- 测试过期 Token 不设置 Authentication
- 测试黑名单中的 Token 不设置 Authentication
- 测试无 Authorization 头时放行（不设置 Authentication）

---

## 任务 1.7：认证失败处理器

**新建文件**: `security/JwtAuthenticationEntryPoint.java`

**实现**: `AuthenticationEntryPoint`

**职责**:
- 返回 JSON 格式的 401 响应
- 错误码 401003（Token 无效），消息 "未认证，请先登录"
- 设置 `Content-Type: application/json`，`charset=UTF-8`

**单元测试**: 验证返回的 JSON 结构包含 `code: 401003`。

---

## 任务 1.8：权限不足处理器

**新建文件**: `security/CustomAccessDeniedHandler.java`

**实现**: `AccessDeniedHandler`

**职责**:
- 返回 JSON 格式的 403 响应
- 错误码 403001（无权限），消息 "无权限访问该资源"

**单元测试**: 验证返回的 JSON 结构包含 `code: 403001`。

---

## 任务 1.9：Security 配置类

**新建文件**: `config/SecurityConfig.java`

**包路径**: `com.audaque.cloud.ai.dataagent.config`

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 白名单接口
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/auth/refresh").permitAll()
                .requestMatchers("/api/auth/captcha").permitAll()
                .requestMatchers("/api/auth/reset-password").permitAll()
                .requestMatchers("/api/auth/oauth2/**").permitAll()
                .requestMatchers("/nl2sql/stream").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // 静态资源
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
                // Swagger/OpenAPI
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // 其他 API 需认证
                .requestMatchers("/api/**").authenticated()
                .requestMatchers("/nl2sql/**").authenticated()
                // 其他请求放行
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(customAccessDeniedHandler)
            );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);  // cost factor >= 10
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

**关键细节**:
- 禁用 CSRF（无状态 JWT）
- Session 策略设为 STATELESS
- CORS 配置不在这里设置，沿用现有 `WebConfig`（任务 6.1 会改造）
- `/nl2sql/stream` 保持白名单（已有 API Key 鉴权），`/nl2sql/**` 其他路径需认证
- `PasswordEncoder` Bean 使用 BCrypt cost factor 10

**集成测试** (`SecurityConfigIntegrationTest.java`):
- 测试白名单接口无需 Token 可访问（200）
- 测试受保护接口无 Token 返回 401
- 测试受保护接口有效 Token 返回 200/正常业务响应

---

## 任务 1.10：自定义 UserDetailsService

**新建文件**: `security/UserDetailsServiceImpl.java`

**实现**: `UserDetailsService`

**职责**:
- 根据 username 从数据库查询 `SysUser`
- 校验用户状态（`status == 0` 时抛 `DisabledException`）
- 校验账号是否被锁定（`lockTime != null && lockTime > now` 时抛 `LockedException`）
- 查询用户角色列表
- 查询用户权限列表（通过角色关联）
- 构建 `org.springframework.security.core.userdetails.User`

**注意**: 此任务依赖第二阶段的 `SysUserMapper`、`SysRoleMapper`、`SysPermissionMapper`，实际编码时在第二阶段完成后实现。

**单元测试** (`UserDetailsServiceImplTest.java`):
- 测试正常用户加载返回 UserDetails
- 测试禁用用户抛 `DisabledException`
- 测试锁定用户抛 `LockedException`
- 测试不存在的用户名抛 `UsernameNotFoundException`

---

## 任务 1.11：密码强度校验器

**新建文件**: `security/PasswordValidator.java`

**职责**:
- 校验密码至少 8 位
- 包含大写字母、小写字母、数字、特殊字符
- 返回校验结果（boolean）和失败原因（String）

```java
public class PasswordValidator {
    public static ValidationResult validate(String password) {
        // 长度 >= 8
        // 包含大写 [A-Z]
        // 包含小写 [a-z]
        // 包含数字 [0-9]
        // 包含特殊字符 [!@#$%^&*()_+...]
    }

    public record ValidationResult(boolean valid, String message) {}
}
```

**单元测试** (`PasswordValidatorTest.java`):
- 测试合法密码通过
- 测试长度不足 8 位失败
- 测试缺少大写字母失败
- 测试缺少小写字母失败
- 测试缺少数字失败
- 测试缺少特殊字符失败
- 测试空密码失败
- 测试 null 密码失败
