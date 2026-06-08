package nbc.c1oud_mall.refund.domain;

import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefundTest {

    private static final Long PAYMENT_ID = 10L;
    private static final Long USER_ID = 100L;
    private static final String REASON = "단순 변심";

    private RefundablePayment completedPayment() {
        return new RefundablePayment(PAYMENT_ID, USER_ID, true, 10_000L, 9_000L, 1_000L, "portone-test-001");
    }

    private RefundablePayment pendingPayment() {
        return new RefundablePayment(PAYMENT_ID, USER_ID, false, 10_000L, 9_000L, 1_000L, "portone-test-002");
    }

    private RefundBreakdown zeroBreakdown() {
        return new RefundBreakdown(0L, 0L);
    }

    @Nested
    @DisplayName("Refund.of 정상 생성")
    class Create {

        @Test
        @DisplayName("REQUESTED 상태로 생성되고 paymentId·userId·reason이 보존된다")
        void of_creates_requested_refund() {
            List<RefundItemRequest> items = List.of(
                    new RefundItemRequest(1L, 2, 3, 1_500L));

            Refund refund = Refund.of(completedPayment(), items, zeroBreakdown(), REASON);

            assertThat(refund.getId()).isNull();
            assertThat(refund.getPaymentId()).isEqualTo(PAYMENT_ID);
            assertThat(refund.getUserId()).isEqualTo(USER_ID);
            assertThat(refund.getReason()).isEqualTo(REASON);
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
            assertThat(refund.getRequestedAt()).isNotNull();
            assertThat(refund.getDbCommittedAt()).isNull();
            assertThat(refund.getPgCancelledAt()).isNull();
            assertThat(refund.getPgCancelTxId()).isNull();
        }

        @Test
        @DisplayName("각 RefundItem의 itemRefundAmount = priceSnapshot × quantity")
        void each_refund_item_calculates_amount_from_snapshot() {
            List<RefundItemRequest> items = List.of(
                    new RefundItemRequest(1L, 3, 5, 1_500L),
                    new RefundItemRequest(2L, 1, 1, 4_000L));

            Refund refund = Refund.of(completedPayment(), items, zeroBreakdown(), REASON);

            assertThat(refund.getRefundItems()).hasSize(2);
            assertThat(refund.getRefundItems().get(0).getItemRefundAmount()).isEqualTo(4_500L);
            assertThat(refund.getRefundItems().get(0).getPriceSnapshotAtPayment()).isEqualTo(1_500L);
            assertThat(refund.getRefundItems().get(0).getQuantity()).isEqualTo(3);
            assertThat(refund.getRefundItems().get(0).getOrderItemId()).isEqualTo(1L);
            assertThat(refund.getRefundItems().get(1).getItemRefundAmount()).isEqualTo(4_000L);
        }

        @Test
        @DisplayName("breakdown은 입력 그대로 보존된다 (Story 1-2 calculator 결과 주입 가정)")
        void breakdown_is_preserved() {
            RefundBreakdown breakdown = new RefundBreakdown(4_500L, 500L);
            List<RefundItemRequest> items = List.of(
                    new RefundItemRequest(1L, 1, 1, 5_000L));

            Refund refund = Refund.of(completedPayment(), items, breakdown, REASON);

            assertThat(refund.getBreakdown().getPgRefundAmount()).isEqualTo(4_500L);
            assertThat(refund.getBreakdown().getPointRefundAmount()).isEqualTo(500L);
            assertThat(refund.getBreakdown().getTotalRefundAmount()).isEqualTo(5_000L);
        }
    }

    @Nested
    @DisplayName("Refund.of 검증 실패")
    class Validation {

        @Test
        @DisplayName("RF002: 결제가 COMPLETED가 아니면 REFUND_NOT_REFUNDABLE_STATE")
        void non_completed_payment_throws_rf002() {
            List<RefundItemRequest> items = List.of(
                    new RefundItemRequest(1L, 1, 5, 1_500L));

            assertThatThrownBy(() -> Refund.of(pendingPayment(), items, zeroBreakdown(), REASON))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.REFUND_NOT_REFUNDABLE_STATE);
        }

        @Test
        @DisplayName("RF001: 요청 수량이 잔여 환불 가능 수량을 초과하면 REFUND_QUANTITY_EXCEEDED")
        void quantity_exceeds_remaining_throws_rf001() {
            List<RefundItemRequest> items = List.of(
                    new RefundItemRequest(1L, 3, 2, 1_500L));

            assertThatThrownBy(() -> Refund.of(completedPayment(), items, zeroBreakdown(), REASON))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.REFUND_QUANTITY_EXCEEDED);
        }

        @Test
        @DisplayName("RF001: remainingRefundableQuantity=0인 경우(이미 전량 환불됨)도 거부")
        void zero_remaining_quantity_throws_rf001() {
            List<RefundItemRequest> items = List.of(
                    new RefundItemRequest(1L, 1, 0, 1_500L));

            assertThatThrownBy(() -> Refund.of(completedPayment(), items, zeroBreakdown(), REASON))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.REFUND_QUANTITY_EXCEEDED);
        }

        @Test
        @DisplayName("itemRequests가 빈 리스트면 IllegalArgumentException")
        void empty_items_throws_illegal_argument() {
            assertThatThrownBy(() -> Refund.of(completedPayment(), List.of(), zeroBreakdown(), REASON))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("itemRequests가 null이면 IllegalArgumentException")
        void null_items_throws_illegal_argument() {
            assertThatThrownBy(() -> Refund.of(completedPayment(), null, zeroBreakdown(), REASON))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("breakdown이 null이면 IllegalArgumentException")
        void null_breakdown_throws_illegal_argument() {
            List<RefundItemRequest> items = List.of(
                    new RefundItemRequest(1L, 1, 1, 1_500L));

            assertThatThrownBy(() -> Refund.of(completedPayment(), items, null, REASON))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("음수 또는 0 수량은 IllegalArgumentException")
        void non_positive_quantity_throws_illegal_argument() {
            List<RefundItemRequest> items = List.of(
                    new RefundItemRequest(1L, 0, 5, 1_500L));

            assertThatThrownBy(() -> Refund.of(completedPayment(), items, zeroBreakdown(), REASON))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("RefundBreakdown 자체: 음수 금액은 IllegalArgumentException")
        void negative_breakdown_amount_throws_illegal_argument() {
            assertThatThrownBy(() -> new RefundBreakdown(-1L, 0L))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new RefundBreakdown(0L, -1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
