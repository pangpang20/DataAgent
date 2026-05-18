# 第四阶段：用户/角色/菜单 CRUD 服务

> 目标：实现完整的用户管理、角色管理、菜单管理 CRUD 接口。

---

## 任务 4.1：用户管理 DTO

**新建文件**（均在 `dto/user/` 包下）:

### UserCreateRequest.java
```java
@Data
public class UserCreateRequest {
    @NotBlank @Size(max = 50) private String username;
    @NotBlank private String password;
    @Size(max = 50) private String nickname;
    @Email @Size(max = 100) private String email;
    @Size(max = 20) private String phone;
    private Long tenantId;
    /** 角色 ID 列表 */
    private List<Long> roleIds;
}
```

### UserUpdateRequest.java
```java
@Data
public class UserUpdateRequest {
    @Size(max = 50) private String nickname;
    @Email @Size(max = 100) private String email;
    @Size(max = 20) private String phone;
    @Size(max = 500) private String avatar;
}
```

### UserQueryDTO.java
```java
@Data
public class UserQueryDTO {
    private String username;
    private Integer status;
    private Long roleId;
    private Long tenantId;
    @Min(1) private Integer pageNum = 1;
    @Min(1) @Max(100) private Integer pageSize = 10;
}
```

### UserDetailResponse.java
```java
@Data
@Builder
public class UserDetailResponse {
    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private Integer status;
    private Long tenantId;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private List<RoleSimpleDTO> roles;
}
```

### ProfileUpdateRequest.java
```java
@Data
public class ProfileUpdateRequest {
    @Size(max = 50) private String nickname;
    @Email @Size(max = 100) private String email;
    @Size(max = 20) private String phone;
}
```

---

## 任务 4.2：用户管理 Service

**新建文件**: `service/user/UserService.java`

```java
public interface UserService {
    /** 创建用户 */
    UserDetailResponse createUser(UserCreateRequest request, Long operatorId);

    /** 编辑用户 */
    UserDetailResponse updateUser(Long userId, UserUpdateRequest request, Long operatorId);

    /** 删除用户（逻辑删除） */
    void deleteUser(Long userId, Long operatorId);

    /** 分页查询用户 */
    PageResult<UserDetailResponse> listUsers(UserQueryDTO query);

    /** 用户详情 */
    UserDetailResponse getUserDetail(Long userId);

    /** 启用/禁用用户 */
    void toggleUserStatus(Long userId, Integer status, Long operatorId);

    /** 为用户分配角色 */
    void assignRoles(Long userId, List<Long> roleIds, Long operatorId);

    /** 管理员重置密码 */
    void resetPassword(Long userId, String newPassword, Long operatorId);

    /** 修改个人信息 */
    void updateProfile(Long userId, ProfileUpdateRequest request);

    /** 修改个人密码 */
    void changePassword(Long userId, String oldPassword, String newPassword);
}
```

**新建文件**: `service/user/UserServiceImpl.java`

**关键实现逻辑**:

**createUser**:
1. 校验用户名唯一（`selectByUsername` 不为 null → 409）
2. 校验密码强度（`PasswordValidator.validate`）
3. BCrypt 加密密码
4. 插入用户记录
5. 批量插入用户角色关联
6. 记录操作日志

**deleteUser**:
1. 校验用户存在
2. 逻辑删除（`deleted = 1`）
3. 清除用户角色关联
4. 记录操作日志

**assignRoles**:
1. 删除旧的角色关联（`deleteByUserId`）
2. 批量插入新的角色关联
3. 记录权限变更日志（变更前后值）

**resetPassword**:
1. 校验密码强度
2. BCrypt 加密新密码
3. 更新密码
4. 记录敏感操作日志

**changePassword**:
1. 校验旧密码（BCrypt 比对）
2. 校验新密码强度
3. 校验新密码不与最近 5 次密码重复（查密码历史表或日志）
4. BCrypt 加密新密码
5. 更新密码
6. 记录操作日志

---

## 任务 4.3：用户管理 Controller

**新建文件**: `controller/UserController.java`

```java
@RestController
@RequestMapping("/api/system/user")
public class UserController {

    @GetMapping
    @PreAuthorize("hasAuthority('user:list')")
    public ApiResponse<PageResult<UserDetailResponse>> listUsers(UserQueryDTO query) { ... }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:view')")
    public ApiResponse<UserDetailResponse> getUserDetail(@PathVariable Long id) { ... }

    @PostMapping
    @PreAuthorize("hasAuthority('user:create')")
    public ApiResponse<UserDetailResponse> createUser(@RequestBody @Valid UserCreateRequest request) { ... }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:edit')")
    public ApiResponse<UserDetailResponse> updateUser(@PathVariable Long id,
                                                       @RequestBody @Valid UserUpdateRequest request) { ... }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:delete')")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) { ... }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('user:toggle')")
    public ApiResponse<Void> toggleStatus(@PathVariable Long id,
                                           @RequestParam Integer status) { ... }

    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('user:reset-password')")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                            @RequestBody Map<String, String> body) { ... }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('user:assign-role')")
    public ApiResponse<Void> assignRoles(@PathVariable Long id,
                                          @RequestBody List<Long> roleIds) { ... }
}
```

