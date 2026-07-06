# [0.0.3v · Issue 04] 검색 v2 — Caffeine Local Cache

> **역할**: 캠프 요구사항의 검색 v2 (Local Memory Cache · Caffeine · @Cacheable).
> **Week**: 1 · **Day**: 5
> **상태**: pending
> **Tier**: `feature-story`
> **캠프 요구사항 매핑**: 필수 — 캐싱 성능 개선 (v2 Local Cache · `spring-boot-starter-cache` + `@Cacheable`)

---

## 배경

캠프 요구사항 명시:
- `spring-boot-starter-cache` 의존성 · `@EnableCaching`
- Spring AOP 방식 `@Cacheable` 활용
- 로컬 캐시 구현체 **Caffeine** 적용
- **TTL(만료 시간)** · **maximumSize(최대 캐시 수)** 설정
- **기존 v1 API 유지** · 새 v2 API 병존 (`/api/v1/products/search` + `/api/v2/products/search`)
- 왜 검색 API에 캐시를 적용했는지 README에 문서화

## 핵심 스코프

- `build.gradle.kts`: `spring-boot-starter-cache` + `com.github.ben-manes.caffeine:caffeine`
- `@EnableCaching` (`CacheConfig` 클래스)
- `CaffeineCacheManager` 설정: TTL (예: 60초) · maximumSize (예: 1000)
- 캐시 Key 전략: `value = "productSearch"`, `key = "'search:' + #query.hashCode() + ':' + #pageable.pageNumber"`
- v1 API 그대로 유지 · v2 컨트롤러 신설 (`/api/v2/products/search`)
- `ProductSearchService.searchV2(query, pageable)` `@Cacheable` 어노테이션

## Key 설계 원칙 (요구사항 언급)

- value/key 명확 분리
- prefix 전략 (`'search:'`)로 다른 캐시와 충돌 방지

## 왜 검색에 캐시를 적용하는가 (README용 근거)

- 검색 쿼리는 read-heavy · 원본 데이터 변동 대비 조회 빈도 훨씬 큼
- 동일 keyword·정렬 조합이 반복 요청되는 성질 (인기 상품 검색)
- 5만+ 데이터 스캔 비용 대비 캐시 조회 비용이 압도적으로 낮음
- v1 대비 응답 시간 개선을 부하테스트(11)로 정량 측정

## 산출물

- BE-04-1: 의존성 추가 · `CacheConfig` + Caffeine 매니저
- BE-04-2: `ProductSearchController` v2 엔드포인트 (`/api/v2/products/search`)
- BE-04-3: `@Cacheable(value = "productSearch", key = "...")` 적용
- BE-04-4: 캐시 히트/미스 로그 (dev 프로파일)
- BE-04-5: v1/v2 동일 결과 검증 통합 테스트
- BE-04-6: 캐시 만료 · maximumSize 초과 시 evict 동작 검증

## 로컬 캐시의 한계 (요구사항 언급 · 문서화 필수)

- 서버 여러 대 시 데이터 공유 불가 → 이슈 10에서 Redis Remote로 전환
- 이 한계를 인지하고 있음을 README에 명시

## 관련 이슈 / 문서

- 선행: [03 검색 v1](./issue-03-search-v1-querydsl-like-cursor.md)
- 다음: [10 Redis Remote Cache](./issue-10-cache-remote-redis-eviction.md) — Scale-out 대응
- 다음: [11 부하테스트](./issue-11-load-test-k6-report.md) — v1/v2 성능 비교
- 규범: `.claude/rules/architecture.md`

## 상세 (착수 시 채움)

_pending_
