package nbc.c1oud_mall.payment.infrastructure;

import nbc.c1oud_mall.common.config.JpaConfig;
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
@Import(JpaConfig.class)
class PaymentJpaRepositoryTest {

    @Autowired
    private PaymentJpaRepository jpaRepository;

    @Autowired
    private TestEntityManager em;

    private PaymentJpaEntity newPendingEntity(String portonePaymentId) {
        return PaymentJpaEntity.from(
                nbc.c1oud_mall.payment.domain.Payment.rehydrate(
                        null,
                        portonePaymentId,
                        1L,
                        100L,
                        10_000L, 9_000L, 1_000L,
                        0L,
                        PaymentStatus.PENDING,
                        null,
                        null
                )
        );
    }

    @Test
    @DisplayName("save 시 id가 채번되고 BaseEntity audit 필드가 자동 세팅된다")
    void save_assigns_id_and_audit_fields() {
        PaymentJpaEntity saved = jpaRepository.save(newPendingEntity(UUID.randomUUID().toString()));

        em.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("findByPortonePaymentId — 존재 시 동일 엔티티 반환")
    void find_by_portone_payment_id_present() {
        String portonePaymentId = UUID.randomUUID().toString();
        jpaRepository.save(newPendingEntity(portonePaymentId));
        em.flush();
        em.clear();

        Optional<PaymentJpaEntity> found = jpaRepository.findByPortonePaymentId(portonePaymentId);

        assertThat(found).isPresent();
        assertThat(found.get().getPortonePaymentId()).isEqualTo(portonePaymentId);
    }

    @Test
    @DisplayName("findByPortonePaymentId — 미존재 시 empty")
    void find_by_portone_payment_id_empty() {
        Optional<PaymentJpaEntity> found =
                jpaRepository.findByPortonePaymentId(UUID.randomUUID().toString());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("동일 portonePaymentId 중복 저장 시 UNIQUE 제약 위반으로 DataIntegrityViolationException")
    void duplicate_portone_payment_id_violates_unique_constraint() {
        String duplicateId = UUID.randomUUID().toString();
        jpaRepository.save(newPendingEntity(duplicateId));
        em.flush();

        assertThatThrownBy(() -> {
            jpaRepository.save(newPendingEntity(duplicateId));
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
