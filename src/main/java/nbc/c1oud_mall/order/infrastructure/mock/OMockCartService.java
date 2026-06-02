package nbc.c1oud_mall.order.infrastructure.mock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cart BC 실구현 도입 전까지 사용하는 임시 mock.
 * 실구현이 준비되면 본 클래스를 삭제하고 PaymentConfirmationService의 의존을 실 서비스로 교체한다.
 */
@Component
@Slf4j
public class OMockCartService {

    public void clearByUserId(Long userId) {
        log.warn("[MOCK] CartService.clearByUserId called userId={} — 실구현 도입 시 교체", userId);
    }

    public List<OMockCartItem> findCartEntities(Long userId) {
        log.warn("[MOCK] CartService.findCartEntities called userId={} — 실구현 도입 시 교체", userId);
        return List.of();
    }

    public List<OMockCartItem> findCartEntitiesByIds(Long userId, List<Long> cartItems) {
        log.warn("[MOCK] CartService.findCartEntitiesByIds called userId={} — 실구현 도입 시 교체", userId);
        return List.of();
    }

    public void clearCartItems(List<Long> orderedItems, Long userId) {
        log.warn("[MOCK] CartService.clearCartItems called userId={} — 실구현 도입 시 교체", userId);
    }


}