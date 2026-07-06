# AI-ADR-010: 환불 트랜잭션 — 선검증 → 비관적 락 + 재검증 → DB 커밋 → TX 밖 PG 취소

- 상태: Accepted
- 일자: 2026-06-05
- 관련 Story: `workflows/products/product02.md` Story 2-2 (환불 처리 도메인 서비스)
- 관련 ADR: 본 토픽은 일반 ADR로 분리 작성하지 않음 (RefundProcessService 코드가 SSOT)
- 관련 룰:
  - `.claude/rules/consitency.md §5, §6` — 락 순서 + DB 커밋 후 외부 호출
  - `.claude/rules/idempotency.md §4` — S+ (DB 유니크 + 사전조회 이중)

---

## Context

환불 처리 흐름은 다음 부수효과를 한 번에 일으킨다:
- Refund Aggregate 저장 (DB_COMMITTED)
- 재고 복구 (Inventory)
- 포인트 복구 + 적립분 회수 (Point)
- PortOne PG 취소 호출 (외부)

설계 결정 4가지:
1. **트랜잭션 경계** — 단일 TX vs 분리
2. **외부 호출 위치** — TX 안 vs 밖
3. **동시성 제어** — 같은 paymentId에 두 환불이 동시 진입 시 한 건만 성공해야 함
4. **선검증 vs 락 후 재검증** — 선검증은 fast-fail에 유리하지만 race를 차단 못함

함정:
- 단일 TX에 PortOne 호출을 두면 → DB 락이 PG latency만큼 hold (consistency.md §6 위반)
- 락 없이 선검증만 → 동시 환불 두 건이 모두 잔여 수량 검증 통과 후 둘 다 진행 → 초과 환불
- 락만 사용 → 잘못된 요청도 락 획득까지 비용 발생

---

## Decisions

### 3단계 흐름 (`RefundProcessService.process`)

```
Step 1: 선검증 (락 없는 fast-fail)
  → Payment 조회 + RF003 소유권 검증
  → RF002 결제 상태 검증 (isCompleted)
  → Order 로드 + OrderItem 매핑
  → sumRefundedQuantity (잔여 수량 *사전* 검증) → RF001
  → RefundAmountCalculator.calculate (환불 금액 산정)

Step 2: DB 트랜잭션 (REQUIRED, RefundTxOp.executeRefund)
  → SELECT ... FOR UPDATE on payments (비관적 락)
  → 잔여 수량 *재계산·재검증* (race 차단) → RF001
  → Refund 저장 (status=DB_COMMITTED)
  → 재고 복구 (productId 정렬 후 ProductService.restoreStockWithLock per item)
  → 포인트 복구 + 적립분 회수 (PointService.restorePoints)
  → 커밋

Step 3: PG 취소 (TX 밖)
  → PortOnePaymentCancelPort.cancel(portonePaymentId, pgRefundAmount, reason, "refund-{id}")
  → 성공: refundTxOp.markPgCancelled(refundId) — 단일 UPDATE TX
  → 실패: log.error [REFUND_PG_CANCEL_FAILED] + Refund는 DB_COMMITTED 상태 유지
```

### 핵심 결정 6가지

1. **선검증을 락 없이 먼저**: 잘못된 요청(잔여 수량 초과, 소유권 실패)을 락 획득 비용 없이 fast-fail. UX 응답성 ↑.
2. **선검증 *후에도* 락 획득 + 재검증**: idempotency.md §4 S+ 패턴 (사전조회 + DB 락 이중). 선검증 이후 락 획득 사이에 다른 환불이 commit되면 잔여 수량이 줄어듦 → 재검증으로 차단.
3. **PortOne 호출은 TX 밖**: consistency.md §6. DB 락이 PG latency에 종속되면 동시성 처참.
4. **PG 취소 실패는 DB 유지 + log.error**: 환불은 *사용자 관점에서* 이미 DB에 반영됨. PG 실패는 운영 보강(별도 reconciliation 작업).
5. **markPgCancelled는 별도 단일 UPDATE TX**: PG 호출 성공 후 Refund 상태 전이만 가벼운 트랜잭션에서. 메인 환불 TX와 분리.
6. **PG=0 (포인트 전액 결제)은 PG 호출 skip**: PortOne 0원 취소 요청의 안전성을 가정하지 않고 호출 자체를 빼고 즉시 `PG_CANCELLED`로 마킹.

