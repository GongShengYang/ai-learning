package com.gsy.ai.common;

public enum ResultCode {

    SUCCESS(0, "success"),
    FAIL(500, "系统异常"),
    BAD_REQUEST(400, "请求参数错误"),
    NOT_FOUND(404, "资源不存在"),
    BUSINESS_ERROR(1001, "业务异常");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}