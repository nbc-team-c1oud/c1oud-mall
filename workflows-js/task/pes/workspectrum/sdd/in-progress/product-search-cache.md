# [Product 2] 검색 · 인기 검색어 · 캐시 계층 (v1 QueryDSL / v2 Local / Remote+Eviction)

## Product Vision
> 5만+ 상품에서 관리자·구매자가 원하는 결과를 빠르게 찾을 수 있고, 로컬(Caffeine)과 원격(Redis) 캐시를 단계적으로 적용해 **v1 대비 v2 응답 시간이 정량적으로 얼마나 개선되는지** 부하 리포트로 증명한다.

## 배경 및 문제

- 현재 상황 (As-Is)
  - `nbc.c1oud_mall.product.*`에 QueryDSL 커스텀 리포지토리 골격 존재 (M1 완료 · `ProductJpaRepositoryCustom` · `ProductJpaRepositoryImpl` · `ProductSearchCondition`)
  - 32건 dummy 상품만 존재 · 성능 실측 불가능
  - 캐시 계층 없음 · Redis 미도입
  - 인기 검색어·트렌드 대시보드 없음
- 발생하는 문제
  - 캠프 요구사항: LIKE 검색 v1 + Caffeine v2 병존 · 인기 검색어 Redis ZSet · v2 → Remote Redis + Cache Eviction · 5만+ 부하 실측
  - 관리자 대시보드(이슈 13)에 인기 검색어 실시간 표시 필요
  - 검색어가 반복되는 read-heavy 특성 → 캐시 계층 유무에 따른 성능 차이가 이력서 소재
- 왜 지금 해결해야 하는가
  - Week 1의 인프라 축 — 이후 이슈 06/07/08(이벤트·락·쿠폰)와 이슈 11(k6 부하)의 무대
  - 캠프 요구사항 다수를 커버 (v1/v2/인기 검색어/Redis Remote/Cache Eviction)

## 목표 (To-Be)

- `nbc.c1oud_mall.product.presentation.ProductSearchController` — v1/v2 두 컨트롤러 병존
- `ProductSearchQuery` (application record) + `ProductSearchProjection` (infrastructure record) — QueryDSL Projection
- v1: 캐시 없음 · QueryDSL BooleanBuilder 동적 · LIKE + Paging + **count 쿼리 분리**
- v2 (초기): Caffeine `@Cacheable(cacheManager="caffeineCacheManager")` · TTL 60초 · maximumSize 1000
- v2 (도전 이후): Redis `@Cacheable(cacheManager="redisCacheManager")` · Serializer(String+GenericJackson2Json) + JavaTimeModule · TTL 5분
- 인기 검색어: Redis ZSet `search:popular:{scope}:{date}` · ZINCRBY(검색 시 카운트) · ZREVRANGE(상위 N) · 중복 카운팅 방지 SET NX 60초
- Cache Eviction: 관리자 상품 CRUD 시 `@CacheEvict(allEntries=true)` · 상품 상세 `@CachePut`
- ErrorCode `SRC001~003` (검색) · `POP001~002` (인기 검색어)

## 설계 결정 (Design Decisions)

- **v1/v2 병존 (v1 삭제 안 함)** — 캠프 요구사항 명시. v1이 v2 대비 얼마나 느린지가 이력서 핵심 증거
- **v2 초기 = Caffeine Local · 이후 Redis Remote 전환** — Local의 한계(Scale-out 시 서버 간 미공유)를 실감한 뒤 Remote 이유가 명확해짐
- **인기 검색어 = Redis ZSet** — ZINCRBY/ZREVRANGE가 O(log n)로 상위 N 조회 최적
- **중복 카운팅 방지 = SET NX 60초** — 동일 사용자·동일 키워드가 분당 1회만 카운트
- **집계 기간 3구획 (실시간 hourly · 일별 daily · 주간 weekly)** — 대시보드 노출 다양성 · TTL로 자연 만료
- **`recordSearch` 훅은 트랜잭션 밖 · 비동기 검토** — 검색 API가 인기 검색어 등록 실패로 지연되지 않도록 (초기: 동기 · 필요 시 @Async)
- **count 쿼리 분리** — content와 다른 최적화 쿼리로 실행 (Spring Data `PageImpl(content, pageable, total)` 조립)

