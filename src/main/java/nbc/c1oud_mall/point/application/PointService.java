package nbc.c1oud_mall.point.application;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nbc.c1oud_mall.auth.application.Service.UserService;
import nbc.c1oud_mall.auth.domain.entity.User;
import nbc.c1oud_mall.auth.infrastructure.UserRepository;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.order.domain.Order;
import nbc.c1oud_mall.payment.domain.Payment;
import nbc.c1oud_mall.point.application.dto.PointBalanceResponse;
import nbc.c1oud_mall.point.application.dto.PointHistoryResponse;
import nbc.c1oud_mall.point.domain.PointHistory;
import nbc.c1oud_mall.point.domain.PointTransactionType;
import nbc.c1oud_mall.point.infrastructure.PointJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PointService {

    private final PointJpaRepository pointJpaRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public PointBalanceResponse getPointBalance(Long userId) {
        User user = userService.findById(userId);
        return new PointBalanceResponse(user.getPointBalance());
    }

    public List<PointHistoryResponse> getPointHistories(Long userId) {
        return pointJpaRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(PointHistoryResponse::from)
                .toList();
    }

    /**
     * 포인트 사용 차감. 호출자 트랜잭션(REQUIRED)에 합류.
     * User 행 비관적 락으로 동시 차감 race 차단.
     */
    @Transactional
    public void deductPoints(Long userId, long amount, Payment payment) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.usePoints(amount);
        pointJpaRepository.save(PointHistory.builder()
                .userId(userId)
                .order(entityManager.getReference(Order.class, payment.getOrderId()))
                .payment(payment)
                .amount(amount)
                .balanceAfter(user.getPointBalance())
                .transactionType(PointTransactionType.USE)
                .description("결제 시 포인트 사용")
                .build());
    }

    /**
     * 포인트 적립. 호출자 트랜잭션(REQUIRED)에 합류.
     */
    @Transactional
    public void accruePoints(Long userId, long amount, Payment payment) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.earnPoints(amount);
        pointJpaRepository.save(PointHistory.builder()
                .userId(userId)
                .order(entityManager.getReference(Order.class, payment.getOrderId()))
                .payment(payment)
                .amount(amount)
                .balanceAfter(user.getPointBalance())
                .transactionType(PointTransactionType.EARN)
                .description("결제 완료 포인트 적립")
                .build());
    }

    /**
     * 환불에 따른 사용 포인트 복구. 호출자 트랜잭션(REQUIRED)에 합류.
     * User 행 비관적 락으로 동시 변경 race 차단.
     */
    @Transactional
    public void restorePoints(Long userId, long amount, Payment payment) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.earnPoints(amount);
        pointJpaRepository.save(PointHistory.builder()
                .userId(userId)
                .order(entityManager.getReference(Order.class, payment.getOrderId()))
                .payment(payment)
                .amount(amount)
                .balanceAfter(user.getPointBalance())
                .transactionType(PointTransactionType.USE_CANCEL)
                .description("환불에 따른 포인트 사용 취소")
                .build());
    }

    /**
     * 환불에 따른 적립 포인트 비례 회수. 호출자 트랜잭션(REQUIRED)에 합류.
     * User 잔액이 amount보다 작으면 잔액까지만 차감하고 부족분은 log.warn으로 회계 모니터링.
     * (환불 자체는 막지 않음 — 사용자 불편/PG 취소 race 회피.)
     */
    @Transactional
    public void cancelEarnedPoints(Long userId, long amount, Payment payment) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        long actualDeduction = user.useEarnedPointsLenient(amount);
        if (actualDeduction < amount) {
            log.warn("[POINT_EARNED_RECOVER_SHORT] userId={}, requested={}, deducted={}, shortfall={} — 잔액 부족",
                    userId, amount, actualDeduction, amount - actualDeduction);
        }
        if (actualDeduction > 0L) {
            pointJpaRepository.save(PointHistory.builder()
                    .userId(userId)
                    .order(entityManager.getReference(Order.class, payment.getOrderId()))
                    .payment(payment)
                    .amount(actualDeduction)
                    .balanceAfter(user.getPointBalance())
                    .transactionType(PointTransactionType.EARN_CANCEL)
                    .description("환불에 따른 적립 포인트 회수")
                    .build());
        }
    }
}
