package nbc.c1oud_mall.refund.domain;

/**
 * 단일 환불 아이템 입력 record (도메인 팩토리 파라미터).
 *
 * remainingRefundableQuantity는 application service가
 * (OrderItem 원 수량 - RefundJpaRepository.sumRefundedQuantity)로 미리 계산해 주입한다.
 * Refund.of(...)가 이 값을 사용해 RF001(잔여 수량 초과) 불변식을 검증한다.
 */
public record RefundItemRequest(
        Long orderItemId,
        int quantity,
        int remainingRefundableQuantity,
        long priceSnapshotAtPayment
) {
}
