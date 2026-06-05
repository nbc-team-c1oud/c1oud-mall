package nbc.c1oud_mall.point.application.dto;

import lombok.Getter;

@Getter
public class PointBalanceResponse {

    private final Long pointBalance;

    public PointBalanceResponse(Long pointBalance) {
        this.pointBalance = pointBalance;
    }
}