**新建文件**: `controller/ProfileController.java`

```java
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    @GetMapping("/info")
    public ApiResponse<UserDetailResponse> getProfile() { ... }

    @PutMapping("/info")
    public ApiResponse<Void> updateProfile(@RequestBody @Valid ProfileUpdateRequest request) { ... }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@RequestBody @Valid ChangePasswordRequest request) { ... }
}
```

---

## 任务 4.4：角色管理 DTO

**新建文件**（均在 `dto/role/` 包下）:

### RoleCreateRequest.java
```java
@Data
public class RoleCreateRequest {
    @NotBlank @Size(max = 50) private String roleCode;
    @NotBlank @Size(max = 50) private String roleName;
    @Size(max = 200) private String description;
    private Integer sortOrder;
}
```

### RoleUpdateRequest.java
```java
@Data
public class RoleUpdateRequest {
    @Size(max = 50) private String roleName;
    @Size(max = 200) private String description;
    private Integer sortOrder;
}
```

### RoleQueryDTO.java
```java
@Data
public class RoleQueryDTO {
    private String roleName;
    private Integer status;
    private Long tenantId;
    @Min(1) private Integer pageNum = 1;
    @Min(1) @Max(100) private Integer pageSize = 10;
}
```

### RoleDetailResponse.java
```java
@Data
@Builder
public class RoleDetailResponse {
    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private Integer status;
    private Integer sortOrder;
    private Integer isSystem;
    private LocalDateTime createTime;
    private List<MenuSimpleDTO> menus;
    private List<PermissionSimpleDTO> permissions;
    private Integer userCount;
}
```

---

## 任务 4.5：角色管理 Service

**新建文件**: `service/role/RoleService.java`

```java
public interface RoleService {
    RoleDetailResponse createRole(RoleCreateRequest request, Long operatorId);
    RoleDetailResponse updateRole(Long roleId, RoleUpdateRequest request, Long operatorId);
    void deleteRole(Long roleId, Long operatorId);
    PageResult<RoleDetailResponse> listRoles(RoleQueryDTO query);
    RoleDetailResponse getRoleDetail(Long roleId);
    void toggleRoleStatus(Long roleId, Integer status, Long operatorId);
    void assignMenus(Long roleId, List<Long> menuIds, Long operatorId);
    void assignPermissions(Long roleId, List<Long> permissionIds, Long operatorId);
}
```

**新建文件**: `service/role/RoleServiceImpl.java`

**关键实现逻辑**:

**createRole**:
1. 校验角色编码唯一（`selectByRoleCode` 不为 null → 409）
2. 插入角色记录
3. 记录操作日志

**deleteRole**:
1. 校验角色存在
2. 校验是否为预置角色（`isSystem == 1` → 拒绝删除）
3. 查询角色下绑定用户数
   - 有绑定用户 → 返回确认信息（前端弹窗确认后重新调用，带 `force=true` 参数）
   - 确认删除 → 清除用户角色关联
4. 逻辑删除角色
5. 清除角色菜单关联和角色权限关联
6. 记录操作日志

**assignMenus**:
1. 校验菜单 ID 合法性
2. 删除旧的角色菜单关联
3. 批量插入新的角色菜单关联
4. 记录权限变更日志

**assignPermissions**:
1. 校验权限 ID 合法性
2. 删除旧的角色权限关联
3. 批量插入新的角色权限关联
4. 记录权限变更日志

---

## 任务 4.6：角色管理 Controller

**新建文件**: `controller/RoleController.java`

```java
@RestController
@RequestMapping("/api/system/role")
public class RoleController {

    @GetMapping
    @PreAuthorize("hasAuthority('role:list')")
    public ApiResponse<PageResult<RoleDetailResponse>> listRoles(RoleQueryDTO query) { ... }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('role:view')")
    public ApiResponse<RoleDetailResponse> getRoleDetail(@PathVariable Long id) { ... }

    @PostMapping
    @PreAuthorize("hasAuthority('role:create')")
    public ApiResponse<RoleDetailResponse> createRole(@RequestBody @Valid RoleCreateRequest request) { ... }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('role:edit')")
    public ApiResponse<RoleDetailResponse> updateRole(@PathVariable Long id,
                                                       @RequestBody @Valid RoleUpdateRequest request) { ... }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('role:delete')")
    public ApiResponse<Void> deleteRole(@PathVariable Long id,
                                         @RequestParam(defaultValue = "false") boolean force) { ... }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('role:toggle')")
    public ApiResponse<Void> toggleStatus(@PathVariable Long id,
                                           @RequestParam Integer status) { ... }

    @PutMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('role:assign-menu')")
    public ApiResponse<Void> assignMenus(@PathVariable Long id,
                                          @RequestBody List<Long> menuIds) { ... }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('role:assign-permission')")
    public ApiResponse<Void> assignPermissions(@PathVariable Long id,
                                                @RequestBody List<Long> permissionIds) { ... }
}
```

