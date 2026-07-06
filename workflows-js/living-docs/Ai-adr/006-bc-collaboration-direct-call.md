# AI-ADR-006: BC 간 협력 — Port 추상화 보류 + Mock 구체 클래스 직접 호출

- 상태: Accepted (1차 결정) / Superseded by 사후 의존성 재정비 (2026-06-04~06-08)
- 일자: 2026-06-01
- 관련 Story: `workflows/product.md` Story 2-2 (결제 확정 도메인 서비스 — BC 협력 방식 협상 여지)
- 관련 ADR: `payment/docs/adr/0003-bc-collaboration-mock-classes.md`

---

## Context

Story 2-2 결제 확정 서비스는 단일 트랜잭션 안에서 **다른 BC**들의 진입점을 호출해야 한다:

- Order BC — `completeOrder(orderId)`
- Point BC — `deductPoints(...)` + `accruePoints(...)`
- Cart BC — `clearByUserId(userId)`
- Inventory BC — `confirmByOrderId(orderId)`

문제: **결제 BC 작업 시점에 4개 BC의 진입점이 미구현 + 인터페이스 시그니처도 미확정.**

이 상태에서 흔히 선택하는 패턴:
- (a) **헥사고날 outbound port + 어댑터**: 결제 BC가 인터페이스 정의 → 다른 BC가 추후 구현
- (b) **Mock 구체 클래스 직접 호출**: 결제 BC가 임시 구체 클래스에 의존, 추후 실 구현으로 교체
- (c) **공유 모듈에 port 정의**: `common.collaboration.port`에 인터페이스
- (d) **이벤트 발행만**: 결제 완료 도메인 이벤트만 발행, 각 BC가 구독

각 선택의 함정:
- (a) — 결제 BC가 시그니처를 추측 → 실 구현이 다르면 port·stub·서비스·테스트 4중 수정
- (b) — 헥사고날 원칙 위반. 추상화가 아예 없음 → 추후 교체 비용
- (c) — 조기 추상화 + BC 간 결합도 ↑
- (d) — 결제 확정의 단일 트랜잭션 일관성 깨짐 (consistency §2 zone 위반)

---

## Decisions

### 1. (b) Mock 구체 클래스 + 직접 호출 채택 (1차)
- 추상화 미도입. 미정 인터페이스의 조기 추상화 비용이 가치 초과.
- `payment.infrastructure.mock/` 패키지에 4개 mock 구체 클래스
- 이름에 `Mock` prefix 명시 — 의도 노출

### 2. Mock 클래스 규칙
- `@Component` + `@Slf4j`
- 메서드 본문: `log.warn("[MOCK] ... 실구현 도입 시 교체", args)` — 운영 진입 시 grep 추적
- Mock은 **호출만 받고 부수효과 없음** (DB 변경 X)

### 3. 실 구현 도입 시 교체 절차
1. 실 BC가 `OrderService` 같은 클래스 신설 + `@Component` 빈 등록
2. `PaymentConfirmationService`의 import 교체 (`MockOrderService` → `OrderService`)
3. 필드 타입 변경
4. 메서드 시그니처 차이 시 호출부/테스트 조정
5. `MockOrderService.java` 삭제
6. 4개 BC 반복

### 4. 일반 ADR(0003)의 "Superseded" 항목
- `Mock*Service` 교체가 진행되면서 **port 패턴은 끝까지 도입하지 않음** 결정 — Mock → 실 구현으로 직접 교체. `OrderFacade` 등 다른 도메인이 `ProductService`를 직접 주입하는 패턴과 일관.

---

## 프롬프트 내용 (AI 활용시)

> "결제 확정 서비스가 Order/Point/Cart/Inventory 4개 BC를 부르는데, 다른 사람들이 아직 구현 안 했고 인터페이스 시그니처도 정해지지 않았습니다.
>
> 옵션:
> (a) 헥사고날 outbound port + NOOP stub adapter
> (b) Mock 구체 클래스 직접 호출
> (c) common 모듈에 port 정의
> (d) 도메인 이벤트만 발행
>
> 결제 확정은 단일 트랜잭션 일관성이 핵심이라 (d)는 위험할 것 같습니다. (a)는 깔끔한데 시그니처 추측 비용이 클 것 같고요. 어떻게 가야 하나요?"

---

## AI 응답 요약

### 옵션 비교

| 옵션 | 장점 | 단점 |
|---|---|---|
| (a) port + stub | 추후 교체 빈 swap만 | 시그니처 추측 → port·stub·서비스·테스트 4중 수정 가능. **조기 추상화** |
| (b) Mock 구체 | 단순. 변경 범위 명확 | 헥사고날 원칙 위반. 실 구현 도입 시 결제 BC 코드 수정 |
| (c) common port | BC 경계 명확 | BC 간 강결합. 변경 시 모든 BC에 파급. **조기 추상화 + 결합 ↑** |
| (d) 이벤트 | BC 완전 분리 | **단일 TX 일관성 깨짐**. consistency §2 결제 확정 zone 위반 |

### 권장
- 본 상황에서는 **(b) Mock 구체 클래스**가 ROI 최선.
- 추상화는 *시그니처가 안정된 후*에 도입해야 가치가 있음. 미정 상태에서 도입은 비용만 추가.
- 단 운영 진입 시 mock 잔존 위험 → 명시적 가드 필요.

