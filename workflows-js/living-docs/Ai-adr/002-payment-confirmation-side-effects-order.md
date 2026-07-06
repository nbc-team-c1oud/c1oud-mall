# AI-ADR-002: 결제 확정 부수효과 처리 순서 (Order → Payment → Point → Inventory → Cart)

- 상태: Accepted
- 일자: 2026-06-02 (1차) / 2026-06-08 (락 순서 재정렬)
- 관련 Story: `workflows/product.md` Story 2-2 (결제 확정 도메인 서비스 — 정상 흐름)
- 관련 ADR:
  - `payment/docs/adr/0003-bc-collaboration-mock-classes.md` (협력 BC 호출)
  - `payment/docs/adr/0004-payment-compensation-transaction-pattern.md` (보상은 역순)
  - `.claude/rules/consitency.md §5` — 락 순서 통일 (Order → Payment → Point → Inventory)

---

## Context

결제 확정 트랜잭션은 한 호출 안에서 **여러 BC를 건드린다**:

- Payment: PENDING → COMPLETED
- Order: PENDING_PAYMENT → CONFIRMED
- Point: `pointUsedAmount` 차감 + `pointEarnedAmount` 적립 (`pgAmount × 1%`)
- Inventory: 재고 차감 확정 (단, Order 생성 단계에서 이미 선차감했으므로 confirm 단계는 추후 결정)
- Cart: 결제된 항목 제거 또는 전체 비우기

세 가지 결정이 얽혀있다:
1. **트랜잭션 경계** — 단일 TX vs 분리 TX
2. **각 BC 처리 순서** — 어느 BC를 먼저 부르나
3. **락 획득 순서** — 데드락 방지 (consistency §5 명시: Order → Payment → Point → Inventory)

순서를 잘못 잡으면 다음 문제가 생긴다:
- **데드락**: A 트랜잭션은 Order→Point 락, B 트랜잭션은 Point→Order 락 → 교착
- **재고 음수**: Cart 비우기 후 재고 차감 실패 시 사용자는 재시도 못 함
- **포인트 음수**: Point 차감 후 다른 단계 실패 시 보상 어려움 (보상 가능하긴 함)

---

## Decisions

### 트랜잭션 경계
- **단일 트랜잭션** (`PaymentConfirmationService.confirm` — `@Transactional`)
- consistency.md §2 "결제 확정 zone = TX-bound": 주문완료·결제완료·포인트·재고·장바구니가 한 zone이므로 분리 금지

### 처리 순서 (확정 시) — 락 획득 순서와 일치
1. **Payment 조회 + 소유권 검증** (락 X — 식별)
2. **PortOne 재조회** (트랜잭션 **시작 전** — 외부 호출, 락 hold 금지)
3. **Order 완료** (`OrderService.completeOrder` — Order 행 비관적 락)
4. **Payment markCompleted** (Payment 행 — 자기 자신 update)
5. **Point 사용/적립** (`PointService.deductPoints` + `accruePoints` — User 비관적 락)
6. **Cart 비우기** (`CartService.clearCart` — 락 영향 적음)
7. **Inventory** — 결제 확정 단계에서 호출 **하지 않음** (2026-06-08 결정, ADR-0003 3차)
    - 이유: Order 생성 시 `ProductService.deductStockWithLock`으로 이미 선차감 확정. 결제 확정은 inventory와 무관함을 명시적 계약으로 못박음.

### 보상 시 (Story 2-3)
- 역순으로 풀지 않고, **별도 보상 트랜잭션**에서 독립적 순서 (AI-ADR-003 참조)
- 보상 트랜잭션 락 순서도 동일: Order 로드 → Payment markFailed → Inventory 복구(productId 정렬) → Order cancel

---

## 프롬프트 내용 (AI 활용시)

> "결제 확정 한 번이 끝나면 다음이 모두 일관되게 반영돼야 합니다:
> - Payment COMPLETED
> - Order CONFIRMED
> - 포인트 사용/적립
> - 재고 확정
> - 장바구니 비우기
>
> 두 가지가 헷갈립니다:
> 1) 단일 트랜잭션이 맞나, 분리해야 하나?
> 2) BC들을 어떤 순서로 부르는 게 안전한가? 데드락 / 음수 / 보상 어려움 같은 함정을 피하려면?
>
> 락 컨벤션은 'Order → Payment → Point → Inventory'로 정해뒀습니다.
> 이 순서가 처리 순서와 일치해야 하나요, 별개인가요?"

---

## AI 응답 요약

