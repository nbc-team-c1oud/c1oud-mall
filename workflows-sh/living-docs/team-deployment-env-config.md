# 팀 실제 배포 구조 — BE 환경변수 · CORS · FE env 수정 체크리스트

> 우리 팀의 *현재 배포 구조*(CloudFront FE + nip.io EC2 BE) 기준으로,
> 환경변수·CORS·FE 빌드 설정에서 **반드시 손봐야 하는 것**만 정리.
> 일반 운영 가이드는 [production-deployment-checklist.md](production-deployment-checklist.md) 참고.

---

## 0. 현재 구조

```
FE  →  https://deqgx2ewq7d9a.cloudfront.net   (CloudFront + S3)
BE  →  https://15.164.9.27.nip.io             (EC2 + Nginx + Let's Encrypt)
```

| 항목 | 값 |
|---|---|
| FE 호스팅 | AWS CloudFront + S3 정적 호스팅 |
| FE 도메인 | `deqgx2ewq7d9a.cloudfront.net` (CloudFront 기본 도메인) |
| BE 호스팅 | AWS EC2 (Public IP: `15.164.9.27`) |
| BE 도메인 | `15.164.9.27.nip.io` (와일드카드 DNS → IP 그대로 반환) |
| BE TLS | Let's Encrypt (nip.io 도메인 기반 무료 인증서) |
| BE 프록시 | Nginx (`:443` HTTPS → `:8080` Spring Boot) |

**핵심**: FE/BE 둘 다 `https://` — 브라우저의 **Mixed Content** 차단을 회피하기 위함.

---

## 1. 요청 흐름 — 2가지 경로

### 경로 ① 브라우저 → 백엔드 (일반 API 호출)
```
사용자 브라우저
  │
  ├─ ① 페이지 로드:     CloudFront → S3 (index.html, JS, CSS)
  │
  └─ ② API 호출:        https://15.164.9.27.nip.io/api/v1/...
                         ↓
                         EC2 Nginx (:443, Let's Encrypt SSL)
                         ↓
                         Spring Boot (:8080)
                         ↑
                         (응답에 CORS 헤더 포함해서 반환)
```
- **CloudFront ↔ EC2가 직접 통신하지 않음**. 둘 다 *브라우저를 거쳐서만* 연결됨.
- 그래서 **BE에서 CloudFront origin을 CORS 허용**해주는 게 핵심.

### 경로 ② PortOne 서버 → 백엔드 (Webhook)
```
PortOne 서버 (api.portone.io)
  │
  └─ Webhook 발사:       https://15.164.9.27.nip.io/api/v1/payments/webhooks/portone
                         ↓
                         EC2 Nginx (:443)
                         ↓
                         Spring Boot (:8080)
                         ↓
                         PortOneWebhookSignatureFilter (HMAC-SHA256 검증)
                         ↓
                         WebhookEvent INSERT-first (멱등) → PaymentConfirmationService 재사용
```
- **브라우저를 거치지 않음** — 서버-서버 직접 통신
- **CORS 무관** — 동일 출처 정책은 브라우저 정책. PortOne 서버 호출엔 적용 안 됨
- **인증은 HMAC 서명**으로 — JWT 아님, CORS 아님, 오직 `PORTONE_WEBHOOK_SECRET`

---

## 2. BE — 환경변수 (EC2에 주입)

### 2.1 필수 시크릿·설정 (운영 EC2의 컨테이너 env / systemd EnvironmentFile / `.env` 등으로 주입)

| 환경변수 | 우리 팀 값 / 형태 | 누락 시 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | **`prod`** (기본값 `dev`라 누락하면 dev로 부팅 — H2/DummyDataInit/show-sql 활성) | dev로 부팅 — 운영 위험 |
| `DB_URL` | `jdbc:mysql://<RDS-host>:3306/c1oud_mall?...` 또는 EC2 내 MySQL | 부팅 실패 |
| `DB_USERNAME` | 운영 DB 계정 | 부팅 실패 |
| `DB_PASSWORD` | 운영 DB 비밀번호 | 부팅 실패 |
| `JWT_SECRET` | 256bit+ 운영 시크릿 (**dev 기본값 절대 금지**) | 부팅 실패 또는 토큰 위조 가능 |
| `PORTONE_API_SECRET` | PortOne 콘솔의 *운영 V2 API Secret* | 결제 확정·취소 호출 시 PM004/PM005 |
| `PORTONE_WEBHOOK_SECRET` | PortOne 콘솔의 *운영 Webhook Secret* (`whsec_<base64>`) | 모든 웹훅 401 거절 → 결제 확정 누락 위험 |
| `SUPER_ADMIN_EMAIL` | 운영 슈퍼관리자 이메일 | 부팅 실패 |
| `SUPER_ADMIN_PASSWORD` | 운영 슈퍼관리자 비밀번호 | 부팅 실패 |

