package nbc.c1oud_mall.payment.application;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.payment.application.dto.PaymentInitiationResult;
import nbc.c1oud_mall.payment.application.dto.command.PaymentInitiationCommand;
import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentInitiationServiceIntegrationTest {

    @Autowired
    private PaymentInitiationService paymentInitiationService;

    @Autowired
    private PaymentJpaRepository paymentRepository;

    @Autowired
    private RolledBackCaller rolledBackCaller;

    @AfterEach
    void cleanup() {
        paymentRepository.deleteAll();
    }

    @Test
    @DisplayName("정상 흐름: initiate 후 Payment가 영속되고 portonePaymentId로 조회 가능")
    void initiate_persists_payment() {
        PaymentInitiationCommand command =
                new PaymentInitiationCommand(1L, 100L, 10_000L, 9_000L, 1_000L);

        PaymentInitiationResult result = paymentInitiationService.initiate(command);

        assertThat(result.paymentId()).isNotNull();
        assertThat(result.portonePaymentId()).isNotBlank();
        assertThat(paymentRepository.findByPortonePaymentId(result.portonePaymentId()))
                .isPresent();
    }

    @Test
    @DisplayName("호출자 트랜잭션 롤백 시 Payment도 함께 롤백됨 (REQUIRED 전파)")
    void caller_rollback_propagates_to_payment() {
        long before = paymentRepository.count();
        PaymentInitiationCommand command =
                new PaymentInitiationCommand(2L, 200L, 5_000L, 5_000L, 0L);

        assertThatThrownBy(() -> rolledBackCaller.initiateAndThrow(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated caller failure");

        assertThat(paymentRepository.count()).isEqualTo(before);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        RolledBackCaller rolledBackCaller(PaymentInitiationUseCase useCase) {
            return new RolledBackCaller(useCase);
        }
    }

    @RequiredArgsConstructor
    static class RolledBackCaller {
        private final PaymentInitiationUseCase paymentInitiationUseCase;

        @Transactional
        public void initiateAndThrow(PaymentInitiationCommand command) {
            paymentInitiationUseCase.initiate(command);
            throw new RuntimeException("simulated caller failure");
        }
    }
}
