package com.campus.market.controller;

import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.PageResult;
import com.campus.market.common.api.Result;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.dto.ActionReasonDTO;
import com.campus.market.dto.SwapPostListQuery;
import com.campus.market.dto.SwapPostPublishDTO;
import com.campus.market.dto.SwapRequestCreateDTO;
import com.campus.market.security.LoginUser;
import com.campus.market.security.UserContext;
import com.campus.market.service.SwapPostService;
import com.campus.market.vo.SwapPostCardVO;
import com.campus.market.vo.SwapPostDetailVO;
import com.campus.market.vo.SwapRequestVO;
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
 * 交换接口（PRD SWP-01 发布 / SWP-02 广场与发起交换 / SWP-03 同意或拒绝）。
 * 广场与详情公开；发布/编辑/发起/同意需登录 + 校园认证 + 未受限。
 */
@Tag(name = "特色板块-交换")
@Validated
@RestController
@RequestMapping("/api/swap-posts")
@RequiredArgsConstructor
public class SwapPostController {

    private final SwapPostService swapPostService;

    @Operation(summary = "发布交换帖（SWP-01，需登录 + 校园认证；差价开关联动）")
    @PostMapping
    public Result<Long> publish(@Valid @RequestBody SwapPostPublishDTO dto) {
        return Result.ok(swapPostService.publish(dto, requireFrontUser()));
    }

    @Operation(summary = "编辑交换帖（SWP-01，仅帖主且交换中）")
    @PutMapping("/{id}")
    public Result<Long> update(@PathVariable Long id, @Valid @RequestBody SwapPostPublishDTO dto) {
        return Result.ok(swapPostService.update(id, dto, requireFrontUser()));
    }

    @Operation(summary = "关闭交换帖（SWP-02：OPEN → CLOSED，其余待处理请求失效）")
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id,
                              @RequestBody(required = false) ActionReasonDTO dto) {
        swapPostService.close(id, requireFrontUser(), dto == null ? null : dto.getReason());
        return Result.ok();
    }

    @Operation(summary = "软删除交换帖（SWP-02，仅帖主；审计四元组 T4）")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        swapPostService.removeByOwner(id, requireFrontUser());
        return Result.ok();
    }

    @Operation(summary = "恢复自己删除的交换帖（30 天内，恢复为已关闭）")
    @PostMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable Long id) {
        swapPostService.restore(id, requireFrontUser());
        return Result.ok();
    }

    @Operation(summary = "交换广场分页（SWP-02，公开；status/categoryId/q/mine 可选）")
    @GetMapping
    public Result<PageResult<SwapPostCardVO>> list(SwapPostListQuery query) {
        return Result.ok(swapPostService.pageList(query, currentFrontUserOrNull()));
    }

    @Operation(summary = "交换详情（SWP-02/03，公开；帖主额外返回请求列表）")
    @GetMapping("/{id}")
    public Result<SwapPostDetailVO> detail(@PathVariable Long id) {
        return Result.ok(swapPostService.detail(id, currentFrontUserOrNull()));
    }

    @Operation(summary = "发起交换（SWP-02，需登录 + 校园认证；可关联自己在售商品）")
    @PostMapping("/{id}/requests")
    public Result<SwapRequestVO> createRequest(@PathVariable Long id,
                                               @Valid @RequestBody SwapRequestCreateDTO dto) {
        return Result.ok(swapPostService.createRequest(id, dto, requireFrontUser()));
    }

    @Operation(summary = "我发起的交换请求分页（status 可选 0/1/2）")
    @GetMapping("/requests/my")
    public Result<PageResult<SwapRequestVO>> myRequests(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize) {
        return Result.ok(swapPostService.pageMyRequests(requireFrontUser().getUserId(), status, pageNum, pageSize));
    }

    @Operation(summary = "同意交换请求（SWP-03，仅帖主；原子生成 SWAP 订单并关闭其余请求）")
    @PostMapping("/requests/{requestId}/accept")
    public Result<SwapRequestVO> acceptRequest(@PathVariable Long requestId) {
        return Result.ok(swapPostService.acceptRequest(requestId, requireFrontUser()));
    }

    @Operation(summary = "拒绝交换请求（SWP-03，仅帖主，待处理 → 已拒绝）")
    @PostMapping("/requests/{requestId}/reject")
    public Result<Void> rejectRequest(@PathVariable Long requestId,
                                      @RequestBody(required = false) ActionReasonDTO dto) {
        swapPostService.rejectRequest(requestId, requireFrontUser(), dto == null ? null : dto.getReason());
        return Result.ok();
    }

    private LoginUser requireFrontUser() {
        LoginUser user = UserContext.requireLogin();
        if (user.getUserType() != LoginUser.UserType.USER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }

    private LoginUser currentFrontUserOrNull() {
        LoginUser user = UserContext.get();
        return user != null && user.getUserType() == LoginUser.UserType.USER ? user : null;
    }
}
