-- 初始化数据文件（达梦版本）

SET DEFINE OFF;

-- 智能体示例数据
SET IDENTITY_INSERT agent ON;
INSERT INTO agent (id, name, description, avatar, status, api_key, api_key_enabled, prompt, category, creator_id, tags, create_time, update_time, human_review_enabled) VALUES 
(1, '中国人口GDP数据智能体', '专门处理中国人口和GDP相关数据查询分析的智能体', '/avatars/china-gdp-agent.png', 'draft', NULL, 0, '你是一个专业的数据分析助手，专门处理中国人口和GDP相关的数据查询。请根据用户的问题，生成准确的SQL查询语句。', '数据分析', 2100246635, '人口数据,GDP分析,经济统计', SYSDATE, SYSDATE, 0);
INSERT INTO agent (id, name, description, avatar, status, api_key, api_key_enabled, prompt, category, creator_id, tags, create_time, update_time, human_review_enabled) VALUES 
(2, '销售数据分析智能体', '专注于销售数据分析和业务指标计算的智能体', '/avatars/sales-agent.png', 'draft', NULL, 0, '你是一个销售数据分析专家，能够帮助用户分析销售趋势、客户行为和业务指标。', '业务分析', 2100246635, '销售分析,业务指标,客户分析', SYSDATE, SYSDATE, 0);
INSERT INTO agent (id, name, description, avatar, status, api_key, api_key_enabled, prompt, category, creator_id, tags, create_time, update_time, human_review_enabled) VALUES 
(3, '财务报表智能体', '专门处理财务数据和报表分析的智能体', '/avatars/finance-agent.png', 'draft', NULL, 0, '你是一个财务分析专家，专门处理财务数据查询和报表生成。', '财务分析', 2100246635, '财务数据,报表分析,会计', SYSDATE, SYSDATE, 0);
INSERT INTO agent (id, name, description, avatar, status, api_key, api_key_enabled, prompt, category, creator_id, tags, create_time, update_time, human_review_enabled) VALUES 
(4, '库存管理智能体', '专注于库存数据管理和供应链分析的智能体', '/avatars/inventory-agent.png', 'draft', NULL, 0, '你是一个库存管理专家，能够帮助用户查询库存状态、分析供应链数据。', '供应链', 2100246635, '库存管理,供应链,物流', SYSDATE, SYSDATE, 0);
SET IDENTITY_INSERT agent OFF;
COMMIT;

-- 数据源示例数据
SET IDENTITY_INSERT datasource ON;
INSERT INTO datasource (id, name, type, host, port, database_name, username, password, connection_url, status, test_status, description, creator_id, create_time, update_time) VALUES 
(1, '生产环境达梦数据库', 'dameng', 'localhost', 5236, 'product_db', 'SYSDBA', 'SYSDBA', 'jdbc:dm://localhost:5236/product_db', 'inactive', 'unknown', '生产环境主数据库，包含核心业务数据', 2100246635, SYSDATE, SYSDATE);
INSERT INTO datasource (id, name, type, host, port, database_name, username, password, connection_url, status, test_status, description, creator_id, create_time, update_time) VALUES 
(2, '数据仓库达梦数据库', 'dameng', 'localhost', 5236, 'china_population_db', 'SYSDBA', 'SYSDBA', 'jdbc:dm://localhost:5236/china_population_db', 'inactive', 'unknown', '数据仓库，用于数据分析和报表生成', 2100246635, SYSDATE, SYSDATE);
SET IDENTITY_INSERT datasource OFF;
COMMIT;

-- 业务知识示例数据
SET IDENTITY_INSERT business_knowledge ON;
INSERT INTO business_knowledge (id, business_term, description, synonyms, is_recall, agent_id, created_time, updated_time) VALUES
(1, 'Customer Satisfaction', 'Measures how satisfied customers are with the service or product.', 'customer happiness, client contentment', 0, 1, SYSDATE, SYSDATE);
INSERT INTO business_knowledge (id, business_term, description, synonyms, is_recall, agent_id, created_time, updated_time) VALUES
(2, 'Net Promoter Score', 'A measure of the likelihood of customers recommending a company to others.', 'NPS, customer loyalty score', 0, 1, SYSDATE, SYSDATE);
INSERT INTO business_knowledge (id, business_term, description, synonyms, is_recall, agent_id, created_time, updated_time) VALUES
(3, 'Customer Retention Rate', 'The percentage of customers who continue to use a service over a given period.', 'retention, customer loyalty', 0, 2, SYSDATE, SYSDATE);
SET IDENTITY_INSERT business_knowledge OFF;
COMMIT;

