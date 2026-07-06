# Issue: 통합 상품 검색 API 확장 (QueryDSL 동적 조건 · 다중 필터·정렬)

## 크라우드 펀딩 컨셉 재해석 (0.0.2v 확장)

> 이 이슈는 초기 일반 쇼핑몰 컨텍스트로 작성되었으나, 0.0.2v 컨셉 확정(크라우드 펀딩)에 따라 재해석함.

### 도메인 리매핑
| 기존 | 재해석 |
|---|---|
| Product 검색 | **Project 검색** |
| `GET /api/v1/products/search` | `GET /api/v1/projects/search` |
| ProductStatus (SALE/SOLD_OUT) | **ProjectStatus** (LIVE/SUCCESSFUL/FAILED/COMPLETED 등) |
| minPrice/maxPrice | 목표 금액 범위 or 최소 후원가 범위 |

### 검색 파라미터 재정의 (디자인 반영)
디자인(`펀딩 탐색.html`) 검토:

| 파라미터 | 재해석 |
|---|---|
| `keyword` | 프로젝트명 · 설명 · 메이커명 |
| `categoryId` | 카테고리 트리 하위 포함 |
| `status` | LIVE (진행중) · UPCOMING (펀딩 예정) · FUNDED (성공) |
| `sort` | **인기순** · **달성률 높은순** · **마감 임박순** · **최신 등록순** (디자인 확인) |
| `hasGithub` | GitHub 연동 여부 필터 (추가 검토) |

### 정렬 화이트리스트 (디자인 확정)
- `popular` — 인기순 (likeCount + backerCount 결합 · 결정 필요)
- `progress_desc` — 달성률 높은순 (`raisedAmount / goalAmount`)
- `dday_asc` — 마감 임박순 (`endedAt` 오름차순)
- `latest` — 최신 등록순 (`createdAt` desc · 기본값)

