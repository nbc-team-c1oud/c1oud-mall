# [Product 1] 타임세일 이벤트 · 동시성 락 3전략 · 선착순 쿠폰

## Product Vision
> 관리자가 발동하는 타임세일·선착순 쿠폰 이벤트가 순간 요청 폭주 하에서도 재고·발급 수량 정합성이 절대 깨지지 않도록, 낙관/비관/Redis Lock **3전략을 실제로 구현·비교·문서화**하고 이력서 관점에서 백엔드 동시성 역량을 증명한다.

## 배경 및 문제

- 현재 상황 (As-Is)
  - `nbc.c1oud_mall.product.*`에 재고 필드는 존재하나 동시 요청 시 race condition 방어 없음 (M1 스코프)
  - 재고 감소는 서비스 계층 단순 `product.decreaseStock(qty)` → 트랜잭션 격리만 의존
  - 쿠폰·이벤트 도메인 미존재
  - `.claude/rules/consitency.md` §5 락 전략 표에 "재고 차감/복구 = 비관적 락" 규범 존재하나 코드 미반영
- 발생하는 문제
  - 5만+ 상품·수백 vUser 동시 접근 시 재고 음수 발생 (미검증 · 이슈 07 실측 예정)
  - 캠프 요구사항 필수: 낙관/비관/Redis Lock 3전략 비교 분석 문서화 · ExecutorService+CyclicBarrier 실패 테스트 · Redisson 금지 · Lettuce 사용
  - 관리자 백오피스에서 타임세일·쿠폰 이벤트를 발동할 수단 부재 (이슈 06·08)
- 왜 지금 해결해야 하는가
  - 백오피스 정체성의 심장부 — "관리자가 발동한 이벤트가 정합성 무너뜨리지 않는다"가 이 프로젝트의 핵심 메시지
  - 이력서 파괴력 최상 — 리뷰어가 GitHub 열어봤을 때 락 3전략 비교 리포트 = 즉시 "동시성 안다" 인지
  - 캠프 필수 요구사항이라 우회 불가
  - 5만+ 데이터 시딩(이슈 02) 이후에만 실측 유의미 → Week 2 진입 조건

## 목표 (To-Be)

- 신규 컨텍스트 `nbc.c1oud_mall.event.*` (TimeSaleEvent 도메인) + `nbc.c1oud_mall.coupon.*` (Coupon · UserCoupon)
- 신규 인프라 `nbc.c1oud_mall.common.lock.*` (LockService · LockRedisRepository · Lettuce 기반 SET NX + UUID + Lua)
- 상태기계: `TimeSaleEvent.status` (SCHEDULED → OPEN → SOLD_OUT/CLOSED) · `@Scheduled` 스케줄러가 자동 전이
- 락 전략 3구현 (낙관 · 비관 · Lettuce Redis Lock) + 3-way 비교 벤치마크 리포트 (ADR 013 발행)
- ExecutorService(100) + CyclicBarrier(100) 실패 테스트 → 락 없이 재고 음수 재현 → 각 전략으로 방어
- 락 순서 규범 갱신 (`.claude/rules/consitency.md` §5): `Product(재고) → TimeSaleEvent → Coupon → Point`
- Idempotency 카탈로그 갱신 (`.claude/rules/idempotency.md` §2): 쿠폰 발급 = 비즈 식별자 `(user_id, coupon_id)` · S+ 등급

## 설계 결정 (Design Decisions)
> 큰 갈림길의 결정. 거부된 옵션도 합리적 근거 명시.

- **락 3전략을 모두 구현하고 실측 비교** — 하나만 구현하면 캠프 요구사항 미충족 + 이력서 파괴력 반감. 세 전략을 실제 코드로 두고 프로파일/설정으로 스위칭.
- **최종 선택 = Lettuce Redis Lock** (Scale-out 대비 · 이벤트 전체 로직 보호). 다만 벤치마크 결과에 따라 트래픽 낮은 케이스는 비관 락도 병존 · ADR 013에서 확정.
- **Redisson 금지 · Lettuce 사용** — 캠프 요구사항 명시. Redisson은 편의 API 크지만 락 원리 학습 관점에서 SETNX+UUID+Lua 직접 구현이 이력서 파괴력 큼.
- **`LockService` 인프라 분리** — 다른 비즈니스 로직(이슈 08 쿠폰)에서 재사용 가능한 표준 인터페이스 (`lock(key, ttl, action)`)
- **ExecutorService + CyclicBarrier 실패 테스트 먼저 작성** — 캠프 명시 요구사항. "실패해야 정상" 확인 후 락으로 해결하는 서사가 이력서·PR 리뷰 관점에서 강함
- **Redis 이벤트 락 = SETNX + TTL + UUID + Lua 원자 해제** — TTL로 서버 장애 시 자동 해제, UUID+Lua로 본인이 잡은 락만 해제 (Redlock 등 분산 복제는 도입 안 함 · 단일 Redis 전제)
- **락 순서 규범 (Product → TimeSaleEvent → Coupon → Point)** — 데드락 방지. 위반 코드는 PR 차단 (consitency.md §5)

## 대안 검토 (Alternatives Considered)

### 락 전략 (핵심 갈림길)

