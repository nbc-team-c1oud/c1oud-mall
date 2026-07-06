# M6 / 0.0.6v — Timesale Concurrency 전 Epic 완주 (핵심 · 이력서 파괴력 최상) · Week of 2026-07-13 ~ 2026-07-16 (D1 = 2026-07-13 Mon)

> **마일스톤의 역할**: M5가 07-13에 SRC E1·E2 완주로 부분 동결됐다 (E3는 D15~D16 대기). 본 M6는 Ops Backend 21일 로드맵의 **세 번째** 마일스톤이자 **본 프로젝트의 핵심 축**으로 **product-timesale-concurrency 전 Epic 완주**를 목표로 한다. 낙관 락(@Version + 재시도) · 비관 락(SELECT FOR UPDATE) · Lettuce Redis Lock(SETNX + TTL + UUID + Lua 원자 해제) 3전략을 실제로 구현·실패 테스트·벤치마크 리포트로 정량 비교하고 ADR 013으로 문서화한다. **이력서 파괴력 최상**.
>
> 한 버전 = `version/0.0.Xv/` 폴더 하나. 본 버전(0.0.6v)에는:
> - `milestone.md` *(본 문서)* — 잡힌 양 + 일정 + 의존 + Epic PR 매트릭스
> - `outcome.md`·`review.md`·`benchmark-report.md` — 완주 후 소급 (벤치마크는 필수)
>
> `infra.md`·`performance.md`·`cost.md`는 스킵. 성능 실측은 M8 perf-lab이 담당(k6 종합) · 본 M6는 3전략 자체 비교만.

**릴리스 대응**: 본 M6는 Ops Backend 21일 로드맵의 **세 번째** 마일스톤 · **본 프로젝트의 정면**. 상위 M3(`../0.0.3v/milestone.md`) §Week 2 로드맵 참조.

**SDD 원본**: `workflows/task/pes/workspectrum/sdd/in-progress/product-timesale-concurrency.md` — Product 1 타임세일·락 3전략·선착순 쿠폰 (3 Epic · Story ~15개 · SP ~12.5).

---

## 0.0.6v 스코프 결정 (M5 이후, 2026-07-13)

**M5 착지 결과 요약** (부분):
- ✅ Search Epic 1·2 완주 (v1 QueryDSL + v2 Caffeine + 인기 검색어)
- ⏸️ Search Epic 3 (Redis Remote) D15~D16 대기
- ✅ Perf-Lab Epic 1 완주 (5만+ 시딩 · Datafaker)
- ✅ Redis Lettuce 인프라 확보 (본 M6 락 저장소 재사용 가능)

**M6 축 결정 — product-timesale-concurrency 전 Epic 완주**:

**본 프로젝트의 핵심 마일스톤**. Timesale + Coupon + 락 3전략은 캠프 요구사항 필수(동시성 제어 · 락 3전략 비교 문서화 · ExecutorService+CyclicBarrier 실패 테스트)이자 이력서 파괴력 최상 소재. Redis Lettuce 인프라(M5 확보) 재사용. **주력 (~100%)**.

**Epic 단위 PR 3건 예정 (본주 목표 · 4일 스판)**:

| Epic PR | 대응 | 예상 SP | 종료 목표 |
|---|---|---|---|
| PR#1 (TS-E1-DOMAIN) | Timesale Epic 1 Story 1-1~1-4 (TimeSaleEvent 도메인 · Admin CRUD · 스케줄러 상태 전이 · 공개 조회) | 3 | D1 |
| PR#2 (TS-E2-LOCK-3WAYS) | Timesale Epic 2 Story 2-1~2-5 (실패 테스트 · 낙관 락 · 비관 락 · Lettuce Redis Lock · 3-way 벤치마크 · **ADR 013**) | 6 | D3 |
| PR#3 (TS-E3-COUPON) | Timesale Epic 3 Story 3-1~3-3 (Coupon · UserCoupon 도메인 · LockService 재사용 · S+ 이중 방어) | 3.5 | D4 |

**Total: 3 Epic PR · 총 ~12.5 SP** (Epic 2가 절반 이상 · 이력서 파괴력 최상 축).

