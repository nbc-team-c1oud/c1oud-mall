package nbc.c1oud_mall.payment.application;

public interface PortOnePaymentCancelPort {

    /**
     * PortOne 결제 취소 호출.
     *
     * @param portonePaymentId PortOne 결제 ID
     * @param amount           부분 취소 금액. null이면 전체 취소 (요청 본문에서 amount 키 제외)
     * @param reason           취소 사유
     * @param requestKey       PG 측 멱등키. null이면 PortOne이 paymentId 기반으로 dedup (요청 본문에서 requestKey 키 제외)
     */
    void cancel(String portonePaymentId, Long amount, String reason, String requestKey);
}
