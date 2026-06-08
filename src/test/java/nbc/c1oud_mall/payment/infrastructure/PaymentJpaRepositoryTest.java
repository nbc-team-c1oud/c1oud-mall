package nbc.c1oud_mall.payment.infrastructure;

import nbc.c1oud_mall.common.config.JpaConfig;
import nbc.c1oud_mall.common.config.QuerydslConfig;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase
@Import({JpaConfig.class, QuerydslConfig.class})
class PaymentJpaRepositoryTest {

    @Autowired
    private PaymentJpaRepository jpaRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("save 시 id가 채번되고 BaseEntity audit 필드가 자동 세팅된다")
    void save_assigns_id_and_audit_fields() {
        Payment saved = jpaRepository.save(Payment.of(1L, 100L, 10_000L, 9_000L, 1_000L));

        em.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("findByPortonePaymentId — 존재 시 동일 엔티티 반환")
    void find_by_portone_payment_id_present() {
        Payment saved = jpaRepository.save(Payment.of(1L, 100L, 10_000L, 9_000L, 1_000L));
        String portonePaymentId = saved.getPortonePaymentId();
        em.flush();
        em.clear();

        Optional<Payment> found = jpaRepository.findByPortonePaymentId(portonePaymentId);

        assertThat(found).isPresent();
        assertThat(found.get().getPortonePaymentId()).isEqualTo(portonePaymentId);
        assertThat(found.get().getBreakdown().getTotalAmount()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("findByPortonePaymentId — 미존재 시 empty")
    void find_by_portone_payment_id_empty() {
        Optional<Payment> found =
                jpaRepository.findByPortonePaymentId(UUID.randomUUID().toString());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("동일 portonePaymentId 중복 저장 시 UNIQUE 제약 위반으로 DataIntegrityViolationException")
    void duplicate_portone_payment_id_violates_unique_constraint() {
        Payment first = Payment.of(1L, 100L, 10_000L, 9_000L, 1_000L);
        String duplicateId = first.getPortonePaymentId();
        jpaRepository.save(first);
        em.flush();

        Payment second = Payment.rehydrate(
                null, duplicateId, 2L, 200L,
                5_000L, 5_000L, 0L, 0L,
                PaymentStatus.PENDING, null, null);

        assertThatThrownBy(() -> {
            jpaRepository.save(second);
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