### 관련 신규 이슈
- [#08 Project 도메인](./issue-08-project-domain.md) — 검색 대상 엔티티
- [#10 Pledge 상태기계](./issue-10-pledge-state-machine.md) — 상태 필터
- [#14 GitHub 연동](./issue-14-github-integration.md) — GitHub 필터 추가

### 디자인 참조
- `C:\Users\user\Desktop\fe\펀딩 탐색.html` — 정렬 셀렉트 · chip 필터

---

## 배경

> **사용자 지시**: "검색 기능을 붙여나갈 것" · Cabbage-Market-10 벤치마크.

- 현재 c1oud-mall `Product` 조회는 `GET /api/v1/products` 페이징만 지원 (QueryDSL 커스텀 리포지토리 존재 확인 · 0.0.1v 완료 사항)
- 사용자가 원하는 상품을 찾기 위한 정교한 검색 UX 부재:
  - keyword 검색 (제목·설명 부분 매칭)
  - 카테고리 필터 (이슈 #02 카테고리 도입 후 하위 트리 포함)
  - 가격 범위 필터 (min · max)
  - 상품 상태 필터 (판매중 · 품절)
  - 정렬 (신상순 · 가격순 · 인기순 = likeCount desc · 별점순 = ratingAvg desc)
- 이슈 #04(좋아요) · #05(리뷰)가 반정규화 필드를 추가하므로 인기·별점 정렬은 컬럼 정렬로 즉시 가능
- Elasticsearch 도입 여부 결정 필요 (미도입 확정 · 근거 명시)

## 조사 결과 — Cabbage-Market-10 벤치마크

| 항목 | Cabbage 방식 | 우리 대응 |
|---|---|---|
| 검색 방식 | QueryDSL `BooleanBuilder` 동적 조건 · Elasticsearch 미사용 | 채택 |
| 엔드포인트 | `GET /api/v1/items/search?keyword=&categoryId=&tradeStatus=&conditionType=&minPrice=&maxPrice=&page=&sort=` | 유사 (`/api/v1/products/search`) |
| 정렬 | `initialPrice`, `currentBid`, `createdAt` 화이트리스트 · 동률시 `id.desc()` tiebreaker | 채택 (`price`, `createdAt`, `likeCount`, `ratingAvg`) |
| 프로젝션 | `Projections.constructor(SearchItemResponse.class, ...)` DTO Projection | 채택 |
| 조인 | `QItem`, `QAuctionStatus`, `QItemImage(썸네일)` | 우리: `QProduct`, `QCategory`(카테고리명), `QProductImage`(썸네일 · 있으면) |
| 기본 조건 | `item.isDraft.isFalse()` (임시저장 제외) | 우리: `product.status.eq(SALE) OR SOLD_OUT` (전부 노출) · 필터로 상태 조절 |
| Count 쿼리 | 별도 count query | 채택 |

## 옵션 비교

**Option A — QueryDSL `BooleanBuilder` + 동적 조건 + 반정규화 컬럼 정렬** `(채택)`
- 장점: 기존 스택(JPA·QueryDSL) 재사용 · Elasticsearch 인프라 불필요 · 이슈 #04·#05 반정규화 컬럼 활용
- 비용: keyword 검색이 `LIKE '%keyword%'` 수준 (인덱스 못 씀 · 소규모에는 무방)
- 벤치마크 실 사례 존재

**Option B — Elasticsearch 도입**
- 거부 이유: 인프라 오버킬 · 초기 상품 수 32건 · sync 관리 부담 · 도입 시점은 상품 10k+ 이후

**Option C — MySQL Full-Text Index (`FULLTEXT`)**
- 부분 채택 여지: 상품 10k~100k 규모 도달 시 keyword 검색 성능 개선용 · 지금은 미도입
- 초기엔 `LIKE`로 충분

## 선택: Option A

## 부속 결정

### 확장 대상
- 기존 `nbc.c1oud_mall.product.*` 컨텍스트 안에 `search` 서브패키지 or 별도 `search` 컨텍스트
  - **결정: `product.infrastructure` 안에 `ProductSearchRepository` 확장** (별도 컨텍스트 오버킬)
  - 이유: Product 도메인의 조회 확장 · 새 엔티티 없음

### 검색 파라미터 표
| 파라미터 | 타입 | 예시 | 비고 |
|---|---|---|---|
| `keyword` | String? | `"고흥"` | `name` OR `description` `LIKE '%keyword%'` |
| `categoryId` | Long? | `12` | 하위 트리 포함 (이슈 #02 `Category.getDescendantIds`) |
| `status` | ProductStatus? | `SALE` | `SALE` · `SOLD_OUT` |
| `minPrice` | Long? | `10000` | ≥ |
| `maxPrice` | Long? | `50000` | ≤ · `min > max` 시 `SEARCH_INVALID_PRICE_RANGE` |
| `sort` | String? | `latest`·`price_asc`·`price_desc`·`popular`·`rating` | 화이트리스트 |
| `page` | int | `0` | 0-based |
| `size` | int | `20` | max 100 |

### QueryDSL 조립 예시
```java
// product.infrastructure.ProductSearchRepositoryImpl
public Page<ProductSearchProjection> search(ProductSearchQuery q, Pageable pageable) {
    BooleanBuilder where = new BooleanBuilder();

    // keyword
    if (StringUtils.hasText(q.keyword())) {
        where.and(product.name.containsIgnoreCase(q.keyword())
              .or(product.description.containsIgnoreCase(q.keyword())));
    }

    // category (하위 트리 포함)
    if (q.categoryId() != null) {
        List<Long> ids = categoryService.getDescendantIds(q.categoryId());   // self + 자식
        where.and(product.categoryId.in(ids));
    }

    // status
    if (q.status() != null) where.and(product.status.eq(q.status()));

    // price range
    if (q.minPrice() != null) where.and(product.price.goe(q.minPrice()));
    if (q.maxPrice() != null) where.and(product.price.loe(q.maxPrice()));

    // 정렬 (화이트리스트)
    OrderSpecifier<?> order = switch (q.sort()) {
        case "price_asc"  -> product.price.asc();
        case "price_desc" -> product.price.desc();
        case "popular"    -> product.likeCount.desc();
        case "rating"     -> product.ratingAvg.desc();
        default           -> product.createdAt.desc();   // "latest"
    };

    List<ProductSearchProjection> content = queryFactory
        .select(Projections.constructor(ProductSearchProjection.class,
                product.id, product.name, product.price,
                product.likeCount, product.ratingAvg, product.status))
        .from(product)
        .where(where)
        .orderBy(order, product.id.desc())   // tiebreaker
        .offset(pageable.getOffset()).limit(pageable.getPageSize())
        .fetch();

    long total = queryFactory.select(product.count()).from(product).where(where).fetchOne();
    return new PageImpl<>(content, pageable, total);
}
```

### API 표면
| 메서드 | 경로 | 인증 | 용도 |
|---|---|---|---|
| GET | `/api/v1/products/search?keyword=&categoryId=&status=&minPrice=&maxPrice=&sort=&page=&size=` | 공개 | 통합 검색 (인증 없이 검색 가능) |

### ErrorCode (신규)
- `SRC001` SEARCH_INVALID_PRICE_RANGE (400 · `minPrice > maxPrice`)
- `SRC002` SEARCH_INVALID_SORT (400 · 화이트리스트 벗어난 sort 값)
- `SRC003` SEARCH_PAGE_SIZE_EXCEEDED (400 · size > 100)

### 응답 스키마
```json
{
  "success": true,
  "code": "OK",
  "data": {
    "content": [
      {
        "id": 42,
        "name": "맛있는 고흥 붉바리 (생물)",
        "price": 45000,
        "likeCount": 128,
        "ratingAvg": 4.65,
        "ratingCount": 47,
        "status": "SALE",
        "thumbnailUrl": null,
        "categoryPath": "식품/신선식품/수산물"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

### 관측
- `product.search.total{sort, has_keyword, has_category}` counter
- `product.search.duration_seconds` histogram
- Slow query log: 응답 시간 P95 > 500ms 시 알람 (m3+)

### 캐싱 정책 (미도입 · 향후)
- 초기: 캐싱 없음 · DB 직결
- 트리거: 검색 QPS > 100/s → Redis 캐시 (인기 검색어 · TTL 60s)

## 이관 산출물

- **BE-Story #06-1**: `ProductSearchQuery` (record · application) + `ProductSearchProjection` (record · infrastructure)
- **BE-Story #06-2**: `ProductSearchRepository` 인터페이스(port) + `ProductSearchRepositoryImpl` (QueryDSL) 확장
- **BE-Story #06-3**: `ProductSearchService.search(query)` (검증 · 정렬 화이트리스트)
- **BE-Story #06-4**: `ProductSearchController` (`GET /api/v1/products/search`)
- **BE-Story #06-5**: `ErrorCode.SRC001~003` 등록
- **BE-Story #06-6**: 통합 테스트 (더미 32건 기반 · 각 필터 조합 · 정렬 화이트리스트 검증)
- **BE-Story #06-7**: 정렬 옵션 · 이슈 #02 카테고리 하위 트리 조회 연동 (Category.getDescendantIds)
- **BE-Story #06-8**: 정렬 옵션 · 이슈 #04 (likeCount) · 이슈 #05 (ratingAvg) 컬럼 활용 (선행 이슈 완료 후)
- **FE-Story #06-1**: `src/features/search/SearchPage.tsx` (필터 사이드바 · 정렬 셀렉트 · 페이지네이션)
- **FE-Story #06-2**: `src/features/search/SearchBar.tsx` (헤더 검색창 · debounce · 자동완성 v2)
- **FE-Story #06-3**: URL 쿼리 파라미터 sync (뒤로가기·공유 URL 지원)
- **FE-Story #06-4**: `useProductSearch(query)` 훅 (TanStack Query · staleTime 조절)
- **Docs-Story #06-1**: `backend-boundary/error-codes.md` SRC001~003 매핑
- **Docs-Story #06-2**: Elasticsearch 미도입 결정 근거를 `workflows/task/pes/brainstorming/0.0.2v/data.md` 후보에 추가 (이슈 #06 결과)
- **SDD 개정**: 향후 `product-catalog.md` 또는 `product-search.md` 작성 시 흡수

## 관련 이슈 / 문서

- 짝 이슈: [#02 카테고리](./issue-02-category-hierarchy.md) — 검색 필터의 카테고리 하위 트리 조회
- 관련: [#04 좋아요](./issue-04-product-like.md) — 정렬 "인기순" (likeCount desc)
- 관련: [#05 리뷰](./issue-05-review.md) — 정렬 "별점순" (ratingAvg desc)
- 이전 (0.0.1v): 상품 QueryDSL 커스텀 리포지토리 (`ProductJpaRepositoryCustom`) — 0.0.1v 완료 · 본 이슈에서 확장
- 벤치마크 원본: `pcb2002/Cabbage-Market-10` — `domain/search/ItemSearchRepositoryImpl.java`
- 규범 참조: `.claude/rules/persistence.md` (QueryDSL · Projection)
