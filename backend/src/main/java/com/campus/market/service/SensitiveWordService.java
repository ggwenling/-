package com.campus.market.service;

import java.util.List;

/**
 * 敏感词服务（PRD §5.8 内容安全 / GDS-01 发布敏感词检查）。
 * DFA 全量内存加载 sensitive_word 表；词库为空时拦截逻辑静默放行（先就位，M6 词库管理后台增删后调 refresh）。
 */
public interface SensitiveWordService {

    /**
     * 检查文本命中的敏感词。
     *
     * @param text 待检查文本（标题/描述/评价内容等）
     * @return 命中的敏感词列表（去重、按出现顺序），未命中返回空列表
     */
    List<String> findHits(String text);

    /**
     * 重新从 sensitive_word 表加载并重建 DFA 缓存（后台增删敏感词后调用，M6 对接）。
     *
     * @return 加载的词数量
     */
    int refresh();
}