### 2.2 EC2 주입 방법 권장 (택 1)

| 방법 | 비고 |
|---|---|
| **systemd EnvironmentFile** | `/etc/c1oud-mall/c1oud.env` 파일 + `chmod 600` + systemd unit에 `EnvironmentFile=` 지정. 가장 단순 |
| **Docker `--env-file`** | 컨테이너 띄울 때 `.env` 파일 주입. Docker 사용 시 |
| **AWS Systems Manager Parameter Store** | 부팅 시 IAM role로 secret 가져오기. 시크릿 회전 가능 |
| ❌ `application-prod.yml`에 평문 작성 | 금지. git에 들어가면 사고 |

---

## 3. BE — CORS 설정 (코드 수정 1곳)

### 3.1 현재 상태 (수정 필요)

`src/main/java/nbc/c1oud_mall/common/security/SecurityConfig.java:83-86`:

```java
config.setAllowedOriginPatterns(List.of(
    "http://localhost:*",
    "http://127.0.0.1:*"
));
```

**문제**: CloudFront 도메인이 없음 → 브라우저가 `CORS policy ... has been blocked` 에러로 모든 API 차단.

### 3.2 변경 — 환경변수 driven

#### `SecurityConfig.java` 수정
```java
@Value("${cors.allowed-origin-patterns}")
private List<String> allowedOriginPatterns;

@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(allowedOriginPatterns);    // ← 환경변수에서
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setExposedHeaders(List.of("Authorization"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);
    // ... (이하 동일)
}
```

#### `application-dev.yml` 추가
```yaml
cors:
  allowed-origin-patterns:
    - http://localhost:*
    - http://127.0.0.1:*
```

#### `application-prod.yml` 추가
```yaml
cors:
  allowed-origin-patterns:
    - https://deqgx2ewq7d9a.cloudfront.net
    # CloudFront 커스텀 도메인 붙이면 여기 추가:
    # - https://c1oudmall.com
    # - https://www.c1oudmall.com
```

### 3.3 CORS 주의

- `allowCredentials: true`이라 `*` 와일드카드 사용 불가 — *정확한 origin* 명시 필수
- CloudFront 도메인은 `cloudfront.net` 그대로. trailing slash·port 안 붙임
- 커스텀 도메인을 CloudFront에 붙이면 *그 도메인을 추가*해야 함 (CloudFront 도메인이 자동 매핑되지 않음)
- **PortOne webhook은 CORS 무관** — 브라우저 안 거침. `/api/v1/payments/webhooks/**`는 origin 검증 의미 없음

---

## 4. FE — 환경변수 (`.env.production`)

### 4.1 필수 변수

| 변수 | 값 | 비고 |
|---|---|---|
| `VITE_API_BASE_URL` | `https://15.164.9.27.nip.io` | **`https://` 필수** (mixed content 차단 회피) |
| `VITE_PORTONE_STORE_ID` | PortOne 콘솔의 *운영* 스토어 ID | |
| `VITE_PORTONE_CHANNEL_KEY` | PortOne 콘솔의 *운영* 채널 키 | KG이니시스 등 PG별 |

### 4.2 빌드·배포 파이프라인

```bash
# 1. 운영 빌드
npm run build -- --mode production
# (또는 vite build --mode production)
# → dist/ 폴더에 .env.production 값이 inline된 JS·CSS 산출

# 2. S3 업로드
aws s3 sync dist/ s3://<bucket-name>/ --delete

# 3. CloudFront 캐시 무효화
aws cloudfront create-invalidation \
  --distribution-id <distribution-id> \
  --paths "/*"
```

### 4.3 주의

- **환경변수는 빌드 타임에 inline됨** — 빌드 후엔 값 변경 불가. dev `.env` 가리키는 빌드를 운영에 올리는 실수 절대 금지
- `.env.production`은 *git에 커밋 가능* — 시크릿 아님 (storeId·channelKey는 *공개 키*, FE에 노출 OK)
- 단 V2 *API Secret*은 절대 FE에 두면 안 됨 — 백엔드 전용

