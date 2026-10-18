package com.campus.market.service;

import com.campus.market.vo.UploadVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片上传服务（PRD §9.1 / GDS-01）：魔数白名单校验、UUID 重命名、Graphics2D 缩略图
 */
public interface UploadService {

    /**
     * 保存单张商品图片（原图 + 缩略图）。
     *
     * @param file 上传文件，jpg/png/webp，≤5MB
     * @return 原图与缩略图 URL（/upload/{yyyyMM}/xxx）
     */
    UploadVO saveImage(MultipartFile file);
}
