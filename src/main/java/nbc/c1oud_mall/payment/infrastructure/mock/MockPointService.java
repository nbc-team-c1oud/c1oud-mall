package nbc.c1oud_mall.payment.infrastructure.mock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Point BC 실구현 도입 전까지 사용하는 임시 mock.
 * 실구현이 준비되면 본 클래스를 삭제하고 PaymentConfirmationService의 의존을 실 서비스로 교체한다.
 */
@Component
@Slf4j
public class MockPointService {

    public void deductPoints(Long userId, long amount) {
        log.warn("[MOCK] PointService.deductPoints called userId={}, amount={} — 실구현 도입 시 교체",
                userId, amount);
    }

    public void accruePoints(Long userId, long amount) {
        log.warn("[MOCK] PointService.accruePoints called userId={}, amount={} — 실구현 도입 시 교체",
                userId, amount);
    }
}
