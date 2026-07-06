# M8 / 0.0.8v — Perf Lab 완주 + 잔여 Epic 통합 · Week of 2026-07-20 ~ 2026-07-26 (D1 = 2026-07-20 Mon)

> **마일스톤의 역할**: M7이 07-19에 동결됐다 (CS Chat 2 Epic 완주 · ADR 015). 본 M8은 Ops Backend 21일 로드맵의 **마지막(다섯 번째)** 마일스톤으로 **product-perf-lab 잔여 Epic (E2 k6 + E3 인덱싱) 완주 + 이월 Epic 흡수**(Search E3 Redis Remote · Admin E2 Dashboard)를 목표로 한다. 5만+ 데이터 위에서 k6 부하테스트로 v1/v2 성능을 정량 비교하고, EXPLAIN Before/After + 복합·커버링 인덱스로 최종 이력서 파괴력 자산을 완성한다.
>
> 한 버전 = `version/0.0.Xv/` 폴더 하나. 본 버전(0.0.8v)에는:
> - `milestone.md` *(본 문서)*
> - `outcome.md`·`review.md` — 완주 후 소급 · Ops Backend 21일 전체 회고 포함
> - `performance.md` — k6 리포트 통합 (본 버전에서만 정식 산출)
>
> `infra.md`·`cost.md` 스킵. Ops Backend 21일 로드맵의 완결 마일스톤.

**릴리스 대응**: Ops Backend 21일 로드맵의 **완결 마일스톤**. 상위 M3(`../0.0.3v/milestone.md`) §Week 3 참조. **본 M8 종료 시 0.0.3v 백오피스 로드맵 전체 동결 · 0.1.0v 릴리스 태그 후보**.

**SDD 원본**:
- `workflows/task/pes/workspectrum/sdd/in-progress/product-perf-lab.md` — Product 5 성능 실험실 (3 Epic · Story ~13개 · SP ~8.5 · **E1 시딩은 M5에서 완주**)
- `product-search-cache.md` §Epic 3 (Redis Remote D15~16 배치가 본 M8으로 이월)
- `product-admin-backoffice.md` §Epic 2 (Dashboard D20 배치가 본 M8으로 이월)

---

## 0.0.8v 스코프 결정 (M7 이후, 2026-07-20)

**M7 착지 결과 요약**:
- ✅ CS Chat Epic 1·2 완주 (STOMP + 상태기계 + 커서 페이징 + N+1 방지 검증 · ADR 015)
- ✅ Ops Backend 3대 축 (Timesale · 쿠폰 · CS) 완결 · 백오피스 정체성 정면 도착

**M8 축 결정 — Perf Lab 완주 + Search E3 + Admin E2 통합 흡수**:

Perf Lab은 백오피스 서사의 마무리 (실측·리포트·인덱싱). 앞선 마일스톤에서 이월된 Search E3 (Redis Remote D15~16)와 Admin E2 (Dashboard D20)를 함께 흡수해 0.0.3v 상위 로드맵 21일을 완결. **주력 60% (Perf) + 이월 흡수 40% (Search E3 + Admin E2)**.

**Epic 단위 PR 4건 예정 (본주 목표 · 7일 스판)**:

| Epic PR | 대응 | 예상 SP | 종료 목표 |
|---|---|---|---|
| PR#1 (SRC-E3-REDIS-EVICT) | Search Epic 3 Story 3-1~3-4 (M5 D15~16 이월 흡수 · Redis Remote + Eviction + ADR 014) | 3 | D2 |
| PR#2 (PERF-E2-K6) | Perf-Lab Epic 2 Story 2-1~2-4 (k6 세팅 · search-v1-vs-v2.js · timesale-purchase.js · 리포트 3건 + README §Performance) | 3 | D4 |
| PR#3 (PERF-E3-INDEXING) | Perf-Lab Epic 3 Story 3-1~3-4 (EXPLAIN Before + 복합 인덱스 DDL + After + 커버링 인덱스 + **ADR 016**) | 2.5 | D6 |
| PR#4 (ADM-E2-DASHBOARD) | Admin Epic 2 Story 2-1~2-4 (M4 D20 이월 흡수 · Overview + 세부 5 API + Micrometer + 선택 Grafana) | 3.5 | D7 |

