# AI-ADR-005: 웹훅 멱등성 — INSERT-first WebhookEvent (A안, vs Redisson/DB Lock)

- 상태: Accepted
- 일자: 2026-06-04
- 관련 Story: `workflows/product.md` Story 3-3 (웹훅 멱등성 보장)
- 관련 ADR: `payment/docs/adr/0007-webhook-idempotency-strategy.md`
- 관련 룰: `.claude/rules/idempotency.md §4` (S+ — DB 유니크 + 사전조회 이중)

---

## Context

Story 3-2의 웹훅 핸들러는 `payment.isCompleted()` 가드로 **순차 중복**을 처리한다. 그러나 다음 시나리오에서 race condition이 남는다.

1. **동일 웹훅 중복 수신** — PortOne이 5xx 재시도로 같은 웹훅을 2회 이상 전송
2. **웹훅 동시 수신** — 두 요청이 거의 동시에 도달 → `isCompleted()` 가드를 둘 다 통과 → 부수효과 2회 적용
3. **역순 수신** — 결제 확정 API 먼저 처리된 뒤 웹훅 도착 → 순차 처리라 `isCompleted()` 가드 OK
4. **확정 API와 웹훅 동시 수신** — 가장 위험: 같은 portonePaymentId에 두 진입점이 race

→ "결제는 정확히 1회 확정"을 race condition 하에서도 보장해야 함.

---

## 선택지

### A. INSERT-first + UNIQUE 제약 ✅ **채택**
- `WebhookEvent` Aggregate + `(portone_payment_id, event_type)` UNIQUE 제약
- 웹훅 처리 전 `REQUIRES_NEW` TX로 WebhookEvent INSERT 시도
- UNIQUE 위반 시 즉시 200 (silent idempotent response)
- 등급: **S+** (idempotency.md §4 — DB 유니크 + 사전조회 이중)

### B. Redisson 분산 락
- `portonePaymentId` 키로 Redis 락 획득 후 처리
- 다중 인스턴스에 유효, 단 Redis 의존성 추가

### C. DB 비관적 락
- Payment 조회 시 `SELECT ... FOR UPDATE`
- 추가 테이블 불필요, 단 lock hold time이 PortOne 재조회 latency만큼 길어짐

---

## Decisions

### A안 채택

- **단일 인스턴스 환경** → B 과잉
- **PortOne 재조회가 트랜잭션 밖에 있어야 함**(consistency.md §6) → C는 락 hold time이 외부 latency에 종속 → 처참
- A는 DB 레벨 원자적 경쟁 해소 + 감사 로그 부수 효과

### WebhookEvent Aggregate
- 필드: `id`, `portonePaymentId`, `eventType`, `signature`(보존용), `receivedAt`, `processedAt`, `processStatus`
- UNIQUE: `(portone_payment_id, event_type)`
- `eventType`: `"Transaction.Paid"` 등 PortOne 표준 값

### 처리 흐름
```
웹훅 수신
  → HMAC 서명 검증 (Story 3-1)
  → portonePaymentId 추출 (본문 비신뢰, ID만)
  → WebhookEventRegistrar.tryRegister(portonePaymentId, eventType) ← @Transactional(REQUIRES_NEW)
      ├─ INSERT 성공 → 외부 TX 재개 → PaymentConfirmationService.confirm() 정상 처리
      └─ UNIQUE 위반 → false → silent 200 OK (alreadyCompleted)
```

### 확정 API와의 양방향 동기화
- 두 진입점 모두 **같은 `PaymentConfirmationService.confirm`** 호출
- 확정 API는 이중 가드:
  1. WebhookEvent INSERT는 안 함 (확정 API 경로니까)
  2. 그 대신 `payment.isCompleted()` 가드 + DB UNIQUE 가드 (`payment.markCompleted()`)
- 웹훅 INSERT-first가 race 차단의 1차 방어선, Payment 상태 가드가 2차 방어선

---

## 프롬프트 내용 (AI 활용시)

> "결제 웹훅 멱등성 처리가 까다롭습니다.
>
> 현재 `isCompleted()` 가드만 있는데 순차 중복은 OK인데 다음이 다 막혀야 합니다:
> - 동시 웹훅 중복 수신 (PortOne 5xx 재시도)
> - 확정 API + 웹훅 동시 도착 (가장 무서움)
> - 확정 API → 웹훅 순차 / 웹훅 → 확정 API 순차
>
> 옵션:
> A) WebhookEvent 테이블 + UNIQUE 제약 + INSERT-first
> B) Redisson 분산 락
> C) Payment에 비관적 락 (SELECT FOR UPDATE)
>
> 환경: 단일 인스턴스, PortOne 재조회는 외부 호출이라 트랜잭션 밖에 둬야 합니다.
> 어떤 게 적절한가요?"

---

## AI 응답 요약

### 옵션 비교

| 항목 | A (INSERT-first) | B (Redisson) | C (DB Lock) |
|---|---|---|---|
| 인프라 의존성 | DB만 | Redis 추가 | DB만 |
| 단일 인스턴스 | OK | 과잉 | OK |
| 다중 인스턴스 | OK | OK | OK (Lock 경쟁 비용 ↑) |
| 락 hold time | 짧음 (INSERT 1회) | 처리 전체 | **처리 전체 + PortOne latency** |
| 감사 로그 | ✅ 부수 효과 | ❌ | ❌ |
| 인프라 비용 | 낮음 | 중간 | 낮음 |

