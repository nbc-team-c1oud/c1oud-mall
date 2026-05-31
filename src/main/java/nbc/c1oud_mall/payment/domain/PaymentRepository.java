package nbc.c1oud_mall.payment.domain;

import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByPortonePaymentId(String portonePaymentId);
}
