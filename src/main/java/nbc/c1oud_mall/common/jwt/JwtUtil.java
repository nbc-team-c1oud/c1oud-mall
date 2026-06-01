package nbc.c1oud_mall.common.jwt;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import nbc.c1oud_mall.auth.domain.UserRole;

@Component
public class JwtUtil {
	@Value("${jwt.secret}")
	private String secretKey;

	@Value("${jwt.expiration}")
	private long expiration;

	private Key getSecretKey() {
		return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
	}

	//토큰 생성
	public String generateToken(Long userId, String email, UserRole role) {
		return Jwts.builder()
			.setSubject(String.valueOf(userId))
			.claim("email", email)
			.claim("role", role.name())
			.setIssuedAt(new Date())
			.setExpiration(new Date(System.currentTimeMillis() + expiration))
			.signWith(getSecretKey(), SignatureAlgorithm.HS256)
			.compact();
	}

	//토큰에서 userId 추출
	public Long getUserId(String token) {
		return Long.parseLong(getClaims(token).getSubject());
	}

	//토큰에서 role 추출
	public String getRole(String token) {
		return getClaims(token).get("role", String.class);
	}

	//토큰 유효성 검증
	public boolean validateToken(String token) {
		try {
			getClaims(token);
			return true;
		}catch (ExpiredJwtException e) {
			throw new RuntimeException("TOKEN_EXPIRED");
		}catch (JwtException e) {
			throw new RuntimeException("INVALID_TOKEN");
		}
	}

	//Claims 파싱 (내부용)
	private Claims getClaims(String token) {
		return Jwts.parserBuilder()
			.setSigningKey(getSecretKey())
			.build()
			.parseClaimsJws(token)
			.getBody();
	}
}