### 트랜잭션 경계
- **단일 TX 추천**. 정합성 zone이 동일하면 분리할 이유 없음.
- 분리할 경우 한쪽 실패 시 보상 코드가 폭증. 결제 확정은 본질적으로 atomic 이벤트.

### 처리 순서 = 락 순서
- **처리 순서를 락 순서와 일치시키는 게 데드락 방지 표준 패턴.**
- 다른 트랜잭션도 같은 순서로 락을 잡으면 cycle이 안 생김.
- "Order → Payment → Point → Inventory" 순서가 이미 컨벤션에 있으면, 결제 확정 처리 순서도 그대로 가야 함.

### 외부 호출(PortOne 재조회) 위치
- **트랜잭션 시작 전.**
- 트랜잭션 안에서 외부 호출 → 락 hold time이 외부 latency에 종속 → 동시성 처참
- consistency.md §6 명시 위반

### 각 BC 호출 시 주의
- Point는 User 행에 비관적 락 — 락 hold 짧게 유지
- Inventory는 Order 생성 시 선차감했으면 confirm 단계에선 호출 자체를 빼는 게 깔끔
- Cart 비우기는 마지막 — 사용자 실수 시 복구가 어렵지만 다른 BC보다 위험 작음

---

## Consequences

### 장점
- 락 컨벤션과 처리 순서가 일치 → 데드락 cycle 불가능
- 단일 TX로 정합성 명확. consistency zone 정의와 1:1
- PortOne 재조회가 트랜잭션 밖 → 락 hold time 짧음
- Inventory 결제 확정 호출 제거(2026-06-08)로 처리 단계 1개 감소 + 책임 경계 명확화

### 단점
- 단일 TX 한 곳에서 5개 BC를 건드리니 통합 테스트가 복잡 — 한 BC 변경 시 회귀 영향 큼
- Cart 비우기를 가장 뒤에 두면, Cart 비우기만 실패해도 결제는 이미 끝난 상태 (롤백되지만 사용자 입장에선 혼란 가능 → 정상 동작에선 거의 안 일어남)
- "결제 확정에 inventory 호출 없음"은 처음 코드 읽는 사람이 헷갈릴 수 있음 → ADR-0003 3차 진행에 명시 보강

---

## 내가 수정한 부분

- AI는 처음에 "Cart 비우기 → Point → Order" 순서를 제시했었음. **이건 락 순서와 어긋남.**
  - Cart 비우기를 먼저 하면, Point/Order 락 잡기 전에 commit-effort가 쌓임
  - 락 컨벤션과 일치하지 않으면 다른 흐름(예: 보상)에서 cycle 발생 가능
  - → 락 순서 그대로 처리 순서를 잡는 걸로 강제

- **Inventory 호출 제거 (2026-06-08)**: AI는 처음에 "재고 확정도 결제 확정 단계에서 한 번 더 호출" 패턴을 제시했는데, Order 생성 시 이미 비관적 락으로 선차감 확정한 상태라 confirm 단계의 inventory 호출은 의미가 없음. **호출 자체를 빼고 ADR-0003 3차 기록에 "결제 확정 단계는 inventory와 무관함을 계약으로 못박음" 명시.** AI가 코드 리팩토링 후에 PaymentCompensationTxOp만 ProductService 직접 호출하는 형태로 정리하도록 재요청.

- Point 적립률은 AI에게 "1% 고정"으로 명시. 등급별 차등은 product.md Out of Scope.

- BC 협력 방식: AI는 "도메인 이벤트(`PaymentConfirmed`) + AFTER_COMMIT" 패턴을 권장했지만, **단일 TX 일관성이 깨질 위험** + 다른 BC가 미구현인 상태(2026-06-01 기준) → **직접 호출 + Mock 구체 클래스 패턴**으로 변경 (AI-ADR-006 참조). 추후 이벤트 분리는 별도 ADR로.

---

## 최종 반영 여부

- ✅ 코드 반영:
  - `PaymentConfirmationService.confirm` — 단일 `@Transactional` + 위 순서대로 호출
  - `OrderService.completeOrder` (Order 비관적 락)
  - `PointService.deductPoints` / `accruePoints` (User 비관적 락)
  - `CartService.clearCart`
  - Inventory 호출 **없음** (Order 생성 시 처리)
- ✅ 일반 ADR: `payment/docs/adr/0003-bc-collaboration-mock-classes.md` "실구현 도입 진행 이력" 1차~3차
- ✅ 보상 흐름: `PaymentCompensationTxOp` — 락 순서 동일 (productId 정렬 + Order→Payment→Point→Inventory)
- 미래 트리거: BC 이벤트 분리, 등급별 차등 적립률
