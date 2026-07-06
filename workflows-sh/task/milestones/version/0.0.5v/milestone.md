# M5 / 0.0.5v — Search & Cache · Week of 2026-07-08 ~ 2026-07-21 (D1 = 2026-07-08 Wed)

> **마일스톤의 역할**: M4가 07-07에 동결됐다 (ADM E1 컨텍스트·롤·JWT 완주 · Epic 2 Dashboard는 D20 대기). 본 M5는 Ops Backend 21일 로드맵의 **두 번째** 마일스톤으로 **product-search-cache 전 Epic 완주**를 목표로 한다. 검색 v1(QueryDSL LIKE 커서 페이징) → v2(Caffeine Local Cache) → Redis Remote 전환 + Cache Eviction의 3단계 캐시 전략과 인기 검색어 Redis ZSet 카운팅을 완결해 이력서 파괴력 있는 성능 비교 기반을 확보한다.
>
> 한 버전 = `version/0.0.Xv/` 폴더 하나. 본 버전(0.0.5v)에는:
> - `milestone.md` *(본 문서)* — 잡힌 양 + 일정 + 의존 + Epic PR 매트릭스
> - `outcome.md`·`review.md` — 완주 후 소급 작성
>
> `infra.md`·`performance.md`·`cost.md`는 스킵. 성능 실측은 M8 perf-lab이 담당.

**릴리스 대응**: 본 M5는 Ops Backend 21일 로드맵의 **두 번째** 마일스톤. 상위 M3(`../0.0.3v/milestone.md`) §Week별 로드맵 §Day 4~6, §Day 15~16 참조.

**SDD 원본**: `workflows/task/pes/workspectrum/sdd/in-progress/product-search-cache.md` — Product 2 검색·인기 검색어·캐시 (3 Epic · Story 12개 · SP ~8.5).

---

## 0.0.5v 스코프 결정 (M4 이후, 2026-07-08)

**M4 착지 결과 요약**:
- ✅ Admin Epic 1 완주 (컨텍스트 · Role · JWT claim · SecurityConfig · Seed)
- ✅ 관리자 인증·인가 확보 (후속 백오피스 이슈의 전제)
- ⏸️ Admin Epic 2 (Dashboard) D20 대기 (지표 원천 M5~M7 완주 필요)

**M5 축 결정 — product-search-cache 전 Epic 완주 + M8 시딩 병행**:

Search & Cache는 백오피스 첫 정면 도메인 확장. Ops Backend 정체성에서 "관리자가 상품 관리 시 검색"·"인기 검색어 트렌드"가 주요 시나리오. 캐시 3단계 전환(no → Caffeine → Redis)이 이력서 파괴력 소재. **주력 (~85%)**.

**부속 병행**: perf-lab Epic 1 (Datafaker 5만+ 시딩)이 검색 성능 실측의 원천 데이터이므로 M5 D3에 병행 진입. **경량 (~15%)**.

**Epic 단위 PR 4건 예정 (본주 목표)**:

| Epic PR | 대응 | 예상 SP | 종료 목표 |
|---|---|---|---|
| PR#0 (PERF-E1-SEED) | Perf-Lab Epic 1 Story 1-1~1-4 (Datafaker + JDBC Batch Insert · 5만+ 상품 · 1만+ 유저 · 10만+ 주문 · 5만+ 검색로그) | 3 | D3 |
| PR#1 (SRC-E1-V1) | Search Epic 1 Story 1-1~1-4 (ProductSearchQuery/Projection · QueryDSL 동적 · LIKE · 커서 페이징 · count 분리 · v1 컨트롤러) | 2.5 | D4 |
| PR#2 (SRC-E2-CAFFEINE-ZSET) | Search Epic 2 Story 2-1~2-4 (Caffeine `@Cacheable` · Redis ZSet 인기 검색어 · dedup SET NX · recordSearch 훅) | 3 | D6 |
| PR#3 (SRC-E3-REDIS-EVICT) | Search Epic 3 Story 3-1~3-4 (RedisCacheManager · Serializer · @CacheEvict · @CachePut · TTL 정책 · ADR 014) | 3 | D16 |

**Total: 4 Epic PR · 총 ~11.5 SP** (M4 대비 +90% 상승. PERF-E1 시딩 병행 · Search 3 Epic 완주가 축).