**본 버전 제외 사유**:
- **Redisson** — Product SDD §Out of Scope · 캠프 요구사항 명시 금지 (Lettuce 강제)
- **Redlock** (다중 Redis 복제) — 단일 Redis 전제 · v0.0.4v+
- **ShedLock** (스케줄러 다중 인스턴스) — 단일 인스턴스 전제
- **대기열** (Waiting Queue) — 티켓 예매 아님 · Fail Fast
- **쿠폰 사용 시 주문 통합** — 이번 스코프는 발급만
- **관리자 대시보드 이벤트 판매 지표** — Admin M4 Epic 2 (D20)가 담당

---

## 진행 중 Product 잔여 인벤토리 (Before/After — M6 진입 시 vs 종료 후 예상)

| Product | 총 Story | M6 진입 시 완료 | M6 진입 시 잔여 | M6 대상 | **M6 종료 후 예상 잔여** | **해결율** |
| --- | --- | --- | --- | --- | --- | --- |
| Admin Backoffice | 8 | 4 (E1) | 4 (E2 D20) | — | 4 | 0% |
| Search & Cache | 14 | 8 (E1+E2) | 6 (E3 D15~16) | — | 6 | 0% |
| **Timesale Concurrency** (`product-timesale-concurrency.md` · 3 Epic) | **~15** | 0 | 15 | **12 Story (E1·E2·E3)** | **~3** | **80%** ↑ |
| CS Chat | 8 | 0 | 8 | — | 8 | 0% (M7) |
| Perf Lab | 13 | 4 (E1) | 9 | — | 9 | 0% (M8) |
| **합계** | **~58** | 16 | 42 | **12 Story · 3 Epic PR · 12.5 SP** | **~30** | **29%** ↑ |

### 📊 M6 예상 성과 카드 (**본 프로젝트 정면**)

- **총 해결 대상**: 12 Story (전체 잔여의 **29% 소진** · 그중 **락 3전략 6 SP는 이력서 파괴력 최상**)
- **완주 예상 SDD Product**:
  - `product-timesale-concurrency.md` Epic 1·2·3 — **완주 100%** (Timesale Product 완결)
- **완주 예상 산출물**:
  - `nbc.c1oud_mall.event.*` 컨텍스트 (TimeSaleEvent 도메인)
  - `nbc.c1oud_mall.coupon.*` 컨텍스트 (Coupon · UserCoupon)
  - `nbc.c1oud_mall.common.lock.*` 인프라 (LockService · LockRedisRepository · Lettuce)
  - `TimeSaleEventStatus` enum (SCHEDULED → OPEN → SOLD_OUT/CLOSED)
  - 도메인 메서드 상태 전이 (`open()` · `markSoldOut()` · `close()` · `decreaseStock()`)
  - `TimeSaleEventScheduler` (@Scheduled 60초 주기)
  - **낙관 락 (@Version + 지수 backoff 3회 재시도)**
  - **비관 락 (SELECT FOR UPDATE via `@Lock(PESSIMISTIC_WRITE)`)**
  - **Lettuce Redis Lock (SETNX + TTL 5s + UUID + Lua 원자 해제)**
  - ExecutorService(100) + CyclicBarrier(100) 실패 테스트
  - **3-way 벤치마크 리포트** (`docs/perf/timesale-lock-benchmark.md`)
  - **ADR 013 발행** (동시성 락 3전략 비교·최종 선택)
  - `.claude/rules/consitency.md` §5 락 순서 표에 `Product → TimeSaleEvent → Coupon → Point` 추가
  - `.claude/rules/idempotency.md` §2 카탈로그에 쿠폰 발급 S+ 등급 추가
  - `ErrorCode.EVT001~005` · `CPN001~007` · `LCK001~003` 등록
  - Micrometer `timesale.purchase.total{strategy, result}` · `lock.acquire.total{key_type, result}` · `lock.hold.duration_seconds{key_type}`
