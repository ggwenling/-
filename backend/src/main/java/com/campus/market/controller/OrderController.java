package com.campus.market.controller;

import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.PageResult;
import com.campus.market.common.api.Result;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.dto.OrderActionDTO;
import com.campus.market.dto.OrderCreateDTO;
import com.campus.market.dto.ReviewCreateDTO;
import com.campus.market.entity.OrderInfo;
import com.campus.market.security.LoginUser;
import com.campus.market.security.UserContext;
import com.campus.market.service.OrderService;
import com.campus.market.service.ReviewService;
import com.campus.market.vo.OrderDetailVO;
import com.campus.market.vo.OrderListVO;
import com.campus.market.vo.ReviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/**
 * 订单接口（PRD ORD-01~07）：下单/确认/拒绝/取消/完成/列表/详情/互评。
 * 除评价列表（ReviewController）外均需登录；创建订单的认证/受限校验在 service 层二次校验（PRD §9.2）。
 */
@Tag(name = "交易-订单")
@Validated
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final ReviewService reviewService;
    private final Environment environment;

    @Operation(summary = "下单（ORD-01，创建 SALE 订单并锁定商品）")
    @PostMapping
    public Result<OrderDetailVO> create(@Valid @RequestBody OrderCreateDTO dto) {
        LoginUser user = requireFrontUser();
        OrderInfo order = orderService.createSaleOrder(user, dto);
        return Result.ok(orderService.detail(order.getId(), user.getUserId()));
    }

    @Operation(summary = "我的订单分页（ORD-05）：role=buyer|seller + 可选状态筛选")
    @GetMapping
    public Result<PageResult<OrderListVO>> page(
            @RequestParam String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") long pageNum,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") long pageSize) {
        return Result.ok(orderService.pageMyOrders(requireFrontUser().getUserId(), role, status, pageNum, pageSize));
    }

    @Operation(summary = "订单详情（ORD-05，仅订单双方）")
    @GetMapping("/{id}")
    public Result<OrderDetailVO> detail(@PathVariable Long id) {
        return Result.ok(orderService.detail(id, requireFrontUser().getUserId()));
    }

    @Operation(summary = "卖家确认出售（ORD-02：待确认→待面交）")
    @PostMapping("/{id}/confirm")
    public Result<OrderDetailVO> confirm(@PathVariable Long id) {
        return Result.ok(orderService.confirm(id, requireFrontUser()));
    }

    @Operation(summary = "卖家拒绝（ORD-02：取消订单并解锁商品）")
    @PostMapping("/{id}/reject")
    public Result<OrderDetailVO> reject(@PathVariable Long id, @RequestBody(required = false) OrderActionDTO dto) {
        return Result.ok(orderService.reject(id, requireFrontUser(), dto == null ? null : dto.getReason()));
    }

    @Operation(summary = "买家取消（ORD-03：待确认/待面交可取消并解锁商品）")
    @PostMapping("/{id}/cancel")
    public Result<OrderDetailVO> cancel(@PathVariable Long id, @RequestBody(required = false) OrderActionDTO dto) {
        return Result.ok(orderService.cancel(id, requireFrontUser(), dto == null ? null : dto.getReason()));
    }

    @Operation(summary = "确认完成（ORD-04：SALE/PURCHASE 卖家单确认；SWAP 双方各自确认）")
    @PostMapping("/{id}/complete")
    public Result<OrderDetailVO> complete(@PathVariable Long id) {
        return Result.ok(orderService.complete(id, requireFrontUser()));
    }

    @Operation(summary = "交易互评（ORD-06：完成后 7 天内，每单每人一次）")
    @PostMapping("/{id}/review")
    public Result<ReviewVO> review(@PathVariable Long id, @Valid @RequestBody ReviewCreateDTO dto) {
        return Result.ok(reviewService.create(id, requireFrontUser(), dto));
    }

    @Operation(summary = "【dev】手动触发超时订单扫描（ORD-03 验证手段，仅 dev profile 可用）")
    @PostMapping("/timeout-scan/trigger")
    public Result<Integer> triggerTimeoutScan() {
        requireFrontUser();
        if (!Arrays.asList(environment.getActiveProfiles()).contains("dev")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅开发环境可手动触发超时扫描");
        }
        return Result.ok(orderService.autoCancelTimeoutOrders());
    }

    /** 订单操作仅限前台用户主体 */
    private LoginUser requireFrontUser() {
        LoginUser user = UserContext.requireLogin();
        if (user.getUserType() != LoginUser.UserType.USER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }
}
