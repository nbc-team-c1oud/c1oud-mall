package nbc.c1oud_mall.payment.application;

import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.payment.application.dto.PaymentConfirmationResult;
import nbc.c1oud_mall.payment.application.dto.PortOnePaymentInfo;
import nbc.c1oud_mall.payment.application.dto.PortOnePaymentStatus;
import nbc.c1oud_mall.payment.application.dto.command.PaymentConfirmationCommand;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.domain.PaymentStatus;
import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentConfirmationServiceIntegrationTest {

    @Autowired
    private PaymentConfirmationService paymentConfirmationService;

    @Autowired
    private PaymentJpaRepository paymentRepository;

    @MockitoBean
    private PortOnePaymentQueryPort portOnePaymentQueryPort;

    @MockitoBean
    private PortOnePaymentCancelPort portOnePaymentCancelPort;

    @AfterEach
    void cleanup() {
        paymentRepository.deleteAll();
    }

    @Test
    @DisplayName("정상 확정: DB에 Payment.status=COMPLETED + pgTxId 영속 (mock 서비스는 log만)")
    void confirm_persists_completed_state() {
        Payment saved = paymentRepository.saveAndFlush(
                Payment.of(1L, 100L, 10_000L, 9_000L, 1_000L));
        String portoneId = saved.getPortonePaymentId();

        PortOnePaymentInfo info = new PortOnePaymentInfo(
                portoneId, PortOnePaymentStatus.PAID, 9_000L, "TOSSPAYMENTS", "pg-tx-int-001");
        given(portOnePaymentQueryPort.query(portoneId)).willReturn(info);

        PaymentConfirmationResult result = paymentConfirmationService.confirm(
                new PaymentConfirmationCommand(portoneId, 100L, 1L));

        assertThat(result.alreadyCompleted()).isFalse();
        Payment fromDb = paymentRepository.findById(saved.getId()).orElseThrow();
        assertThat(fromDb.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(fromDb.getPgTxId()).isEqualTo("pg-tx-int-001");
        assertThat(fromDb.getConfirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("멱등: 이미 COMPLETED → DB 변경 없이 alreadyCompleted=true 반환")
    void confirm_idempotent_when_already_completed() {
        Payment payment = paymentRepository.saveAndFlush(
                Payment.of(2L, 200L, 5_000L, 5_000L, 0L));
        payment.markCompleted("pg-tx-existing", 0L, LocalDateTime.of(2026, 1, 1, 0, 0));
        paymentRepository.saveAndFlush(payment);

        String portoneId = payment.getPortonePaymentId();
        PortOnePaymentInfo info = new PortOnePaymentInfo(
                portoneId, PortOnePaymentStatus.PAID, 5_000L, "TOSSPAYMENTS", "pg-tx-new-attempt");
        given(portOnePaymentQueryPort.query(portoneId)).willReturn(info);

        PaymentConfirmationResult result = paymentConfirmationService.confirm(
                new PaymentConfirmationCommand(portoneId, 200L, 2L));

        assertThat(result.alreadyCompleted()).isTrue();
        Payment fromDb = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(fromDb.getPgTxId()).isEqualTo("pg-tx-existing");
        assertThat(fromDb.getConfirmedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Test
    @DisplayName("금액 불일치(PM001) 보상: 메인 TX 롤백되지만 REQUIRES_NEW 보상은 commit → FAILED 영속, PortOne cancel 호출")
    void compensation_persists_failed_status_via_requires_new() {
        Payment saved = paymentRepository.saveAndFlush(
                Payment.of(3L, 300L, 10_000L, 9_000L, 1_000L));
        String portoneId = saved.getPortonePaymentId();

        // PortOne은 9_000 보고했어야 하지만 위조된 8_999 시뮬레이션
        PortOnePaymentInfo info = new PortOnePaymentInfo(
                portoneId, PortOnePaymentStatus.PAID, 8_999L, "TOSSPAYMENTS", "pg-tx-mismatch");
        given(portOnePaymentQueryPort.query(portoneId)).willReturn(info);

        assertThatThrownBy(() -> paymentConfirmationService.confirm(
                new PaymentConfirmationCommand(portoneId, 300L, 3L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        Payment fromDb = paymentRepository.findById(saved.getId()).orElseThrow();
        assertThat(fromDb.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(fromDb.getPgTxId()).isNull();
        assertThat(fromDb.getConfirmedAt()).isNull();

        verify(portOnePaymentCancelPort).cancel(Mockito.eq(portoneId), Mockito.anyString());
    }
}
