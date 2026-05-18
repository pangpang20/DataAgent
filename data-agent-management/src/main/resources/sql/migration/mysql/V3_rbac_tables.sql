-- RBAC 权限管理表（MySQL 版本）
-- 执行方式：手动执行或通过 Flyway 自动迁移

-- 1. 系统用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(200) NOT NULL COMMENT '密码（BCrypt加密）',
    nickname VARCHAR(100) COMMENT '昵称',
    email VARCHAR(200) COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '手机号',
    avatar VARCHAR(500) COMMENT '头像URL',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    tenant_id BIGINT DEFAULT 1 COMMENT '租户ID',
    last_login_time DATETIME COMMENT '最后登录时间',
    last_login_ip VARCHAR(50) COMMENT '最后登录IP',
    login_fail_count INT DEFAULT 0 COMMENT '连续登录失败次数',
    lock_time DATETIME COMMENT '账号锁定时间',
    password_update_time DATETIME COMMENT '密码最后修改时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted INT DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    INDEX idx_status (status),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_is_deleted (is_deleted)
) ENGINE = InnoDB COMMENT = '系统用户表';

-- 2. 系统角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
    role_key VARCHAR(50) NOT NULL COMMENT '角色标识',
    description VARCHAR(200) COMMENT '角色描述',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    tenant_id BIGINT DEFAULT 1 COMMENT '租户ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted INT DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_key (role_key),
    INDEX idx_status (status),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_is_deleted (is_deleted)
) ENGINE = InnoDB COMMENT = '系统角色表';

-- 3. 系统菜单表
CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT DEFAULT 0 COMMENT '父菜单ID，0为顶级',
    menu_name VARCHAR(100) NOT NULL COMMENT '菜单名称',
    menu_type VARCHAR(20) NOT NULL COMMENT '菜单类型：directory-目录，menu-菜单，button-按钮',
    path VARCHAR(200) COMMENT '路由地址',
    component VARCHAR(200) COMMENT '组件路径',
    icon VARCHAR(100) COMMENT '图标',
    permission VARCHAR(200) COMMENT '权限标识',
    sort_order INT DEFAULT 0 COMMENT '排序',
    visible TINYINT DEFAULT 1 COMMENT '是否可见：0-隐藏，1-显示',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_parent_id (parent_id),
    INDEX idx_menu_type (menu_type),
    INDEX idx_status (status)
) ENGINE = InnoDB COMMENT = '系统菜单表';

-- 4. 系统权限表
CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    permission_name VARCHAR(100) NOT NULL COMMENT '权限名称',
    permission_key VARCHAR(200) NOT NULL COMMENT '权限标识',
    description VARCHAR(200) COMMENT '权限描述',
    resource_type VARCHAR(50) COMMENT '资源类型：agent, datasource, knowledge 等',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_key (permission_key)
) ENGINE = InnoDB COMMENT = '系统权限表';

-- 5. 用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
) ENGINE = InnoDB COMMENT = '用户角色关联表';

-- 6. 角色菜单关联表
CREATE TABLE IF NOT EXISTS sys_role_menu (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_id BIGINT NOT NULL COMMENT '角色ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_menu (role_id, menu_id),
    INDEX idx_role_id (role_id),
    INDEX idx_menu_id (menu_id)
) ENGINE = InnoDB COMMENT = '角色菜单关联表';

-- 7. 角色权限关联表
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    INDEX idx_role_id (role_id),
    INDEX idx_permission_id (permission_id)
) ENGINE = InnoDB COMMENT = '角色权限关联表';

-- 8. Agent数据权限表
CREATE TABLE IF NOT EXISTS sys_agent_auth (
    id BIGINT NOT NULL AUTO_INCREMENT,
    agent_id INT NOT NULL COMMENT 'Agent ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    permission_level VARCHAR(20) NOT NULL COMMENT '权限级别：admin-管理员，write-读写，read-只读',
    granted_by BIGINT COMMENT '授权人ID',
    granted_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_user (agent_id, user_id),
    INDEX idx_agent_id (agent_id),
    INDEX idx_user_id (user_id),
    INDEX idx_permission_level (permission_level)
) ENGINE = InnoDB COMMENT = 'Agent数据权限表';

-- 9. 登录日志表
CREATE TABLE IF NOT EXISTS sys_login_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) COMMENT '用户名',
    ip VARCHAR(50) COMMENT '登录IP',
    user_agent VARCHAR(500) COMMENT '用户代理',
    status TINYINT COMMENT '登录状态：0-失败，1-成功',
    message VARCHAR(200) COMMENT '提示消息',
    login_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    PRIMARY KEY (id),
    INDEX idx_username (username),
    INDEX idx_login_time (login_time),
    INDEX idx_status (status)
) ENGINE = InnoDB COMMENT = '登录日志表';

-- 10. 操作日志表
CREATE TABLE IF NOT EXISTS sys_operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT COMMENT '操作用户ID',
    username VARCHAR(50) COMMENT '操作用户名',
    module VARCHAR(50) COMMENT '操作模块',
    operation VARCHAR(100) COMMENT '操作描述',
    method VARCHAR(200) COMMENT '请求方法',
    request_url VARCHAR(500) COMMENT '请求URL',
    request_params TEXT COMMENT '请求参数',
    response_result TEXT COMMENT '返回结果',
    ip VARCHAR(50) COMMENT '操作IP',
    status TINYINT COMMENT '操作状态：0-失败，1-成功',
    error_msg TEXT COMMENT '错误消息',
    cost_time BIGINT COMMENT '耗时（毫秒）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_user_id (user_id),
    INDEX idx_module (module),
    INDEX idx_create_time (create_time),
    INDEX idx_status (status)
) ENGINE = InnoDB COMMENT = '操作日志表';

-- 11. 密码历史记录表
CREATE TABLE IF NOT EXISTS sys_password_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    password VARCHAR(200) NOT NULL COMMENT '历史密码（BCrypt加密）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_user_id (user_id)
) ENGINE = InnoDB COMMENT = '密码历史记录表';
