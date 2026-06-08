package nbc.c1oud_mall.payment.application;

import nbc.c1oud_mall.payment.application.dto.PortOnePaymentInfo;

public interface PortOnePaymentQueryPort {

    PortOnePaymentInfo query(String portonePaymentId);
}
