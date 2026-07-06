# [트러블슈팅] 환불 race condition — 사전검증만으로 막힘, 비관적 락 + 재검증 이중(S+ 패턴) 도입

---
date: 2026-06-09
domain: [refund, idempotency, concurrency]
tags: [s-plus-pattern, race-condition, pre-validation, pessimistic-lock, sum-revalidation]
related-story: workflows/products/product02.md Story 2-2
related-adr: workflows/living-docs/Ai-adr/010-refund-transaction-pattern.md
related-topology: workflows/topologys/idempotency-Design-Topology.md, workflows/topologys/Consistency-Design-Topology.md
---

## 1. 문제 (What)

환불 처리 도메인 서비스 설계 중 AI에 시퀀스를 물었더니 처음에 제시한 흐름:

```
1. Payment 조회 + 소유권 검증
2. RF002 결제 상태 검증
3. sumRefundedQuantity로 잔여 환불 가능 수량 검증 (RF001)
4. 환불 금액 산정
5. @Transactional 시작 — Refund 저장 + 재고 복구 + 포인트 복구
6. PortOne 취소 호출
```

이 자체로 *행복 경로*에선 동작. 문제는 동시 환불 race:

```
시간 T1: 환불 A — 잔여 수량 검증 통과 (요청 2개, 잔여 2개)
시간 T1: 환불 B — 잔여 수량 검증 통과 (요청 1개, 잔여 2개)
시간 T2: 환불 A — TX 시작, Refund 저장 (잔여 2 → 0)
시간 T3: 환불 B — TX 시작, Refund 저장 (잔여 음수가 되어야 하는데 검증을 이미 통과해서 그냥 저장)
```

**잔여 수량 음수**가 됨. 누적 환불 합이 결제 금액 초과 가능. 정합성 깨짐.

`idempotency.md §4` 등급표:
- **S+** (DB 유니크 + 사전조회 이중) — race-safe
- **A** (트랜잭션 + 비관적 락 + 상태 검증) — race-safe
- **B** (상태 기반 단순) — 🟡 *동시 진입 시 둘 다 통과 가능*

AI 초안은 B. race-critical 영역이라 S 또는 A 필수.

## 2. 문제 해결 방법 (How)

1. **사전검증은 그대로 유지** — *잘못된 요청을 락 비용 없이 빠르게 거절* (fast-fail). UX 응답성 ↑.
2. **DB 트랜잭션 안에 *비관적 락 + 재검증* 추가** — `RefundTxOp.executeRefund`:
   ```
   SELECT ... FOR UPDATE on payments WHERE id = :paymentId   ← 락 획득
   sumRefundedQuantity 다시 호출 (락 안에서)               ← 재검증
   요청 수량 + 누적 환불 수량이 OrderItem.quantity 초과면 RF001 throw
   Refund 저장 (status = DB_COMMITTED)
   재고 복구 (productId 정렬 후 restoreStockWithLock per item)
   포인트 복구 (PointService.restorePoints)
   commit
   ```
3. **PortOne 취소 호출은 TX 종료 후** — consistency.md §6.
4. **PG=0이면 PortOne 호출 skip** + `markPgCancelled` 별도 단일 UPDATE TX.
5. **PG 호출 실패 시 DB 유지 + `log.error [REFUND_PG_CANCEL_FAILED]` 마커**:
   - 환불은 *사용자 관점에서 이미 처리됨* (재고·포인트·Refund 모두 commit)
   - PG 실패는 *운영 보강* (별도 reconciliation)
   - 응답은 **202 Accepted** (AI-ADR-011)

## 3. 방식 (Why this way)

### 사전 + 사후 이중 검증 = S+ 패턴
- 사전 검증 단독: race window 존재 → race-critical에 부적합
- 사후 검증 단독: 잘못된 요청도 *락 획득 비용 발생* → 응답성 ↓
- 둘 다: *fast-fail UX* + *race-safe* 둘 다 확보. 같은 함수(`sumRefundedQuantity`) 두 번 호출 비용은 *환불 빈도가 낮아 부담 X*.

### 비관적 락 위치 — Payment 행
- 같은 paymentId에 대한 환불은 *반드시 한 번에 하나*만 진행하면 됨
- Refund 행이 아니라 *Payment 행*에 락 — 환불은 *결제 1건당 누적 추적*이라 Payment가 자연스러운 동시성 경계
- consistency.md §5 락 순서: `Order → Payment → Point → Inventory` — Payment 락이 두 번째이므로 다른 흐름과 cycle 안 생김

