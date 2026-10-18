package com.campus.market.common.api;

import lombok.Getter;

/**
 * 全局业务状态码。分段约定：0 成功；400xx 参数；401xx 登录态；403xx 权限；
 * 404xx 不存在；409xx 冲突；429xx 限流；500xx 系统。各模块细分错误码在此追加。
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "成功"),
    PARAM_ERROR(40000, "参数错误"),
    UNAUTHORIZED(40100, "未登录或登录已过期"),
    TOKEN_INVALID(40101, "登录凭证无效，请重新登录"),
    FORBIDDEN(40300, "无权限执行该操作"),
    ACCOUNT_BANNED(40301, "账号已被封禁"),
    ACCOUNT_RESTRICTED(40302, "信用分受限，暂无法执行该操作"),
    NOT_FOUND(40400, "资源不存在"),
    CONFLICT(40900, "数据冲突，请刷新后重试"),
    TOO_MANY_REQUESTS(42900, "操作过于频繁，请稍后再试"),
    SYSTEM_ERROR(50000, "系统繁忙，请稍后再试"),

    // ==================== GOODS 商品段（M2 商品中心，追加式维护） ====================
    /** PRD GDS-01：标题/描述等文本命中敏感词，message 附命中词列表 */
    GOODS_SENSITIVE(40001, "内容包含敏感词"),
    /** PRD GDS-01：商品图片数量不满足 1~9 张约束 */
    GOODS_IMAGE_LIMIT(40002, "商品图片数量须为 1~9 张"),
    /** PRD GDS-01/02：商品字段级参数不合法（价格/成色/分类等） */
    GOODS_PARAM_INVALID(40003, "商品参数不合法"),
    /** PRD GDS-02：无权操作他人商品 */
    GOODS_FORBIDDEN(40303, "无权操作该商品"),
    /** PRD GDS-05：商品不存在或已删除 */
    GOODS_NOT_FOUND(40401, "商品不存在或已删除"),
    /** PRD §5.1：商品处于 IN_TRANSACTION 等不可操作状态 */
    GOODS_NOT_ON_SALE(40901, "商品当前不可交易"),

    // ==================== AUTH 认证段（M1 用户与校园认证，追加式维护） ====================
    /** PRD USR-01/02：用户名或密码错误（不区分二者，防撞库） */
    AUTH_CREDENTIALS_INVALID(40006, "用户名或密码错误"),
    /** PRD §5.1：校园邮箱域名不在白名单 */
    AUTH_EMAIL_DOMAIN_FORBIDDEN(40007, "仅支持校园邮箱认证"),
    /** PRD USR-03：验证码错误或已过期 */
    AUTH_CODE_INVALID(40008, "验证码错误或已过期"),
    /** PRD USR-01：注册用户名重复 */
    AUTH_USERNAME_DUP(40902, "用户名已被占用"),
    /** PRD USR-03：学号已被其他账号认证（一学号一账号） */
    AUTH_STUDENT_NO_DUP(40903, "该学号已完成认证"),
    /** PRD USR-03：校园邮箱已被其他账号认证 */
    AUTH_CAMPUS_EMAIL_DUP(40904, "该校园邮箱已被认证"),
    /** PRD USR-02：登录失败 5 次锁定 10 分钟 */
    AUTH_LOGIN_LOCKED(40102, "登录失败次数过多，请 10 分钟后再试"),
    /** PRD §4.1：发布/下单等操作要求先完成校园认证（M2 发布接口已按此名引用） */
    AUTH_NOT_CERTIFIED(40304, "请先完成校园认证"),
    /** PRD §5.1：验证码发送限频（1 分钟内不可重发 / 每日 10 次） */
    AUTH_CODE_LIMIT(42901, "验证码发送过于频繁，请稍后再试"),
    /** PRD USR-03：该账号已完成校园认证，无需重复认证 */
    AUTH_ALREADY_CERTIFIED(40905, "该账号已完成校园认证"),

    // ==================== CHAT 聊天段（M4 即时消息，追加式维护） ====================
    /** PRD CHT-01/02：会话不存在（含未创建场景） */
    CHAT_CONV_NOT_FOUND(40403, "会话不存在"),
    /** PRD CHT-02：非会话双方访问会话消息 */
    CHAT_FORBIDDEN(40306, "无权访问该会话"),
    /** PRD CHT-02/06：消息内容/类型不合法（空、超 500 字、类型未知、给自己发消息等） */
    CHAT_MESSAGE_INVALID(40010, "消息内容不合法"),

    // ==================== ORDER/CRD 订单与信用段（M3 交易与信用，追加式维护） ====================
    /** PRD ORD-05：订单不存在 */
    ORDER_NOT_FOUND(40402, "订单不存在"),
    /** PRD ORD-05/06：仅订单双方可查看或操作 */
    ORDER_FORBIDDEN(40305, "无权访问该订单"),
    /** PRD §5.2：订单当前状态不允许该操作（重复确认/重复取消/重复完成等） */
    ORDER_STATE_INVALID(40906, "订单当前状态不允许该操作"),
    /** PRD ORD-01：商品不在 ON_SALE，无法下单（含并发下单锁失败） */
    ORDER_GOODS_NOT_AVAILABLE(40907, "商品当前不可下单"),
    /** PRD ORD-06：每单每人只能评价一次（uk_order_reviewer 兜底） */
    ORDER_DUPLICATE_REVIEW(40908, "该订单已评价过，请勿重复评价"),
    /** PRD ORD-06：仅订单完成（COMPLETED）后 7 天内可评价 */
    REVIEW_WINDOW_CLOSED(40009, "已过评价期（完成后 7 天内有效）"),

    // ==================== REQ/SWP 求购与交换段（M5 特色板块，追加式维护） ====================
    /** PRD REQ-01/03：求购帖字段级参数不合法（预算为负、分类非法等） */
    WANT_PARAM_INVALID(40011, "求购信息不合法"),
    /** PRD SWP-01：交换帖字段级参数不合法（差价金额为负或缺失等） */
    SWAP_PARAM_INVALID(40012, "交换信息不合法"),
    /** PRD REQ-02/04：无权操作他人求购帖 */
    WANT_POST_FORBIDDEN(40307, "无权操作该求购帖"),
    /** PRD SWP-02/03：无权操作他人交换帖或他人请求 */
    SWAP_POST_FORBIDDEN(40308, "无权操作该交换帖"),
    /** PRD §6.3：求购帖不存在或已删除 */
    WANT_POST_NOT_FOUND(40404, "求购帖不存在或已删除"),
    /** PRD REQ-03：应约不存在或已删除 */
    OFFER_NOT_FOUND(40405, "应约不存在"),
    /** PRD §6.3：交换帖不存在或已删除 */
    SWAP_POST_NOT_FOUND(40406, "交换帖不存在或已删除"),
    /** PRD SWP-02：交换请求不存在 */
    SWAP_REQUEST_NOT_FOUND(40407, "交换请求不存在"),
    /** PRD §6.3：帖子关闭/成交/删除后禁止新增应约与交换请求 */
    WANT_POST_CLOSED(40909, "求购帖已关闭或已成交，无法应约"),
    /** PRD REQ-03：不能应约自己发布的求购帖 */
    WANT_OFFER_SELF(40910, "不能应约自己发布的求购帖"),
    /** PRD REQ-03：该应约已被接受/拒绝/撤回 */
    WANT_OFFER_HANDLED(40911, "该应约已处理，请刷新后查看"),
    /** PRD REQ-03：同一求购帖最多一条待处理应约（uk_offer_pending 兜底） */
    WANT_OFFER_DUPLICATE(40915, "您已提交过应约，请等待对方处理"),
    /** PRD §6.3：帖子关闭/成交/删除后禁止新增交换请求 */
    SWAP_POST_CLOSED(40912, "交换帖已关闭或已成交，无法发起交换"),
    /** PRD SWP-02：不能对自己发布的交换帖发起交换 */
    SWAP_REQUEST_SELF(40913, "不能对自己发布的交换帖发起交换"),
    /** PRD SWP-03：该交换请求已被同意/拒绝 */
    SWAP_REQUEST_HANDLED(40914, "该交换请求已处理，请刷新后查看"),
    /** PRD SWP-02：同一交换帖最多一条待处理请求（uk_swap_req_pending 兜底） */
    SWAP_REQUEST_DUPLICATE(40916, "您已提交过交换请求，请等待对方处理"),

    // ==================== ADM 管理后台段（M6，追加式维护） ====================
    /** PRD ADM-01：管理端字段级参数不合法 */
    ADM_PARAM_INVALID(40013, "管理端参数不合法"),
    /** PRD ADM-01：修改密码时原密码不正确 */
    ADM_OLD_PASSWORD_WRONG(40014, "原密码不正确"),
    /** PRD ADM-01：该管理员账号已停用 */
    ADMIN_DISABLED(40309, "该管理员账号已停用"),
    /** PRD ADM-01/T12：首次登录须先修改密码 */
    ADM_MUST_CHANGE_PASSWORD(40310, "首次登录请先修改密码"),
    /** PRD ADM-01：管理员不存在 */
    ADMIN_NOT_FOUND(40408, "管理员不存在"),
    /** PRD ADM-01：管理员用户名已存在（uk_username 兜底） */
    ADMIN_USERNAME_DUP(40917, "管理员用户名已存在"),

    // ==================== RPT 举报处置段（M6 处置端，M7 前台入口复用） ====================
    /** PRD ADM-04：举报工单不存在 */
    REPORT_NOT_FOUND(40409, "举报工单不存在"),
    /** PRD ADM-04：被举报对象不存在或已删除 */
    REPORT_TARGET_MISSING(40410, "被举报对象不存在或已删除"),
    /** PRD ADM-04：该举报已处置，请勿重复操作 */
    REPORT_ALREADY_HANDLED(40918, "该举报已处置，请勿重复操作"),

    // ==================== ADM 目录与词库段（M6 C7） ====================
    /** PRD ADM-06：分类/标签仍被子资源或商品引用，无法删除 */
    REF_IN_USE(40919, "该资源仍被引用，无法删除"),
    /** PRD ADM-03：敏感词已存在（uk_word） */
    WORD_DUPLICATE(40920, "该敏感词已存在"),
    /** PRD ADM-03：敏感词不存在 */
    WORD_NOT_FOUND(40411, "敏感词不存在");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
