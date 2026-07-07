package nbc.c1oud_mall.auth.presentation.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import nbc.c1oud_mall.auth.domain.UserRole;
import nbc.c1oud_mall.auth.domain.entity.User;
import nbc.c1oud_mall.auth.infrastructure.UserRepository;
import nbc.c1oud_mall.common.jwt.JwtUtil;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtUtil jwtUtil;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private String bearer(Long userId, String email, UserRole role) {
		return "Bearer " + jwtUtil.generateToken(userId, email, role);
	}

	//ADMIN 토큰으로 /admin/me 조회 시 본인 프로필이 반환된다
	@Test
	void admin_me는_ADMIN_토큰으로_호출하면_본인_프로필을_반환한다() throws Exception {
		User admin = userRepository.save(
				new User("admin@test.com", passwordEncoder.encode("pw"), "관리자", "010-1111-1111", UserRole.ADMIN));

		mockMvc.perform(get("/api/v1/admin/me")
				.header("Authorization", bearer(admin.getId(), admin.getEmail(), UserRole.ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.email").value("admin@test.com"));
	}

	//일반 USER 토큰으로 /admin/me 조회 시 403이 반환된다
	@Test
	void admin_me는_USER_토큰으로_호출하면_403이다() throws Exception {
		User user = userRepository.save(
				new User("user@test.com", passwordEncoder.encode("pw"), "일반유저", "010-2222-2222", UserRole.USER));

		mockMvc.perform(get("/api/v1/admin/me")
				.header("Authorization", bearer(user.getId(), user.getEmail(), UserRole.USER)))
			.andExpect(status().isForbidden());
	}
}
