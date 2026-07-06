# [0.0.3v · Issue 07] 동시성 락 3전략 구현 + 비교 리포트 (이력서 핵심)

> **역할**: 캠프 요구사항의 핵심 — 낙관/비관/Redis Lock 3전략을 실제 구현하고 비교 리포트로 문서화. **이력서 파괴력 최상**.
> **Week**: 2 · **Day**: 9~10
> **상태**: pending
> **Tier**: `pes`
> **캠프 요구사항 매핑**: 필수 — 동시성 제어 (락 3전략 비교 분석 · ExecutorService+CyclicBarrier 실패 테스트 · README 정리)

---

## 배경

캠프 요구사항 명시:
- 여러 Thread가 동시에 같은 메소드 호출하는 시나리오
- **실패하는 테스트 먼저 작성**
- 낙관적 락 (JPA `@Version` · `OptimisticLockException` 재시도)
- 비관적 락 (JPA `@Lock(PESSIMISTIC_WRITE)`)
- Redis Lock (Lettuce 사용 · Redisson X)
- 3가지 비교 분석 문서화 (관리 주체 · 보호 범위 · 성능 특성 · 적용 시나리오)
- 최종 선택 근거 README

**이 이슈가 이력서에서 가장 강한 어필 포인트**. 리뷰어가 GitHub 열어봤을 때 락 비교 리포트가 있으면 즉시 "동시성 안다" 인지.

## 핵심 스코프

### Day 9 — 실패 테스트 + 낙관 락
- `TimeSaleEventPurchaseConcurrencyTest`:
  - `ExecutorService(100)` + `CyclicBarrier(100)`로 동시 100 스레드 발사
  - 재고 10개인 이벤트에 100명 동시 구매 요청
  - **실패해야 정상**: 락 없이 → 재고 음수 발생 (assertion 실패 확인)
- 낙관 락 구현:
  - `TimeSaleEvent.@Version Long version`
  - `OptimisticLockException` 발생 시 재시도 (Spring `@Retryable` or 자체 지수 backoff 3회)
  - 100 스레드 동시 → 재시도 폭발 관찰 · 최종 재고 10 정확

### Day 10 — 비관 락 + Lettuce Redis Lock + 비교
- 비관 락 구현:
  - `TimeSaleEventRepository.findByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`)
  - 락 대기 시간 관찰 · P95 응답 시간
- Lettuce Redis Lock 구현:
  - `LockRedisRepository` · `LockService` 분리 (요구사항 명시)
  - `SETNX` + TTL (5초) + UUID + Lua 원자 해제 (요구사항 언급)
  - Fail Fast (즉시 실패 반환) · 짧은 재시도 옵션
- 3전략 벤치마크:
  - 동일 시나리오 100 스레드 · 재고 10
  - 지표: 총 소요 시간 · 성공/실패 비율 · P50/P95/P99
  - 리포트: `docs/adr/002-concurrency-lock-strategy.md` (or 프로젝트 컨벤션에 맞게)

## 비교 분석 표 (README·ADR용)

| 축 | 낙관 락 | 비관 락 | Redis Lock |
|---|---|---|---|
| 관리 주체 | JPA `@Version` · 앱 | DB Row Lock | Redis 외부 시스템 |
| 보호 범위 | UPDATE 시점 | DB 트랜잭션 내 | **비즈 로직 전체** |
| 서버 수 | 1대 or 여러 대 (충돌 적음) | 1대 (충돌 잦음) | **여러 대 (Scale-out)** |
| 성능 | 충돌 적을 때 최고 · 많을 땐 재시도 폭발 | 대기 시간 큼 | 네트워크 왕복 오버헤드 |
| 데드락 | 없음 | 가능 | 없음 (TTL 자동 해제) |
| 서버 장애 시 | 무관 | DB 트랜잭션 롤백 | TTL 자동 해제 |

## 최종 선택 (초안 · 실측 후 확정)

- **타임세일**: Redis Lock (Scale-out 대비 · 이벤트 전체 로직 보호 · 이슈 08 쿠폰과 락 재사용)
- **대안**: 트래픽 낮은 케이스는 비관 락도 OK

## 산출물

- BE-07-1: `TimeSaleEventPurchaseConcurrencyTest` (100 스레드 실패 테스트)
- BE-07-2: `TimeSaleEvent.@Version` + 낙관 락 재시도 (지수 backoff 3회)
- BE-07-3: `TimeSaleEventRepository.findByIdForUpdate` + 비관 락
- BE-07-4: `LockRedisRepository` (Lettuce SETNX + TTL + UUID + Lua)
- BE-07-5: `LockService` (다른 로직에서 의존 · 이슈 08 재사용)
- BE-07-6: 3전략 벤치마크 통합 테스트 (동시 100 · 재고 10 · 지표 로깅)
- BE-07-7: 비교 리포트 (`docs/adr/002-concurrency-lock-strategy.md` · README §Concurrency)
- BE-07-8: `ErrorCode.LCK001~003` (LOCK_ACQUIRE_FAILED · LOCK_TIMEOUT · LOCK_ILLEGAL_RELEASE)

## 락 순서 갱신 (`.claude/rules/consitency.md`)

- `Product(재고) FOR UPDATE → TimeSaleEvent → Coupon → Point`
- Redis 이벤트 락 키: `event:{eventId}` · TTL 5초

## 관련 이슈 / 문서

- 선행: [06 타임세일 도메인](./issue-06-timesale-event-domain.md)
- 다음: [08 선착순 쿠폰](./issue-08-first-come-coupon-issuance.md) — 락 재사용
- 다음: [14 ADR·README](./issue-14-adr-readme-documentation.md) — 비교 리포트 통합
- 규범: `.claude/rules/consitency.md` §5 · `.claude/rules/idempotency.md`

## 상세 (착수 시 채움)

_pending_
