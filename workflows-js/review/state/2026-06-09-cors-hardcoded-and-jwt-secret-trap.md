# [트러블슈팅] 운영 배포 직전 — SecurityConfig CORS 하드코딩 발견 + JWT 시크릿 dev 기본값 운영 잔존 위험

---
date: 2026-06-09
domain: [infra, security, deployment]
tags: [cors, env-var-discipline, dev-default-leak, secret-management, profile-separation]
related-doc: workflows/living-docs/production-deployment-checklist.md
related-topology: workflows/topologys/Authentication-Authorization-Topology.md, workflows/topologys/API-Topology.md
---

## 1. 문제 (What)

운영 배포 체크리스트(`production-deployment-checklist.md`)를 작성하면서 *지금 운영에 띄우면 무엇이 문제인가*를 코드 단위로 추적. 두 가지 시한폭탄 발견.

### 발견 1: CORS allowed origins가 SecurityConfig에 하드코딩

`SecurityConfig.java:83-86`:
```java
config.setAllowedOriginPatterns(List.of(
    "http://localhost:*",
    "http://127.0.0.1:*"
));
```

운영 FE 도메인(`https://c1oudmall.com`)이 *코드에 없음*. 운영 띄우면 FE 요청이 *모두 CORS 차단*. 컨테이너 환경변수로 해결 불가 — *코드 수정 필요*.

### 발견 2: `JWT_SECRET` dev 기본값이 운영 누락 시 자동 적용

`application.yml`:
```yaml
jwt:
  secret: ${JWT_SECRET}
```

`application-dev.yml`:
```yaml
jwt:
  secret: ${JWT_SECRET:dev-jwt-secret-for-local-and-test-environment-only}
```

`SPRING_PROFILES_ACTIVE=prod`로 띄우면 dev profile이 안 로드되어 *dev 기본값도 사용 안 됨* → 부팅 실패. **단** 누군가 실수로:
- `SPRING_PROFILES_ACTIVE`를 안 줘서 기본값(`dev`)이 살아남는 경우
- 운영 컨테이너에 dev profile을 *명시적으로* 활성화한 경우

→ `dev-jwt-secret-for-local-and-test-environment-only`로 *진짜 운영 JWT가 서명됨*. 토큰 위조 가능. 보안 사고.

같은 함정이 `PORTONE_WEBHOOK_SECRET`에도 있음 — placeholder `whsec_ZGV2LXBsYWNlaG9sZGVyLXNlY3JldC1mb3ItYm9vdC1vbmx5`.

## 2. 문제 해결 방법 (How)

### CORS — 환경변수 driven으로 변경 권장 (체크리스트 §2)
1. `SecurityConfig`에 `@Value("${cors.allowed-origin-patterns}")` 주입
2. `application-prod.yml`:
   ```yaml
   cors:
     allowed-origin-patterns:
       - https://c1oudmall.com
       - https://www.c1oudmall.com
   ```
3. `application-dev.yml`:
   ```yaml
   cors:
     allowed-origin-patterns:
       - http://localhost:*
       - http://127.0.0.1:*
   ```
4. 코드 수정 1곳, 운영·dev profile별 yml에 각자 정확한 origin 명시.

### JWT 시크릿 dev 기본값 — *주입 강제 + dev 기본값 운영 잠금*
1. 운영 진입 시 **`SPRING_PROFILES_ACTIVE=prod` 명시 강제** — 체크리스트 §1 최우선 항목으로 박음.
2. 시크릿은 K8s Secret / AWS Secrets Manager / Vault 등 *secret store*로 주입 (평문 env 파일 금지) — 체크리스트 §1 "보안 주의".
3. dev 기본값을 운영에 절대 쓰지 말 것을 체크리스트에 *명시적 경고*:
   > `JWT_SECRET`은 dev 기본값(`dev-jwt-secret-for-local-and-test-environment-only`)을 **절대 운영에서 쓰지 말 것** — 토큰 위조 가능
   > `PORTONE_WEBHOOK_SECRET` dev placeholder도 운영 사용 금지
4. (선택, 권장) 운영 부팅 시 시크릿이 *dev 기본값과 일치하면 부팅 실패* 가드 도입 — 별도 작업.

## 3. 방식 (Why this way)

### CORS — 환경변수 driven vs 프로파일별 yml 코드 분기
- 옵션 (A): SecurityConfig에 `@Profile` 분기로 prod/dev 빈 분리
- 옵션 (B): yml에 origin 리스트 정의 + Spring `@Value` 주입 ← 채택
- (A)는 코드 두 곳 — 운영 도메인 추가 시 코드 수정 필요
- (B)는 yml 한 곳 — 운영 도메인 추가/변경 시 yml만, 코드 무수정. 환경변수로도 오버라이드 가능
- 단 yml 분리도 *결국 빌드 산출물*에 들어가는 거라 *외부 secret store*에 둘 수는 없음. CORS origin은 secret이 아니라 *환경별 설정*이라 yml로 충분.