**Option A — 낙관 락 (JPA @Version)**
- 장점: DB 락 없음 · 서버 1대·충돌 적음 시 최고 성능 · 데드락 없음
- 비용: 충돌 잦으면 재시도 폭발 → CPU 증가 · 응답 시간 증가
- 실측 대상 · 부분 채택 (읽기 많고 충돌 적은 조회 API 등)

**Option B — 비관 락 (`@Lock(PESSIMISTIC_WRITE)`)**
- 장점: 충돌 자체 방지 · DB 레벨 원자성 · 단일 인스턴스에서 확실
- 비용: 락 대기로 인한 처리량 감소 · 데드락 가능성 · Scale-out 시 인스턴스 간 락 공유 불가 (같은 DB Row로는 가능하나 병목)
- 실측 대상 · 부분 채택 (트래픽 낮고 정확성 우선 API)

**Option C (핵심 채택) — Lettuce Redis Lock (SETNX + TTL + UUID + Lua)**
- 장점: 비즈니스 로직 전체 보호 (DB 트랜잭션 밖 로직 포함) · Scale-out 대비 · TTL 자동 해제
- 비용: Redis 인프라 의존 · 네트워크 왕복 오버헤드 · 락 획득 실패 시 정책 결정 필요(Fail Fast vs 재시도)
- 이력서 파괴력 관점에서 SETNX+UUID+Lua 직접 구현이 강함

**Option D — Redisson**
- 거부 이유: 캠프 요구사항 명시 금지 (Lettuce 사용 강제). Redisson은 편의성 크지만 락 원리 학습이 얕아짐.

### 락 획득 실패 시 정책

**Option A (선택 · 이벤트) — Fail Fast (즉시 실패 반환)**
- 비용: 사용자는 재시도해야 함
- 보상: 서버 리소스 보호 · 대기 큐 없음 → 명확한 UX
- 타임세일·쿠폰 발급은 "선착순"이라 대기가 무의미

**Option B — Retry with backoff**
- 부분 채택: 낙관 락 재시도에만 (지수 backoff 3회)
- 이벤트 락에는 채택 안 함 (선착순 특성상 즉시 실패가 맞음)

**Option C — 블로킹 대기**
- 거부: 락 대기 큐 관리 부담 · 데드락 리스크 · 사용자 UX 지연

### 재고 감소 원자성

**Option A — 도메인 메서드 `product.decreaseStock(qty)` + 트랜잭션 격리**
- 거부: race 방어 없음 · 이번 이슈의 대상

**Option B (선택) — 락 획득 후 도메인 메서드 호출**
- 락 안에서 조회 → 검증 → 감소 → 저장을 원자적 수행

**Option C — 원자적 UPDATE (`UPDATE product SET stock = stock - ? WHERE id = ? AND stock >= ?`)**
- 부분 채택: 단순 재고 감소에는 좋으나 이벤트 스케줄링·쿠폰 카운트 등 부수 로직 있으면 락이 나음
- 비교 리포트에 포함

## 전체 아키텍처 (High-Level Architecture)

### 컴포넌트 배치
```
presentation ──▶ application ──▶ domain ◀── infrastructure
  Controller       Service          Entity      JpaRepository
  Admin*·Event*·   EventOpen        TimeSale    LockRedisRepo
  Coupon*          Scheduler        Event       (Lettuce)
                   LockService      Coupon      RedisTemplate
                                    UserCoupon
                                    (@Version · @Lock)
```

### 핵심 플로우

**1. 타임세일 이벤트 등록·오픈 (Epic 1)**
```
Admin → AdminTimeSaleController.create
      → TimeSaleEventService.create (검증: startedAt < endedAt · totalStock > 0)
      → repository.save (status=SCHEDULED)

TimeSaleEventScheduler @Scheduled(fixedDelay=60초)
      → findByStatusAndStartedAtBefore(SCHEDULED, now)
      → 각 이벤트 markOpen (status → OPEN)
      → 각 이벤트 findByStatusAndEndedAtBefore(OPEN, now) → markClosed
```

**2. 타임세일 구매 (Epic 2 · 락 3전략)**
```
User → TimeSaleController.purchase(eventId)
     → TimeSalePurchaseService.purchase(userId, eventId)
        │
        ├─ [Strategy: OPTIMISTIC]  event = repo.findById · @Version 체크 · decreaseStock · save (OptimisticLockException 시 3회 재시도)
        ├─ [Strategy: PESSIMISTIC] event = repo.findByIdForUpdate (SELECT FOR UPDATE) · decreaseStock · save
        └─ [Strategy: REDIS_LOCK]  lockService.lock("event:" + eventId, TTL=5s, () -> {
                                       event = repo.findById · decreaseStock · save · Order 생성
                                   })
```

**3. 선착순 쿠폰 발급 (Epic 3 · LockService 재사용)**
```
User → CouponController.issue(couponId)
     → CouponIssueService.issue(userId, couponId)
        → 사전조회 existsByUserIdAndCouponId (S+ 1단계)
        → lockService.lock("coupon:" + couponId, TTL=5s, () -> {
              coupon = repo.findById · checkQuantity · save (issuedQuantity++)
              userCoupon = UserCoupon.of(userId, couponId) · save (DB UNIQUE 이중 방어)
          })
```

