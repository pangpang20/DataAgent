-- 添加 splitter_type 字段到 agent_knowledge 表
-- 用于支持知识分片策略配置

-- MySQL 版本
ALTER TABLE agent_knowledge 
ADD COLUMN splitter_type VARCHAR(50) COLLATE utf8mb4_bin DEFAULT 'DEFAULT' COMMENT '分片策略类型：DEFAULT-默认分片，CUSTOM-自定义分片';

-- Dameng 版本 (如果使用 Dameng 数据库)
-- ALTER TABLE agent_knowledge 
-- ADD splitter_type VARCHAR(50) DEFAULT 'DEFAULT';

-- 为现有数据设置默认值
UPDATE agent_knowledge 
SET splitter_type = 'DEFAULT' 
WHERE splitter_type IS NULL;

-- 添加索引以提高查询性能
CREATE INDEX idx_agent_knowledge_splitter_type ON agent_knowledge(splitter_type);