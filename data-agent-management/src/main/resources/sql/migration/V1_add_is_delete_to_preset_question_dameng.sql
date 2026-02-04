-- Migration script: Add is_delete column to agent_preset_question table
-- Date: 2026-02-04
-- Database: DaMeng (达梦数据库)

-- Add is_delete column
ALTER TABLE agent_preset_question 
ADD is_delete TINYINT DEFAULT 0;

-- Add comment
COMMENT ON COLUMN agent_preset_question.is_delete IS '是否删除：0-未删除，1-已删除';

-- Add index for is_delete
CREATE INDEX idx_apq_is_delete ON agent_preset_question(is_delete);

-- Update existing records to set is_delete = 0
UPDATE agent_preset_question SET is_delete = 0 WHERE is_delete IS NULL;

-- Commit changes
COMMIT;
