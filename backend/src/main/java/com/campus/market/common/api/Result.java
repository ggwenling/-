package com.campus.market.common.api;

import lombok.Getter;
import lombok.Setter;

/**
 * 统一 REST 响应包装：所有接口返回 { code, message, data }，code=0 表示成功
 */
@Getter
@Setter
public class Result<T> {

    /** 业务状态码：0 成功，其余见 {@link ErrorCode} */
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.code = ErrorCode.SUCCESS.getCode();
        result.message = ErrorCode.SUCCESS.getMessage();
        result.data = data;
        return result;
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.getMessage());
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        Result<T> result = new Result<>();
        result.code = errorCode.getCode();
        result.message = message;
        return result;
    }
}
