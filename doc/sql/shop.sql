-- 创建数据库
CREATE DATABASE IF NOT EXISTS shop DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE shop;

-- 用户表（JPA会自动创建，此处为手动初始化数据）
-- 商品表（JPA会自动创建，此处为手动初始化测试数据）
-- 订单表（JPA会自动创建）

-- 插入测试用户
INSERT INTO user (username, password, telephone) VALUES
('张三', '123456', '13800138001'),
('李四', '123456', '13800138002'),
('王五', '123456', '13800138003');

-- 插入测试商品
INSERT INTO product (pname, pprice, stock) VALUES
('小米手机', 1999.00, 100),
('华为笔记本', 5999.00, 50),
('苹果平板', 3999.00, 80);
