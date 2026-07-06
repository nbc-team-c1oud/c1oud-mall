package nbc.c1oud_mall.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.auth.domain.UserRole;
import nbc.c1oud_mall.auth.domain.entity.User;
import nbc.c1oud_mall.auth.infrastructure.UserRepository;

// dev/local 전용 — 로컬 개발·통합 테스트용 관리자 계정 하드코딩 시드 (SUPER_ADMIN 1 · ADMIN 2).
// prod 초기 SUPER_ADMIN은 DataInitializer가 담당.
@Component
@Profile({"dev", "local"})
@RequiredArgsConstructor
public class AdminAccountInit implements ApplicationRunner {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Value("${super-admin.email}")
	private String superAdminEmail;

	@Value("${super-admin.password}")
	private String superAdminPassword;

	@Override
	public void run(ApplicationArguments args) {
		seedIfAbsent(superAdminEmail, superAdminPassword, "슈퍼어드민", "010-0000-0000", UserRole.SUPER_ADMIN);
		seedIfAbsent("admin1-dev@example.com", "dev-admin-password", "관리자1", "010-0000-0001", UserRole.ADMIN);
		seedIfAbsent("admin2-dev@example.com", "dev-admin-password", "관리자2", "010-0000-0002", UserRole.ADMIN);
	}

	private void seedIfAbsent(String email, String rawPassword, String name, String phoneNumber, UserRole role) {
		if (userRepository.existsByEmail(email)) {
			return;
		}
		userRepository.save(new User(email, passwordEncoder.encode(rawPassword), name, phoneNumber, role));
	}
}