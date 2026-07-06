package nbc.c1oud_mall.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import nbc.c1oud_mall.auth.domain.UserRole;
import nbc.c1oud_mall.auth.infrastructure.UserRepository;

@DataJpaTest
@AutoConfigureTestDatabase
@Import({JpaConfig.class, QuerydslConfig.class})
class AdminAccountInitTest {

	@Autowired
	private UserRepository userRepository;

	private AdminAccountInit adminAccountInit;

	@BeforeEach
	void setUp() {
		PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
		adminAccountInit = new AdminAccountInit(userRepository, passwordEncoder);
		ReflectionTestUtils.setField(adminAccountInit, "superAdminEmail", "super-admin-dev@example.com");
		ReflectionTestUtils.setField(adminAccountInit, "superAdminPassword", "dev-super-admin-password");
	}

	//최초 실행 시 SUPER_ADMIN 1건 · ADMIN 2건이 시드된다
	@Test
	void run은_SUPER_ADMIN_1건과_ADMIN_2건을_시드한다() throws Exception {
		adminAccountInit.run(null);

		assertThat(userRepository.count()).isEqualTo(3);
		assertThat(userRepository.findByEmail("super-admin-dev@example.com"))
				.get().extracting("role").isEqualTo(UserRole.SUPER_ADMIN);
		assertThat(userRepository.findByEmail("admin1-dev@example.com"))
				.get().extracting("role").isEqualTo(UserRole.ADMIN);
		assertThat(userRepository.findByEmail("admin2-dev@example.com"))
				.get().extracting("role").isEqualTo(UserRole.ADMIN);
	}

	//재실행해도 이메일 중복 삽입 없이 멱등하다
	@Test
	void run은_재실행해도_중복_삽입하지_않는다() throws Exception {
		adminAccountInit.run(null);
		adminAccountInit.run(null);

		assertThat(userRepository.count()).isEqualTo(3);
	}
}
