# [FE SDD] 양식 정의 (풀버전) — c1oud-mall 프론트엔드

> 작업 양 스펙트럼 최상위 (FE 측). 1~2개월 단위 도메인 재설계·다중 화면 정합성·인프라(CDN/도메인/CI) + 기능 묶음 작업의 표준 양식.
> 본 디렉토리(`workflows/task/fe/fe-workspectrum/sdd/`)의 `in-progress/`에 있는 실 사례가 gold standard이다.
> BE SDD와 **버전·용어·에러 코드**가 동일해야 한다 — 편차가 생기면 PR 게이트에서 지적 대상.

---

## BE SDD와의 관계 (Sync 규범)

| 축 | 정합성 유지 방식 |
|---|---|
| **Product 페어링** | FE `product-payment.md` ↔ BE `product-payment.md` (`workflows/products/`) 1:1 대응 |
| **Epic·Story 번호** | BE Story 번호를 FE에서 그대로 재사용 가능 (예: BE Story 2-4 결제 확정 API ↔ FE Story 2-4 결제 확정 페이지) |
| **ErrorCode 매핑** | `workflows/backend-boundary/error-codes.md` — SSOT. BE에 코드 추가되면 FE는 UX 액션 매핑 갱신 |
| **버전** | BE 마일스톤과 **같은 SemVer** 사용 (BE 0.0.1v ↔ FE 0.0.1v) |
| **응답 계약** | BE `ApiResponse<T>` 스키마를 FE Zod 스키마로 미러 (`src/lib/api/schemas/*.ts`) |
| **인증 계약** | BE JWT accessToken 정책 (만료 1h · Refresh 없음) → FE 401 처리 정책 일치 |

---

## 사용 시점 (트리거)

다음 조건 중 **3개 이상**을 만족할 때 본 양식을 쓴다.

- [ ] 추정 작업 기간 **1~2개월 이상** (Story 15+개 · Epic 3+개)
- [ ] **여러 라우트·화면**의 상태 일관성 개선 또는 **다중 feature** 협력 재설계
- [ ] 설계 갈림길 **3건 이상** (예: state 라이브러리 · form 라이브러리 · 라우팅 라이브러리)
- [ ] 컴포넌트 트리·상태 플로우 다이어그램이 **필수** (텍스트만으로 설명 불가)
- [ ] Web Vitals(LCP · INP · CLS) 목표를 사전 설계해야 함
- [ ] 마이그레이션 단계가 있음 (Zod 스키마 v1 → v2, 라우트 재편, 401/403 처리 정책 전환)
- [ ] FE 인프라(CloudFront · S3 · GHA · 도메인 · CORS) 변경이 함께 진행

**졸업 신호 → 완료/아카이브**:
- 모든 Epic 완료 + 핵심 FE-ADR 작성 끝
- 진실 소스(코드 · Zod 스키마 · Route · Storybook · README)에 결과 반영
- 본 파일은 `done/version/{X.Y.Zv}/`로 이동, 또는 다음 Product의 의존 문서로 인용됨

---

## 섹션 순서 (요약 표)

한 파일 안에 다음 순서로 Product 15섹션 → Epic N개 → Story N-M개가 이어진다. 순서 벗어남 자체가 리뷰 지적 대상.

### Product 레벨 (필수 15섹션)

| # | 섹션 | 필수/선택 | 목적 |
|---|---|---|---|
| 1 | `# [Product N] {이름}` | 필수 | 파일당 1개 · BE와 동일 번호 권장 |
| 2 | `## Product Vision` | 필수 | 2~3줄 인용 블록 |
| 3 | `## 배경 및 문제` | 필수 | As-Is / 발생 문제 / 왜 지금 |
| 4 | `## 목표 (To-Be)` | 필수 | 어떤 컴포넌트 · Hook · 스키마가 어떻게 바뀌는가 |
| 5 | `## 설계 결정 (Design Decisions)` | 필수 | 갈림길 결정 · 거부 옵션 근거 |
| 6 | `## 대안 검토 (Alternatives Considered)` | 필수 | 갈림길마다 Option A/B/C |
| 7 | `## 전체 아키텍처 (High-Level Architecture)` | 필수 | 컴포넌트/라우트 배치 · 핵심 플로우 · 외부 의존 |
| 8 | `## 실패 모드 / 관측 (Failure Modes & Observability)` | 필수 | ErrorCode UX 매핑 · 로깅 정책 · Web Vitals |
| 9 | `## 롤아웃 / 마이그레이션 (Rollout)` | 필수 | 전제 · Product 의존성 · Epic·Story 그래프 · 환경별 설정 분기 |
| 10 | `## 성공 지표 (KPI)` | 필수 | Web Vitals + Lighthouse + 사용자 지표 표 |
| 11 | `## Scope` | 필수 | In / Out of Scope |
| 12 | `## 대상 사용자` | 필수 | 역할별 가치 |
| 13 | `## 연결된 Epic 목록` | 필수 | 체크박스 리스트 |
| 14 | `## 관련 문서` | 필수 | BE Product · FE-ADR · backend-boundary 갱신 지점 |
| 15 | `## 열린 질문 (Open Questions)` | 필수 | 미결 항목 · 다음 결정 트리거 |
| 15+ | `## 제품 수준 완료 기준 (Product-level DoD)` | 선택 | 대형 Product는 §Scope 뒤에 추가 |

