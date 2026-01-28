SET DEFINE OFF;

-- 插入用户数据
SET IDENTITY_INSERT users ON;
INSERT INTO users (id, username, email) VALUES (1, 'alice', 'alice@example.com');
INSERT INTO users (id, username, email) VALUES (2, 'bob', 'bob@example.com');
INSERT INTO users (id, username, email) VALUES (3, 'cathy', 'cathy@example.com');
INSERT INTO users (id, username, email) VALUES (4, 'daniel', 'daniel@example.com');
INSERT INTO users (id, username, email) VALUES (5, 'emily', 'emily@example.com');
SET IDENTITY_INSERT users OFF;
COMMIT;

-- 插入商品分类数据
SET IDENTITY_INSERT categories ON;
INSERT INTO categories (id, name) VALUES (1, '电子产品');
INSERT INTO categories (id, name) VALUES (2, '服装');
INSERT INTO categories (id, name) VALUES (3, '图书');
INSERT INTO categories (id, name) VALUES (4, '家居用品');
INSERT INTO categories (id, name) VALUES (5, '食品');
SET IDENTITY_INSERT categories OFF;
COMMIT;

-- 插入商品数据
SET IDENTITY_INSERT products ON;
INSERT INTO products (id, name, price, stock) VALUES (1, '智能手机', 2999.00, 100);
INSERT INTO products (id, name, price, stock) VALUES (2, 'T恤衫', 89.00, 500);
INSERT INTO products (id, name, price, stock) VALUES (3, '小说', 39.00, 200);
INSERT INTO products (id, name, price, stock) VALUES (4, '咖啡机', 599.00, 50);
INSERT INTO products (id, name, price, stock) VALUES (5, '牛奶', 15.00, 300);
INSERT INTO products (id, name, price, stock) VALUES (6, '笔记本电脑', 4999.00, 30);
INSERT INTO products (id, name, price, stock) VALUES (7, '沙发', 2599.00, 10);
INSERT INTO products (id, name, price, stock) VALUES (8, '巧克力', 25.00, 100);
INSERT INTO products (id, name, price, stock) VALUES (9, '羽绒服', 399.00, 80);
INSERT INTO products (id, name, price, stock) VALUES (10, '历史书', 69.00, 150);
SET IDENTITY_INSERT products OFF;
COMMIT;

-- 插入商品-分类关联数据
INSERT INTO product_categories (product_id, category_id) VALUES (1, 1);
INSERT INTO product_categories (product_id, category_id) VALUES (2, 2);
INSERT INTO product_categories (product_id, category_id) VALUES (3, 3);
INSERT INTO product_categories (product_id, category_id) VALUES (4, 1);
INSERT INTO product_categories (product_id, category_id) VALUES (4, 4);
INSERT INTO product_categories (product_id, category_id) VALUES (5, 5);
INSERT INTO product_categories (product_id, category_id) VALUES (6, 1);
INSERT INTO product_categories (product_id, category_id) VALUES (7, 4);
INSERT INTO product_categories (product_id, category_id) VALUES (8, 5);
INSERT INTO product_categories (product_id, category_id) VALUES (9, 2);
INSERT INTO product_categories (product_id, category_id) VALUES (10, 3);
COMMIT;

-- 插入订单数据
SET IDENTITY_INSERT orders ON;
INSERT INTO orders (id, user_id, total_amount, status, order_date) VALUES (1, 1, 3088.00, 'completed', '2025-06-01 10:10:00');
INSERT INTO orders (id, user_id, total_amount, status, order_date) VALUES (2, 2, 39.00, 'pending', '2025-06-02 09:23:00');
INSERT INTO orders (id, user_id, total_amount, status, order_date) VALUES (3, 3, 1204.00, 'completed', '2025-06-03 13:45:00');
INSERT INTO orders (id, user_id, total_amount, status, order_date) VALUES (4, 4, 65.00, 'cancelled', '2025-06-04 16:05:00');
INSERT INTO orders (id, user_id, total_amount, status, order_date) VALUES (5, 5, 5113.00, 'completed', '2025-06-05 20:12:00');
INSERT INTO orders (id, user_id, total_amount, status, order_date) VALUES (6, 1, 814.00, 'completed', '2025-06-05 21:03:00');
INSERT INTO orders (id, user_id, total_amount, status, order_date) VALUES (7, 2, 424.00, 'pending', '2025-06-06 08:10:00');
INSERT INTO orders (id, user_id, total_amount, status, order_date) VALUES (8, 3, 524.00, 'completed', '2025-06-06 14:48:00');
INSERT INTO orders (id, user_id, total_amount, status, order_date) VALUES (9, 4, 399.00, 'completed', '2025-06-07 10:15:00');
INSERT INTO orders (id, user_id, total_amount, status, order_date) VALUES (10, 5, 129.00, 'pending', '2025-06-07 18:00:00');
SET IDENTITY_INSERT orders OFF;
COMMIT;

-- 插入订单明细数据
SET IDENTITY_INSERT order_items ON;
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (1, 1, 1, 1, 2999.00);
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (2, 1, 2, 1, 89.00);
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (3, 2, 3, 1, 39.00);
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (4, 3, 4, 2, 599.00);
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (5, 3, 5, 2, 3.00);
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (6, 4, 8, 2, 25.00);
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (7, 4, 5, 1, 15.00);
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (8, 5, 6, 1, 4999.00);
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (9, 5, 2, 1, 89.00);
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (10, 5, 5, 5, 5.00);
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (11, 5, 8, 1, 25.00);
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (12, 6, 9, 2, 399.00);
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (13, 6, 3, 1, 16.00);
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (14, 7, 2, 2, 89.00);
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (15, 7, 3, 3, 39.00);
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (16, 8, 10, 4, 69.00);
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (17, 9, 9, 1, 399.00);
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (18, 10, 8, 4, 25.00);
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (19, 10, 5, 1, 29.00);
SET IDENTITY_INSERT order_items OFF;
COMMIT;

EXIT;
