# [FE-ADR-CANDIDATES] c1oud-mall 프론트엔드 ADR 후보 우선순위

> **목적**: FE 아키텍처·라이브러리·정책 결정 후보를 우선순위(P0/P1/P2)로 축적. 승격 시 정식 ADR로 발행.
> **위치 규칙**: 정식 ADR은 `workflows/living-docs/Ai-adr/`에 `FE-XXX-{slug}.md` 형식으로 발행 (BE ADR과 동일 위치, `FE-` 접두어로 구분)
> **BE ADR과의 관계**: BE ADR 결정에 FE가 따라야 하는 부분은 §정합 매트릭스 참조

---

## 상태 정의

| 상태 | 의미 |
|---|---|
| **candidate** | 후보 단계 · 결정 트리거 발생 대기 |
| **in-progress** | 결정 논의 중 |
| **decided** | 결정 완료 · 정식 ADR 발행 |
| **deferred** | 미래로 이월 · 트리거 조건 명시 |
| **rejected** | 채택 안 함 · 사유 명시 |

---

## P0 — 즉시 결정 필요 (배포 전 · 배포 직후)

### FE-ADR-P0-001: JWT accessToken 저장 위치
- **상태**: decided (v1 · BE 정책 준수)
- **결정**: `localStorage`에 저장 (HttpOnly Cookie 아님 · Refresh 없음)
- **사유**: BE가 Stateless JWT · 만료 1시간 · Refresh 미도입 · v1 스코프에 부합
- **트레이드오프**: XSS 시 토큰 노출 리스크 · Refresh Token 도입 시 재검토
- **재검토 트리거**: 실 사용자 100명+ · XSS 발생 · Refresh Token 도입 시
- **BE 연관**: BE `SecurityConfig` · CLAUDE.md JWT 정책

### FE-ADR-P0-002: 서버 상태 관리 라이브러리
- **상태**: candidate
- **후보**: TanStack Query (v5) · SWR · Redux Toolkit Query
- **1차 권장**: **TanStack Query** (커뮤니티 · Devtools · Suspense 지원)
- **결정 트리거**: 첫 Product 착수 전
- **BE 연관**: 없음

### FE-ADR-P0-003: 라우팅 라이브러리
- **상태**: candidate
- **후보**: React Router v6 · TanStack Router · Next.js App Router
- **1차 권장**: **React Router v6** (Vite 조합에서 표준 · 학습 곡선 낮음)
- **결정 트리거**: 첫 Product 착수 전
- **BE 연관**: 없음

### FE-ADR-P0-004: 응답 스키마 소스
- **상태**: decided
- **결정**: **Zod 스키마** (`src/lib/api/schemas/*.ts`) — BE `ApiResponse<T>`의 FE 진실 소스
- **사유**: 런타임 검증 · TypeScript 타입 추론 · BE 계약 변경 감지
- **BE 연관**: `workflows/backend-boundary/error-codes.md` · BE `common.response.ApiResponse`

### FE-ADR-P0-005: PortOne SDK 통합 방식
- **상태**: candidate
- **후보**: `@portone/browser-sdk/v2` 직접 사용 · 커스텀 래퍼
- **1차 권장**: **직접 사용 + 얇은 래퍼** (`src/lib/portone/*.ts`)
- **결정 트리거**: 결제 Product 착수 전
- **BE 연관**: BE ADR 001 (`portone-payment-id-uuid`) — FE는 서버가 채번한 `portonePaymentId`를 SDK에 넘김

---

## P1 — 진행 중 · 결정 근접

### FE-ADR-P1-001: 폼 라이브러리
- **상태**: candidate · 폼 3개 이상 시 결정
- **후보**: react-hook-form + Zod resolver · native form · Formik
- **1차 권장**: **react-hook-form + zod resolver** (Zod 이미 채택 · 성능 우수)
- **결정 트리거**: 결제 Confirm 폼 · 회원가입 · 환불 요청 3개 폼 확정 시

### FE-ADR-P1-002: 컴포넌트/디자인 시스템
- **상태**: candidate
- **후보**: shadcn/ui + Tailwind · MUI · Chakra UI
- **1차 권장**: **shadcn/ui + Tailwind CSS** (경량 · 커스텀 자유도)
- **결정 트리거**: 초기 프로토타입 후

### FE-ADR-P1-003: CORS · SameSite · Cookie 정책
- **상태**: candidate · BE 프로파일 분기와 동기
- **필요 결정 매트릭스**:
  | 항목 | dev | prod |
  |---|---|---|
  | SameSite | Lax | None (Secure 필요) |
  | HttpOnly | (accessToken은 localStorage · Cookie 미사용) | 동일 |
  | CORS credentials | `include` (필요 시) | `include` (동일) |
- **BE 연관**: `workflows/task/fix/brainstorming/version/0.0.1v/infra.md` Issue 1 (CORS 프로파일 분기)

### FE-ADR-P1-004: 환경변수 정책
- **상태**: candidate
- **결정 필요 항목**:
  - `VITE_API_BASE_URL` — dev `http://localhost:8080` · prod `https://api.c1oud-mall.dev`
  - `VITE_PORTONE_STORE_ID` · `VITE_PORTONE_CHANNEL_KEY` (FE 공개 OK)
  - `VITE_SENTRY_DSN` (환경별 분리)
  - 디버그 스위치 (`VITE_DEBUG_QUERY` 등)