**4. 실패 테스트 흐름 (Epic 2 Story 2-1)**
```
@Test
void 재고10_동시100요청_락없으면_음수발생() {
    ExecutorService pool = Executors.newFixedThreadPool(100);
    CyclicBarrier barrier = new CyclicBarrier(100);
    for (int i = 0; i < 100; i++) {
        pool.submit(() -> {
            barrier.await();
            purchaseService.purchaseWithoutLock(eventId);
        });
    }
    assertThat(event.remainingStock).isLessThan(0);  // 실패해야 정상
}
```

### Out-of-Process 의존
- **Redis (Lettuce)**: 이벤트 락 · 쿠폰 락 저장 (SET NX EX + UUID + Lua)
- **RDS (MySQL) / H2 (dev)**: TimeSaleEvent · Coupon · UserCoupon 영속화 + SELECT FOR UPDATE 지원
- **Micrometer**: `lock.acquire.total{result}` · `lock.duration_seconds` histogram · `timesale.purchase.total{strategy,result}` (이슈 13 대시보드 연결)

## 실패 모드 / 운영 관측 (Failure Modes & Observability)

### 실패 시나리오와 응답
| 시나리오 | ErrorCode | HTTP | 클라이언트 권장 동작 |
| --- | --- | --- | --- |
| 이벤트 존재하지 않음 | `EVT001` | 404 | 목록 재조회 |
| 이벤트 OPEN 아님 (SCHEDULED/CLOSED/SOLD_OUT) | `EVT002` | 409 | 이벤트 목록 재조회 |
| 재고 소진 (SOLD_OUT) | `EVT003` | 409 | 이벤트 목록 재조회 (품절 표시) |
| 락 획득 실패 (Fail Fast) | `LCK001` | 429 | 잠시 후 재시도 (Retry-After 헤더) |
| 락 TTL 초과 | `LCK002` | 500 | 자동 해제 · 재시도 안내 |
| 낙관 락 재시도 3회 실패 | `LCK003` | 429 | 잠시 후 재시도 |
| 쿠폰 소진 | `CPN003` | 409 | 이벤트 종료 (다음 쿠폰 안내) |
| 쿠폰 중복 발급 (S+ 사전조회) | `CPN004` | 409 | 마이 쿠폰 페이지 이동 |
| 쿠폰 중복 발급 (S+ DB UNIQUE 이중) | `CPN004` | 409 | 마이 쿠폰 페이지 이동 |

### 로깅 정책
- **항상 기록**: `requestId` (MDC), `userId`, `eventId/couponId`, `strategy` (OPTIMISTIC/PESSIMISTIC/REDIS_LOCK), `lockAcquiredAt`, `lockReleasedAt`, `retryCount`, `remainingStock` (전이 전/후)
- **debug**: 락 획득 실패 원인 (`LockAcquireFailedException.cause`) · 낙관 락 재시도 상세
- **절대 금지**: 사용자 실명·이메일 (userId만) · 락 UUID 원문 (마스킹)

### 관측 지표 (Micrometer · 이슈 13 대시보드 연결)
- `timesale.purchase.total{strategy=optimistic|pessimistic|redis, result=success|fail|stock_exhausted}` — counter
- `timesale.purchase.duration_seconds{strategy}` — histogram (P50/P95/P99 비교)
- `coupon.issue.total{result=success|sold_out|duplicate}` — counter
- `lock.acquire.total{key_type=event|coupon, result=success|fail|timeout}` — counter
- `lock.hold.duration_seconds{key_type}` — histogram (락 hold time 관찰 · consitency.md §5.2 "hold time 최소화" 준수 검증)
- `event.status.gauge{status=SCHEDULED|OPEN|SOLD_OUT|CLOSED}` — 상태별 이벤트 수 (관리자 대시보드)

## 롤아웃 / 마이그레이션 (Rollout)

### 전제
- Week 2 Day 8 진입 시점에 이슈 01(Admin 컨텍스트) · 이슈 02(5만+ 시딩) 완료 상태
- Redis 단일 인스턴스 · docker-compose로 로컬 부팅 (prod는 ElastiCache 확장 v0.0.4v+)
- 초기 트래픽 없음 (더미 시나리오만) · k6로 부하 발생

### Product 의존성
- 선행: Admin 컨텍스트(이슈 01) → 관리자 롤 · @PreAuthorize
- 선행: Datafaker 시딩(이슈 02) → 5만+ 상품·유저
- 후행: k6 부하테스트(이슈 11) → 3전략 벤치마크 결과
- 후행: 관리자 대시보드(이슈 13) → 이벤트 판매 지표 · 쿠폰 사용률

### Epic·Story 의존성 그래프
```
Epic 1 (TimeSale 도메인·스케줄러) ──► Epic 2 (락 3전략 실패 테스트→구현→비교)
                                          │
                                          └─► Epic 3 (선착순 쿠폰 · LockService 재사용)
```

