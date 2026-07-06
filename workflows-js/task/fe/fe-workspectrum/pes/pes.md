# [FE PES] Product·Epic·Story 양식 (축약)

> **사용 시점**: 1~3주 스코프 · Story 3~10건 · Epic 1~3개
> sdd(풀버전)까진 필요 없고, feature-story(1 PR)보단 크다.

---

## 사용 시점 (트리거)

- 추정 작업 기간 **1~3주**
- Story 3~10건, Epic 1~3개
- 여러 화면·라우트 정합성 조정이 필요하나 도메인 재설계는 아님
- BE 짝 Product 완결 후 FE 화면 흐름 재편

---

## 섹션 구조 (3계층 축약)

### Product 레벨 (7섹션)

| # | 섹션 | 필수 |
|---|---|---|
| 1 | `# [Product] {이름}` | ✅ |
| 2 | 성과 (Outcome) | ✅ |
| 3 | 성공 지표 (KPI · Web Vitals + 사용자 지표) | ✅ |
| 4 | 범위 (Scope · In/Out) | ✅ |
| 5 | Epic 목록 (체크박스) | ✅ |
| 6 | 관련 문서 (BE 짝 · backend-boundary) | ✅ |
| 7 | 제품 완료 기준 | 선택 |

### Epic 레벨 (5섹션)

- `## [Epic N] {이름}`
- 목표 (한 문장)
- 포함 Story
- Epic 인수 시나리오 (사용자 플로우 화살표)
- Epic 완료 기준

### Story 레벨 (5섹션)

- `## [Story N-M] {이름}`
- User Story (As/I want/so that)
- 설명 (컴포넌트 · Hook · Zod · Endpoint · Route)
- 완료 기준 (AC · BDD)
- Definition of Done

---

## 양식 골격

```markdown
# [Product] {이름}

## 성과 (Outcome)
- {한 줄 — 완결 시 무엇이 달성되는가}

## 성공 지표
| 지표 | 목표 | 측정 |
| --- | --- | --- |
| Lighthouse Performance | ≥ 90 | Lighthouse CI |
| LCP (P95) | ≤ 2.5s | web-vitals RUM |
| 회귀 UX 건수 | 0건 | 수동 ux-test |

## 범위
- **In Scope**: {포함 화면·기능}
- **Out of Scope**: {제외 항목 — 사유}

## Epic 목록
- [ ] Epic 1: {제목}
- [ ] Epic 2: {제목}

## 관련 문서
- BE 짝: `workflows/products/product-{name}.md`
- BE Handoff: `workflows/task/pes/workspectrum/sdd/done/sdd-*.md`
- backend-boundary: `workflows/backend-boundary/error-codes.md`

## 제품 완료 기준
- [ ] 모든 Epic 완료
- [ ] Web Vitals 회귀 0건
- [ ] E2E 시나리오 3건 이상 통과
- [ ] backend-boundary 매핑 최신 상태

---

## [Epic 1] {이름}

### 목표
{한 문장}

### 포함 Story
- Story 1-1: {제목}
- Story 1-2: {제목}

### Epic 인수 시나리오
{사용자 플로우 · 화살표로 연결}
```
사용자 로그인 → 상품 목록 → 담기 → 장바구니 →
  선택 결제 → PortOne SDK 창 → 확정 → 주문 완료 페이지
```

### Epic 완료 기준
- [ ] Story 전부 완료
- [ ] E2E 통과 (Playwright · 해당 시)

---

## [Story 1-1] {이름}

### User Story
- As a {역할}
- I want {행위}
- so that {가치}

### 설명

**컴포넌트**:
- `src/features/{feature}/{Component}.tsx` — 역할

**Hook**:
- `const { data, isPending } = use{X}()` — `api.{feature}.{action}` 래퍼

**Zod**:
- `{X}Schema` — 필드 명세

**Endpoint**:
- `api.{feature}.{action}(input)` — `{METHOD} /api/v1/...`

**Route**:
- `/{path}` (또는 별도 라우팅 필요 시)

### 완료 기준 (AC)
- Given ... / When ... / Then ...
- *(엣지)* Given ... / When ... / Then ...
- *(예외 - 코드)* Given ... / When ... / Then {ErrorCode → UX}

### Definition of Done
- [ ] 컴포넌트 구현 (경로)
- [ ] Hook 구현 (경로)
- [ ] Zod 스키마 등록
- [ ] Endpoint 모듈
- [ ] Vitest 3+ 케이스 (해피/엣지/예외)
- [ ] a11y 검증
- [ ] backend-boundary 매핑 최신
```

---

## 참조

- **FE SDD (풀버전 · 15섹션)**: `../sdd/sdd.md`
- **FE feature-story (1 PR 티어)**: `../feature-story/feature-story.md`
- **BE PES**: `../../../pes/workspectrum/pes/`
