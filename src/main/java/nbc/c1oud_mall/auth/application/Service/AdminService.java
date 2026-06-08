package nbc.c1oud_mall.auth.application.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
public class AdminService {

	private final UserRepository userRepository;

	/**
	 * 권한 승격
	 * @param userId 해당 id의 유저를 권한을 어드미으로 승격한다. (이미 admin인 경우 예외처리 필요?)
	 */
	@Transactional
	public void promoteToAdmin(Long userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		user.promoteToAdmin();
	}

	@Transactional
	public Page<UserResponse> getAllUsers(Pageable pageable) {
		return userRepository.findAll(pageable)
			.map(UserResponse::from);
	}
}
