package nbc.c1oud_mall.refund.application;

import nbc.c1oud_mall.refund.domain.RefundItemRequest;

import java.util.List;

public interface InventoryRestorePort {

    void restore(Long orderId, List<RefundItemRequest> items);
}
