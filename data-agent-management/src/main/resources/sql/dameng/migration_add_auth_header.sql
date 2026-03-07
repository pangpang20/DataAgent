-- 为已存在的 model_config 表添加 auth_header_name 字段
-- 执行此脚本前请先备份数据库

-- 达梦数据库
ALTER TABLE model_config 
ADD COLUMN auth_header_name VARCHAR(100) DEFAULT NULL;

COMMENT ON COLUMN model_config.auth_header_name IS '自定义认证头名称。例如：szc-api-key。为空则使用标准的Authorization: Bearer';
