package nbc.c1oud_mall.point.application;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.auth.application.Service.UserService;
import nbc.c1oud_mall.auth.domain.entity.User;
import nbc.c1oud_mall.point.application.dto.PointReconciliationResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointFacade {

    private final UserService userService;
    private final PointService pointService;

    public PointReconciliationResponse reconcileUserPoint(Long userId) {
        User user = userService.findById(userId);

        long calculatedBalance = pointService.calculateBalanceFromHistory(userId);

        return new PointReconciliationResponse(
                userId,
                user.getPointBalance(),
                calculatedBalance
        );
    }
}