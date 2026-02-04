-- Migration script: Add is_delete column to agent_preset_question table
-- Date: 2026-02-04

-- Add is_delete column (remove COMMENT for DamengDB compatibility)
ALTER TABLE agent_preset_question 
ADD COLUMN is_delete TINYINT DEFAULT 0;

-- Add column comment for DamengDB (MySQL will ignore this if not supported)
COMMENT ON COLUMN agent_preset_question.is_delete IS '是否删除：0-未删除，1-已删除';

-- Add index for is_delete
CREATE INDEX idx_is_delete ON agent_preset_question(is_delete);

-- Update existing records to set is_delete = 0
UPDATE agent_preset_question SET is_delete = 0 WHERE is_delete IS NULL;
