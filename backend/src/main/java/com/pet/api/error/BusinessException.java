package com.pet.api.error;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
  private final ApiError error;
  private final HttpStatus status;

  public BusinessException(ApiError error, HttpStatus status) {
    super(error.message());
    this.error = error;
    this.status = status;
  }

  public BusinessException(ApiError error) {
    this(error, HttpStatus.BAD_REQUEST);
  }

  public ApiError getError() {
    return error;
  }

  public HttpStatus getStatus() {
    return status;
  }
}