## 대안 검토 (Alternatives Considered)

### 캐시 백엔드

**Option A — Caffeine만**
- 장점: 단순 · 로컬 메모리 · 초저지연
- 거부 이유: Scale-out 시 서버 간 데이터 공유 불가

**Option B — Redis만**
- 장점: 중앙 저장소 · Scale-out 대응
- 거부 이유: 캠프 요구사항이 "v2 초기 Local → 이후 Redis 전환" 서사 요구

**Option C (선택) — Caffeine → Redis 단계 전환**
- 비용: 두 캐시 매니저 병존 · 스위칭 로직 필요
- 보상: v1(no) → v2 Caffeine → v2 Redis 3단계 성능 비교 리포트 가능 (이슈 11)

### 인기 검색어 저장

**Option A (선택) — Redis ZSet + ZINCRBY/ZREVRANGE**
- 비용: Redis 의존
- 보상: 상위 N 조회 O(log n) · Redis 자체 TTL로 자연 만료

**Option B — DB 테이블 + 배치 집계**
- 거부 이유: 실시간 인기 검색어 반영 지연 · DB 부하

**Option C — 애플리케이션 메모리 카운터**
- 거부 이유: Scale-out 미대응 · 서버 재시작 시 소실

### 검색 성능 저하 시 대응 (초기 스코프 외)

- MySQL FULLTEXT — 향후 상품 10k+ 시 검토 (초기: LIKE 충분 · 캐시로 커버)
- Elasticsearch — 오버킬 (초기 규모 · Scale-out 시 v0.0.5v+)

## 전체 아키텍처 (High-Level Architecture)

### 컴포넌트 배치
```
presentation ──▶ application ──▶ domain ◀── infrastructure
  ProductSearch    ProductSearch    Product     ProductJpa
  Controller       Service          Entity      RepositoryCustom
  (v1 · v2)        PopularSearch    (재활용)     QueryDSL Impl
                   Service                      Projection

                   @Cacheable                   CaffeineCacheManager
                                                RedisCacheManager
                                                RedisTemplate
                                                PopularSearchRepository
                                                  (ZINCRBY/ZREVRANGE)
```

### 핵심 플로우

**1. 검색 v1 (원본 · 캐시 없음)**
```
Client → ProductSearchController.v1.search(query, pageable)
       → ProductSearchService.search(query, pageable)
       → ProductJpaRepositoryImpl.search (QueryDSL BooleanBuilder)
       → PopularSearchService.recordSearch(userId, keyword)  [트랜잭션 밖]
       ← Page<ProductSearchResponse>
```

**2. 검색 v2 (Caffeine 초기 → Redis 이후)**
```
Client → ProductSearchController.v2.search
       → ProductSearchService.searchV2 @Cacheable(value="productSearch", key="'search:'+#query.hashCode()+':'+#pageable.pageNumber")
                                        cacheManager="caffeineCacheManager" (초기)
                                        or "redisCacheManager" (Story 4-1 이후)
       ↓ [캐시 미스]
       → ProductJpaRepositoryImpl.search
       → PopularSearchService.recordSearch
       ← Page<ProductSearchResponse> (캐시 저장)
```

**3. 인기 검색어 카운팅 · 조회**
```
검색 발생:
recordSearch(userId, keyword):
  if setnx("search:dedup:" + userId + ":" + keyword, "1", 60s) succeeded:
    ZINCRBY search:popular:daily:{yyyy-MM-dd} 1 {keyword}
    ZINCRBY search:popular:hourly:{yyyy-MM-dd-HH} 1 {keyword}

조회:
GET /api/v1/search/popular?scope=daily&limit=10
  → ZREVRANGE search:popular:daily:{yyyy-MM-dd} 0 9 WITHSCORES
  → List<PopularKeywordResponse>
```

**4. Cache Eviction (관리자 상품 수정)**
```
Admin → ProductAdminController.update(productId, request)
      → @CacheEvict(value="productSearch", allEntries=true)
      → ProductService.update
      → 다음 검색 = 캐시 미스 → 최신 데이터 반영
```

