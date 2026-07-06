# AI-ADR-008: 환불 단위 — `orderItemId + 수량`, 서버 금액 자동 산정 (클라이언트 금액 미입력)

- 상태: Accepted
- 일자: 2026-06-05
- 관련 Story: `workflows/products/product02.md` Story 1-1 + 1-2 + 2-3
- 관련 ADR:
  - `refund/docs/adr/0008-refund-amount-split-policy.md` (소수점 정책 — AI-ADR-009 매핑)
- 관련 룰: `.claude/rules/idempotency.md §3` (비즈니스 식별자 키)

---

## Context

환불 API의 입력으로 무엇을 받을 것인가가 가장 큰 보안·정합성 결정 중 하나.

선택지:
- (a) **환불 금액**을 클라이언트가 입력 (`{ amount: 5000 }`)
- (b) **주문상품 ID + 수량**만 입력, 금액은 서버가 자동 산정 (`{ items: [{orderItemId, quantity}] }`)
- (c) (a) + (b) 둘 다 받고 서버에서 검증

(a)의 함정:
- 사용자가 5,000원 결제한 주문에 10,000원 환불 요청을 보낼 수 있음 → 서버가 검증해야 함
- 가격 변동 시 "결제 시점 가격"과 "환불 요청 시점 가격" 중 어느 게 기준인지 모호
- 부분 환불 누적 시 잔여 환불 가능 금액 계산이 복잡 (어떤 상품을 얼마나 환불했는지 추적 불가)

(b)의 강점:
- 클라이언트는 *환불 대상 물리적 단위*만 지정
- 서버가 결제 시점 가격 스냅샷 × 수량으로 자동 산정 → 위변조 차단
- 잔여 환불 가능 수량을 `orderItem` 단위로 추적 가능 → 누적 부분 환불 안전

추가 결정: **가격 스냅샷 출처**
- 결제 시점 가격을 어디에 저장? Order? Payment? Refund?
- 정식 패턴: `PaymentItem` 신설 — Payment 생성 시 각 OrderItem 단가를 복사
- 1차 단순화: 이미 `OrderItem.priceSnapshot` (주문 시점 단가)이 존재 → 그대로 사용

---

## Decisions

### 1. 환불 단위 = `orderItemId + quantity`
- API Request body:
  ```json
  {
    "items": [
      { "orderItemId": 123, "quantity": 2 },
      { "orderItemId": 456, "quantity": 1 }
    ],
    "reason": "단순 변심"
  }
  ```
- 클라이언트는 **금액을 입력하지 않음** (입력 필드 자체 없음)

### 2. 서버 자동 산정
- `totalRefundAmount = Σ (OrderItem.priceSnapshot × quantity)`
- 가격 스냅샷은 **주문 시점 가격** 사용 (1차) — `OrderItem.priceSnapshot`
- 정식 PaymentItem 스냅샷은 후속 Story (가격 변동·할인 적용 시 보강)

### 3. 잔여 환불 가능 수량 누적 추적
- `RefundJpaRepository.sumRefundedQuantity(paymentId, orderItemId): long` — SUM 쿼리
- 잔여 = `OrderItem.quantity − sum(RefundItem.quantity)`
- 잔여 초과 요청 → `RF001` (409)

### 4. 별도 Refund Aggregate에 `RefundItem` 보존
- 한 번의 환불 요청 = `Refund` 1개 + `RefundItem` N개
- `RefundItem`: `orderItemId`, `quantity`, `priceSnapshotAtPayment`, `itemRefundAmount`
- 부분 환불을 여러 번 누적해도 *어떤 상품을 몇 개 환불했는지* 추적 가능

---

## 프롬프트 내용 (AI 활용시)

> "환불 API 입력 설계를 결정해야 합니다. 옵션:
> (a) 금액 (`amount: 5000`)
> (b) 주문상품 ID + 수량 (`items: [{orderItemId, quantity}]`)
> (c) 둘 다 받고 서버 검증
>
> 우려 사항:
> - 사용자가 결제액보다 많이 입력하면? (보안)
> - 가격 변동 시 어느 시점 가격이 기준?
> - 부분 환불 여러 번 누적되면 잔여 환불 가능 금액을 어떻게 추적?
>
> 가격 스냅샷은 어디에 저장하나요? Payment 생성 시점에 PaymentItem 신설? 아니면 OrderItem.priceSnapshot 그대로?"

---

## AI 응답 요약

### 입력 방식
- **(b) 권장.** 금액은 절대 클라이언트가 입력하지 않는 게 표준 패턴.
  - 보안: 위변조 차단 (서버 산정이라 입력값 검증 불필요)
  - UX: 클라이언트도 단순 (어떤 상품을 몇 개 환불할지만 선택)
  - 정합성: 부분 환불 누적 추적이 *물리적 단위*로 가능 (금액 단위는 추적 어려움)
