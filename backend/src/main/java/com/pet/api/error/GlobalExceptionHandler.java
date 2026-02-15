package com.pet.api.error;

import com.pet.api.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<List<String>>> handleValidation(MethodArgumentNotValidException ex) {
    List<String> details = ex.getBindingResult().getFieldErrors().stream()
        .map(FieldError::getDefaultMessage)
        .collect(Collectors.toList());
    return ResponseEntity.badRequest()
        .body(new ApiResponse<>(ApiError.VALIDATION_FAILED.code(), ApiError.VALIDATION_FAILED.message(), details));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<List<String>>> handleConstraint(ConstraintViolationException ex) {
    List<String> details = ex.getConstraintViolations().stream()
        .map(v -> v.getMessage())
        .collect(Collectors.toList());
    return ResponseEntity.badRequest()
        .body(new ApiResponse<>(ApiError.VALIDATION_FAILED.code(), ApiError.VALIDATION_FAILED.message(), details));
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
    return ResponseEntity.status(ex.getStatus())
        .body(new ApiResponse<>(ex.getError().code(), ex.getError().message(), null));
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiResponse<Void>> handleStatus(ResponseStatusException ex) {
    String reason = ex.getReason() == null ? ApiError.INTERNAL_ERROR.message() : ex.getReason();
    int code = ex.getStatusCode().is4xxClientError() ? ApiError.VALIDATION_FAILED.code() : ApiError.INTERNAL_ERROR.code();
    return ResponseEntity.status(ex.getStatusCode()).body(new ApiResponse<>(code, reason, null));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiResponse<List<String>>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
    return ResponseEntity.badRequest()
        .body(new ApiResponse<>(ApiError.FILE_TOO_LARGE.code(), ApiError.FILE_TOO_LARGE.message(),
            List.of("图片过大，请上传 5MB 以内文件")));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ApiResponse<>(ApiError.INTERNAL_ERROR.code(), ApiError.INTERNAL_ERROR.message(), null));
  }
}
