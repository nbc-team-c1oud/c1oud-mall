package nbc.c1oud_mall.payment.domain;

import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;

public record PaymentBreakdown(long totalAmount, long pgAmount, long pointUsedAmount) {

    public PaymentBreakdown {
        if (totalAmount < 0 || pgAmount < 0 || pointUsedAmount < 0) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_AMOUNT);
        }
        if (totalAmount != pgAmount + pointUsedAmount) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }
}
