package nbc.c1oud_mall.point.application;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
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
}
