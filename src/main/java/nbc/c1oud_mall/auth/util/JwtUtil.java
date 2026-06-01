package nbc.c1oud_mall.auth.util;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import nbc.c1oud_mall.auth.UserRole;

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


}
