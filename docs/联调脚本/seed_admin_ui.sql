-- 联调种子数据（仅写入隔离临时库 3307，不触碰用户真实库）
USE campus_secondhand;

-- 幂等重置（仅联调临时库）
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE `report`;
TRUNCATE TABLE `credit_log`;
TRUNCATE TABLE `goods_image`;
TRUNCATE TABLE `goods`;
TRUNCATE TABLE `want_post`;
TRUNCATE TABLE `swap_post`;
TRUNCATE TABLE `banner`;
TRUNCATE TABLE `notice`;
TRUNCATE TABLE `user`;
SET FOREIGN_KEY_CHECKS = 1;

SET @img1 = '/upload/202610/114b924b52fe45be9047b4d149da48dc.png';
SET @img2 = '/upload/202610/2a0204631fb84423b324734c2e14ffec.png';

-- 用户（密码占位，哈希无关：后台不校验用户密码）
INSERT INTO `user` (username, password, nickname, avatar, college, bio, credit_score, auth_status, status, ban_reason, banned_until, last_login_at, created_at) VALUES
('stu_alice',  '$2a$10$abcdefghijklmnopqrstuv', '爱丽丝', NULL, '计算机学院', '喜欢二手书', 132, 1, 0, NULL, NULL, NOW(), DATE_SUB(NOW(), INTERVAL 30 DAY)),
('stu_bob',    '$2a$10$abcdefghijklmnopqrstuv', '鲍勃',   NULL, '外国语学院', '常年收数码', 88,  1, 0, NULL, NULL, NOW(), DATE_SUB(NOW(), INTERVAL 20 DAY)),
('stu_carol',  '$2a$10$abcdefghijklmnopqrstuv', '卡罗尔', NULL, '体育学院',   '羽毛球爱好者', 45, 0, 1, '多次发布违规内容', DATE_ADD(NOW(), INTERVAL 7 DAY), NOW(), DATE_SUB(NOW(), INTERVAL 10 DAY)),
('stu_dave',   '$2a$10$abcdefghijklmnopqrstuv', '戴夫',   NULL, '数学学院',   NULL, 100, 0, 0, NULL, NULL, NULL, NOW());

-- 内容：GOODS —— 覆盖 ON_SALE / OFF_SALE / IN_TRANSACTION / DELETED
INSERT INTO `goods` (user_id, category_id, title, description, price, condition_level, course_name, isbn, trade_location, status, off_sale_reason, deleted_at, deleted_by_type, deleted_by, delete_reason, created_at) VALUES
(1, 1, '高等数学教材（第七版）', '正版教材，笔记齐全，无破损', 25.00, 3, '高等数学', '9787040396638', '南门菜鸟驿站', 'ON_SALE', NULL, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 5 DAY)),
(2, 2, 'iPad Air 4 64G', '轻微使用痕迹，配原装充电器', 2200.00, 2, NULL, NULL, '图书馆一楼', 'OFF_SALE', '信息需要补充，暂时下架', NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY)),
(3, 3, '机械键盘 87 键', '已与买家约定面交', 199.00, 3, NULL, NULL, '宿舍楼下', 'IN_TRANSACTION', NULL, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(4, 4, '违规测试商品', '用于删除门禁测试', 10.00, 4, NULL, NULL, NULL, 'DELETED', NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), 'ADMIN', 1, '含违禁词', DATE_SUB(NOW(), INTERVAL 2 DAY));

INSERT INTO `goods_image` (goods_id, url, thumb_url, sort) VALUES
(1, @img1, @img2, 0),
(2, @img1, @img2, 0);