**Total: 4 Epic PR · 총 ~12 SP** (M6 대비 -4% · 잔여 흡수 마일스톤 · 완결 부담).

**본 버전 제외 사유**:
- **Gatling/JMeter 부하 도구** — Product SDD §Out of Scope · k6 채택
- **Stored Procedure 시딩** — Out of Scope · JDBC Batch 충분
- **MySQL FULLTEXT / Elasticsearch** — 초기 스코프 · v0.0.4v+
- **프로덕션 부하테스트** — dev-large 로컬 only · prod 부하 X
- **CI/CD 도전** — 여유 시 검토 (Product SDD §Out of Scope 옵션 · M9 이관)

---

## 진행 중 Product 잔여 인벤토리 (Before/After — M8 진입 시 vs 종료 후 예상)

| Product | 총 Story | M8 진입 시 완료 | M8 진입 시 잔여 | M8 대상 | **M8 종료 후 예상 잔여** | **해결율** |
| --- | --- | --- | --- | --- | --- | --- |
| Admin Backoffice | 8 | 4 (E1) | 4 (E2) | **4 (E2)** | 0 | **100%** ✅ 완주 |
| Search & Cache | 14 | 8 (E1+E2) | 6 (E3) | **4 (E3)** | ~2 | **67%** ↑ 완주 |
| Timesale Concurrency | 12 | 12 | 0 | — | 0 | ✅ 완주 (M6) |
| CS Chat | 8 | 8 | 0 | — | 0 | ✅ 완주 (M7) |
| **Perf Lab** (`product-perf-lab.md` · 3 Epic) | **13** | 4 (E1) | 9 | **8 (E2·E3)** | **~1** | **89%** ↑ |
| **합계** | **~55** | 36 | 19 | **16 Story · 4 Epic PR · 12 SP** | **~3** | **84%** ↑ |

### 📊 M8 예상 성과 카드

- **총 해결 대상**: 16 Story (전체 잔여의 **84% 소진 · 0.0.3v 백오피스 로드맵 사실상 완결**)
- **완주 예상 SDD Product** (본 M8 이후 전체 5 Product 완결):
  - `product-perf-lab.md` Epic 2·3 — **완주 100%**
  - `product-search-cache.md` Epic 3 — **완주** (Product 완결)
  - `product-admin-backoffice.md` Epic 2 — **완주** (Product 완결)
- **완주 예상 산출물**:
  - k6 스크립트 (`tests/k6/search-v1-vs-v2.js` · `timesale-purchase.js` · 실행 스크립트)
  - 부하 리포트 3건 (`docs/perf/search-v1-vs-v2-report.md` · `timesale-lock-benchmark.md` (M6 이미 완료 · 통합만) · `docs/perf/README.md`)
  - EXPLAIN Before/After 표 (병목 쿼리 4+건 · 복합 인덱스 4+개 · 커버링 인덱스 최소 1건)
  - DDL 스크립트 (`CREATE INDEX ...`) README §Indexing 명시
  - RedisCacheManager + Serializer · @CacheEvict + @CachePut
  - `AdminDashboardController` Overview + 세부 5 API + Micrometer 지표
  - **ADR 014 발행** (v2 Local → Redis Remote)
  - **ADR 016 발행** (인덱싱 전략 · 복합·커버링·Leftmost Prefix)
  - README §Performance · §Indexing 섹션 완성 (그래프 · 표 · 스크린샷)
  - (선택) docker-compose Prometheus + Grafana + Dashboard JSON export + README 스크린샷
