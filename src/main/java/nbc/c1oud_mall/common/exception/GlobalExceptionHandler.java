package nbc.c1oud_mall.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import nbc.c1oud_mall.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(
            BusinessException ex, HttpServletRequest req) {
        ErrorCode code = ex.getErrorCode();
        log.warn("BusinessException: code={}, detail={}, path={}",
                code.getCode(), ex.getDetail(), req.getRequestURI());
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        ErrorCode code = ErrorCode.INVALID_INPUT;
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation error: path={}, detail={}", req.getRequestURI(), detail);
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.getCode(), code.getMessage() + " — " + detail));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest req) {
        ErrorCode code = ErrorCode.ACCESS_DENIED;
        // 구체 사유는 로그에만, 외부 메시지는 enum의 통일된 문구 유지
        log.warn("AccessDenied: uri={}, reason={}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.getCode(), code.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(
            Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception at {}", req.getRequestURI(), ex);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.getCode(), code.getMessage()));
    }
}
