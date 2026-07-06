# [트러블슈팅] `MockInventoryService` 제거 — confirm 호출 자체 제거 + 보상 흐름 productId 정렬 (데드락 cycle 차단)

---
date: 2026-06-09
domain: [payment, refund, concurrency]
tags: [mock-cleanup, lock-ordering, deadlock, port-vs-direct-call, yagni]
related-story: workflows/products/product.md Story 2-2·2-3
related-adr: src/main/java/.../payment/docs/adr/0003-bc-collaboration-mock-classes.md (3차 진행 이력), workflows/living-docs/Ai-adr/006-bc-collaboration-direct-call.md
related-topology: workflows/topologys/Consistency-Design-Topology.md, workflows/topologys/Context-Dependency-Topology.md
---

## 1. 문제 (What)

ADR-0003에서 임시로 도입한 `MockInventoryService` 4개 mock 중 마지막 하나. 다른 3개(`MockOrderService`, `MockPointService`, `MockCartService`)는 실 BC 도입에 따라 차례로 교체 완료.

`MockInventoryService` 차례에서 두 가지가 걸렸음:
1. **결제 확정 단계에서 inventory 호출이 정말 필요한가?** Order 생성 시 이미 `ProductService.deductStockWithLock`으로 비관적 락 + 차감 *확정*함. confirm 단계의 `confirmByOrderId(orderId)`는 **무엇을 하는가?**
2. **별도 `InventoryService` 클래스를 새로 만들 것인가?** AI는 처음에 `OrderFacade`와 별개의 `InventoryService`를 신설하자고 제시. 그러나 `OrderFacade.createOrder`가 이미 `ProductService`를 직접 호출하는 패턴. 일관성이 깨짐.

추가로 보상 흐름의 재고 복구:
- `MockInventoryService.restoreByOrderId(orderId)` — 단일 호출. 내부에서 어떤 락 순서로 처리하는지 mock에는 명시 없음.
- 실 구현 시 OrderItem N개에 대해 `restoreStockWithLock`을 호출해야 함. 호출 순서가 *임의*면 다른 환불/보상이 같은 상품을 다른 순서로 잠그면서 **데드락 cycle 형성 가능**.

## 2. 문제 해결 방법 (How)

### 결제 확정 단계 inventory 호출 제거
1. **Order 생성 단계의 재고 차감 확정 상태 확인** — `OrderFacade.createOrder`에서 `productService.deductStockWithLock(productId, quantity)` 호출이 *이미 commit*된 시점. confirm 단계는 재고와 무관함.
2. **`PaymentConfirmationService`에서 inventory 호출 라인 자체 제거** — `MockInventoryService.confirmByOrderId` 호출 코드 삭제.
3. **ADR-0003 3차 진행 이력에 "결제 확정 단계는 inventory와 무관함을 명시적 계약으로 못박음" 기록** — 다음 작업자가 "왜 confirm 단계에 재고 호출이 없지?" 의문 가질 때 답이 되도록.

### 별도 InventoryService 신설하지 않음
1. **OrderFacade 패턴 확인** — 이미 `ProductService` 직접 주입. port 추상화 없음.
2. **`PaymentCompensationTxOp`도 같은 패턴 적용** — `ProductService` 직접 주입, 별도 InventoryService 신설 안 함.
3. **port 도입 기준 명시**(ADR-0003에 추가): *시그니처 안정 + 어댑터 ≥ 2 가능성*이 보일 때만 port. 단일 어댑터에 port는 조기 추상화.

### 보상 흐름 재고 복구 순서 — productId 정렬
1. **OrderService.findOrderEntity로 Order + items 로드**
2. **`items.sort(Comparator.comparing(OrderItem::getProductId))`**
3. **정렬 순서대로 `productService.restoreStockWithLock(productId, quantity)` per item**
4. 다른 환불/보상이 같은 상품들을 *반드시 같은 순서*로 잠그도록 강제

### refund 도메인의 사후 정리 (4차)
- `refund.application.PointRestorePort` + `MockPointRestoreAdapter`도 같은 결정으로 *제거*
- `PointService.restorePoints(userId, amount, payment)` 메서드 추가
- `RefundTxOp`가 직접 주입, port 추상화 폐기

## 3. 방식 (Why this way)

