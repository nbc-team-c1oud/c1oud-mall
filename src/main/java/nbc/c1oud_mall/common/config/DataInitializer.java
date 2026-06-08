package nbc.c1oud_mall.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.auth.domain.UserRole;
import nbc.c1oud_mall.auth.domain.entity.User;
import nbc.c1oud_mall.auth.infrastructure.UserRepository;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Value("${super-admin.email}")
	private String superAdminEmail;

	@Value("${super-admin.password}")
	private String superAdminPassword;

	@Override
	public void run(ApplicationArguments args){
		if (userRepository.existsByEmail(superAdminEmail)){
			return;
		}
		User superAdmin = new User(
			superAdminEmail,
			passwordEncoder.encode(superAdminPassword),
			"슈퍼어드민",
			"010-0000-0000",
			UserRole.SUPER_ADMIN
		);
		userRepository.save(superAdmin);
	}
}
