-- =====================================================================
-- 校园二手交易系统 · 建库建表脚本 v1.1（用户修订版）
-- 依据：数据库设计文档.md v1.1 / 需求规格说明书.md v1.0
-- 环境：MySQL 8.x，字符集 utf8mb4
-- 执行：mysql -u root -p < sql/schema.sql（重置验证请先 DROP DATABASE）
-- 变更（v1.1）：新增 tag/goods_tag/goods_want/order_no_seq（共 27 表）；
--   核心关系 41 条物理外键（T3）；软删审计字段（T4）；派生字段事实表化（T5）；
--   交换订单双方独立确认（T7）；待处理应约生成列唯一约束（T8）；
--   浏览行为日期唯一键（T9）；订单号每日序列表（T11）；强制改密标记（T12）
-- =====================================================================

CREATE DATABASE IF NOT EXISTS campus_secondhand
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE campus_secondhand;

-- ---------------------------------------------------------------------
-- 1. user 用户表
-- ---------------------------------------------------------------------
CREATE TABLE `user` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `username`      VARCHAR(50)     NOT NULL COMMENT '登录用户名',
  `password`      VARCHAR(100)    NOT NULL COMMENT 'BCrypt密文',
  `nickname`      VARCHAR(50)     NOT NULL COMMENT '展示昵称',
  `avatar`        VARCHAR(255)    DEFAULT NULL COMMENT '头像URL,空=默认头像',
  `college`       VARCHAR(100)    DEFAULT NULL COMMENT '学院',
  `bio`           VARCHAR(200)    DEFAULT NULL COMMENT '个人简介(入库前XSS转义)',
  `credit_score`  INT             NOT NULL DEFAULT 100 COMMENT '信用分0~150,<60受限',
  `auth_status`   TINYINT         NOT NULL DEFAULT 0 COMMENT '0未认证 1已认证',
  `status`        TINYINT         NOT NULL DEFAULT 0 COMMENT '0正常 1封禁',
  `ban_reason`    VARCHAR(200)    DEFAULT NULL COMMENT '封禁理由',
  `banned_until`  DATETIME        DEFAULT NULL COMMENT '封禁截止,NULL且status=1为永久',
  `last_login_at` DATETIME        DEFAULT NULL COMMENT '最近登录时间',
  `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB COMMENT='前台用户表';

-- ---------------------------------------------------------------------
-- 2. user_auth 校园认证表（验证码在Redis不落库-T6）
-- ---------------------------------------------------------------------
CREATE TABLE `user_auth` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '用户ID,1:1',
  `student_no`   VARCHAR(30)     NOT NULL COMMENT '学号,全局唯一',
  `campus_email` VARCHAR(100)    NOT NULL COMMENT '校园邮箱,全局唯一',
  `status`       TINYINT         NOT NULL DEFAULT 0 COMMENT '0待验证 1已验证',
  `verified_at`  DATETIME        DEFAULT NULL COMMENT '验证通过时间',
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  UNIQUE KEY `uk_student_no` (`student_no`),
  UNIQUE KEY `uk_campus_email` (`campus_email`),
  CONSTRAINT `fk_user_auth_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='校园认证表';

