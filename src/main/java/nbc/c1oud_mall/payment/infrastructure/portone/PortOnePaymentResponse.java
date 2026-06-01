package nbc.c1oud_mall.payment.infrastructure.portone;

import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.payment.application.dto.PortOnePaymentInfo;
import nbc.c1oud_mall.payment.application.dto.PortOnePaymentStatus;

public record PortOnePaymentResponse(
        String status,
        Amount amount,
        Channel channel,
        String pgTxId
) {

    public record Amount(long total) {
    }

    public record Channel(String pgProvider) {
    }

    public PortOnePaymentInfo toInfo(String portonePaymentId) {
        if (status == null || amount == null || channel == null) {
            throw new BusinessException(ErrorCode.PORTONE_RESPONSE_INVALID);
        }
        PortOnePaymentStatus mappedStatus;
        try {
            mappedStatus = PortOnePaymentStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw BusinessException.withDetail(
                    ErrorCode.PORTONE_RESPONSE_INVALID,
                    "unknown status: " + status
            );
        }
        return new PortOnePaymentInfo(
                portonePaymentId,
                mappedStatus,
                amount.total(),
                channel.pgProvider(),
                pgTxId
        );
    }
}