### 락 순서 (consistency.md §5 준수)
- `Order → Payment → Point → Inventory`
- 환불 TX 내부 순서: `Payment FOR UPDATE` → Refund 저장 → 재고 복구(productId 정렬) → 포인트 복구
- productId 정렬은 다른 환불이 같은 상품을 다른 순서로 잠그면서 데드락 cycle 만드는 걸 차단

---

## 프롬프트 내용 (AI 활용시)

> "환불 처리 트랜잭션 설계 도와주세요.
>
> 부수효과:
> - Refund 저장
> - 재고 복구
> - 포인트 복구 + 적립분 회수
> - PortOne PG 취소 호출
>
> 제약:
> - DB 락이 PG latency를 기다리면 안 됨 (consistency §6)
> - 같은 paymentId 동시 환불 두 건 동시 진입 시 한 건만 성공
> - 잘못된 요청(잔여 수량 초과 등)은 빨리 거절 (fast-fail)
> - 락 순서는 Order → Payment → Point → Inventory
>
> 헷갈리는 점:
> 1) 선검증을 락 전에 할까, 락 후에만 할까?
> 2) PortOne 호출 실패 시 DB 롤백할까?
> 3) markPgCancelled (PG 호출 성공 후 상태 전이)는 메인 TX에 포함시킬까, 별도 TX?"

---

## AI 응답 요약

### Q1: 선검증 위치
- **사전 + 사후 이중 검증 권장.** idempotency.md §4 S+ 패턴.
- 사전 검증 (락 없음):
  - 빠른 fail (잘못된 요청을 락 비용 없이 거절)
  - 응답성 ↑
- 사후 검증 (락 획득 후 재계산):
  - 선검증 통과 후 다른 환불이 commit되면 잔여 수량이 변함 → 재검증 필수
  - DB 레벨 원자성 보장

### Q2: PG 호출 실패 시
- **DB 롤백 X.** 환불은 *사용자 관점에서* 이미 처리됨.
- PG 호출 실패는 외부 시스템 일시 장애 또는 멱등 안전성 검증 영역 → 운영 보강(reconciliation)
- DB 롤백하면 사용자는 *환불 진행 중인데 갑자기 취소*된 경험. 정합성도 깨짐(재고·포인트만 복구된 뒤 롤백)

### Q3: markPgCancelled 위치
- **메인 TX 안 X.** 메인 TX는 PG 호출 전에 이미 commit됨. PG 호출 후 별도 단일 UPDATE TX.
- 별도 TX의 장점: 메인 TX 종료 후 PG 호출에 시간이 걸려도 락 hold 0. UPDATE 자체는 가벼움.
- self-injection 회피를 위해 `RefundTxOp`를 별도 `@Component`로 분리 (`@Transactional` 메서드 호출이 같은 클래스 내부면 프록시 안 거침).

### 락 데드락 방지
- `productId` 정렬 후 재고 복구. 다른 환불이 같은 상품들을 *다른 순서로* 잡으면 cycle 가능.
- `OrderFacade.createOrder`도 같은 정렬 패턴 — 일관성 유지.

---

## Consequences

### 장점
- **consistency.md §5, §6 정확히 준수** — 락 순서 통일 + DB 커밋 후 외부 호출
- 잘못된 요청 fast-fail — 락 획득 비용 없이 RF001/RF002/RF003 거절
- 동시 환불 race 차단 — 비관적 락 + 재검증으로 S+ 등급
- PG 실패가 사용자 응답에 *완전한 실패*로 노출되지 않음 — `DB_COMMITTED` 상태 + 202 (AI-ADR-011)
- markPgCancelled가 별도 단일 UPDATE TX — 락 hold time 최소

