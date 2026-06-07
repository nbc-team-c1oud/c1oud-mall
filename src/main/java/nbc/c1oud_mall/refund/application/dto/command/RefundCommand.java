package nbc.c1oud_mall.refund.application.dto.command;

import java.util.List;

public record RefundCommand(
        Long orderId,
        Long userId,
        List<RefundItemCommand> items,
        String reason
) {
}
