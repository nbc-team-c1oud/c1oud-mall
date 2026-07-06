# [Product · Done] Order-Payment 통합 — mock → real 전환

## Product Vision
> Payment BC의 `PaymentConfirmationService`가 `MockOrderService`가 아닌 실제 `OrderService`를 호출하도록 mock을 걷어내고, Order → Payment 방향의 사전등록(`portonePaymentId` 채번)까지 연결하여 결제 e2e 흐름을 실 데이터 기반으로 성립시킨다.

## 배경 및 문제
- 현재 상황 (As-Is)
  - Payment 결제 확정 흐름이 `MockOrderService.completeOrder/cancelOrder`에 의존 (mock 로그만 찍음)
  - Order `PaymentInitiationService`는 빈이 등록되었으나 production 호출자 0건 → `portonePaymentId` 항상 `null`
  - `Order.changeStatus`가 `IllegalArgumentException` throw → GlobalExceptionHandler 500 처리
  - `OrderFacade.createOrder:88`에 quantity 오사용 버그 (`product.getStockQuantity()` → `cartItem.getQuantity()`)
- 발생하는 문제
  - 결제 확정 성공해도 실제 주문 상태 CONFIRMED 전이 안 됨 (mock)
  - 결제 실패 시 보상 취소가 mock → 실제 주문 CANCELLED 전이 안 됨
  - FE가 결제 흐름 시작 불가 (`portonePaymentId=null`)
  - 결제 요청 금액이 실 주문 금액과 불일치 (quantity 버그)
- 왜 지금 해결해야 했는가
  - Payment Product Epic 1·2가 e2e로 동작하려면 필수
  - Refund Product의 전제 (완결된 결제 위에서만 환불 가능)

## 목표 (To-Be)
- `OrderService.completeOrder(Long)` + `cancelOrder(Long)` 신설 (멱등 가드 내장, `REQUIRED` propagation)
- `Order.changeStatus` 예외 타입 `IllegalArgumentException` → `BusinessException(OD002)`
- `OrderFacade.createOrder`에서 `PaymentInitiationService.initiate(...)` 호출로 `portonePaymentId` 채번
- `MockOrderService` 필드를 실 `OrderService`로 교체 (2곳: `PaymentConfirmationService`, `PaymentCompensationTxOp`)
- `OrderFacade` `product.getStockQuantity()` → `cartItem.getQuantity()` 버그 픽스
- `OrderFacade` 미사용 `import Payment` 제거 (DDD 의존 방향 위반)

## 설계 결정 (Design Decisions)

- **`REQUIRED` propagation (호출자 TX 참여)**
  - `REQUIRES_NEW` 금지 → 부분 커밋 방지
- **멱등 가드는 OrderService 내부에** (Payment는 `isCompleted()`로 1차 · Order는 상태로 최종)
  - `.claude/rules/idempotency.md` §2 "Silent OK" 정책
- **BC 간 협력은 직접 호출** (도메인 이벤트 X)
  - `application.OrderService` → `payment.application.PaymentInitiationService` 한 방향
  - DDD 상 application 간 직접 의존 허용 (도메인은 X)
- **Order `application/OrderService`에 배치** (OrderFacade X)
  - Payment 직접 호출 진입점 → facade 거치는 것은 오버킬
- **`PaymentInitiationCommand`는 현 시점 `totalAmount = pgAmount, pointUsedAmount = 0L`**
  - 포인트 미도입 → 도입 시 `OrderCheckoutRequest.pointUsedAmount` 필드 추가

## 대안 검토 (Alternatives Considered)

### BC 간 통신 방식
**Option A (선택) — 직접 서비스 주입 (port 인터페이스 X)**
- 비용: 강한 결합
- 보상: 단순, 디버깅 쉬움 · 팀 결정
- ADR 006 (`bc-collaboration-direct-call.md`)

**Option B — Port/Adapter 도입 (port 인터페이스 우회)**
- 거부 이유: 초기 규모 오버킬 · 팀 합의 없음

**Option C — 도메인 이벤트**
- 거부 이유: 이벤트 유실 리스크 · 트랜잭션 경계 복잡화

### 예외 타입
**Option A (선택) — `BusinessException(ErrorCode.INVALID_ORDER_STATUS)` (OD002)**
- 보상: payment 메인 TX 정상 롤백 · FE에 400 응답 · 컨벤션 준수
- 비용: 없음

**Option B — `IllegalArgumentException` 유지**
- 거부 이유: `GlobalExceptionHandler.handleUnknown` → 500 오응답 · 컨벤션 위반

## 전체 아키텍처 (High-Level Architecture)

### 컴포넌트 배치 (최종)
```
[주문 생성 TX]                                [결제 확정 TX]                          [결제 보상 TX · REQUIRES_NEW]
OrderFacade.createOrder                       PaymentConfirmationService.confirm      PaymentCompensationTxOp.compensateDb
  ├── orderService.createOrder                  ├── orderService.completeOrder ✅       └── orderService.cancelOrder ✅
  └── paymentInitiationService.initiate ✅      │   (CONFIRMED silent OK 멱등)             (CANCELLED silent OK 멱등)
      → portonePaymentId 채번                   │
                                                └── mockCart/Point/Inventory (잔여)
```

