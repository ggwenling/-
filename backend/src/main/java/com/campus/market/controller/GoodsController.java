package com.campus.market.controller;

import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.PageResult;
import com.campus.market.common.api.Result;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.dto.GoodsListQuery;
import com.campus.market.dto.GoodsPublishDTO;
import com.campus.market.security.LoginUser;
import com.campus.market.security.UserContext;
import com.campus.market.service.GoodsService;
import com.campus.market.vo.GoodsCardVO;
import com.campus.market.vo.GoodsDetailVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品接口（PRD GDS-01 发布 / GDS-02 卖家管理 / GDS-03 列表 / GDS-04 搜索 / GDS-05 详情）。
 * 列表与详情为公开接口（游客可浏览，PRD §3.1）；写操作均需登录，发布/编辑另需校园认证（service 层校验）。
 */
@Tag(name = "商品-商品")
@RestController
@RequestMapping("/api/goods")
@RequiredArgsConstructor
public class GoodsController {

    private final GoodsService goodsService;

    @Operation(summary = "发布商品（需登录 + 校园认证）")
    @PostMapping
    public Result<Long> publish(@Valid @RequestBody GoodsPublishDTO dto) {
        return Result.ok(goodsService.publish(dto, requireFrontUser()));
    }

    @Operation(summary = "编辑商品（仅本人）")
    @PutMapping("/{id}")
    public Result<Long> update(@PathVariable Long id, @Valid @RequestBody GoodsPublishDTO dto) {
        return Result.ok(goodsService.update(id, dto, requireFrontUser()));
    }

    @Operation(summary = "删除商品（仅本人，软删审计）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        goodsService.deleteByOwner(id, requireFrontUser());
        return Result.ok();
    }

    @Operation(summary = "下架商品（仅本人，ON_SALE → OFF_SALE）")
    @PostMapping("/{id}/off-sale")
    public Result<Void> offSale(@PathVariable Long id) {
        goodsService.offSale(id, requireFrontUser());
        return Result.ok();
    }

    @Operation(summary = "重新上架商品（仅本人，OFF_SALE → ON_SALE；管理员下架的除外，PRD §5.3）")
    @PostMapping("/{id}/on-sale")
    public Result<Void> onSale(@PathVariable Long id) {
        goodsService.onSale(id, requireFrontUser());
        return Result.ok();
    }

    @Operation(summary = "恢复删除的商品（仅本人删除且 30 天内，恢复为 OFF_SALE）")
    @PostMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable Long id) {
        goodsService.restore(id, requireFrontUser());
        return Result.ok();
    }

    @Operation(summary = "在售商品分页列表（筛选/排序/全文搜索/教材检索，公开）")
    @GetMapping
    public Result<PageResult<GoodsCardVO>> list(GoodsListQuery query) {
        return Result.ok(goodsService.pageList(query));
    }

    @Operation(summary = "我的商品分页（个人中心 Tab，仅登录本人）")
    @GetMapping("/my")
    public Result<PageResult<GoodsCardVO>> my(@RequestParam(defaultValue = "1") long pageNum,
                                              @RequestParam(defaultValue = "10") long pageSize) {
        LoginUser user = requireFrontUser();
        return Result.ok(goodsService.pageMine(user.getUserId(), pageNum, pageSize));
    }

    @Operation(summary = "商品详情（公开；登录用户写浏览埋点，同日去重）")
    @GetMapping("/{id}")
    public Result<GoodsDetailVO> detail(@PathVariable Long id) {
        // 游客可浏览：token 缺失/非法时 UserContext 为空；管理员主体按游客口径处理（不写埋点）
        LoginUser viewer = currentFrontUserOrNull();
        return Result.ok(goodsService.detail(id, viewer));
    }

    /** 写操作仅限前台用户主体（admin 与 user 主键空间独立，防止越权） */
    private LoginUser requireFrontUser() {
        LoginUser user = UserContext.requireLogin();
        if (user.getUserType() != LoginUser.UserType.USER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }

    /** 详情场景的"登录可选"主体：非前台用户一律按游客处理 */
    private LoginUser currentFrontUserOrNull() {
        LoginUser user = UserContext.get();
        return user != null && user.getUserType() == LoginUser.UserType.USER ? user : null;
    }
}
