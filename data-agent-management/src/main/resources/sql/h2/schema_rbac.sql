-- RBAC 权限管理表（H2 测试版本，MySQL兼容模式）

-- 1. 系统用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(200) NOT NULL,
    nickname VARCHAR(100),
    email VARCHAR(200),
    phone VARCHAR(20),
    avatar VARCHAR(500),
    status TINYINT DEFAULT 1,
    tenant_id BIGINT DEFAULT 1,
    last_login_time TIMESTAMP,
    last_login_ip VARCHAR(50),
    login_fail_count INT DEFAULT 0,
    lock_time TIMESTAMP,
    password_update_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);

-- 2. 系统角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_name VARCHAR(50) NOT NULL,
    role_key VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    sort_order INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    tenant_id BIGINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_role_key UNIQUE (role_key)
);

-- 3. 系统菜单表
CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT DEFAULT 0,
    menu_name VARCHAR(100) NOT NULL,
    menu_type VARCHAR(20) NOT NULL,
    path VARCHAR(200),
    component VARCHAR(200),
    icon VARCHAR(100),
    permission VARCHAR(200),
    sort_order INT DEFAULT 0,
    visible TINYINT DEFAULT 1,
    status TINYINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- 4. 系统权限表
CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    permission_name VARCHAR(100) NOT NULL,
    permission_key VARCHAR(200) NOT NULL,
    description VARCHAR(200),
    resource_type VARCHAR(50),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_permission_key UNIQUE (permission_key)
);

-- 5. 用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_role UNIQUE (user_id, role_id)
);

-- 6. 角色菜单关联表
CREATE TABLE IF NOT EXISTS sys_role_menu (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_menu UNIQUE (role_id, menu_id)
);

-- 7. 角色权限关联表
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_permission UNIQUE (role_id, permission_id)
);

-- 8. Agent数据权限表
CREATE TABLE IF NOT EXISTS sys_agent_auth (
    id BIGINT NOT NULL AUTO_INCREMENT,
    agent_id INT NOT NULL,
    user_id BIGINT NOT NULL,
    permission_level VARCHAR(20) NOT NULL,
    granted_by BIGINT,
    granted_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_agent_auth UNIQUE (agent_id, user_id)
);

-- 9. 登录日志表
CREATE TABLE IF NOT EXISTS sys_login_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50),
    ip VARCHAR(50),
    user_agent VARCHAR(500),
    status TINYINT,
    message VARCHAR(200),
    login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- 10. 操作日志表
CREATE TABLE IF NOT EXISTS sys_operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT,
    username VARCHAR(50),
    module VARCHAR(50),
    operation VARCHAR(100),
    method VARCHAR(200),
    request_url VARCHAR(500),
    request_params TEXT,
    response_result TEXT,
    ip VARCHAR(50),
    status TINYINT,
    error_msg TEXT,
    cost_time BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- 11. 密码历史记录表
CREATE TABLE IF NOT EXISTS sys_password_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    password VARCHAR(200) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
