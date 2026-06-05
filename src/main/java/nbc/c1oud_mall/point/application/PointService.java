package nbc.c1oud_mall.point.application;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.auth.application.Service.UserService;
import nbc.c1oud_mall.auth.domain.entity.User;
import nbc.c1oud_mall.point.application.dto.PointBalanceResponse;
import nbc.c1oud_mall.point.application.dto.PointHistoryResponse;
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
}
