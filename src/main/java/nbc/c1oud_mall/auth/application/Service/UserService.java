package nbc.c1oud_mall.auth.application.Service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.auth.domain.entity.User;
import nbc.c1oud_mall.auth.infrastructure.UserRepository;
import nbc.c1oud_mall.auth.presentation.dto.UserResponse;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;

	@Transactional(readOnly = true)
	public UserResponse getMe(Long userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

		return new UserResponse(
			user.getId(),
			user.getEmail(),
			user.getName(),
			user.getPhoneNumber(),
			user.getRole().name(),
			user.getPointBalance(),
			user.getCreatedAt(),
			user.getUpdatedAt()
		);
	}
}
