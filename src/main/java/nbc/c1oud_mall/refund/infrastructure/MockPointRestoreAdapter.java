package nbc.c1oud_mall.refund.infrastructure;

import lombok.extern.slf4j.Slf4j;
import nbc.c1oud_mall.refund.application.PointRestorePort;
import org.springframework.stereotype.Component;

/**
 * 포인트 복구 실구현 도입 전까지 사용하는 임시 mock.
 * 실구현이 준비되면 본 클래스를 삭제하고 PointRestorePort 구현체를 교체한다.
 */
@Component
@Slf4j
public class MockPointRestoreAdapter implements PointRestorePort {

    @Override
    public void restore(Long userId, long pointAmount, Long paymentId) {
        log.warn("[MOCK] PointRestorePort.restore userId={}, amount={}, paymentId={} — 실구현 도입 시 교체",
                userId, pointAmount, paymentId);
    }
}
