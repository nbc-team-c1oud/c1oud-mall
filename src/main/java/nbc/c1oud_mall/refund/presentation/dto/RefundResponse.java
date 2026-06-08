package nbc.c1oud_mall.refund.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import nbc.c1oud_mall.refund.application.dto.RefundResult;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RefundResponse(
        Long refundId,
        String refundStatus,
        long pgRefundAmount,
        long pointRefundAmount,
        String warning
) {
    public static RefundResponse from(RefundResult result) {
        return new RefundResponse(
                result.refundId(),
                result.finalStatus().name(),
                result.breakdown().getPgRefundAmount(),
                result.breakdown().getPointRefundAmount(),
                null
        );
    }

    public static RefundResponse fromDbCommitted(RefundResult result) {
        return new RefundResponse(
                result.refundId(),
                result.finalStatus().name(),
                result.breakdown().getPgRefundAmount(),
                result.breakdown().getPointRefundAmount(),
                "PG 취소 처리 진행 중. 운영팀 확인 필요"
        );
    }
}
