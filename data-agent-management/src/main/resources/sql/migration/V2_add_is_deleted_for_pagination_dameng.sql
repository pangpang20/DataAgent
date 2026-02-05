-- Migration script: Add is_deleted column to support pagination with logical delete
-- Date: 2025-02-05
-- Database: DaMeng (达梦数据库)
-- Affects: agent, semantic_model, datasource tables

-- ================================================
-- 1. agent table
-- ================================================
ALTER TABLE "agent" ADD "is_deleted" INT DEFAULT 0;

COMMENT ON COLUMN "agent"."is_deleted" IS '逻辑删除：0-未删除，1-已删除';

CREATE INDEX "idx_agent_is_deleted" ON "agent"("is_deleted");

UPDATE "agent" SET "is_deleted" = 0 WHERE "is_deleted" IS NULL;

-- ================================================
-- 2. semantic_model table
-- ================================================
ALTER TABLE "semantic_model" ADD "is_deleted" INT DEFAULT 0;

COMMENT ON COLUMN "semantic_model"."is_deleted" IS '逻辑删除：0-未删除，1-已删除';

CREATE INDEX "idx_sm_is_deleted" ON "semantic_model"("is_deleted");

UPDATE "semantic_model" SET "is_deleted" = 0 WHERE "is_deleted" IS NULL;

-- ================================================
-- 3. datasource table
-- ================================================
ALTER TABLE "datasource" ADD "is_deleted" INT DEFAULT 0;

COMMENT ON COLUMN "datasource"."is_deleted" IS '逻辑删除：0-未删除，1-已删除';

CREATE INDEX "idx_ds_is_deleted" ON "datasource"("is_deleted");

UPDATE "datasource" SET "is_deleted" = 0 WHERE "is_deleted" IS NULL;

-- ================================================
-- 4. Composite indexes for pagination performance (optional)
-- ================================================
CREATE INDEX "idx_bk_agent_deleted_time" ON "business_knowledge"("agent_id", "is_deleted", "created_time");
CREATE INDEX "idx_sm_agent_deleted_time" ON "semantic_model"("agent_id", "is_deleted", "created_time");
CREATE INDEX "idx_agent_deleted_update" ON "agent"("is_deleted", "update_time");

-- Commit changes
COMMIT;