### 환경별 설정 분기
| 항목 | dev (H2 + docker Redis) | prod (RDS MySQL + ElastiCache) |
| --- | --- | --- |
| DataSource | H2 MODE=MySQL | RDS endpoint |
| Redis | localhost:6379 (docker) | ElastiCache endpoint |
| 스케줄러 fixedDelay | 60초 | 60초 |
| 락 TTL | 5초 | 5초 |
| 락 전략 스위치 | `c1oudmall.lock.strategy=redis` (default) | 동일 · 벤치마크 시에만 수동 스위칭 |
| ExecutorService 풀 크기 | 100 (테스트) | N/A |

## 성공 지표 (KPI)
| 지표 | 목표 값 | 측정 방법 |
| --- | --- | --- |
| 재고 정합성 (음수 발생률) | **0건** | k6 부하 후 `SELECT * FROM timesale_event WHERE remaining_stock < 0` = 0 |
| 쿠폰 중복 발급률 | **0건** | `SELECT user_id, coupon_id, COUNT(*) FROM user_coupon GROUP BY 1,2 HAVING COUNT(*) > 1` = empty |
| 락 획득 실패율 (Fail Fast) | ≤ **5%** (100 vUser 기준) | Prometheus `lock_acquire_total{result=fail} / lock_acquire_total{total}` |
| 3전략 P95 응답 시간 | 낙관 < 비관 < Redis 가능 (실측 후 확정) | k6 리포트 P95 (이슈 11) |
| 이벤트 OPEN 전이 지연 | ≤ **60초** (스케줄러 주기) | 이벤트 `startedAt` vs 실제 status=OPEN 전이 시각 diff |
| 락 hold time P95 | ≤ **50ms** | Micrometer `lock.hold.duration_seconds{quantile=0.95}` |

## Scope

**In Scope**:
- TimeSaleEvent 도메인 (등록·수정·삭제·스케줄러·상태 전이 SCHEDULED→OPEN→SOLD_OUT/CLOSED)
- Coupon 정책 + UserCoupon 발급 이력 도메인
- 낙관 락(@Version + 재시도) · 비관 락(SELECT FOR UPDATE) · Lettuce Redis Lock 3구현
- LockService/LockRedisRepository 인프라 (재사용 가능한 락 표준)
- ExecutorService + CyclicBarrier 실패 테스트
- 3전략 벤치마크 리포트 (ADR 013 · README 락 섹션)
- 락 순서 규범 갱신 (`.claude/rules/consitency.md` §5)
- Idempotency 카탈로그 갱신 (`.claude/rules/idempotency.md` §2)
- ErrorCode `EVT001~005` · `CPN001~007` · `LCK001~003`

**Out of Scope**:
- Redisson — 사유: 캠프 요구사항 명시 금지 (Lettuce 강제)
- Redlock (다중 Redis 복제) — 사유: 단일 Redis 전제 · Scale-out 시 v0.0.4v+ 검토
- ShedLock (스케줄러 다중 인스턴스) — 사유: 단일 인스턴스 전제 · v0.0.4v+
- 대기열 (Waiting Queue) — 사유: 티켓 예매 아님 · Fail Fast 채택
- 쿠폰 사용 시 주문 통합 — 사유: 이번 스코프는 발급만 · 사용 로직은 별도 이슈
- 관리자 대시보드 시각화 — 사유: 이슈 13 (product-admin-backoffice.md)이 담당

## 대상 사용자
- **관리자** — 타임세일 이벤트 등록·모니터링 · 쿠폰 정책 등록 · 발급 현황 확인 (백오피스 핵심 사용자)
- **일반 구매자** — 진행 중 타임세일 참여 · 쿠폰 발급 요청
- **개발자/리뷰어** — 락 3전략 비교 리포트 (ADR 013 · README) · 이력서 검증 대상

## 연결된 Epic 목록
- [ ] Epic 1: TimeSaleEvent 도메인 · Admin CRUD · 상태 전이 스케줄러
- [ ] Epic 2: 락 3전략 구현 + 실패 테스트 + 벤치마크 리포트 (이력서 파괴력 최상)
- [ ] Epic 3: 선착순 쿠폰 발급 · LockService 재사용 · S+ 이중 방어

## 관련 문서
- 대응 이슈 (fix/brainstorming):
  - [issue-06-timesale-event-domain](../../../fix/brainstorming/version/0.0.3v/issue-06-timesale-event-domain.md)
  - [issue-07-concurrency-lock-strategy-comparison](../../../fix/brainstorming/version/0.0.3v/issue-07-concurrency-lock-strategy-comparison.md)
  - [issue-08-first-come-coupon-issuance](../../../fix/brainstorming/version/0.0.3v/issue-08-first-come-coupon-issuance.md)
- 관련 ADR (발행 예정):
  - `ADR 013` — 동시성 락 3전략 비교·최종 선택 (본 Product 완주 시)
- CLAUDE.md / `.claude/rules/*` 갱신:
  - `.claude/rules/consitency.md` §5 — 락 순서 `Product → TimeSaleEvent → Coupon → Point` 추가
  - `.claude/rules/idempotency.md` §2 — 카탈로그에 쿠폰 발급 S+ (`(user_id, coupon_id)` UNIQUE + 사전조회)