- **M6 종료 후 남는 것**:
  - Search E3 (Redis Remote D15~D16 예정)
  - CS Chat (M7 · 8 Story)
  - Perf Lab E2·E3 (M8)
  - Admin Dashboard (E2 D20)

---

## Epic PR 매트릭스 (본주 잡힌 양)

### Epic PR #1 — `TS-E1-DOMAIN` (TimeSaleEvent 도메인 · Admin CRUD · 스케줄러)

**Base 브랜치**: `feature/timesale-event-domain`

**SDD 위치**:
- Epic: `# [Epic 1] TimeSaleEvent 도메인 · Admin CRUD · 상태 전이 스케줄러`
- Story 범위: `## [Story 1-1]` ~ `## [Story 1-4]`
- 상위 이슈 원천: `issue-06-timesale-event-domain.md`

| # | Story | 한 줄 | SP |
|---|---|---|---|
| 1 | TS E1 S1-1 | `nbc.c1oud_mall.event.*` 4레이어 골격 + `TimeSaleEventStatus` enum (4값) + `TimeSaleEvent` 엔티티 (id · productId · discountRate · totalStock · remainingStock · startedAt · endedAt · status · @Version) + 도메인 메서드 4개 + Repository | 1 |
| 2 | TS E1 S1-2 | `AdminTimeSaleEventController` (POST/GET/PATCH/DELETE `/api/v1/admin/timesale-events`) + Bean Validation + @PreAuthorize | 1 |
| 3 | TS E1 S1-3 | `TimeSaleEventScheduler` @Scheduled(fixedDelay=60000) · `openScheduled()` + `closeExpired()` · 상태 전이 도메인 메서드 호출 | 0.5 |
| 4 | TS E1 S1-4 | `TimeSaleEventController.getActive` (공개 조회 · status=OPEN 필터 · 커서 페이징 or 페이지) | 0.5 |

**PR 종료 신호**:
- `TimeSaleEvent.create()` · `.open()` · `.markSoldOut()` · `.close()` · `.decreaseStock()` 단위 테스트 5건 통과
- POST /admin/timesale-events → 201 · SCHEDULED 상태 저장
- 스케줄러 60초 주기 로컬 부팅 검증 (시간 mock or Awaitility)
- ErrorCode EVT001~005 등록 · Bean Validation SRC005 (기간 오류) 검증

**의존**: M4 Admin 컨텍스트 (관리자 롤 검증).

### Epic PR #2 — `TS-E2-LOCK-3WAYS` (락 3전략 + 실패 테스트 + 벤치마크 + ADR 013) **⭐ 핵심**

**Base 브랜치**: `feature/concurrency-lock-strategy-comparison`

**SDD 위치**:
- Epic: `# [Epic 2] 락 3전략 구현 + 실패 테스트 + 벤치마크 리포트 (이력서 파괴력 최상)`
- Story 범위: `## [Story 2-1]` ~ `## [Story 2-5]`
- 상위 이슈 원천: `issue-07-concurrency-lock-strategy-comparison.md`

| # | Story | 한 줄 | SP |
|---|---|---|---|
| 1 | TS E2 S2-1 | `TimeSalePurchaseConcurrencyTest` (ExecutorService(100) + CyclicBarrier(100) · 재고 10 · 100 요청 · **실패해야 정상** · @Tag("concurrency-failure-demo") CI skip) + `purchaseWithoutLock` 취약 구현 | 1 |
| 2 | TS E2 S2-2 | 낙관 락 (@Version 활용 · `purchaseOptimistic` · `OptimisticLockException` catch · 지수 backoff 3회 (100ms · 200ms · 400ms) · 최종 실패 시 `LCK003 429`) | 1 |
| 3 | TS E2 S2-3 | 비관 락 (`TimeSaleEventRepository.findByIdForUpdate` @Lock(PESSIMISTIC_WRITE) · `purchasePessimistic` · 락 hold time 로깅 histogram) | 1 |
| 4 | TS E2 S2-4 | **Lettuce Redis Lock** (`LockRedisRepository` SETNX + TTL + UUID · `LockService.lock(key, ttl, action)` · Lua 원자 해제 스크립트 · Fail Fast · `LCK001 429`) | 2 |
| 5 | TS E2 S2-5 | 3-way 벤치마크 (100 스레드 동일 시나리오 · 지표: 총 소요 · 성공/실패 · P50/P95/P99 · 재시도 폭발 · 락 hold time) + `docs/perf/timesale-lock-benchmark.md` 리포트 + **ADR 013 발행** + README §Concurrency 섹션 (3전략 비교 표 · 코드 스니펫) + `.claude/rules/consitency.md` §5 락 순서 갱신 | 1 |

