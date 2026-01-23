-- H2 compatible schema for ModelConfigMapperTest
DROP TABLE IF EXISTS model_config;

CREATE TABLE model_config (
  id int NOT NULL AUTO_INCREMENT,
  provider varchar(255) NOT NULL,
  base_url varchar(255) NOT NULL,
  api_key varchar(255) NOT NULL,
  model_name varchar(255) NOT NULL,
  temperature decimal(10,2) DEFAULT '0.00',
  is_active tinyint DEFAULT '0',
  max_tokens int DEFAULT '2000',
  model_type varchar(20) NOT NULL DEFAULT 'CHAT',
  completions_path varchar(255) DEFAULT NULL,
  embeddings_path varchar(255) DEFAULT NULL,
  created_time datetime DEFAULT NULL,
  updated_time datetime DEFAULT NULL,
  is_deleted int DEFAULT '0',
  PRIMARY KEY (id)
);