**본 버전 제외 사유**:
- **Search Epic 3 조기 완주** — Day 15~16 배치. Caffeine 초기(Epic 2) 관찰 후 Redis 전환 서사가 자연스러움.
- **관리자용 검색 필터 확장** (DELETED 포함 상태) — Product SDD §Out of Scope 준수. 초기 SALE·SOLD_OUT만.
- **자동완성 (검색어 추천)** — Product SDD §Out of Scope.
- **MySQL FULLTEXT / Elasticsearch** — Out of Scope (초기 규모).

---

## 진행 중 Product 잔여 인벤토리 (Before/After — M5 진입 시 vs 종료 후 예상)

| Product | 총 Story | M5 진입 시 완료 | M5 진입 시 잔여 | M5 대상 | **M5 종료 후 예상 잔여** | **해결율** |
| --- | --- | --- | --- | --- | --- | --- |
| Admin Backoffice | 8 | 4 (E1) | 4 (E2 · D20 대기) | — | 4 | 0% (M4 D20 대기) |
| **Search & Cache** (`product-search-cache.md` · 3 Epic) | **~14** | 0 | 14 | **12 Story (E1·E2·E3)** | **~2** | **86%** ↑ |
| Timesale Concurrency | ~15 | 0 | 15 | — | 15 | 0% (M6) |
| CS Chat | ~8 | 0 | 8 | — | 8 | 0% (M7) |
| **Perf Lab** (`product-perf-lab.md` · 3 Epic) | **~13** | 0 | 13 | **4 Story (E1 시딩)** | **~9** | **31%** ↑ (E1 완주 부분) |
| **합계** | **~58** | 4 | ~54 | **16 Story · 4 Epic PR · 11.5 SP** | **~38** | **30%** ↑ |

### 📊 M5 예상 성과 카드

- **총 해결 대상**: 16 Story (전체 잔여의 **30% 소진**)
- **완주 예상 SDD Product**:
  - `product-search-cache.md` Epic 1·2·3 — **완주 100%** (Search Product 완결)
  - `product-perf-lab.md` Epic 1 — 완주 (시딩 확보)
- **완주 예상 산출물**:
  - `nbc.c1oud_mall.product.presentation.ProductSearchController` v1·v2 병존
  - QueryDSL 동적 검색 (`ProductJpaRepositoryImpl.search`) + count 쿼리 분리 + Projection
  - Caffeine `@EnableCaching` + CaffeineCacheManager (TTL 60초 · maximumSize 1000)
  - Redis Lettuce ZSet 인기 검색어 (ZINCRBY/ZREVRANGE + dedup SET NX)
  - `/api/v1/search/popular?scope=hourly\|daily\|weekly&limit=`
  - RedisCacheManager + Serializer (String+GenericJackson2Json+JavaTimeModule)
  - @CacheEvict allEntries · @CachePut on Product CRUD
  - Datafaker 5만+ 시딩 (Product·User·Order·SearchLog)
  - **ADR 014 발행** (v2 Local → Redis Remote 전환 근거)
  - `ErrorCode.SRC001~003` · `POP001~002` 등록
- **M5 종료 후 남는 것**:
  - Timesale Concurrency (M6 · 15 Story · 이력서 파괴력 최상)
  - CS Chat (M7 · 8 Story)
  - Perf Lab E2·E3 (M8 · k6 + 인덱싱)
  - Admin Dashboard (E2 D20 대기)

---

## Epic PR 매트릭스 (본주 잡힌 양)

### Epic PR #0 — `PERF-E1-SEED` (Datafaker + JDBC Batch 5만+ 시딩)

**Base 브랜치**: `feature/dummy-seed-50k-datafaker`

**SDD 위치**:
- 파일: `workflows/task/pes/workspectrum/sdd/in-progress/product-perf-lab.md`
- Epic: `# [Epic 1] Datafaker + JDBC Batch 시딩`
- Story 범위: `## [Story 1-1]` ~ `## [Story 1-4]`
- 상위 이슈 원천: `workflows/task/fix/brainstorming/version/0.0.3v/issue-02-dummy-seed-50k-datafaker.md`

