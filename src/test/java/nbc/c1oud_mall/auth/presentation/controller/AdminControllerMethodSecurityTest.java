package nbc.c1oud_mall.auth.presentation.controller;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import nbc.c1oud_mall.auth.domain.UserRole;
import nbc.c1oud_mall.auth.domain.entity.User;
import nbc.c1oud_mall.auth.infrastructure.UserRepository;

@SpringBootTest
@Transactional
class AdminControllerMethodSecurityTest {

	@Autowired
	private AdminController adminController;

	@Autowired
	private UserRepository userRepository;

	//@PreAuthorize("hasRole('SUPER_ADMIN')") — ADMIN 롤은 권한 승격 호출 시 거부된다
	@Test
	@WithMockUser(roles = "ADMIN")
	void promoteToAdmin은_ADMIN_롤로_호출하면_AccessDenied() {
		User target = userRepository.save(new User("target@test.com", "pw", "타겟", "010-0000-0001", UserRole.USER));

		assertThatThrownBy(() -> adminController.promoteToAdmin(target.getId()))
				.isInstanceOf(AccessDeniedException.class);
	}

	//@PreAuthorize("hasRole('SUPER_ADMIN')") — SUPER_ADMIN 롤은 정상 통과한다
	@Test
	@WithMockUser(roles = "SUPER_ADMIN")
	void promoteToAdmin은_SUPER_ADMIN_롤로_호출하면_정상_동작() {
		User target = userRepository.save(new User("target2@test.com", "pw", "타겟2", "010-0000-0002", UserRole.USER));

		assertThatCode(() -> adminController.promoteToAdmin(target.getId())).doesNotThrowAnyException();
	}

	//@PreAuthorize("hasRole('ADMIN')") — 일반 USER 롤은 유저 목록 조회 시 거부된다
	@Test
	@WithMockUser(roles = "USER")
	void getAllUsers는_USER_롤로_호출하면_AccessDenied() {
		assertThatThrownBy(() -> adminController.getAllUsers(PageRequest.of(0, 20)))
				.isInstanceOf(AccessDeniedException.class);
	}

	//RoleHierarchy(SUPER_ADMIN > ADMIN) — SUPER_ADMIN은 ADMIN 전용 엔드포인트도 통과한다
	@Test
	@WithMockUser(roles = "SUPER_ADMIN")
	void getAllUsers는_RoleHierarchy에_의해_SUPER_ADMIN도_정상_동작() {
		assertThatCode(() -> adminController.getAllUsers(PageRequest.of(0, 20))).doesNotThrowAnyException();
	}
}