# [Product · Done] Payment BC 경계 통합 리포트 (v3)

## Product Vision
> Payment BC가 옆 BC(Cart · Order · Point · Product/Inventory)와 부딪치는 **경계선**을 명시적으로 문서화하여, mock → real 전환의 잔여 작업 · 트랜잭션 zone 위치 · 의존 방향 위반 지점을 한눈에 파악할 수 있게 한다.

## 배경 및 문제
- 현재 상황 (As-Is · 2026-06-04 기준)
  - Order 경계: 실 연결 완료 (PR #30)
  - Cart 경계: 실 CartService 이미 존재, mock 교체 · 위치 결정만 남음
  - Point 경계: 도메인 클래스 2개만 존재, application/infra 전무
  - Inventory 경계: 별도 BC 없음, Product BC 흡수 검토 필요
- 발생하는 문제
  - 결제 확정 TX-bound zone 안에 들어가야 할 6개 부수효과 중 2개만 실 연결 (2/6)
  - Point BC 잔액 SSOT 모호 (`User.pointBalance` vs `PointHistory.balanceAfter`)
  - OrderFacade가 Point BC 우회 (`user.getPointBalance()` 직접 읽음)
- 왜 지금 해결해야 했는가
  - Payment · Refund Product의 실 데이터 e2e 성립을 위한 남은 blocker 명세
  - Cart 삭제 위치 결정, Point BC 도입 순서 등 다음 스프린트 우선순위 확정

## 목표 (To-Be)
- Payment BC와 4개 옆 BC(Cart · Order · Point · Product) 사이의 경계선 명시
- 각 경계의 현재 상태 · 실 서비스 매핑 · 트랜잭션 zone 위치 · 남은 작업 명세
- 결제 확정 TX-bound zone 진척률 (2/6) · 보상 zone 잔여 (재고 복구 · 포인트 복구 누락)
- 다음 작업 우선순위 (P0~P2) 매트릭스

## 설계 결정 (Design Decisions)

- **Cart 삭제 위치 = OrderFacade.createOrder (경로 A)**
  - 이미 OrderFacade에 cart 삭제 주석 자리 예약됨
  - 결제 확정/보상 흐름 미변경
  - 결제 실패 시 cart 복구 포기 (정책 명시)
- **Point 잔액 SSOT = `User.pointBalance` 단일 진실**
  - `PointHistory.balanceAfter`는 이력용
  - 잔액 변경 권한은 Point BC만
- **Point 차감/적립은 결제 확정 TX 안에서 원자적**
  - `AFTER_COMMIT` 분리 금지 (`balanceAfter` 즉시 불일치 리스크)
- **`REQUIRED` propagation 유지 (`REQUIRES_NEW` 금지)**
  - zone 유지 · 부분 커밋 방지
- **보상 흐름에 포인트 복구 반드시 추가**
  - 현재 `PaymentCompensationTxOp`에 호출 자체가 없음 — 누락 알람

## 대안 검토 (Alternatives Considered)

### Cart 삭제 위치
**Option A (선택) — OrderFacade.createOrder**
- 비용: 결제 실패 시 cart 복구 포기 (정책으로 명시)
- 보상: 결제 흐름 미변경 · 이미 자리 예약됨

**Option B — Payment confirm 시점**
- 거부 이유: Order가 cartItemId snapshot 보관 필요 (스키마 변경)

### Inventory 책임
**Option A — Product BC 흡수**
- 후보 · ADR 대기

**Option B — 별도 Inventory BC**
- 후보 · ADR 대기

## 전체 아키텍처 (High-Level Architecture)

### 통합 상태 매트릭스
| 경계 | 방향 | 위치 | 실 연결 | 비고 |
| --- | --- | --- | --- | --- |
| Payment → Order (확정) | confirm | `PaymentConfirmationService:64` → `orderService.completeOrder` | ✅ (PR #30) | 멱등 가드 내장 |
| Payment → Order (취소) | 보상 | `PaymentCompensationTxOp:30` → `orderService.cancelOrder` | ✅ (PR #30) | 멱등 가드 |
| Order → Payment (사전등록) | create | `OrderFacade:110` → `paymentInitiationService.initiate` | ✅ | `portonePaymentId` 채번 |
| Order → Payment (조회) | list/detail | `OrderFacade:160, 173` | ⚠️ `oMockpaymentId=0L` | `PaymentQueryService` 호출로 교체 |
| Payment → Cart | confirm 후 | `PaymentConfirmationService:72` → `MockCartService` | ❌ | OrderFacade로 이관 예정 |
| Payment → Point (사용) | confirm | `PaymentConfirmationService:66` → `MockPointService.deductPoints` | ❌ | Point BC 미구현 |
| Payment → Point (적립) | confirm | `PaymentConfirmationService:70` → `MockPointService.accruePoints` | ❌ | `pointEarnedAmount=0L` |
| Payment → Inventory (확정) | confirm | `PaymentConfirmationService:73` → `MockInventoryService.confirmByOrderId` | ❌ | Product BC 흡수 검토 |
| Payment → Inventory (복구) | 보상 | `PaymentCompensationTxOp:31` → `MockInventoryService.restoreByOrderId` | ❌ | 동상 |

### 결제 확정 TX-bound zone 진척률 (2/6)
| # | 부수효과 | 상태 | 책임 BC |
| --- | --- | --- | --- |
| 1 | Payment.markCompleted | ✅ | Payment |
| 2 | OrderService.completeOrder | ✅ | Order |
| 3 | 포인트 사용 | ❌ mock | Point |
| 4 | 포인트 적립 | ❌ mock + 0L | Point |
| 5 | 장바구니 초기화 | ❌ mock (또는 이관) | Cart |
| 6 | 재고 확정 | ❌ mock | Inventory (Product?) |

### 보상 zone (REQUIRES_NEW)
| # | 부수효과 | 상태 |
| --- | --- | --- |
| 1 | Payment.markFailed | ✅ |
| 2 | OrderService.cancelOrder | ✅ |
| 3 | 재고 복구 | ❌ mock |
| 4 | 포인트 복구 | ❌ **누락 (호출 자체 없음)** |

## 실패 모드 / 운영 관측 (Failure Modes & Observability)

### 잠재 위반 (통합 완료 후 위험)
- **Point BC 우회**: OrderFacade가 `user.getPointBalance()` 직접 읽음 → Point BC 신설 시 `pointService.getBalance(userId)`로 교체
- **차감/적립 멱등 부재**: mock은 로그만 · 실 구현 시 `PaymentConfirmationService`가 confirm 두 번 부르면 이중 차감 리스크 (`isCompleted()` 가드로 1차 방어)
- **`pointEarnedAmount=0L` 하드코딩**: 적립률 미정 → 정책 결정 후 산정 로직 필요

### 락 순서 (consistency §5 준수)
```
Order → Payment → Point → Inventory
```

## 롤아웃 / 마이그레이션 (Rollout)

### 남은 변경 (목표 그래프)
```
- MockCartService 제거
  → Cart 삭제를 OrderFacade.createOrder로 이관 (주석 해제)
- MockPointService → PointService 교체 + 신규 구현
  → PointService.deduct/accrue/refund(보상용)
  → PaymentCompensationTxOp에 포인트 복구 호출 추가
- MockInventoryService → ProductService 또는 InventoryService
  → 재고 확정/복구 위치 합의 (ADR 필요)
- OrderFacade.getOrdersMe/getOrder의 oMockpaymentId=0L
  → PaymentQueryService 호출로 교체
```

### 다음 작업 우선순위
| # | 작업 | 영향 BC | 난이도 | 우선 |
| --- | --- | --- | --- | --- |
| 1 | OrderFacade에 `PaymentQueryService` 연결 | Order | 🟢 | P0 (한 줄) |
| 2 | MockCartService 제거 + OrderFacade에서 cart 삭제 | Cart | 🟢 | P0 |
| 3 | Point BC 최소 구현 (Service · Repository · User 도메인 메서드) | Point | 🟡 | P1 |
| 4 | Mock → 실 PointService + 보상에 포인트 복구 추가 | Payment · Point | 🟡 | P1 |
| 5 | 재고 확정/복구 책임 BC 결정 (Product 흡수 vs 별도 Inventory) | Product | 🟡 | P2 (ADR) |
| 6 | 적립률 정책 · `pointEarnedAmount` 산정 로직 | Payment · Point | 🔴 | P2 (정책) |

## 성공 지표 (KPI)
| 지표 | 목표 | 결과 (v3 시점) |
| --- | --- | --- |
| 결제 확정 TX-bound zone 진척률 | 6/6 | 2/6 |
| 보상 zone 진척률 | 4/4 | 2/4 |
| Mock 개수 (Payment) | 0 | 3 (Cart · Point · Inventory) |
| Order → Payment 조회 연결 | 실 | ⚠️ 하드코딩 잔존 |
| Point BC 신설 | 완료 | ❌ 도메인만 |

## Scope
**In Scope**:
- Payment ↔ Cart / Order / Point / Product(Inventory) 경계 명세
- 결제 확정 TX-bound zone · 보상 zone 진척률
- Mock 인벤토리 · 남은 변경 · 우선순위

**Out of Scope**:
- 실제 Point BC 구현 (별도 Product)
- Inventory BC 신설 ADR (별도 트랙)
- 적립률 정책 결정 (Payment/Point/Membership 합의)

## 대상 사용자
- **Payment 팀**: 남은 mock 인벤토리 · 통합 순서
- **Order · Cart · Point 팀**: 자신 담당 BC의 경계 · 협력 규약
- **Team Lead**: 다음 스프린트 우선순위 결정

## 연결된 Epic 목록 (완료)
- [x] Epic 1: 통합 상태 매트릭스 (경계별 실 연결 상태)
- [x] Epic 2: Cart 경계 결정 (OrderFacade로 이관 권장)
- [x] Epic 3: Order 경계 (완료 · 잔여 조회 연결)
- [x] Epic 4: Point 경계 (SSOT · 락 · zone · 구현 순서)
- [x] Epic 5: 결제 확정 zone 종합 · 보상 zone 잔여
- [x] Epic 6: 의존성 그래프 (현재 vs 목표)
- [x] Epic 7: 다음 작업 우선순위 (P0~P2)

## 관련 문서
- 원본 문서: `workflows/payment-integration-v3.md` (2026-06-04)
- 짝 문서: [`sdd-order-payment-integration.md`](./sdd-order-payment-integration.md), [`sdd-bc-integration-status.md`](./sdd-bc-integration-status.md), [`sdd-payment-incident-response.md`](./sdd-payment-incident-response.md)
- Product SDD: `workflows/products/product-payment.md`, `workflows/products/product-refund.md`
- `.claude/rules/consitency.md` §2 (Zone 매핑) · `.claude/rules/idempotency.md` §2 (카탈로그)
- Topology 참조: `workflows/topologys/Context-Dependency-Topology.md`, `Consistency-Design-Topology.md`
- Fix 이슈: `workflows/task/fix/brainstorming/version/0.0.1v/payment.md`, `refund.md`, `order.md`

## 열린 질문 (Open Questions)
- Inventory 책임 위치 (Product BC 흡수 vs 별도 Inventory BC) — ADR 필요
- 적립률 정책 위치 (Payment · Point · Membership)
- OrderFacade `validatePointUsage`의 잔액 직접 읽기 유지 여부 (in-TX read → SSOT 유지 OK, 변경 권한은 분리)
- Cart 삭제 결정 (경로 A 확정) 후 결제 실패 정책 UI 안내 필요 여부

## 제품 수준 완료 기준 (Product-level DoD)
- [x] Payment ↔ 4개 옆 BC 경계 매트릭스 문서화
- [x] 결제 확정 zone · 보상 zone 진척률 명시
- [x] Mock 인벤토리 · 우선순위 표
- [x] 다음 작업 우선순위 명시 (P0~P2)
- [x] Cart · Point · Inventory 미해결 항목 명세
