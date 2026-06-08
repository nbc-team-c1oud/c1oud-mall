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
    INVALID_PRICE("PRD003", "가격은 0 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
    INVALID_STOCK("PRD004", "재고는 0 이상이어야 합니다.", HttpStatus.BAD_REQUEST),

    // ─── 사용자 ───
    EMAIL_DUPLICATE("U001", "이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT),
    INVALID_CREDENTIALS("U002", "이메일 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED),
    USER_NOT_FOUND("U003", "존재하지 않는 사용자입니다.", HttpStatus.NOT_FOUND),
    INVALID_TOKEN("U005", "유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("U006", "만료된 토큰입니다.", HttpStatus.UNAUTHORIZED),
    ALREADY_ADMIN("U007", "이미 관리자 권한을 가진 사용자입니다.", HttpStatus.CONFLICT),

    // ─── 장바구니 ───
    CART_ITEM_NOT_FOUND("CRT001", "장바구니 상품을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    CART_ACCESS_DENIED("CRT002", "해당 장바구니에 대한 접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    CART_EMPTY("CT003", "장바구니가 비어있습니다.", HttpStatus.BAD_REQUEST),
    INVALID_QUANTITY("CT004", "수량은 1 이상이어야 합니다.", HttpStatus.BAD_REQUEST),

    // ─── 주문 ───
    ORDER_NOT_FOUND("OD001", "주문을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INVALID_ORDER_STATUS("OD002", "유효하지 않은 주문 상태 변경입니다.", HttpStatus.BAD_REQUEST),

    // ─── 포인트 ───
    POINT_AMOUNT_INVALID("PT001", "포인트 금액이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    POINT_INSUFFICIENT("PT002", "포인트 잔액이 부족합니다.", HttpStatus.BAD_REQUEST),

    // ─── 결제 ───
    PAYMENT_AMOUNT_MISMATCH("PM001", "결제 금액이 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_DUPLICATE_PAYMENT_ID("PM002", "결제 ID 채번 충돌이 발생했습니다.", HttpStatus.CONFLICT),
    PAYMENT_INVALID_AMOUNT("PM003", "결제 금액이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    PORTONE_QUERY_FAILED("PM004", "PortOne 조회에 실패했습니다.", HttpStatus.BAD_GATEWAY),
    PORTONE_RESPONSE_INVALID("PM005", "PortOne 응답이 유효하지 않습니다.", HttpStatus.BAD_GATEWAY),
    PAYMENT_AUTHORIZATION_FAILED("PM006", "결제 소유권 검증에 실패했습니다.", HttpStatus.FORBIDDEN),
    PORTONE_PAYMENT_NOT_PAID("PM007", "PortOne 결제 상태가 PAID가 아닙니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_NOT_FOUND("PM008", "결제를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    PORTONE_CANCEL_FAILED("PM009", "PortOne 결제 취소 호출에 실패했습니다.", HttpStatus.BAD_GATEWAY),
    PAYMENT_ORDER_MISMATCH("PM010", "결제와 주문 정보가 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_INVALID_STATUS("PM011", "유효하지 않은 결제 상태 변경입니다.", HttpStatus.BAD_REQUEST),

    // ─── 환불 ───
    REFUND_QUANTITY_EXCEEDED("RF001", "잔여 환불 가능 수량을 초과했습니다.", HttpStatus.CONFLICT),
    REFUND_NOT_REFUNDABLE_STATE("RF002", "환불할 수 없는 결제 상태입니다.", HttpStatus.CONFLICT),
    REFUND_OWNERSHIP_FAILED("RF003", "본인 소유의 결제만 환불할 수 있습니다.", HttpStatus.FORBIDDEN);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
