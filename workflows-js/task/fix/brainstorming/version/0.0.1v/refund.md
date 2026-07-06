# [Fix · Brainstorming] Refund BC — 0.0.1v (2026-07-02)

> **역할**: 환불(Refund) BC의 실행 중 발견 이슈를 가설·결정으로 축적
> **짝 파일**: [`payment.md`](./payment.md), [`order.md`](./order.md)
> **다음 단계**: 각 이슈 → 결정 → 정식 fix tier 문서로 승격

---

## 추적 컨텍스트

| 항목 | 사실 |
|---|---|
| Scope | 환불 도메인 (`nbc.c1oud_mall.refund.*`) + PortOne cancel port 재사용 |
| 진행 중 작업 | Product 2 (환불) — feedback/product02.md 반영 중 |
| 발견 시점 | 2026-06-09 페어리뷰 |
| fix tier 정의 | `../../../pes/workspectrum/sdd/sdd.md` |

---

## [Issue 1] — 환불 선검증 + 후검증 S+ 패턴 [pending]

### 현상 / 트리거
`2026-06-09-refund-pre-and-post-validation-s-plus.md` (review/state) 페어리뷰: 환불 요청에서 "선검증(요청 수량 vs 남은 수량) → DB 커밋 → PG 호출 → 실패 시 복구" 흐름의 후검증 지점이 명확하지 않음.

### 원인 가설
| # | 가설 | 개연성 근거 |
|---|---|---|
| (a) | 선검증만 있고 후검증(PG 응답 기반 정합성 재확인) 부재 | product02.md AC 검토로 확인 |
| (b) | PG 실패 시 복구(refund 상태 revert) 로직이 도메인 밖 | Service 코드 검토 |

**최고 개연성**: (a). 명세 자체에 후검증 부재.

### 영향 범위
- 부분 환불 반복 케이스 (예: 3개 중 1개 → 1개 환불 시 잔여 2개 정합성)
- 여러 환불 요청이 동시에 들어올 때 race condition

### 결정해야 할 것
- **(A) 선검증 → PENDING 커밋 → PG 호출 → 응답 기반 후검증 → CONFIRMED/FAILED 전이** — 상태 기계 명시
- **(B) 낙관적 락(`@Version`) + 재시도** — 단순하지만 UX 저하
- **(C) 비관적 락(`SELECT FOR UPDATE`) + 짧은 TX** — 동시성 안전 · 커넥션 부담

### 권장 fix 방향 (1차)
1. Step 1: `RefundService.request` 상태 전이 문서화 (feedback/product02.md §2 확장)
2. Step 2: 상태 (PENDING → CONFIRMED / FAILED) 명시 + 각 전이의 트리거 정의
3. Step 3: 결정 (A) 채택 후 `feature-story` tier로 승격

### workspectrum tier 추천
- **`feature-story`** — RefundService 리팩토링 + 슬라이스 테스트 3건 (해피/부분/전체)

---

## [Issue 2] — 환불 단위: OrderItem quantity 기준 [promoted → ADR 008]

### 상태
**promoted** — ADR 008 (`refund-unit-orderitem-quantity.md`)로 결정 완료.

### 잔여 액션
- [ ] `RefundItem` 도메인에 `quantity` 필드 · 검증 로직 구현
- [ ] 잔여 수량 조회 쿼리(`OrderItemJpaRepository.findRemainingQuantity`) 정의

---

## [Issue 3] — 환불 금액 분할: floor 정책 [promoted → ADR 009]

### 상태
**promoted** — ADR 009 (`refund-amount-split-floor-policy.md`)로 결정 완료.

### 잔여 액션
- [ ] `RefundAmountCalculator` 도메인 서비스 구현
- [ ] 1원 단위 truncation 정확성 테스트 (엣지 케이스: 3개 상품, 총액 100원 → 각 33원, 1원은 마지막 항목에 할당)

---

## [Issue 4] — PortOne 취소 포트 확장 vs 신규 [promoted → ADR 011]

### 상태
**promoted** — ADR 011 (`portone-cancel-port-extension.md`)로 결정 완료 (**기존 확장** 채택).

### 잔여 액션
- [ ] `PortOnePaymentPort` 기존 인터페이스에 `cancelPartial(...)` 시그니처 추가
- [ ] Adapter (`PortOnePaymentAdapter`)에 v2 API 호출 구현
- [ ] `PaymentContext`에서 위임 호출

---

## 누적 메모 (Free-form Memo)
- 2026-06-09 — Refund 관련 페어리뷰 4건 (Issue 1·2·3·4)
- 2026-07-02 — 브레인스토밍 파일 정식 등록
- (추후 추가)

---

## 참조
- 짝 파일: [`payment.md`](./payment.md), [`order.md`](./order.md)
- 원본 Product SDD: `workflows/products/product-refund.md` (이전 `product02.md`)
- Fix tier 정의: `../../../pes/workspectrum/sdd/sdd.md` §fix 레이어
- ErrorCode 원본: `src/main/java/nbc/c1oud_mall/common/exception/ErrorCode.java` (`RF001`, `RF002` 등)
- ADR: `workflows/living-docs/Ai-adr/007~011-refund-*.md`
- 피드백 문서: `workflows/products/feedback/product02.md`
