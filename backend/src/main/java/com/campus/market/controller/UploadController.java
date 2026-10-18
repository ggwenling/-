package com.campus.market.controller;

import com.campus.market.common.api.Result;
import com.campus.market.security.LoginUser;
import com.campus.market.security.UserContext;
import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.service.UploadService;
import com.campus.market.vo.UploadVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片上传接口（PRD §9.1 / GDS-01 / ADM-07）：登录后可上传（前台 USER 与后台 ADMIN 主体均放行），
 * jpg/png/webp ≤5MB，返回 {url, thumbUrl}。
 * 上传文件无业务归属（仅落磁盘/静态映射），不存在主键空间越权面。
 */
@Tag(name = "商品-图片上传")
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @Operation(summary = "上传单张图片（multipart 字段名 file；前台用户与后台管理员均可）")
    @PostMapping
    public Result<UploadVO> upload(@RequestPart("file") MultipartFile file) {
        requireAuthenticated();
        return Result.ok(uploadService.saveImage(file));
    }

    /** 任意已登录主体（USER/ADMIN）均可上传；未登录拒绝 */
    private void requireAuthenticated() {
        LoginUser user = UserContext.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