### confirm 단계 inventory 호출 제거 — 무엇을 하는지가 모호하면 빼라
- Mock 시점엔 *log.warn만 찍는 placeholder*였음. 실 구현을 만들 때 "이게 무엇을 해야 하는가?"부터 막힘.
- Order 생성 시 비관적 락 + commit으로 *재고가 확정*된 상태. confirm 시점에 "추가로 무엇을" 할 게 없음.
- "실제 일이 없는 호출"을 유지하는 건 *미래 자신에게 거짓말*. 호출 자체 제거가 정직.

### InventoryService 신설하지 않음 — 일관성 + YAGNI
- AI는 *"port 추상화 도입이 깔끔하다"*는 입장. 그러나:
  - `OrderFacade`가 이미 ProductService 직접 호출 — 일관성 깨짐
  - 어댑터 1개뿐인 상태(`ProductService`)에 port를 두는 건 조기 추상화
  - 미래에 별도 InventoryService가 필요해지면 *그때* 도입하는 게 비용 효율적
- "port 도입 기준 = 시그니처 안정 + 어댑터 ≥ 2 가능성"을 명문화.

### productId 정렬 — 데드락 cycle 방지
- 재고 복구 락은 *Row Lock* (Product 행). 두 트랜잭션이 같은 상품들을 *다른 순서*로 잠그면 cycle.
- `OrderFacade.createOrder`가 *이미* 같은 정렬 패턴 사용. 보상에서도 동일하게 적용해 *모든 흐름이 같은 순서로 잠금*.
- 비교적 자명한 결정인데, mock 시점엔 *호출 1번*이라 정렬 개념이 없었음. 실 구현 시점에 *세부 락 동작*이 보이면서 정렬 필요성 인지.

## 4. 결과 (Outcome)

- **코드 변경**:
  - `MockInventoryService.java` 삭제
  - `payment/infrastructure/mock/` 디렉터리 정리 (4개 mock 모두 제거)
  - `PaymentConfirmationService` — inventory 호출 라인 제거
  - `PaymentCompensationTxOp` — `ProductService` 직접 주입 + productId 정렬 후 `restoreStockWithLock` per item
  - `RefundTxOp` — 동일 패턴 적용 (Refund 저장 → Point 복구 → 재고 복구 productId 정렬)
  - `refund.application.PointRestorePort` + `MockPointRestoreAdapter` 제거 (4차)
- **테스트**:
  - `PaymentCompensationTxOpTest` 3 케이스 신규 (정상 / 일부 실패 / PG 호출 실패)
  - `RefundProcessServiceIntegrationTest`에 `Product.stockQuantity` 검증 추가
- **문서**:
  - ADR-0003 "실구현 도입 진행 이력" 3차·4차 기록
  - AI-ADR-006 "내가 수정한 부분"에 InventoryService 신설 거부 + port 도입 기준 명시
- **부하 검증**: 동시 환불 2건이 같은 상품들을 잠글 때 데드락 발생 0건

## 5. 좋아진 점 (What got better)

- **"호출이 무엇을 하는지 모호하면 호출 자체를 의심"하는 습관.** Mock 시점에 *log.warn placeholder*인 상태로 살아남은 호출들이 *실제론 일이 없음*인 케이스가 종종 있음. 실 구현 시점이 *호출 의미를 재검토*하는 가장 좋은 타이밍.
- **port 도입 기준이 팀 컨벤션으로 정착.** "시그니처 안정 + 어댑터 ≥ 2 가능성"이 보이지 않으면 port 안 둠. *3차 결정이 4차에서 자동 적용*되며 일관성 확보 — refund 도메인이 일찍 도입한 `PointRestorePort`를 사후에 제거하는 데도 같은 기준이 작동.
- **데드락 cycle 방지 = "모든 흐름이 같은 락 순서를 따른다"는 약속.** 재고 복구 productId 정렬은 *말로는 자명*하지만 *코드에 박혀있어야 의미*. `OrderFacade.createOrder`와 `PaymentCompensationTxOp`, `RefundTxOp`가 *모두 같은 정렬 패턴*. 새 흐름이 추가될 때 "정렬 빠뜨리면 안 됨"이 PR 리뷰의 자동 체크 항목이 됨.
- **AI가 *port 추상화*를 좋아한다는 패턴 인식.** AI는 객체지향 *교과서적 패턴*을 선호하지만, 우리 코드의 *실제 일관성·YAGNI 컨벤션*은 다를 수 있음. 매번 "현재 코드 패턴이 이미 무엇인가?"를 프롬프트에 명시하는 습관.
