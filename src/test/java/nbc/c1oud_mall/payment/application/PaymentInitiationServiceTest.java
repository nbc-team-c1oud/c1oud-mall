package nbc.c1oud_mall.payment.application;

import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.payment.application.dto.PaymentInitiationResult;
import nbc.c1oud_mall.payment.application.dto.command.PaymentInitiationCommand;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.payment.domain.PaymentStatus;
import nbc.c1oud_mall.payment.infrastructure.PaymentJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentInitiationServiceTest {

    @Mock
    private PaymentJpaRepository paymentRepository;

    @InjectMocks
    private PaymentInitiationService service;

    @Test
    @DisplayName("정상: command로 Payment를 생성·저장 후 paymentId·portonePaymentId가 채워진 Result 반환")
    void initiate_returns_result_with_persisted_ids() {
        PaymentInitiationCommand command =
                new PaymentInitiationCommand(1L, 100L, 10_000L, 9_000L, 1_000L);

        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment input = invocation.getArgument(0);
                    return Payment.rehydrate(
                            42L,
                            input.getPortonePaymentId(),
                            input.getOrderId(),
                            input.getUserId(),
                            input.getBreakdown().getTotalAmount(),
                            input.getBreakdown().getPgAmount(),
                            input.getBreakdown().getPointUsedAmount(),
                            input.getPointEarnedAmount(),
                            input.getStatus(),
                            input.getConfirmedAt(),
                            input.getPgTxId()
                    );
                });

        PaymentInitiationResult result = service.initiate(command);

        assertThat(result.paymentId()).isEqualTo(42L);
        assertThat(result.portonePaymentId()).isNotBlank();

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(captor.capture());
        Payment captured = captor.getValue();
        assertThat(captured.getOrderId()).isEqualTo(1L);
        assertThat(captured.getUserId()).isEqualTo(100L);
        assertThat(captured.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(captured.getBreakdown().getTotalAmount()).isEqualTo(10_000L);
        assertThat(captured.getBreakdown().getPgAmount()).isEqualTo(9_000L);
        assertThat(captured.getBreakdown().getPointUsedAmount()).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("금액 불일치(PM001): Payment.of 단계에서 BusinessException, save 미호출")
    void amount_mismatch_throws_pm001_without_save() {
        PaymentInitiationCommand command =
                new PaymentInitiationCommand(1L, 100L, 10_000L, 5_000L, 4_000L);

        assertThatThrownBy(() -> service.initiate(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("음수 금액(PM003): Payment.of 단계에서 BusinessException, save 미호출")
    void negative_amount_throws_pm003_without_save() {
        PaymentInitiationCommand command =
                new PaymentInitiationCommand(1L, 100L, 0L, 1L, -1L);

        assertThatThrownBy(() -> service.initiate(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_INVALID_AMOUNT);

        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UNIQUE 충돌(PM002): saveAndFlush의 DataIntegrityViolationException → BusinessException 번역")
    void unique_conflict_translated_to_pm002() {
        PaymentInitiationCommand command =
                new PaymentInitiationCommand(1L, 100L, 10_000L, 9_000L, 1_000L);

        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenThrow(new DataIntegrityViolationException("uk_payments_portone_payment_id"));

        assertThatThrownBy(() -> service.initiate(command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_DUPLICATE_PAYMENT_ID);
    }
}
