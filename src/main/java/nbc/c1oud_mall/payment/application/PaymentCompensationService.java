package nbc.c1oud_mall.payment.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCompensationService {

    private final PaymentCompensationTxOp txOp;
    private final PortOnePaymentCancelPort portOnePaymentCancelPort;

    public void compensate(String portonePaymentId, String reason) {
        txOp.compensateDb(portonePaymentId, reason);
        try {
            portOnePaymentCancelPort.cancel(portonePaymentId, reason);
        } catch (Exception ex) {
            log.error(
                    "[COMPENSATION] PortOne 취소 실패 — 운영 개입 필요. portonePaymentId={}, reason={}",
                    portonePaymentId, reason, ex);
        }
    }
}
