# [Fix · Brainstorming] c1oud-mall — 도메인별 이슈 브레인스토밍

> **역할**: SDD Product가 `in-progress`로 굴러가는 도중에 발견되는 결함·정책 충돌·리팩토링·요구사항 변화를 각 도메인별로 축적하는 자유 필드.
> **위치 규칙**: `version/{X.Y.Zv}/{domain}.md` — 도메인 하나당 1 파일. 여러 이슈를 누적하다가 결정이 굳으면 정식 fix tier 문서로 승격.

---

## 도메인 파일 구성

`version/0.0.1v/` 하위에 도메인별로 파일을 하나씩 둔다.

| 파일 | 담당 범위 | 짝(Pair) |
|---|---|---|
| `payment.md` | 결제(Payment) BC | ↔ `order.md` (주문 확정 흐름) |
| `refund.md` | 환불(Refund) BC | ↔ `payment.md` (부분 취소·비율 분리) |
| `order.md` | 주문(Order) BC | ↔ `payment.md`, `cart.md` |
| `cart.md` | 장바구니(Cart) BC | ↔ `order.md`, `product.md` |
| `product.md` | 상품(Product) BC | ↔ `cart.md`, `order.md` |
| `user.md` | 사용자(User) BC | ↔ `auth` 관련 이슈 |
| `ai-suggestion.md` | AI 제안 (Gemini 연동) | 독립 |
| `log.md` | 관측성 / 로깅 | 크로스 컷팅 |
| `infra.md` | 인프라 · 배포 · CI/CD | 크로스 컷팅 |

새 도메인이 필요하면 이 표에 추가한 뒤 파일 생성.

---

## 각 파일 내부 규격 (표준 템플릿)

한 파일에는 다음 순서로 이슈를 누적한다.

```markdown
# [Fix · Brainstorming] {Domain} — {Version} ({Date})

> **역할**: {도메인명} BC의 실행 중 발견 이슈를 가설·결정으로 축적
> **짝 파일**: {Pair 파일 상대경로}
> **다음 단계**: 각 이슈 → 결정 → 정식 fix tier 문서(one-line-spec / feature-story / pes / sdd-lite / sdd)로 승격

---

## 추적 컨텍스트

| 항목 | 사실 |
|---|---|
| Scope | {대응하는 마일스톤 링크 — 예: `workflows/task/milestones/version/0.0.1v/milestone.md`} |
| 진행 중 작업 | {현재 브랜치 · 머지된 PR 번호} |
| 발견 시점 | {이슈가 처음 감지된 시점 — YYYY-MM-DD} |
| fix tier 정의 | `../../../pes/workspectrum/sdd/sdd.md` |

---

## [Issue N] — {제목}

### 현상 / 트리거
{관찰된 사실, 사용자·QA 시그널, 에러 메시지, 로그 스니펫}

### 원인 가설
| # | 가설 | 개연성 근거 |
|---|---|---|
| (a) | {구체 원인 1 — 예: `ApiResponse.error` 래핑 누락} | 최빈, 로그에서 즉시 확인 가능 |
| (b) | {구체 원인 2 — 예: `@Transactional` 경계 오배치} | Service 스택 트레이스로 검증 |
| (c) | {구체 원인 3 — 예: PortOne webhook 타이밍 이슈} | FE/BE 계약 검토로 확인 |

**최고 개연성**: {(a) 또는 (b)}. {검증에 필요한 1스텝}.

### 영향 범위
{영향 받는 기능 흐름, 차단되는 사용자 경로, fallback 유무}

### 결정해야 할 것
- **(A) Option 1** — 설명 + 트레이드오프
- **(B) Option 2** — 설명 + 트레이드오프
- **(C) Option 3** — 설명 + 트레이드오프

### 권장 fix 방향 (1차)
1. Step 1: {가설 좁히기}
2. Step 2: {가설 (a) fix}
3. Step 3: {가설 (b) fix}

### workspectrum tier 추천
- {가설 (a) 단독}: **`one-line-spec`** (설정 한 줄 · Response 래핑 1곳)
- {가설 (b) 단독}: **`feature-story`** (Service 트랜잭션 경계 조정 + 슬라이스 테스트)
- {(a)+(c) 결합}: **`pes`** (환경 설정 + 외부 계약 정렬)

---

## 누적 메모 (Free-form Memo)
- YYYY-MM-DD — 초기 브레인스토밍 (Issue 1·2 식별)
- (추후 추가)

---

## 참조
- 짝 파일: `./{pair}.md`
- Fix tier 정의: `../../../pes/workspectrum/sdd/sdd.md` §fix 레이어
- 대상 마일스톤: `../../../milestones/version/0.0.1v/milestone.md`
- ErrorCode 원본: `src/main/java/nbc/c1oud_mall/common/exception/ErrorCode.java`
- 예외 처리 규범: `.claude/rules/exception.md`
- 아키텍처 규범: `.claude/rules/architecture.md`
```

---

## Issue 상태 전이

각 Issue는 다음 상태 중 하나:

| 상태 | 의미 |
|---|---|
| **pending** | 브레인스토밍 중, 아직 결정 안 됨 |
| **promoted** | fix tier 문서로 승격됨 — 원 이슈 아래 링크 남김 |
| **resolved** | 코드에 반영 완료 (`[명세 변경 이력]` 블록으로 원본 SDD 갱신) |
| **deprecated** | 무효화 — 이유를 이슈 하단에 남김 |
| **merged** | 다른 이슈와 합쳐짐 — 이동 대상 링크 |

상태 변경은 이슈 제목에 접미어로 표기: `[Issue 3] {제목} [promoted — feature-story: 2026-07-05]`

---

## 참조

- Fix tier 정의: `../../pes/workspectrum/sdd/sdd.md` §fix 레이어
- PES 브레인스토밍(크로스 컷팅): `../../pes/brainstorming/`
- 마일스톤: `../../milestones/version/{X.Y.Zv}/milestone.md`
- ADR: `workflows/living-docs/Ai-adr/`