- **M8 종료 후 남는 것 (v0.0.9v+ 이관)**:
  - Grafana 정식 세팅 (S2-4 선택)
  - CI/CD 도전 (Docker + EC2 + SSM + Actions)
  - Redis Pub/Sub 다중 서버 (Chat 성능 개선 도전)
  - 관리자 활동 감사 테이블
  - Redlock / ShedLock 확장

---

## Epic PR 매트릭스

### Epic PR #1 — `SRC-E3-REDIS-EVICT` (Search Epic 3 이월 흡수)

**Base 브랜치**: `feature/cache-remote-redis-eviction`

**SDD 위치**:
- 파일: `product-search-cache.md`
- Epic: `# [Epic 3] Redis Remote 전환 + Cache Eviction`

| # | Story | 한 줄 | SP |
|---|---|---|---|
| 1 | SRC E3 S3-1 | `spring-boot-starter-data-redis` + `RedisConfig` + `RedisTemplate<String, Object>` Serializer (String Key + GenericJackson2Json Value + JavaTimeModule) | 1 |
| 2 | SRC E3 S3-2 | `RedisCacheManager` 등록 · v2 스위치 · Caffeine 병존 유지 | 0.5 |
| 3 | SRC E3 S3-3 | @CacheEvict (관리자 상품 CRUD · allEntries) + @CachePut (상품 상세) + TTL (검색 5m · 상세 30m) | 1 |
| 4 | SRC E3 S3-4 | 통합 테스트 (LocalDateTime 직렬화 · 상품 수정→새 데이터) + **ADR 014 발행** | 0.5 |

**PR 종료 신호**: (M5 참조와 동일 · 시점만 이월)

### Epic PR #2 — `PERF-E2-K6` (k6 부하테스트 리포트)

**Base 브랜치**: `feature/load-test-k6-report`

**SDD 위치**:
- 파일: `product-perf-lab.md`
- Epic: `# [Epic 2] k6 부하테스트 (v1/v2 · 락 3전략 · Ramp Up · Saturation)`

| # | Story | 한 줄 | SP |
|---|---|---|---|
| 1 | PERF E2 S2-1 | k6 로컬 세팅 (설치 · `tests/k6/` 폴더 · 공통 유틸 `lib/http.js`) + 선택 docker-compose (k6+influxdb+grafana) | 0.5 |
| 2 | PERF E2 S2-2 | `search-v1-vs-v2.js` (vUser stages · Ramp Up · thresholds · JSON export) | 1 |
| 3 | PERF E2 S2-3 | `timesale-purchase.js` (STRATEGY 환경변수 락 스위칭 · 3전략 비교 실행) | 1 |
| 4 | PERF E2 S2-4 | 리포트 문서 3건 (`docs/perf/search-v1-vs-v2-report.md` · `timesale-lock-benchmark.md` (M6 결과 통합) · `docs/perf/README.md`) + README §Performance 섹션 | 0.5 |

**PR 종료 신호**:
- `k6 run tests/k6/search-v1-vs-v2.js` → JSON summary export · P50/P95/P99 3구획 (v1 · v2 Caffeine · v2 Redis) 표
- v2 대비 v1 P95 개선률 ≥ 50% (KPI 확인)
- 타임세일 3전략 벤치마크 결과 리포트 통합
- README §Performance 그래프 스크린샷

**의존**: PR#1 (Search E3 Redis 완주 → v2 Redis 부하 대상).

### Epic PR #3 — `PERF-E3-INDEXING` (EXPLAIN Before/After + ADR 016)

**Base 브랜치**: `feature/indexing-explain-report`

**SDD 위치**:
- 파일: `product-perf-lab.md`
- Epic: `# [Epic 3] 인덱싱 EXPLAIN Before/After + 커버링 인덱스`