---

## 任务 4.7：菜单管理 DTO

**新建文件**（均在 `dto/menu/` 包下）:

### MenuCreateRequest.java
```java
@Data
public class MenuCreateRequest {
    @NotNull private Long parentId;
    @NotBlank @Size(max = 50) private String menuName;
    @NotNull @InEnum(value = MenuTypeEnum.class, message = "menuType 必须为 0/1/2")
    private Integer menuType;
    @Size(max = 200) private String path;
    @Size(max = 200) private String component;
    @Size(max = 100) private String permission;
    @Size(max = 100) private String icon;
    private Integer sortOrder;
    private Integer visible;
}
```

### MenuUpdateRequest.java
```java
@Data
public class MenuUpdateRequest {
    @Size(max = 50) private String menuName;
    @Size(max = 200) private String path;
    @Size(max = 200) private String component;
    @Size(max = 100) private String permission;
    @Size(max = 100) private String icon;
    private Integer sortOrder;
    private Integer visible;
    private Integer status;
}
```

### MenuTypeEnum.java
```java
public enum MenuTypeEnum {
    DIRECTORY(0), MENU(1), BUTTON(2);
    private final int value;
    MenuTypeEnum(int value) { this.value = value; }
    public int getValue() { return value; }
}
```

---

## 任务 4.8：菜单管理 Service

**新建文件**: `service/menu/MenuService.java`

```java
public interface MenuService {
    MenuTreeDTO createMenu(MenuCreateRequest request, Long operatorId);
    MenuTreeDTO updateMenu(Long menuId, MenuUpdateRequest request, Long operatorId);
    void deleteMenu(Long menuId, Long operatorId);
    List<MenuTreeDTO> getMenuTree();
    MenuTreeDTO getMenuDetail(Long menuId);
}
```

**新建文件**: `service/menu/MenuServiceImpl.java`

**关键实现逻辑**:

**createMenu**:
1. 校验父节点存在性（parentId=0 表示根节点，跳过校验）
2. 校验层级深度：
   - parentId=0 → 当前为第 1 级（目录）
   - 父节点为第 1 级 → 当前为第 2 级（菜单）
   - 父节点为第 2 级 → 当前为第 3 级（按钮）
   - 父节点为第 3 级 → 拒绝创建（超过三级）
3. 校验按钮节点不可有子节点（如果父节点是按钮类型，拒绝创建）
4. 校验菜单类型合法性：
   - 目录下可挂目录和菜单
   - 菜单下可挂按钮
   - 按钮下不可挂任何节点
5. 菜单类型为 1 时，`path` 和 `component` 必填
6. 按钮类型为 2 时，`permission` 必填
7. 插入菜单记录
8. 记录操作日志

**deleteMenu**:
1. 校验菜单存在
2. 校验是否有子菜单（`countByParentId > 0` → 拒绝删除，提示先删子菜单）
3. 逻辑删除
4. 清除相关角色菜单关联
5. 记录操作日志

**getMenuTree**:
1. 查询所有未删除的菜单
2. 构建三级树形结构（目录 → 菜单 → 按钮）
3. 按 sortOrder 排序

---

## 任务 4.9：菜单管理 Controller

**新建文件**: `controller/MenuController.java`

```java
@RestController
@RequestMapping("/api/system/menu")
public class MenuController {

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('menu:list')")
    public ApiResponse<List<MenuTreeDTO>> getMenuTree() { ... }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:view')")
    public ApiResponse<MenuTreeDTO> getMenuDetail(@PathVariable Long id) { ... }

    @PostMapping
    @PreAuthorize("hasAuthority('menu:create')")
    public ApiResponse<MenuTreeDTO> createMenu(@RequestBody @Valid MenuCreateRequest request) { ... }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:edit')")
    public ApiResponse<MenuTreeDTO> updateMenu(@PathVariable Long id,
                                                @RequestBody @Valid MenuUpdateRequest request) { ... }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:delete')")
    public ApiResponse<Void> deleteMenu(@PathVariable Long id) { ... }
}
```

---

## 任务 4.10：密码历史校验

**新建表**: `sys_password_history`

```sql
CREATE TABLE sys_password_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    password VARCHAR(200) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**修改文件**: `service/user/UserServiceImpl.java` — `changePassword` 方法中增加密码历史校验：
1. 查询该用户最近 5 条密码历史记录
2. 逐条用 BCrypt 比对新密码
3. 如果匹配则拒绝修改，提示"密码不可与最近5次使用过的密码重复"
4. 密码修改成功后，插入新密码到历史表
5. 如果历史记录超过 5 条，删除最早的记录

**单元测试**:
- 测试修改密码成功（新密码不在历史中）
- 测试修改密码失败（新密码与最近密码重复）
- 测试密码历史超过 5 条时自动清理