### Epic 레벨 (Product 아래 이어짐)

| # | 섹션 | 필수/선택 |
|---|---|---|
| 1 | `# [Epic N] {이름}` | 필수 |
| 2 | `## 목표` | 필수 |
| 3 | `## 배경` | 필수 |
| 4 | `## 포함 Story` | 필수 |
| 5 | `## Epic 인수 시나리오` | 선택(권장) — 사용자 플로우 화살표 |
| 6 | `## Epic 완료 기준 (DoD)` | 필수 |
| 7 | `## 내부 메모 / 제약 사항` | 선택 |
| 8 | `## BE Epic 참조` | 선택 — 대응 BE Epic 링크 |

### Story 레벨 (Epic 아래 이어짐)

| # | 섹션 (H3) | 필수/선택 |
|---|---|---|
| 1 | `## [Story N-M] {이름}` | 필수 (H2) |
| 2 | `### User Story` | 필수 |
| 3 | `### 설명` | 필수 (컴포넌트 · Hook · Zod · Endpoint · Route 명시) |
| 4 | `### 완료 기준 (AC)` | 필수 (BDD Given/When/Then) |
| 5 | `### Definition of Done` | 필수 |
| 6 | `### 스토리 포인트` | 필수 |
| 7 | `### 의존성` | 필수 (BE Story · FE 선행/후행) |
| 8 | `### [명세 변경 이력]` | 선택 |

---

## 양식 골격 상세

### Product 레벨

```markdown
# [Product N] {이름}

## Product Vision
> {2~3줄 인용 블록}

## 배경 및 문제
- 현재 상황 (As-Is)
  - {화면/컴포넌트/훅 현 상태 1}
  - {현 상태 2}
- 발생하는 문제
  - {UX/성능/유지보수 문제 1}
- 왜 지금 해결해야 하는가
  - {BE Product 완결 · 사용자 지표 등 타이밍 사유}

## 목표 (To-Be)
- {목표 1 — 어떤 컴포넌트/훅/스키마가 어떻게 바뀌는가}

## 설계 결정 (Design Decisions)
- **{결정 1}**
  - {핵심 근거}
- **{결정 2}**
  - ...

## 대안 검토 (Alternatives Considered)

### {갈림길 1}
**Option A — {요지}**
- 장점: ...
- 거부 이유: ...

**Option B (선택) — {요지}**
- 비용: ...
- 보상: ...

**Option C — {요지}**
- 거부 이유: ...

## 전체 아키텍처 (High-Level Architecture)

### 컴포넌트 / 라우트 배치
`​`​`
src/
├── app/
│   ├── router.tsx           # Route 정의
│   └── providers/           # QueryClient, Theme 등
├── features/
│   ├── payment/
│   │   ├── PaymentConfirmPage.tsx
│   │   ├── usePaymentConfirm.ts
│   │   └── PaymentSummary.tsx
│   └── ...
├── lib/
│   ├── api/
│   │   ├── endpoints/       # api.payment.confirm(...) 등
│   │   └── schemas/         # Zod 스키마 (BE ApiResponse 미러)
│   └── auth/                # JWT 처리
└── shared/
    └── ui/                  # 공용 컴포넌트
`​`​`

### 핵심 플로우
**1. {플로우명 — 예: 결제 확정}**
`​`​`
Client Input
  → 폼 Zod 검증
    → useMutation (api.payment.confirm)
       → 성공: /orders/{id}/complete로 라우팅 + toast
       └── 실패:
            ├── PAY001 → inline error "금액 재조회 필요"
            ├── C003   → 접근 거부 모달
            └── 502    → 재시도 안내 toast
