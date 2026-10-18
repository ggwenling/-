package com.campus.market.service.impl;

import com.campus.market.entity.SensitiveWord;
import com.campus.market.mapper.SensitiveWordMapper;
import com.campus.market.service.SensitiveWordService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 敏感词 DFA 服务实现（PRD §5.8 / GDS-01）。
 * Trie 结构全量内存匹配：启动加载（词库当前为空则空树，拦截逻辑先就位），M6 词库变更后调 {@link #refresh()}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordServiceImpl implements SensitiveWordService {

    private final SensitiveWordMapper sensitiveWordMapper;

    /** DFA 根节点；volatile 保证刷新后的读可见性 */
    private volatile Node root = new Node();

    @PostConstruct
    public void init() {
        int count = refresh();
        log.info("敏感词库加载完成：{} 个词", count);
    }

    @Override
    public int refresh() {
        List<SensitiveWord> all = sensitiveWordMapper.selectList(null);
        Node newRoot = new Node();
        Set<String> distinct = new HashSet<>();
        for (SensitiveWord sw : all) {
            String word = sw.getWord();
            if (word == null || word.isBlank()) {
                continue;
            }
            distinct.add(word);
            insertWord(newRoot, word);
        }
        root = newRoot;
        return distinct.size();
    }

    @Override
    public List<String> findHits(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        // 匹配文本与词库同口径归一小写，防"BadWord"变体绕过 DFA（验收 P2③）
        String normalized = text.toLowerCase(Locale.ROOT);
        Node currentRoot = root;
        if (currentRoot.children.isEmpty()) {
            return List.of();
        }
        // 逐起点沿 Trie 匹配，命中即记录（同一词去重，按出现顺序）
        Set<String> hits = new HashSet<>();
        List<String> ordered = new ArrayList<>();
        for (int i = 0; i < normalized.length(); i++) {
            Node node = currentRoot;
            for (int j = i; j < normalized.length(); j++) {
                node = node.children.get(normalized.charAt(j));
                if (node == null) {
                    break;
                }
                if (node.end) {
                    String word = normalized.substring(i, j + 1);
                    if (hits.add(word)) {
                        ordered.add(word);
                    }
                }
            }
        }
        return ordered;
    }

    private void insertWord(Node root, String word) {
        Node node = root;
        for (int i = 0; i < word.length(); i++) {
            node = node.children.computeIfAbsent(word.charAt(i), c -> new Node());
        }
        node.end = true;
    }

    /** Trie 节点 */
    private static class Node {
        private final Map<Character, Node> children = new HashMap<>();
        private boolean end;
    }
}
