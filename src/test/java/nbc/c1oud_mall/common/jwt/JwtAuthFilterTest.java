package nbc.c1oud_mall.common.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

	@Mock
	private JwtUtil jwtUtil;

	private JwtAuthFilter jwtAuthFilter;

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	//Authorization 헤더의 role claim(ADMIN)이 SecurityContext의 ROLE_ADMIN 권한으로 세팅된다
	@Test
	void 유효한_토큰이면_role_claim으로_ROLE_ADMIN_권한을_SecurityContext에_세팅한다() throws Exception {
		jwtAuthFilter = new JwtAuthFilter(jwtUtil);
		String token = "valid-admin-token";
		when(jwtUtil.validateToken(token)).thenReturn(true);
		when(jwtUtil.getUserId(token)).thenReturn(1L);
		when(jwtUtil.getRole(token)).thenReturn("ADMIN");

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		jwtAuthFilter.doFilterInternal(request, response, filterChain);

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		assertThat(authentication).isNotNull();
		assertThat(authentication.getPrincipal()).isEqualTo(1L);
		assertThat(authentication.getAuthorities())
				.extracting(GrantedAuthority::getAuthority)
				.containsExactly("ROLE_ADMIN");
	}

	//Authorization 헤더가 없으면 SecurityContext에 인증 정보를 세팅하지 않는다
	@Test
	void 토큰이_없으면_SecurityContext를_세팅하지_않는다() throws Exception {
		jwtAuthFilter = new JwtAuthFilter(jwtUtil);
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();

		jwtAuthFilter.doFilterInternal(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}
}