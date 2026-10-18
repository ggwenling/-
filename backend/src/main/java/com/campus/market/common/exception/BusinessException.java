package com.campus.market.common.exception;

import com.campus.market.common.api.ErrorCode;
import lombok.Getter;

/**
 * 业务异常：service 层主动抛出，由 GlobalExceptionHandler 统一转为 Result 返回
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
