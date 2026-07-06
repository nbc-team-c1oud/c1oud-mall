# 운영 배포 시 변경 체크리스트 — 도메인 주소 / FE 환경

> 로컬·샌드박스 → 운영 환경 진입 시 **도메인 주소·시크릿·FE 설정**만 정리한 문서.
> 코드 변경(파일 수정) vs 환경변수 주입 vs 외부 콘솔 설정으로 칸 나눔.
> 운영 진입 전 모든 항목 ✅ 확인.

---

## 0. 한눈에 — 변경되어야 하는 것

| 카테고리 | 항목 수 | 위치 |
|---|---|---|
| BE 환경변수 (운영 시크릿) | 8개 | `application-prod.yml` + 컨테이너 env |
| BE 코드 수정 (1곳) | 1개 | `SecurityConfig.corsConfigurationSource` |
| FE 환경변수 | 3개 | `.env.production` |
| PortOne 콘솔 설정 | 4개 | https://admin.portone.io |
| 운영 진입 전 가드 | 3개 | profile / DB / 테스트 상품 |

---

## 1. BE — 환경변수 (운영 진입 시 *반드시* 주입)

`application.yml`·`application-prod.yml`이 `${...}` 형태로 외부 주입을 기다림. 운영 컨테이너 환경변수로 설정.

### 필수
| 환경변수 | 용도 | 위치 | 누락 시 |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | **`prod`로 강제** (기본값 `dev`) | `application.yml:5` | DummyDataInit + H2 콘솔 + show-sql 활성 — **반드시 prod로** |
| `DB_URL` | MySQL 운영 DB JDBC URL | `application-prod.yml:4` | 부팅 실패 |
| `DB_USERNAME` | DB 계정 | `application-prod.yml:6` | 부팅 실패 |
| `DB_PASSWORD` | DB 비밀번호 | `application-prod.yml:7` | 부팅 실패 |
| `JWT_SECRET` | JWT 서명 키 (256bit 이상 권장) | `application.yml:21` | 부팅 실패 (dev 기본값 ❌ 사용 금지) |
| `PORTONE_API_SECRET` | PortOne V2 API 인증 토큰 | `application.yml:12` | 결제 확정·취소 API 호출 실패 (PM004/PM005) |
| `PORTONE_WEBHOOK_SECRET` | PortOne 웹훅 HMAC-SHA256 검증 (`whsec_<base64>`) | `application.yml:15` | 모든 웹훅 401로 거절 → 결제 확정 누락 위험 |
| `SUPER_ADMIN_EMAIL` | 슈퍼관리자 계정 이메일 | `application-prod.yml:32` | 부팅 실패 |
| `SUPER_ADMIN_PASSWORD` | 슈퍼관리자 비밀번호 | `application-prod.yml:33` | 부팅 실패 |

### 선택 (기본값으로 OK인 것 — 운영 변경 케이스만)
| 환경변수 | 기본값 | 변경 케이스 |
|---|---|---|
| `PORTONE_BASE_URL` | `https://api.portone.io` | PortOne 전용 region 엔드포인트 도입 시 |

### 보안 주의
- `JWT_SECRET`은 dev 기본값(`dev-jwt-secret-for-local-and-test-environment-only`)을 **절대 운영에서 쓰지 말 것** — 토큰 위조 가능
- `PORTONE_WEBHOOK_SECRET` dev placeholder(`whsec_ZGV2LXBsYWNlaG9sZGVyLXNlY3JldC1mb3ItYm9vdC1vbmx5`)도 운영 사용 금지
- 시크릿은 K8s Secret / AWS Secrets Manager / Vault 등 secret store로 주입 (평문 env 파일 금지)

---

## 2. BE — 코드 수정 1곳 (CORS allowed origins)

현재 `SecurityConfig.corsConfigurationSource` 안에 localhost가 **하드코딩**되어 있음. 운영 FE 도메인 추가 필수.

### 위치
`src/main/java/nbc/c1oud_mall/common/security/SecurityConfig.java:83-86`

```java
// 현재 (dev 가정)
config.setAllowedOriginPatterns(List.of(
    "http://localhost:*",
    "http://127.0.0.1:*"
));
```

### 변경 방향
프로파일별 분리 또는 환경변수 driven으로 변경 권장. 후자 예시:

```java
@Value("${cors.allowed-origin-patterns}")
private List<String> allowedOriginPatterns;

// ...
config.setAllowedOriginPatterns(allowedOriginPatterns);
```

`application-prod.yml`:
```yaml
cors:
  allowed-origin-patterns:
    - https://c1oudmall.com         # 운영 FE 도메인
    - https://www.c1oudmall.com
    - https://staging.c1oudmall.com  # 스테이징
```

