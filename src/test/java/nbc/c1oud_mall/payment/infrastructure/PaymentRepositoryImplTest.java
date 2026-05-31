package nbc.c1oud_mall.payment.infrastructure;

import nbc.c1oud_mall.common.config.JpaConfig;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase
@Import({JpaConfig.class, PaymentRepositoryImpl.class})
class PaymentRepositoryImplTest {

    @Autowired
    private PaymentRepositoryImpl paymentRepository;

    @Test
    @DisplayName("save 후 도메인 Payment가 반환되며 id가 채번된다")
    void save_returns_persisted_domain() {
        Payment payment = Payment.of(1L, 100L, 10_000L, 9_000L, 1_000L);

        Payment saved = paymentRepository.save(payment);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPortonePaymentId()).isEqualTo(payment.getPortonePaymentId());
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getBreakdown()).isEqualTo(payment.getBreakdown());
    }

    @Test
    @DisplayName("findByPortonePaymentId — 저장된 Payment를 도메인으로 복원해 반환")
    void find_by_portone_payment_id_returns_domain() {
        Payment payment = Payment.of(1L, 100L, 10_000L, 9_000L, 1_000L);
        paymentRepository.save(payment);

        Optional<Payment> found =
                paymentRepository.findByPortonePaymentId(payment.getPortonePaymentId());

        assertThat(found).isPresent();
        assertThat(found.get().getPortonePaymentId()).isEqualTo(payment.getPortonePaymentId());
        assertThat(found.get().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("findByPortonePaymentId — 미존재 시 empty")
    void find_by_portone_payment_id_empty() {
        Optional<Payment> found =
                paymentRepository.findByPortonePaymentId("non-existent-id");

        assertThat(found).isEmpty();
    }
}
