package nbc.c1oud_mall.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. REST API이므로 CSRF 보호 비활성화 (토큰 기반 통신 예정)
                .csrf(AbstractHttpConfigurer::disable)

                // 2. 세션을 사용하지 않고 STATELESS 상태로 설정 (추후 JWT 사용을 위함)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 3. 요청 경로별 권한 설정
                .authorizeHttpRequests(auth -> auth
                        // 상품 조회 API는 누구나 접근 가능하도록 허용 (API 명세서 기준)
                        .requestMatchers("/api/v1/products/**").permitAll()

                        // 개발 환경에서 H2 콘솔 접근 허용
                        .requestMatchers("/h2-console/**").permitAll()

                        // 그 외의 모든 요청은 인증(토큰)이 필요함
                        .anyRequest().authenticated()
                )

                // 4. H2 콘솔 UI가 iframe 내부에서 정상 작동하도록 설정
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()));

        return http.build();
    }
}