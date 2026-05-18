-- 简化的数据库初始化脚本，兼容Spring Boot SQL初始化

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `model_config`;
DROP TABLE IF EXISTS `agent_datasource_tables`;
DROP TABLE IF EXISTS `user_prompt_config`;
DROP TABLE IF EXISTS `chat_message`;
DROP TABLE IF EXISTS `chat_session`;
DROP TABLE IF EXISTS `agent_preset_question`;
DROP TABLE IF EXISTS `agent_datasource`;
DROP TABLE IF EXISTS `logical_relation`;
DROP TABLE IF EXISTS `datasource`;
DROP TABLE IF EXISTS `agent_knowledge`;
DROP TABLE IF EXISTS `semantic_model`;
DROP TABLE IF EXISTS `business_knowledge`;
DROP TABLE IF EXISTS `agent`;
SET FOREIGN_KEY_CHECKS = 1;

-- 智能体表
CREATE TABLE IF NOT EXISTS agent (
    id INT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL COMMENT '智能体名称',
    description TEXT COMMENT '智能体描述',
    avatar TEXT COMMENT '头像URL',
    status VARCHAR(50) DEFAULT 'draft' COMMENT '状态：draft-待发布，published-已发布，offline-已下线',
    api_key VARCHAR(255) DEFAULT NULL COMMENT '访问 API Key，格式 sk-xxx',
    api_key_enabled TINYINT DEFAULT 0 COMMENT 'API Key 是否启用：0-禁用，1-启用',
    prompt TEXT COMMENT '自定义Prompt配置',
    category VARCHAR(100) COMMENT '分类',
    creator_id BIGINT COMMENT '创建者ID',
    tags TEXT COMMENT '标签，逗号分隔',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    human_review_enabled TINYINT DEFAULT 0 COMMENT '是否启用计划人工复核：0-否，1-是',
    is_deleted INT DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    INDEX idx_name (name),
    INDEX idx_status (status),
    INDEX idx_category (category),
    INDEX idx_creator_id (creator_id),
    INDEX idx_is_deleted (is_deleted)
    ) ENGINE = InnoDB COMMENT = '智能体表';

