package nbc.c1oud_mall.common.jwt;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

	private final JwtUtil jwtUtil;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
		FilterChain filterChain) throws ServletException, IOException {

		String token = resolveToken(request);

		if (token != null && jwtUtil.validateToken(token)) {
			Long userId = jwtUtil.getUserId(token);
			String role = jwtUtil.getRole(token);
			SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);
			UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of(authority));
			SecurityContextHolder.getContext().setAuthentication(authentication);
		}
		filterChain.doFilter(request, response);
	}

	// Authorization 헤더에서 토큰 추출
	private String resolveToken(HttpServletRequest request) {
		String bearer = request.getHeader("Authorization");
		if (bearer != null && bearer.startsWith("Bearer ")) {
			return bearer.substring(7);
		}
		return null;
	}
}