- (c)는 두 진실 source가 충돌하는 위험 — 검증 통과시켜 받으면 서버 산정 결과로 덮어쓰는 거라 (b)와 같음. 입력만 더 받는 비용.

### 가격 스냅샷
- 정식: `PaymentItem` 신설 (Payment 생성 시 OrderItem 단가 복사)
- 1차 단순화: `OrderItem.priceSnapshot`이 이미 *주문 시점 단가*를 가짐 → 그대로 사용 OK
- 트레이드오프:
  - 1차: Order 단가가 환불 산정 기준 → 결제 후 Order 단가가 변하지 않는 가정 (사실상 안 변함)
  - 정식: Payment 시점이 진정한 *결제* 시점이라 의미상 더 정확
- 1차 사용 후 *가격 변동 케이스가 실제 발생하면* PaymentItem 마이그레이션 ADR

### 잔여 수량 추적
- SUM 쿼리 (`sumRefundedQuantity(paymentId, orderItemId)`)
- 캐시·denormalized 컬럼 안 만들기 (race condition + 정합성 위험)

---

## Consequences

### 장점
- **금액 위변조 원천 차단** — 클라이언트가 금액을 모름, 서버 산정 결과만 응답
- 부분 환불 누적 추적이 *물리적 단위*로 명확 — orderItemId별 잔여 수량
- 가격 스냅샷이 1차에 OrderItem.priceSnapshot로 충분 — 추가 컬럼/엔티티 없이 동작
- `RefundItem`에 환불 시점 가격 보존 — 향후 OrderItem 단가가 바뀌어도 환불 기록은 불변

### 단점 / 알려진 한계
- 가격 변동·할인·쿠폰 적용 시의 환불 산정은 1차 미지원 (OrderItem.priceSnapshot이 *주문 시점*이라 변동 후 환불은 변동 전 가격으로)
- `sumRefundedQuantity` SUM 쿼리가 환불마다 호출 — 환불 빈도가 낮아 부담 X
- 정식 PaymentItem 스냅샷 도입 시 마이그레이션 필요 (RefundItem.priceSnapshotAtPayment를 다른 출처로 변경)
- 잔여 수량 계산이 **동시 환불 race에 취약** → 비관적 락으로 별도 방어 (AI-ADR-010)

---

## 내가 수정한 부분

- AI는 처음에 (c) "둘 다 받고 서버 검증"을 *방어 코드 가독성 측면에서* 제안. 두 source가 충돌 시의 결정 로직이 코드를 더럽힘 + 입력값 검증이 추가됨. **(b) 단일 source**로 강제.
- 가격 스냅샷을 AI가 처음에 *정식 PaymentItem 신설*을 권장. **1차 OrderItem.priceSnapshot로 충분**, 정식 마이그레이션은 가격 변동 케이스 실제 발생 시 별도 ADR로 분리. **YAGNI**.
- 잔여 수량을 캐시(`Refund.cumulativeRefundedQuantity` 컬럼) 두는 대안을 AI가 제시. **SUM 쿼리 + 비관적 락**으로 처리 — 캐시는 race + 정합성 위험. 환불 빈도 자체가 낮아 SUM 비용 부담 X.
- `priceSnapshotAtPayment` 필드명을 처음엔 `unitPriceAtRefund`로 AI 제안. *결제 시점*이 의미상 정확 (`AtPayment`) → 이름 변경. 환불 시점 가격은 의미 없음 (가격 변동 무관하게 결제 시점 기준).
- `RefundItem.itemRefundAmount` 필드 추가 — 환불 시점에 계산해서 보존 (`priceSnapshotAtPayment × quantity`). AI는 계산 가능한 값이라 안 둬도 된다 했지만, **감사·디버깅용으로 보존** 결정. 환불 내역 조회 시 매번 계산 안 해도 됨.

---

## 최종 반영 여부

- ✅ 코드 반영:
  - `RefundRequest` (presentation DTO) — `items: List<RefundItemRequest>`, `reason: String`
  - `RefundItemRequest(orderItemId, quantity)` — 클라이언트 입력. 금액 필드 없음
  - `RefundCommand` (application) — 동일 구조 + userId
  - `Refund` Aggregate + `RefundItem` Entity — `priceSnapshotAtPayment`, `itemRefundAmount` 보존
  - `RefundProcessService.buildAndValidateItems` (RefundProcessService:104) — OrderItem 매핑 + sumRefundedQuantity SUM + 잔여 검증
  - `RefundJpaRepository.sumRefundedQuantity(paymentId, orderItemId)`
- ✅ API: `POST /api/v1/refunds` (실제 컨트롤러 경로는 코드 기준)
- 미래 트리거:
  - 가격 변동·할인·쿠폰 케이스 발생 시 `PaymentItem` 정식 스냅샷 도입 ADR
  - 환불 누적 빈도가 높아져 SUM 쿼리 비용 문제 시 캐시 컬럼 도입 ADR
