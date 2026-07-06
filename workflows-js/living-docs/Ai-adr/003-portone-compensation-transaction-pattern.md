# AI-ADR-003: PortOne 보상 트랜잭션 — DB 커밋 후 외부 호출 + 메인/보상 TX 분리

- 상태: Accepted
- 일자: 2026-06-02
- 관련 Story: `workflows/product.md` Story 2-3 (결제 확정 실패 보상 흐름)
- 관련 ADR: `payment/docs/adr/0004-payment-compensation-transaction-pattern.md`
- 관련 룰: `.claude/rules/consitency.md §6` (DB 먼저 커밋, 외부 호출은 TX 밖)

---

## Context

Epic 2의 가장 까다로운 시나리오: **외부(PortOne)는 PAID 성공인데 우리 측 검증이 실패하는 경우**.

대표 케이스:
- 클라이언트가 SDK에 넘긴 금액을 도중에 변조해서 PortOne은 50,000원 결제했는데 우리 DB의 pgAmount는 5,000원
- PortOne status가 PAID가 아닌 다른 값으로 응답 (사용자가 결제창에서 취소 등)

이때 해야 할 일:
1. Payment를 `FAILED`로 표시
2. Order를 `CANCELLED`로 표시
3. 선차감했던 재고 복구
4. **PortOne 결제 자체를 취소** (PG 보상 호출)

여기서 두 가지 트랜잭션 문제가 생긴다:

**문제 A — 외부 호출 위치**:
- 트랜잭션 안에서 PortOne 취소를 호출하면 DB 락이 외부 latency를 기다림 → 동시성 처참
- consistency.md §6: "DB 먼저 커밋, 외부 호출은 트랜잭션 밖"

**문제 B — 메인 TX와 보상 TX의 분리**:
- 호출자(`PaymentConfirmationService`)의 메인 TX는 검증 실패 시 **반드시 롤백** (markCompleted/포인트 적립 등 부수효과 미반영 보장)
- 그런데 보상은 **commit 보존** 필요 — markFailed/cancelOrder가 롤백되면 의미 없음
- 즉 메인 롤백과 보상 commit이 한 호출 안에서 공존해야 함

---

## Decisions

### 1. 두 단계로 분리
```
PaymentCompensationService.compensate(portonePaymentId, reason)  ← 비-트랜잭션
  ├─ PaymentCompensationTxOp.compensateDb(...) ← @Transactional(REQUIRES_NEW)
  │     ① OrderService.findOrderEntity (Order + items 로드)
  │     ② Payment.markFailed
  │     ③ productId 정렬 → ProductService.restoreStockWithLock per item
  │     ④ OrderService.cancelOrder
  │  → commit
  └─ PortOnePaymentCancelPort.cancel(portonePaymentId) ← TX 밖 외부 호출
       실패 시 log.error + 호출자에 예외 전파 X
```

### 2. `REQUIRES_NEW`로 메인 TX와 분리
- `PaymentCompensationTxOp`를 **별도 `@Component`**로 분리 (self-injection 회피)
- 메인 TX가 검증 실패 → 보상 TxOp는 새 TX → commit → 외부 호출 → 메인 TX는 그 후 throw → 롤백

### 3. 외부 호출 실패는 삼킴 (정확히는 log)
- PortOne 취소 호출 4xx/5xx/IO 실패 → `log.error`만, `compensate` 메서드는 정상 종료
- 호출자는 원 `BusinessException`(PM001 등)을 그대로 4xx로 반환
- → 사용자는 "결제 처리되지 않음" 응답, 운영자는 별도 알람으로 PortOne 잔여 결제 처리

### 4. `isCompensable` 분기 (모든 실패를 보상하지 않음)
| ErrorCode | 보상? | 이유 |
|---|---|---|
| `PAYMENT_AMOUNT_MISMATCH (PM001)` | ✅ | 금액 위변조 의심 — PortOne 취소 필수 |
| `PORTONE_PAYMENT_NOT_PAID (PM007)` | ✅ | 우리 측 마킹 + PortOne 취소 멱등이라 안전 |
| `PAYMENT_AUTHORIZATION_FAILED (PM006)` | ❌ | 다른 사용자 결제 요청 — 원본 결제는 그대로 두고 4xx만 |

---

## 프롬프트 내용 (AI 활용시)

> "결제 확정 흐름에서 외부(PortOne)는 결제 성공인데 우리 검증이 실패하는 케이스가 있습니다. 예를 들어 클라이언트가 금액을 위조하면 PortOne은 50,000원 결제 처리됐는데 우리 DB pgAmount는 5,000원입니다.
>
> 이때 (a) Payment FAILED 마킹, (b) Order 취소, (c) 재고 복구, (d) PortOne 결제 취소 호출이 모두 일어나야 합니다.
>
> 헷갈리는 두 가지:
> 1) PortOne 취소 호출을 트랜잭션 안에서 vs 밖에서?
> 2) 메인 트랜잭션은 검증 실패니까 롤백돼야 하는데, 보상 트랜잭션은 commit이 보존돼야 합니다. 어떻게 분리하나요?
>
> 또 보상이 항상 안전한가요? 다른 사용자의 결제 요청까지 보상하면 안 되는 경우가 있을 것 같은데..."

---

## AI 응답 요약

