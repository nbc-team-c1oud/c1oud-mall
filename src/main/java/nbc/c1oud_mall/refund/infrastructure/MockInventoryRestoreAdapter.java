package nbc.c1oud_mall.refund.infrastructure;

import lombok.extern.slf4j.Slf4j;
import nbc.c1oud_mall.refund.application.InventoryRestorePort;
import nbc.c1oud_mall.refund.domain.RefundItemRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 재고 복구 실구현 도입 전까지 사용하는 임시 mock.
 * 실구현이 준비되면 본 클래스를 삭제하고 InventoryRestorePort 구현체를 교체한다.
 */
@Component
@Slf4j
public class MockInventoryRestoreAdapter implements InventoryRestorePort {

    @Override
    public void restore(Long orderId, List<RefundItemRequest> items) {
        log.warn("[MOCK] InventoryRestorePort.restore orderId={}, itemCount={} — 실구현 도입 시 교체",
                orderId, items.size());
    }
}