### 추가 권장
- 클래스명에 `Mock` prefix
- `log.warn("[MOCK]...")` 호출 시 마커 — 운영 grep
- `@Profile("!prod")` 로 운영에서 빈 등록 차단 가능 (1차 도입 X, 운영 진입 전)
- 또는 **운영 부팅 시 mock 빈 존재 감지 → 부팅 실패** assertion (별도 작업)

---

## Consequences

### 장점
- 다른 BC 미구현 상태에서도 결제 BC 단독 개발/테스트/배포 가능
- 코드 복잡도 최소 — 4개 mock + 직접 의존
- 실 구현 도입 시 변경 범위 명확 (mock 삭제 + import/필드 교체)
- `Mock` prefix + `log.warn` 마커로 잔존 추적 가능

### 단점
- 결제 BC가 다른 BC의 구체 타입(혹은 mock)을 직접 import → 헥사고날 원칙 위반
- 실 구현 도입 시 결제 BC 코드 손대야 함 (port 패턴이라면 빈 교체만으로 가능했을 작업)
- **운영에서 mock 잔존 시 부수효과 조용히 누락** — log.warn 외 가드 없음 (운영 진입 전 보강 필요)

### 운영 진입 전 필수 검증
- 운영 부팅 시 `Mock*Service` 존재 감지 → 부팅 실패 또는 헬스체크 fail
- 통합 테스트에 "운영 프로파일 + mock 잔존" 명시적 fail 어서션

---

## 사후 의존성 재정비 진행 (실 구현 도입)

| 차수 | 일자 | 교체 대상 | 결과 |
|---|---|---|---|
| 1차 | 2026-06-04 | `MockOrderService → OrderService` | 주문 BC 실 구현 도입. import/필드 교체, 단위 테스트 갱신 |
| 2차 | 2026-06-05 | `MockPointService → PointService`, `MockCartService → CartService` | 포인트 BC `deductPoints/accruePoints(userId, amount, payment)` 실 구현 + Cart `clearCart(userId)` |
| 3차 | 2026-06-08 | `MockInventoryService → ProductService 직접 호출` | Inventory BC는 별도 서비스 신설 안 함. `OrderFacade.createOrder`가 이미 쓰는 `ProductService.deductStockWithLock` 패턴을 결제 보상에서도 직접 호출 (productId 정렬 + 비관적 락). **결제 확정 단계 inventory 호출은 제거** — 재고는 Order 생성 시 차감 확정 |
| 4차 | 2026-06-08 | `MockPointRestoreAdapter / PointRestorePort → PointService.restorePoints 직접` | 환불 흐름의 port 추상화도 동일 결정에 맞춰 제거. `PointService.restorePoints(userId, amount, payment)` 추가, RefundTxOp 락 순서 재배치(Order → Payment → Point → Inventory) |

### 차수별 교훈
- 1~2차: 시그니처가 안정된 BC부터 안전하게 교체. 단위 테스트 1개씩 갱신해서 회귀 위험 낮음.
- 3차: 처음에 `InventoryService` 신설을 검토했으나 **이미 `ProductService`로 충분** — *port 도입의 유혹*을 한 번 더 기각. OrderFacade 패턴과 일관성 확보 + 클래스 수 절감.
- 4차: refund 도메인에 일찍 도입했던 `PointRestorePort` 추상화를 사후 제거. **port 도입은 시그니처가 안정되고 두 개 이상의 어댑터 가능성이 보일 때만** — 이를 다른 신규 도메인 작업 시 기준으로 명시.

---

## 내가 수정한 부분

- AI는 처음에 (a) port + NOOP stub을 적극 권장. **시그니처 추측 비용을 과소 평가**한 추천이라 판단 — 결제 BC가 `MockOrderService.completeOrder(Long)` 라고 정한 시그니처가 실 구현에서 `OrderService.completeOrder(Long orderId, Long paymentId)` 같은 형태로 다르면 4중 수정. (b)로 변경.
- AI는 운영 가드를 부드럽게 제시(`@Profile`). **운영 부팅 시 mock 빈 감지 → 부팅 실패**라는 더 강한 가드로 격상 결정 (1차 도입 X, 운영 진입 전).
- 3차(2026-06-08)에서 처음엔 `InventoryService` 신설을 AI가 제시. **OrderFacade가 이미 ProductService 직접 호출**하는 패턴이 있어 일관성 깨짐 + 클래스 늘어남. → `ProductService` 직접 호출 + productId 정렬 패턴으로 변경.
- 4차에서 refund의 port를 사후 제거하면서 **"port 도입 기준"**(시그니처 안정 + 어댑터 ≥ 2 가능성)을 ADR 0003에 명시.

---

## 최종 반영 여부

- ✅ 1차 코드: `payment.infrastructure.mock/` 4개 mock 클래스 + 직접 의존
- ✅ 1~4차 사후 재정비 완료 — 모든 Mock 삭제, 실 BC 직접 호출로 통일
- ✅ 일반 ADR: `payment/docs/adr/0003-bc-collaboration-mock-classes.md` "실구현 도입 진행 이력" 1차~4차
- ✅ Refund 도메인도 같은 결정 적용 (PointRestorePort 제거)
- 미래 트리거:
  - 운영 부팅 시 Mock 빈 감지 가드 (현재는 모두 제거됐으므로 비활성)
  - 신규 도메인에서 port 도입 시 "시그니처 안정 + 어댑터 ≥ 2" 기준 확인
  - BC 간 통신을 도메인 이벤트로 전환할 시점 (단일 TX 일관성 분리 가능 여부 ADR)