**PR 종료 신호**:
- Story 2-1 테스트 실행 시 락 없이 재고 음수 재현 (**실패해야 정상 · 로그 마커**)
- 낙관 락: 100 스레드 동시 → `remainingStock >= 0` · 최종 판매 10 · 재시도 로그 다수
- 비관 락: 100 스레드 동시 → 정확 · 락 hold time histogram 확인
- Redis Lock: 100 스레드 동시 → 락 획득 실패 사용자에게 `LCK001 429` · 성공률 관찰
- 벤치마크 리포트: P50/P95/P99 3전략 비교 표
- `docs/adr/013-concurrency-lock-strategy.md` §1~9 완결
- `.claude/rules/consitency.md` §5 락 순서 표에 `Product → TimeSaleEvent → Coupon → Point` 추가
- README §Concurrency 섹션 완성 (3전략 비교 표 + 코드 스니펫 + 최종 선택 근거)

**의존**: PR#1 완주 (TimeSaleEvent 도메인).

**Reviewer 세션**: 5관점 발사 (Domain / Architecture / API-Exception / Test / Sceptical). **특히 Architecture Reviewer가 "Lua 스크립트 원자 해제 정확성"과 "Redis 연결 실패 시 fallback 정책"을 검증** · Sceptical Reviewer가 "3전략 최종 선택 근거가 벤치마크 결과와 정합적인가"를 판정.

### Epic PR #3 — `TS-E3-COUPON` (선착순 쿠폰 · LockService 재사용 · S+ 이중 방어)

**Base 브랜치**: `feature/first-come-coupon-issuance`

**SDD 위치**:
- Epic: `# [Epic 3] 선착순 쿠폰 발급 · LockService 재사용 · S+ 이중 방어`
- Story 범위: `## [Story 3-1]` ~ `## [Story 3-3]`
- 상위 이슈 원천: `issue-08-first-come-coupon-issuance.md`

| # | Story | 한 줄 | SP |
|---|---|---|---|
| 1 | TS E3 S3-1 | `nbc.c1oud_mall.coupon.*` 4레이어 + `Coupon` 엔티티 (정책) + `UserCoupon` 엔티티 (발급 이력) + `UNIQUE(user_id, coupon_id)` DB 제약 + `AdminCouponController` (정책 CRUD) | 1 |
| 2 | TS E3 S3-2 | `CouponIssueService.issue(userId, couponId)` — 사전조회(existsBy) + `LockService.lock("coupon:" + couponId, ...)` + coupon.increaseIssued() + userCoupon save + `catch DataIntegrityViolationException → CPN004` (S+ 이중 방어) + `.claude/rules/idempotency.md` §2 카탈로그 갱신 | 1.5 |
| 3 | TS E3 S3-3 | `CouponController` (POST /coupons/{id}/issue · GET /coupons/available · GET /users/me/coupons · POST /users/me/coupons/{id}/use 상태만 USED) + `ErrorCode.CPN001~007` + 1000 스레드 발급 통합 테스트 | 1 |

**PR 종료 신호**:
- 관리자가 `totalQuantity=100` 쿠폰 등록 · 1000명 동시 발급 요청 → 정확히 100명 발급 성공 · 나머지 900명 CPN003 · 중복 발급 0건
- 같은 사용자 재요청 → CPN004 (사전조회) 또는 DataIntegrityViolationException → CPN004 (S+ 이중 방어) 모두 검증
- `.claude/rules/idempotency.md` §2에 쿠폰 발급 항목 추가 (`(user_id, coupon_id)` UNIQUE + 사전조회 · S+ 등급)
- LockService 재사용 확인 (Epic 2 인프라 · 새 락 코드 X)