| # | Story | 한 줄 | SP |
|---|---|---|---|
| 1 | PERF E3 S3-1 | 병목 쿼리 4+건 선정 (상품 검색 정렬 · 마이 주문 이력 · CS 조회 · 포인트 이력) + EXPLAIN Before 캡처 (type · key · rows · Extra) | 0.5 |
| 2 | PERF E3 S3-2 | 복합 인덱스 DDL (`idx_product_status_created` · `idx_order_item_user_created` · `idx_chat_room_customer_status` · `idx_point_history_user_created`) + Leftmost Prefix + README 명시 | 0.5 |
| 3 | PERF E3 S3-3 | After EXPLAIN 재실행 + Before/After 비교표 (`docs/perf/indexing-explain-report.md`) + 응답 시간 실측 개선률 | 1 |
| 4 | PERF E3 S3-4 | 커버링 인덱스 시연 (SELECT 컬럼 = 인덱스 컬럼 → `Extra: Using index`) + **ADR 016 발행** + README §Indexing 섹션 | 0.5 |

**PR 종료 신호**:
- 병목 쿼리 4건 Before: type=ALL · key=NULL · Full Scan 확인
- After: type=index/ref · key=idx_* · rows 감소 · Extra 개선
- 커버링 인덱스 최소 1건 `Extra: Using index` 획득
- ADR 016 (`docs/adr/016-indexing-strategy.md`) 발행

**의존**: PR#0 (M5 완주 · 5만+ 시딩 · 인덱스 효과 실측 전제).

**Reviewer 세션**: 5관점 발사. **특히 Sceptical Reviewer가 "옵티마이저가 인덱스를 실제로 사용하는가 (통계 갱신 필요 여부)"를 판정**.

### Epic PR #4 — `ADM-E2-DASHBOARD` (Admin Epic 2 이월 흡수)

**Base 브랜치**: `feature/admin-dashboard-observability`

**SDD 위치**:
- 파일: `product-admin-backoffice.md`
- Epic: `# [Epic 2] Dashboard 통합 + 세부 API + Micrometer 지표`

| # | Story | 한 줄 | SP |
|---|---|---|---|
| 1 | ADM E2 S2-1 | `AdminDashboardController.overview` + 6 카드 통합 + graceful degrade | 1 |
| 2 | ADM E2 S2-2 | 세부 5 API (popular-search · cs-stats · timesale-stats · coupon-usage · low-stock) | 1 |
| 3 | ADM E2 S2-3 | Micrometer 커스텀 지표 (재사용 4종 + 신규 `admin.dashboard.duration_seconds{card}`) | 0.5 |
| 4 | ADM E2 S2-4 | (선택) docker-compose Prometheus + Grafana + Dashboard JSON + README 스크린샷 | 1 |

**PR 종료 신호**: (M4 참조와 동일 · 시점만 이월 · 지표 원천 확보 후 흡수)

**의존**: M5·M6·M7 완주 (지표 원천).

---

## Story 카테고리별 합계

| 카테고리 | SDD Epic/Story | Story 수 | SP | 비중 |
| --- | --- | --- | --- | --- |
| Search Epic 3 (이월 흡수) | SRC E3 S3-1~S3-4 | 4 | 3 | 25% |
| Perf-Lab Epic 2 (k6) | PERF E2 S2-1~S2-4 | 4 | 3 | 25% |
| Perf-Lab Epic 3 (인덱싱) | PERF E3 S3-1~S3-4 | 4 | 2.5 | 21% |
| Admin Epic 2 (이월 흡수) | ADM E2 S2-1~S2-4 | 4 | 3.5 | 29% |
| **합계** | | **16** | **12** | 100% |

---

## 종료 신호 — "Perf Lab 완주 + 이월 Epic 흡수 + Ops Backend 21일 로드맵 완결"

본 M8 종료 시점에 다음이 모두 성립해야 한다. (10 신호 중 8개 이상 → 0.0.8v 동결 · 0.1.0v 릴리스 태그 후보)

