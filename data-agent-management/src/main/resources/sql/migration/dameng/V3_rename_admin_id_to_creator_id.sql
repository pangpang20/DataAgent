-- 重命名 agent 表的 admin_id 为 creator_id（达梦版本）

ALTER TABLE agent RENAME COLUMN admin_id TO creator_id;
DROP INDEX IF EXISTS idx_agent_admin_id;
CREATE INDEX idx_agent_creator_id ON agent(creator_id);

COMMENT ON COLUMN agent.creator_id IS '创建者ID';

EXIT;
