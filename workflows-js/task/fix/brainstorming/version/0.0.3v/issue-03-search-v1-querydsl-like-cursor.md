# [0.0.3v · Issue 03] 검색 v1 — QueryDSL 동적 · LIKE · 커서 페이징

> **역할**: 캠프 요구사항의 검색 필수 기능 v1 (캐시 없음 · 원본 성능).
> **Week**: 1 · **Day**: 4
> **상태**: pending
> **Tier**: `feature-story`
> **캠프 요구사항 매핑**: 필수 — 검색 v1 (QueryDSL LIKE + Paging + count 쿼리 분리)

---

## 배경

캠프 요구사항 명시:
- 특정 컬럼 기준 `LIKE` 조건 검색 API (SQL에 `LIKE` 실체 포함)
- Paging 처리 · 결과를 페이지 단위로
- QueryDSL 동적 쿼리 (`BooleanBuilder` or `BooleanExpression`)
- `Projections.constructor()`로 DTO 직접 조회
- `offset`/`limit` 페이징 + `Page` 반환 위한 **count 쿼리 분리**

v2(Caffeine 캐시)와 성능 비교의 baseline. 5만+ 데이터 위에서 실 성능 측정 대상.

## 핵심 스코프

- `ProductSearchQuery` record (application)
- `ProductSearchProjection` record (infrastructure)
- `ProductSearchRepository` port (domain) + `ProductSearchRepositoryImpl` (infrastructure)
- 동적 조건: `keyword` (name/description LIKE) · `categoryId` · `minPrice` · `maxPrice` · `status`
- 정렬 화이트리스트: `latest` (기본) · `price_asc` · `price_desc`
- 커서 페이징 (or offset — 캠프 명세는 offset도 허용)
- **count 쿼리 분리** — content와 다른 최적화 쿼리로 분리 실행

## API 표면

| 메서드 | 경로 | 인증 | 용도 |
|---|---|---|---|
| GET | `/api/v1/products/search?keyword=&categoryId=&minPrice=&maxPrice=&status=&sort=&page=&size=` | 공개 | 원본 검색 (캐시 없음) |

## 산출물

- BE-03-1: `ProductSearchQuery` record + Bean Validation
- BE-03-2: `ProductSearchProjection` record + QueryDSL Projection
- BE-03-3: `ProductSearchRepositoryCustom` 인터페이스 + Impl (QueryDSL BooleanBuilder)
- BE-03-4: 동적 조건 분기 (keyword null/space, price range, category, status)
- BE-03-5: count 쿼리 분리 (본 쿼리와 다른 최적화 · `Page` 객체 반환)
- BE-03-6: 정렬 화이트리스트 검증 + `SRC002` 등
- BE-03-7: `ProductSearchController` + `/api/v1/products/search`
- BE-03-8: 통합 테스트 (5만 데이터 위 각 조건 조합 · 응답 시간 측정 · P95 기록)

## ErrorCode (신규)

- `SRC001` SEARCH_INVALID_PRICE_RANGE (400 · `minPrice > maxPrice`)
- `SRC002` SEARCH_INVALID_SORT (400 · 화이트리스트 벗어남)
- `SRC003` SEARCH_PAGE_SIZE_EXCEEDED (400 · size > 100)

## 관련 이슈 / 문서

- 선행: [02 더미 시드](./issue-02-dummy-seed-50k-datafaker.md)
- 다음: [04 검색 v2](./issue-04-search-v2-caffeine-local-cache.md)
- 다음: [05 인기 검색어](./issue-05-popular-search-redis-zset.md)
- 다음: [11 부하테스트](./issue-11-load-test-k6-report.md) — v1/v2 성능 비교 대상
- 다음: [12 인덱싱](./issue-12-indexing-explain-report.md) — 검색 쿼리가 병목 후보
- 규범: `.claude/rules/persistence.md` · `.claude/rules/dto.md`

## 상세 (착수 시 채움)

_pending_