-- 内容：WANT —— OPEN / CLOSED
INSERT INTO `want_post` (user_id, category_id, title, description, budget, status, created_at) VALUES
(1, 2, '求购二手显示器', '24 寸以上，1080P 即可', 400.00, 'OPEN', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(2, 5, '求购秋季外套', 'L 码，深色', 120.00, 'CLOSED', DATE_SUB(NOW(), INTERVAL 6 DAY));

-- 内容：SWAP —— OPEN / CLOSED
INSERT INTO `swap_post` (user_id, category_id, title, my_item_desc, want_item_desc, allow_diff, diff_amount, status, created_at) VALUES
(1, 1, '用教材换考研资料', '高数教材九成新', '考研数学复习全书', 0, NULL, 'OPEN', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(3, 3, '键帽换鼠标', 'PBT 键帽一套', '无线鼠标', 1, 30.00, 'CLOSED', DATE_SUB(NOW(), INTERVAL 8 DAY));

-- 举报工单：待处理(GOODS，JSON 数组证据图) / 待处理(USER) / 已处置 / 已驳回
INSERT INTO `report` (reporter_id, target_type, target_id, report_type, description, images, status, result, handler_id, handled_at, created_at) VALUES
(2, 'GOODS', 1, 'COUNTERFEIT', '疑似盗版教材，印刷模糊', CAST('["/upload/202610/114b924b52fe45be9047b4d149da48dc.png","/upload/202610/2a0204631fb84423b324734c2e14ffec.png"]' AS JSON), 0, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 6 HOUR)),
(1, 'USER', 2, 'FRAUD', '收款后不发货', CAST('["/upload/202610/5203808f7fe04c158d2d49e884f1d297.png"]' AS JSON), 0, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(4, 'WANT', 1, 'OTHER', '描述含联系方式', CAST('[]' AS JSON), 0, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
(2, 'SWAP', 2, 'VIOLATION', '交换物品描述违规', NULL, 1, '已下架并警告发布者', 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(3, 'GOODS', 2, 'OTHER', '重复发布', NULL, 2, '证据不足，驳回', 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY));

-- 信用流水（用户 1，12 条，用于分页验证）
INSERT INTO `credit_log` (user_id, score_change, before_score, after_score, reason, ref_type, ref_id, remark, operator_id, created_at) VALUES
(1,  2, 100, 102, 'ORDER_COMPLETE', 'ORDER', 1, NULL, NULL, DATE_SUB(NOW(), INTERVAL 11 DAY)),
(1,  2, 102, 104, 'ORDER_COMPLETE', 'ORDER', 2, NULL, NULL, DATE_SUB(NOW(), INTERVAL 10 DAY)),
(1, -5, 104,  99, 'MALICIOUS_REPORT', 'REPORT', 3, NULL, NULL, DATE_SUB(NOW(), INTERVAL 9 DAY)),
(1,  2,  99, 101, 'ORDER_COMPLETE', 'ORDER', 4, NULL, NULL, DATE_SUB(NOW(), INTERVAL 8 DAY)),
(1, -2, 101,  99, 'CANCEL_TIMEOUT', 'ORDER', 5, NULL, NULL, DATE_SUB(NOW(), INTERVAL 7 DAY)),
(1,  2,  99, 101, 'ORDER_COMPLETE', 'ORDER', 6, NULL, NULL, DATE_SUB(NOW(), INTERVAL 6 DAY)),
(1,  2, 101, 103, 'ORDER_COMPLETE', 'ORDER', 7, NULL, NULL, DATE_SUB(NOW(), INTERVAL 5 DAY)),
(1,  2, 103, 105, 'ORDER_COMPLETE', 'ORDER', 8, NULL, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY)),
(1, -5, 105, 100, 'MALICIOUS_REPORT', 'REPORT', 4, NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(1,  2, 100, 102, 'ORDER_COMPLETE', 'ORDER', 9, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(1,  2, 102, 104, 'ORDER_COMPLETE', 'ORDER', 10, NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, 28, 104, 132, 'ADMIN_ADJUST', NULL, NULL, '活动奖励补分', 1, NOW());

-- 运营：轮播图 1 条、公告 2 条（草稿 + 已发布）
INSERT INTO `banner` (title, image_url, link_url, sort, status) VALUES
('开学季教材专区', '/upload/202610/114b924b52fe45be9047b4d149da48dc.png', '/goods?category=1', 1, 1);

INSERT INTO `notice` (title, content, status, publisher_id, published_at) VALUES
('平台使用规范（草稿）', '正在拟定中的规范草案。', 0, 1, NULL),
('校园二手交易平台正式上线', '欢迎使用校园二手交易平台，交易请注意面交安全。', 1, 1, DATE_SUB(NOW(), INTERVAL 5 DAY));
