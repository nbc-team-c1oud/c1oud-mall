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

    @Nested
    @DisplayName("isCompleted")
    class IsCompleted {

        @Test
        @DisplayName("status COMPLETED → true")
        void completed_returns_true() {
            Payment payment = Payment.rehydrate(
                    1L, "p-1", ORDER_ID, USER_ID,
                    10_000L, 9_000L, 1_000L, 0L,
                    PaymentStatus.COMPLETED, LocalDateTime.now(), "pg-1");

            assertThat(payment.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("status PENDING → false")
        void pending_returns_false() {
            Payment payment = Payment.of(ORDER_ID, USER_ID, 10_000L, 9_000L, 1_000L);

            assertThat(payment.isCompleted()).isFalse();
        }

        @Test
        @DisplayName("status FAILED → false")
        void failed_returns_false() {
            Payment payment = Payment.rehydrate(
                    1L, "p-1", ORDER_ID, USER_ID,
                    10_000L, 9_000L, 1_000L, 0L,
                    PaymentStatus.FAILED, null, null);

            assertThat(payment.isCompleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("verifyOwnership")
    class VerifyOwnership {

        @Test
        @DisplayName("동일 userId → 정상 통과")
        void same_user_passes() {
            Payment payment = Payment.of(ORDER_ID, USER_ID, 10_000L, 9_000L, 1_000L);

            payment.verifyOwnership(USER_ID);
        }

        @Test
        @DisplayName("다른 userId → BusinessException(PM006)")
        void different_user_throws_pm006() {
            Payment payment = Payment.of(ORDER_ID, USER_ID, 10_000L, 9_000L, 1_000L);

            assertThatThrownBy(() -> payment.verifyOwnership(999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_AUTHORIZATION_FAILED);
        }

        @Test
        @DisplayName("null userId → BusinessException(PM006)")
        void null_user_throws_pm006() {
            Payment payment = Payment.of(ORDER_ID, USER_ID, 10_000L, 9_000L, 1_000L);

            assertThatThrownBy(() -> payment.verifyOwnership(null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_AUTHORIZATION_FAILED);
        }
    }

    @Nested
    @DisplayName("verifyOrderId")
    class VerifyOrderId {

        @Test
        @DisplayName("동일 orderId → 정상 통과")
        void same_order_passes() {
            Payment payment = Payment.of(ORDER_ID, USER_ID, 10_000L, 9_000L, 1_000L);

            payment.verifyOrderId(ORDER_ID);
        }

        @Test
        @DisplayName("다른 orderId → BusinessException(PM010)")
        void different_order_throws_pm010() {
            Payment payment = Payment.of(ORDER_ID, USER_ID, 10_000L, 9_000L, 1_000L);

            assertThatThrownBy(() -> payment.verifyOrderId(999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_ORDER_MISMATCH);
        }

        @Test
        @DisplayName("null orderId → BusinessException(PM010)")
        void null_order_throws_pm010() {
            Payment payment = Payment.of(ORDER_ID, USER_ID, 10_000L, 9_000L, 1_000L);

            assertThatThrownBy(() -> payment.verifyOrderId(null))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_ORDER_MISMATCH);
        }
    }

    @Nested
    @DisplayName("verifyPortOneStatus")
    class VerifyPortOneStatus {

        @Test
        @DisplayName("PAID → 정상 통과")
        void paid_passes() {
            Payment payment = Payment.of(ORDER_ID, USER_ID, 10_000L, 9_000L, 1_000L);

            payment.verifyPortOneStatus(nbc.c1oud_mall.payment.application.dto.PortOnePaymentStatus.PAID);
        }

        @Test
        @DisplayName("FAILED → BusinessException(PM007)")
        void failed_throws_pm007() {
            Payment payment = Payment.of(ORDER_ID, USER_ID, 10_000L, 9_000L, 1_000L);

            assertThatThrownBy(() -> payment.verifyPortOneStatus(
                    nbc.c1oud_mall.payment.application.dto.PortOnePaymentStatus.FAILED))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PORTONE_PAYMENT_NOT_PAID);
        }

        @Test
        @DisplayName("READY → BusinessException(PM007)")
        void ready_throws_pm007() {
            Payment payment = Payment.of(ORDER_ID, USER_ID, 10_000L, 9_000L, 1_000L);

            assertThatThrownBy(() -> payment.verifyPortOneStatus(
                    nbc.c1oud_mall.payment.application.dto.PortOnePaymentStatus.READY))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PORTONE_PAYMENT_NOT_PAID);
        }
    }

    @Nested
    @DisplayName("verifyAmount")
    class VerifyAmount {

        @Test
        @DisplayName("PortOne totalAmount == breakdown.pgAmount → 정상 통과")
        void matching_amount_passes() {
            // breakdown.pgAmount = 9_000L
            Payment payment = Payment.of(ORDER_ID, USER_ID, 10_000L, 9_000L, 1_000L);

            payment.verifyAmount(9_000L);
        }

        @Test
        @DisplayName("PortOne totalAmount != breakdown.pgAmount → BusinessException(PM001)")
        void mismatch_throws_pm001() {
            // breakdown.pgAmount = 9_000L, PortOne 8_999L (위변조 시뮬레이션)
            Payment payment = Payment.of(ORDER_ID, USER_ID, 10_000L, 9_000L, 1_000L);

            assertThatThrownBy(() -> payment.verifyAmount(8_999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }

    @Nested
    @DisplayName("markCompleted")
    class MarkCompleted {

        @Test
        @DisplayName("PENDING → COMPLETED 전이 + pgTxId·pointEarnedAmount·confirmedAt 세팅")
        void pending_transitions_to_completed() {
            Payment payment = Payment.of(ORDER_ID, USER_ID, 10_000L, 9_000L, 1_000L);
            LocalDateTime now = LocalDateTime.of(2026, 6, 1, 12, 0);

            payment.markCompleted("pg-tx-100", 90L, now);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(payment.getPgTxId()).isEqualTo("pg-tx-100");
            assertThat(payment.getPointEarnedAmount()).isEqualTo(90L);
            assertThat(payment.getConfirmedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("이미 COMPLETED인 결제 → IllegalStateException")
        void already_completed_throws() {
            Payment payment = Payment.rehydrate(
                    1L, "p-1", ORDER_ID, USER_ID,
                    10_000L, 9_000L, 1_000L, 90L,
                    PaymentStatus.COMPLETED, LocalDateTime.now(), "pg-existing");

            assertThatThrownBy(() -> payment.markCompleted("pg-new", 100L, LocalDateTime.now()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("FAILED 상태 → IllegalStateException")
        void failed_throws() {
            Payment payment = Payment.rehydrate(
                    1L, "p-1", ORDER_ID, USER_ID,
                    10_000L, 9_000L, 1_000L, 0L,
                    PaymentStatus.FAILED, null, null);

            assertThatThrownBy(() -> payment.markCompleted("pg-new", 0L, LocalDateTime.now()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("markFailed")
    class MarkFailed {

        @Test
        @DisplayName("PENDING → FAILED 전이")
        void pending_transitions_to_failed() {
            Payment payment = Payment.of(ORDER_ID, USER_ID, 10_000L, 9_000L, 1_000L);

            payment.markFailed("amount mismatch");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        @DisplayName("이미 COMPLETED인 결제 → IllegalStateException")
        void already_completed_throws() {
            Payment payment = Payment.rehydrate(
                    1L, "p-1", ORDER_ID, USER_ID,
                    10_000L, 9_000L, 1_000L, 90L,
                    PaymentStatus.COMPLETED, LocalDateTime.now(), "pg-existing");

            assertThatThrownBy(() -> payment.markFailed("late mismatch"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("이미 FAILED인 결제 → IllegalStateException")
        void already_failed_throws() {
            Payment payment = Payment.rehydrate(
                    1L, "p-1", ORDER_ID, USER_ID,
                    10_000L, 9_000L, 1_000L, 0L,
                    PaymentStatus.FAILED, null, null);

            assertThatThrownBy(() -> payment.markFailed("retry"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
