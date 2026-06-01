package nbc.c1oud_mall.payment.application.dto;

public record PortOnePaymentInfo(
        String portonePaymentId,
        PortOnePaymentStatus status,
        long totalAmount,
        String pgProvider,
        String pgTxId
) {
}