-- 语义模型示例数据
SET IDENTITY_INSERT semantic_model ON;
INSERT INTO semantic_model (id, agent_id, datasource_id, table_name, column_name, business_name, synonyms, business_description, column_comment, data_type, created_time, updated_time, status) VALUES
(1, 1, 2, 'customer_feedback', 'csat_score', 'customerSatisfactionScore', 'satisfaction score, customer rating', 'Customer satisfaction rating from 1-10', '客户满意度评分', 'integer', SYSDATE, SYSDATE, 0);
INSERT INTO semantic_model (id, agent_id, datasource_id, table_name, column_name, business_name, synonyms, business_description, column_comment, data_type, created_time, updated_time, status) VALUES
(2, 1, 2, 'customer_feedback', 'nps_value', 'netPromoterScore', 'NPS, promoter score', 'Net Promoter Score from -100 to 100', '净推荐值', 'integer', SYSDATE, SYSDATE, 0);
INSERT INTO semantic_model (id, agent_id, datasource_id, table_name, column_name, business_name, synonyms, business_description, column_comment, data_type, created_time, updated_time, status) VALUES
(3, 2, 1, 'customer_metrics', 'retention_pct', 'customerRetentionRate', 'retention rate, loyalty rate', 'Percentage of retained customers', '客户保留率', 'decimal', SYSDATE, SYSDATE, 0);
SET IDENTITY_INSERT semantic_model OFF;
COMMIT;

-- 智能体知识示例数据
SET IDENTITY_INSERT agent_knowledge ON;
INSERT INTO agent_knowledge (id, agent_id, title, content, type, is_recall, embedding_status, file_type, created_time, updated_time) VALUES 
(1, 1, '中国人口统计数据说明', '中国人口统计数据包含了历年的人口总数、性别比例、年龄结构、城乡分布等详细信息。数据来源于国家统计局，具有权威性和准确性。查询时请注意数据的时间范围和统计口径。', 'DOCUMENT', 1, 'PENDING', 'text', SYSDATE, SYSDATE);
INSERT INTO agent_knowledge (id, agent_id, title, content, type, is_recall, embedding_status, file_type, created_time, updated_time) VALUES 
(2, 1, 'GDP数据使用指南', 'GDP（国内生产总值）数据反映了国家经济发展水平。包含名义GDP、实际GDP、GDP增长率等指标。数据按季度和年度进行统计，支持按地区、行业进行分类查询。', 'DOCUMENT', 1, 'PENDING', 'text', SYSDATE, SYSDATE);
INSERT INTO agent_knowledge (id, agent_id, title, content, type, is_recall, embedding_status, file_type, created_time, updated_time) VALUES 
(3, 1, '常见查询问题', '问：如何查询2023年的人口数据？\n答：可以使用"SELECT * FROM population WHERE year = 2023"进行查询。\n\n问：如何计算GDP增长率？\n答：GDP增长率 = (当年GDP - 上年GDP) / 上年GDP * 100%', 'QA', 1, 'PENDING', 'text', SYSDATE, SYSDATE);
INSERT INTO agent_knowledge (id, agent_id, title, content, type, is_recall, embedding_status, file_type, created_time, updated_time) VALUES 
(4, 2, '销售数据字段说明', '销售数据表包含以下关键字段：\n- sales_amount：销售金额\n- customer_id：客户ID\n- product_id：产品ID\n- sales_date：销售日期\n- region：销售区域\n- sales_rep：销售代表', 'DOCUMENT', 1, 'PENDING', 'text', SYSDATE, SYSDATE);
INSERT INTO agent_knowledge (id, agent_id, title, content, type, is_recall, embedding_status, file_type, created_time, updated_time) VALUES 
(5, 2, '客户分析指标体系', '客户分析包含多个维度：\n1. 客户价值分析：RFM模型（最近购买时间、购买频次、购买金额）\n2. 客户生命周期：新客户、活跃客户、流失客户\n3. 客户满意度：NPS评分、满意度调研\n4. 客户行为分析：购买偏好、渠道偏好', 'DOCUMENT', 1, 'PENDING', 'text', SYSDATE, SYSDATE);
INSERT INTO agent_knowledge (id, agent_id, title, content, type, is_recall, embedding_status, file_type, created_time, updated_time) VALUES 
(6, 3, '财务报表模板', '标准财务报表包含：\n1. 资产负债表：反映企业财务状况\n2. 利润表：反映企业经营成果\n3. 现金流量表：反映企业现金流动情况\n4. 所有者权益变动表：反映股东权益变化', 'DOCUMENT', 1, 'PENDING', 'pdf', SYSDATE, SYSDATE);
INSERT INTO agent_knowledge (id, agent_id, title, content, type, is_recall, embedding_status, file_type, created_time, updated_time) VALUES 
(7, 4, '库存管理最佳实践', '库存管理的核心要点：\n1. 安全库存设置：确保不断货\n2. ABC分类管理：重点管理A类物料\n3. 先进先出原则：避免库存积压\n4. 定期盘点：确保数据准确性\n5. 供应商管理：建立稳定供应关系', 'DOCUMENT', 1, 'PENDING', 'text', SYSDATE, SYSDATE);
SET IDENTITY_INSERT agent_knowledge OFF;
COMMIT;