### JWT 시크릿 dev 기본값 — 왜 코드에서 제거하지 않나
- 옵션 (X): `application-dev.yml`에서 dev 기본값을 *완전히 제거* → 로컬 개발 시에도 환경변수 강제
- 옵션 (Y): dev 기본값은 유지 + 운영 가드로 막음 ← 채택
- (X)는 로컬 개발 시작 비용 ↑ — 새 개발자가 환경변수 세팅까지 해야 부팅. 진입 장벽이 큼.
- (Y)는 *로컬 편의 유지* + *운영은 별도 가드*. 가드는:
  - `SPRING_PROFILES_ACTIVE=prod` 명시 강제 (체크리스트)
  - secret store로 시크릿 주입 (체크리스트)
  - (선택) 부팅 시 dev 기본값 일치 검증 (미래 작업)
- 단 (Y)의 *가드가 누락되면* 사고. 그래서 체크리스트에 *최우선 항목*으로 박음.

### `/h2-console/**` permitAll 라인도 점검 권장
- `SecurityConfig.java:52`에 박혀있음. prod에선 h2-console 자체가 `enabled: false`라 404로 차단됨 — *직접 영향 없음*.
- 그러나 *depth-in-defense* 차원에서 prod 프로파일에선 이 라인을 빼는 게 안전 — 체크리스트 §3에 *권장*으로 표시.

## 4. 결과 (Outcome)

- **`workflows/living-docs/production-deployment-checklist.md` 신규 작성** — 도메인·시크릿·FE 설정만 정리한 운영 배포 단일 참조 문서.
- 8개 섹션 + 마지막 체크박스 리스트 (BE / FE / PortOne 콘솔 / 회귀 검증)
- 발견 1 (CORS): 체크리스트 §2에 *코드 수정 1곳* 명시 + 변경 패턴 코드 예시
- 발견 2 (JWT/Webhook 시크릿): 체크리스트 §1에 *환경변수 9개* 표 + "보안 주의" 박스로 dev 기본값 잠금 경고
- 운영 진입 *후* 정기 reconciliation 잡 3개도 §3에 명시 (Payment FAILED 불일치 / Refund DB_COMMITTED 잔존 / WebhookEvent 90일 아카이브)
- AI-ADR 작성·환불 도메인 회고·FE 가이드 v3 작성하면서 *기능 측면만 본 시점*에서, 운영 측면을 *늦지 않게 별도로 추적*한 결과물

## 5. 좋아진 점 (What got better)

- **"기능 동작"과 "운영 안전"을 *분리 추적*하는 습관.** 기능 ADR이 끝났다고 다 끝난 게 아님. *운영 진입 전에 봐야 할 것*은 별도 체크리스트로 관리. 이번 발견 2개가 *기능 ADR엔 안 보이는* 영역 — CORS·시크릿·프로파일은 *기능과 직교*.
- **dev 기본값의 시한폭탄 인식.** 로컬 편의를 위한 기본값이 *운영에 잔존하면 보안 사고*. 같은 함정이 향후 *Slack secret, AWS access key* 등 다른 시크릿에서 반복될 가능성. "dev 기본값을 두면 *항상* 운영 가드를 같이 둔다"는 페어 룰로 정착.
- **CORS 같은 *환경별 설정*과 시크릿 *secret store* 구분 인식.** 둘 다 환경변수로 다룰 수 있지만 *보안 등급이 다름*. CORS origin은 yml에 박아도 OK (정보 공개 OK), JWT 시크릿은 secret store 필수. 이 구분이 명확하면 *어디까지 git에 넣어도 되는지* 결정이 쉬워짐.
- **체크리스트가 "검토 → 누락 발견 → 보강" 흐름의 마지막 그물.** AI-ADR과 일반 ADR이 *결정*을 담는다면, 체크리스트는 *결정 후에도 누락될 수 있는 것*을 잡음. 다음 Product 트랙에서도 *기능 ADR 작성 + 운영 체크리스트 동시 유지*가 표준 루틴.
- **"운영 진입 직전이 가장 좋은 보안 점검 타이밍"이 입증.** 기능 작업 중엔 무시되기 쉬운 보안 함정이 *체크리스트 작성 행위 자체*가 트리거가 되어 드러남. 향후 Product 단위로 운영 진입 시 동일 루틴 반복.
