package nbc.c1oud_mall.payment.infrastructure;

import nbc.c1oud_mall.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPortonePaymentId(String portonePaymentId);
}