-- 智能体数据源关联示例数据
SET IDENTITY_INSERT agent_datasource ON;
INSERT INTO agent_datasource (id, agent_id, datasource_id, is_active, create_time, update_time) VALUES 
(1, 1, 2, 0, SYSDATE, SYSDATE);
INSERT INTO agent_datasource (id, agent_id, datasource_id, is_active, create_time, update_time) VALUES 
(2, 2, 1, 0, SYSDATE, SYSDATE);
INSERT INTO agent_datasource (id, agent_id, datasource_id, is_active, create_time, update_time) VALUES 
(3, 3, 1, 0, SYSDATE, SYSDATE);
INSERT INTO agent_datasource (id, agent_id, datasource_id, is_active, create_time, update_time) VALUES 
(4, 4, 1, 0, SYSDATE, SYSDATE);
SET IDENTITY_INSERT agent_datasource OFF;

COMMIT;

-- ========================================
-- RBAC 权限管理初始数据
-- ========================================

-- 预设角色
INSERT INTO sys_role (role_name, role_key, description, sort_order, status) VALUES ('超级管理员', 'SUPER_ADMIN', '系统超级管理员，拥有所有权限', 1, 1);
INSERT INTO sys_role (role_name, role_key, description, sort_order, status) VALUES ('租户管理员', 'TENANT_ADMIN', '租户管理员，管理租户下的用户和资源', 2, 1);
INSERT INTO sys_role (role_name, role_key, description, sort_order, status) VALUES ('审计员', 'AUDITOR', '审计员，可查看日志和审计数据', 3, 1);
INSERT INTO sys_role (role_name, role_key, description, sort_order, status) VALUES ('运维管理员', 'OPS_ADMIN', '运维管理员，管理数据源和系统配置', 4, 1);
INSERT INTO sys_role (role_name, role_key, description, sort_order, status) VALUES ('普通用户', 'NORMAL_USER', '普通用户，可使用已授权的Agent', 5, 1);
INSERT INTO sys_role (role_name, role_key, description, sort_order, status) VALUES ('访客', 'GUEST', '访客，仅有查看权限', 6, 1);

