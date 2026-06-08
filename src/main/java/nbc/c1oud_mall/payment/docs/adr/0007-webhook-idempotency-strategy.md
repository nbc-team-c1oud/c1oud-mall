# ADR-0007: 웹훅 멱등성 보장 전략 선택 (A/B/C 비교)

- 상태: Accepted
- 일자: 2026-06-04
- 범위: 결제 BC (Story 3-3)

## Context

Story 3-2에서 구현한 웹훅 핸들러(`PaymentConfirmationService.handleWebhook`)는
`isCompleted()` 가드로 순차 중복을 처리하지만, 동시 수신 시 Race Condition이 남아있다.

구체적으로 방어해야 할 시나리오:
1. **동일 웹훅 중복 수신** — PortOne이 5xx 재시도로 같은 웹훅을 2회 이상 전송
2. **웹훅 동시 수신** — 두 요청이 거의 동시에 도달해 `isCompleted()` 가드를 둘 다 통과
3. **역순 수신** — 결제 확정 API 먼저 처리된 뒤 웹훅 도착 (순차 처리로 `isCompleted()` 가드 작동)

## 선택지

### A. INSERT-first with UNIQUE 제약 ✅ **채택**
- `WebhookEvent` Aggregate + `(portone_payment_id, event_type)` UNIQUE 제약
- 웹훅 처리 전 REQUIRES_NEW 트랜잭션으로 WebhookEvent INSERT 시도
- UNIQUE 위반 시 즉시 200 (silent idempotent response)
- 장점: DB 레벨 원자적 경쟁 해소, 감사 로그 부수 효과, 인프라 의존성 없음
- 단점: REQUIRES_NEW 커밋 후 confirm() 실패 시 WebhookEvent가 남아 후속 PortOne 재시도 차단 가능 (1차 한정 허용)

### B. Redisson 분산 락
- `portonePaymentId` 키로 Redis 락 획득 후 처리
- 장점: 다중 인스턴스 환경에서도 유효
- 단점: Redis 의존성 추가, 락 타임아웃 정책 설계 필요, 현재 단일 인스턴스라 과잉

### C. DB 비관적 락
- Payment 조회 시 `SELECT ... FOR UPDATE`로 행 잠금
- 장점: DB 레벨 동시성 제어, 추가 테이블 불필요
- 단점: 웹훅 처리 시간 동안 Lock hold time 증가, `isCompleted()` 체크 후 PortOne 재조회까지 락 유지로 PG 지연이 DB 락 경쟁으로 전이

## Decision

**A안 (INSERT-first + UNIQUE)** 을 채택한다.

- 단일 인스턴스라 Redis 불필요 (B안 기각)
- PortOne 재조회가 트랜잭션 밖에 없어 락 hold time이 길어짐 (C안 기각)
- WebhookEvent는 감사 로그로도 사용 가능 — 장기적으로 운영 visibility 확보

처리 흐름:
```
웹훅 수신
  → WebhookEventRegistrar.tryRegister(REQUIRES_NEW)
      ├─ INSERT 성공 → 외부 TX 재개 → confirm() 정상 처리
      └─ UNIQUE 위반 → false → PaymentConfirmationResult.alreadyCompleted
```

## Consequences

### 장점
- DB UNIQUE 제약으로 동시 수신 Race Condition 원자적 해소
- WebhookEvent 테이블이 처리 감사 로그 역할
- Inbox 패턴보다 단순 (스케줄러·배치 불필요)

### 단점 및 운영 진입 전 주의사항

**알려진 한계 (1차):**
- `tryRegister()` REQUIRES_NEW 커밋 후 `confirm()` 트랜잭션 실패 시:
  - WebhookEvent는 RECEIVED 상태로 DB에 남음
  - 후속 PortOne 재시도 → `tryRegister()` false → 200 반환 → PortOne 재시도 중단
  - 결제는 여전히 PENDING — 수동 모니터링 필요
- 운영 진입 전 WebhookEvent 보존 정책 결정 필요 (무한 증가 방지, 예: 90일 아카이브)

**미구현 사항 (추후 보완):**
- WebhookEvent 처리 완료 후 `processStatus = COMPLETED/SKIPPED` 업데이트 (현재 RECEIVED 유지)
- 처리 실패 시 WebhookEvent 삭제 or FAILED 마킹 → PortOne 재시도 허용

## References

- workflows/product.md — [Story 3-3] 웹훅 멱등성 보장
- .claude/rules/idempotency.md §2 — Webhook 결제 통지 멱등 카탈로그
- .claude/rules/consitency.md §5 — 동시성/락 전략
- ADR-0006 — 웹훅 동기 처리 선택 (Inbox 패턴 미사용)
