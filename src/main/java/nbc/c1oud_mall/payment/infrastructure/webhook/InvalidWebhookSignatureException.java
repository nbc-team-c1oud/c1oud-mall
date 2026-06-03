package nbc.c1oud_mall.payment.infrastructure.webhook;

/**
 * 웹훅 서명 검증 실패 시 발생.
 * 인프라 레이어 내부에서만 사용되며, 필터가 받아서 401 응답으로 변환한다.
 * 외부에 노출되는 메시지는 통일된 형태로 마스킹되어야 한다.
 */
public class InvalidWebhookSignatureException extends RuntimeException {

    public InvalidWebhookSignatureException(String message) {
        super(message);
    }

    public InvalidWebhookSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
