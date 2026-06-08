package nbc.c1oud_mall.common.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;
import nbc.c1oud_mall.common.jwt.JwtAuthFilter;
import nbc.c1oud_mall.payment.infrastructure.webhook.PortOneWebhookSignatureFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthFilter jwtAuthFilter;
	private final PortOneWebhookSignatureFilter portOneWebhookSignatureFilter;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			// CSRF 비활성화 (REST API + 토큰 기반)
			.csrf(csrf -> csrf.disable())

			// CORS 활성화 (corsConfigurationSource 빈 사용)
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))

			// 세션 STATELESS (JWT 기반)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/v1/auth/**").permitAll()
				.requestMatchers("/api/v1/products/**").permitAll()
				.requestMatchers("/api/v1/admin/users/*/role").hasRole("SUPER_ADMIN")
				.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

				// 웹훅 엔드포인트는 HMAC 서명이 인증 역할 (PortOneWebhookSignatureFilter)
				.requestMatchers("/api/v1/payments/webhooks/**").permitAll()
				.requestMatchers("/h2-console/**").permitAll()
				.anyRequest().authenticated()
			)

			// H2 콘솔 UI가 iframe 내부에서 동작하도록
			.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()))

			// 두 필터 모두 UsernamePasswordAuthenticationFilter 앞에 등록.
			// 웹훅 필터는 shouldNotFilter()로 웹훅 URL에서만 동작하므로 JWT 필터와의 상대 순서는 실질적 영향 없음.
			.addFilterBefore(portOneWebhookSignatureFilter, UsernamePasswordAuthenticationFilter.class)
			.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	public RoleHierarchy roleHierarchy() {
		return RoleHierarchyImpl.fromHierarchy("""
			ROLE_SUPER_ADMIN > ROLE_ADMIN
			ROLE_ADMIN > ROLE_USER
			""");
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		// allowCredentials=true 에서는 와일드카드 사용 불가 → originPatterns 사용
		config.setAllowedOriginPatterns(List.of(
			"http://localhost:*",
			"http://127.0.0.1:*"
		));
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		config.setExposedHeaders(List.of("Authorization"));
		config.setAllowCredentials(true);
		config.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}