### 호출 규약 (Contract)
```java
// OrderService.completeOrder — REQUIRED propagation, 멱등
@Transactional
public void completeOrder(Long orderId) {
    Order order = orderJpaRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    if (order.getOrderStatus() == OrderStatus.CONFIRMED) return;  // silent OK
    order.markAsConfirmed();  // 내부 가드 → OD002 가능
}
```

## 실패 모드 / 운영 관측 (Failure Modes & Observability)

| 시나리오 | ErrorCode | HTTP | 결과 |
| --- | --- | --- | --- |
| Order 없음 | `ORDER_NOT_FOUND` | 404 | Payment 메인 TX 롤백 |
| 이미 CANCELLED 상태에서 completeOrder | `OD002` (INVALID_ORDER_STATUS) | 400 | Payment 메인 TX 롤백 · 보상 미트리거 |
| 이미 CONFIRMED 상태에서 completeOrder | - | 200 (silent OK) | 부수효과 없이 정상 반환 |

## 롤아웃 / 마이그레이션 (Rollout)

### 전제
- Payment 결제 확정 코드는 이미 mock 호출 형태 · 필드 타입만 교체
- 호출 코드(line 63 · line 30) 변경 없음

### Product 의존성
- 선행: Payment BC · Order BC 도메인 · `PaymentInitiationService`
- 후행: Point BC 통합 · Refund Product

### 커밋 계획 (실제 PR #30에 반영)
```
1. Order.changeStatus 예외 타입 변경 (BusinessException + OD002)
2. OrderService.completeOrder/cancelOrder 추가 (멱등 가드)
3. PaymentConfirmationService · PaymentCompensationTxOp mock 필드 → 실 OrderService 교체
4. OrderFacade.createOrder에 PaymentInitiationService 호출 추가
5. OrderFacade quantity 버그 픽스
6. 미사용 import Payment 제거
```

## 성공 지표 (KPI)
| 지표 | 목표 | 결과 (PR #30 이후) |
| --- | --- | --- |
| 결제 확정 → 주문 CONFIRMED 전이 | 100% | ✅ 실 연결 |
| 보상 취소 → 주문 CANCELLED 전이 | 100% | ✅ 실 연결 |
| `portonePaymentId` 채번 | 실 UUID | ✅ 정상 |
| 결제 요청 금액 = 실 주문 금액 | 100% 일치 | ✅ 버그 픽스 |
| `IllegalArgumentException` 500 응답 | 0건 | ✅ 400 응답으로 정정 |

## Scope
**In Scope**:
- `OrderService.completeOrder(Long)` · `cancelOrder(Long)` 신설
- `Order.changeStatus` 예외 타입 정정
- `OrderFacade`에 `PaymentInitiationService` 호출 추가
- Mock → 실 서비스 교체 (2곳)
- `OrderFacade` quantity 버그 픽스
- 미사용 `import Payment` 제거

**Out of Scope**:
- Cart · Point · Inventory 통합 — 별도 트랙 (P2/P3)
- `MockOrderService` 파일 삭제 — 다른 mock 4종 함께 정리 시 처리

## 대상 사용자
- **Payment 팀**: 결제 확정 흐름이 real 데이터로 동작
- **Order 팀**: 명확한 진입점 시그니처
- **FE 팀**: 결제 사전등록 응답에 실 `portonePaymentId` 포함 → 결제 흐름 정상 시작
- **사용자**: 결제 결과가 실제 주문 상태에 반영됨

## 연결된 Epic 목록 (완료)
- [x] Epic 1: `OrderService.completeOrder`/`cancelOrder` 추가 (P1)
- [x] Epic 2: `Order.changeStatus` 예외 타입 정정
- [x] Epic 3: `OrderFacade` PaymentInitiationService 호출 통합
- [x] Epic 4: `OrderFacade` quantity 버그 픽스
- [x] Epic 5: `MockOrderService` 교체 (Payment 2곳)

## 관련 문서
- 원본 요청: `workflows/order-bc-changes-for-payment-integration.md` (2026-06-03)
- 실 반영 PR: PR #30 (MockOrderService 제거 + PaymentQueryService 도입)
- 관련 ADR: 006 (`bc-collaboration-direct-call.md`)
- 후속 통합 리포트: [`sdd-payment-integration-report.md`](./sdd-payment-integration-report.md)
- CLAUDE.md §8 · `.claude/rules/consitency.md` §2 (주문 생성 zone) · `.claude/rules/idempotency.md` §2

## 열린 질문 (Open Questions)
- Point 도입 시 `PaymentInitiationCommand`의 `pointUsedAmount` 필드 채우기 트리거
- Cart 삭제를 OrderFacade.createOrder 안으로 이동 여부 (권장 · [`sdd-payment-integration-report.md`](./sdd-payment-integration-report.md) 참조)
- 잔여 Mock 3종(Cart · Point · Inventory) 통합 순서

## 제품 수준 완료 기준 (Product-level DoD)
- [x] Payment 확정 e2e 시 실 Order 상태 전이 확인
- [x] Payment 보상 시 실 Order CANCELLED 전이 확인
- [x] `portonePaymentId` 실 UUID 반환 확인
- [x] `OD002` HTTP 400 응답 확인 (500 아님)
- [x] `OrderFacade` quantity 버그 픽스 검증 (총액 일치)
- [x] 미사용 `import Payment` 제거 (DDD 규칙 준수)