### Out-of-Process 의존
- **Redis (Lettuce)**: v2 캐시 (Story 4-1 이후) · 인기 검색어 ZSet · 중복 카운팅 dedup SET
- **RDS (MySQL) / H2 (dev)**: Product 원본 · QueryDSL 검색

## 실패 모드 / 운영 관측 (Failure Modes & Observability)

### 실패 시나리오와 응답
| 시나리오 | ErrorCode | HTTP | 클라이언트 권장 동작 |
| --- | --- | --- | --- |
| `minPrice > maxPrice` | `SRC001` | 400 | 필터 수정 |
| 정렬 화이트리스트 벗어남 | `SRC002` | 400 | 기본 정렬로 재시도 |
| `size > 100` | `SRC003` | 400 | size 감소 |
| Redis 연결 실패 (v2 · 인기 검색어) | (없음) | 200 | 로그 남기고 캐시 없는 결과 반환 (graceful degrade) |
| 인기 검색어 scope 무효 | `POP001` | 400 | scope 재선택 |

### 로깅 정책
- **항상 기록**: `requestId` · `keyword` (마스킹 X · 인기 검색어 데이터 특성) · `sort` · `cacheHit=true|false` · `duration_ms`
- **debug**: QueryDSL 생성 SQL · @Cacheable 진입 여부 · Redis 명령 실패 상세

### 관측 지표
- `product.search.total{version=v1|v2, cache_hit=true|false}` — counter
- `product.search.duration_seconds{version}` — histogram (P50/P95/P99)
- `search.popular.record.total{result=success|dedup_skip}` — counter
- `search.popular.query.total{scope=hourly|daily|weekly}` — counter
- `cache.eviction.total{trigger=product_update|product_create|product_delete}` — counter

## 롤아웃 / 마이그레이션 (Rollout)

### 전제
- Week 1 Day 4~6 진입 · 이슈 01(Admin) · 이슈 02(시딩) 완료 상태
- Redis docker-compose 로컬 (Story 1-3 · Story 4-1의 준비)

### Product 의존성
- 선행: 이슈 01 (Admin) · 이슈 02 (5만+ 시딩)
- 후행: 이슈 11 (k6 부하테스트) · 이슈 13 (관리자 대시보드 인기 검색어)

### Epic·Story 의존성 그래프
```
Epic 1 (v1 QueryDSL) ──► Epic 2 (v2 Caffeine + 인기 검색어) ──► Epic 3 (Redis Remote + Eviction)
```

### 환경별 설정 분기
| 항목 | dev (H2 + local Redis) | prod (RDS + ElastiCache) |
| --- | --- | --- |
| DataSource | H2 MODE=MySQL | RDS |
| Redis | docker-compose | ElastiCache |
| 캐시 매니저 스위치 | `c1oudmall.cache.v2=caffeine` (초기) · `redis` (Story 4-1 이후) | 동일 |
| TTL (v2) | 60s (Caffeine) · 5m (Redis) | 5m (Redis) |
| 인기 검색어 TTL | daily 24h · hourly 1h · weekly 7d | 동일 |

## 성공 지표 (KPI)
| 지표 | 목표 값 | 측정 방법 |
| --- | --- | --- |
| v1 vs v2 P95 응답 시간 개선률 | ≥ **50%** | k6 리포트 (v2 P95 / v1 P95 - 1) |
| 캐시 히트율 (v2) | ≥ **70%** (반복 검색어 시나리오) | Micrometer `product.search.total{cache_hit=true} / total` |
| Cache Eviction 정확성 | **100%** | 관리자 수정 후 다음 검색이 새 데이터 반환 (통합 테스트) |
| 인기 검색어 상위 10 조회 | ≤ **10ms P95** | Micrometer `search.popular.query.duration_seconds{quantile=0.95}` |
| 인기 검색어 중복 카운팅 방지 | **100%** (같은 사용자 60초 내 재검색 시 카운트 안 됨) | 단위 테스트 · SET NX 로그 |

## Scope