| # | Story | 한 줄 | SP |
|---|---|---|---|
| 1 | PERF E1 S1-1 | `SeedProperties` + `@Profile("dev-large")` + Datafaker 의존성 (`net.datafaker:datafaker`) + application-dev-large.yml | 0.5 |
| 2 | PERF E1 S1-2 | `JdbcBatchInserter` 유틸 (1000건 커밋 단위) + `ProductSeeder.seed(50000)` (name·description·price 랜덤) | 1 |
| 3 | PERF E1 S1-3 | `UserSeeder.seed(10000)` + `OrderSeeder.seed(100000)` + `SearchLogSeeder.seed(50000)` | 1 |
| 4 | PERF E1 S1-4 | `SeedRunner implements ApplicationRunner` + 실행 시간 로깅 (`SEED_START`·`SEED_PROGRESS`·`SEED_COMPLETE`) | 0.5 |

**PR 종료 신호**:
- `dev-large` 프로파일 부팅 시 5만 상품 + 1만 유저 + 10만 주문 + 5만 검색로그 시딩 완료
- 총 실행 시간 ≤ 60초 (로그 검증 · `SEED_COMPLETE` 마커)
- `dev` 프로파일에서 시딩 실행 안 됨 (`@Profile` 검증)
- OOM 없음 · GC 로그 정상

**병렬 진입 조건**: 없음 (Search v1 성능 실측의 원천 · 선행 완주 필요).

### Epic PR #1 — `SRC-E1-V1` (검색 v1 QueryDSL 동적 · LIKE · 커서 페이징)

**Base 브랜치**: `feature/search-v1-querydsl-like-cursor`

**SDD 위치**:
- Epic: `# [Epic 1] 검색 v1 (QueryDSL 동적 · LIKE · 커서 페이징 · count 분리)`
- Story 범위: `## [Story 1-1]` ~ `## [Story 1-4]`
- 상위 이슈 원천: `workflows/task/fix/brainstorming/version/0.0.3v/issue-03-search-v1-querydsl-like-cursor.md`

| # | Story | 한 줄 | SP |
|---|---|---|---|
| 1 | SRC E1 S1-1 | `ProductSearchQuery` (application record) + `ProductSearchProjection` (infrastructure record) + Bean Validation (`@AssertTrue price 범위`) | 0.5 |
| 2 | SRC E1 S1-2 | `ProductJpaRepositoryImpl.search` (QueryDSL BooleanBuilder 동적 + count 쿼리 분리 + `Projections.constructor`) | 1 |
| 3 | SRC E1 S1-3 | `ProductSearchController.v1` (`/api/v1/products/search`) + 정렬 화이트리스트 검증 + `ErrorCode.SRC001~003` | 0.5 |
| 4 | SRC E1 S1-4 | 통합 테스트 (5만 데이터 위 각 조건 조합 · SQL 로그 LIKE 확인 · count 쿼리 별도 실행 확인 · P95 기록) | 0.5 |

**PR 종료 신호**:
- `GET /api/v1/products/search?keyword=맥북&minPrice=1000000&maxPrice=3000000&sort=price_asc` → 200 · Page<ProductSearchResponse>
- SQL 로그에 `LIKE '%맥북%'` 실체 확인
- count 쿼리 별도 실행 로그 확인
- (엣지) minPrice > maxPrice → 400 SRC001 · sort=invalid → 400 SRC002 · size=200 → 400 SRC003
- 5만 데이터 위 통합 테스트 P95 ≤ 500ms (참고 기록 · 정식 기준은 M8 k6)

**의존**: PR#0 (5만 시딩) 완주 필수.

### Epic PR #2 — `SRC-E2-CAFFEINE-ZSET` (검색 v2 Caffeine + 인기 검색어 ZSet)

**Base 브랜치**: `feature/search-v2-caffeine-popular-redis`

**SDD 위치**:
- Epic: `# [Epic 2] 검색 v2 (Caffeine Local Cache) + 인기 검색어 (Redis ZSet)`
- Story 범위: `## [Story 2-1]` ~ `## [Story 2-4]`
- 상위 이슈 원천: `issue-04-search-v2-caffeine-local-cache.md` · `issue-05-popular-search-redis-zset.md`

| # | Story | 한 줄 | SP |
|---|---|---|---|
| 1 | SRC E2 S2-1 | `spring-boot-starter-cache` + `caffeine` 의존성 + `CacheConfig @EnableCaching` + CaffeineCacheManager (TTL 60s · maximumSize 1000) + `ProductSearchController.v2 @Cacheable` | 1 |
| 2 | SRC E2 S2-2 | Redis Lettuce 설정 확인 (docker-compose) + `PopularSearchRepository` (ZINCRBY/ZREVRANGE + SET NX dedup 60s) | 1 |
| 3 | SRC E2 S2-3 | `PopularSearchService.recordSearch(userId, keyword)` + 검색 v1/v2 진입점에 훅 (try-catch 격리) | 0.5 |
| 4 | SRC E2 S2-4 | `/api/v1/search/popular?scope=hourly\|daily\|weekly&limit=` + `ErrorCode.POP001~002` | 0.5 |

