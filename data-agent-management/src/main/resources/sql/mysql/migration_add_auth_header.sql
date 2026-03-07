-- 为已存在的 model_config 表添加 auth_header_name 字段
-- 执行此脚本前请先备份数据库

-- MySQL
ALTER TABLE `model_config` 
ADD COLUMN `auth_header_name` varchar(100) DEFAULT NULL COMMENT '自定义认证头名称。例如：szc-api-key。为空则使用标准的Authorization: Bearer' 
AFTER `embeddings_path`;
