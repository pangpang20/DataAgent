-- RBAC 权限管理表（达梦版本）

-- 1. 系统用户表
CREATE TABLE sys_user (
    id BIGINT NOT NULL IDENTITY(1,1),
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
CREATE INDEX idx_sys_user_status ON sys_user(status);
CREATE INDEX idx_sys_user_tenant_id ON sys_user(tenant_id);
CREATE INDEX idx_sys_user_is_deleted ON sys_user(is_deleted);

COMMENT ON TABLE sys_user IS '系统用户表';
COMMENT ON COLUMN sys_user.username IS '用户名';
COMMENT ON COLUMN sys_user.password IS '密码（BCrypt加密）';
COMMENT ON COLUMN sys_user.status IS '状态：0-禁用，1-启用';
COMMENT ON COLUMN sys_user.tenant_id IS '租户ID';
COMMENT ON COLUMN sys_user.login_fail_count IS '连续登录失败次数';
COMMENT ON COLUMN sys_user.lock_time IS '账号锁定时间';
COMMENT ON COLUMN sys_user.is_deleted IS '逻辑删除：0-未删除，1-已删除';

-- 2. 系统角色表
CREATE TABLE sys_role (
    id BIGINT NOT NULL IDENTITY(1,1),
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
CREATE INDEX idx_sys_role_status ON sys_role(status);
CREATE INDEX idx_sys_role_tenant_id ON sys_role(tenant_id);
CREATE INDEX idx_sys_role_is_deleted ON sys_role(is_deleted);

COMMENT ON TABLE sys_role IS '系统角色表';
COMMENT ON COLUMN sys_role.role_name IS '角色名称';
COMMENT ON COLUMN sys_role.role_key IS '角色标识';
COMMENT ON COLUMN sys_role.is_deleted IS '逻辑删除：0-未删除，1-已删除';

-- 3. 系统菜单表
CREATE TABLE sys_menu (
    id BIGINT NOT NULL IDENTITY(1,1),
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
CREATE INDEX idx_sys_menu_parent_id ON sys_menu(parent_id);
CREATE INDEX idx_sys_menu_menu_type ON sys_menu(menu_type);
CREATE INDEX idx_sys_menu_status ON sys_menu(status);

COMMENT ON TABLE sys_menu IS '系统菜单表';
COMMENT ON COLUMN sys_menu.parent_id IS '父菜单ID，0为顶级';
COMMENT ON COLUMN sys_menu.menu_type IS '菜单类型：directory-目录，menu-菜单，button-按钮';
COMMENT ON COLUMN sys_menu.visible IS '是否可见：0-隐藏，1-显示';

-- 4. 系统权限表
CREATE TABLE sys_permission (
    id BIGINT NOT NULL IDENTITY(1,1),
    permission_name VARCHAR(100) NOT NULL,
    permission_key VARCHAR(200) NOT NULL,
    description VARCHAR(200),
    resource_type VARCHAR(50),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_permission_key UNIQUE (permission_key)
);

COMMENT ON TABLE sys_permission IS '系统权限表';
COMMENT ON COLUMN sys_permission.permission_key IS '权限标识';

-- 5. 用户角色关联表
CREATE TABLE sys_user_role (
    id BIGINT NOT NULL IDENTITY(1,1),
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_role UNIQUE (user_id, role_id)
);
CREATE INDEX idx_sys_user_role_user_id ON sys_user_role(user_id);
CREATE INDEX idx_sys_user_role_role_id ON sys_user_role(role_id);

COMMENT ON TABLE sys_user_role IS '用户角色关联表';

-- 6. 角色菜单关联表
CREATE TABLE sys_role_menu (
    id BIGINT NOT NULL IDENTITY(1,1),
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_menu UNIQUE (role_id, menu_id)
);
CREATE INDEX idx_sys_role_menu_role_id ON sys_role_menu(role_id);
CREATE INDEX idx_sys_role_menu_menu_id ON sys_role_menu(menu_id);

COMMENT ON TABLE sys_role_menu IS '角色菜单关联表';

-- 7. 角色权限关联表
CREATE TABLE sys_role_permission (
    id BIGINT NOT NULL IDENTITY(1,1),
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_permission UNIQUE (role_id, permission_id)
);
CREATE INDEX idx_sys_role_permission_role_id ON sys_role_permission(role_id);
CREATE INDEX idx_sys_role_permission_permission_id ON sys_role_permission(permission_id);

COMMENT ON TABLE sys_role_permission IS '角色权限关联表';

-- 8. Agent数据权限表
CREATE TABLE sys_agent_auth (
    id BIGINT NOT NULL IDENTITY(1,1),
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
CREATE INDEX idx_sys_agent_auth_agent_id ON sys_agent_auth(agent_id);
CREATE INDEX idx_sys_agent_auth_user_id ON sys_agent_auth(user_id);
CREATE INDEX idx_sys_agent_auth_permission_level ON sys_agent_auth(permission_level);

COMMENT ON TABLE sys_agent_auth IS 'Agent数据权限表';
COMMENT ON COLUMN sys_agent_auth.permission_level IS '权限级别：admin-管理员，write-读写，read-只读';

-- 9. 登录日志表
CREATE TABLE sys_login_log (
    id BIGINT NOT NULL IDENTITY(1,1),
    username VARCHAR(50),
    ip VARCHAR(50),
    user_agent VARCHAR(500),
    status TINYINT,
    message VARCHAR(200),
    login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_sys_login_log_username ON sys_login_log(username);
CREATE INDEX idx_sys_login_log_login_time ON sys_login_log(login_time);
CREATE INDEX idx_sys_login_log_status ON sys_login_log(status);

COMMENT ON TABLE sys_login_log IS '登录日志表';
COMMENT ON COLUMN sys_login_log.status IS '登录状态：0-失败，1-成功';

-- 10. 操作日志表
CREATE TABLE sys_operation_log (
    id BIGINT NOT NULL IDENTITY(1,1),
    user_id BIGINT,
    username VARCHAR(50),
    module VARCHAR(50),
    operation VARCHAR(100),
    method VARCHAR(200),
    request_url VARCHAR(500),
    request_params CLOB,
    response_result CLOB,
    ip VARCHAR(50),
    status TINYINT,
    error_msg CLOB,
    cost_time BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_sys_operation_log_user_id ON sys_operation_log(user_id);
CREATE INDEX idx_sys_operation_log_module ON sys_operation_log(module);
CREATE INDEX idx_sys_operation_log_create_time ON sys_operation_log(create_time);
CREATE INDEX idx_sys_operation_log_status ON sys_operation_log(status);

COMMENT ON TABLE sys_operation_log IS '操作日志表';
COMMENT ON COLUMN sys_operation_log.cost_time IS '耗时（毫秒）';

-- 11. 密码历史记录表
CREATE TABLE sys_password_history (
    id BIGINT NOT NULL IDENTITY(1,1),
    user_id BIGINT NOT NULL,
    password VARCHAR(200) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_sys_password_history_user_id ON sys_password_history(user_id);

COMMENT ON TABLE sys_password_history IS '密码历史记录表';

-- 更新时间触发器
CREATE OR REPLACE TRIGGER trg_sys_user_update_time BEFORE UPDATE ON sys_user FOR EACH ROW BEGIN :NEW.update_time := SYSDATE; END;
/
CREATE OR REPLACE TRIGGER trg_sys_role_update_time BEFORE UPDATE ON sys_role FOR EACH ROW BEGIN :NEW.update_time := SYSDATE; END;
/
CREATE OR REPLACE TRIGGER trg_sys_menu_update_time BEFORE UPDATE ON sys_menu FOR EACH ROW BEGIN :NEW.update_time := SYSDATE; END;
/
CREATE OR REPLACE TRIGGER trg_sys_permission_update_time BEFORE UPDATE ON sys_permission FOR EACH ROW BEGIN :NEW.update_time := SYSDATE; END;
/
CREATE OR REPLACE TRIGGER trg_sys_agent_auth_update_time BEFORE UPDATE ON sys_agent_auth FOR EACH ROW BEGIN :NEW.update_time := SYSDATE; END;
/

EXIT;
