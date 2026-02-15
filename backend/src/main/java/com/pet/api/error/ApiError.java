package com.pet.api.error;

public enum ApiError {
  VALIDATION_FAILED(1000, "参数校验失败"),
  UNAUTHORIZED(1001, "未登录或登录已过期"),
  FORBIDDEN(1002, "无权限访问"),

  USERNAME_EXISTS(2000, "用户名已存在"),
  INVALID_CREDENTIALS(2001, "用户名或密码错误"),
  USER_NOT_FOUND(2002, "用户不存在"),
  PET_NOT_FOUND(2003, "宠物不存在"),

  FILE_REQUIRED(3000, "请选择要上传的文件"),
  UPLOAD_FAILED(3001, "上传失败"),
  FILE_TOO_LARGE(3002, "上传文件过大"),

  INTERNAL_ERROR(9000, "服务器内部错误");

  private final int code;
  private final String message;

  ApiError(int code, String message) {
    this.code = code;
    this.message = message;
  }

  public int code() {
    return code;
  }

  public String message() {
    return message;
  }
}
