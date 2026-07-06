# [Fix · Brainstorming] Order BC — 0.0.1v (2026-07-02)

> **역할**: 주문(Order) BC의 실행 중 발견 이슈를 가설·결정으로 축적
> **짝 파일**: [`payment.md`](./payment.md), [`cart.md`](./cart.md)
> **다음 단계**: 각 이슈 → 결정 → 정식 fix tier 문서로 승격

---

## 추적 컨텍스트

| 항목 | 사실 |
|---|---|
| Scope | 주문 도메인 (`nbc.c1oud_mall.order.*`) + Payment / Cart 협력 |
| 진행 중 작업 | 결제 통합 후속 조정 |
| 발견 시점 | 2026-06-09 페어리뷰 (`order-bc-changes-for-payment-integration.md`) |
| fix tier 정의 | `../../../pes/workspectrum/sdd/sdd.md` |

---

## [Issue 1] — Mock inventory 제거 및 productId 정렬 [pending → 진행 중]

### 현상 / 트리거
`2026-06-09-mock-inventory-removal-and-productid-sort.md` (review/state): 주문 생성 시 재고 조회가 mock으로 되어 있고, productId 정렬(deadlock 방지) 로직 부재.

### 원인 가설
| # | 가설 | 개연성 근거 |
|---|---|---|
| (a) | 재고 조회가 in-memory mock → 실제 상품 재고 미검증 | 코드 즉시 확인 가능 |
| (b) | 여러 상품 락 획득 시 순서 없음 → deadlock 리스크 | 통합 테스트 없음 |

**최고 개연성**: (a), (b) 둘 다 실재.

### 영향 범위
- 주문 생성 → 결제 초기화 흐름 (Payment Epic 1)
- 재고 부족 상황에서 결제 성공 후 재고 없음 발견 → 보상 취소 유발

### 결정해야 할 것
- **(A) Product BC에 `InventoryService`(재고 잠금) 도입 + productId 오름차순 정렬 락** — 정공법
- **(B) 재고를 Redis atomic decr로 관리** — 성능 우선, 정합성 후처리
- **(C) 낙관적 락 (`@Version`)** — 단순하지만 재시도 로직 필요

### 권장 fix 방향 (1차)
1. Step 1: 재고 조회 mock 코드 위치 확인
2. Step 2: 결정 (A) 채택 시 Product BC와 협의
3. Step 3: `pes` tier로 승격 (Order + Product 크로스 BC)

### workspectrum tier 추천
- **`pes`** — Order + Product BC 협력 · 3 Story (재고 서비스, 락 순서, 통합 테스트)

---

## [Issue 2] — Cart itemId vs productId 혼동 [pending]

### 현상 / 트리거
`2026-06-09-cart-item-id-vs-product-id-confusion.md` (review/state): API 응답에서 `id` 필드가 cart line item id인지 product id인지 명확하지 않아 FE 개발자 혼동.

### 원인 가설
| # | 가설 | 개연성 근거 |
|---|---|---|
| (a) | DTO 필드명 `id`가 애매 | Response DTO 확인 즉시 |
| (b) | Cart와 Product 응답이 flat하게 섞임 | OpenAPI 스펙 확인 |

**최고 개연성**: (a).

### 영향 범위
- FE 통합 (`workflows/fe-integration-guide-v3.md`)
- 주문 생성 요청 (`cart-team-request-list-api.md`)

### 결정해야 할 것
- **(A) DTO 필드명 `cartItemId`, `productId`로 명시 분리** — 즉각 개선
- **(B) `id` 유지 + JSDoc/Javadoc 상세 명세** — 최소 변경, 여전히 혼동 여지

### 권장 fix 방향 (1차)
1. Step 1: Cart 관련 Response DTO 확인
2. Step 2: 결정 (A) 채택 → `one-line-spec`으로 승격

### workspectrum tier 추천
- **`one-line-spec`** — DTO 필드명 변경 + FE 팀 알림

---

## 누적 메모 (Free-form Memo)
- 2026-06-09 — 페어리뷰에서 2건 식별
- 2026-07-02 — 브레인스토밍 파일 등록
- (추후 추가)

---

## 참조
- 짝 파일: [`payment.md`](./payment.md), [`cart.md`](./cart.md)
- Fix tier 정의: `../../../pes/workspectrum/sdd/sdd.md`
- Order BC 통합 노트: `workflows/order-bc-changes-for-payment-integration.md`
- FE 통합 가이드: `workflows/fe-integration-guide-v3.md`
- ErrorCode: `common.exception.ErrorCode` (`ORDER001~003`)