**In Scope**:
- 검색 v1 (QueryDSL LIKE · 동적 · 커서 페이징 · count 분리)
- 검색 v2 (Caffeine 초기 → Redis 이후 전환)
- 인기 검색어 (Redis ZSet · ZINCRBY/ZREVRANGE · dedup SET NX · 3구획 scope)
- Cache Eviction (@CacheEvict · @CachePut)
- Serializer 구성 (String + GenericJackson2Json + JavaTimeModule)
- 관리자 대시보드 인기 검색어 표시 API (`AdminDashboardController` 일부 · 이슈 13)
- ErrorCode `SRC001~003` · `POP001~002`

**Out of Scope**:
- Elasticsearch — 사유: 초기 규모 오버킬 (v0.0.5v+)
- MySQL FULLTEXT — 사유: 상품 100k+ 시 검토
- 자동완성 (검색어 추천) — 사유: 스코프 오버 (v0.0.4v+)
- Redis Sentinel/Cluster — 사유: 단일 Redis 전제

## 대상 사용자
- **관리자** — 상품 관리용 검색 · 인기 검색어 트렌드 모니터링 (백오피스 대시보드)
- **일반 구매자** — 상품 탐색 (API 호출)
- **개발자/리뷰어** — v1/v2 성능 비교 리포트 · 캐시 전략 학습

## 연결된 Epic 목록
- [ ] Epic 1: 검색 v1 (QueryDSL 동적 · LIKE · 커서 페이징 · count 분리)
- [ ] Epic 2: 검색 v2 Caffeine + 인기 검색어 (ZSet)
- [ ] Epic 3: Redis Remote 전환 + Cache Eviction

## 관련 문서
- 대응 이슈:
  - [issue-03-search-v1-querydsl-like-cursor](../../../fix/brainstorming/version/0.0.3v/issue-03-search-v1-querydsl-like-cursor.md)
  - [issue-04-search-v2-caffeine-local-cache](../../../fix/brainstorming/version/0.0.3v/issue-04-search-v2-caffeine-local-cache.md)
  - [issue-05-popular-search-redis-zset](../../../fix/brainstorming/version/0.0.3v/issue-05-popular-search-redis-zset.md)
  - [issue-10-cache-remote-redis-eviction](../../../fix/brainstorming/version/0.0.3v/issue-10-cache-remote-redis-eviction.md)
- 관련 ADR (발행 예정):
  - `ADR 014` — v2 Local → Redis Remote 전환 근거 (Scale-out)
- CLAUDE.md / `.claude/rules/*` 갱신:
  - `.claude/rules/persistence.md` (선택 · QueryDSL 활용 사례 추가)
- 재활용 조각 (아카이브):
  - `../archive/0.0.2v-crowdfunding/product-project.md` §Epic 3 — QueryDSL 동적 검색 확장
- 관련 milestone: `../../../milestones/version/0.0.3v/milestone.md`
- 관련 Product: `./product-timesale-concurrency.md` (Redis 인프라 공유) · `./product-perf-lab.md` (k6 부하 리포트가 본 Product 결과 검증)

## 열린 질문 (Open Questions)
- **Q1**: `recordSearch`를 @Async로 전환하는 시점? (초기 동기 · P95 지연 관찰 후 결정)
- **Q2**: 인기 검색어 daily TTL 24h vs 48h? (24h 시작 · 대시보드 관찰 후 조정)
- **Q3**: Cache Eviction `allEntries=true`가 트래픽 많을 때 캐시 스템피드 유발 가능성? (초기 채택 · 문제 시 태그 기반 evict로 재설계)
- **Q4**: 관리자용 검색은 상품 status 필터 확장 (DELETED 포함) 필요? (초기: SALE·SOLD_OUT만 · 필요 시 별도 Story)

## 제품 수준 완료 기준 (Product-level DoD)
- [ ] Epic 1·2·3 완료
- [ ] ADR 014 발행
- [ ] k6 부하 리포트 (이슈 11)에 v1/v2 P95 비교 포함
- [ ] Cache Eviction 통합 테스트 (관리자 수정 → 다음 검색 새 데이터)
- [ ] 관리자 대시보드 인기 검색어 API 노출 (이슈 13 결합)

---

