package nbc.c1oud_mall.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserRoleTest {

	//Role enum 값 검증 — USER, ADMIN, SUPER_ADMIN 순서·구성
	@Test
	void values_는_USER_ADMIN_SUPER_ADMIN_순서로_구성된다() {
		assertThat(UserRole.values())
				.containsExactly(UserRole.USER, UserRole.ADMIN, UserRole.SUPER_ADMIN);
	}
}