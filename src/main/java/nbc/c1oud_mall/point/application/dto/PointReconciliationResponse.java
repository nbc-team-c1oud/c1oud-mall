package nbc.c1oud_mall.point.application.dto;

import lombok.Getter;

@Getter
public class PointReconciliationResponse {

    private final Long userId;
    private final Long currentBalance;
    private final Long calculatedBalance;
    private final Long difference;
    private final boolean matched;

    public PointReconciliationResponse(
            Long userId,
            Long currentBalance,
            Long calculatedBalance
    ) {
        this.userId = userId;
        this.currentBalance = currentBalance;
        this.calculatedBalance = calculatedBalance;
        this.difference = currentBalance - calculatedBalance;
        this.matched = currentBalance.equals(calculatedBalance);
    }
}