- **관리**: `.env.development` · `.env.production` · `.env.local` (gitignore)

### FE-ADR-P1-005: ErrorCode UX 매핑 유지 정책
- **상태**: candidate
- **결정 필요**: BE `ErrorCode` enum이 확장될 때 FE 매핑 갱신 프로세스
- **1차 권장**:
  - BE PR에서 ErrorCode 추가 시 `workflows/backend-boundary/error-codes.md` 함께 갱신 강제
  - FE PR에서 `src/lib/api/errors.ts`의 매핑 코드 갱신
  - Vitest로 매핑 누락 감지 (모든 코드에 액션 등록됨을 assertion)

### FE-ADR-P1-006: Optimistic Update 정책
- **상태**: candidate · 사용자 지표 필요 시
- **후보**: `useOptimisticMutation` 커스텀 훅 표준화
- **트리거**: 카트 담기 · 즐겨찾기 등 즉시 반영 필요 UX 도입 시

### FE-ADR-P1-007: CloudFront · S3 · GHA FE 배포
- **상태**: candidate
- **후보 배포 파이프라인**:
  - GHA `.github/workflows/deploy-fe.yml`
  - S3 정적 자산 업로드 + CloudFront invalidation
  - Cache-Control: HTML `no-cache` · JS/CSS `max-age=31536000, immutable`
- **BE 연관**: 없음 (별도 파이프라인)

### FE-ADR-P1-008: Zod 스키마 파일 위치
- **상태**: decided
- **결정**: `src/lib/api/schemas/` (도메인별 분리 · `types/` 아님)
- **사유**: 스키마 = 진실 소스 · types는 스키마 파생

### FE-ADR-P1-009: Lighthouse CI 통합
- **상태**: candidate
- **후보**: GHA step (`treosh/lighthouse-ci-action`) · 별도 워크플로우
- **1차 권장**: PR 단계 GHA step (배포 전)

### FE-ADR-P1-010: Sentry + requestId breadcrumb
- **상태**: candidate
- **후보**: axios/fetch interceptor에서 응답 헤더 `X-Request-Id`를 Sentry breadcrumb에 자동 첨부
- **트리거**: BE Log Product Epic 2 (MDC requestId) 완결 후

### FE-ADR-P1-011: PortOne 결제 창 콜백 처리
- **상태**: candidate
- **결정 필요**: 팝업 vs 리다이렉트 · `portonePaymentId` 손실 시 복구 경로

---

## P2 — 미래 · 정보 축적 중

- **P2-001**: i18n (한국어 → 다국어) — 사용자 지역 확장 시
- **P2-002**: 디자인 토큰 (Tailwind v4 inline vs CSS variables + dark mode)
- **P2-003**: 커맨드 팔레트 (⌘K 검색 진입)
- **P2-004**: Service Worker + 오프라인 캐시
- **P2-005**: FCM 웹 푸시 알림
- **P2-006**: A/B 테스트 인프라 (LaunchDarkly 등)
- **P2-007**: 접근성(a11y) 자동 검사 (axe-core CI)
- **P2-008**: SSR/ISR 전환 (Next.js) — SEO 요건 발생 시

---

## BE ADR ↔ FE-ADR 정합 매트릭스

BE ADR 결정에 FE가 반드시 정합을 맞춰야 하는 항목:

| BE ADR | 내용 | FE 대응 |
|---|---|---|
| ADR 001 `portone-payment-id-uuid` | 서버 채번 UUID · DB UNIQUE | FE는 서버에서 받은 `portonePaymentId`를 PortOne SDK에 그대로 전달 (클라 채번 금지) |
| ADR 002 `payment-confirmation-side-effects-order` | 외부 호출은 TX 밖 · DB 커밋은 TX 안 | FE는 confirm 응답을 신뢰 · 부수효과는 BE 관장 |
| ADR 004 `payment-confirmation-validation-order` | 검증 순서 확정 | FE는 400 응답의 ErrorCode를 신뢰 · 자체 재검증 X |
| ADR 005 `webhook-idempotency-insert-first` | 웹훅 멱등성 | FE 무관 (BE↔PG 내부) |
| ADR 006 `bc-collaboration-direct-call` | BC 간 직접 호출 | FE 무관 |
| ADR 008 `refund-unit-orderitem-quantity` | 환불 단위 = `orderItemId + quantity` | FE 환불 요청 폼 필드 정합 (`orderItemId` · `quantity` · 금액 미입력) |
| ADR 009 `refund-amount-split-floor-policy` | 환불 금액 분할 정책 | FE는 서버 산정 금액을 표시만 (재계산 X) |
| ADR 011 `portone-cancel-port-extension` | 취소 포트 확장 (`amount` · `requestKey`) | FE 무관 (BE↔PG) |

---

## 참조

- **BE ADR 인덱스**: `workflows/living-docs/Ai-adr/README.md`
- **backend-boundary SSOT**: `workflows/backend-boundary/error-codes.md`
- **FE SDD**: `sdd/sdd.md`
- **팀 컨벤션 (BE SSOT)**: `CLAUDE.md`
