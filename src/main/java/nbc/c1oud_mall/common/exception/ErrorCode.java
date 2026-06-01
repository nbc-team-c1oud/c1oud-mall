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
    UNAUTHORIZED("C004", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),

    // ─── 상품 ───
    PRODUCT_NOT_FOUND("PRD001", "존재하지 않는 상품입니다.", HttpStatus.NOT_FOUND),
    INSUFFICIENT_STOCK("PRD002", "요청 상품 재고가 없습니다.", HttpStatus.BAD_REQUEST),

    // ─── 사용자 ───
    // (도메인 작업 시 추가)

    // ─── 주문 ───
    // (도메인 작업 시 추가)

    // ─── 결제 ───
    PAYMENT_AMOUNT_MISMATCH("PM001", "결제 금액이 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_DUPLICATE_PAYMENT_ID("PM002", "결제 ID 채번 충돌이 발생했습니다.", HttpStatus.CONFLICT),
    PAYMENT_INVALID_AMOUNT("PM003", "결제 금액이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    PORTONE_QUERY_FAILED("PM004", "PortOne 조회에 실패했습니다.", HttpStatus.BAD_GATEWAY),
    PORTONE_RESPONSE_INVALID("PM005", "PortOne 응답이 유효하지 않습니다.", HttpStatus.BAD_GATEWAY);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
