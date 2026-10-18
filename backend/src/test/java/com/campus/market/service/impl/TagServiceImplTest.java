package com.campus.market.service.impl;

import com.campus.market.common.MpTestSupport;
import com.campus.market.entity.Tag;
import com.campus.market.mapper.TagMapper;
import com.campus.market.vo.TagVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * TagServiceImpl 浅测（GDS-08：仅启用标签 + 排序 + VO 映射）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TagServiceImplTest {

    @Mock TagMapper tagMapper;

    @InjectMocks TagServiceImpl service;

    @BeforeAll
    static void initMp() {
        MpTestSupport.initTables(Tag.class);
    }

    @Test
    void listEnabled_mapsToVO() {
        Tag t1 = new Tag();
        t1.setId(2L);
        t1.setName("九成新");
        t1.setStatus(Tag.STATUS_ENABLED);
        t1.setSort(1);
        when(tagMapper.selectList(any())).thenReturn(List.of(t1));

        List<TagVO> vos = service.listEnabled();

        assertThat(vos).hasSize(1);
        assertThat(vos.get(0).getId()).isEqualTo(2L);
        assertThat(vos.get(0).getName()).isEqualTo("九成新");
    }
}
