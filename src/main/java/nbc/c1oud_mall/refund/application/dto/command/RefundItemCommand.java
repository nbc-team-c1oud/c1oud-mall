package nbc.c1oud_mall.refund.application.dto.command;

public record RefundItemCommand(
        Long orderItemId,
        int quantity
) {
}
