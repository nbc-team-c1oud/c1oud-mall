# ADR-0005: PortOne 웹훅 서명 검증 위치 및 raw body 보존 전략

- 상태: Accepted
- 일자: 2026-06-03
- 범위: 결제 BC (Story 3-1)

## Context

Epic 3은 PortOne 웹훅을 수신해 결제 확정 흐름과 동일한 도메인 서비스를 호출하는 작업이다. 웹훅은 **외부에서 들어오는 비신뢰 요청**이므로 다음 두 조건을 만족해야 한다.

1. **위·변조 차단** — HMAC-SHA256 서명 검증 (PortOne v2는 [Standard Webhooks](https://www.standardwebhooks.com/) 사양 따름)
2. **raw body 보존** — 서명은 파싱 전 원본 바이트에 대해 계산되어야 한다 (JSON 키 순서·공백 정규화에 영향받지 않게)

추가로 `.claude/rules/errorhandling.md` §3은 "Webhook Handler는 자체 처리 + PortOne 응답 규약으로 변환"으로 명시하며, Inbound 보안 검증을 Application Service에 섞지 않도록 한다. 즉 **컨트롤러 진입 전에 위·변조를 차단**하는 것이 자연.

## Decision

1. **`PortOneWebhookSignatureFilter`** (`OncePerRequestFilter`, `nbc.c1oud_mall.payment.infrastructure.webhook`)에서 서명을 검증한다.
   - `shouldNotFilter()`로 `/api/v1/payments/webhooks/portone` URL만 처리. 다른 엔드포인트에는 영향 없음.
   - SecurityConfig에서 `UsernamePasswordAuthenticationFilter.class` 앞에 등록 (JWT 필터와 동일 위치).
   - 웹훅 URL은 `permitAll()`로 두고 인증 책임을 본 필터가 담당.
2. **raw body 보존**은 자체 `CachedBodyHttpServletRequest extends HttpServletRequestWrapper`로 한다.
   - 필터에서 `InputStream`을 한 번 읽어 `byte[]`로 캐싱 → wrapper로 감싸 chain에 전달.
   - 컨트롤러(Story 3-2)에서 본문을 다시 읽어도 안전.
3. **서명 검증은 [Standard Webhooks](https://github.com/standard-webhooks/standard-webhooks) 사양**.
   - 헤더: `webhook-id`, `webhook-timestamp`, `webhook-signature`
   - Signed input: `{webhook-id}.{webhook-timestamp}.{body}`
   - HMAC-SHA256 + base64. 헤더 값은 `v1,<base64sig>` (여러 서명 공백 구분, 하나라도 일치 시 통과)
   - 시크릿은 `whsec_<base64>` 형식. 접두사 제거 후 base64 디코드한 바이트가 HMAC 키.
4. **Replay 방지**는 timestamp 윈도우 5분 (`Duration.ofMinutes(5)`). 서버 시각과 절대값 비교.
5. **시크릿 관리**는 `PortOneProperties.webhookSecret`. `application.yml`에서 `${PORTONE_WEBHOOK_SECRET:<dev-placeholder>}`로 환경변수 우선. dev placeholder는 로컬 기동 막힘 방지용 base64 유효 더미.
6. **검증 실패 응답**은 401 (Filter가 직접 응답, chain 미진행). 본문 없음. 구체 사유는 `log.warn`에만 기록.

## Alternatives

- **Controller에서 검증**: 컨트롤러 메서드 진입 후 `@RequestBody String body` + 헤더 추출로 검증. 단점: ① 다른 웹훅 엔드포인트 추가 시 매번 보일러플레이트 ② 컨트롤러 책임이 인증으로 흐려짐 ③ raw body 보존을 위해 어차피 wrapper 필요 ④ errorhandling.md §3의 "Webhook Handler 자체 처리" 분리 원칙과 어긋남.
- **HandlerInterceptor**: Spring 컨텍스트에서 동작 (DispatcherServlet 진입 후). 다만 wrapper 교체는 Filter 단계에서만 가능 → InputStream 캐싱이 어색해짐.
- **Spring `ContentCachingRequestWrapper`**: 표준 wrapper이지만 `getContentAsByteArray()`는 inputStream을 한 번 다 읽은 후에만 채워짐. 필터에서 어차피 직접 읽어야 해서 자체 wrapper 대비 이득 적음.
- **`ContentCachingResponseWrapper` 변형 — 비동기 처리**: 1차 동기 처리만, 비동기는 Story 3-2에서 검토.
- **메모리 캐시 기반 nonce(webhook-id) 중복 차단**: idempotency.md §4의 C 등급. race-vulnerable + 인스턴스 재시작 시 손실. Story 3-3(`WebhookEvent` Aggregate + DB UNIQUE)에서 처리.

## Consequences

### 장점
- 컨트롤러 진입 전 위·변조 차단 → 보안 방어선 명확 (errorhandling.md §3 부합)
- 다른 웹훅 엔드포인트(향후 배송·환불 등)는 본 필터 또는 같은 패턴 재사용 가능
- `WebhookSignatureVerifier`는 순수 도메인 로직(시간·HMAC·base64만 사용) → 단위 테스트로 race-free 검증
- raw body wrapper로 컨트롤러는 일반 `@RequestBody` 사용 가능

### 단점
- 시크릿 미설정 시 Filter 빈 생성 자체가 IllegalStateException → 기동 실패. application.yml에 dev placeholder 두어 회피하지만, 운영 환경에서 환경변수 누락 시 즉시 기동 실패하는 게 안전 vs 다른 도메인 작업 마비 사이 trade-off.
- `OncePerRequestFilter`의 `shouldNotFilter` URL 매칭은 정적. PortOne 외 웹훅이 추가되면 별도 필터 또는 URL 패턴 확장 필요.
- Replay 윈도우가 클럭 스큐에 민감 (서버 ↔ PortOne 시각 5분 이상 차이 시 정상 요청도 거부). 1차에서는 OK, 향후 NTP 보정 + 모니터링.

### 운영 진입 전 필수
- 실제 PortOne 웹훅 시크릿 환경변수 주입 (`PORTONE_WEBHOOK_SECRET=whsec_...`)
- 서명 검증 실패 알림 (구조화 로그 + 메트릭 — Story 3-3 또는 운영 PR로 별도)
- webhook-id 기반 멱등 보장 (Story 3-3)

## References

- workflows/product.md — [Story 3-1] 웹훅 엔드포인트 + raw body 보존 + HMAC 서명 검증
- .claude/rules/errorhandling.md §3 — Webhook Handler 자체 처리 + PortOne 응답 규약 변환
- .claude/rules/consistency.md §6 — Webhook Handler · Confirm API 동일 도메인 서비스 호출 (Story 3-2)
- .claude/rules/idempotency.md §2 — Webhook 결제 통지 (Story 3-3에서 INSERT-first WebhookEvent 패턴)
- [Standard Webhooks 사양](https://github.com/standard-webhooks/standard-webhooks)
