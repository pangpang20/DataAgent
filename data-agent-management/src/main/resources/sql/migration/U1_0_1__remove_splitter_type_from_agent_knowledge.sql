-- 回滚 splitter_type 字段的添加操作

-- 删除索引
DROP INDEX idx_agent_knowledge_splitter_type ON agent_knowledge;

-- 删除字段
ALTER TABLE agent_knowledge 
DROP COLUMN splitter_type;