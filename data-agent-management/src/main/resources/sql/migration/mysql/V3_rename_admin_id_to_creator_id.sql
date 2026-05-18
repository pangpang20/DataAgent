-- 重命名 agent 表的 admin_id 为 creator_id（MySQL 版本）

-- 检查并重命名列
ALTER TABLE agent CHANGE COLUMN admin_id creator_id BIGINT COMMENT '创建者ID';
ALTER TABLE agent DROP INDEX idx_admin_id;
CREATE INDEX idx_creator_id ON agent(creator_id);