# [Epic 1] 검색 v1 (QueryDSL 동적 · LIKE · 커서 페이징 · count 분리)

## 목표
QueryDSL BooleanBuilder로 다중 조건 동적 쿼리를 구성하고 LIKE + count 쿼리 분리 + Projection DTO 조회를 v1 API로 완성한다.

## 배경
캠프 요구사항 필수. v2와의 성능 비교 baseline. 5만+ 데이터 시딩(이슈 02) 후 실측 유의미.

## 포함 Story
- Story 1-1: `ProductSearchQuery` record + `ProductSearchProjection` record
- Story 1-2: `ProductJpaRepositoryImpl.search` (QueryDSL BooleanBuilder + count 분리)
- Story 1-3: `ProductSearchController.v1` + ErrorCode SRC001~003
- Story 1-4: 통합 테스트 (5만 데이터 위 각 조건 조합 · P95 기록)

## Epic 인수 시나리오
- Given 5만 상품 · 관리자 시드 · Redis 미필요
- When `GET /api/v1/products/search?keyword=맥북&minPrice=1000000&maxPrice=3000000&sort=price_asc&page=0&size=20`
- Then 200 · `Page<ProductSearchResponse>` · content 20건 · totalElements 정확 · SQL 로그에 `LIKE '%맥북%'` 실체 확인
- (엣지) minPrice > maxPrice → 400 SRC001
- (엣지) sort=invalid → 400 SRC002
- (엣지) size=200 → 400 SRC003

## Epic 완료 기준 (DoD)
- [ ] 4개 Story 완료
- [ ] SQL 로그에 LIKE 구문 실체 확인
- [ ] count 쿼리 별도 실행 로그 확인
- [ ] 통합 테스트 (5만 데이터 위)

## [Story 1-1] ProductSearchQuery record + ProductSearchProjection record

### User Story
- As a 개발자
- I want 검색 파라미터·응답을 record로 명시하고
- so that DTO 규범 (`dto.md`) 준수 + 컴파일 타임 안전성 확보

### 설명
- `ProductSearchQuery` (application · record):
  - `keyword` (nullable) · `categoryId` (nullable) · `minPrice` (nullable) · `maxPrice` (nullable) · `status` (nullable ProductStatus) · `sort` (String default "latest")
  - Bean Validation: `@AssertTrue price 범위 검증` (`minPrice == null || maxPrice == null || minPrice <= maxPrice`)
