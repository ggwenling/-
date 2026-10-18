package com.campus.market.service.impl;

import com.campus.market.common.api.ErrorCode;
import com.campus.market.common.exception.BusinessException;
import com.campus.market.vo.UploadVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UploadServiceImpl 浅测（§9.1：后缀白名单 + 魔数校验 + 保存路径；缩略图解码失败降级原图 URL）。
 * PNG 魔数正确但内容非真实图片 → ImageIO.read 返回 null → thumbUrl 降级为原图（不阻断发布）。
 */
class UploadServiceImplTest {

    @TempDir
    Path tempDir;

    private UploadServiceImpl service() {
        return new UploadServiceImpl(tempDir.toString());
    }

    @Test
    void saveImage_nullFile_rejected() {
        assertThatThrownBy(() -> service().saveImage(null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void saveImage_emptyFile_rejected() {
        MockMultipartFile file = new MockMultipartFile("file", new byte[0]);
        assertThatThrownBy(() -> service().saveImage(file))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void saveImage_forbiddenExtension_rejected() {
        MockMultipartFile file = new MockMultipartFile("file", "evil.gif", "image/gif",
                new byte[]{1, 2, 3, 4});
        assertThatThrownBy(() -> service().saveImage(file))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void saveImage_magicNumberMismatch_rejected() {
        // 后缀 png 但内容非 PNG 魔数
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png",
                new byte[]{0x00, 0x01, 0x02, 0x03, 0x04});
        assertThatThrownBy(() -> service().saveImage(file))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void saveImage_pngWithValidMagic_savedWithDatePath() {
        // PNG 魔数头（内容非完整图片 → 缩略图降级原图 URL）
        byte[] pngHeader = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", pngHeader);

        UploadVO vo = service().saveImage(file);

        assertThat(vo.getUrl()).startsWith("/upload/").contains("/").endsWith(".png");
        assertThat(vo.getThumbUrl()).isEqualTo(vo.getUrl());   // 解码失败降级
    }
}