---

## 5. PortOne 콘솔 설정

| 항목 | 값 |
|---|---|
| **Webhook URL** | `https://15.164.9.27.nip.io/api/v1/payments/webhooks/portone` |
| V2 API Secret | 운영용 발급 → BE `PORTONE_API_SECRET` 환경변수 |
| Webhook Secret | 운영용 발급 → BE `PORTONE_WEBHOOK_SECRET` 환경변수 |
| 스토어 / 채널 | 운영용 생성 + FE `.env.production` 반영 |

### Webhook URL 주의
- nip.io 도메인이 EC2 IP를 가리키므로 PortOne이 *직접 EC2*로 호출
- Nginx의 `/api/v1/payments/webhooks/portone` 경로가 Spring Boot로 forward되도록 reverse proxy 설정 확인
- HTTPS 필수 — PortOne은 HTTP webhook 호출 안 함 (Let's Encrypt SSL이 이 요구를 충족)
- EC2 IP가 바뀌면(인스턴스 재생성 등) nip.io 도메인도 바뀜 → PortOne 콘솔에 다시 등록 필요. *Elastic IP*로 IP 고정 권장

---

## 6. Nginx 설정 권장 (참고)

`/etc/nginx/sites-available/c1oud-mall`:

```nginx
server {
    listen 443 ssl http2;
    server_name 15.164.9.27.nip.io;

    # Let's Encrypt 인증서 (certbot 자동 발급)
    ssl_certificate     /etc/letsencrypt/live/15.164.9.27.nip.io/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/15.164.9.27.nip.io/privkey.pem;

    # 본문 크기 (PortOne webhook + 요청 안전 여유)
    client_max_body_size 10M;

    # 모든 /api/v1/* 요청 Spring Boot로
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }
}

# HTTP → HTTPS 강제 리다이렉트
server {
    listen 80;
    server_name 15.164.9.27.nip.io;
    return 301 https://$server_name$request_uri;
}
```

### Spring Boot 측 호환
- `X-Forwarded-Proto: https` 헤더가 Nginx에서 들어와야 Spring이 *HTTPS로 인식*
- `application-prod.yml`에 `server.forward-headers-strategy: native` 추가 권장 (Spring Boot가 X-Forwarded-* 헤더 신뢰하도록)

```yaml
server:
  forward-headers-strategy: native
```

---

## 7. Mixed Content / HTTPS 강제 — 안전망

| 시나리오 | 결과 |
|---|---|
| FE `https://` + BE `https://` (현재) | ✅ 정상 |
| FE `https://` + BE `http://` | ❌ 브라우저 Mixed Content 차단 |
| FE `http://` + BE `https://` | △ FE 자체가 안전하지 않음 (HTTP 페이지에서 PortOne SDK도 정상 동작 X) |
| PortOne webhook → BE `http://` | ❌ PortOne이 HTTP webhook 호출 안 함 |

→ 모든 경로가 `https://`여야 함. *Let's Encrypt + nip.io 조합*이 이 요구를 BE에서 충족.

---

## 8. 전체 체크리스트 (배포 직전)

### BE (EC2)
- [ ] `SPRING_PROFILES_ACTIVE=prod` 환경변수 주입
- [ ] `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 운영 DB 시크릿
- [ ] `JWT_SECRET` 운영 시크릿 (256bit+, dev 기본값 ❌)
- [ ] `PORTONE_API_SECRET` 운영 시크릿
- [ ] `PORTONE_WEBHOOK_SECRET` 운영 시크릿 (`whsec_` prefix 확인)
- [ ] `SUPER_ADMIN_EMAIL` / `SUPER_ADMIN_PASSWORD` 운영 계정
- [ ] `SecurityConfig.corsConfigurationSource` 환경변수 driven 리팩토링
- [ ] `application-prod.yml`에 `cors.allowed-origin-patterns`에 `https://deqgx2ewq7d9a.cloudfront.net` 추가
- [ ] `application-prod.yml`에 `server.forward-headers-strategy: native` 추가
- [ ] Nginx `:443` Let's Encrypt 인증서 발급 + 자동 갱신(`certbot renew`) cron 등록
- [ ] Nginx `proxy_set_header X-Forwarded-Proto $scheme` 확인
- [ ] EC2 보안 그룹: 80, 443만 0.0.0.0/0 인바운드, 8080은 차단 (Spring Boot 직접 노출 금지)
- [ ] (권장) Elastic IP로 `15.164.9.27` 고정 — IP 바뀌면 nip.io 도메인도 바뀜

### FE (S3 + CloudFront)
- [ ] `.env.production`의 `VITE_API_BASE_URL=https://15.164.9.27.nip.io`
- [ ] `.env.production`의 `VITE_PORTONE_STORE_ID` 운영
- [ ] `.env.production`의 `VITE_PORTONE_CHANNEL_KEY` 운영
- [ ] 빌드: `vite build --mode production` 명시 (dev `.env` 누락 빌드 ❌)
- [ ] S3 업로드 + CloudFront 캐시 무효화 (`/*`)

### PortOne 콘솔
- [ ] Webhook URL: `https://15.164.9.27.nip.io/api/v1/payments/webhooks/portone`
- [ ] V2 API Secret 운영용 발급 → BE 환경변수
- [ ] Webhook Secret 운영용 발급 → BE 환경변수
- [ ] 운영 스토어·채널 생성 + FE `.env.production` 반영

### 회귀 검증 (배포 직후)
- [ ] 브라우저에서 `https://15.164.9.27.nip.io/api/v1/products`로 직접 호출 → SSL 자물쇠 + 200 OK
- [ ] CloudFront FE 페이지에서 개발자도구 Network 탭:
  - API 요청이 `https://`로 나가는지 (http ❌)
  - 응답 200 + `Access-Control-Allow-Origin: https://deqgx2ewq7d9a.cloudfront.net` 헤더 확인
  - CORS 에러 0건
- [ ] `POST /api/v1/auth/signup` → `POST /api/v1/auth/login` → `GET /api/v1/auth/me` 흐름 통과
- [ ] PortOne 콘솔에서 Webhook *테스트 발송* → BE 로그에 200 OK 확인
- [ ] PortOne 샌드박스 결제 1원 e2e 통과

---

## 9. 일반 운영 가이드와의 차이

[`production-deployment-checklist.md`](production-deployment-checklist.md)는 **운영 도메인 일반론**, 본 문서는 **현재 팀의 실제 배포 구조**.

| 항목 | 일반 가이드 | 본 문서 |
|---|---|---|
| FE 도메인 | `https://c1oudmall.com` 예시 | `https://deqgx2ewq7d9a.cloudfront.net` 실제 |
| BE 도메인 | `https://api.c1oudmall.com` 예시 | `https://15.164.9.27.nip.io` 실제 |
| CORS 설정 | 일반 패턴 제시 | CloudFront origin 정확히 명시 |
| 인프라 | 추상적 (ALB / Nginx 등) | EC2 + Nginx + Let's Encrypt + nip.io 구체 |
| FE 호스팅 | 미지정 | S3 + CloudFront 구체 |

→ 본 문서는 *오늘 우리가 배포하는 환경*에 정확히 매핑. 커스텀 도메인 도입 / 인프라 변경 시 일반 가이드를 참고해 본 문서 갱신.

---

## 10. 향후 변경 트리거

| 변경 | 본 문서 갱신 항목 |
|---|---|
| CloudFront 커스텀 도메인 도입 (예: `c1oudmall.com`) | §3.2 prod CORS origin 추가, §4 FE는 변경 없음 (CloudFront 동작 유지) |
| BE 커스텀 도메인 도입 (예: `api.c1oudmall.com`) | §4 `VITE_API_BASE_URL` 변경, §5 PortOne webhook URL 변경 + Nginx `server_name` 변경 + Let's Encrypt 재발급 |
| EC2 IP 변경 (Elastic IP 사용 안 함) | §0 IP 갱신, §5 PortOne webhook URL 재등록, Let's Encrypt 재발급 |
| RDS 도입 | §2.1 `DB_URL` 형식 변경 |
| 다중 인스턴스 (ALB + 다중 EC2) | 분산 락 ADR 별도 진행, JWT_SECRET 공유 방식 검토 |

---

## 변경 이력

| 날짜 | 변경 |
|---|---|
| 2026-06-09 | 최초 작성 — CloudFront + nip.io EC2 실 배포 구조 반영 |
