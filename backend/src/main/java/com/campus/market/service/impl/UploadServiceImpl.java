package com.campus.market.service.impl;

import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.service.UploadService;
import com.campus.market.vo.UploadVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 图片上传服务实现（PRD §9.1 / GDS-01）。
 * 校验链：非空 → 后缀白名单 → 魔数白名单（大小由 spring.servlet.multipart 全局限制 ≤5MB）→
 * UUID 重命名存 {upload-dir}/{yyyyMM}/ → JDK Graphics2D 生成缩略图（禁第三方图片库）。
 * webp 原图 JDK ImageIO 不支持解码，缩略图直接复用原图 URL（口径见接口文档）。
 */
@Slf4j
@Service
public class UploadServiceImpl implements UploadService {

    /** 允许的后缀（统一小写） */
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp");

    /** 缩略图最长边像素（4:3 卡片显示 2x 余量） */
    private static final int THUMB_MAX_SIDE = 400;

    private final String uploadDir;

    public UploadServiceImpl(@Value("${app.upload-dir}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public UploadVO saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择要上传的图片");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = extOf(original);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅支持 jpg/png/webp 格式图片");
        }
        checkMagicNumber(file, ext);

        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String normalExt = "jpeg".equals(ext) ? "jpg" : ext;
        String filename = uuid + "." + normalExt;
        String thumbName = uuid + "_thumb." + normalExt;

        Path dir = Paths.get(uploadDir, month).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            file.transferTo(target);

            String url = "/upload/" + month + "/" + filename;
            String thumbUrl = buildThumb(target, dir, month, thumbName, normalExt, url);
            log.info("图片上传成功：{}（缩略图 {}）", url, thumbUrl);
            UploadVO vo = new UploadVO();
            vo.setUrl(url);
            vo.setThumbUrl(thumbUrl);
            return vo;
        } catch (IOException e) {
            log.error("图片保存失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片保存失败，请稍后重试");
        }
    }

    /** 提取并规范化小写后缀 */
    private String extOf(String original) {
        int dot = original.lastIndexOf('.');
        if (dot < 0 || dot == original.length() - 1) {
            return "";
        }
        return original.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 魔数白名单：JPEG(FF D8 FF) / PNG(89 50 4E 47) / WEBP(RIFF....WEBP) */
    private void checkMagicNumber(MultipartFile file, String ext) {
        byte[] header = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(header, 0, header.length);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "图片读取失败，请重试");
        }
        if (read < 4) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "图片文件内容无效");
        }
        boolean ok = switch (ext) {
            case "jpg", "jpeg" -> (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
            case "png" -> (header[0] & 0xFF) == 0x89 && (header[1] & 0xFF) == 0x50
                    && (header[2] & 0xFF) == 0x4E && (header[3] & 0xFF) == 0x47;
            case "webp" -> header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && read >= 12 && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default -> false;
        };
        if (!ok) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "图片内容与格式不符（魔数校验未通过）");
        }
    }

    /**
     * Graphics2D 等比缩放生成缩略图（最长边 {@link #THUMB_MAX_SIDE}）。
     * webp 无法被 JDK 解码，返回原图 URL；解码/写盘失败时降级为原图 URL，不阻断发布流程。
     */
    private String buildThumb(Path source, Path dir, String month, String thumbName,
                              String ext, String originalUrl) throws IOException {
        if ("webp".equals(ext)) {
            return originalUrl;
        }
        BufferedImage sourceImage;
        try (InputStream in = Files.newInputStream(source)) {
            sourceImage = ImageIO.read(in);
        } catch (IOException e) {
            // 解码失败（如魔数正确但内容损坏的图片）降级为原图 URL，不阻断发布流程
            log.warn("缩略图解码失败，降级为原图 URL：{}（{}）", originalUrl, e.getMessage());
            return originalUrl;
        }
        if (sourceImage == null) {
            return originalUrl;
        }
        int w = sourceImage.getWidth();
        int h = sourceImage.getHeight();
        if (w <= 0 || h <= 0) {
            return originalUrl;
        }
        double scale = Math.min(1.0, (double) THUMB_MAX_SIDE / Math.max(w, h));
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));
        // JPEG 不支持透明通道：绘制到白底 RGB 画布；PNG 保留 ARGB
        boolean png = "png".equals(ext);
        BufferedImage thumb = new BufferedImage(tw, th,
                png ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumb.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (png) {
                g.drawImage(sourceImage, 0, 0, tw, th, null);
            } else {
                g.drawImage(sourceImage, 0, 0, tw, th, Color.WHITE, null);
            }
        } finally {
            g.dispose();
        }
        Path thumbPath = dir.resolve(thumbName);
        ImageIO.write(thumb, png ? "png" : "jpg", thumbPath.toFile());
        return "/upload/" + month + "/" + thumbName;
    }
}
