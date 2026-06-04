package nbc.c1oud_mall.payment.presentation.dto;

public record PortOneWebhookPayload(String type, PayloadData data) {

    public record PayloadData(String paymentId) {}

    public String portonePaymentId() {
        return (data != null) ? data.paymentId() : null;
    }
}