`application-dev.yml`:
```yaml
cors:
  allowed-origin-patterns:
    - http://localhost:*
    - http://127.0.0.1:*
```

### 주의
- `allowCredentials: true` 상태라서 `*` 와일드카드 사용 불가 → 정확한 origin 명시 필수
- PortOne SDK는 별도 origin 아님(같은 페이지 내 호출) → 추가 등록 불필요

---

## 3. BE — 운영 진입 전 가드 (확인만, 코드는 이미 OK)

| 항목 | 현재 상태 | 확인 |
|---|---|---|
| `DummyDataInit` 운영 차단 | `@Profile("dev")` 박혀있음 (`DummyDataInit.java:17`) | ✅ `SPRING_PROFILES_ACTIVE=prod`이면 빈 등록 X |
| `ddl-auto` | prod는 `validate` (`application-prod.yml:12`) | ✅ DDL 자동 변경 X — 스키마는 별도 마이그레이션 도구로 |
| H2 콘솔 | prod는 `enabled: false` (`application-prod.yml:21-22`) | ✅ |
| `show-sql` | prod는 `false` (`application-prod.yml:13`) | ✅ |
| logging level | prod는 `INFO` (`application-prod.yml:25-28`) | ✅ — `org.hibernate.SQL: DEBUG`은 dev만 |
| MySQL Dialect | prod는 명시 (`application-prod.yml:17`) | ✅ |

### 그러나 점검 권장 1곳 — SecurityConfig의 `/h2-console/**` permitAll
`SecurityConfig.java:52`에 `requestMatchers("/h2-console/**").permitAll()` 박혀있음. prod에선 h2-console 자체가 disabled라 404로 차단되지만, **보안 강화 차원에서 prod 프로파일에선 이 라인을 적용하지 않는 게 안전** (depth-in-defense).

### 정기 reconciliation 작업 (운영 진입 *후*)
| 작업 | 빈도 권장 |
|---|---|
| `Payment.status=FAILED` + PortOne `status=PAID` 불일치 감지 (AI-ADR-003) | 시간당 1회 |
| `Refund.status=DB_COMMITTED` + PortOne 미취소 잔존 (AI-ADR-010) | 시간당 1회 |
| `WebhookEvent` 90일 아카이브 (AI-ADR-005) | 일 1회 |

---

## 4. FE — 환경변수 (`.env.production`)

| 변수 | dev 값 | 운영 값 예시 |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080` | `https://api.c1oudmall.com` |
| `VITE_PORTONE_STORE_ID` | PortOne 콘솔의 **테스트** 스토어 ID | PortOne 콘솔의 **운영** 스토어 ID |
| `VITE_PORTONE_CHANNEL_KEY` | 테스트 채널 키 (`channel-key-...-test`) | 운영 채널 키 (`channel-key-...-live`) |

### 빌드 파이프라인
- 환경별 `.env` 파일 분리 (`.env.development` / `.env.staging` / `.env.production`)
- CI에서 `vite build --mode production` 형태로 환경 명시
- 환경변수는 빌드 타임 inline됨 — 운영 빌드를 dev API 가리키게 빌드 후 배포하는 실수 방지

### HTTPS 강제 (FE 코드)
- 운영은 반드시 `https://` API URL 사용 — mixed content 차단
- PortOne SDK도 HTTPS 페이지에서만 정상 동작 (브라우저 보안 정책)

---

## 5. FE — 권장 보안 변경 (선택, 운영 진입 시 검토)

현재 v3 가이드 §3 FE 헬퍼는 `localStorage.getItem('accessToken')`. 운영 진입 시 검토 항목:

| 항목 | 현재 | 운영 권장 | 비고 |
|---|---|---|---|
| 토큰 저장 위치 | `localStorage` | `httpOnly Secure SameSite=Strict cookie` | XSS 방어. BE 응답 헤더로 Set-Cookie 발급해야 함 (BE 변경 필요 — ADR 별도) |
| HTTPS 강제 | 없음 | `Strict-Transport-Security` 응답 헤더 | BE 또는 reverse proxy 설정 |
| 에러 메시지 노출 | `e.message` 그대로 | 상세 사유는 운영자에게만 | PM004/PM005 같은 502 에러는 사용자에게 "일시 장애" 정도로만 |

> 본 ADR 범위: 토큰 저장 변경은 **별도 ADR 필요** — JWT 헤더 vs 쿠키 전환은 보안 모델 변경. 현 v3 가이드(localStorage)로 운영 진입은 가능하나 XSS 위험 의식.

---

## 6. PortOne 콘솔 — 외부 시스템 설정

