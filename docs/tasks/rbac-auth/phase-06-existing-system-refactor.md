# 第六阶段：现有系统改造

> 目标：为现有 Controller 添加权限注解，收紧 CORS，改造 userId 获取方式。

---

## 任务 6.1：CORS 策略收紧

**修改文件**: `config/WebConfig.java`

**改造内容**:
- 将 `allowedOrigins("*")` 改为从配置读取的允许来源列表
- 添加 `allowedMethods`、`allowedHeaders`、`allowCredentials` 配置

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${dataagent.cors.allowed-origins:http://localhost:3000}")
    private List<String> allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins(allowedOrigins.toArray(new String[0]))
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
```

**修改文件**: `application.yml`

```yaml
dataagent:
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000}
```

**集成测试**:
- 测试已配置来源的跨域请求返回正确的 CORS 头
- 测试未配置来源的跨域请求不返回 CORS 头（浏览器拒绝）

---

## 任务 6.2：AgentController 权限注解

**修改文件**: `controller/AgentController.java`

| 方法 | 路径 | 注解 |
|---|---|---|
| listAgents | `GET /api/agent/list` | `@PreAuthorize("hasAuthority('agent:view')")` |
| getAgent | `GET /api/agent/{id}` | `@PreAuthorize("hasAuthority('agent:view')")` |
| createAgent | `POST /api/agent` | `@PreAuthorize("hasAuthority('agent:create')")` |
| updateAgent | `PUT /api/agent/{id}` | `@PreAuthorize("hasAuthority('agent:edit')")` |
| deleteAgent | `DELETE /api/agent/{id}` | `@PreAuthorize("hasAuthority('agent:delete')")` |
| publishAgent | `POST /api/agent/{id}/publish` | `@PreAuthorize("hasAuthority('agent:edit')")` |
| offlineAgent | `POST /api/agent/{id}/offline` | `@PreAuthorize("hasAuthority('agent:edit')")` |
| pageAgents | `POST /api/agent/page` | `@PreAuthorize("hasAuthority('agent:view')")` |
| API Key 相关 | 各种 | `@PreAuthorize("hasAuthority('agent:edit')")` |

---

## 任务 6.3：DatasourceController 权限注解

**修改文件**: `controller/DatasourceController.java`

| 方法 | 路径 | 注解 |
|---|---|---|
| list | `GET /api/datasource` | `@PreAuthorize("hasAuthority('datasource:view')")` |
| page | `POST /api/datasource/page` | `@PreAuthorize("hasAuthority('datasource:view')")` |
| getById | `GET /api/datasource/{id}` | `@PreAuthorize("hasAuthority('datasource:view')")` |
| getTables | `GET /api/datasource/{id}/tables` | `@PreAuthorize("hasAuthority('datasource:view')")` |
| create | `POST /api/datasource` | `@PreAuthorize("hasAuthority('datasource:create')")` |
| update | `PUT /api/datasource/{id}` | `@PreAuthorize("hasAuthority('datasource:edit')")` |
| delete | `DELETE /api/datasource/{id}` | `@PreAuthorize("hasAuthority('datasource:delete')")` |
| test | `POST /api/datasource/{id}/test` | `@PreAuthorize("hasAuthority('datasource:test')")` |
| getColumns | `GET /api/datasource/{id}/tables/{table}/columns` | `@PreAuthorize("hasAuthority('datasource:view')")` |
| logicalRelations | 各种 | `@PreAuthorize("hasAuthority('datasource:edit')")` |

---

## 任务 6.4：KnowledgeController 权限注解

**修改文件**: `controller/AgentKnowledgeController.java`

| 方法 | 路径 | 注解 |
|---|---|---|
| getById | `GET /api/agent-knowledge/{id}` | `@PreAuthorize("hasAuthority('knowledge:view')")` |
| create | `POST /api/agent-knowledge/create` | `@PreAuthorize("hasAuthority('knowledge:create')")` |
| update | `PUT /api/agent-knowledge/{id}` | `@PreAuthorize("hasAuthority('knowledge:edit')")` |
| toggleRecall | `PUT /api/agent-knowledge/recall/{id}` | `@PreAuthorize("hasAuthority('knowledge:edit')")` |
| delete | `DELETE /api/agent-knowledge/{id}` | `@PreAuthorize("hasAuthority('knowledge:delete')")` |
| page | `POST /api/agent-knowledge/query/page` | `@PreAuthorize("hasAuthority('knowledge:view')")` |
| retryEmbedding | `POST /api/agent-knowledge/retry-embedding/{id}` | `@PreAuthorize("hasAuthority('knowledge:edit')")` |

**修改文件**: `controller/BusinessKnowledgeController.java`

类似添加权限注解。

---

## 任务 6.5：ModelConfigController 权限注解

**修改文件**: `controller/ModelConfigController.java`

| 方法 | 路径 | 注解 |
|---|---|---|
| list | `GET /api/model-config/list` | `@PreAuthorize("hasAuthority('model:view')")` |
| add | `POST /api/model-config/add` | `@PreAuthorize("hasAuthority('model:create')")` |
| update | `PUT /api/model-config/update` | `@PreAuthorize("hasAuthority('model:edit')")` |
| delete | `DELETE /api/model-config/{id}` | `@PreAuthorize("hasAuthority('model:delete')")` |
| activate | `POST /api/model-config/activate/{id}` | `@PreAuthorize("hasAuthority('model:edit')")` |
| forceActivate | `POST /api/model-config/force-activate/{id}` | `@PreAuthorize("hasAuthority('model:edit')")` |
| test | `POST /api/model-config/test` | `@PreAuthorize("hasAuthority('model:edit')")` |
| checkReady | `GET /api/model-config/check-ready` | `@PreAuthorize("hasAuthority('model:view')")` |
| vectorDimension | `GET /api/model-config/vector-dimension` | `@PreAuthorize("hasAuthority('model:view')")` |

---

## 任务 6.6：其他 Controller 权限注解

**修改文件**:

| Controller | 权限注解 |
|---|---|
| `AgentDatasourceController` | `datasource:view` / `datasource:edit` |
| `AgentPresetQuestionController` | `agent:edit` |
| `SemanticModelController` | `agent:edit` |
| `PromptConfigController` | `agent:edit` |
| `FileUploadController` | 登录用户即可 |
| `GraphController` | `agent:view`（聊天入口） |
| `SessionEventController` | 登录用户即可 |

---

## 任务 6.7：Nl2sqlController 双重鉴权

**修改文件**: `controller/Nl2sqlController.java`

**改造内容**:
- 保留现有 API Key 校验逻辑
- 新增 JWT Token 校验分支
- 优先使用 JWT Token（如果请求头中有 Authorization）
- 如果没有 Authorization 头，回退到 API Key 校验

```java
@GetMapping("/stream")
public SseEmitter stream(@RequestParam String query,
                          @RequestParam(required = false) String apiKey,
                          @RequestParam(required = false) Integer agentId,
                          HttpServletRequest request) {
    // 优先检查 JWT Token
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        String token = authHeader.substring(7);
        // JWT Token 校验逻辑
        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        // 继续处理...
    } else if (apiKey != null) {
        // 现有 API Key 校验逻辑（保持不变）
        // ...
    } else {
        throw new BizException(401003, "未提供认证信息");
    }
}
```

**注意**: `/nl2sql/stream` 在 SecurityConfig 中配置为白名单，所以 Security 过滤器不会拦截。鉴权逻辑在 Controller 内部处理。

---

## 任务 6.8：Spring Security 集成现有异常处理

**修改文件**: `aop/ExceptionAdvice.java`

确保 Spring Security 抛出的异常被正确处理：

```java
/** Spring Security 认证异常 */
@ExceptionHandler(AuthenticationException.class)
public ApiResponse<Void> handleAuthenticationException(AuthenticationException e) {
    log.warn("认证失败: {}", e.getMessage());
    if (e instanceof BadCredentialsException) {
        return ApiResponse.error(401001, "用户名或密码错误");
    } else if (e instanceof DisabledException) {
        return ApiResponse.error(401004, "账号已被禁用");
    } else if (e instanceof LockedException) {
        return ApiResponse.error(401005, "账号已被锁定");
    }
    return ApiResponse.error(401003, "认证失败");
}

/** Spring Security 权限异常 */
@ExceptionHandler(AccessDeniedException.class)
public ApiResponse<Void> handleAccessDeniedException(AccessDeniedException e) {
    log.warn("权限不足: {}", e.getMessage());
    return ApiResponse.error(403001, "无权限访问该资源");
}

/** 业务异常 */
@ExceptionHandler(BizException.class)
public ApiResponse<Void> handleBizException(BizException e) {
    log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
    return ApiResponse.error(e.getCode(), e.getMessage());
}

/** 方法参数校验异常 */
@ExceptionHandler(MethodArgumentNotValidException.class)
public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
    String message = e.getBindingResult().getFieldErrors().stream()
        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
        .collect(Collectors.joining(", "));
    return ApiResponse.error(400, message);
}
```

---

## 任务 6.9：MyBatis 拦截器注册

**修改文件**: `config/DataAgentConfiguration.java` 或新建 `config/MyBatisConfig.java`

注册 `DataPermissionInterceptor`：

```java
@Configuration
public class MyBatisConfig {
    @Bean
    public DataPermissionInterceptor dataPermissionInterceptor() {
        return new DataPermissionInterceptor();
    }
}
```

**注意**: 如果使用 `@Component` 注解方式注册拦截器，此步骤可省略。