**의존**: PR#2 완주 (LockService 인프라).

**Reviewer 세션**: 5관점 발사. **특히 Test Reviewer가 "S+ 이중 방어(사전조회 + DB UNIQUE)가 race 하에서 정확히 CPN004로 정합 응답하는가"를 검증**.

---

## Story 카테고리별 합계

| 카테고리 | SDD Epic/Story | Story 수 | SP | 비중 |
| --- | --- | --- | --- | --- |
| Timesale Epic 1 (도메인·스케줄러) | TS E1 S1-1~S1-4 | 4 | 3 | 24% |
| **Timesale Epic 2 (락 3전략) ⭐** | TS E2 S2-1~S2-5 | 5 | 6 | 48% |
| Timesale Epic 3 (쿠폰) | TS E3 S3-1~S3-3 | 3 | 3.5 | 28% |
| **합계** | | **12** | **12.5** | 100% |

**분배 근거**:
- **Epic 2 (락 3전략) 48%** — 본 프로젝트의 이력서 파괴력 최상 축. 3전략 실 구현 + 벤치마크 + ADR + README 섹션이 큰 시간
- **Epic 1 (도메인) 24%** — Epic 2의 무대 · 상대적 경량
- **Epic 3 (쿠폰) 28%** — LockService 재사용으로 볼륨 절감 · S+ 이중 방어가 추가 시간

---

## 종료 신호 — "락 3전략 완주 + 벤치마크 + ADR 013 + 쿠폰 정합성"

본 M6 종료 시점에 다음이 모두 성립해야 한다. (9 신호 중 7개 이상 → 0.0.6v 동결)

- [ ] **머지 신호**: Epic PR 3개 모두 머지 (100%)
- [ ] **도메인 신호**: TimeSaleEvent 도메인 메서드 5건 단위 테스트 통과 · 스케줄러 상태 전이 로컬 검증
- [ ] **실패 테스트 신호**: `TimeSalePurchaseConcurrencyTest` 실행 시 락 없이 재고 음수 재현 · @Tag CI skip 유지
- [ ] **낙관 락 신호**: 100 스레드 동시 → remainingStock >= 0 · 판매 정확 10 · 지수 backoff 재시도 로그
- [ ] **비관 락 신호**: 100 스레드 동시 → 정확 · `lock.hold.duration_seconds` histogram 노출
- [ ] **Redis Lock 신호**: SETNX + TTL + UUID + Lua 원자 해제 · 100 스레드 동시 · 락 획득 실패 시 LCK001 · Fail Fast 검증
- [ ] **벤치마크·ADR 신호**: `docs/perf/timesale-lock-benchmark.md` P50/P95/P99 3전략 표 · ADR 013 발행 · README §Concurrency 섹션
- [ ] **쿠폰 신호**: 1000 스레드 동시 발급 → 정확히 100 성공 · 900 CPN003 · 중복 0
- [ ] **규범 신호**: `.claude/rules/consitency.md` §5 락 순서 갱신 · `.claude/rules/idempotency.md` §2 쿠폰 발급 항목

**미합격 처리**: 7 신호 미만 시 0.0.6.1v 패치 발행 → M7 진입 지연. Epic 2(락 3전략)는 프로젝트 정면이므로 미완 시 시한 초과 감수하고 완주 우선.

---

## 의존 chain

```
[선행: M4·M5 착지]
Admin 컨텍스트 · 롤 · JWT ✅ 완료
Redis Lettuce 인프라 (M5) ✅ 확보

[D1: 착수]
PR#1 (TS-E1-DOMAIN)  단독 진입
  TS E1 S1-1 ~ S1-4
     │
     ▼
TimeSaleEvent 도메인 완주
     │
     ▼
PR#2 (TS-E2-LOCK-3WAYS)  ⭐ 핵심
  TS E2 S2-1 ~ S2-5
     │
     ▼
락 3전략 완주 · 벤치마크 · ADR 013
     │
     ▼
PR#3 (TS-E3-COUPON)
  TS E3 S3-1 ~ S3-3 (LockService 재사용)
     │
     ▼
Timesale Product 완주 · Ops Backend 정면 도착
```

