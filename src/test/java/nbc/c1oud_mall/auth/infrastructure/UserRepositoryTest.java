package nbc.c1oud_mall.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import nbc.c1oud_mall.auth.domain.UserRole;
import nbc.c1oud_mall.auth.domain.entity.User;
import nbc.c1oud_mall.common.config.JpaConfig;
import nbc.c1oud_mall.common.config.QuerydslConfig;

@DataJpaTest
@AutoConfigureTestDatabase
@Import({JpaConfig.class, QuerydslConfig.class})
class UserRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TestEntityManager em;

	//role='ADMIN'으로 저장 후 JPA로 재조회하면 UserRole.ADMIN으로 역직렬화된다
	@Test
	void role_ADMIN으로_저장하면_조회시_UserRole_ADMIN이다() {
		User saved = userRepository.save(
				new User("admin@test.com", "encoded-password", "관리자", "010-0000-0000", UserRole.ADMIN));
		em.flush();
		em.clear();

		User found = userRepository.findById(saved.getId()).orElseThrow();

		assertThat(found.getRole()).isEqualTo(UserRole.ADMIN);
	}
}