- [ ] **머지 신호**: Epic PR 4개 모두 머지 (100%) or 최소 3개 머지 (75%)
- [ ] **Redis Remote 신호**: v2 캐시 매니저 Redis · Serializer LocalDateTime OK · Eviction 통합 테스트
- [ ] **k6 신호**: `search-v1-vs-v2.js` 실행 · P50/P95/P99 3구획 표 · v2 대비 v1 개선률 ≥ 50%
- [ ] **락 부하 리포트 신호**: 3전략 벤치마크 통합 리포트 (M6 결과 + k6 시나리오)
- [ ] **EXPLAIN 신호**: 병목 쿼리 4+건 Before/After 표 · Full Scan 제거 · rows 감소
- [ ] **커버링 인덱스 신호**: 최소 1건 `Extra: Using index` 획득
- [ ] **ADR 신호**: ADR 014 (Redis 전환) · ADR 016 (인덱싱) 발행
- [ ] **Dashboard 신호**: `/admin/dashboard/overview` 6 카드 반환 · 세부 5 API 노출 · Micrometer 지표 노출
- [ ] **README 신호**: §Performance · §Indexing · §Real-time · §Concurrency 4 섹션 완성 · 아키텍처 다이어그램 · 성능 그래프 스크린샷
- [ ] **Ops Backend 완결 신호**: 5 Product 모두 Epic 완주 · 캠프 요구사항 필수·도전 매트릭스 커버리지 100%

