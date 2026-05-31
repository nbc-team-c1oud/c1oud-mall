package nbc.c1oud_mall.payment.infrastructure;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.domain.PaymentRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    @Override
    public Payment save(Payment payment) {
        PaymentJpaEntity entity = PaymentJpaEntity.from(payment);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return jpaRepository.findById(id).map(PaymentJpaEntity::toDomain);
    }

    @Override
    public Optional<Payment> findByPortonePaymentId(String portonePaymentId) {
        return jpaRepository.findByPortonePaymentId(portonePaymentId).map(PaymentJpaEntity::toDomain);
    }
}