**PR 종료 신호**:
- v2 첫 호출 = DB · 이후 2회 = Caffeine 히트 (`cache_hit=true` 지표)
- 동일 (userId, keyword) 60초 내 재검색 시 ZINCRBY skip (dedup SET NX 검증)
- daily scope에 keyword 3개 → `GET /search/popular?scope=daily&limit=2` → 상위 2개 반환
- 통합 테스트 (Caffeine 히트/미스 · dedup · scope별 조회)

**의존**: PR#1 완주 (검색 v1 진입점 사용).

### Epic PR #3 — `SRC-E3-REDIS-EVICT` (Redis Remote 전환 + Cache Eviction + ADR 014)

**Base 브랜치**: `feature/cache-remote-redis-eviction`

**SDD 위치**:
- Epic: `# [Epic 3] Redis Remote 전환 + Cache Eviction`
- Story 범위: `## [Story 3-1]` ~ `## [Story 3-4]`
- 상위 이슈 원천: `issue-10-cache-remote-redis-eviction.md`

| # | Story | 한 줄 | SP |
|---|---|---|---|
| 1 | SRC E3 S3-1 | `spring-boot-starter-data-redis` + `RedisConfig` + `RedisTemplate<String, Object>` (Key StringRedisSerializer · Value GenericJackson2JsonRedisSerializer + JavaTimeModule) | 1 |
| 2 | SRC E3 S3-2 | `RedisCacheManager` 등록 · v2 스위치 (`@Cacheable(cacheManager="redisCacheManager")`) · Caffeine 캐시 매니저 병존 유지 | 0.5 |
| 3 | SRC E3 S3-3 | 관리자 상품 CRUD `@CacheEvict(value="productSearch", allEntries=true)` + 상품 상세 `@CachePut(value="productDetail", key="'detail:'+#result.id")` + TTL (검색 5m · 상세 30m) | 1 |
| 4 | SRC E3 S3-4 | 통합 테스트 (LocalDateTime 직렬화·역직렬화 · 상품 수정 → 다음 검색 새 데이터) + **ADR 014 발행** | 0.5 |

**PR 종료 신호**:
- v2 캐시 매니저가 RedisCacheManager 사용 · Prometheus/logs 검증
- `LocalDateTime` 포함 상품 응답 정상 역직렬화
- 관리자 상품 X 수정 → 다음 v2 검색 = 캐시 미스 · 새 데이터 반환 (통합 테스트)
- `docs/adr/014-cache-local-to-remote.md` §배경~§검증 완결

**의존**: PR#2 완주 (Caffeine v2가 전환 대상).

**Reviewer 세션**: 5관점 발사. **특히 Architecture Reviewer가 "Serializer 구성이 LocalDateTime 포함 객체에 안전한가"를 검증**.

---

## Story 카테고리별 합계

| 카테고리 | SDD Epic/Story | Story 수 | SP | 비중 |
| --- | --- | --- | --- | --- |
| Perf-Lab Epic 1 (시딩) | PERF E1 S1-1~S1-4 | 4 | 3 | 26% |
| Search Epic 1 (v1) | SRC E1 S1-1~S1-4 | 4 | 2.5 | 22% |
| Search Epic 2 (Caffeine + ZSet) | SRC E2 S2-1~S2-4 | 4 | 3 | 26% |
| Search Epic 3 (Redis Remote + Eviction) | SRC E3 S3-1~S3-4 | 4 | 3 | 26% |
| **합계** | | **16** | **11.5** | 100% |

---

## 종료 신호 — "Search Product 완주 + 5만+ 시딩 확보"

본 M5 종료 시점에 다음이 모두 성립해야 한다. (8 신호 중 6개 이상 → 0.0.5v 동결)