- 재활용 조각 (아카이브 참조):
  - `../archive/0.0.2v-crowdfunding/product-wallet.md` §Epic 1 — 비관 락 `findByIdForUpdate` 패턴 · @Version 낙관 락 패턴
  - `../archive/0.0.2v-crowdfunding/product-wallet.md` §Epic 4 — Micrometer 지표 등록 방식
- 관련 milestone: `../../../milestones/version/0.0.3v/milestone.md`
- 회의록: `C:\Users\user\.claude\plans\splendid-finding-feather.md`

## 열린 질문 (Open Questions)
- **Q1**: 3전략 벤치마크 결과, 낙관 락이 특정 조건에서 승리하면 전략 스위치를 이벤트별 설정으로 노출할지? (초기: 프로파일 스위치만 · 이벤트별 스위치는 v0.0.4v+ 검토)
- **Q2**: 쿠폰 사용(주문 결제 시 할인 적용) 로직은 언제 붙일지? (현재: 발급만 · 사용은 M2 or 별도 마일스톤)
- **Q3**: 락 hold time P95 목표 50ms 초과 시 대응 방침? (초기: 알람만 · 문제 지속 시 로직 분리 검토)
- **Q4**: TimeSaleEvent CANCEL 상태 추가 여부? (관리자 이벤트 취소 시나리오 · 초기 스코프 밖 · 필요 시 Epic 1에 흡수)
- **Q5**: Redlock 도입 시점? (단일 Redis 다운 시 이벤트 락 실패 · v0.0.4v+ ADR로 결정)

## 제품 수준 완료 기준 (Product-level DoD)
- [ ] Epic 1·2·3 각 DoD 통과
- [ ] ADR 013 발행 (동시성 락 3전략 비교·최종 선택)
- [ ] `.claude/rules/consitency.md` §5 · `idempotency.md` §2 갱신 완료
- [ ] k6 부하테스트 (이슈 11) 결과에 3전략 P50/P95/P99 표 · 재고 음수 발생률 0건 확인
- [ ] README §Concurrency 섹션에 3전략 비교 표 · 최종 선택 근거 · 코드 스니펫

---

# [Epic 1] TimeSaleEvent 도메인 · Admin CRUD · 상태 전이 스케줄러

## 목표
관리자가 백오피스에서 타임세일 이벤트를 등록·수정·조회할 수 있고, 스케줄러가 `startedAt` 도래 시 자동으로 SCHEDULED → OPEN, `endedAt` 도래 시 OPEN → CLOSED로 상태를 전이한다.

## 배경
Epic 2 (락 3전략)의 무대. 이벤트 도메인 없이는 락 대상이 없음. 관리자가 이벤트를 자유롭게 발동할 수 있어야 백오피스 시연 스토리 성립.

## 포함 Story
- Story 1-1: TimeSaleEvent 엔티티 · Repository · Status enum
- Story 1-2: Admin CRUD (`POST/GET/PATCH/DELETE /api/v1/admin/timesale-events`)
- Story 1-3: TimeSaleEventScheduler (@Scheduled 60초 주기 · 상태 전이)
- Story 1-4: 공개 조회 API (`GET /api/v1/timesale-events/active`)

## Epic 인수 시나리오
- Given 관리자 인증 · 상품 재고 100개 존재
- When `POST /api/v1/admin/timesale-events {productId, startedAt=NOW+5분, endedAt=NOW+65분, totalStock=10, discountRate=50}` 호출
- Then 201 Created · `TimeSaleEvent(status=SCHEDULED)` DB 저장 확인
- (5분 후) 스케줄러 실행 · `status → OPEN` 전이 · `event.status.gauge{status=OPEN} +1`

*(엣지)* Given `startedAt >= endedAt` · When 등록 요청 · Then 400 · `EVT005`
*(엣지)* Given SCHEDULED 이벤트 · When 관리자가 PATCH로 수정 · Then 200 · 정상 수정
*(엣지)* Given OPEN 이벤트 · When 관리자가 PATCH · Then 409 · 상태 잠금 (`EVT004` — 초안 · 정책 확정 필요)

## Epic 완료 기준 (DoD)
- [ ] Story 1-1 ~ 1-4 모두 완료
- [ ] 통합 테스트: 스케줄러 트리거 시나리오 (`@SpringBootTest` + 시간 mock)
- [ ] Admin CRUD 각 4개 엔드포인트 슬라이스 테스트 (`@WebMvcTest`)
- [ ] ErrorCode `EVT001~005` 등록

## Epic 기술 결정 / 대안 (Epic-Level Alternatives)
- **스케줄러 주기 60초** vs 30초: 60초 채택 (P95 30초 지연 허용 · 리소스 절약). 프로덕션 배포 시 조정 가능
- **상태 전이 = 도메인 메서드** (`event.markOpen()`) vs Service 직접 조작: 도메인 메서드 채택 (규범 준수 · 상태 잠금 로직 도메인 안)

## [Story 1-1] TimeSaleEvent 엔티티 · Repository · Status enum

### User Story
- As a 개발자
- I want TimeSaleEvent 도메인 골격을 갖추고
- so that 이벤트 등록·조회·상태 전이의 데이터 모델을 완성한다

