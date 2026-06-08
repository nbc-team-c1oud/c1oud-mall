package nbc.c1oud_mall.refund.application.dto;

import nbc.c1oud_mall.refund.domain.RefundBreakdown;
import nbc.c1oud_mall.refund.domain.RefundStatus;

public record RefundResult(
        Long refundId,
        RefundStatus finalStatus,
        RefundBreakdown breakdown
) {
    public boolean isPgCancelled() {
        return finalStatus == RefundStatus.PG_CANCELLED;
    }
}
