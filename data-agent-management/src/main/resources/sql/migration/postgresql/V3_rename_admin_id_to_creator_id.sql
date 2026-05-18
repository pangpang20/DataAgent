-- 重命名 agent 表的 admin_id 为 creator_id（PostgreSQL 版本）

ALTER TABLE agent RENAME COLUMN admin_id TO creator_id;
DROP INDEX IF EXISTS idx_admin_id;
CREATE INDEX IF NOT EXISTS idx_creator_id ON agent(creator_id);