-- 超级管理员用户（密码: Admin@123456，BCrypt加密）
INSERT INTO sys_user (username, password, nickname, status) VALUES ('admin', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36Fz4kdFqBpt.uyVHSq7iSe', '超级管理员', 1);

-- 管理员角色关联
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r WHERE u.username = 'admin' AND r.role_key = 'SUPER_ADMIN';

-- 菜单数据
SET IDENTITY_INSERT sys_menu ON;
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (1, 0, 'Agent管理', 'directory', '/agent', NULL, 'robot', NULL, 1);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (2, 0, '系统管理', 'directory', '/system', NULL, 'setting', NULL, 2);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (3, 0, '审计日志', 'directory', '/audit', NULL, 'document', NULL, 3);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (101, 1, 'Agent列表', 'menu', '/agent/list', 'agent/List', NULL, 'agent:list', 1);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (102, 1, '创建Agent', 'menu', '/agent/create', 'agent/Create', NULL, 'agent:create', 2);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (103, 1, 'Agent详情', 'menu', '/agent/detail/:id', 'agent/Detail', NULL, 'agent:detail', 3);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (1001, 101, '编辑Agent', 'button', NULL, NULL, NULL, 'agent:edit', 1);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (1002, 101, '删除Agent', 'button', NULL, NULL, NULL, 'agent:delete', 2);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (1003, 101, '发布Agent', 'button', NULL, NULL, NULL, 'agent:publish', 3);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (1004, 101, 'API Key管理', 'button', NULL, NULL, NULL, 'agent:apikey', 4);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (201, 2, '用户管理', 'menu', '/system/user', 'system/User', 'user', 'system:user:list', 1);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (202, 2, '角色管理', 'menu', '/system/role', 'system/Role', 'peoples', 'system:role:list', 2);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (203, 2, '菜单管理', 'menu', '/system/menu', 'system/Menu', 'tree-table', 'system:menu:list', 3);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (204, 2, '模型配置', 'menu', '/system/model', 'system/Model', 'component', 'system:model:list', 4);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (205, 2, '数据源管理', 'menu', '/system/datasource', 'system/Datasource', 'database', 'system:datasource:list', 5);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (2001, 201, '创建用户', 'button', NULL, NULL, NULL, 'system:user:create', 1);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (2002, 201, '编辑用户', 'button', NULL, NULL, NULL, 'system:user:edit', 2);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (2003, 201, '删除用户', 'button', NULL, NULL, NULL, 'system:user:delete', 3);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (2004, 201, '重置密码', 'button', NULL, NULL, NULL, 'system:user:reset-password', 4);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (2005, 201, '分配角色', 'button', NULL, NULL, NULL, 'system:user:assign-role', 5);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (2011, 202, '创建角色', 'button', NULL, NULL, NULL, 'system:role:create', 1);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (2012, 202, '编辑角色', 'button', NULL, NULL, NULL, 'system:role:edit', 2);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (2013, 202, '删除角色', 'button', NULL, NULL, NULL, 'system:role:delete', 3);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (2014, 202, '分配权限', 'button', NULL, NULL, NULL, 'system:role:assign-permission', 4);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (301, 3, '登录日志', 'menu', '/audit/login-log', 'audit/LoginLog', NULL, 'audit:login-log:list', 1);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, permission, sort_order) VALUES (302, 3, '操作日志', 'menu', '/audit/operation-log', 'audit/OperationLog', NULL, 'audit:operation-log:list', 2);
SET IDENTITY_INSERT sys_menu OFF;

-- 权限标识
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES ('查看Agent列表', 'agent:list', '查看Agent列表', 'agent');
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES ('创建Agent', 'agent:create', '创建新Agent', 'agent');
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES ('编辑Agent', 'agent:edit', '编辑Agent配置', 'agent');
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES ('删除Agent', 'agent:delete', '删除Agent', 'agent');
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES ('发布Agent', 'agent:publish', '发布/下线Agent', 'agent');
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES ('管理API Key', 'agent:apikey', '管理Agent的API Key', 'agent');
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES ('查看Agent详情', 'agent:detail', '查看Agent详情和使用', 'agent');
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES ('查看数据源列表', 'datasource:list', '查看数据源列表', 'datasource');
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES ('创建数据源', 'datasource:create', '创建新数据源', 'datasource');
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES ('编辑数据源', 'datasource:edit', '编辑数据源配置', 'datasource');
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES ('删除数据源', 'datasource:delete', '删除数据源', 'datasource');
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES ('测试数据源连接', 'datasource:test', '测试数据源连接', 'datasource');
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES ('查看知识库', 'knowledge:list', '查看知识库内容', 'knowledge');
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES ('创建知识', 'knowledge:create', '创建知识条目', 'knowledge');
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES ('编辑知识', 'knowledge:edit', '编辑知识条目', 'knowledge');
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES ('删除知识', 'knowledge:delete', '删除知识条目', 'knowledge');
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES ('用户管理', 'system:user:*', '用户管理全部权限', 'system');
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES ('角色管理', 'system:role:*', '角色管理全部权限', 'system');
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES ('菜单管理', 'system:menu:*', '菜单管理全部权限', 'system');
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES ('模型配置', 'system:model:*', '模型配置全部权限', 'system');
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES ('查看登录日志', 'audit:login-log:list', '查看登录日志', 'audit');
INSERT INTO sys_permission (permission_name, permission_key, description, resource_type) VALUES ('查看操作日志', 'audit:operation-log:list', '查看操作日志', 'audit');

-- 超级管理员拥有所有菜单权限
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r, sys_menu m WHERE r.role_key = 'SUPER_ADMIN';

-- 超级管理员拥有所有权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p WHERE r.role_key = 'SUPER_ADMIN';

COMMIT;
EXIT;