- `ProductSearchProjection` (infrastructure · record): id · name · price · imageUrl · categoryId · status · createdAt

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.product.application.dto.ProductSearchQuery`
- `nbc.c1oud_mall.product.infrastructure.projection.ProductSearchProjection`
- `nbc.c1oud_mall.product.application.dto.ProductSearchResponse` (presentation 응답용)

### 완료 기준 (AC)
- Given `ProductSearchQuery(keyword="맥북", null, 1000, 500, ...)` / When Bean Validation / Then `SRC001` (사전조건 위반)
- Given `ProductSearchQuery(...)` 유효 / When toString · equals · hashCode / Then record 자동 생성 검증

### Definition of Done
- [ ] 구현: 3개 파일
- [ ] 단위 테스트 3건 (Bean Validation 통과·실패, 정렬 화이트리스트 검증)
- [ ] `dto.md` 규범 준수 확인

### 스토리 포인트
0.5d

### 의존성
- 후행: Story 1-2 (Repository가 Projection 사용)

## [Story 1-2] ProductJpaRepositoryImpl.search (QueryDSL + count 분리)

목록:
- 기존 `ProductJpaRepositoryImpl` 재사용 · `ProductSearchCondition` → `ProductSearchQuery`로 리네임 or 확장
- BooleanBuilder 동적 조건 (keyword ↔ `product.name.containsIgnoreCase.or(description...)` · categoryId · min/max price · status)
- 정렬 화이트리스트 switch (latest / price_asc / price_desc)
- `Projections.constructor(ProductSearchProjection.class, ...)` DTO Projection
- **count 쿼리 분리** — content 쿼리와 다른 최적화 (`fetchCount()` 대신 별도 `queryFactory.select(product.count()).from(product).where(where).fetchOne()`)
- `PageImpl(content, pageable, total)` 반환

**SP**: 1d

## [Story 1-3] ProductSearchController.v1 + ErrorCode

목록:
- `@RestController @RequestMapping("/api/v1/products/search")`
- `@GetMapping` — `ProductSearchQuery` @Valid · `Pageable` · `ResponseEntity<ApiResponse<Page<ProductSearchResponse>>>`
- ErrorCode `SRC001` (범위) · `SRC002` (정렬) · `SRC003` (size)
- `ApiResponses.ok(page)` 헬퍼 사용

**SP**: 0.5d

## [Story 1-4] 통합 테스트 (5만 데이터 위)

목록:
- `@SpringBootTest @ActiveProfiles("dev-large")` (5만 시딩 로드)
- 시나리오: 각 조건 조합 3+건 · SQL 로그 검증 · P95 측정 로그
- 응답 시간 기준 이하 확인 (초기 참고용 · 정식 기준은 이슈 11 부하테스트에서)

**SP**: 0.5d

---

# [Epic 2] 검색 v2 (Caffeine Local Cache) + 인기 검색어 (Redis ZSet)

## 목표
Caffeine 로컬 캐시로 v2 검색을 완성하고, Redis ZSet 기반 인기 검색어 카운팅·조회를 병행 도입한다.

## 배경
캠프 요구사항 필수 (v2 · 인기 검색어). v1 대비 성능 개선의 첫 단계. 관리자 대시보드 표시의 원천.

## 포함 Story
- Story 2-1: Caffeine CacheConfig + v2 컨트롤러 + @Cacheable
- Story 2-2: 인기 검색어 도메인 (`PopularSearchRepository` · `PopularSearchService`)
- Story 2-3: 검색 진입점에 `recordSearch` 훅
- Story 2-4: `/api/v1/search/popular?scope=&limit=` API

## Epic 인수 시나리오
- Given v2 캐시 활성화 · 5만 상품
- When 동일 keyword로 3회 연속 검색
- Then 첫 요청 = DB 히트 · 이후 2회 = Caffeine 히트 (`cache_hit=true` 지표)

- Given 사용자 A가 "맥북" 검색 · 60초 이내 재검색
- When `recordSearch(A, "맥북")`
- Then 첫 번째만 ZINCRBY 실행 (SET NX dedup) · 두 번째는 skip

- Given daily scope에 keyword 3개 존재 (`맥북=15 · 아이폰=10 · 노트북=8`)
- When `GET /api/v1/search/popular?scope=daily&limit=2`
- Then `[{keyword:"맥북",score:15},{keyword:"아이폰",score:10}]`

## Epic 완료 기준 (DoD)
- [ ] 4개 Story 완료
- [ ] `@EnableCaching` + CaffeineCacheManager 등록
- [ ] Redis 연결 (docker-compose 확인)
- [ ] 통합 테스트: 캐시 히트/미스 · dedup · scope별 조회

## [Story 2-1] Caffeine CacheConfig + v2 컨트롤러

목록:
- `build.gradle.kts`: `spring-boot-starter-cache` + `com.github.ben-manes.caffeine:caffeine`
- `CacheConfig` @EnableCaching · CaffeineCacheManager (`productSearch` 캐시명 · TTL 60초 · maximumSize 1000)
- `ProductSearchService.searchV2` `@Cacheable(value="productSearch", key="'search:'+#query.hashCode()+':'+#pageable.pageNumber+':'+#pageable.pageSize")`
- `ProductSearchController.v2` — `/api/v2/products/search`

**SP**: 1d

## [Story 2-2] 인기 검색어 도메인

목록:
- `PopularSearchRepository` (infrastructure):
  - `recordKeyword(userId, keyword, LocalDateTime now)` — SET NX + ZINCRBY 조합 (2개 명령 · Lettuce sync)
  - `getTop(scope, limit)` — ZREVRANGE WITHSCORES
- `PopularSearchService.recordSearch(userId, keyword)` · `getTop(scope, limit)`
- `PopularKeywordProjection` (record) — keyword · score

**SP**: 1d

## [Story 2-3] 검색 진입점 recordSearch 훅

목록:
- `ProductSearchService.search` · `searchV2` 진입점 하단에서 `popularSearchService.recordSearch(userId, keyword)` 호출
- 트랜잭션 밖 (검색 API는 read-only 트랜잭션이라 큰 문제 없으나 지연 방지 위해 try-catch로 방어)

**SP**: 0.5d

## [Story 2-4] /api/v1/search/popular API + ErrorCode POP001~002

목록:
- `@GetMapping("/api/v1/search/popular")` — `scope` (hourly/daily/weekly) · `limit` (default 10 · max 50)
- `SearchPopularController` (공개)
- ErrorCode `POP001` (scope 무효) · `POP002` (limit 초과)

**SP**: 0.5d

---

# [Epic 3] Redis Remote 전환 + Cache Eviction

## 목표
v2 캐시 매니저를 Caffeine에서 Redis로 스위칭하고, 관리자 상품 CRUD 시 캐시 무효화를 @CacheEvict로 처리해 원본 데이터 변경 시 stale 검색 결과가 반환되지 않도록 보장한다.

## 배경
캠프 요구사항 도전. Scale-out 대비. Cache Eviction 정합성 = 리뷰어가 좋아하는 소재.

## 포함 Story
- Story 3-1: `spring-boot-starter-data-redis` + `RedisConfig` + Serializer (String+GenericJackson2Json+JavaTimeModule)
- Story 3-2: `RedisCacheManager` 등록 · v2 스위치
- Story 3-3: 관리자 상품 CRUD에 `@CacheEvict(value="productSearch", allEntries=true)` · 상품 상세 `@CachePut`
- Story 3-4: TTL 정책 (검색 5분 · 상세 30분) · 통합 테스트

## Epic 인수 시나리오
- Given v2가 RedisCacheManager 사용 · 상품 X 검색 결과 캐시됨
- When 관리자가 상품 X 수정 (`PATCH /api/v1/admin/products/X`)
- Then `@CacheEvict` 실행 · 다음 v2 검색 = 캐시 미스 · 새 데이터 반환

## Epic 완료 기준 (DoD)
- [ ] 4개 Story 완료
- [ ] Redis Serializer 통합 테스트 (`LocalDateTime` 포함 객체 직렬화/역직렬화)
- [ ] Cache Eviction 통합 테스트 (수정 → 검색 stale 없음)
- [ ] ADR 014 발행

## [Story 3-1] RedisConfig + Serializer

목록:
- 의존성 추가 · `RedisConfig` @Configuration
- `RedisTemplate<String, Object>`: Key `StringRedisSerializer` · Value `GenericJackson2JsonRedisSerializer(objectMapper)`
- `ObjectMapper` `.registerModule(new JavaTimeModule())` · `.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)`
- `RedisCacheManager` builder · TTL default 5분 · 캐시별 커스터마이즈

**SP**: 1d

## [Story 3-2] v2 캐시 매니저 스위치

목록:
- `@Cacheable(cacheManager="redisCacheManager")` — 명시적 지정 or 프로파일 스위칭
- Story 4-1 이후 Caffeine 캐시 매니저는 유지 (실험용) · 기본은 Redis

**SP**: 0.5d

## [Story 3-3] @CacheEvict + @CachePut

목록:
- `ProductAdminService.update` · `create` · `delete` 각각 `@CacheEvict(value="productSearch", allEntries=true)`
- `ProductService.findById` `@Cacheable(value="productDetail", key="'detail:'+#id")`
- `ProductAdminService.update` `@CachePut(value="productDetail", key="'detail:'+#result.id")`
- TTL 상세 30분

**SP**: 1d

## [Story 3-4] 통합 테스트 + ADR 014

목록:
- 시나리오 1: `LocalDateTime` 포함 상품 캐시 저장 → 역직렬화 검증
- 시나리오 2: 상품 수정 → 다음 검색 = 새 데이터
- 시나리오 3: 캐시 히트율 관찰 (반복 검색)
- ADR 014 발행: 배경(Scale-out) · 결정(Caffeine→Redis) · 대안 · 결과 · TTL 정책

**SP**: 0.5d