### PG 호출 실패 시 DB 롤백 안 함
- AI 초안: PG 실패 시 DB 롤백
- 문제: 사용자는 *환불 진행 중*이라고 봤는데 갑자기 *환불 취소*된 경험 → UX 망가짐
- 환불은 *DB 처리는 일단 완료, PG 보강 별도*로 분리하는 게 더 자연스러움
- 응답을 200 (성공) / 202 (DB는 완료, PG 진행 중)으로 *상태 코드로 명확 구분*

### markPgCancelled 위치 — 별도 단일 UPDATE TX
- 메인 환불 TX는 PG 호출 *전*에 이미 commit (consistency.md §6)
- PG 호출 *후*에 Refund.status를 `PG_CANCELLED`로 전이하는 건 별도 TX
- 가벼운 단일 UPDATE라 락 hold time 0에 가까움
- self-injection 회피 위해 `RefundTxOp` 별도 컴포넌트로 분리 (`PaymentCompensationTxOp` 패턴 재사용)

## 4. 결과 (Outcome)

- 코드 반영:
  - `RefundProcessService.process` — 3단계 흐름 (선검증 → DB TX → TX 밖 PG 호출)
  - `RefundTxOp.executeRefund` — `@Transactional` + 비관적 락 + 재검증 + 저장 + 재고/포인트 복구
  - `RefundTxOp.markPgCancelled` — 별도 단일 UPDATE TX
  - `RefundJpaRepository.sumRefundedQuantity(paymentId, orderItemId)` — SUM 쿼리
- 통합 테스트:
  - **동시 환불 race 테스트** — 같은 paymentId에 두 환불 동시 진입 시 한 건만 성공, 다른 건 RF001 throw 검증 (멀티 스레드)
  - 단일 TX 보장 — Refund 저장·재고·포인트 중 하나 실패 시 전체 롤백
  - PG 호출 실패 시 DB 유지 + log.error 마커 검증
- 멱등성 등급: **B → S+** 전이
- 응답: PG 성공 200 / PG 실패 202 / RF001 409 / RF002 409 / RF003 403

## 5. 좋아진 점 (What got better)

- **`idempotency.md` 등급표가 *실제 코드 결정의 기준*으로 자리.** 막연히 "동시성 처리해야지" 가 아니라 "이 진입점은 race-critical → S 또는 A 필수" 라는 *명확한 등급 매핑*. 등급표를 보면 *내가 어디까지 했어야 하는지*가 즉시 드러남.
- **사전 + 사후 이중 검증이 *S+ 패턴*임을 명문화.** 결제 확정에서도 같은 패턴 — `payment.isCompleted()` 사전 가드 + DB UNIQUE(`payment.status` 전이). 환불에서 발견한 패턴이 *결제 확정의 멱등성 설명*에도 역적용 — 같은 *이중 보장* 사고가 두 흐름 다 깔끔하게 정리.
- **"PG 실패 시 DB 롤백" 본능을 의심.** *사용자 관점에서의 완료*와 *외부 시스템에서의 완료*가 다를 수 있고, **HTTP 상태 코드로 그 차이를 표현**할 수 있음. 202 Accepted의 의미("요청 수락됨, 처리 진행 중")가 *DB는 commit, PG는 보강 대기*와 정확히 일치 — 의미 보존.
- **AI는 동시성을 *놓치기 쉬움*이라는 인식.** AI 초안에서 *행복 경로*는 보통 정확. 단 *동시성·race·idempotency* 같은 횡단 관심사는 *명시적으로 프롬프트에 박아야* 검토함. 이후 결제·환불·웹훅 설계 시 "동시 진입·중복 수신·재시도 시 어떻게 되나?"를 매번 *명시 질문*으로 변환.
- **`RefundTxOp` 별도 컴포넌트 패턴이 트랜잭션 경계 분리의 *표준*으로 정착.** `PaymentCompensationTxOp`에서 시작한 패턴이 `RefundTxOp`에서 재사용 — 새 흐름이 *메인 TX + 별도 TX* 조합이면 `XxxTxOp` 컴포넌트 신설이 자연스러운 첫 선택.