-- 业务知识表
CREATE TABLE IF NOT EXISTS business_knowledge (
  id INT NOT NULL AUTO_INCREMENT,
  business_term VARCHAR(255) NOT NULL COMMENT '业务名词',
  description TEXT COMMENT '描述',
  synonyms TEXT COMMENT '同义词，逗号分隔',
  is_recall INT DEFAULT 1 COMMENT '是否召回：0-不召回，1-召回',
  agent_id INT NOT NULL COMMENT '关联的智能体ID',
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  embedding_status VARCHAR(20) DEFAULT NULL COMMENT '向量化状态：PENDING待处理，PROCESSING处理中，COMPLETED已完成，FAILED失败',
  error_msg VARCHAR(255) DEFAULT NULL COMMENT '操作失败的错误信息',
  is_deleted INT DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
  PRIMARY KEY (id),
  INDEX idx_business_term (business_term),
  INDEX idx_agent_id (agent_id),
  INDEX idx_is_recall (is_recall),
  INDEX idx_embedding_status (embedding_status),
  INDEX idx_is_deleted (is_deleted),
  FOREIGN KEY (agent_id) REFERENCES agent(id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT = '业务知识表';

-- 语义模型表
CREATE TABLE IF NOT EXISTS `semantic_model` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `agent_id` int(11) NOT NULL COMMENT '关联的智能体ID',
  `datasource_id` int(11) NOT NULL COMMENT '关联的数据源ID',
  `table_name` varchar(255) COLLATE utf8mb4_bin NOT NULL COMMENT '关联的表名',
  `column_name` varchar(255) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '数据库中的物理字段名 (例如: csat_score)',
  `business_name` varchar(255) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '业务名/别名 (例如: 客户满意度分数)',
  `synonyms` text COLLATE utf8mb4_bin COMMENT '业务名的同义词 (例如: 满意度,客户评分)',
  `business_description` text COLLATE utf8mb4_bin COMMENT '业务描述 (用于向LLM解释字段的业务含义)',
  `column_comment` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '数据库中的物理字段的原始注释 ',
  `data_type` varchar(255) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '物理数据类型 (例如: int, varchar(20))',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '0 停用 1 启用',
  `is_deleted` int(11) DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
  `created_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_agent_id` (`agent_id`) USING BTREE,
  KEY `idx_field_name` (`business_name`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_is_deleted` (`is_deleted`) USING BTREE,
  CONSTRAINT `fk_semantic_model_agent` FOREIGN KEY (`agent_id`) REFERENCES `agent` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='语义模型表';


-- 智能体知识表
CREATE TABLE IF NOT EXISTS `agent_knowledge` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID, 用于内部关联',
  `agent_id` int(11) NOT NULL COMMENT '关联的智能体ID',
  `title` varchar(255) COLLATE utf8mb4_bin NOT NULL COMMENT '知识的标题 (用户定义, 用于在UI上展示和识别)',
  `type` varchar(50) COLLATE utf8mb4_bin NOT NULL COMMENT '知识类型: DOCUMENT-文档, QA-问答, FAQ-常见问题',
  `question` text COLLATE utf8mb4_bin COMMENT '问题 (仅当type为QA或FAQ时使用)',
  `content` mediumtext COLLATE utf8mb4_bin COMMENT '知识内容 (对于QA/FAQ是答案; 对于DOCUMENT, 此字段通常为空)',
  `is_recall` int(11) DEFAULT 1 COMMENT '业务状态: 1=召回, 0=非召回',
  `embedding_status` varchar(20) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '向量化状态：PENDING待处理，PROCESSING处理中，COMPLETED已完成，FAILED失败',
  `error_msg` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '操作失败的错误信息',
  `source_filename` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '上传时的原始文件名',
  `file_path` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '文件在服务器上的物理存储路径',
  `file_size` bigint(20) DEFAULT NULL COMMENT '文件大小 (字节)',
  `file_type` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '文件类型（pdf,md,markdown,doc等）',
  `splitter_type` varchar(50) COLLATE utf8mb4_bin DEFAULT 'DEFAULT' COMMENT '分片策略类型：DEFAULT-默认分片，CUSTOM-自定义分片',
  `created_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` int(11) DEFAULT 0 COMMENT '逻辑删除字段，0=未删除, 1=已删除',
  `is_resource_cleaned` int(11) DEFAULT 0 COMMENT '0=物理资源（文件和向量）未清理, 1=物理资源已清理',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_agent_id_status` (`agent_id`,`is_recall`) USING BTREE,
  KEY `idx_embedding_status` (`embedding_status`) USING BTREE,
  KEY `idx_is_deleted` (`is_deleted`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin ROW_FORMAT=DYNAMIC COMMENT='智能体知识源管理表 (支持文档、QA、FAQ)';

-- 数据源表
CREATE TABLE IF NOT EXISTS datasource (
  id INT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL COMMENT '数据源名称',
  type VARCHAR(50) NOT NULL COMMENT '数据源类型：mysql, postgresql',
  host VARCHAR(255) NOT NULL COMMENT '主机地址',
  port INT NOT NULL COMMENT '端口号',
  database_name VARCHAR(255) NOT NULL COMMENT '数据库名称',
  username VARCHAR(255) NOT NULL COMMENT '用户名',
  password VARCHAR(255) NOT NULL COMMENT '密码（加密存储）',
  connection_url VARCHAR(1000) COMMENT '完整连接URL',
  status VARCHAR(50) DEFAULT 'inactive' COMMENT '状态：active-启用，inactive-禁用',
  test_status VARCHAR(50) DEFAULT 'unknown' COMMENT '连接测试状态：success-成功，failed-失败，unknown-未知',
  description TEXT COMMENT '描述',
  creator_id BIGINT COMMENT '创建者ID',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_deleted INT DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
  PRIMARY KEY (id),
  INDEX idx_name (name),
  INDEX idx_type (type),
  INDEX idx_status (status),
  INDEX idx_creator_id (creator_id),
  INDEX idx_is_deleted (is_deleted)
) ENGINE = InnoDB COMMENT = '数据源表';

-- 逻辑外键配置表
CREATE TABLE IF NOT EXISTS logical_relation (
  id INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  datasource_id INT NOT NULL COMMENT '关联的数据源ID',
  source_table_name VARCHAR(100) NOT NULL COMMENT '主表名 (例如 t_order)',
  source_column_name VARCHAR(100) NOT NULL COMMENT '主表字段名 (例如 buyer_uid)',
  target_table_name VARCHAR(100) NOT NULL COMMENT '关联表名 (例如 t_user)',
  target_column_name VARCHAR(100) NOT NULL COMMENT '关联表字段名 (例如 id)',
  relation_type VARCHAR(20) DEFAULT NULL COMMENT '关系类型: 1:1, 1:N, N:1 (辅助LLM理解数据基数，可选)',
  description VARCHAR(500) DEFAULT NULL COMMENT '业务描述: 存入Prompt中帮助LLM理解 (例如: 订单表通过buyer_uid关联用户表id)',
  is_deleted TINYINT(1) DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  INDEX idx_datasource_id (datasource_id) COMMENT '加速根据数据源查找关系的查询',
  INDEX idx_source_table (datasource_id, source_table_name) COMMENT '加速根据表名查找关系的查询',
  FOREIGN KEY (datasource_id) REFERENCES datasource(id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '逻辑外键配置表';

-- 智能体数据源关联表
CREATE TABLE IF NOT EXISTS agent_datasource (
  id INT NOT NULL AUTO_INCREMENT,
  agent_id INT NOT NULL COMMENT '智能体ID',
  datasource_id INT NOT NULL COMMENT '数据源ID',
  is_active TINYINT DEFAULT 0 COMMENT '是否启用：0-禁用，1-启用',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_agent_datasource (agent_id, datasource_id),
  INDEX idx_agent_id (agent_id),
  INDEX idx_datasource_id (datasource_id),
  INDEX idx_is_active (is_active),
  FOREIGN KEY (agent_id) REFERENCES agent(id) ON DELETE CASCADE,
  FOREIGN KEY (datasource_id) REFERENCES datasource(id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT = '智能体数据源关联表';

-- 智能体预设问题表
CREATE TABLE IF NOT EXISTS agent_preset_question (
  id INT NOT NULL AUTO_INCREMENT,
  agent_id INT NOT NULL COMMENT '智能体ID',
  question TEXT NOT NULL COMMENT '预设问题内容',
  sort_order INT DEFAULT 0 COMMENT '排序顺序',
  is_active TINYINT DEFAULT 0 COMMENT '是否启用：0-禁用，1-启用',
  is_delete TINYINT DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  INDEX idx_agent_id (agent_id),
  INDEX idx_sort_order (sort_order),
  INDEX idx_is_active (is_active),
  INDEX idx_is_delete (is_delete),
  FOREIGN KEY (agent_id) REFERENCES agent(id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT = '智能体预设问题表';

-- 会话表
CREATE TABLE IF NOT EXISTS chat_session (
  id VARCHAR(36) NOT NULL COMMENT '会话ID（UUID）',
  agent_id INT NOT NULL COMMENT '智能体ID',
  title VARCHAR(255) DEFAULT '新对话' COMMENT '会话标题',
  status VARCHAR(50) DEFAULT 'active' COMMENT '状态：active-活跃，archived-归档，deleted-已删除',
  is_pinned TINYINT DEFAULT 0 COMMENT '是否置顶：0-否，1-是',
  user_id BIGINT COMMENT '用户ID',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  INDEX idx_agent_id (agent_id),
  INDEX idx_user_id (user_id),
  INDEX idx_status (status),
  INDEX idx_is_pinned (is_pinned),
  INDEX idx_create_time (create_time),
  FOREIGN KEY (agent_id) REFERENCES agent(id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT = '聊天会话表';

-- 消息表
CREATE TABLE IF NOT EXISTS chat_message (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id VARCHAR(36) NOT NULL COMMENT '会话ID',
  role VARCHAR(20) NOT NULL COMMENT '角色：user-用户，assistant-助手，system-系统',
  content TEXT NOT NULL COMMENT '消息内容',
  message_type VARCHAR(50) DEFAULT 'text' COMMENT '消息类型：text-文本，sql-SQL查询，result-查询结果，error-错误',
  metadata JSON COMMENT '元数据（JSON格式）',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  INDEX idx_session_id (session_id),
  INDEX idx_role (role),
  INDEX idx_message_type (message_type),
  INDEX idx_create_time (create_time),
  FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT = '聊天消息表';

-- 用户Prompt配置表
CREATE TABLE IF NOT EXISTS user_prompt_config (
  id VARCHAR(36) NOT NULL COMMENT '配置ID（UUID）',
  name VARCHAR(255) NOT NULL COMMENT '配置名称',
  prompt_type VARCHAR(100) NOT NULL COMMENT 'Prompt类型（如report-generator, planner等）',
  agent_id INT COMMENT '关联的智能体ID，为空表示全局配置',
  system_prompt TEXT NOT NULL COMMENT '用户自定义系统Prompt内容',
  enabled TINYINT DEFAULT 1 COMMENT '是否启用该配置：0-禁用，1-启用',
  description TEXT COMMENT '配置描述',
  priority INT DEFAULT 0 COMMENT '配置优先级，数字越大优先级越高',
  display_order INT DEFAULT 0 COMMENT '配置显示顺序，数字越小越靠前',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  creator VARCHAR(255) COMMENT '创建者',
  PRIMARY KEY (id),
  INDEX idx_prompt_type (prompt_type),
  INDEX idx_agent_id (agent_id),
  INDEX idx_enabled (enabled),
  INDEX idx_create_time (create_time),
  INDEX idx_prompt_type_enabled_priority (prompt_type, agent_id, enabled, priority DESC),
  INDEX idx_display_order (display_order ASC)
) ENGINE = InnoDB COMMENT = '用户Prompt配置表';

create table if not exists agent_datasource_tables
(
    id                  int auto_increment primary key,
    agent_datasource_id int                                 not null comment '智能体数据源ID',
    table_name          varchar(255)                        not null comment '数据表名',
    create_time         timestamp default CURRENT_TIMESTAMP null comment '创建时间',
    update_time         timestamp default CURRENT_TIMESTAMP null comment '更新时间',
    constraint agent_datasource_tables_agent_datasource_id_table_name_uindex
        unique (agent_datasource_id, table_name),
    constraint agent_datasource_tables_agent_datasource_id_fk
        foreign key (agent_datasource_id) references agent_datasource (id)
            on update cascade on delete cascade
)
    comment '某个智能体某个数据源所选中的数据表';


-- 模型配置表
CREATE TABLE IF NOT EXISTS `model_config` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `provider` varchar(255) NOT NULL COMMENT '厂商标识 (方便前端展示回显，实际调用主要靠 baseUrl)',
  `base_url` varchar(255) NOT NULL COMMENT '关键配置',
  `api_key` varchar(255) NOT NULL COMMENT 'API密钥',
  `model_name` varchar(255) NOT NULL COMMENT '模型名称',
  `temperature` decimal(10,2) unsigned DEFAULT '0.00' COMMENT '温度参数',
  `is_active` tinyint(1) DEFAULT '0' COMMENT '是否激活',
  `max_tokens` int(11) DEFAULT '2000' COMMENT '输出响应最大令牌数',
  `model_type` varchar(20) NOT NULL DEFAULT 'CHAT' COMMENT '模型类型 (CHAT/EMBEDDING)',
  `completions_path` varchar(255) DEFAULT NULL COMMENT 'Chat模型专用。附加到 Base URL 的路径。例如OpenAi的/v1/chat/completions',
  `embeddings_path` varchar(255) DEFAULT NULL COMMENT '嵌入模型专用。附加到 Base URL 的路径。',
  `auth_header_name` varchar(100) DEFAULT NULL COMMENT '自定义认证头名称。例如：szc-api-key。为空则使用标准的Authorization: Bearer',
  `created_time` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  `is_deleted` int(11) DEFAULT '0' COMMENT '0=未删除, 1=已删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 多轮对话历史记录表
CREATE TABLE IF NOT EXISTS conversation_turn (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `thread_id` varchar(64) NOT NULL COMMENT '会话线程ID',
  `user_question` text COMMENT '用户问题',
  `plan` text COMMENT 'AI规划输出',
  `sequence_number` int(11) NOT NULL DEFAULT '0' COMMENT '序号（用于排序）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversation_turn_thread_id` (`thread_id`),
  KEY `idx_conversation_turn_sequence` (`thread_id`, `sequence_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多轮对话历史记录表';

-- ========================================
-- RBAC 权限管理表
-- ========================================

-- 系统用户表
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

-- 系统角色表
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

-- 系统菜单表
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

-- 系统权限表
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

-- 用户角色关联表
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

-- 角色菜单关联表
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

-- 角色权限关联表
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

-- Agent数据权限表
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

-- 登录日志表
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

-- 操作日志表
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

-- 密码历史记录表
CREATE TABLE IF NOT EXISTS sys_password_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    password VARCHAR(200) NOT NULL COMMENT '历史密码（BCrypt加密）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_user_id (user_id)
) ENGINE = InnoDB COMMENT = '密码历史记录表';
