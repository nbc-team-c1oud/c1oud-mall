# ADR-0004: 결제 보상 트랜잭션 패턴 (DB 커밋 + TX 밖 PG 취소)

- 상태: Accepted
- 일자: 2026-06-02
- 범위: 결제 BC (Story 2-3)

## Context

Epic 2 결제 확정 흐름에서 외부(PortOne) 결제는 PAID 성공인데 우리 측 검증이 실패하는 시나리오가 존재한다(예: 금액 위·변조, PortOne status가 PAID로 보고됐으나 우리 측 기록과 불일치). 이때 **보상 흐름**을 실행해 (a) Payment를 FAILED로 표시, (b) 주문 취소·재고 복구, (c) PortOne 결제 자체를 취소해야 한다.

consistency.md §6 규칙:
- "DB 먼저 커밋, 외부 호출은 트랜잭션 밖"
- "보상 가능한 작업만 커밋 후 호출"
- "보상 실패는 운영 이슈 격상 — 자동 재시도 N회 후 알림 + 수동 개입"

또한 호출자(PaymentConfirmationService) 입장에서는:
- 메인 트랜잭션은 검증 실패 시 **반드시 롤백**되어야 함 (markCompleted·부수효과 미반영 보장)
- 보상은 **별도 트랜잭션**에서 commit 보존되어야 함 (메인 롤백이 보상까지 휩쓸면 의미 없음)

## Decision

1. **`PaymentCompensationService.compensate(portonePaymentId, reason)`는 비-트랜잭션 메서드**.
2. 내부에서 두 단계로 분리:
   1. **`PaymentCompensationTxOp.compensateDb(...)`** — 별도 `@Component`로 분리한 컴포넌트의 `@Transactional(propagation = Propagation.REQUIRES_NEW)` 메서드. 처리 순서: ① `OrderService.findOrderEntity`로 Order+items 로드 → ② `Payment.markFailed` → ③ `productId` 정렬 후 `ProductService.restoreStockWithLock(productId, quantity)` per item으로 재고 복구(consistency.md §5 락 순서·데드락 방지 정렬) → ④ `OrderService.cancelOrder`. (재고 복구는 2026-06-08 이전엔 `MockInventoryService.restoreByOrderId` placeholder였음 — ADR-0003 3차 진행 참조.) DB 작업 commit.
   2. **`PortOnePaymentCancelPort.cancel(...)`** — TX 종료 후 외부 호출. 4xx/5xx/IO 실패 시 `BusinessException(PORTONE_CANCEL_FAILED, PM009)` 발생.
3. **PortOne 취소 실패는 `log.error`로 처리**, `compensate` 메서드는 정상 종료. 호출자에게 예외 전파하지 않음 (보상은 백그라운드 처리로 간주).
4. **`PaymentConfirmationService` 정정**: 검증 단계 try-catch + `isCompensable(ex)` 판단 후 `compensate` 호출 → 원 `BusinessException` 재 throw. 메인 TX는 롤백되고 클라이언트는 4xx 받음.
5. **`isCompensable` 정책**:
   - `PAYMENT_AMOUNT_MISMATCH (PM001)`: ✓ 보상 (PortOne 결제 취소 + 우리 측 마킹)
   - `PORTONE_PAYMENT_NOT_PAID (PM007)`: ✓ 보상 (우리 측 마킹. PortOne 취소 호출은 멱등이라 안전)
   - `PAYMENT_AUTHORIZATION_FAILED (PM006)`: ✗ 미보상 (인증 문제 — 다른 사용자의 결제 요청. 원본 결제는 그대로 두고 4xx만 반환)

## Alternatives

- **단일 `@Transactional` + 트랜잭션 안 외부 호출**: 가장 단순하나 consistency.md §6 명시 위반. PortOne 호출 동안 DB 락 점유, 외부 실패 시 DB 작업도 전부 롤백되어 보상 의도 깨짐.
- **`@TransactionalEventListener(AFTER_COMMIT)`**: 메인 TX 커밋 후 비동기로 PortOne 취소 호출. 본 흐름은 메인 TX가 롤백되어야 하므로 부적합. 별도 보상 TX commit 후 이벤트 발행하는 변형은 가능하나 1차에는 과잉 설계.
- **메시지 큐 발행**: 트랜잭션과 분리된 보상 워커. 1차 단계 인프라 부담 큼. 향후 운영 규모 확대 시 검토.
- **모든 검증 실패에 일괄 보상 (isCompensable 분기 없음)**: 단순하나 PM006(소유권 위반)에서 다른 사용자의 정상 결제까지 취소될 위험. 분기로 안전성 확보.

## Consequences

### 장점
- consistency.md §6 의도 준수: DB 커밋 후 외부 호출, 외부 실패 수용
- 메인 TX 롤백과 보상 TX commit이 명확히 분리되어 의도 가독성 ↑
- PortOne 취소 실패가 클라이언트 응답에 노출되지 않음 (보상은 운영 측 작업)

### 단점
- self-injection 회피를 위한 별도 컴포넌트(`PaymentCompensationTxOp`) 추가 — 클래스 1개 늘어남
- PortOne 취소 실패 시 로그만 남고 자동 재시도·운영 알림 없음 — 운영 진입 전 별도 시스템 도입 필요
- DB 커밋 후 PortOne 호출 직전 프로세스 다운 시: DB는 FAILED 마킹되었으나 PortOne은 PAID 잔존 → 불일치. 향후 reconciliation 작업 별도 필요

### 운영 진입 전 필수
- PortOne 취소 실패 시 운영 알림 시스템 연동 (Slack·이메일·PagerDuty 등)
- 정기 reconciliation 잡 — Payment.status FAILED인데 PortOne status 미일치 항목 감지 후 수동 보상

## References

- workflows/product.md — [Story 2-3] PG 보상 취소 + 보상 흐름
- .claude/rules/consistency.md §6 — DB 커밋 후 외부 호출, 보상 트랜잭션
- .claude/rules/idempotency.md §7 — PortOne 보장(`paymentId` 멱등 취소)에 의존하지 말고 우리 측 멱등 추가
- .claude/rules/errorhandling.md §3 — 외부 SDK 예외를 application에서 번역
- ADR-0002 — PortOne V2 REST API HTTP 클라이언트 (취소 어댑터도 같은 RestClient 패턴 사용)
- ADR-0003 — BC 협력 mock 구체 클래스 (cancelOrder·restoreByOrderId 호출 대상)