- [ ] **머지 신호**: Epic PR 4개 중 최소 3개 머지 (75%)
- [ ] **시딩 신호**: `dev-large` 부팅 시 5만+ 시딩 완료 · 60초 이내
- [ ] **v1 신호**: `/api/v1/products/search` 각 조건 조합 · SQL LIKE 실체 · count 쿼리 분리 확인
- [ ] **v2 Caffeine 신호**: v2 재요청 시 캐시 히트 (`cache_hit=true`) · TTL 60초 반영
- [ ] **인기 검색어 신호**: `/search/popular?scope=daily&limit=10` 상위 N · dedup SET NX 검증
- [ ] **v2 Redis 신호**: v2 캐시 매니저가 Redis 사용 · Serializer LocalDateTime 직렬화 OK
- [ ] **Eviction 신호**: 상품 수정 → 다음 검색 새 데이터 (통합 테스트)
- [ ] **ADR 신호**: ADR 014 발행 (v2 Local → Redis Remote 근거)

**미합격 처리**: 6 신호 미만 시 0.0.5.1v 패치 발행 → M6 진입 지연. PR#3 (Redis Remote)이 미달일 경우 Caffeine 유지 상태로 M6 진입 · M8에 재진입.

---

## 의존 chain

```
[선행: M4 착지]
Admin 컨텍스트 · 롤 · JWT ✅ 완료

[D1: 병렬 착수]
PR#0 (PERF-E1-SEED)  단독 진입 (Search v1 실측의 전제)
  PERF E1 S1-1 ~ S1-4
     │
     ▼
5만+ 시딩 완주 (D3)
     │
     ▼
PR#1 (SRC-E1-V1)
  SRC E1 S1-1 ~ S1-4 (D4)
     │
     ▼
PR#2 (SRC-E2-CAFFEINE-ZSET)
  SRC E2 S2-1 ~ S2-4 (D5-D6)
     │
     ▼
(M6 · M7 진행 · D7-D14)
     │
     ▼
PR#3 (SRC-E3-REDIS-EVICT)  진입 (D15-D16)
  SRC E3 S3-1 ~ S3-4
     │
     ▼
Search Product 완주
```

**병렬 진입 가능 묶음**:
- **A** (D1 · 07-08 Wed): PR#0 착수 (PERF E1 S1-1 SeedProperties + Datafaker)
- **B** (D2 · 07-09 Thu): PR#0 S1-2 (JdbcBatchInserter · ProductSeeder) · S1-3 (User·Order·SearchLog Seeder) 진행
- **C** (D3 · 07-10 Fri): PR#0 S1-4 (SeedRunner + 로깅) 완주 → **PR#0 머지** · PR#1 착수 (SRC E1 S1-1 record DTO)
- **D** (D4 · 07-11 Sat): PR#1 S1-2·S1-3·S1-4 완주 → **PR#1 머지 + Reviewer 세션**
- **E** (D5 · 07-12 Sun): PR#2 착수 (SRC E2 S2-1 Caffeine 설정) + S2-2 (PopularSearchRepository) 진행
- **F** (D6 · 07-13 Mon): PR#2 S2-3 (recordSearch 훅) + S2-4 (인기 검색어 API) 완주 → **PR#2 머지 + Reviewer 세션**
- **D7~D14**: M6 (Timesale) · M7 (CS Chat) 진행 · 본 마일스톤 대상 없음
- **G** (D15 · 07-20 Mon): PR#3 착수 (SRC E3 S3-1 RedisConfig + Serializer)
- **H** (D16 · 07-21 Tue): PR#3 S3-2 (v2 스위치) + S3-3 (Eviction) + S3-4 (통합 테스트 + ADR 014) 완주 → **PR#3 머지 + Reviewer 세션** · 0.0.5v 동결

---

## 작업 일정 (Day별 체크리스트)

| 일 | 날짜 | 잡힌 작업 |
| --- | --- | --- |
| D1 (수) | 07-08 | PR#0 착수 (**PERF E1 S1-1** SeedProperties + Datafaker) |
| D2 (목) | 07-09 | PR#0 **S1-2** (JdbcBatchInserter + ProductSeeder) + **S1-3** (User·Order·SearchLog Seeder) 진행 |
| D3 (금) | 07-10 | PR#0 **S1-4** (SeedRunner) 완주 → **PR#0 머지** · PR#1 착수 (**SRC E1 S1-1**) |
| D4 (토) | 07-11 | PR#1 **S1-2** (QueryDSL + count) + **S1-3** (Controller v1) + **S1-4** (통합 테스트) 완주 → **PR#1 머지** |
| D5 (일) | 07-12 | PR#2 착수 (**SRC E2 S2-1** Caffeine 설정) + **S2-2** (PopularSearchRepository) 진행 |
| D6 (월) | 07-13 | PR#2 **S2-3** (recordSearch 훅) + **S2-4** (인기 검색어 API + POP001~002) 완주 → **PR#2 머지** |
| D7~D14 | 07-14~07-19 | (M6 · M7 진행 · 본 마일스톤 대상 없음) |
| D15 (월) | 07-20 | PR#3 착수 (**SRC E3 S3-1** RedisConfig + Serializer JavaTimeModule) |
| D16 (화) | 07-21 | PR#3 **S3-2** (v2 스위치) + **S3-3** (@CacheEvict + @CachePut + TTL) + **S3-4** (통합 테스트 + ADR 014) 완주 → **PR#3 머지** · 0.0.5v 동결 · `outcome.md` 골격 |

