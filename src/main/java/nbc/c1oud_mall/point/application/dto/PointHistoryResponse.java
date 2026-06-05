package nbc.c1oud_mall.point.application.dto;

import lombok.Getter;
import nbc.c1oud_mall.point.domain.PointHistory;
import nbc.c1oud_mall.point.domain.PointTransactionType;

import java.time.LocalDateTime;

@Getter
public class PointHistoryResponse {

    private final Long pointHistoryId;
    private final PointTransactionType type;
    private final Long amount;
    private final LocalDateTime createdAt;

    public PointHistoryResponse(Long pointHistoryId, PointTransactionType type, Long amount, LocalDateTime createdAt) {
        this.pointHistoryId = pointHistoryId;
        this.type = type;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    public static PointHistoryResponse from(PointHistory pointHistory) {
        return new PointHistoryResponse(
                pointHistory.getId(),
                pointHistory.getTransactionType(),
                pointHistory.getAmount(),
                pointHistory.getCreatedAt()
        );
    }
}