`​`​`

### 외부 의존
- **BE API**: `${VITE_API_BASE_URL}/api/v1/*` — `ApiResponse<T>` envelope
- **PortOne SDK**: `@portone/browser-sdk/v2` — 결제 창 오픈
- **CloudFront/S3**: 정적 자산 배포 (계획)

## 실패 모드 / 관측 (Failure Modes & Observability)

### ErrorCode → UX 액션 매핑
| ErrorCode | HTTP | UX 액션 |
| --- | --- | --- |
| `PAY001` PAYMENT_AMOUNT_MISMATCH | 400 | inline error + 재조회 버튼 |
| `PAY002` PORTONE_QUERY_FAILED | 502 | toast "일시적 오류 · 재시도" |
| `C003` ACCESS_DENIED | 403 | 접근 거부 modal + 홈 이동 |
| `C004` UNAUTHORIZED | 401 | 로그인 페이지로 강제 이동 |

> 완전한 매핑은 `workflows/backend-boundary/error-codes.md` (SSOT).

### 로깅 정책
- **항상 기록** (Sentry breadcrumb):
  - `requestId`(X-Request-Id 응답 헤더), `errorCode`, HTTP status, route
- **debug**: TanStack Query cache 상태 (개발 모드만)
- **절대 금지**:
  - JWT 원문 · 카드번호 · 사용자 입력 반영(reflection)

### 관측 지표
- **Web Vitals**: LCP · INP · CLS (`web-vitals` 라이브러리)
- **Lighthouse CI**: PR마다 90+ 유지
- **번들 크기**: 초기 로드 chunk 예산 (예: 300KB gzipped)

## 롤아웃 / 마이그레이션 (Rollout)

### 전제
{현재 사용자 수 · 트래픽 · 마이그레이션 방식}

### Product 의존성
- 선행 BE Product: {`workflows/products/product-{name}.md`}
- 선행 FE Product: {링크}
- 후행: {링크}

### Epic·Story 의존성 그래프
`​`​`
Epic 1 ──► Epic 2 ──► Epic 3
              │
              └─► Epic 4
`​`​`

### 환경별 설정 분기
| 항목 | dev | prod |
| --- | --- | --- |
| `VITE_API_BASE_URL` | `http://localhost:8080` | `https://api.c1oud-mall.dev` |
| MSW | 활성 (선택) | 비활성 |
| Sentry | dev DSN | prod DSN |
| SameSite/Secure | Lax / off | None / on |
| PortOne 채널 키 | 테스트 | 운영 |

## 성공 지표 (KPI)
| 지표 | 목표 값 | 측정 방법 |
| --- | --- | --- |
| Lighthouse Performance | ≥ 90 | Lighthouse CI (main PR) |
| LCP (P95) | ≤ 2.5s | web-vitals RUM |
| INP (P95) | ≤ 200ms | web-vitals RUM |
| CLS | ≤ 0.1 | web-vitals RUM |
| 초기 번들 (gzipped) | ≤ 300KB | rollup-plugin-visualizer |
| 401 refresh 성공률 | ≥ 99% | Sentry 이벤트 카운트 |
| UX 회귀 건수 | 0건 | 수동 ux-test 체크리스트 |

## Scope
**In Scope**:
- {포함 1}
- {포함 2}

**Out of Scope**:
- {제외 1} — 사유
- {제외 2} — 사유

## 대상 사용자
- {역할 1 — 어떤 가치}
- {역할 2 — 어떤 가치}

## 연결된 Epic 목록
- [ ] Epic 1: {제목}
- [ ] Epic 2: {제목}

## 관련 문서
- **BE Product (짝)**: `workflows/products/product-{name}.md`
- **BE Handoff 리포트**: `workflows/task/pes/workspectrum/sdd/done/sdd-{name}-integration-report.md`
- **관련 FE-ADR**: {`FE-ADR-CANDIDATES.md` 항목 참조}
- **backend-boundary 갱신 예정 섹션**: `workflows/backend-boundary/error-codes.md` (ErrorCode UX 매핑)
- **FE 통합 가이드**: `workflows/task/pes/workspectrum/sdd/done/sdd-fe-integration-guide.md`

## 열린 질문 (Open Questions)
- {미결 1}
- {미결 2}

## 제품 수준 완료 기준 (Product-level DoD)
- [ ] 모든 Epic DoD 통과
- [ ] Lighthouse 90+ · Web Vitals 목표 달성
- [ ] backend-boundary/error-codes.md 매핑 최신 상태
- [ ] Zod 스키마가 BE `ApiResponse<T>` 스펙과 정합 (통합 테스트 통과)
- [ ] FE-ADR N건 발행 (해당 시)
```

### Epic 레벨

```markdown
# [Epic N] {이름}

## 목표
{한 문장 — Epic이 끝났을 때 무엇이 가능한지}

## 배경
{본 Epic이 Product 전체에서 차지하는 위치 + 선행 의존}

## 포함 Story
- Story N-1: {제목}
- Story N-2: {제목}

## Epic 인수 시나리오
- Given {선행 상태 — 로그인 완료 등}
- When {트리거 — 사용자가 결제 버튼 클릭}
- Then {관측 결과 — 결제 페이지 이동 + PortOne SDK 오픈}

*(엣지)* Given ... / When ... / Then ...

## Epic 완료 기준 (DoD)
- [ ] 포함 Story 모두 완료
- [ ] E2E 테스트 (Playwright · 해당 시) 통과
- [ ] 스크린 리더 접근성 검증 (a11y)
- [ ] Lighthouse 회귀 없음

## BE Epic 참조
- BE Product · Epic: `workflows/products/product-{name}.md#epic-N`
- 계약 갱신 시 `backend-boundary/error-codes.md` 동기 확인
```

### Story 레벨

```markdown
## [Story N-M] {이름}

### User Story
- As a {역할 — 구매자}
- I want {원하는 행위 — 결제 완료 후 즉시 주문 상세로 이동}
- so that {얻는 가치 — 결제 결과 확인 · 재구매 유도}

### 설명
{구현 단서 — 정확한 컴포넌트 경로 · Hook 시그니처 · Zod 필드 · Endpoint · Route}

**핵심 컴포넌트**:
- `src/features/payment/PaymentConfirmPage.tsx` — 역할 한 줄
- `src/features/payment/PaymentSummary.tsx` — 역할 한 줄

**Hook 시그니처**:
- `const { mutate, isPending } = usePaymentConfirm()` — `api.payment.confirm(input)` 래퍼

**Zod 스키마**:
- `PaymentConfirmInputSchema` — `orderId: string`, `portonePaymentId: z.string().uuid()`
- `PaymentConfirmResponseSchema` — `ApiResponse<PaymentConfirmResponse>` 미러

**Endpoint 모듈**:
- `api.payment.confirm(input)` — `POST /api/v1/payments/confirm`

**Route**:
- `/orders/:orderId/pay` (Confirm 트리거) → 성공 시 `/orders/:orderId/complete`

### 완료 기준 (AC)
- Given 인증 사용자 · 유효 `portonePaymentId` / When 결제 확정 버튼 클릭 / Then 200 · `success=true` → `/orders/:orderId/complete` 라우팅
- *(엣지 - 금액 불일치)* Given `PAY001` 응답 / When 응답 처리 / Then inline error "결제 금액이 변경됨 · 재조회" + 재시도 버튼
- *(예외 - 401)* Given accessToken 만료 / When 요청 / Then 로그인 페이지로 강제 이동 + toast "재로그인 필요"

### Definition of Done
- [ ] 컴포넌트 구현 (`src/features/payment/PaymentConfirmPage.tsx`)
- [ ] Hook 구현 (`src/features/payment/usePaymentConfirm.ts`)
- [ ] Zod 스키마 등록 (`src/lib/api/schemas/payment.ts`)
- [ ] Endpoint 모듈 (`src/lib/api/endpoints/payment.ts`)
- [ ] 단위 테스트 (Vitest · 해피/엣지/예외 각 케이스)
- [ ] E2E 테스트 (Playwright · 해당 시)
- [ ] `backend-boundary/error-codes.md`의 관련 코드가 UX 매핑에 반영됨
- [ ] Sentry breadcrumb 확인 (개발 모드)
- [ ] a11y 검증 (스크린 리더 · 키보드 네비게이션)

### 스토리 포인트
{0.5d / 1d / 2d / 3d — Estimable 미달이면 분할}

### 의존성
- 선행 BE Story: {BE Product Story 링크 — 예: BE Story 2-4 결제 확정 API}
- 선행 FE Story: {있으면}
- 후행: {있으면}

### [명세 변경 이력]
- YYYY-MM-DD: {원안 → 실제 구현} 차이 + 사유
```

---

## 섹션 작성 가이드

| 원칙 | 적용 |
| --- | --- |
| **한 파일 = 한 Product** | Product/Epic/Story 수직 통합. 별도 spec/plan/tasks 파일로 쪼개지 않음 |
| **거부된 옵션도 합리적 근거 명시** | 트레이드오프 드러냄 |
| **ASCII 다이어그램** | 외부 도구 의존 X. 컴포넌트 트리·라우트 그래프·상태 플로우 모두 ASCII |
| **실패 시나리오 표를 사전 설계** | ErrorCode → UX 액션 매핑을 구현 전에 확정 |
| **Zod 스키마가 진실 소스** | `src/lib/api/schemas/*.ts`가 BE ApiResponse 계약의 FE 진실 소스 |
| **추정 식별자 금지** | 컴포넌트 경로 · Hook 이름 · Zod 필드는 코드에서 확인해 쓴다 |
| **FE-ADR 트리거** | 새 라이브러리·정책 결정은 별도 FE-ADR (`FE-ADR-CANDIDATES.md`에 초안 → 승격) |
| **Web Vitals는 KPI 필수** | LCP · INP · CLS + Lighthouse 90+ |
| **CORS·SameSite 명시** | dev/prod 분기 표에 반드시 기재 |
| **backend-boundary 갱신** | BE ErrorCode 추가/변경 시 FE UX 매핑도 함께 갱신 |

---

## 예시 파일 (gold standard)

`in-progress/`에 실 사례 축적 시 여기 링크. 초기에는 비어 있음.

- `in-progress/product-payment.md` — BE `product-payment.md`의 FE 짝
- `in-progress/product-refund.md` — BE `product-refund.md`의 FE 짝
- `in-progress/product-auth.md` — 로그인·JWT 흐름 (401 재로그인 강제 등)
- `in-progress/product-cart.md` — 장바구니 UI + `cartItemId` 통합
- `in-progress/product-checkout.md` — 주문 미리보기 → PortOne SDK → 확정

---

## fix 레이어 (진행 중 계획 변경 흡수)

FE SDD Product 진행 중 발견되는 정책 충돌·UX 회귀·요구사항 변화는 별도 `fix/` 폴더로 흡수.

- 위치: `sdd/fix/fix-{핵심주제}.md`
- Fix-Story는 SDD Story 골격 그대로 사용
- 완료 시 원본 Product SDD의 해당 Story에 `[명세 변경 이력]` 블록만 남기고 아카이브

---

## 워크플로우 위치

```
FE 명령 수신
   ↓
CLAUDE.md → workflow.md → 대상 판정 (BE 짝 Product 확인)
   ↓                        ↓
   │        {복잡도·기간·화면 폭 판단}
   │                        ↓
   │      1건·1시간         ─────► one-line-spec
   │      1건·1~3일         ─────► feature-story (1 PR)
   │      3~10 Story        ─────► pes (Product·Epic·Story · 축약)
   │      Story 5+·다중 화면 ─────► sdd-lite (또는 BE→FE 핸드오프)
   │      Story 15+·1~2M    ─────► ★ sdd (본 양식) ★
   │                        ↓
   │        본 sdd.md 참조 · `in-progress/product-*.md` 작성
   ↓
Plan mode → Story 순차 실행 → BE 짝 Story와 sync → PR
```

---

## 참조

- **BE SDD (본 양식의 원본)**: `../../../pes/workspectrum/sdd/sdd.md`
- **backend-boundary** (BE↔FE 정합성 SSOT): `../../../../backend-boundary/error-codes.md`
- **BE Products**: `../../../../products/product-*.md`
- **FE-ADR 인덱스**: `../FE-ADR-CANDIDATES.md`
- **FE 마일스톤**: `../../fe-milestones/version/{X.Y.Zv}/`
- **팀 컨벤션 (BE SSOT)**: `CLAUDE.md` · `.claude/rules/*.md` (FE는 이 중 dto.md 응답 계약 · exception.md 에러 코드 규범만 반영)
- **양식 진화**: SemVer로 진화. 변경 시 `version/{X.Y.Zv}/` 아래 새 버전 배치, 본 버전은 보존
