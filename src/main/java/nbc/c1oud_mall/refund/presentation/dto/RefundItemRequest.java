package nbc.c1oud_mall.refund.presentation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RefundItemRequest(
        @NotNull Long orderItemId,
        @Min(1) int quantity
) {}
