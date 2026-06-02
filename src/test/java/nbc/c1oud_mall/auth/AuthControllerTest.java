package nbc.c1oud_mall.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import nbc.c1oud_mall.auth.domain.entity.User;
import nbc.c1oud_mall.auth.infrastructure.UserRepository;
import nbc.c1oud_mall.auth.presentation.dto.LoginRequest;
import nbc.c1oud_mall.auth.presentation.dto.SignupRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	private ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	//회원가입 성공
	@Test
	void 회원가입_성공() throws Exception {
		SignupRequest request = new SignupRequest("test@test.com", "password123", "홍길동", "010-1234-5678");

		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true));
	}

	//이메일 중복 시 409
	@Test
	void 회원가입_이메일중복_실패() throws Exception {
		// 미리 유저 저장
		userRepository.save(new User("test@test.com", passwordEncoder.encode("password123"), "홍길동", "010-1234-5678"));

		SignupRequest request = new SignupRequest("test@test.com", "password123", "홍길동", "010-1234-5678");

		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("U001"));

	}

	//로그인 성공
	@Test
	void 로그인_성공() throws Exception {
		// 미리 유저 저장
		userRepository.save(new User("test@test.com", passwordEncoder.encode("password123"), "홍길동", "010-1234-5678"));

		LoginRequest request = new LoginRequest("test@test.com", "password123");

		mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.accessToken").exists())
			.andExpect(jsonPath("$.data.tokenType").value("Bearer"));
	}

	//비밀번호 불일치시 401
	@Test
	void 로그인_비밀번호불일치_실패() throws Exception {
		// 미리 유저 저장
		userRepository.save(new User("test@test.com", passwordEncoder.encode("password123"), "홍길동", "010-1234-5678"));

		LoginRequest request = new LoginRequest("test@test.com", "password456");

		mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("U002"));
	}


}