**미합격 처리**: 8 신호 미만 시 0.0.8.1v 패치 발행. Dashboard (PR#4)가 완주 미달 시 우선 흡수 (백오피스 정체성의 시각적 무대이므로 필수). Grafana (S2-4)는 완주 필수 아님 · v0.0.9v+ 이관 가능.

---

## 의존 chain

```
[선행: M4·M5·M6·M7 착지]
Admin E1 ✅ (M4)
Search E1·E2 · Perf E1 ✅ (M5)
Timesale E1·E2·E3 ✅ (M6)
CS Chat E1·E2 ✅ (M7)

[D1: 착수]
PR#1 (SRC-E3-REDIS-EVICT)  — M5 D15~16 이월 흡수
  SRC E3 S3-1 ~ S3-4
     │
     ▼
Redis Remote 완주 · Search Product 완결
     │
     ▼
PR#2 (PERF-E2-K6)
  PERF E2 S2-1 ~ S2-4 (v1/v2 Redis 부하 리포트)
     │
     ▼
PR#3 (PERF-E3-INDEXING)
  PERF E3 S3-1 ~ S3-4 (ADR 016)
     │
     ▼
Perf Lab 완주
     │
     ▼
PR#4 (ADM-E2-DASHBOARD)  — M4 D20 이월 흡수
  ADM E2 S2-1 ~ S2-4
     │
     ▼
Admin Product 완결 · Ops Backend 21일 로드맵 완결 · 0.1.0v 릴리스 태그 후보
```

**병렬 진입 가능 묶음**:
- **A** (D1 · 07-20 Mon): PR#1 착수 (SRC E3 S3-1 RedisConfig + Serializer) + S3-2 (v2 스위치)
- **B** (D2 · 07-21 Tue): PR#1 S3-3 (@CacheEvict + @CachePut) + S3-4 (통합 테스트 + ADR 014) 완주 → **PR#1 머지**
- **C** (D3 · 07-22 Wed): PR#2 착수 (PERF E2 S2-1 k6 세팅 + S2-2 search-v1-vs-v2.js) 진행
- **D** (D4 · 07-23 Thu): PR#2 S2-3 (timesale-purchase.js) + S2-4 (리포트 3건 + README) 완주 → **PR#2 머지**
- **E** (D5 · 07-24 Fri): PR#3 착수 (PERF E3 S3-1 Before EXPLAIN + S3-2 DDL) 진행
- **F** (D6 · 07-25 Sat): PR#3 S3-3 (After 비교표) + S3-4 (커버링 인덱스 + ADR 016) 완주 → **PR#3 머지**
- **G** (D7 · 07-26 Sun): PR#4 착수 (ADM E2 S2-1 Overview + S2-2 세부 5 API + S2-3 Micrometer + S2-4 선택 Grafana) 완주 → **PR#4 머지 + Reviewer 세션** · 0.0.8v 동결 · **Ops Backend 21일 로드맵 완결** · 0.1.0v 릴리스 태그 판정

---

## 작업 일정 (Day별 체크리스트)

D1 = 2026-07-20 (Mon). 종료 D7 = 2026-07-26 (Sun). 7일 안에 4 Epic PR.

| 일 | 날짜 | 잡힌 작업 |
| --- | --- | --- |
| D1 (월) | 07-20 | PR#1 착수 (**SRC E3 S3-1** RedisConfig + Serializer JavaTimeModule) + **S3-2** (v2 스위치) 진행 |
| D2 (화) | 07-21 | PR#1 **S3-3** (@CacheEvict + @CachePut + TTL) + **S3-4** (통합 테스트 + ADR 014) 완주 → **PR#1 머지 + Reviewer** |
| D3 (수) | 07-22 | PR#2 착수 (**PERF E2 S2-1** k6 세팅) + **S2-2** (search-v1-vs-v2.js Ramp Up) 진행 |
| D4 (목) | 07-23 | PR#2 **S2-3** (timesale-purchase.js STRATEGY) + **S2-4** (리포트 3건 + README §Performance) 완주 → **PR#2 머지 + Reviewer** |
| D5 (금) | 07-24 | PR#3 착수 (**PERF E3 S3-1** 병목 쿼리 4+건 EXPLAIN Before) + **S3-2** (복합 인덱스 DDL + README) 진행 |
| D6 (토) | 07-25 | PR#3 **S3-3** (After 재실행 + 비교표) + **S3-4** (커버링 인덱스 + ADR 016 + README §Indexing) 완주 → **PR#3 머지 + Reviewer 세션 (Sceptical 강조 · 옵티마이저 검증)** |
| D7 (일) | 07-26 | PR#4 착수 (**ADM E2 S2-1** Overview + **S2-2** 세부 5 API + **S2-3** Micrometer + **S2-4** 선택 Grafana docker-compose) 완주 → **PR#4 머지 + Reviewer 세션** · 통합 로컬 검증 · **Ops Backend 21일 로드맵 완결** · 0.0.8v 동결 · 0.1.0v 릴리스 태그 판정 · `outcome.md` · `review.md` (21일 전체 회고) · `performance.md` (k6 통합 리포트) |

---

## 리스크와 관찰 포인트

| 영역 | 리스크 | 관찰 포인트·완화 |
| --- | --- | --- |
| **7일 스판 · 4 Epic PR** | 12 SP · 4개 Reviewer 세션 · README 4 섹션 갱신 · 최종 마일스톤 부담 큼 | D2 진행률 확인 → 미달 시 Grafana (S2-4)만 선택으로 유예 · 나머지 필수 유지 |
| **k6 로컬 설치** | k6 CLI 설치 필요 · Windows chocolatey · WSL도 대안 | 설치 스크립트 README 안내 · 실행 실패 시 GH Actions 대체 검토 (M9 이관) |
| **옵티마이저 인덱스 미사용** | 통계 미갱신 · 데이터 분포 문제 시 EXPLAIN이 인덱스 안 씀 | `ANALYZE TABLE` 실행 · 통계 갱신 · FORCE INDEX 힌트 실험 (남용 금지 · 리포트에 언급) |
| **커버링 인덱스 실측 실패** | SELECT 컬럼 vs 인덱스 컬럼 불일치 시 `Using index` 안 걸림 | S3-4에 SELECT 컬럼 명시 · 인덱스 재설계 여지 |
| **Dashboard 지표 원천 부재 재확인** | M5·M6·M7 완주 후 진입이지만 일부 실패 시나리오 대응 | Overview `try-catch` 격리 유지 · 실패 지표 null 반환 · README에 명시 |
| **Grafana Dashboard JSON export** | 로컬 Grafana → JSON export 후 Git 저장 → 다른 개발자가 import 재현 | 표준 export 절차 문서화 · 스크린샷 첨부 · S2-4 선택 · 시간 부족 시 skip |
| **21일 로드맵 회고 문서** | outcome.md · review.md · performance.md 통합 작성 시간 | D7 마지막 · 회고 부담 대비 시나리오 (핵심 성과 표만 우선 · 상세 회고는 소급) |

---

## 다음 마일스톤 (M9 / 0.0.9v) 후보

**M9 / 0.0.9v (07-27 ~) — 0.1.0v 릴리스 후속 (Optional)**
- CI/CD 도전 (Docker + docker-compose + EC2 + SSM + Actions) — 캠프 도전 · 이미 배포 라인 일부 존재
- Redis Pub/Sub 다중 서버 (Chat 성능 개선) — 캠프 도전 · 별도 이슈
- 관리자 활동 감사 테이블
- SUPER_ADMIN 액션 확장

또는 v0.0.9v 스킵하고 0.1.0v 릴리스 태그 부착 후 v0.1.1v로 진입.

---

## Product 상태 전환 신호 (M8 종료 시)

- `in-progress/product-perf-lab.md` — **Epic 1·2·3 완주** 표기 · Perf Lab 완결
- `in-progress/product-search-cache.md` — **Epic 1·2·3 완주** 표기 · Search 완결
- `in-progress/product-admin-backoffice.md` — **Epic 1·2 완주** 표기 · Admin 완결
- `in-progress/product-timesale-concurrency.md` — 완주 유지 (M6)
- `in-progress/product-cs-chat.md` — 완주 유지 (M7)
- `fix/brainstorming/version/0.0.3v/issue-02·03·04·05·10·11·12·13.md` → **resolved**
- **ADR 014 · 016 발행 완료**
- **0.0.3v 백오피스 로드맵 완결 · 0.1.0v 릴리스 태그 부착 후보**

---

## brainstorming 트리거

본 M8 완료 후 `workflows/task/pes/brainstorming/0.1.0v/` 신설:
- CI/CD 도전 착수 스코프 (`brainstorming/0.1.0v/cicd-adoption-scope.md`)
- Redis Pub/Sub 이관 시나리오 (`brainstorming/0.1.0v/chat-scale-out.md`)
- 관리자 활동 감사 (`brainstorming/0.1.0v/admin-audit-log.md`)
- 실 사용자 노출 여부 결정 (`brainstorming/0.1.0v/real-user-exposure.md`) — 순수 백엔드 훈련장 원칙 유지 vs 실 노출

---

## 참고

- SDD: `../../pes/workspectrum/sdd/in-progress/product-perf-lab.md` · `product-search-cache.md` · `product-admin-backoffice.md`
- 대응 이슈: `../../fix/brainstorming/version/0.0.3v/issue-10·11·12·13.md`
- 상위 마일스톤: `../0.0.3v/milestone.md` — Ops Backend 21일 로드맵 (완결)
- 이전 마일스톤: `../0.0.7v/milestone.md` — M7 · CS Chat 완주
- **본 M8 완결 시**: Ops Backend 21일 로드맵 종료 · 5 Product 모두 완결 · 0.1.0v 릴리스 태그 후보
- 회의록: `C:\Users\user\.claude\plans\splendid-finding-feather.md`
- **주요 SDD 참조 비율**:
  - `product-perf-lab.md` **~46%** (Epic 2·3 · 8 Story · SP 5.5)
  - `product-search-cache.md` §Epic 3 **~25%** (4 Story · SP 3)
  - `product-admin-backoffice.md` §Epic 2 **~29%** (4 Story · SP 3.5)