### 설명
- 신규 컨텍스트 `nbc.c1oud_mall.event.*` (4레이어) 골격
- `TimeSaleEventStatus` enum: `SCHEDULED · OPEN · SOLD_OUT · CLOSED`
- `TimeSaleEvent` 엔티티 필드: id · productId · discountRate · totalStock · remainingStock · startedAt · endedAt · status · @Version version · createdAt · updatedAt (BaseEntity 상속)
- 도메인 메서드: `open()` · `markSoldOut()` · `close()` · `decreaseStock()` — 상태 전이 검증
- `TimeSaleEventRepository` (Spring Data JPA) + `TimeSaleEventJpaEntity` (팀 컨벤션 = 도메인=Entity 통합 방식 A · memory 참조)

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.event.domain.TimeSaleEvent` — Aggregate root · 상태 전이 검증
- `nbc.c1oud_mall.event.domain.TimeSaleEventStatus` — enum
- `nbc.c1oud_mall.event.infrastructure.TimeSaleEventRepository` — Spring Data JPA
- `nbc.c1oud_mall.event.infrastructure.TimeSaleEventCustomRepository` — findByIdForUpdate (Epic 2 진입점)

**주요 메서드**:
- `TimeSaleEvent.create(productId, discountRate, totalStock, startedAt, endedAt)` — 정적 팩토리 (검증)
- `TimeSaleEvent.open()` — SCHEDULED만 · 아니면 EVT004
- `TimeSaleEvent.markSoldOut()` — OPEN만 · remainingStock=0 필수
- `TimeSaleEvent.close()` — OPEN/SOLD_OUT만
- `TimeSaleEvent.decreaseStock()` — OPEN만 · remainingStock > 0 필수 · 자동 SOLD_OUT 전이

### 완료 기준 (AC)
- Given `TimeSaleEvent.create(1L, 50, 10, T+5m, T+65m)` / When 호출 / Then `status=SCHEDULED · remainingStock=10 · version=0`
- Given `event.status=SCHEDULED` / When `event.markSoldOut()` / Then `BusinessException(EVT004)` — 잘못된 상태 전이
- Given `event.status=OPEN · remainingStock=1` / When `event.decreaseStock()` / Then `remainingStock=0 · status=SOLD_OUT` 자동 전이
- Given `event.status=OPEN · remainingStock=0` / When `event.decreaseStock()` / Then `BusinessException(EVT003)`
- *(엣지 · 검증)* Given `startedAt >= endedAt` / When `TimeSaleEvent.create(...)` / Then `BusinessException(EVT005)` — 잘못된 기간

### Definition of Done
- [ ] 구현: `src/main/java/nbc/c1oud_mall/event/domain/{TimeSaleEvent,TimeSaleEventStatus}.java` · `src/main/java/nbc/c1oud_mall/event/infrastructure/TimeSaleEventRepository.java`
- [ ] 단위 테스트 5건 (해피 · 상태 전이 잘못 · 재고 0 감소 · 기간 오류 · SOLD_OUT 자동 전이)
- [ ] 슬라이스 테스트 (`@DataJpaTest` · Repository save/find 검증)
- [ ] ErrorCode `EVT001~005` 등록 (`common.exception.ErrorCode`)
- [ ] JPA ddl-auto=update 자동 반영 확인 (dev H2)

### 스토리 포인트
1d

### 의존성
- 선행: 이슈 01 (Admin 컨텍스트) 완료 (관리자 시드 존재)
- 후행: Story 1-2 (Admin CRUD) · Story 1-3 (스케줄러) · Epic 2 (락 전략 대상)

## [Story 1-2] Admin CRUD 엔드포인트

목록만 (상세 골격은 착수 시 확장):
- `POST /api/v1/admin/timesale-events` — 이벤트 등록 (관리자만 · @PreAuthorize)
- `GET /api/v1/admin/timesale-events?status=&page=&size=` — 목록 (상태별 필터)
- `PATCH /api/v1/admin/timesale-events/{id}` — 수정 (SCHEDULED만)
- `DELETE /api/v1/admin/timesale-events/{id}` — 삭제 (SCHEDULED만)

**SP**: 1d

## [Story 1-3] TimeSaleEventScheduler 상태 전이

목록만:
- `@Component TimeSaleEventScheduler` · `@Scheduled(fixedDelay=60000)`
- `openScheduled()` — `findByStatusAndStartedAtBefore(SCHEDULED, now)` → 각 이벤트 `markOpen()` + save
- `closeExpired()` — `findByStatusAndEndedAtBefore(OPEN, now)` → 각 이벤트 `close()` + save
- 로그: `SCHEDULER_TIMESALE_TRANSITION eventId={} from={} to={}`

**SP**: 0.5d

## [Story 1-4] 공개 조회 API

목록만:
- `GET /api/v1/timesale-events/active` — `status=OPEN` 이벤트만 (커서 페이징 or 페이지)
- `TimeSaleEventListResponse` (record) — id · productId · productName · discountRate · remainingStock · endedAt

**SP**: 0.5d

---

# [Epic 2] 락 3전략 구현 + 실패 테스트 + 벤치마크 리포트 (이력서 파괴력 최상)

## 목표
낙관 락 · 비관 락 · Lettuce Redis Lock 3전략을 실제로 구현하고, 재고 10개 · 동시 100 요청 시나리오에서 각 전략의 정합성·성능·데드락 여부를 실측 비교해 ADR 013 · README 락 섹션으로 문서화한다.

## 배경
캠프 요구사항의 핵심. 이력서에서 가장 어필할 지점. 실패 테스트 → 각 전략 구현 → 비교의 서사가 강함.

## 포함 Story
- Story 2-1: ExecutorService + CyclicBarrier 실패 테스트 (락 없이 재고 음수 재현)
- Story 2-2: 낙관 락 (@Version + 재시도 3회 지수 backoff)
- Story 2-3: 비관 락 (SELECT FOR UPDATE)
- Story 2-4: Lettuce Redis Lock (SETNX + TTL + UUID + Lua 원자 해제)
- Story 2-5: 3-way 벤치마크 · 리포트 · ADR 013

## Epic 인수 시나리오
- Given 재고 10개 이벤트 · 락 없이 구현된 `purchaseWithoutLock`
- When ExecutorService 100 스레드 + CyclicBarrier로 동시 실행
- Then `remainingStock < 0` 재현됨 (**실패해야 정상**) · 이 테스트가 커밋되어 있음

- Given 낙관 락 적용된 `purchaseOptimistic`
- When 동일 시나리오 실행
- Then `remainingStock >= 0` · 최종 판매 수 = 10 · 재시도 로그 다수

- Given Redis Lock 적용된 `purchaseWithRedisLock`
- When 동일 시나리오 실행
- Then `remainingStock >= 0` · 락 획득 실패 사용자에게 `LCK001 429`

## Epic 완료 기준 (DoD)
- [ ] 5개 Story 완료
- [ ] ADR 013 발행 (락 3전략 비교 · 최종 선택 근거 · 코드 스니펫)
- [ ] `.claude/rules/consitency.md` §5 락 순서 표에 `Product → TimeSaleEvent → Coupon → Point` 추가
- [ ] README §Concurrency 섹션 작성 (표 + 스니펫)
- [ ] k6 부하테스트 (이슈 11) 결과가 리포트에 포함

## [Story 2-1] ExecutorService + CyclicBarrier 실패 테스트

### User Story
- As a 개발자
- I want 락 없이 동시 요청 시 재고 음수가 발생함을 재현하는 테스트를 먼저 작성하고
- so that 이후 락 전략들이 이 문제를 해결함을 정량적으로 증명한다

### 설명
- `TimeSalePurchaseConcurrencyTest` 클래스 · `@SpringBootTest`
- `TimeSalePurchaseService.purchaseWithoutLock(eventId)` — 조회 · 감소 · 저장을 락 없이 순차 수행 (의도된 취약)
- `ExecutorService(100)` · `CyclicBarrier(100)` · 동시 시작
- 재고 10개 · 100 스레드 · 예상 실패: `remainingStock < 0` 또는 판매 성공 > 10

**핵심 클래스/인터페이스**:
- `TimeSalePurchaseService.purchaseWithoutLock(eventId)` — 락 없는 취약 구현
- `TimeSalePurchaseConcurrencyTest` — 통합 테스트

### 완료 기준 (AC)
- Given 재고 10 이벤트 · 100 스레드 CyclicBarrier 대기 / When barrier.await() 이후 동시 실행 / Then `remainingStock < 0` 또는 성공 카운트 > 10 (**실패해야 정상**)
- Given 테스트가 CI에서 항상 실패하면 안 됨 / When `@Tag("concurrency-failure-demo")` 태그 / Then CI에서 skip · 로컬 명시 실행만
- *(예외 · 사유 = 락 도입 후 이 테스트가 성공하면 Epic 2 완료 신호 아님)* 태그 유지

### Definition of Done
- [ ] 구현: `src/test/java/nbc/c1oud_mall/event/concurrency/TimeSalePurchaseConcurrencyTest.java`
- [ ] `purchaseWithoutLock` 구현 (의도된 취약 · 프로덕션 코드가 아니라 테스트 대상)
- [ ] 통합 테스트 · 100 스레드 시나리오 재현
- [ ] `@Tag("concurrency-failure-demo")` 부착 · CI skip
- [ ] 테스트 실행 시 로그에 "재고 음수 발생 · 취약 재현 확인" 마커

### 스토리 포인트
1d

### 의존성
- 선행: Epic 1 완료 (TimeSaleEvent 도메인)
- 후행: Story 2-2 · 2-3 · 2-4 (각 전략이 이 테스트를 통과)

## [Story 2-2] 낙관 락 (@Version + 재시도)

목록:
- `TimeSaleEvent.@Version Long version` 추가
- `TimeSalePurchaseService.purchaseOptimistic(userId, eventId)` — 지수 backoff (100ms · 200ms · 400ms) 3회 재시도
- `OptimisticLockException` catch → `LCK003` after 3회

**SP**: 1d

## [Story 2-3] 비관 락 (SELECT FOR UPDATE)

목록:
- `TimeSaleEventRepository.findByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`)
- `TimeSalePurchaseService.purchasePessimistic(userId, eventId)` — 락 획득 · 감소 · 저장
- 락 hold time 로깅 (`lock.hold.duration_seconds`)

**SP**: 0.5d

## [Story 2-4] Lettuce Redis Lock (SETNX + TTL + UUID + Lua)

목록 (상세는 착수 시):
- `LockRedisRepository` — Lettuce `SETNX` + `EXPIRE` 통합 (`SET key value NX EX ttl`)
- `LockService.lock(key, ttl, action)` — UUID 생성 · SETNX 시도 · 성공 시 action 실행 · Lua 스크립트로 원자 해제
- Lua 해제 스크립트: `if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end`
- Fail Fast 정책 · 획득 실패 시 `LCK001` 429
- 통합 테스트: 100 스레드 · 락 획득 성공/실패 비율 확인

**SP**: 2d (핵심 · 이력서 파괴력 최상)

## [Story 2-5] 3-way 벤치마크 · 리포트 · ADR 013

목록:
- 벤치마크 스크립트 (JMH or 통합 테스트 기반)
- 지표: 총 소요 시간 · 성공/실패 카운트 · P50/P95/P99 · 재시도 폭발률 (낙관 락) · 락 hold time (비관·Redis)
- ADR 013 발행: 배경 · 결정 · 대안 · 결과 · 롤아웃 · 검증
- README §Concurrency: 3전략 비교 표 + 각 전략 코드 스니펫

**SP**: 1d

---

# [Epic 3] 선착순 쿠폰 발급 · LockService 재사용 · S+ 이중 방어

## 목표
관리자가 등록한 쿠폰 정책에 대해 사용자가 선착순 발급을 요청하면, LockService(Epic 2 결과)를 재사용해 발급 수량 정합성을 보장하고 중복 발급을 사전조회 + DB UNIQUE로 이중 방어한다.

## 배경
캠프 요구사항 필수 (동시성 예시 2). Epic 2의 LockService를 다른 도메인에서 재사용해 인프라의 재사용성 증명.

## 포함 Story
- Story 3-1: Coupon · UserCoupon 도메인 + Admin CRUD (정책 등록)
- Story 3-2: CouponIssueService.issue (LockService 재사용 · S+ 이중 방어)
- Story 3-3: CouponController (발급·조회·사용) + ErrorCode CPN001~007

## Epic 인수 시나리오
- Given 관리자가 `totalQuantity=100 · discountRate=10%` 쿠폰 등록
- When 1000명이 동시 발급 요청
- Then 정확히 100명 발급 성공 · 나머지 900명 `CPN003 409` (SOLD_OUT) · 중복 발급 0건

- Given 사용자 A가 쿠폰 X 발급 완료
- When 사용자 A가 재요청
- Then `CPN004 409` (사전조회 통과 · DB UNIQUE 이중 방어 무관하게 명확)

## Epic 완료 기준 (DoD)
- [ ] Story 3-1 ~ 3-3 완료
- [ ] LockService(Epic 2) 재사용 (`lockService.lock("coupon:" + couponId, ...)`)
- [ ] `.claude/rules/idempotency.md` §2 카탈로그에 쿠폰 발급 항목 추가 (`(user_id, coupon_id)` UNIQUE + 사전조회 · S+ 등급)
- [ ] 통합 테스트: 1000 스레드 발급 시나리오

## [Story 3-1] Coupon · UserCoupon 도메인 + Admin CRUD

목록:
- `Coupon` 엔티티: id · code · name · discountType (RATE/AMOUNT) · discountValue · minOrderAmount · totalQuantity · issuedQuantity · issueStartedAt · issueEndedAt · usableUntil · status
- `UserCoupon` 엔티티: id · userId · couponId · issuedAt · usedAt · orderId
- `UNIQUE(user_id, coupon_id)` DB 제약
- Admin API: `POST /api/v1/admin/coupons` · `GET /api/v1/admin/coupons?status=` · `PATCH /api/v1/admin/coupons/{id}/status`

**SP**: 1d

## [Story 3-2] CouponIssueService.issue (LockService 재사용 · S+ 이중 방어)

목록:
- `CouponIssueService.issue(userId, couponId)`:
  1. 사전조회 `existsByUserIdAndCouponId(userId, couponId)` → 있으면 `CPN004`
  2. `lockService.lock("coupon:" + couponId, 5s, () -> {`
     - `coupon = repo.findById(couponId)` · 발급 기간·수량 검증
     - `coupon.increaseIssued()` (`issuedQuantity++`) · 소진 시 `CPN003`
     - `userCoupon = UserCoupon.of(userId, couponId)` · save (DB UNIQUE 이중 방어)
     - `catch DataIntegrityViolationException → CPN004`
  3. `})`
- 로그: `COUPON_ISSUED userId={} couponId={} issued={} remaining={}`

**SP**: 1.5d

## [Story 3-3] CouponController + ErrorCode

목록:
- `POST /api/v1/coupons/{couponId}/issue` (JWT · 발급)
- `GET /api/v1/coupons/available` (JWT · 발급 가능 쿠폰 목록)
- `GET /api/v1/users/me/coupons` (JWT · 내 쿠폰)
- `POST /api/v1/users/me/coupons/{userCouponId}/use` (JWT · 사용 · 주문 시 · 이번 스코프는 상태만 USED로 · 실 주문 결합은 별도 이슈)
- ErrorCode `CPN001~007` 등록

**SP**: 1d
