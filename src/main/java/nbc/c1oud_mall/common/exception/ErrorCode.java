package nbc.c1oud_mall.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ─── 공통 ───
    INVALID_INPUT("C001", "잘못된 입력입니다.", HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR("C002", "서버 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    ACCESS_DENIED("C003", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    UNAUTHORIZED("C004", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED);

    // ─── 사용자 ───
    // (도메인 작업 시 추가)

    // ─── 주문 ───
    // (도메인 작업 시 추가)

    // ─── 결제 ───
    // (도메인 작업 시 추가)

    private final String code;
    private final String message;
    private final HttpStatus status;
}
