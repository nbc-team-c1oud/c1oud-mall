package nbc.c1oud_mall.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(
            BusinessException ex, HttpServletRequest req) {
        ErrorCode code = ex.getErrorCode();
        log.warn("BusinessException: code={}, detail={}", code.getCode(), ex.getDetail());
        return ResponseEntity.status(code.getStatus())
                .body(new ErrorResponse(
                        code.getCode(),
                        ex.getMessage(),
                        req.getRequestURI(),
                        OffsetDateTime.now()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        ErrorCode code = ErrorCode.INVALID_INPUT;
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(code.getStatus())
                .body(new ErrorResponse(
                        code.getCode(),
                        code.getMessage() + " — " + detail,
                        req.getRequestURI(),
                        OffsetDateTime.now()
                ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest req) {
        ErrorCode code = ErrorCode.ACCESS_DENIED;
        log.warn("AccessDenied: uri={}, reason={}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(code.getStatus())
                .body(new ErrorResponse(
                        code.getCode(),
                        code.getMessage(),
                        req.getRequestURI(),
                        OffsetDateTime.now()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(
            Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception", ex);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(new ErrorResponse(
                        code.getCode(),
                        code.getMessage(),
                        req.getRequestURI(),
                        OffsetDateTime.now()
                ));
    }
}