### 단점 / 알려진 한계
- **PG 취소 실패 시 잔존 상태**: Refund는 `DB_COMMITTED` 상태로 남음. PortOne은 결제 그대로. 자동 재시도 없음 → 운영 알람·수동 보강 필요
- **레어 케이스**: DB 커밋 후 PortOne 호출 직전 프로세스 다운 → DB는 환불 처리됐는데 PortOne은 결제 그대로 → reconciliation 잡으로 보강
- 3단계 흐름이 길어 첫 코드 읽을 때 인지 부담 ↑ → 주석 + 본 ADR로 보강
- 사전 검증과 사후 검증이 같은 로직(`sumRefundedQuantity`)을 두 번 호출 — race 차단 비용. 빈도 낮아 부담 X

### 운영 진입 전 필수
- PG 취소 실패 시 운영 알람 (Slack/이메일/PagerDuty)
- 정기 reconciliation: `Refund.status = DB_COMMITTED`인데 PortOne status가 PAID로 잔존하는 건 감지 → 수동 보강
- WebhookEvent 모니터링 (PortOne이 PG 취소 후 다른 이벤트 발송 시)

---

## 내가 수정한 부분

- AI는 처음에 *선검증만으로 충분*하다는 제안. **동시 환불 race**를 명확히 짚어 **사전 + 사후 이중 검증**으로 변경. idempotency.md §4 S+ 패턴이 컨벤션이라 명시.
- PortOne 호출을 메인 TX 안에 두자는 단순화 제안 → **consistency.md §6 위반**임을 명시. 락 hold time이 PG latency에 종속되면 동시성 처참.
- AI는 PG 실패 시 DB 롤백을 제시했음. **롤백하면 사용자 UX 깨짐 + 정합성 어색**. DB 유지 + 운영 보강으로 변경.
- markPgCancelled를 메인 TX 메서드에 같이 두자는 AI 제안. **메인 TX는 PG 호출 전에 이미 커밋**되었으므로 같은 TX 안에 둘 수 없음. self-injection 회피 위해 `RefundTxOp` 별도 컴포넌트 분리 (AI-ADR-003의 `PaymentCompensationTxOp` 패턴 재사용).
- 재고 복구 순서: AI는 처음에 OrderItem 순서대로 처리. **productId 정렬 패턴**을 OrderFacade에서 가져와 적용. 데드락 cycle 차단.
- **PG=0 case (포인트 전액 결제) PortOne 호출 skip**: AI는 호출하고 PortOne 응답으로 판단하자 제시. PortOne 0원 취소 동작이 명확하지 않고, *호출 자체가 의미 없음* → 호출 skip + 즉시 `PG_CANCELLED` 마킹 (`RefundProcessService:82-86`).

---

## 최종 반영 여부

- ✅ 코드 반영:
  - `RefundProcessService.process` (`refund.application.RefundProcessService`)
  - Step 1 선검증 (`:48-77`)
  - Step 2 DB TX (`RefundTxOp.executeRefund`)
  - Step 3 PG 취소 + markPgCancelled (`:81-101`)
  - PG=0 skip 분기 (`:82-86`)
- ✅ `RefundTxOp` 별도 `@Component`:
  - `executeRefund(command, breakdown)` — `@Transactional` + 비관적 락 + 재검증 + Refund 저장 + 재고/포인트 복구
  - `markPgCancelled(refundId, pgCancelTxId)` — 별도 단일 UPDATE TX
- ✅ 락 순서: Payment FOR UPDATE → Refund 저장 → productId 정렬 후 재고 복구 → 포인트 복구
- ✅ PG 취소 실패 로그 마커: `[REFUND_PG_CANCEL_FAILED]` (`RefundProcessService:97`)
- ✅ 통합 테스트: 동시 환불 race / 단일 TX 보장 / PG 실패 시 DB 유지
- 미래 트리거:
  - PG 취소 자동 재시도 ADR
  - reconciliation 잡 ADR
  - 운영 알람 시스템 통합