**병렬 진입 가능 묶음**:
- **A** (D1 · 07-13 Mon): PR#1 착수 (TS E1 S1-1~S1-4 도메인·CRUD·스케줄러·공개조회 · 압축 진행) → **PR#1 머지 + Reviewer**
- **B** (D2 · 07-14 Tue): PR#2 착수 (TS E2 S2-1 실패 테스트 + purchaseWithoutLock 취약) + S2-2 (낙관 락) 진행
- **C** (D3 · 07-15 Wed): PR#2 S2-3 (비관 락) + S2-4 (Lettuce Redis Lock · SETNX+Lua) + S2-5 (벤치마크 리포트 + ADR 013 + README + consitency.md §5) 완주 → **PR#2 머지 + Reviewer 세션 (Architecture 강조 · Lua 원자성)**
- **D** (D4 · 07-16 Thu): PR#3 착수 (TS E3 S3-1 Coupon 도메인 + Admin CRUD) + S3-2 (CouponIssueService + LockService 재사용 + S+ 이중 방어 + idempotency.md §2 갱신) + S3-3 (CouponController + 1000 스레드 통합 테스트) 완주 → **PR#3 머지 + Reviewer 세션 (Test 강조)** · 0.0.6v 동결

---

## 작업 일정 (Day별 체크리스트)

D1 = 2026-07-13 (Mon). 종료 D4 = 2026-07-16 (Thu). 4일 안에 3 Epic PR.

| 일 | 날짜 | 잡힌 작업 |
| --- | --- | --- |
| D1 (월) | 07-13 | PR#1 착수 (**TS E1 S1-1** TimeSaleEvent 도메인 + status enum + 도메인 메서드 5) + **S1-2** (Admin CRUD) + **S1-3** (Scheduler) + **S1-4** (공개 조회) 완주 → **PR#1 머지** |
| D2 (화) | 07-14 | PR#2 착수 (**TS E2 S2-1** 실패 테스트 · CyclicBarrier · 재고 음수 재현) + **S2-2** (낙관 락 @Version + 지수 backoff) 진행 |
| D3 (수) | 07-15 | PR#2 **S2-3** (비관 락 SELECT FOR UPDATE) + **S2-4** (Lettuce Redis Lock SETNX+TTL+UUID+Lua) + **S2-5** (3-way 벤치마크 + ADR 013 + README §Concurrency + consitency.md §5) 완주 → **PR#2 머지 + Reviewer 세션 (Architecture 강조 · Lua 원자성 · Redis fallback)** |
| D4 (목) | 07-16 | PR#3 착수 (**TS E3 S3-1** Coupon·UserCoupon 도메인 + Admin CRUD) + **S3-2** (`CouponIssueService.issue` · LockService 재사용 · S+ 이중 방어 · idempotency.md §2 갱신) + **S3-3** (CouponController + 1000 스레드 통합 테스트) 완주 → **PR#3 머지 + Reviewer 세션 (Test 강조)** · 0.0.6v 동결 · `benchmark-report.md` 완성 |

---

## 리스크와 관찰 포인트