### A안 권장 이유
- 단일 인스턴스라 B의 가치 없음
- C는 PortOne 재조회가 트랜잭션 밖에 있어야 한다는 consistency 룰과 충돌 (락 hold 길어짐)
- A는 DB 원자성으로 race 해결 + WebhookEvent 자체가 감사 로그

### 미세 디테일
- `tryRegister`는 `REQUIRES_NEW`여야 함. 그래야 INSERT가 외부 TX와 분리되어 즉시 commit/visible.
- `eventType`을 키에 포함시켜야 같은 결제의 다른 이벤트(Paid → PartialCancelled 등)를 따로 처리 가능.
- 처리 완료 후 `WebhookEvent.processStatus`를 `COMPLETED`로 업데이트 — 단 1차에서 미구현이어도 멱등성 자체는 보장됨.

### 알려진 한계
- `tryRegister()` 커밋 후 `confirm()` 트랜잭션 실패 시:
  - WebhookEvent는 `RECEIVED` 상태로 남음
  - 후속 PortOne 재시도 → `tryRegister()` false → 200 silent → PortOne 재시도 중단
  - 결제는 PENDING 잔존 → 수동 보강 필요
- → 1차 한계로 수용, 보강은 미래 ADR

---

## Consequences

### 장점
- DB UNIQUE로 동시 수신 race 원자적 해소 — S+ 등급
- WebhookEvent가 처리 감사 로그 역할
- Inbox 패턴보다 단순 (스케줄러·배치 불필요)
- 확정 API + 웹훅 동시 처리 시에도 한쪽만 INSERT 성공 → 정확히 1회 확정 보장

### 단점 / 알려진 한계
- WebhookEvent 무한 증가 — 보존 정책 필요 (90일 아카이브 등, 별도 작업)
- `tryRegister` 커밋 후 `confirm` 실패 시 PortOne 재시도 차단 — 결제 PENDING 잔존 → 수동 보강
- 1차에서는 `processStatus` 업데이트 미구현 (RECEIVED 유지) — 멱등성엔 영향 없음

### 운영 진입 전 보강
- WebhookEvent 보존·아카이브 잡
- `confirm` 실패 시 `WebhookEvent.processStatus = FAILED` + PortOne 재시도 허용 옵션
- 결제 PENDING + WebhookEvent RECEIVED 잔존 모니터링 알람

---

## 내가 수정한 부분

- AI는 처음에 `(portone_payment_id)` UNIQUE만 제시. **eventType 포함**으로 변경. 같은 결제의 `Transaction.Paid`와 `Transaction.PartialCancelled`(미래 환불 흐름)가 같은 키로 충돌하면 안 됨.
- AI는 `processStatus` 업데이트(INSERT 후 `RECEIVED` → 처리 끝나면 `COMPLETED`)를 필수로 제시. **1차에선 INSERT까지만**으로 축소 — 멱등성 자체는 INSERT 시점에 보장됨. `processStatus` 업데이트는 모니터링·운영 단계에서 추가하는 게 ROI ↑.
- AI는 `tryRegister` 실패 시 응답을 `409 Conflict`로 제시. **`200 OK` + silent**로 변경 — 외부 멱등 응답 정책(idempotency.md §5 "Webhook = Silent OK")과 일치. PortOne이 5xx/409 받으면 재시도하는데, 우리는 처리 완료 의미니까 200으로 끝내야 함.
- **확정 API 쪽에 WebhookEvent INSERT 추가하지 않음**으로 명시 결정. 확정 API는 별도 멱등 가드(payment.isCompleted) + DB UNIQUE(payment.status 전이)로 충분. 둘 다 WebhookEvent에 INSERT하면 진입점별 의미가 모호해짐.
- **PortOne 재조회를 `tryRegister` *전*에** 두는 옵션도 검토. 그러나 (a) PortOne 호출 비용이 멱등 재호출에도 듬, (b) `tryRegister` 후 PortOne 호출이 같은 흐름이라 추적 쉬움 → `tryRegister` *후*로 결정.

---

## 최종 반영 여부

- ✅ 코드 반영:
  - `WebhookEvent` Aggregate + `payment.infrastructure.WebhookEventJpaRepository`
  - UNIQUE `(portone_payment_id, event_type)`
  - `WebhookEventRegistrar.tryRegister` — `@Transactional(REQUIRES_NEW)`
  - 웹훅 핸들러: HMAC 검증 → portonePaymentId 추출 → tryRegister → confirm 호출
- ✅ 일반 ADR: `payment/docs/adr/0007-webhook-idempotency-strategy.md`
- ✅ 통합 테스트: 멀티스레드 동시 수신 시 결제 정확히 1회 확정 검증
- 미래 트리거:
  - WebhookEvent processStatus 갱신 + FAILED 마킹
  - WebhookEvent 90일 아카이브 잡
  - 결제 PENDING + WebhookEvent RECEIVED 잔존 모니터링
