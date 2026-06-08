package nbc.c1oud_mall.auth.application.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.auth.domain.UserRole;
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
	 * @param userId 해당 id의 유저를 권한을 어드민으로 승격한다.
	 */
	@Transactional
	public void promoteToAdmin(Long userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

		if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.SUPER_ADMIN) {
			throw new BusinessException(ErrorCode.ALREADY_ADMIN);
		}

		user.promoteToAdmin();
	}

	/**
	 * 유저 전체 조회
	 * @param pageable size = 20, sort = "createdAt", direction = Sort.Direction.DESC
	 * @return 전체 유저
	 */
	@Transactional
	public Page<UserResponse> getAllUsers(Pageable pageable) {
		return userRepository.findAll(pageable)
			.map(UserResponse::from);
	}
}
