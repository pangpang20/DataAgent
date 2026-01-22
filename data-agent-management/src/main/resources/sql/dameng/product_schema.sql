-- 用户表
CREATE TABLE users (
                       id INT IDENTITY(1, 1) PRIMARY KEY,
                       username VARCHAR(50) NOT NULL,
                       email VARCHAR(100) NOT NULL,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE users IS '用户表';
COMMENT ON COLUMN users.id IS '用户ID，主键自增';
COMMENT ON COLUMN users.username IS '用户名';
COMMENT ON COLUMN users.email IS '用户邮箱';
COMMENT ON COLUMN users.created_at IS '用户注册时间';

-- 商品表
CREATE TABLE products (
                          id INT IDENTITY(1, 1) PRIMARY KEY,
                          name VARCHAR(100) NOT NULL,
                          price DECIMAL(10,2) NOT NULL,
                          stock INT NOT NULL,
                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE products IS '商品表';
COMMENT ON COLUMN products.id IS '商品ID，主键自增';
COMMENT ON COLUMN products.name IS '商品名称';
COMMENT ON COLUMN products.price IS '商品单价';
COMMENT ON COLUMN products.stock IS '商品库存数量';
COMMENT ON COLUMN products.created_at IS '商品上架时间';

-- 订单表
CREATE TABLE orders (
                        id INT IDENTITY(1, 1) PRIMARY KEY,
                        user_id INT NOT NULL,
                        order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        total_amount DECIMAL(10,2) NOT NULL,
                        status VARCHAR(20) DEFAULT 'pending'
);
COMMENT ON TABLE orders IS '订单表';
COMMENT ON COLUMN orders.id IS '订单ID，主键自增';
COMMENT ON COLUMN orders.user_id IS '下单用户ID';
COMMENT ON COLUMN orders.order_date IS '下单时间';
COMMENT ON COLUMN orders.total_amount IS '订单总金额';
COMMENT ON COLUMN orders.status IS '订单状态（pending/completed/cancelled等）';

ALTER TABLE orders ADD CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id);

-- 订单明细表
CREATE TABLE order_items (
                             id INT IDENTITY(1, 1) PRIMARY KEY,
                             order_id INT NOT NULL,
                             product_id INT NOT NULL,
                             quantity INT NOT NULL,
                             unit_price DECIMAL(10,2) NOT NULL
);
COMMENT ON TABLE order_items IS '订单明细表';
COMMENT ON COLUMN order_items.id IS '订单明细ID，主键自增';
COMMENT ON COLUMN order_items.order_id IS '订单ID';
COMMENT ON COLUMN order_items.product_id IS '商品ID';
COMMENT ON COLUMN order_items.quantity IS '购买数量';
COMMENT ON COLUMN order_items.unit_price IS '下单时商品单价';

ALTER TABLE order_items ADD CONSTRAINT fk_items_order FOREIGN KEY (order_id) REFERENCES orders(id);
ALTER TABLE order_items ADD CONSTRAINT fk_items_product FOREIGN KEY (product_id) REFERENCES products(id);

-- 商品分类表
CREATE TABLE categories (
                            id INT IDENTITY(1, 1) PRIMARY KEY,
                            name VARCHAR(50) NOT NULL
);
COMMENT ON TABLE categories IS '商品分类表';
COMMENT ON COLUMN categories.id IS '分类ID，主键自增';
COMMENT ON COLUMN categories.name IS '分类名称';

-- 商品-分类关联表（多对多）
CREATE TABLE product_categories (
                                    product_id INT NOT NULL,
                                    category_id INT NOT NULL,
                                    PRIMARY KEY (product_id, category_id)
);
COMMENT ON TABLE product_categories IS '商品与分类关联表';
COMMENT ON COLUMN product_categories.product_id IS '商品ID';
COMMENT ON COLUMN product_categories.category_id IS '分类ID';

ALTER TABLE product_categories ADD CONSTRAINT fk_pc_product FOREIGN KEY (product_id) REFERENCES products(id);
ALTER TABLE product_categories ADD CONSTRAINT fk_pc_category FOREIGN KEY (category_id) REFERENCES categories(id);

EXIT;