| 영역 | 리스크 | 관찰 포인트·완화 |
| --- | --- | --- |
| **Lua 스크립트 원자성** | Redis Lock 해제 시 `if get == uuid then del`을 Lua로 원자화. 스크립트 오작성 시 락 훔치기 리스크 | Architecture Reviewer가 Lua 스크립트 검증 · 통합 테스트 (UUID 미매칭 시 del 미실행 확인) |
| **낙관 락 재시도 폭발** | 100 스레드 동시 · 재시도 3회 · 지수 backoff → CPU/GC 폭발 가능성 | 벤치마크 결과 관찰 · 재시도 횟수·backoff 튜닝 여지 명시 (`application.yml` externalize) |
| **비관 락 hold time** | 재고 감소 + 저장 시간이 짧아야 · 외부 호출 있으면 데드락 리스크 | `lock.hold.duration_seconds` P95 관찰 · consitency.md §5.2 "락 안 외부 호출 금지" 준수 |
| **Redis 연결 실패 시 Timesale 다운** | Redis docker 종료 시 락 획득 실패 · 전략 스위칭 정책 필요 | S2-4에 Redis fallback 정책 명시 (초기 fail-fast · 향후 비관 락 fallback 검토 ADR 013 §Open Question) |
| **1000 스레드 쿠폰 통합 테스트 시간** | ExecutorService(1000) 실행 시간 · CI에서 타임아웃 리스크 | 로컬만 실행 · @Tag CI skip · 결과는 벤치마크 리포트에 기록 |
| **S+ 이중 방어 정합** | 사전조회 통과 후 DB UNIQUE 위반 → catch → CPN004. race에서 정확히 CPN404 응답하는지 | Test Reviewer가 검증 · 시나리오 (동일 사용자 · 동시 2개 요청) 통합 테스트 |
| **락 순서 규범 위반 감시** | `Product → TimeSaleEvent → Coupon → Point` 순서 위반 시 데드락 · PR 차단 규칙 필요 | consitency.md §5.1 "락 획득 순서 통일 · 위반 코드 PR 차단" 준수 · Reviewer 검사 |
| **12.5 SP 4일 스판 부담** | 5+ SP/일 · Reviewer 세션 3회 · 벤치마크 리포트 · ADR · README 갱신 | D2 진행률 확인 → 미달 시 PR#3 쿠폰 통합 테스트 규모 축소 (100 스레드로 조정) |

---

## 다음 마일스톤 (M7 / 0.0.7v) 후보

**M7 / 0.0.7v (07-17 ~ 07-19) — CS Chat 전 Epic 완주**
- **Chat Epic 1** — STOMP + JWT ChannelInterceptor + ChatRoom·ChatMessage 엔티티 + 개설·전송
- **Chat Epic 2** — 상태기계(WAITING→IN_PROGRESS→COMPLETED · 역방향 금지) + 관리자 API + 커서 페이징 + Fetch Join + 재연결 + **ADR 015**

**Epic PR 예상 2건** (총 ~6 SP).

---

## Product 상태 전환 신호 (M6 종료 시)

- `in-progress/product-timesale-concurrency.md` — **Epic 1·2·3 완주** 표기 (**본 프로젝트 정면 도착**)
- `fix/brainstorming/version/0.0.3v/issue-06·07·08.md` → **resolved**
- **ADR 013 발행 완료** (동시성 락 3전략 비교·최종 선택)
- `.claude/rules/consitency.md` §5 락 순서 갱신 완료
- `.claude/rules/idempotency.md` §2 쿠폰 발급 카탈로그 추가 완료

---

## brainstorming 트리거

본 M6 완료 후 `workflows/task/pes/brainstorming/0.0.6v/` 신설:
- Redis 다운 시 락 fallback 정책 (`brainstorming/0.0.6v/redis-lock-fallback.md`) — 비관 락 대체 정책
- 낙관 락 재시도 튜닝 (`brainstorming/0.0.6v/optimistic-lock-backoff-tuning.md`) — 벤치마크 실측 후
- Redlock 도입 시점 (`brainstorming/0.0.6v/redlock-adoption-trigger.md`) — v0.0.4v+ 후속

---

## 참고

- SDD: `../../pes/workspectrum/sdd/in-progress/product-timesale-concurrency.md`
- 대응 이슈: `../../fix/brainstorming/version/0.0.3v/issue-06·07·08.md`
- 상위 마일스톤: `../0.0.3v/milestone.md`
- 이전 마일스톤: `../0.0.5v/milestone.md` — M5 · Search 완주 (부분 · E3 D15~16)
- 회의록: `C:\Users\user\.claude\plans\splendid-finding-feather.md`
- **본 프로젝트 정면 · 이력서 파괴력 최상**
- **주요 SDD 참조 비율**:
  - `product-timesale-concurrency.md` **100%** (Epic 1·2·3 · Story 1-1~3-3 · 12 Story · SP 12.5)