---

## 리스크와 관찰 포인트

| 영역 | 리스크 | 관찰 포인트·완화 |
| --- | --- | --- |
| 시딩 OOM | 5만 상품 + 10만 주문 · JPA 캐시 폭발 리스크 | JDBC Batch (JPA X) · 1000건 커밋 · `@Transactional` 범위 조절 · GC 로그 확인 |
| Caffeine TTL 60초 vs Redis TTL 5분 이질 | v2 → Redis 전환 시 TTL 급증에 놀랄 리스크 · UX 관찰 필요 | ADR 014에 TTL 정책 근거 명시 · 통합 테스트로 검증 |
| Redis 연결 실패 시 v2 다운 | Redis docker 종료 시 v2 검색 500 리스크 | try-catch로 캐시 실패 시 v1으로 fallback · 로그 마커 `CACHE_REDIS_FAILED` |
| Serializer JavaTimeModule 미등록 | `LocalDateTime` 포함 객체 직렬화 실패 | S3-1 통합 테스트에 LocalDateTime 명시 · Jackson 설정 확인 |
| Cache Eviction allEntries 스템피드 | 관리자 수정 트래픽 많을 때 캐시 전체 삭제로 DB 부하 | 초기 채택 · 문제 시 태그 기반 evict로 재설계 (Product SDD §Open Question Q3) |
| 인기 검색어 dedup 정책 60초 | 정책 결정 근거 부족 시 인기 카운팅 부정확 리스크 | ADR 후속 필요 시 · `application.yml` externalize |
| recordSearch 동기 호출 지연 | 검색 P95에 인기 검색어 등록 비용 포함 | 초기 동기 · try-catch 격리 · P95 지연 관찰 후 @Async 전환 (Product SDD §Open Question Q1) |

---

## 다음 마일스톤 (M6 / 0.0.6v) 후보

**M6 / 0.0.6v (07-13 ~ 07-16) — Timesale Concurrency 전 Epic 완주 (핵심)**
- **Timesale Epic 1** — TimeSaleEvent 도메인 · Admin CRUD · 스케줄러 상태 전이
- **Timesale Epic 2** — **락 3전략 구현 + 실패 테스트 + 벤치마크 리포트 (이력서 파괴력 최상)**
- **Timesale Epic 3** — 선착순 쿠폰 발급 · LockService 재사용

**Epic PR 예상 3건** (총 ~12.5 SP · 이력서 파괴력 최상).

---

## Product 상태 전환 신호 (M5 종료 시)

- `in-progress/product-search-cache.md` — **Epic 1·2·3 완주** 표기
- `in-progress/product-perf-lab.md` — **Epic 1 완주** 표기 (Epic 2·3은 M8 대기)
- `fix/brainstorming/version/0.0.3v/issue-02·03·04·05·10.md` → **resolved**
- **ADR 014 발행 완료** (v2 Local → Redis Remote)

---

## 참고

- SDD: `../../pes/workspectrum/sdd/in-progress/product-search-cache.md` · `product-perf-lab.md`
- 대응 이슈: `../../fix/brainstorming/version/0.0.3v/issue-02·03·04·05·10.md`
- 상위 마일스톤: `../0.0.3v/milestone.md`
- 이전 마일스톤: `../0.0.4v/milestone.md` — M4 · Admin 완주
- 회의록: `C:\Users\user\.claude\plans\splendid-finding-feather.md`
- **주요 SDD 참조 비율**:
  - `product-search-cache.md` **~75%** (Epic 1·2·3 · 12 Story · SP 8.5)
  - `product-perf-lab.md` **~25%** (Epic 1 · 4 Story · SP 3)
