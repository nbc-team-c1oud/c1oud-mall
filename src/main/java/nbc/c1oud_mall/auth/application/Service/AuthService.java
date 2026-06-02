package nbc.c1oud_mall.auth.application.Service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.auth.domain.entity.User;
import nbc.c1oud_mall.auth.infrastructure.UserRepository;
import nbc.c1oud_mall.auth.presentation.dto.LoginRequest;
import nbc.c1oud_mall.auth.presentation.dto.LoginResponse;
import nbc.c1oud_mall.auth.presentation.dto.SignupRequest;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;
import nbc.c1oud_mall.common.jwt.JwtUtil;

@Service
@RequiredArgsConstructor
public class AuthService {
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtUtil jwtUtil;

	/**
	 * 회원가입
	 *
	 * @param request 회권가입 정보 (이메일, 비밀번호, 이름, 핸드폰번호)
	 */
	@Transactional
	public void signup(SignupRequest request) {
		// 이메일 중복 체크
		if (userRepository.existsByEmail(request.getEmail())) {
			throw new BusinessException(ErrorCode.EMAIL_DUPLICATE);
		}

		// 비밀번호 암호화
		String encodedPassword = passwordEncoder.encode(request.getPassword());

		// 유저 생성 후 저장
		User user = new User(
			request.getEmail(),
			encodedPassword,
			request.getName(),
			request.getPhoneNumber()
		);
		userRepository.save(user);
	}

	/**
	 * 로그인
	 *
	 * @param request 이메일, 비밀번호
	 * @return 로그인 가능한 Bearer 토큰
	 */
	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		//이메일로 유저 조회
		User user = userRepository. findByEmail(request.getEmail())
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

		//비밀번호 검증
		if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}

		//JWT 토큰 발급
		String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole());

		return new LoginResponse(token, "Bearer");
	}


}
