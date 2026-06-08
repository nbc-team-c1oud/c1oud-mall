package nbc.c1oud_mall.refund.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefundAmountCalculatorTest {

    private final RefundAmountCalculator calculator = new RefundAmountCalculator();

    private RefundablePayment payment(long totalAmount, long pgAmount, long pointUsedAmount) {
        return new RefundablePayment(1L, 100L, true, totalAmount, pgAmount, pointUsedAmount, 0L, "portone-calc-test");
    }

    private RefundablePayment paymentWithEarned(long totalAmount, long pgAmount, long pointUsedAmount, long pointEarnedAmount) {
        return new RefundablePayment(1L, 100L, true, totalAmount, pgAmount, pointUsedAmount, pointEarnedAmount, "portone-calc-test");
    }

    private List<RefundItemRequest> single(long priceSnapshot, int quantity) {
        return List.of(new RefundItemRequest(1L, quantity, quantity, priceSnapshot));
    }

    @Nested
    @DisplayName("결제 수단 단일 — PG 또는 포인트 전액 결제")
    class SingleMethod {

        @Test
        @DisplayName("PG 전액 결제: 환불 전액이 PG로 분배 (point=0)")
        void pg_only_returns_full_pg_refund() {
            RefundBreakdown result = calculator.calculate(
                    payment(10_000L, 10_000L, 0L), single(2_500L, 2));

            assertThat(result.getPgRefundAmount()).isEqualTo(5_000L);
            assertThat(result.getPointRefundAmount()).isZero();
            assertThat(result.getTotalRefundAmount()).isEqualTo(5_000L);
        }

        @Test
        @DisplayName("포인트 전액 결제: 환불 전액이 포인트로 분배 (pg=0)")
        void point_only_returns_full_point_refund() {
            RefundBreakdown result = calculator.calculate(
                    payment(10_000L, 0L, 10_000L), single(2_500L, 2));

            assertThat(result.getPgRefundAmount()).isZero();
            assertThat(result.getPointRefundAmount()).isEqualTo(5_000L);
            assertThat(result.getTotalRefundAmount()).isEqualTo(5_000L);
        }
    }

    @Nested
    @DisplayName("복합결제 — PG floor + 포인트 잔액 흡수")
    class MixedMethod {

        @Test
        @DisplayName("PG 8000 + 포인트 2000 = 총 10000, 5000원 환불 → pg=4000, point=1000 (8:2)")
        void mixed_8_2_ratio_5000_refund() {
            RefundBreakdown result = calculator.calculate(
                    payment(10_000L, 8_000L, 2_000L), single(2_500L, 2));

            assertThat(result.getPgRefundAmount()).isEqualTo(4_000L);
            assertThat(result.getPointRefundAmount()).isEqualTo(1_000L);
            assertThat(result.getTotalRefundAmount()).isEqualTo(5_000L);
        }

        @Test
        @DisplayName("PG 7500 + 포인트 2500 = 총 10000, 3333원 환불 → pg=2499(floor), point=834(잔액)")
        void mixed_3_1_ratio_with_decimal() {
            RefundBreakdown result = calculator.calculate(
                    payment(10_000L, 7_500L, 2_500L), single(3_333L, 1));

            assertThat(result.getPgRefundAmount()).isEqualTo(2_499L);
            assertThat(result.getPointRefundAmount()).isEqualTo(834L);
            assertThat(result.getTotalRefundAmount()).isEqualTo(3_333L);
        }

        @Test
        @DisplayName("PG 1 + 포인트 999 = 총 1000, 500원 환불 → 1원 단위 경계")
        void mixed_extreme_ratio_one_won_boundary() {
            RefundBreakdown result = calculator.calculate(
                    payment(1_000L, 1L, 999L), single(500L, 1));

            // pg = floor(500 * 1 / 1000) = 0, point = 500 - 0 = 500
            assertThat(result.getPgRefundAmount()).isZero();
            assertThat(result.getPointRefundAmount()).isEqualTo(500L);
            assertThat(result.getTotalRefundAmount()).isEqualTo(500L);
        }

        @Test
        @DisplayName("PG 999 + 포인트 1 = 총 1000, 500원 환불 → 1원 단위 경계 (반대 케이스)")
        void mixed_extreme_ratio_pg_dominant() {
            RefundBreakdown result = calculator.calculate(
                    payment(1_000L, 999L, 1L), single(500L, 1));

            // pg = floor(500 * 999 / 1000) = floor(499.5) = 499, point = 500 - 499 = 1
            assertThat(result.getPgRefundAmount()).isEqualTo(499L);
            assertThat(result.getPointRefundAmount()).isEqualTo(1L);
            assertThat(result.getTotalRefundAmount()).isEqualTo(500L);
        }
    }

    @Nested
    @DisplayName("다중 RefundItem 합산")
    class MultipleItems {

        @Test
        @DisplayName("여러 RefundItem의 itemRefundAmount 합산이 총 환불 금액")
        void multiple_items_sum_correctly() {
            List<RefundItemRequest> items = List.of(
                    new RefundItemRequest(1L, 3, 5, 1_500L), // 4500
                    new RefundItemRequest(2L, 1, 1, 4_000L), // 4000
                    new RefundItemRequest(3L, 2, 3, 500L));  // 1000
            // total = 9500

            RefundBreakdown result = calculator.calculate(
                    payment(10_000L, 9_000L, 1_000L), items);

            // pg = floor(9500 * 9000 / 10000) = floor(8550) = 8550
            // point = 9500 - 8550 = 950
            assertThat(result.getPgRefundAmount()).isEqualTo(8_550L);
            assertThat(result.getPointRefundAmount()).isEqualTo(950L);
            assertThat(result.getTotalRefundAmount()).isEqualTo(9_500L);
        }
    }

    @Nested
    @DisplayName("불변식: pg + point == totalRefund (합계 보장)")
    class SumInvariant {

        @ParameterizedTest(name = "[{index}] total={0}, pg={1}, point={2}, refund={3}")
        @CsvSource({
                "10000, 8000, 2000, 1",
                "10000, 8000, 2000, 7",
                "10000, 7500, 2500, 3333",
                "10000, 1, 9999, 7777",
                "10000, 9999, 1, 7777",
                "10000, 5000, 5000, 4999",
                "10000, 3333, 6667, 9999",
                "12345, 6789, 5556, 4321",
                "10000, 8000, 2000, 9999"
        })
        @DisplayName("CSV 테이블 케이스: 합계 항상 일치")
        void sum_invariant_table_cases(long total, long pg, long point, long refund) {
            RefundBreakdown result = calculator.calculate(
                    payment(total, pg, point),
                    List.of(new RefundItemRequest(1L, 1, 1, refund)));

            assertThat(result.getPgRefundAmount() + result.getPointRefundAmount())
                    .isEqualTo(refund);
        }

        @Test
        @DisplayName("랜덤 500회 — 모든 입력 조합에서 pg + point == totalRefund")
        void sum_invariant_random_property() {
            Random random = new Random(42);
            for (int i = 0; i < 500; i++) {
                long totalAmount = 100L + random.nextInt(1_000_000);
                long pgAmount = random.nextInt((int) totalAmount + 1);
                long pointUsedAmount = totalAmount - pgAmount;
                long refundAmount = 1L + random.nextInt((int) totalAmount);

                RefundBreakdown result = calculator.calculate(
                        payment(totalAmount, pgAmount, pointUsedAmount),
                        List.of(new RefundItemRequest(1L, 1, 1, refundAmount)));

                assertThat(result.getPgRefundAmount() + result.getPointRefundAmount())
                        .as("total=%d pg=%d point=%d refund=%d",
                                totalAmount, pgAmount, pointUsedAmount, refundAmount)
                        .isEqualTo(refundAmount);
                assertThat(result.getPgRefundAmount()).isNotNegative();
                assertThat(result.getPointRefundAmount()).isNotNegative();
            }
        }
    }

    @Nested
    @DisplayName("입력 검증")
    class InputValidation {

        @Test
        @DisplayName("null payment → IllegalArgumentException")
        void null_payment_throws() {
            assertThatThrownBy(() -> calculator.calculate(null, single(1_000L, 1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null items → IllegalArgumentException")
        void null_items_throws() {
            assertThatThrownBy(() -> calculator.calculate(payment(10_000L, 10_000L, 0L), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("빈 items → IllegalArgumentException")
        void empty_items_throws() {
            assertThatThrownBy(() ->
                    calculator.calculate(payment(10_000L, 10_000L, 0L), List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("totalAmount 0/음수 → IllegalArgumentException")
        void non_positive_total_amount_throws() {
            assertThatThrownBy(() ->
                    calculator.calculate(payment(0L, 0L, 0L), single(1_000L, 1)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() ->
                    calculator.calculate(payment(-1L, 0L, 0L), single(1_000L, 1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("적립 포인트 비례 회수")
    class EarnedPointRecover {

        @Test
        @DisplayName("PG 전액 결제 + 적립 100p, 전액 환불 → 회수 100p")
        void full_refund_recovers_all_earned() {
            // pgAmount=10_000, totalAmount=10_000, earned=100. 전액(10_000원) 환불.
            RefundBreakdown result = calculator.calculate(
                    paymentWithEarned(10_000L, 10_000L, 0L, 100L), single(10_000L, 1));

            assertThat(result.getPgRefundAmount()).isEqualTo(10_000L);
            assertThat(result.getPointEarnedRecoverAmount()).isEqualTo(100L);
        }

        @Test
        @DisplayName("절반 환불 → 비례 회수 (floor)")
        void half_refund_recovers_half_earned() {
            // totalAmount=10_000, earned=100. 5_000원(50%) 환불 → 회수 50p.
            RefundBreakdown result = calculator.calculate(
                    paymentWithEarned(10_000L, 10_000L, 0L, 100L), single(5_000L, 1));

            assertThat(result.getPgRefundAmount()).isEqualTo(5_000L);
            assertThat(result.getPointEarnedRecoverAmount()).isEqualTo(50L);
        }

        @Test
        @DisplayName("소수점: 1/3 환불 + 적립 100p → floor(33.33) = 33p 회수")
        void fractional_recover_uses_floor() {
            RefundBreakdown result = calculator.calculate(
                    paymentWithEarned(9_000L, 9_000L, 0L, 100L), single(3_000L, 1));

            assertThat(result.getPointEarnedRecoverAmount()).isEqualTo(33L); // 3_000 * 100 / 9_000
        }

        @Test
        @DisplayName("적립 없는 결제 → 회수 0")
        void no_earned_returns_zero_recover() {
            RefundBreakdown result = calculator.calculate(
                    paymentWithEarned(10_000L, 10_000L, 0L, 0L), single(10_000L, 1));

            assertThat(result.getPointEarnedRecoverAmount()).isZero();
        }

        @Test
        @DisplayName("복합결제 + 적립 → 회수도 비례")
        void mixed_payment_with_earned_recovers_proportionally() {
            // totalAmount=10_000 (PG 8_000 + Point 2_000), earned=100. 절반(5_000) 환불.
            RefundBreakdown result = calculator.calculate(
                    paymentWithEarned(10_000L, 8_000L, 2_000L, 100L), single(5_000L, 1));

            assertThat(result.getPgRefundAmount()).isEqualTo(4_000L); // 5000*8000/10000
            assertThat(result.getPointRefundAmount()).isEqualTo(1_000L);
            assertThat(result.getPointEarnedRecoverAmount()).isEqualTo(50L); // 5000*100/10000
        }
    }
}
