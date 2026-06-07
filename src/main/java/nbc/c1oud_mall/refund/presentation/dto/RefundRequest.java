package nbc.c1oud_mall.refund.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import nbc.c1oud_mall.refund.application.dto.command.RefundCommand;
import nbc.c1oud_mall.refund.application.dto.command.RefundItemCommand;

import java.util.List;

public record RefundRequest(
        @Valid @NotEmpty List<RefundItemRequest> items,
        @NotBlank String reason
) {
    public RefundCommand toCommand(Long orderId, Long userId) {
        return new RefundCommand(orderId, userId,
                items.stream()
                        .map(i -> new RefundItemCommand(i.orderItemId(), i.quantity()))
                        .toList(),
                reason);
    }
}
