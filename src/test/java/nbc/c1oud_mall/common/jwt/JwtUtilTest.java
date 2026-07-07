package nbc.c1oud_mall.common.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import nbc.c1oud_mall.auth.domain.UserRole;
import nbc.c1oud_mall.common.exception.BusinessException;
import nbc.c1oud_mall.common.exception.ErrorCode;

class JwtUtilTest {

	private JwtUtil jwtUtil;

	@BeforeEach
	void setUp() {
		jwtUtil = new JwtUtil();
		ReflectionTestUtils.setField(jwtUtil, "secretKey", "test-jwt-secret-key-for-unit-test-only-32bytes+");
		ReflectionTestUtils.setField(jwtUtil, "expiration", 3_600_000L);
	}

	//role claim이 토큰에 삽입되고 getRole로 그대로 추출된다
	@Test
	void generateToken은_role_claim을_삽입하고_getRole로_추출된다() {
		String token = jwtUtil.generateToken(1L, "admin@test.com", UserRole.ADMIN);

		assertThat(jwtUtil.getRole(token)).isEqualTo("ADMIN");
	}

	//userId는 subject claim으로 들어가고 getUserId로 그대로 추출된다
	@Test
	void generateToken은_userId를_subject_claim으로_삽입하고_getUserId로_추출된다() {
		String token = jwtUtil.generateToken(42L, "user@test.com", UserRole.USER);

		assertThat(jwtUtil.getUserId(token)).isEqualTo(42L);
	}

	//SUPER_ADMIN 등 다른 role 값도 동일하게 왕복된다
	@Test
	void SUPER_ADMIN_role도_동일하게_왕복된다() {
		String token = jwtUtil.generateToken(7L, "root@test.com", UserRole.SUPER_ADMIN);

		assertThat(jwtUtil.getRole(token)).isEqualTo("SUPER_ADMIN");
	}

	//정상 토큰은 validateToken이 true를 반환한다
	@Test
	void validateToken은_정상_토큰에_대해_true를_반환한다() {
		String token = jwtUtil.generateToken(1L, "user@test.com", UserRole.USER);

		assertThat(jwtUtil.validateToken(token)).isTrue();
	}

	//만료된 토큰은 validateToken에서 TOKEN_EXPIRED로 실패한다
	@Test
	void validateToken은_만료된_토큰에_대해_TOKEN_EXPIRED_예외를_던진다() {
		ReflectionTestUtils.setField(jwtUtil, "expiration", -1_000L);
		String expiredToken = jwtUtil.generateToken(1L, "user@test.com", UserRole.USER);

		assertThatThrownBy(() -> jwtUtil.validateToken(expiredToken))
				.isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.TOKEN_EXPIRED);
	}

	//위·변조된 토큰은 validateToken에서 INVALID_TOKEN으로 실패한다
	@Test
	void validateToken은_위변조된_토큰에_대해_INVALID_TOKEN_예외를_던진다() {
		String token = jwtUtil.generateToken(1L, "user@test.com", UserRole.USER);
		String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");

		assertThatThrownBy(() -> jwtUtil.validateToken(tampered))
				.isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TOKEN);
	}
}