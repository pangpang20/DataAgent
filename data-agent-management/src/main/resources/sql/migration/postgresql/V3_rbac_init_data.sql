-- RBAC 初始数据（PostgreSQL 版本）

-- 预设角色
INSERT INTO sys_role (role_name, role_key, description, sort_order, status) VALUES
('超级管理员', 'SUPER_ADMIN', '系统超级管理员，拥有所有权限', 1, 1),
('租户管理员', 'TENANT_ADMIN', '租户管理员，管理租户下的用户和资源', 2, 1),
('审计员', 'AUDITOR', '审计员，可查看日志和审计数据', 3, 1),
('运维管理员', 'OPS_ADMIN', '运维管理员，管理数据源和系统配置', 4, 1),
('普通用户', 'NORMAL_USER', '普通用户，可使用已授权的Agent', 5, 1),
('访客', 'GUEST', '访客，仅有查看权限', 6, 1)
ON CONFLICT (role_key) DO NOTHING;

-- 超级管理员用户（密码: Admin@123456，BCrypt加密）
INSERT INTO sys_user (username, password, nickname, status) VALUES
('admin', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36Fz4kdFqBpt.uyVHSq7iSe', '超级管理员', 1)
ON CONFLICT (username) DO NOTHING;

-- 管理员角色关联
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r WHERE u.username = 'admin' AND r.role_key = 'SUPER_ADMIN'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- 菜单数据
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES
(1, 0, 'Agent管理', 'directory', '/agent', NULL, 'robot', NULL, 1),
(2, 0, '系统管理', 'directory', '/system', NULL, 'setting', NULL, 2),
(3, 0, '审计日志', 'directory', '/audit', NULL, 'document', NULL, 3),
(101, 1, 'Agent列表', 'menu', '/agent/list', 'agent/List', NULL, 'agent:list', 1),
(102, 1, '创建Agent', 'menu', '/agent/create', 'agent/Create', NULL, 'agent:create', 2),
(103, 1, 'Agent详情', 'menu', '/agent/detail/:id', 'agent/Detail', NULL, 'agent:detail', 3),
(1001, 101, '编辑Agent', 'button', NULL, NULL, NULL, 'agent:edit', 1),
(1002, 101, '删除Agent', 'button', NULL, NULL, NULL, 'agent:delete', 2),
(1003, 101, '发布Agent', 'button', NULL, NULL, NULL, 'agent:publish', 3),
(1004, 101, 'API Key管理', 'button', NULL, NULL, NULL, 'agent:apikey', 4),
(201, 2, '用户管理', 'menu', '/system/user', 'system/User', 'user', 'system:user:list', 1),
(202, 2, '角色管理', 'menu', '/system/role', 'system/Role', 'peoples', 'system:role:list', 2),
(203, 2, '菜单管理', 'menu', '/system/menu', 'system/Menu', 'tree-table', 'system:menu:list', 3),
(204, 2, '模型配置', 'menu', '/system/model', 'system/Model', 'component', 'system:model:list', 4),
(205, 2, '数据源管理', 'menu', '/system/datasource', 'system/Datasource', 'database', 'system:datasource:list', 5),
(2001, 201, '创建用户', 'button', NULL, NULL, NULL, 'system:user:create', 1),
(2002, 201, '编辑用户', 'button', NULL, NULL, NULL, 'system:user:edit', 2),
(2003, 201, '删除用户', 'button', NULL, NULL, NULL, 'system:user:delete', 3),
(2004, 201, '重置密码', 'button', NULL, NULL, NULL, 'system:user:reset-password', 4),
(2005, 201, '分配角色', 'button', NULL, NULL, NULL, 'system:user:assign-role', 5),
(2011, 202, '创建角色', 'button', NULL, NULL, NULL, 'system:role:create', 1),
(2012, 202, '编辑角色', 'button', NULL, NULL, NULL, 'system:role:edit', 2),
(2013, 202, '删除角色', 'button', NULL, NULL, NULL, 'system:role:delete', 3),
(2014, 202, '分配权限', 'button', NULL, NULL, NULL, 'system:role:assign-permission', 4),
(301, 3, '登录日志', 'menu', '/audit/login-log', 'audit/LoginLog', NULL, 'audit:login-log:list', 1),
(302, 3, '操作日志', 'menu', '/audit/operation-log', 'audit/OperationLog', NULL, 'audit:operation-log:list', 2)
ON CONFLICT (id) DO NOTHING;

-- 权限标识
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES
('查看Agent列表', 'agent:list', '查看Agent列表', 'agent'),
('创建Agent', 'agent:create', '创建新Agent', 'agent'),
('编辑Agent', 'agent:edit', '编辑Agent配置', 'agent'),
('删除Agent', 'agent:delete', '删除Agent', 'agent'),
('发布Agent', 'agent:publish', '发布/下线Agent', 'agent'),
('管理API Key', 'agent:apikey', '管理Agent的API Key', 'agent'),
('查看Agent详情', 'agent:detail', '查看Agent详情和使用', 'agent'),
('查看数据源列表', 'datasource:list', '查看数据源列表', 'datasource'),
('创建数据源', 'datasource:create', '创建新数据源', 'datasource'),
('编辑数据源', 'datasource:edit', '编辑数据源配置', 'datasource'),
('删除数据源', 'datasource:delete', '删除数据源', 'datasource'),
('测试数据源连接', 'datasource:test', '测试数据源连接', 'datasource'),
('查看知识库', 'knowledge:list', '查看知识库内容', 'knowledge'),
('创建知识', 'knowledge:create', '创建知识条目', 'knowledge'),
('编辑知识', 'knowledge:edit', '编辑知识条目', 'knowledge'),
('删除知识', 'knowledge:delete', '删除知识条目', 'knowledge'),
('用户管理', 'system:user:*', '用户管理全部权限', 'system'),
('角色管理', 'system:role:*', '角色管理全部权限', 'system'),
('菜单管理', 'system:menu:*', '菜单管理全部权限', 'system'),
('模型配置', 'system:model:*', '模型配置全部权限', 'system'),
('查看登录日志', 'audit:login-log:list', '查看登录日志', 'audit'),
('查看操作日志', 'audit:operation-log:list', '查看操作日志', 'audit')
ON CONFLICT (permission_key) DO NOTHING;

-- 超级管理员拥有所有菜单权限
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r, sys_menu m WHERE r.role_key = 'SUPER_ADMIN'
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- 超级管理员拥有所有权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p WHERE r.role_key = 'SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
