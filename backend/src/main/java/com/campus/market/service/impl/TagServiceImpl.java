package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.market.entity.Tag;
import com.campus.market.mapper.TagMapper;
import com.campus.market.service.TagService;
import com.campus.market.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 标签服务实现（PRD GDS-08）
 */
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;

    @Override
    public List<TagVO> listEnabled() {
        List<Tag> tags = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getStatus, Tag.STATUS_ENABLED)
                .orderByAsc(Tag::getSort)
                .orderByAsc(Tag::getId));
        return tags.stream().map(tag -> {
            TagVO vo = new TagVO();
            vo.setId(tag.getId());
            vo.setName(tag.getName());
            return vo;
        }).toList();
    }
}
