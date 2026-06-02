package nbc.c1oud_mall.payment.application;

public interface PortOnePaymentCancelPort {

    void cancel(String portonePaymentId, String reason);
}
