package com.campus.market.service.impl;

import com.campus.market.entity.SensitiveWord;
import com.campus.market.mapper.SensitiveWordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * SensitiveWordServiceImpl 深测（§5.8 DFA：refresh 计数/去重、findHits 命中与重叠、volatile 刷新可见性）。
 */
@ExtendWith(MockitoExtension.class)
class SensitiveWordServiceImplTest {

    @Mock
    SensitiveWordMapper sensitiveWordMapper;

    private SensitiveWordServiceImpl newService(String... words) {
        SensitiveWordServiceImpl service = new SensitiveWordServiceImpl(sensitiveWordMapper);
        lenient().when(sensitiveWordMapper.selectList(null)).thenReturn(toEntities(words));
        return service;
    }

    private List<SensitiveWord> toEntities(String... words) {
        return java.util.Arrays.stream(words)
                .map(w -> {
                    SensitiveWord sw = new SensitiveWord();
                    sw.setWord(w);
                    return sw;
                })
                .toList();
    }

    @Test
    void refresh_emptyDictionary_returnsZero_andFindHitsEmpty() {
        SensitiveWordServiceImpl service = newService();
        assertThat(service.refresh()).isZero();
        assertThat(service.findHits("任何文本")).isEmpty();
    }

    @Test
    void refresh_dedupAndBlankIgnored() {
        SensitiveWordServiceImpl service = newService("代考", "代考", "代考", "", "  ", null);
        assertThat(service.refresh()).isEqualTo(1);   // 去重 + 空白忽略
    }

    @Test
    void findHits_singleAndOverlapHits_inOrder() {
        SensitiveWordServiceImpl service = newService("代考", "代考服务", "枪支");
        service.refresh();

        // 同起点最长/次长都命中；不同词按出现顺序
        assertThat(service.findHits("提供代考服务")).containsExactly("代考", "代考服务");
        assertThat(service.findHits("枪支出售")).containsExactly("枪支");
        assertThat(service.findHits("正常交易")).isEmpty();
    }

    @Test
    void findHits_nullOrEmpty_returnsEmpty() {
        SensitiveWordServiceImpl service = newService("代考");
        service.refresh();
        assertThat(service.findHits(null)).isEmpty();
        assertThat(service.findHits("")).isEmpty();
    }

    @Test
    void refresh_replacesOldDictionary_volatileVisibility() {
        SensitiveWordServiceImpl service = newService("代考");
        service.refresh();
        assertThat(service.findHits("约代考")).containsExactly("代考");

        // 词库更新 → refresh 后旧词不再命中、新词生效
        when(sensitiveWordMapper.selectList(null)).thenReturn(toEntities("刷单"));
        assertThat(service.refresh()).isEqualTo(1);
        assertThat(service.findHits("约代考")).isEmpty();
        assertThat(service.findHits("帮刷单")).containsExactly("刷单");
    }

    @Test
    void findHits_crossBoundaryMatch() {
        SensitiveWordServiceImpl service = newService("bc");
        service.refresh();
        // 跨起点匹配：abcab 中第二个起点仍命中
        assertThat(service.findHits("abcab")).containsExactly("bc");
    }
}
