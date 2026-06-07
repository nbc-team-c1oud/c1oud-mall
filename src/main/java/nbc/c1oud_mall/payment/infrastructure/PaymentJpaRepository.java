package nbc.c1oud_mall.payment.infrastructure;

import jakarta.persistence.LockModeType;
import nbc.c1oud_mall.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPortonePaymentId(String portonePaymentId);

    Optional<Payment> findByOrderId(Long orderId);

    List<Payment> findByOrderIdIn(List<Long> orderIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") Long orderId);
}
