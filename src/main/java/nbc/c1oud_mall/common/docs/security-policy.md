# 보안 정책 (Security Policy)

## 1. 개요
본 프로젝트는 Spring Security 6.x를 기반으로 인증/인가를 처리한다.

## 2. API 접근 제어 정책
- **공개 API:** 상품 조회 API(`GET /api/v1/products/**`)는 누구나 접근 가능하도록 `permitAll()`을 적용한다.
- **인증 필요 API:** 위를 제외한 모든 요청은 인증(JWT 등)을 필요로 하며, 인증 실패 시 401 Unauthorized를 반환한다.
- **CSRF 정책:** REST API를 지향하므로 CSRF 보호는 비활성화(`disable`)한다.
- **세션 정책:** `STATELESS` 정책을 채택하여 서버가 세션을 유지하지 않고 토큰 기반의 통신을 수행한다.

## 3. 개발 편의성 정책
- **H2 콘솔:** 로컬 개발 환경의 데이터베이스 확인을 위해 `/h2-console/**` 접근을 허용한다.
- **FrameOptions:** H2 콘솔의 UI 렌더링을 위해 `frameOptions`를 비활성화(`disable`)한다. (단, 운영 환경에서는 반드시 차단되어야 함)