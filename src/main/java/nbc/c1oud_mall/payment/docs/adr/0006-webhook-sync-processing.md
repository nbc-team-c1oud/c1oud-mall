# ADR-0006: 웹훅 결제 확정 처리 방식 — 동기 vs Inbox 패턴

- 상태: Accepted
- 일자: 2026-06-03
- 범위: 결제 BC (Story 3-2)

## Context

Story 3-2는 HMAC 검증을 통과한 PortOne 웹훅 본문에서 `portonePaymentId`를 추출해
결제 확정 도메인 서비스(`PaymentConfirmationService`)를 호출하는 핸들러를 구현한다.

웹훅 처리 방식으로 두 가지 선택지가 있다.

1. **동기 처리**: HTTP 요청 스레드에서 즉시 서비스 호출 후 응답
2. **Inbox 패턴**: 웹훅 이벤트를 DB에 저장(Inbox table)하고 별도 스케줄러/비동기로 처리

## Decision

**동기 처리**를 채택한다.

- PortOne은 5xx 응답 시 자동 재시도(Standard Webhooks 사양)
- `PaymentConfirmationService`는 이미 멱등성을 보장(`isCompleted()` 가드 + DB UNIQUE)
- Story 3-3에서 `WebhookEvent` Aggregate + DB UNIQUE로 webhook-id 기반 멱등성 추가 예정
- 1차 구현에서 운영 복잡도를 최소화하는 것이 우선 — Inbox 패턴은 스케줄러·배치·모니터링이 함께 필요

처리 흐름:
```
PortOne 재시도(5xx) ←→ [Filter: HMAC 검증] → [Controller: portonePaymentId 추출]
                                                → [Service: PortOne 재조회 + 결제 확정]
```

## Alternatives

**Inbox 패턴**
- 웹훅 수신 즉시 `webhook_inbox` 테이블에 저장 + 200 OK 반환
- 별도 스케줄러가 inbox를 순차 처리
- 장점: 수신 즉시 응답 보장, 처리 실패 시 재시도 분리
- 단점: inbox 테이블 + 스케줄러 + 처리 상태 관리 추가 복잡도
- Story 3-3 WebhookEvent 이후 동시 도달 문제가 실제로 발생하면 재검토

**비동기 `@Async` + 즉시 200 반환**
- 동기 처리보다 응답이 빠르지만 처리 실패 시 PortOne 재시도 신호가 없음
- 내부 retry 메커니즘 별도 필요 → 도입 비용이 Inbox와 유사하면서 visibility가 낮아 선택하지 않음

## Consequences

### 장점
- 구현 단순: 컨트롤러 + 서비스 호출만
- 5xx 응답 → PortOne 자동 재시도로 신뢰성 확보
- `PaymentConfirmationService` 재사용 — Confirm API와 완전히 동일한 로직

### 단점
- 처리 시간이 길면(PortOne 재조회 지연 등) PortOne 웹훅 타임아웃 가능
- Confirm API + Webhook 동시 도달 시 DB UNIQUE로만 방어 (Story 3-3에서 WebhookEvent로 보완)

### 운영 진입 전 필수
- Story 3-3: `WebhookEvent` Aggregate + DB UNIQUE(webhook-id) 중복 수신 방지
- PortOne 재시도 알림 모니터링 (연속 5xx → 운영 이슈)
- 처리 지연 시 Inbox 패턴 전환 검토

## References

- workflows/product.md — [Story 3-2] 웹훅 핸들러 동기 처리 결정
- .claude/rules/consistency.md §6 — Webhook + Confirm 양방향 동기화 (단일 도메인 서비스)
- .claude/rules/idempotency.md §2 — Webhook 결제 통지 멱등성 카탈로그
- ADR-0005 — 웹훅 서명 검증 위치 및 raw body 보존 전략