-- ---------------------------------------------------------------------
-- 3. admin 管理员表（初始凭据环境变量注入+幂等初始化+强制改密-T12）
-- ---------------------------------------------------------------------
CREATE TABLE `admin` (
  `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `username`             VARCHAR(50)     NOT NULL COMMENT '登录名',
  `password`             VARCHAR(100)    NOT NULL COMMENT 'BCrypt密文',
  `real_name`            VARCHAR(50)     DEFAULT NULL COMMENT '姓名',
  `role`                 VARCHAR(20)     NOT NULL COMMENT 'SUPER/AUDITOR/OPERATOR(DB大写,代码小写)',
  `status`               TINYINT         NOT NULL DEFAULT 0 COMMENT '0启用 1禁用',
  `must_change_password` TINYINT         NOT NULL DEFAULT 0 COMMENT '首次登录强制改密标记,T12',
  `last_login_at`        DATETIME        DEFAULT NULL COMMENT '最近登录时间',
  `created_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_role` (`role`)
) ENGINE=InnoDB COMMENT='管理员表';

-- ---------------------------------------------------------------------
-- 4. category 分类表（二级树形,负责导航与筛选-T1修订）
-- ---------------------------------------------------------------------
CREATE TABLE `category` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `parent_id`  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父分类ID,0=一级',
  `name`       VARCHAR(50)     NOT NULL COMMENT '分类名,同一父级下不重名(应用层保证)',
  `icon`       VARCHAR(255)    DEFAULT NULL COMMENT '图标URL或前端图标名',
  `sort`       INT             NOT NULL DEFAULT 0 COMMENT '排序,小者在前',
  `status`     TINYINT         NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB COMMENT='商品分类表';

-- ---------------------------------------------------------------------
-- 5. tag 标签表【v1.1新增】（负责推荐的多维匹配-T1修订）
-- ---------------------------------------------------------------------
CREATE TABLE `tag` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`       VARCHAR(30)     NOT NULL COMMENT '标签名,全局唯一',
  `sort`       INT             NOT NULL DEFAULT 0 COMMENT '排序',
  `status`     TINYINT         NOT NULL DEFAULT 0 COMMENT '0启用 1停用',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB COMMENT='商品标签表';

-- ---------------------------------------------------------------------
-- 6. goods 商品表
-- ---------------------------------------------------------------------
CREATE TABLE `goods` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '卖家ID',
  `category_id`     BIGINT UNSIGNED NOT NULL COMMENT '二级分类ID',
  `title`           VARCHAR(50)     NOT NULL COMMENT '标题',
  `description`     VARCHAR(500)    NOT NULL COMMENT '描述',
  `price`           DECIMAL(10,2)   NOT NULL DEFAULT 0.00 COMMENT '面交价,0=免费赠送',
  `condition_level` TINYINT         NOT NULL DEFAULT 1 COMMENT '成色1全新/2几乎全新/3轻微使用/4明显使用',
  `course_name`     VARCHAR(100)    DEFAULT NULL COMMENT '教材课程名(分类=教材书籍时选填)',
  `isbn`            VARCHAR(20)     DEFAULT NULL COMMENT 'ISBN(同上)',
  `trade_location`  VARCHAR(100)    DEFAULT NULL COMMENT '常约交易地点',
  `status`          VARCHAR(20)     NOT NULL DEFAULT 'ON_SALE' COMMENT 'ON_SALE/IN_TRANSACTION/SOLD/OFF_SALE/DELETED',
  `off_sale_reason` VARCHAR(200)    DEFAULT NULL COMMENT '下架理由(管理员下架必填)',
  `deleted_at`      DATETIME        DEFAULT NULL COMMENT '软删时间-T4',
  `deleted_by_type` VARCHAR(10)     DEFAULT NULL COMMENT '删除操作者类型USER/ADMIN-T4',
  `deleted_by`      BIGINT UNSIGNED DEFAULT NULL COMMENT '删除操作者ID(逻辑外键)-T4',
  `delete_reason`   VARCHAR(200)    DEFAULT NULL COMMENT '删除原因,管理员必填-T4',
  `view_count`      INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '浏览量(派生,事实=user_behavior-T5)',
  `want_count`      INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '想要数(派生,事实=goods_want-T5)',
  `favorite_count`  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '收藏数(派生,事实=favorite-T5)',
  `heat_score`      INT             NOT NULL DEFAULT 0 COMMENT '热度分=浏览*1+收藏*3+想要*5,定时重算',
  `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_category_status` (`category_id`,`status`),
  KEY `idx_status_created` (`status`,`created_at`),
  FULLTEXT KEY `ft_title_desc` (`title`,`description`) WITH PARSER ngram,
  CONSTRAINT `fk_goods_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_goods_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='商品表';

-- ---------------------------------------------------------------------
-- 7. goods_image 商品图片表（每商品1~9张,应用层校验）
-- ---------------------------------------------------------------------
CREATE TABLE `goods_image` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `goods_id`   BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `url`        VARCHAR(255)    NOT NULL COMMENT '原图URL',
  `thumb_url`  VARCHAR(255)    NOT NULL COMMENT '缩略图URL',
  `sort`       TINYINT         NOT NULL DEFAULT 0 COMMENT '顺序0~8',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_goods_id` (`goods_id`),
  CONSTRAINT `fk_goods_image_goods` FOREIGN KEY (`goods_id`) REFERENCES `goods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='商品图片表';

-- ---------------------------------------------------------------------
-- 8. goods_tag 商品-标签关联表【v1.1新增】（每商品0~5个标签,全量重建-事务先删后插）
-- ---------------------------------------------------------------------
CREATE TABLE `goods_tag` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `goods_id`   BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `tag_id`     BIGINT UNSIGNED NOT NULL COMMENT '标签ID',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_goods_tag` (`goods_id`,`tag_id`),
  KEY `idx_tag_id` (`tag_id`),
  CONSTRAINT `fk_goods_tag_goods` FOREIGN KEY (`goods_id`) REFERENCES `goods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_goods_tag_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='商品标签关联表';

-- ---------------------------------------------------------------------
-- 9. want_post 求购帖表（软删审计字段-T4）
-- ---------------------------------------------------------------------
CREATE TABLE `want_post` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '求购者ID',
  `category_id`     BIGINT UNSIGNED NOT NULL COMMENT '期望分类ID',
  `title`           VARCHAR(50)     NOT NULL COMMENT '标题',
  `description`     VARCHAR(500)    NOT NULL COMMENT '求购描述',
  `budget`          DECIMAL(10,2)   DEFAULT NULL COMMENT '心理价,NULL=面议',
  `status`          VARCHAR(20)     NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/DEALT/CLOSED/DELETED',
  `deleted_at`      DATETIME        DEFAULT NULL COMMENT '软删时间-T4',
  `deleted_by_type` VARCHAR(10)     DEFAULT NULL COMMENT '删除操作者类型USER/ADMIN-T4',
  `deleted_by`      BIGINT UNSIGNED DEFAULT NULL COMMENT '删除操作者ID(逻辑外键)-T4',
  `delete_reason`   VARCHAR(200)    DEFAULT NULL COMMENT '删除原因-T4',
  `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status_created` (`status`,`created_at`),
  CONSTRAINT `fk_want_post_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_want_post_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='求购帖表';

-- ---------------------------------------------------------------------
-- 10. order_info 订单表（出售/求购/交换共用状态机-PRD§5.3；
--     SWAP为双方参与者模型,完成需双确认-T7；单号由order_no_seq事务生成-T11）
-- ---------------------------------------------------------------------
CREATE TABLE `order_info` (
  `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no`           VARCHAR(32)     NOT NULL COMMENT '业务单号SH+yyyyMMdd+N位,order_no_seq事务生成-T11',
  `type`               VARCHAR(10)     NOT NULL COMMENT 'SALE/PURCHASE/SWAP',
  `status`             VARCHAR(20)     NOT NULL DEFAULT 'WAIT_CONFIRM' COMMENT 'WAIT_CONFIRM/SCHEDULED/COMPLETED/CANCELLED',
  `goods_id`           BIGINT UNSIGNED DEFAULT NULL COMMENT '关联商品,SALE必填其余可空',
  `buyer_id`           BIGINT UNSIGNED NOT NULL COMMENT '展示角色:SALE=下单/PURCHASE=求购者/SWAP=发起方-T7',
  `seller_id`          BIGINT UNSIGNED NOT NULL COMMENT '展示角色:SALE=发布者/PURCHASE=应约者/SWAP=帖主-T7',
  `amount`             DECIMAL(10,2)   NOT NULL DEFAULT 0.00 COMMENT '金额,SWAP=0或差价,线下支付平台不经手',
  `confirmed_at`       DATETIME        DEFAULT NULL COMMENT '进入待面交时间(SWAP创建即SCHEDULED,=创建时间)',
  `buyer_confirmed_at` DATETIME        DEFAULT NULL COMMENT '买方完成确认时间-T7',
  `seller_confirmed_at` DATETIME       DEFAULT NULL COMMENT '卖方完成确认时间-T7',
  `completed_at`       DATETIME        DEFAULT NULL COMMENT '完成时间',
  `cancelled_at`       DATETIME        DEFAULT NULL COMMENT '取消时间',
  `cancelled_by`       VARCHAR(10)     DEFAULT NULL COMMENT 'BUYER/SELLER/TIMEOUT',
  `cancel_reason`      VARCHAR(200)    DEFAULT NULL COMMENT '取消理由',
  `created_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_goods_id` (`goods_id`),
  KEY `idx_buyer_status` (`buyer_id`,`status`),
  KEY `idx_seller_status` (`seller_id`,`status`),
  KEY `idx_status_created` (`status`,`created_at`),
  KEY `idx_confirmed_at` (`confirmed_at`),
  CONSTRAINT `fk_order_goods` FOREIGN KEY (`goods_id`) REFERENCES `goods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_order_buyer` FOREIGN KEY (`buyer_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_order_seller` FOREIGN KEY (`seller_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='订单表(出售/求购/交换共用;SWAP双确认-T7)';

-- ---------------------------------------------------------------------
-- 11. goods_want "想要"事实表【v1.1新增】（want_count唯一事实来源-T5；
--     写入与计数+1同事务;订单取消不回退,语义为兴趣记录）
-- ---------------------------------------------------------------------
CREATE TABLE `goods_want` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '想要的用户ID',
  `goods_id`   BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `order_id`   BIGINT UNSIGNED DEFAULT NULL COMMENT '首次产生的订单ID',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_goods` (`user_id`,`goods_id`),
  KEY `idx_goods_id` (`goods_id`),
  CONSTRAINT `fk_goods_want_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_goods_want_goods` FOREIGN KEY (`goods_id`) REFERENCES `goods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_goods_want_order` FOREIGN KEY (`order_id`) REFERENCES `order_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='"想要"事实表(want_count事实来源-T5)';

-- ---------------------------------------------------------------------
-- 12. offer 求购应约表（pending_key生成列唯一约束-T8；
--     接受时同事务:锁帖→帖DEALT→本条已接受→其余待处理全部拒绝→建单）
-- ---------------------------------------------------------------------
CREATE TABLE `offer` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `want_post_id` BIGINT UNSIGNED NOT NULL COMMENT '求购帖ID',
  `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '应约卖家ID',
  `price`        DECIMAL(10,2)   NOT NULL COMMENT '报价',
  `message`      VARCHAR(200)    DEFAULT NULL COMMENT '留言',
  `status`       TINYINT         NOT NULL DEFAULT 0 COMMENT '0待处理/1已接受/2已拒绝/3已撤回',
  `order_id`     BIGINT UNSIGNED DEFAULT NULL COMMENT '被接受后生成的订单ID',
  `pending_key`  BIGINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN `status` = 0 THEN `want_post_id` END) STORED COMMENT '待处理键-T8:非待处理行为NULL',
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_want_post` (`want_post_id`),
  KEY `idx_user_id` (`user_id`),
  UNIQUE KEY `uk_offer_pending` (`want_post_id`,`user_id`,`pending_key`),
  CONSTRAINT `fk_offer_want_post` FOREIGN KEY (`want_post_id`) REFERENCES `want_post` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_offer_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_offer_order` FOREIGN KEY (`order_id`) REFERENCES `order_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='求购应约表(待处理唯一-T8)';

-- ---------------------------------------------------------------------
-- 13. swap_post 交换帖表（软删审计字段-T4）
-- ---------------------------------------------------------------------
CREATE TABLE `swap_post` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '帖主ID',
  `category_id`     BIGINT UNSIGNED NOT NULL COMMENT '物品分类ID',
  `title`           VARCHAR(50)     NOT NULL COMMENT '标题',
  `my_item_desc`    VARCHAR(500)    NOT NULL COMMENT '我的物品描述',
  `want_item_desc`  VARCHAR(500)    NOT NULL COMMENT '想要的物品描述',
  `allow_diff`      TINYINT         NOT NULL DEFAULT 0 COMMENT '是否接受补差价 0否 1是',
  `diff_amount`     DECIMAL(10,2)   DEFAULT NULL COMMENT '期望差价金额,allow_diff=1时填',
  `status`          VARCHAR(20)     NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/DEALT/CLOSED/DELETED',
  `deleted_at`      DATETIME        DEFAULT NULL COMMENT '软删时间-T4',
  `deleted_by_type` VARCHAR(10)     DEFAULT NULL COMMENT '删除操作者类型USER/ADMIN-T4',
  `deleted_by`      BIGINT UNSIGNED DEFAULT NULL COMMENT '删除操作者ID(逻辑外键)-T4',
  `delete_reason`   VARCHAR(200)    DEFAULT NULL COMMENT '删除原因-T4',
  `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status_created` (`status`,`created_at`),
  CONSTRAINT `fk_swap_post_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_swap_post_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='交换帖表';

-- ---------------------------------------------------------------------
-- 14. swap_request 交换请求表（pending_key生成列唯一约束-T8；
--     同意时同事务:锁帖→帖DEALT→本条已同意→其余待处理全部拒绝→建SWAP单）
-- ---------------------------------------------------------------------
CREATE TABLE `swap_request` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `swap_post_id` BIGINT UNSIGNED NOT NULL COMMENT '交换帖ID',
  `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '发起方ID',
  `item_desc`    VARCHAR(500)    NOT NULL COMMENT '发起方物品说明',
  `goods_id`     BIGINT UNSIGNED DEFAULT NULL COMMENT '关联发起方在售商品,可空',
  `status`       TINYINT         NOT NULL DEFAULT 0 COMMENT '0待处理/1已同意/2已拒绝',
  `order_id`     BIGINT UNSIGNED DEFAULT NULL COMMENT '被同意后生成的订单ID',
  `pending_key`  BIGINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN `status` = 0 THEN `swap_post_id` END) STORED COMMENT '待处理键-T8',
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_swap_post` (`swap_post_id`),
  KEY `idx_user_id` (`user_id`),
  UNIQUE KEY `uk_swap_req_pending` (`swap_post_id`,`user_id`,`pending_key`),
  CONSTRAINT `fk_swap_req_post` FOREIGN KEY (`swap_post_id`) REFERENCES `swap_post` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_swap_req_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_swap_req_goods` FOREIGN KEY (`goods_id`) REFERENCES `goods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_swap_req_order` FOREIGN KEY (`order_id`) REFERENCES `order_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='交换请求表(待处理唯一-T8)';

-- ---------------------------------------------------------------------
-- 15. review 交易评价表（每人每单一条;COMPLETED后7天内可评）
-- ---------------------------------------------------------------------
CREATE TABLE `review` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`    BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
  `reviewer_id` BIGINT UNSIGNED NOT NULL COMMENT '评价人ID',
  `reviewee_id` BIGINT UNSIGNED NOT NULL COMMENT '被评价人ID',
  `score`       TINYINT         NOT NULL COMMENT '1~5星',
  `content`     VARCHAR(200)    DEFAULT NULL COMMENT '评价内容(敏感词校验)',
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_reviewer` (`order_id`,`reviewer_id`),
  KEY `idx_reviewee` (`reviewee_id`),
  CONSTRAINT `fk_review_order` FOREIGN KEY (`order_id`) REFERENCES `order_info` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_review_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_review_reviewee` FOREIGN KEY (`reviewee_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='交易评价表';

-- ---------------------------------------------------------------------
-- 16. credit_log 信用分流水表（唯一事实来源;ref_*为多态逻辑外键+孤儿扫描）
-- ---------------------------------------------------------------------
CREATE TABLE `credit_log` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '被变动用户ID',
  `score_change` INT             NOT NULL COMMENT '变动值,正加负减(T2避保留字)',
  `before_score` INT             NOT NULL COMMENT '变动前分值',
  `after_score`  INT             NOT NULL COMMENT '变动后分值',
  `reason`       VARCHAR(30)     NOT NULL COMMENT 'ORDER_COMPLETE+2/REPORT_VALID-10/CANCEL_TIMEOUT-2/MALICIOUS_REPORT-5/ADMIN_ADJUST',
  `ref_type`     VARCHAR(20)     DEFAULT NULL COMMENT '多态关联类型ORDER/REPORT(逻辑外键)-T3',
  `ref_id`       BIGINT UNSIGNED DEFAULT NULL COMMENT '关联单据ID',
  `remark`       VARCHAR(200)    DEFAULT NULL COMMENT '备注,ADMIN_ADJUST必填',
  `operator_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '操作管理员ID,仅ADMIN_ADJUST',
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_created` (`user_id`,`created_at`),
  CONSTRAINT `fk_credit_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_credit_operator` FOREIGN KEY (`operator_id`) REFERENCES `admin` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='信用分流水表';

-- ---------------------------------------------------------------------
-- 17. favorite 收藏表（与favorite_count±1同事务）
-- ---------------------------------------------------------------------
CREATE TABLE `favorite` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `goods_id`   BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_goods` (`user_id`,`goods_id`),
  KEY `idx_goods_id` (`goods_id`),
  CONSTRAINT `fk_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_favorite_goods` FOREIGN KEY (`goods_id`) REFERENCES `goods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='收藏表';

-- ---------------------------------------------------------------------
-- 18. conversation 会话表（last_msg/unread为派生字段,事实=message.read_at-T5）
-- ---------------------------------------------------------------------
CREATE TABLE `conversation` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user1_id`    BIGINT UNSIGNED NOT NULL COMMENT '会话一方,约定小于user2_id',
  `user2_id`    BIGINT UNSIGNED NOT NULL COMMENT '会话另一方',
  `last_msg`    VARCHAR(500)    DEFAULT NULL COMMENT '最近消息摘要(派生-T5)',
  `last_msg_at` DATETIME        DEFAULT NULL COMMENT '最近消息时间(派生-T5)',
  `unread1`     INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT 'user1未读数(派生-T5)',
  `unread2`     INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT 'user2未读数(派生-T5)',
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pair` (`user1_id`,`user2_id`),
  KEY `idx_user1_last` (`user1_id`,`last_msg_at`),
  KEY `idx_user2_last` (`user2_id`,`last_msg_at`),
  CONSTRAINT `fk_conv_user1` FOREIGN KEY (`user1_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_conv_user2` FOREIGN KEY (`user2_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='聊天会话表';

-- ---------------------------------------------------------------------
-- 19. message 消息表（read_at为未读数唯一事实依据-T5;先落库再推送）
-- ---------------------------------------------------------------------
CREATE TABLE `message` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `conversation_id` BIGINT UNSIGNED NOT NULL COMMENT '会话ID',
  `sender_id`       BIGINT UNSIGNED NOT NULL COMMENT '发送者ID',
  `msg_type`        VARCHAR(10)     NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT/GOODS_CARD/IMAGE',
  `content`         VARCHAR(500)    NOT NULL COMMENT 'TEXT=文本/GOODS_CARD=商品ID/IMAGE=图片URL',
  `read_at`         DATETIME        DEFAULT NULL COMMENT '接收方阅读时间-T5,未读数事实依据',
  `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_conv_created` (`conversation_id`,`created_at`),
  KEY `idx_conv_read` (`conversation_id`,`read_at`),
  CONSTRAINT `fk_message_conv` FOREIGN KEY (`conversation_id`) REFERENCES `conversation` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_message_sender` FOREIGN KEY (`sender_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='聊天消息表';

-- ---------------------------------------------------------------------
-- 20. notification 站内通知表（ref_*为跳转多态逻辑外键+孤儿扫描-T3）
-- ---------------------------------------------------------------------
CREATE TABLE `notification` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '接收人ID',
  `type`       VARCHAR(20)     NOT NULL COMMENT 'ORDER/AUDIT/REPORT/CREDIT/SYSTEM',
  `title`      VARCHAR(100)    NOT NULL COMMENT '标题',
  `content`    VARCHAR(500)    NOT NULL COMMENT '内容',
  `ref_type`   VARCHAR(20)     DEFAULT NULL COMMENT '跳转类型(逻辑外键)-T3',
  `ref_id`     BIGINT UNSIGNED DEFAULT NULL COMMENT '跳转对象ID',
  `is_read`    TINYINT         NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_read` (`user_id`,`is_read`),
  KEY `idx_user_created` (`user_id`,`created_at`),
  CONSTRAINT `fk_notification_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='站内通知表';

-- ---------------------------------------------------------------------
-- 21. report 举报表（target_*为多态逻辑外键-T3;images JSON-T10维持）
-- ---------------------------------------------------------------------
CREATE TABLE `report` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `reporter_id`  BIGINT UNSIGNED NOT NULL COMMENT '举报人ID',
  `target_type`  VARCHAR(10)     NOT NULL COMMENT 'GOODS/WANT/SWAP/USER',
  `target_id`    BIGINT UNSIGNED NOT NULL COMMENT '被举报对象ID(多态逻辑外键)-T3',
  `report_type`  VARCHAR(20)     NOT NULL COMMENT 'VIOLATION/FRAUD/COUNTERFEIT/OTHER',
  `description`  VARCHAR(500)    DEFAULT NULL COMMENT '补充描述',
  `images`       JSON            DEFAULT NULL COMMENT '截图URL数组,最多3张-T10',
  `status`       TINYINT         NOT NULL DEFAULT 0 COMMENT '0待处理/1已处置/2已驳回',
  `result`       VARCHAR(200)    DEFAULT NULL COMMENT '处置说明',
  `handler_id`   BIGINT UNSIGNED DEFAULT NULL COMMENT '处置管理员ID',
  `handled_at`   DATETIME        DEFAULT NULL COMMENT '处置时间',
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_created` (`status`,`created_at`),
  KEY `idx_target` (`target_type`,`target_id`),
  CONSTRAINT `fk_report_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_report_handler` FOREIGN KEY (`handler_id`) REFERENCES `admin` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='举报工单表';

-- ---------------------------------------------------------------------
-- 22. sensitive_word 敏感词表（DFA全量内存加载,增删后刷新缓存）
-- ---------------------------------------------------------------------
CREATE TABLE `sensitive_word` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `word`       VARCHAR(50)     NOT NULL COMMENT '敏感词',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word` (`word`)
) ENGINE=InnoDB COMMENT='敏感词表';

-- ---------------------------------------------------------------------
-- 23. banner 轮播图表
-- ---------------------------------------------------------------------
CREATE TABLE `banner` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `title`      VARCHAR(50)     DEFAULT NULL COMMENT '标题(管理端备注)',
  `image_url`  VARCHAR(255)    NOT NULL COMMENT '图片URL',
  `link_url`   VARCHAR(255)    DEFAULT NULL COMMENT '跳转链接',
  `sort`       INT             NOT NULL DEFAULT 0 COMMENT '排序,小者在前',
  `status`     TINYINT         NOT NULL DEFAULT 0 COMMENT '0下线 1上线',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_sort` (`status`,`sort`)
) ENGINE=InnoDB COMMENT='首页轮播图表';

-- ---------------------------------------------------------------------
-- 24. notice 公告表
-- ---------------------------------------------------------------------
CREATE TABLE `notice` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `title`        VARCHAR(100)    NOT NULL COMMENT '公告标题',
  `content`      VARCHAR(2000)   NOT NULL COMMENT '公告内容',
  `status`       TINYINT         NOT NULL DEFAULT 0 COMMENT '0草稿/1发布/2下线',
  `publisher_id` BIGINT UNSIGNED NOT NULL COMMENT '发布管理员ID',
  `published_at` DATETIME        DEFAULT NULL COMMENT '发布时间',
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_published` (`status`,`published_at`),
  CONSTRAINT `fk_notice_publisher` FOREIGN KEY (`publisher_id`) REFERENCES `admin` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='平台公告表';

-- ---------------------------------------------------------------------
-- 25. operation_log 后台操作日志表（操作者FK;审计对象target_*多态逻辑外键-T3;只写不改不删）
-- ---------------------------------------------------------------------
CREATE TABLE `operation_log` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `admin_id`       BIGINT UNSIGNED NOT NULL COMMENT '操作管理员ID',
  `admin_username` VARCHAR(50)     NOT NULL COMMENT '审计快照,写入固化(非派生)-T5',
  `action`         VARCHAR(50)     NOT NULL COMMENT '动作枚举,含ADMIN_RESET_PASSWORD等',
  `target_type`    VARCHAR(20)     DEFAULT NULL COMMENT '操作对象类型(多态逻辑外键)-T3',
  `target_id`      BIGINT UNSIGNED DEFAULT NULL COMMENT '操作对象ID',
  `detail`         VARCHAR(500)    DEFAULT NULL COMMENT '参数与理由',
  `ip`             VARCHAR(45)     NOT NULL COMMENT '来源IP,兼容IPv6',
  `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin_created` (`admin_id`,`created_at`),
  KEY `idx_created` (`created_at`),
  CONSTRAINT `fk_oplog_admin` FOREIGN KEY (`admin_id`) REFERENCES `admin` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='后台操作日志表';

-- ---------------------------------------------------------------------
-- 26. user_behavior 用户行为表（VIEW按人/商品/行为/业务日期唯一-T9,数据库保证并发安全;
--     非VIEW行view_day_key为NULL不受限,ORDER_DONE可同日多次）
-- ---------------------------------------------------------------------
CREATE TABLE `user_behavior` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `goods_id`      BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
  `behavior`      VARCHAR(10)     NOT NULL COMMENT 'VIEW/FAVORITE/ORDER_DONE',
  `behavior_date` DATE            NOT NULL COMMENT '业务日期,写入取当天-T9',
  `view_day_key`  DATE GENERATED ALWAYS AS (CASE WHEN `behavior` = 'VIEW' THEN `behavior_date` END) STORED COMMENT 'VIEW日期键-T9',
  `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_behavior_view_day` (`user_id`,`goods_id`,`behavior`,`view_day_key`),
  KEY `idx_user_behavior` (`user_id`,`behavior`),
  KEY `idx_behavior_goods` (`behavior`,`goods_id`),
  CONSTRAINT `fk_behavior_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_behavior_goods` FOREIGN KEY (`goods_id`) REFERENCES `goods` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB COMMENT='用户行为表(推荐输入;VIEW日唯一-T9)';

-- ---------------------------------------------------------------------
-- 27. order_no_seq 订单号每日序列表【v1.1新增】
--     取号(两步式,与订单插入同事务): 
--       INSERT IGNORE INTO order_no_seq (seq_date, seq_val) VALUES (?, 0);
--       UPDATE order_no_seq SET seq_val = LAST_INSERT_ID(seq_val + 1) WHERE seq_date = ?;
--       SELECT LAST_INSERT_ID();
--     注意: 单语句 upsert 在当天首次取号时因无自增列会返回 0(实测),故用两步式;
--     序列>999999自动扩7位,order_no唯一索引为最终防线
-- ---------------------------------------------------------------------
CREATE TABLE `order_no_seq` (
  `seq_date`   DATE            NOT NULL COMMENT '业务日期',
  `seq_val`    BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当日已用序号',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`seq_date`)
) ENGINE=InnoDB COMMENT='订单号每日序列表-T11';

-- =====================================================================
-- 种子数据：10 个一级分类（需求规格说明书 §12.1 默认值；二级分类与标签由后台配置）
-- =====================================================================
INSERT INTO `category` (`id`, `parent_id`, `name`, `sort`) VALUES
  (1,  0, '教材书籍', 1),
  (2,  0, '数码电子', 2),
  (3,  0, '电脑配件', 3),
  (4,  0, '生活用品', 4),
  (5,  0, '服饰鞋包', 5),
  (6,  0, '运动健身', 6),
  (7,  0, '美妆护肤', 7),
  (8,  0, '乐器',     8),
  (9,  0, '代步工具', 9),
  (10, 0, '其他',     10);

-- =====================================================================
-- 种子数据：通用违禁词测试词库（PRD §12.1 默认值，约 100 词）
-- 类目口径参照电商禁售清单，仅用于本平台内容安全拦截（§5.8 DFA）；
-- 生产上线前由管理员在后台词库（ADM-03）维护替换
-- =====================================================================
INSERT IGNORE INTO `sensitive_word` (`word`) VALUES
  -- 枪支/爆炸/管制器具
  ('枪支'),('弹药'),('子弹'),('手枪'),('猎枪'),('气枪'),('雷管'),('炸药'),('爆炸物'),('管制刀具'),
  ('匕首'),('弩'),('电击器'),('甩棍'),('手铐'),('烟花爆竹制作'),
  -- 毒品
  ('毒品'),('冰毒'),('摇头丸'),('大麻'),('K粉'),('迷药'),('吗啡'),('杜冷丁'),('罂粟'),
  -- 赌博
  ('赌博'),('赌具'),('老虎机'),('私彩'),('六合彩'),('网络赌博'),
  -- 欺诈/违法金融
  ('信用卡套现'),('洗钱'),('跑分'),('收购银行卡'),('出售银行卡'),('黑卡'),('假钞'),('假币'),
  ('高利贷'),('套路贷'),('刷单'),('刷信誉'),('传销'),('资金盘'),
  -- 假证/票据
  ('办证'),('假证'),('伪造证件'),('毕业证代办'),('学历造假'),('假发票'),('代开发票'),('假公章'),
  ('身份证复制'),('代实名'),
  -- 盗版/假冒
  ('盗版'),('高仿'),('假货'),('山寨机'),('盗版光碟'),('破解版'),
  -- 黑客/窃听工具
  ('木马'),('病毒程序'),('钓鱼网站'),('黑客软件'),('监听器'),('窃听器'),('针孔摄像头'),
  ('定位追踪器'),('撞库工具'),
  -- 野生动植物及其制品
  ('象牙'),('穿山甲'),('熊掌'),('玳瑁'),('红珊瑚'),('野生保护动物'),('野生动物制品'),
  -- 违禁药品/危险品
  ('处方药代购'),('精神药品'),('放射性物质'),('剧毒物品'),('氰化物'),
  -- 校园违规场景
  ('代考'),('替考'),('代写论文'),('论文代写'),('代课'),('代签到'),('保过'),('考试作弊器'),
  -- 其他
  ('色情'),('淫秽物品'),('人体器官'),('赃物'),('销赃');

-- =====================================================================
-- 种子数据：二级分类（每个一级分类 2 个，供发布/浏览演示；后台 ADM-06 可维护）
-- =====================================================================
INSERT INTO `category` (`parent_id`, `name`, `sort`) VALUES
  (1, '教材课本', 1), (1, '考试辅导', 2),
  (2, '手机', 1),     (2, '相机', 2),
  (3, 'CPU', 1),      (3, '显卡', 2),
  (4, '收纳用品', 1), (4, '小家电', 2),
  (5, '运动服饰', 1), (5, '鞋靴', 2),
  (6, '健身器材', 1), (6, '球类装备', 2),
  (7, '护肤', 1),     (7, '彩妆', 2),
  (8, '吉他', 1),     (8, '键盘乐器', 2),
  (9, '自行车', 1),   (9, '电动车', 2),
  (10, '其他闲置', 1);