### Q1: 외부 호출 위치
- **트랜잭션 밖이 정답.** consistency.md §6 명시.
- TX 안에서 외부 호출하면:
  - DB 락이 PortOne latency만큼 hold
  - PortOne 호출 실패 시 DB도 같이 롤백되면 보상 의도(markFailed)가 사라짐
- 표준 패턴: DB 커밋 → 외부 호출 → 실패는 별도 처리

### Q2: 메인/보상 TX 분리
- **`@Transactional(propagation = REQUIRES_NEW)`** 가 표준.
- 단 Spring AOP 특성상 같은 클래스 내부 호출은 프록시를 안 거치므로 self-injection 또는 별도 컴포넌트 필요.
- → **별도 `@Component`**(`PaymentCompensationTxOp`)로 분리 권장.

흐름:
1. 메인 TX (검증 단계)에서 try-catch
2. catch에서 `compensate(...)` 호출
3. `compensate` 내부에서 REQUIRES_NEW TX commit
4. TX 종료 후 외부 PortOne 취소 호출
5. catch 마지막에 원 예외 재 throw → 메인 TX 롤백

### Q3: 모든 실패가 보상 대상인가
- **NO.** 보상이 *추가 피해*를 일으킬 수 있는 케이스를 분기해야 함.
- 예: 소유권 위반(PM006) — 다른 사용자의 정상 결제일 가능성. 보상 시 멀쩡한 결제 취소.
- 분기 함수: `isCompensable(errorCode)` — 화이트리스트.

### 외부 호출 실패 정책
- 1차에서는 `log.error`만, 자동 재시도 X
- 운영 진입 전 알람·재시도·reconciliation 시스템 별도 도입 필요

---

## Consequences

### 장점
- consistency.md §6 정확히 준수 — DB 락이 외부 latency에 종속되지 않음
- 메인 TX 롤백과 보상 TX commit이 명확히 분리 — 코드 의도 가독성 ↑
- PortOne 취소 실패가 클라이언트 응답에 노출되지 않음 (보상은 운영 측 작업)
- `isCompensable` 분기로 무차별 보상으로 인한 2차 피해 차단

### 단점
- `PaymentCompensationTxOp` 별도 컴포넌트 추가 — 클래스 1개 늘어남 (self-injection 회피 비용)
- PortOne 취소 실패 시 로그만 — 운영 알람·재시도 시스템 도입 전엔 사람이 모니터링해야 함
- **레어 케이스**: DB 커밋 후 PortOne 호출 직전 프로세스 다운 → DB는 FAILED, PortOne은 PAID 잔존 → 불일치. 정기 reconciliation 잡으로 처리 (별도 작업)

### 운영 진입 전 필수
- PortOne 취소 실패 시 Slack/이메일/PagerDuty 알람 연동
- 정기 reconciliation: Payment.status FAILED인데 PortOne status가 PAID인 항목 감지 → 수동 보상

---

## 내가 수정한 부분

- AI는 처음에 **모든 검증 실패에 일괄 보상**(`isCompensable` 분기 없음)을 제시했음. 단순하지만 **PM006(소유권 위반)에서 다른 사용자의 정상 결제까지 취소될 위험**을 깨닫고 화이트리스트 분기 추가.
- AI는 self-injection 패턴(`@Autowired private MyService self;`)을 제시했는데, **Spring 권장 패턴이 아니고 가독성도 떨어짐.** → **별도 `@Component` 분리**로 변경. self-injection은 cycle 의심·테스트 복잡도 ↑.
- 보상 trigger 위치를 AI는 "검증 실패 분기마다 호출" 패턴을 제시했는데, **try-catch 한 곳에서 한 번만 호출**하고 `isCompensable(ex)`로 분기하는 형태로 통합. 분기마다 호출하면 추후 검증 단계 추가 시 누락 가능.
- 외부 호출 실패 시 AI는 `log.warn`을 제시했는데, **`log.error` + 명시적 마커**(`[PORTONE_CANCEL_FAILED]`)로 강화. 운영 시 grep·alert 등록 쉬워짐.
- 재고 복구 순서: 처음엔 `MockInventoryService.restoreByOrderId(orderId)` 단일 호출. **2026-06-08 ProductService 직접 호출로 변경하면서 productId 정렬 추가** (consistency.md §5 락 순서 데드락 방지). AI-ADR-002와 연결.

---

## 최종 반영 여부

- ✅ 코드 반영:
  - `PaymentCompensationService.compensate` — 비-트랜잭션
  - `PaymentCompensationTxOp` — 별도 `@Component` + `@Transactional(REQUIRES_NEW)`
  - `PortOnePaymentCancelPort` + 어댑터 (RestClient)
  - `PaymentConfirmationService.confirm` — try-catch + `isCompensable` 분기
- ✅ ErrorCode:
  - `PORTONE_CANCEL_FAILED("PM009", "...", BAD_GATEWAY)` — 보상 내부용
  - `PAYMENT_AMOUNT_MISMATCH("PM001")`, `PORTONE_PAYMENT_NOT_PAID("PM007")` 보상 대상
  - `PAYMENT_AUTHORIZATION_FAILED("PM006")` 보상 제외
- ✅ 일반 ADR: `payment/docs/adr/0004-payment-compensation-transaction-pattern.md`
- 미래 트리거: PortOne 취소 자동 재시도 ADR, reconciliation 잡 ADR, 운영 알람 시스템 통합
