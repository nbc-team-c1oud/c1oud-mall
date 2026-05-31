package nbc.c1oud_mall.payment.domain;

import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static final Long ORDER_ID = 1L;
    private static final Long USER_ID = 100L;

    @Nested
    @DisplayName("Payment.of 정상 생성")
    class Create {

        @Test
        @DisplayName("PENDING 상태로 생성되고 portonePaymentId가 채번된다")
        void of_creates_pending_payment_with_portone_id() {
            Payment payment = Payment.of(ORDER_ID, USER_ID, 10_000L, 9_000L, 1_000L);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getPortonePaymentId()).isNotBlank();
            assertThat(payment.getId()).isNull();
            assertThat(payment.getPointEarnedAmount()).isZero();
            assertThat(payment.getConfirmedAt()).isNull();
            assertThat(payment.getPgTxId()).isNull();
            assertThat(payment.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(payment.getUserId()).isEqualTo(USER_ID);
            assertThat(payment.getBreakdown()).isEqualTo(
                    new PaymentBreakdown(10_000L, 9_000L, 1_000L));
        }

        @Test
        @DisplayName("portonePaymentId는 UUID v4 형식이다")
        void portone_payment_id_is_uuid_v4() {
            Payment payment = Payment.of(ORDER_ID, USER_ID, 5_000L, 5_000L, 0L);

            UUID parsed = UUID.fromString(payment.getPortonePaymentId());
            assertThat(parsed.version()).isEqualTo(4);
        }

        @Test
        @DisplayName("전액 포인트 결제도 정상 생성된다")
        void all_points() {
            Payment payment = Payment.of(ORDER_ID, USER_ID, 5_000L, 0L, 5_000L);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getBreakdown().getPgAmount()).isZero();
            assertThat(payment.getBreakdown().getPointUsedAmount()).isEqualTo(5_000L);
        }

        @Test
        @DisplayName("전액 PG 결제도 정상 생성된다")
        void all_pg() {
            Payment payment = Payment.of(ORDER_ID, USER_ID, 5_000L, 5_000L, 0L);

            assertThat(payment.getBreakdown().getPgAmount()).isEqualTo(5_000L);
            assertThat(payment.getBreakdown().getPointUsedAmount()).isZero();
        }

        @Test
        @DisplayName("여러 번 생성해도 portonePaymentId는 전역 유일하다")
        void portone_payment_id_is_globally_unique() {
            Set<String> ids = new HashSet<>();
            IntStream.range(0, 1_000).forEach(i ->
                    ids.add(Payment.of(ORDER_ID, USER_ID, 1_000L, 1_000L, 0L).getPortonePaymentId()));

            assertThat(ids).hasSize(1_000);
        }
    }

    @Nested
    @DisplayName("Payment.of 불변식 위반")
    class InvariantViolation {

        @Test
        @DisplayName("totalAmount ≠ pgAmount + pointUsedAmount 이면 PM001")
        void amount_mismatch_throws_pm001() {
            assertThatThrownBy(() -> Payment.of(ORDER_ID, USER_ID, 10_000L, 5_000L, 4_000L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        @Test
        @DisplayName("pgAmount 음수면 PM003")
        void negative_pg_amount_throws_pm003() {
            assertThatThrownBy(() -> Payment.of(ORDER_ID, USER_ID, 0L, -1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_INVALID_AMOUNT);
        }

        @Test
        @DisplayName("totalAmount 음수면 PM003")
        void negative_total_amount_throws_pm003() {
            assertThatThrownBy(() -> Payment.of(ORDER_ID, USER_ID, -1L, 0L, 0L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_INVALID_AMOUNT);
        }

        @Test
        @DisplayName("pointUsedAmount 음수면 PM003")
        void negative_point_used_throws_pm003() {
            assertThatThrownBy(() -> Payment.of(ORDER_ID, USER_ID, 0L, 1L, -1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_INVALID_AMOUNT);
        }
    }

    @Nested
    @DisplayName("Payment.rehydrate")
    class Rehydrate {

        @Test
        @DisplayName("저장된 데이터로부터 모든 필드를 복원한다")
        void rehydrate_restores_fields() {
            LocalDateTime confirmedAt = LocalDateTime.of(2026, 6, 1, 12, 30);
            Payment payment = Payment.rehydrate(
                    42L,
                    "11111111-2222-4333-8444-555555555555",
                    ORDER_ID,
                    USER_ID,
                    10_000L, 9_000L, 1_000L,
                    90L,
                    PaymentStatus.COMPLETED,
                    confirmedAt,
                    "pg-tx-001"
            );

            assertThat(payment.getId()).isEqualTo(42L);
            assertThat(payment.getPortonePaymentId()).isEqualTo("11111111-2222-4333-8444-555555555555");
            assertThat(payment.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(payment.getUserId()).isEqualTo(USER_ID);
            assertThat(payment.getBreakdown()).isEqualTo(new PaymentBreakdown(10_000L, 9_000L, 1_000L));
            assertThat(payment.getPointEarnedAmount()).isEqualTo(90L);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(payment.getConfirmedAt()).isEqualTo(confirmedAt);
            assertThat(payment.getPgTxId()).isEqualTo("pg-tx-001");
        }
    }
}
