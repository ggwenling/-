package com.campus.market.controller;

import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.PageResult;
import com.campus.market.common.api.Result;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.dto.ActionReasonDTO;
import com.campus.market.dto.OfferCreateDTO;
import com.campus.market.dto.WantPostListQuery;
import com.campus.market.dto.WantPostPublishDTO;
import com.campus.market.security.LoginUser;
import com.campus.market.security.UserContext;
import com.campus.market.service.WantPostService;
import com.campus.market.vo.OfferVO;
import com.campus.market.vo.WantPostCardVO;
import com.campus.market.vo.WantPostDetailVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
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
 * 求购接口（PRD REQ-01 发布 / REQ-02 广场与编辑关闭 / REQ-03 应约与接受 / REQ-04 状态流转）。
 * 广场与详情为公开接口（游客可浏览，PRD §3.1）；发布/编辑/应约/接受均需登录，
 * 且要求已完成校园认证、信用分未受限（service 层校验，PRD §4.1）。
 */
@Tag(name = "特色板块-求购")
@Validated
@RestController
@RequestMapping("/api/want-posts")
@RequiredArgsConstructor
public class WantPostController {

    private final WantPostService wantPostService;

    @Operation(summary = "发布求购帖（REQ-01，需登录 + 校园认证）")
    @PostMapping
    public Result<Long> publish(@Valid @RequestBody WantPostPublishDTO dto) {
        return Result.ok(wantPostService.publish(dto, requireFrontUser()));
    }

    @Operation(summary = "编辑求购帖（REQ-02，仅帖主且求购中）")
    @PutMapping("/{id}")
    public Result<Long> update(@PathVariable Long id, @Valid @RequestBody WantPostPublishDTO dto) {
        return Result.ok(wantPostService.update(id, dto, requireFrontUser()));
    }

    @Operation(summary = "关闭求购帖（REQ-02：OPEN → CLOSED，其余待处理应约失效）")
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id,
                             @RequestBody(required = false) ActionReasonDTO dto) {
        wantPostService.close(id, requireFrontUser(), dto == null ? null : dto.getReason());
        return Result.ok();
    }

    @Operation(summary = "软删除求购帖（REQ-02，仅帖主；审计四元组 T4）")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        wantPostService.removeByOwner(id, requireFrontUser());
        return Result.ok();
    }

    @Operation(summary = "恢复自己删除的求购帖（30 天内，恢复为已关闭）")
    @PostMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable Long id) {
        wantPostService.restore(id, requireFrontUser());
        return Result.ok();
    }

    @Operation(summary = "求购广场分页（REQ-02，公开；status/categoryId/q/mine/sort 可选）")
    @GetMapping
    public Result<PageResult<WantPostCardVO>> list(WantPostListQuery query) {
        return Result.ok(wantPostService.pageList(query, currentFrontUserOrNull()));
    }

    @Operation(summary = "求购详情（REQ-02/03，公开；帖主额外返回应约列表）")
    @GetMapping("/{id}")
    public Result<WantPostDetailVO> detail(@PathVariable Long id) {
        return Result.ok(wantPostService.detail(id, currentFrontUserOrNull()));
    }

    @Operation(summary = "提交应约（REQ-03，需登录 + 校园认证；一人一帖仅一条待处理）")
    @PostMapping("/{id}/offers")
    public Result<OfferVO> createOffer(@PathVariable Long id, @Valid @RequestBody OfferCreateDTO dto) {
        return Result.ok(wantPostService.createOffer(id, dto, requireFrontUser()));
    }

    @Operation(summary = "我提交的应约分页（REQ-03 查看应约状态，status 可选 0/1/2/3）")
    @GetMapping("/offers/my")
    public Result<PageResult<OfferVO>> myOffers(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize) {
        return Result.ok(wantPostService.pageMyOffers(requireFrontUser().getUserId(), status, pageNum, pageSize));
    }

    @Operation(summary = "撤回自己的应约（REQ-03，待处理 → 已撤回）")
    @PostMapping("/offers/{offerId}/withdraw")
    public Result<Void> withdrawOffer(@PathVariable Long offerId) {
        wantPostService.withdrawOffer(offerId, requireFrontUser());
        return Result.ok();
    }

    @Operation(summary = "接受应约（REQ-03/04，仅求购者；原子生成 PURCHASE 订单并关闭其余应约）")
    @PostMapping("/offers/{offerId}/accept")
    public Result<OfferVO> acceptOffer(@PathVariable Long offerId) {
        return Result.ok(wantPostService.acceptOffer(offerId, requireFrontUser()));
    }

    @Operation(summary = "拒绝应约（REQ-03，仅求购者，待处理 → 已拒绝）")
    @PostMapping("/offers/{offerId}/reject")
    public Result<Void> rejectOffer(@PathVariable Long offerId,
                                    @RequestBody(required = false) ActionReasonDTO dto) {
        wantPostService.rejectOffer(offerId, requireFrontUser(), dto == null ? null : dto.getReason());
        return Result.ok();
    }

    /** 写操作仅限前台用户主体（admin 与 user 主键空间独立，防止越权） */
    private LoginUser requireFrontUser() {
        LoginUser user = UserContext.requireLogin();
        if (user.getUserType() != LoginUser.UserType.USER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }

    /** 广场/详情场景的"登录可选"主体：非前台用户一律按游客处理 */
    private LoginUser currentFrontUserOrNull() {
        LoginUser user = UserContext.get();
        return user != null && user.getUserType() == LoginUser.UserType.USER ? user : null;
    }
}
