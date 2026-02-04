-- DataAgent 元数据库表结构（达梦版本）

DROP TABLE IF EXISTS "MODEL_CONFIG" CASCADE;
DROP TABLE IF EXISTS "USER_PROMPT_CONFIG" CASCADE;
DROP TABLE IF EXISTS "CHAT_MESSAGE" CASCADE;
DROP TABLE IF EXISTS "CHAT_SESSION" CASCADE;
DROP TABLE IF EXISTS "AGENT_PRESET_QUESTION" CASCADE;
DROP TABLE IF EXISTS "AGENT_DATASOURCE_TABLES" CASCADE;
DROP TABLE IF EXISTS "AGENT_DATASOURCE" CASCADE;
DROP TABLE IF EXISTS "LOGICAL_RELATION" CASCADE;
DROP TABLE IF EXISTS "DATASOURCE" CASCADE;
DROP TABLE IF EXISTS "AGENT_KNOWLEDGE" CASCADE;
DROP TABLE IF EXISTS "SEMANTIC_MODEL" CASCADE;
DROP TABLE IF EXISTS "BUSINESS_KNOWLEDGE" CASCADE;
DROP TABLE IF EXISTS "AGENT" CASCADE;

-- 1. 智能体表
CREATE TABLE agent (
    id INT NOT NULL IDENTITY(1,1),
    name VARCHAR(255) NOT NULL,
    description CLOB,
    avatar CLOB,
    status VARCHAR(50) DEFAULT 'draft',
    api_key VARCHAR(255) DEFAULT NULL,
    api_key_enabled TINYINT DEFAULT 0,
    prompt CLOB,
    category VARCHAR(100),
    admin_id BIGINT,
    tags CLOB,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    human_review_enabled TINYINT DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_agent_name ON agent(name);
CREATE INDEX idx_agent_status ON agent(status);
CREATE INDEX idx_agent_category ON agent(category);
CREATE INDEX idx_agent_admin_id ON agent(admin_id);

COMMENT ON TABLE agent IS '智能体表';
COMMENT ON COLUMN agent.name IS '智能体名称';
COMMENT ON COLUMN agent.description IS '智能体描述';
COMMENT ON COLUMN agent.avatar IS '头像URL';
COMMENT ON COLUMN agent.status IS '状态：draft-待发布，published-已发布，offline-已下线';
COMMENT ON COLUMN agent.api_key IS '访问 API Key，格式 sk-xxx';
COMMENT ON COLUMN agent.api_key_enabled IS 'API Key 是否启用：0-禁用，1-启用';
COMMENT ON COLUMN agent.prompt IS '自定义Prompt配置';
COMMENT ON COLUMN agent.category IS '分类';
COMMENT ON COLUMN agent.admin_id IS '管理员ID';
COMMENT ON COLUMN agent.tags IS '标签，逗号分隔';
COMMENT ON COLUMN agent.create_time IS '创建时间';
COMMENT ON COLUMN agent.update_time IS '更新时间';
COMMENT ON COLUMN agent.human_review_enabled IS '是否启用计划人工复核：0-否，1-是';

-- 2. 业务知识表
CREATE TABLE business_knowledge (
    id INT NOT NULL IDENTITY(1,1),
    business_term VARCHAR(255) NOT NULL,
    description CLOB,
    synonyms CLOB,
    is_recall INT DEFAULT 1,
    agent_id INT NOT NULL,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    embedding_status VARCHAR(20) DEFAULT NULL,
    error_msg VARCHAR(255) DEFAULT NULL,
    is_deleted INT DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_business_knowledge_agent FOREIGN KEY (agent_id) REFERENCES agent(id) ON DELETE CASCADE
);
CREATE INDEX idx_bk_business_term ON business_knowledge(business_term);
CREATE INDEX idx_bk_agent_id ON business_knowledge(agent_id);
CREATE INDEX idx_bk_is_recall ON business_knowledge(is_recall);
CREATE INDEX idx_bk_embedding_status ON business_knowledge(embedding_status);
CREATE INDEX idx_bk_is_deleted ON business_knowledge(is_deleted);

COMMENT ON TABLE business_knowledge IS '业务知识表';
COMMENT ON COLUMN business_knowledge.business_term IS '业务名词';
COMMENT ON COLUMN business_knowledge.description IS '描述';
COMMENT ON COLUMN business_knowledge.synonyms IS '同义词，逗号分隔';
COMMENT ON COLUMN business_knowledge.is_recall IS '是否召回：0-不召回，1-召回';
COMMENT ON COLUMN business_knowledge.agent_id IS '关联的智能体ID';
COMMENT ON COLUMN business_knowledge.created_time IS '创建时间';
COMMENT ON COLUMN business_knowledge.updated_time IS '更新时间';
COMMENT ON COLUMN business_knowledge.embedding_status IS '向量化状态：PENDING待处理，PROCESSING处理中，COMPLETED已完成，FAILED失败';
COMMENT ON COLUMN business_knowledge.error_msg IS '操作失败的错误信息';
COMMENT ON COLUMN business_knowledge.is_deleted IS '逻辑删除：0-未删除，1-已删除';

-- 3. 语义模型表
CREATE TABLE semantic_model (
    id INT NOT NULL IDENTITY(1,1),
    agent_id INT NOT NULL,
    datasource_id INT NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    column_name VARCHAR(255) DEFAULT '' NOT NULL,
    business_name VARCHAR(255) DEFAULT '' NOT NULL,
    synonyms CLOB,
    business_description CLOB,
    column_comment VARCHAR(255) DEFAULT NULL,
    data_type VARCHAR(255) DEFAULT '' NOT NULL,
    status TINYINT DEFAULT 1 NOT NULL,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_semantic_model_agent FOREIGN KEY (agent_id) REFERENCES agent(id) ON DELETE CASCADE
);
CREATE INDEX idx_sm_agent_id ON semantic_model(agent_id);
CREATE INDEX idx_sm_field_name ON semantic_model(business_name);
CREATE INDEX idx_sm_status ON semantic_model(status);

COMMENT ON TABLE semantic_model IS '语义模型表';
COMMENT ON COLUMN semantic_model.agent_id IS '关联的智能体ID';
COMMENT ON COLUMN semantic_model.datasource_id IS '关联的数据源ID';
COMMENT ON COLUMN semantic_model.table_name IS '关联的表名';
COMMENT ON COLUMN semantic_model.column_name IS '数据库中的物理字段名 (例如: csat_score)';
COMMENT ON COLUMN semantic_model.business_name IS '业务名/别名 (例如: 客户满意度分数)';
COMMENT ON COLUMN semantic_model.synonyms IS '业务名的同义词 (例如: 满意度,客户评分)';
COMMENT ON COLUMN semantic_model.business_description IS '业务描述 (用于向LLM解释字段的业务含义)';
COMMENT ON COLUMN semantic_model.column_comment IS '数据库中的物理字段的原始注释 ';
COMMENT ON COLUMN semantic_model.data_type IS '物理数据类型 (例如: int, varchar(20))';
COMMENT ON COLUMN semantic_model.status IS '0 停用 1 启用';
COMMENT ON COLUMN semantic_model.created_time IS '创建时间';
COMMENT ON COLUMN semantic_model.updated_time IS '更新时间';

-- 4. 智能体知识表
CREATE TABLE agent_knowledge (
    id INT NOT NULL IDENTITY(1,1),
    agent_id INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    question CLOB,
    content CLOB,
    is_recall INT DEFAULT 1,
    embedding_status VARCHAR(20) DEFAULT NULL,
    error_msg VARCHAR(255) DEFAULT NULL,
    source_filename VARCHAR(500) DEFAULT NULL,
    file_path VARCHAR(500) DEFAULT NULL,
    file_size BIGINT DEFAULT NULL,
    file_type VARCHAR(255) DEFAULT NULL,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    is_deleted INT DEFAULT 0,
    is_resource_cleaned INT DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_ak_agent_id_status ON agent_knowledge(agent_id, is_recall);
CREATE INDEX idx_ak_embedding_status ON agent_knowledge(embedding_status);
CREATE INDEX idx_ak_is_deleted ON agent_knowledge(is_deleted);

COMMENT ON TABLE agent_knowledge IS '智能体知识源管理表 (支持文档、QA、FAQ)';
COMMENT ON COLUMN agent_knowledge.id IS '主键ID, 用于内部关联';
COMMENT ON COLUMN agent_knowledge.agent_id IS '关联的智能体ID';
COMMENT ON COLUMN agent_knowledge.title IS '知识的标题 (用户定义, 用于在UI上展示 and 识别)';
COMMENT ON COLUMN agent_knowledge.type IS '知识类型: DOCUMENT-文档, QA-问答, FAQ-常见问题';
COMMENT ON COLUMN agent_knowledge.question IS '问题 (仅当type为QA或FAQ时使用)';
COMMENT ON COLUMN agent_knowledge.content IS '知识内容 (对于QA/FAQ是答案; 对于DOCUMENT, 此字段通常为空)';
COMMENT ON COLUMN agent_knowledge.is_recall IS '业务状态: 1=召回, 0=非召回';
COMMENT ON COLUMN agent_knowledge.embedding_status IS '向量化状态：PENDING待处理，PROCESSING处理中，COMPLETED已完成，FAILED失败';
COMMENT ON COLUMN agent_knowledge.error_msg IS '操作失败的错误信息';
COMMENT ON COLUMN agent_knowledge.source_filename IS '上传时的原始文件名';
COMMENT ON COLUMN agent_knowledge.file_path IS '文件在服务器上的物理存储路径';
COMMENT ON COLUMN agent_knowledge.file_size IS '文件大小 (字节)';
COMMENT ON COLUMN agent_knowledge.file_type IS '文件类型（pdf,md,markdown,doc等）';
COMMENT ON COLUMN agent_knowledge.created_time IS '创建时间';
COMMENT ON COLUMN agent_knowledge.updated_time IS '更新时间';
COMMENT ON COLUMN agent_knowledge.is_deleted IS '逻辑删除字段，0=未删除, 1=已删除';
COMMENT ON COLUMN agent_knowledge.is_resource_cleaned IS '0=物理资源（文件和向量）未清理, 1=物理资源已清理';

-- 5. 数据源表
CREATE TABLE datasource (
    id INT NOT NULL IDENTITY(1,1),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    database_name VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    connection_url VARCHAR(1000),
    status VARCHAR(50) DEFAULT 'inactive',
    test_status VARCHAR(50) DEFAULT 'unknown',
    description CLOB,
    creator_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_ds_name ON datasource(name);
CREATE INDEX idx_ds_type ON datasource(type);
CREATE INDEX idx_ds_status ON datasource(status);
CREATE INDEX idx_ds_creator_id ON datasource(creator_id);

COMMENT ON TABLE datasource IS '数据源表';
COMMENT ON COLUMN datasource.name IS '数据源名称';
COMMENT ON COLUMN datasource.type IS '数据源类型：mysql, postgresql';
COMMENT ON COLUMN datasource.host IS '主机地址';
COMMENT ON COLUMN datasource.port IS '端口号';
COMMENT ON COLUMN datasource.database_name IS '数据库名称';
COMMENT ON COLUMN datasource.username IS '用户名';
COMMENT ON COLUMN datasource.password IS '密码（加密存储）';
COMMENT ON COLUMN datasource.connection_url IS '完整连接URL';
COMMENT ON COLUMN datasource.status IS '状态：active-启用，inactive-禁用';
COMMENT ON COLUMN datasource.test_status IS '连接测试状态：success-成功，failed-失败，unknown-未知';
COMMENT ON COLUMN datasource.description IS '描述';
COMMENT ON COLUMN datasource.creator_id IS '创建者ID';
COMMENT ON COLUMN datasource.create_time IS '创建时间';
COMMENT ON COLUMN datasource.update_time IS '更新时间';

-- 6. 逻辑外键配置表
CREATE TABLE logical_relation (
    id INT NOT NULL IDENTITY(1,1),
    datasource_id INT NOT NULL,
    source_table_name VARCHAR(100) NOT NULL,
    source_column_name VARCHAR(100) NOT NULL,
    target_table_name VARCHAR(100) NOT NULL,
    target_column_name VARCHAR(100) NOT NULL,
    relation_type VARCHAR(20) DEFAULT NULL,
    description VARCHAR(500) DEFAULT NULL,
    is_deleted TINYINT DEFAULT 0,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_logical_relation_datasource FOREIGN KEY (datasource_id) REFERENCES datasource(id) ON DELETE CASCADE
);
CREATE INDEX idx_lr_datasource_id ON logical_relation(datasource_id);
CREATE INDEX idx_lr_source_table ON logical_relation(datasource_id, source_table_name);

COMMENT ON TABLE logical_relation IS '逻辑外键配置表';
COMMENT ON COLUMN logical_relation.id IS '主键ID';
COMMENT ON COLUMN logical_relation.datasource_id IS '关联的数据源ID';
COMMENT ON COLUMN logical_relation.source_table_name IS '主表名 (例如 t_order)';
COMMENT ON COLUMN logical_relation.source_column_name IS '主表字段名 (例如 buyer_uid)';
COMMENT ON COLUMN logical_relation.target_table_name IS '关联表名 (例如 t_user)';
COMMENT ON COLUMN logical_relation.target_column_name IS '关联表字段名 (例如 id)';
COMMENT ON COLUMN logical_relation.relation_type IS '关系类型: 1:1, 1:N, N:1 (辅助LLM理解数据基数，可选)';
COMMENT ON COLUMN logical_relation.description IS '业务描述: 存入Prompt中帮助LLM理解 (例如: 订单表通过buyer_uid关联用户表id)';
COMMENT ON COLUMN logical_relation.is_deleted IS '逻辑删除: 0-未删除, 1-已删除';
COMMENT ON COLUMN logical_relation.created_time IS '创建时间';
COMMENT ON COLUMN logical_relation.updated_time IS '更新时间';

-- 7. 智能体数据源关联表
CREATE TABLE agent_datasource (
    id INT NOT NULL IDENTITY(1,1),
    agent_id INT NOT NULL,
    datasource_id INT NOT NULL,
    is_active TINYINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_agent_datasource UNIQUE (agent_id, datasource_id),
    CONSTRAINT fk_agent_datasource_agent FOREIGN KEY (agent_id) REFERENCES agent(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_datasource_datasource FOREIGN KEY (datasource_id) REFERENCES datasource(id) ON DELETE CASCADE
);
CREATE INDEX idx_ad_agent_id ON agent_datasource(agent_id);
CREATE INDEX idx_ad_datasource_id ON agent_datasource(datasource_id);
CREATE INDEX idx_ad_is_active ON agent_datasource(is_active);

COMMENT ON TABLE agent_datasource IS '智能体数据源关联表';
COMMENT ON COLUMN agent_datasource.agent_id IS '智能体ID';
COMMENT ON COLUMN agent_datasource.datasource_id IS '数据源ID';
COMMENT ON COLUMN agent_datasource.is_active IS '是否启用：0-禁用，1-启用';
COMMENT ON COLUMN agent_datasource.create_time IS '创建时间';
COMMENT ON COLUMN agent_datasource.update_time IS '更新时间';

-- 8. 智能体预设问题表
CREATE TABLE agent_preset_question (
    id INT NOT NULL IDENTITY(1,1),
    agent_id INT NOT NULL,
    question CLOB NOT NULL,
    sort_order INT DEFAULT 0,
    is_active TINYINT DEFAULT 0,
    is_delete TINYINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_agent_preset_question_agent FOREIGN KEY (agent_id) REFERENCES agent(id) ON DELETE CASCADE
);
CREATE INDEX idx_apq_agent_id ON agent_preset_question(agent_id);
CREATE INDEX idx_apq_sort_order ON agent_preset_question(sort_order);
CREATE INDEX idx_apq_is_active ON agent_preset_question(is_active);
CREATE INDEX idx_apq_is_delete ON agent_preset_question(is_delete);

COMMENT ON TABLE agent_preset_question IS '智能体预设问题表';
COMMENT ON COLUMN agent_preset_question.agent_id IS '智能体ID';
COMMENT ON COLUMN agent_preset_question.question IS '预设问题内容';
COMMENT ON COLUMN agent_preset_question.sort_order IS '排序顺序';
COMMENT ON COLUMN agent_preset_question.is_active IS '是否启用：0-禁用，1-启用';
COMMENT ON COLUMN agent_preset_question.is_delete IS '是否删除：0-未删除，1-已删除';
COMMENT ON COLUMN agent_preset_question.create_time IS '创建时间';
COMMENT ON COLUMN agent_preset_question.update_time IS '更新时间';

-- 9. 会话表
CREATE TABLE chat_session (
    id VARCHAR(36) NOT NULL,
    agent_id INT NOT NULL,
    title VARCHAR(255) DEFAULT '新对话',
    status VARCHAR(50) DEFAULT 'active',
    is_pinned TINYINT DEFAULT 0,
    user_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_chat_session_agent FOREIGN KEY (agent_id) REFERENCES agent(id) ON DELETE CASCADE
);
CREATE INDEX idx_cs_agent_id ON chat_session(agent_id);
CREATE INDEX idx_cs_user_id ON chat_session(user_id);
CREATE INDEX idx_cs_status ON chat_session(status);
CREATE INDEX idx_cs_is_pinned ON chat_session(is_pinned);
CREATE INDEX idx_cs_create_time ON chat_session(create_time);

COMMENT ON TABLE chat_session IS '聊天会话表';
COMMENT ON COLUMN chat_session.id IS '会话ID（UUID）';
COMMENT ON COLUMN chat_session.agent_id IS '智能体ID';
COMMENT ON COLUMN chat_session.title IS '会话标题';
COMMENT ON COLUMN chat_session.status IS '状态：active-活跃，archived-归档，deleted-已删除';
COMMENT ON COLUMN chat_session.is_pinned IS '是否置顶：0-否，1-是';
COMMENT ON COLUMN chat_session.user_id IS '用户ID';
COMMENT ON COLUMN chat_session.create_time IS '创建时间';
COMMENT ON COLUMN chat_session.update_time IS '更新时间';

-- 10. 消息表
CREATE TABLE chat_message (
    id BIGINT NOT NULL IDENTITY(1,1),
    session_id VARCHAR(36) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content CLOB NOT NULL,
    message_type VARCHAR(50) DEFAULT 'text',
    metadata CLOB,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
);
CREATE INDEX idx_cm_session_id ON chat_message(session_id);
CREATE INDEX idx_cm_role ON chat_message(role);
CREATE INDEX idx_cm_message_type ON chat_message(message_type);
CREATE INDEX idx_cm_create_time ON chat_message(create_time);

COMMENT ON TABLE chat_message IS '聊天消息表';
COMMENT ON COLUMN chat_message.session_id IS '会话ID';
COMMENT ON COLUMN chat_message.role IS '角色：user-用户，assistant-助手，system-系统';
COMMENT ON COLUMN chat_message.content IS '消息内容';
COMMENT ON COLUMN chat_message.message_type IS '消息类型：text-文本，sql-SQL查询，result-查询结果，error-错误';
COMMENT ON COLUMN chat_message.metadata IS '元数据（JSON格式，达梦中使用CLOB存储）';
COMMENT ON COLUMN chat_message.create_time IS '创建时间';

-- 11. 用户Prompt配置表
CREATE TABLE user_prompt_config (
    id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    prompt_type VARCHAR(100) NOT NULL,
    agent_id INT,
    system_prompt CLOB NOT NULL,
    enabled TINYINT DEFAULT 1,
    description CLOB,
    priority INT DEFAULT 0,
    display_order INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    creator VARCHAR(255),
    PRIMARY KEY (id)
);
CREATE INDEX idx_upc_prompt_type ON user_prompt_config(prompt_type);
CREATE INDEX idx_upc_agent_id ON user_prompt_config(agent_id);
CREATE INDEX idx_upc_enabled ON user_prompt_config(enabled);
CREATE INDEX idx_upc_create_time ON user_prompt_config(create_time);
CREATE INDEX idx_upc_display_order ON user_prompt_config(display_order);

COMMENT ON TABLE user_prompt_config IS '用户Prompt配置表';
COMMENT ON COLUMN user_prompt_config.id IS '配置ID（UUID）';
COMMENT ON COLUMN user_prompt_config.name IS '配置名称';
COMMENT ON COLUMN user_prompt_config.prompt_type IS 'Prompt类型（如report-generator, planner等）';
COMMENT ON COLUMN user_prompt_config.agent_id IS '关联的智能体ID，为空表示全局配置';
COMMENT ON COLUMN user_prompt_config.system_prompt IS '用户自定义系统Prompt内容';
COMMENT ON COLUMN user_prompt_config.enabled IS '是否启用该配置：0-禁用，1-启用';
COMMENT ON COLUMN user_prompt_config.description IS '配置描述';
COMMENT ON COLUMN user_prompt_config.priority IS '配置优先级，数字越大优先级越高';
COMMENT ON COLUMN user_prompt_config.display_order IS '配置显示顺序，数字越小越靠前';
COMMENT ON COLUMN user_prompt_config.create_time IS '创建时间';
COMMENT ON COLUMN user_prompt_config.update_time IS '更新时间';
COMMENT ON COLUMN user_prompt_config.creator IS '创建者';

-- 12. 某个智能体某个数据源所选中的数据表
CREATE TABLE agent_datasource_tables (
    id INT NOT NULL IDENTITY(1,1),
    agent_datasource_id INT NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_agent_datasource_tables UNIQUE (agent_datasource_id, table_name),
    CONSTRAINT fk_adt_agent_datasource FOREIGN KEY (agent_datasource_id) REFERENCES agent_datasource (id) ON DELETE CASCADE
);

COMMENT ON TABLE agent_datasource_tables IS '某个智能体某个数据源所选中的数据表';
COMMENT ON COLUMN agent_datasource_tables.agent_datasource_id IS '智能体数据源ID';
COMMENT ON COLUMN agent_datasource_tables.table_name IS '数据表名';
COMMENT ON COLUMN agent_datasource_tables.create_time IS '创建时间';
COMMENT ON COLUMN agent_datasource_tables.update_time IS '更新时间';

-- 13. 模型配置表
CREATE TABLE model_config (
    id INT NOT NULL IDENTITY(1,1),
    provider VARCHAR(255) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    api_key VARCHAR(255) NOT NULL,
    model_name VARCHAR(255) NOT NULL,
    temperature DECIMAL(10,2) DEFAULT 0.00,
    is_active TINYINT DEFAULT 0,
    max_tokens INT DEFAULT 2000,
    model_type VARCHAR(20) DEFAULT 'CHAT' NOT NULL,
    completions_path VARCHAR(255) DEFAULT NULL,
    embeddings_path VARCHAR(255) DEFAULT NULL,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT DEFAULT 0,
    PRIMARY KEY (id)
);

COMMENT ON TABLE model_config IS '模型配置表';
COMMENT ON COLUMN model_config.provider IS '厂商标识 (方便前端展示回显，实际调用主要靠 baseUrl)';
COMMENT ON COLUMN model_config.base_url IS '关键配置';
COMMENT ON COLUMN model_config.api_key IS 'API密钥';
COMMENT ON COLUMN model_config.model_name IS '模型名称';
COMMENT ON COLUMN model_config.temperature IS '温度参数';
COMMENT ON COLUMN model_config.is_active IS '是否激活';
COMMENT ON COLUMN model_config.max_tokens IS '输出响应最大令牌数';
COMMENT ON COLUMN model_config.model_type IS '模型类型 (CHAT/EMBEDDING)';
COMMENT ON COLUMN model_config.completions_path IS 'Chat模型专用。附加到 Base URL 的路径。例如OpenAi的/v1/chat/completions';
COMMENT ON COLUMN model_config.embeddings_path IS '嵌入模型专用。附加到 Base URL 的路径。';
COMMENT ON COLUMN model_config.created_time IS '创建时间';
COMMENT ON COLUMN model_config.updated_time IS '更新时间';
COMMENT ON COLUMN model_config.is_deleted IS '0=未删除, 1=已删除';

-- 触发器：自动更新 update_time 字段
CREATE OR REPLACE TRIGGER trg_agent_update_time BEFORE UPDATE ON agent FOR EACH ROW BEGIN :NEW.update_time := SYSDATE; END;
/
CREATE OR REPLACE TRIGGER trg_business_knowledge_update_time BEFORE UPDATE ON business_knowledge FOR EACH ROW BEGIN :NEW.updated_time := SYSDATE; END;
/
CREATE OR REPLACE TRIGGER trg_semantic_model_update_time BEFORE UPDATE ON semantic_model FOR EACH ROW BEGIN :NEW.updated_time := SYSDATE; END;
/
CREATE OR REPLACE TRIGGER trg_agent_knowledge_update_time BEFORE UPDATE ON agent_knowledge FOR EACH ROW BEGIN :NEW.updated_time := SYSDATE; END;
/
CREATE OR REPLACE TRIGGER trg_datasource_update_time BEFORE UPDATE ON datasource FOR EACH ROW BEGIN :NEW.update_time := SYSDATE; END;
/
CREATE OR REPLACE TRIGGER trg_logical_relation_update_time BEFORE UPDATE ON logical_relation FOR EACH ROW BEGIN :NEW.updated_time := SYSDATE; END;
/
CREATE OR REPLACE TRIGGER trg_agent_datasource_update_time BEFORE UPDATE ON agent_datasource FOR EACH ROW BEGIN :NEW.update_time := SYSDATE; END;
/
CREATE OR REPLACE TRIGGER trg_agent_preset_question_update_time BEFORE UPDATE ON agent_preset_question FOR EACH ROW BEGIN :NEW.update_time := SYSDATE; END;
/
CREATE OR REPLACE TRIGGER trg_chat_session_update_time BEFORE UPDATE ON chat_session FOR EACH ROW BEGIN :NEW.update_time := SYSDATE; END;
/
CREATE OR REPLACE TRIGGER trg_user_prompt_config_update_time BEFORE UPDATE ON user_prompt_config FOR EACH ROW BEGIN :NEW.update_time := SYSDATE; END;
/
CREATE OR REPLACE TRIGGER trg_agent_datasource_tables_update_time BEFORE UPDATE ON agent_datasource_tables FOR EACH ROW BEGIN :NEW.update_time := SYSDATE; END;
/
CREATE OR REPLACE TRIGGER trg_model_config_update_time BEFORE UPDATE ON model_config FOR EACH ROW BEGIN :NEW.updated_time := SYSDATE; END;
/

EXIT;