PortOne 콘솔(https://admin.portone.io)에서 *운영 환경* 항목 설정 필요.

| 항목 | dev | 운영 |
|---|---|---|
| **스토어** | 테스트 스토어 사용 | 운영 스토어 신설 (사업자 정보 등록) |
| **채널** (PG 연동) | KG이니시스 테스트 채널 | KG이니시스 운영 계약 + 운영 채널 등록 |
| **Webhook URL** | (등록 안 함 — 로컬은 ngrok 등 필요) | `https://api.c1oudmall.com/api/v1/payments/webhooks/portone` 등록 |
| **V2 API Secret** | 콘솔에서 발급된 dev secret | 운영 secret 별도 발급 — `PORTONE_API_SECRET` 환경변수로 주입 |
| **Webhook Secret** | dev placeholder | 콘솔에서 운영 시크릿 발급 — `PORTONE_WEBHOOK_SECRET` 환경변수로 주입 |

### Webhook URL 등록 주의
- PortOne 콘솔에 등록한 URL이 BE의 `POST /api/v1/payments/webhooks/portone`로 정확히 도달해야 함
- reverse proxy(ALB/Nginx)가 path 그대로 전달하도록 설정 — `/api/v1/*` rewrite 금지
- HTTPS 필수 — PortOne은 HTTP webhook 호출 안 함

---

## 7. 운영 진입 전 dev-스러운 데이터 정리

| 데이터 | 위치 | 처리 |
|---|---|---|
| 1원 / 1000원 테스트 상품 | `DummyDataInit.java` (dev 프로필 한정) | prod 프로필이면 자동 미등록 — 추가 작업 X |
| 운영 DB에 잔존하는 테스트 데이터 | 마이그레이션 시 청소 | 운영 DB 초기 부팅 전 cleanup script |
| dev 계정 (`super-admin-dev@example.com`) | dev 기본값 — `SUPER_ADMIN_EMAIL` 환경변수가 prod에서 강제 주입 | ✅ — 환경변수만 정확히 주입하면 자동 처리 |

---

## 8. 최종 체크리스트

운영 배포 직전 PR/Release 노트에 첨부:

### BE
- [ ] `SPRING_PROFILES_ACTIVE=prod` 설정
- [ ] `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 운영 DB 시크릿 주입
- [ ] `JWT_SECRET` 운영 시크릿 (256bit+) 주입
- [ ] `PORTONE_API_SECRET` 운영 시크릿 주입
- [ ] `PORTONE_WEBHOOK_SECRET` 운영 시크릿 주입 (`whsec_` prefix 확인)
- [ ] `SUPER_ADMIN_EMAIL` / `SUPER_ADMIN_PASSWORD` 운영 계정 주입
- [ ] `SecurityConfig.corsConfigurationSource` 운영 FE 도메인 반영 (코드 수정)
- [ ] (선택) `/h2-console/**` permitAll 라인 prod에서 제거
- [ ] reverse proxy(ALB/Nginx) HTTPS 강제 + Webhook path 그대로 전달
- [ ] reconciliation 잡 등록 (Payment FAILED 불일치 / Refund DB_COMMITTED 잔존 / WebhookEvent 아카이브)

### FE
- [ ] `.env.production`의 `VITE_API_BASE_URL` 운영 도메인 (`https://`)
- [ ] `.env.production`의 `VITE_PORTONE_STORE_ID` 운영 스토어
- [ ] `.env.production`의 `VITE_PORTONE_CHANNEL_KEY` 운영 채널
- [ ] 빌드 시 `--mode production` 명시 (dev .env 누락 빌드 금지)
- [ ] (선택, 권장) 토큰 저장 localStorage → httpOnly cookie 전환 ADR 진행

### PortOne 콘솔
- [ ] 운영 스토어 생성 + 사업자 정보 등록
- [ ] 운영 채널 생성 (PG 계약 완료 후)
- [ ] Webhook URL = `https://<운영 API 도메인>/api/v1/payments/webhooks/portone` 등록
- [ ] V2 API Secret 운영용 발급 → BE 환경변수
- [ ] Webhook Secret 운영용 발급 → BE 환경변수

### 회귀 검증 (운영 진입 *직후*)
- [ ] PortOne 샌드박스 → 운영 환경 1원 결제 E2E (운영 PG로 실제 결제 1건 → 환불)
- [ ] 운영 도메인에서 CORS preflight 통과 확인
- [ ] 운영 Webhook URL로 PortOne 테스트 호출 200 OK
- [ ] `/api/v1/auth/login` 후 JWT 발급 + `/auth/me` 호출

---

## 변경 이력

| 날짜 | 변경 |
|---|---|
| 2026-06-08 | 최초 작성 — v3 FE 가이드 기준 |
