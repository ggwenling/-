package com.campus.market.controller;

import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.api.PageResult;
import com.campus.market.common.api.Result;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.dto.FavoriteAddDTO;
import com.campus.market.security.LoginUser;
import com.campus.market.security.UserContext;
import com.campus.market.service.FavoriteService;
import com.campus.market.vo.FavoriteVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 收藏接口（PRD GDS-06）：收藏/取消与 favorite_count ±1 同事务，重复请求幂等；均需登录。
 */
@Tag(name = "商品-收藏")
@Validated
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "收藏商品（幂等）")
    @PostMapping
    public Result<Void> add(@Valid @RequestBody FavoriteAddDTO dto) {
        favoriteService.add(requireFrontUser().getUserId(), dto.getGoodsId());
        return Result.ok();
    }

    @Operation(summary = "取消收藏（幂等）")
    @DeleteMapping("/{goodsId}")
    public Result<Void> remove(@PathVariable Long goodsId) {
        favoriteService.remove(requireFrontUser().getUserId(), goodsId);
        return Result.ok();
    }

    @Operation(summary = "我的收藏分页")
    @GetMapping("/my")
    public Result<PageResult<FavoriteVO>> my(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") long pageNum,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 100, message = "每页条数最大为 100") long pageSize) {
        return Result.ok(favoriteService.pageMy(requireFrontUser().getUserId(), pageNum, pageSize));
    }

    /** 收藏仅限前台用户主体 */
    private LoginUser requireFrontUser() {
        LoginUser user = UserContext.requireLogin();
        if (user.getUserType() != LoginUser.UserType.USER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }
}
