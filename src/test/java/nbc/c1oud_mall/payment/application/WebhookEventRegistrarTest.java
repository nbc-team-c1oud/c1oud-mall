package nbc.c1oud_mall.payment.application;

import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import nbc.c1oud_mall.payment.infrastructure.WebhookEventJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WebhookEventRegistrarTest {

    private static final String PORTONE_PAYMENT_ID = "we-test-portone-001";
    private static final String EVENT_TYPE = "Transaction.Paid";

    @Autowired
    private WebhookEventRegistrar webhookEventRegistrar;

    @Autowired
    private WebhookEventJpaRepository webhookEventJpaRepository;

    // PaymentConfirmationService가 두 UseCase를 모두 구현하므로 양쪽 목킹 필요
    @MockitoBean
    private PortOnePaymentQueryPort portOnePaymentQueryPort;

    @MockitoBean
    private PortOnePaymentCancelPort portOnePaymentCancelPort;

    @AfterEach
    void cleanup() {
        webhookEventJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("최초 등록 → true 반환 + DB에 WebhookEvent 저장")
    void first_registration_returns_true() {
        boolean result = webhookEventRegistrar.tryRegister(PORTONE_PAYMENT_ID, EVENT_TYPE);

        assertThat(result).isTrue();
        assertThat(webhookEventJpaRepository
                .existsByPortonePaymentIdAndEventType(PORTONE_PAYMENT_ID, EVENT_TYPE)).isTrue();
    }

    @Test
    @DisplayName("동일 portonePaymentId + eventType 중복 등록 → false 반환 (UNIQUE 위반 억제)")
    void duplicate_registration_returns_false() {
        webhookEventRegistrar.tryRegister(PORTONE_PAYMENT_ID, EVENT_TYPE);

        boolean result = webhookEventRegistrar.tryRegister(PORTONE_PAYMENT_ID, EVENT_TYPE);

        assertThat(result).isFalse();
        assertThat(webhookEventJpaRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("서로 다른 portonePaymentId → 각각 독립 등록 가능")
    void different_payment_ids_register_independently() {
        boolean first = webhookEventRegistrar.tryRegister("portone-a", EVENT_TYPE);
        boolean second = webhookEventRegistrar.tryRegister("portone-b", EVENT_TYPE);

        assertThat(first).isTrue();
        assertThat(second).isTrue();
        assertThat(webhookEventJpaRepository.findAll()).hasSize(2);
    }
}
