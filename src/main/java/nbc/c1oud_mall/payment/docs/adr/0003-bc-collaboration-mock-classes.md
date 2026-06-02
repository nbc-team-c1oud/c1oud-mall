# ADR-0003: BC 협력 — Mock 구체 클래스 + 사후 의존성 재정비

- 상태: Accepted
- 일자: 2026-06-01
- 범위: 결제 BC (Story 2-2)

## Context

Story 2-2 결제 확정 서비스(`PaymentConfirmationService`)는 단일 트랜잭션 안에서 주문 완료·포인트 사용/적립·장바구니 초기화·재고 확정 같은 부수효과를 일으켜야 한다(consistency.md §2 결제 확정 zone).

문제는 작업 시점에 Order / Point / Cart / Inventory BC의 결제 확정 연동 진입점이 **다른 담당자에 의해 미구현**이라는 것. 그리고 각 BC의 진짜 인터페이스 시그니처도 아직 확정되지 않았다.

이 상태에서 결제 BC가 **헥사고날 outbound port + 어댑터** 패턴을 미리 도입하면:

- port 인터페이스의 시그니처를 결제 BC가 추측해서 짜야 함
- 향후 다른 BC가 자기 인터페이스를 정의할 때 시그니처가 안 맞으면 port·stub·서비스·테스트 4중 수정 발생
- 추상화가 무가치한 상태에서 도입되어 코드 복잡도만 증가 (조기 추상화)

## Decision

1. **추상화(port 인터페이스) 도입하지 않는다**. 결제 BC는 다른 BC 의존을 **mock 구체 클래스**로 직접 가진다.

2. **`src/main/java/nbc/c1oud_mall/payment/infrastructure/mock/`에 4개 mock 구체 클래스를 둔다**:
   - `MockOrderService` — `completeOrder(Long orderId)`
   - `MockPointService` — `deductPoints(Long, long)` + `accruePoints(Long, long)`
   - `MockCartService` — `clearByUserId(Long)`
   - `MockInventoryService` — `confirmByOrderId(Long)`

3. **각 mock 클래스**:
   - `@Component` (Spring 빈)
   - `@Slf4j`
   - 메서드 본문은 `log.warn("[MOCK] ... 실구현 도입 시 교체", args)` — 운영 진입 시 log scan으로 잔존 추적
   - 클래스명에 `Mock` prefix 명시 — 코드 읽는 사람과 grep 검색 모두에게 의도 노출

4. **`PaymentConfirmationService`는 mock 구체 클래스에 직접 의존**한다 (`@RequiredArgsConstructor`).

5. **실구현 도입 시 의존성 재정비 절차**:
   1. 실구현 BC가 `OrderService` 같은 클래스를 자기 패키지에 만들고 빈으로 등록
   2. `PaymentConfirmationService`에서 `MockOrderService` import를 `nbc.c1oud_mall.order.application.OrderService` 같은 실 클래스로 교체
   3. 필드 타입 변경 (`private final OrderService orderService;`)
   4. 메서드 시그니처 차이가 있으면 호출부·테스트 인자 조정
   5. `MockOrderService.java` 파일 삭제
   6. 4개 BC 모두에 대해 반복

## Alternatives

- **헥사고날 outbound port + NOOP stub adapter** (직전 시도): 미정 인터페이스의 조기 추상화 + port·stub·서비스·테스트 4중 수정 비용. 가치 대비 복잡도 과잉으로 회수.
- **port 인터페이스만 정의, stub 없음**: Spring 컨텍스트 시작 자체가 안 됨. dev/prod 부팅 불가.
- **Optional 주입**: `Optional<OrderCompletionPort>` 같은 패턴. 서비스 코드가 `ifPresent(...)`로 더러워지고, "있어야 한다"는 의도가 코드에서 흐려짐.
- **공유 모듈(`common.collaboration.port`)에 port 정의**: 조기 추상화 + BC 간 결합도 상승. 후속 수정이 모든 BC에 파급.
- **메시지 큐(이벤트 발행만)**: 단일 트랜잭션 일관성 보장 안 됨 (consistency.md §2 zone 위반).

## Consequences

### 장점
- 다른 BC 미구현 상태에서도 결제 BC 단독 개발·테스트·배포 가능 (mock이 빈으로 등록되어 컨텍스트 시작)
- 코드 복잡도 최소 — 4개 클래스 + 직접 의존만으로 동작
- 실구현 도입 시 변경 범위가 명확 (mock 파일 삭제 + import·필드 교체)
- `Mock` 클래스명·`log.warn` 메시지로 운영 진입 시 잔존 추적 가능

### 단점
- 결제 BC 코드가 다른 BC의 구체 타입(혹은 그 mock)을 직접 import → 헥사고날 원칙 위반. 다만 mock이 명시된 임시 상태라 의도가 흐려지지는 않음.
- 실구현 도입 시 결제 BC 코드를 손대야 함 (port 패턴이라면 빈 교체만으로 됐을 작업). 현 단계 비용 절감과 트레이드.
- 운영 환경에서 mock이 살아있는 채로 결제 확정이 호출되면 주문 완료·포인트 처리 등이 조용히 누락됨 → log.warn 시그널 + 운영 진입 전 별도 검증 필요.

### 운영 진입 전 필수 검증
- 운영 프로파일 부팅 시 `Mock*Service` 빈이 존재하면 **부팅 실패** 시키는 `@Profile` 또는 헬스체크 도입 권장 (별도 작업)
- 또는 통합 테스트에서 운영 프로파일 + mock 잔존 시 명시적 fail 어서션

## References

- workflows/product.md — [Story 2-2] 결제 확정 도메인 서비스
- .claude/rules/consistency.md §2, §6 — 결제 확정 zone(TX-bound) + 외부 호출 위치
- .claude/rules/idempotency.md §2 — 결제 확정 진입점 멱등성
- ADR-0001 — `portonePaymentId` 채번 전략
- ADR-0002 — PortOne V2 REST API HTTP 클라이언트 선택
