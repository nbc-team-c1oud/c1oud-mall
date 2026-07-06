# Issue: 상품 카테고리 도메인 신설 (계층 지원 · Product.category String 필드 대체)

## 크라우드 펀딩 컨셉 재해석 (0.0.2v 확장)

> 이 이슈는 초기 일반 쇼핑몰 컨텍스트로 작성되었으나, 0.0.2v 컨셉 확정(크라우드 펀딩)에 따라 재해석함.

### 도메인 리매핑
| 기존 | 재해석 |
|---|---|
| Product.category | **Project.category** (프로젝트 분류) |
| 신선식품·가전 등 | **개발도구 · 인프라·DevOps · 라이브러리·SDK · AI·데이터 · 웹·앱 · 오픈소스** |

### 카테고리 트리 (디자인 반영)
디자인(`펀딩 탐색.html` · `프로젝트 등록.html`) 검토 결과 다음 카테고리로 seed 조정:

```
개발 · 테크/                     (root)
├── 개발도구/                    (depth 1)
├── 인프라 · DevOps/
├── 라이브러리 · SDK/
├── AI · 데이터/
├── 웹 · 앱/
└── 오픈소스/
```

- 디자인 확인: 3-depth 계층 (예: `개발 · 테크 > 오픈소스`)
- 등록 페이지 카테고리 pill: single-select (다중 선택 X)
- 탐색 페이지 chip 필터: `is-active` 상태로 필터링

### 관련 신규 이슈
- [#08 Project 도메인](./issue-08-project-domain.md) — Project.categoryId FK 참조
- [#06 검색](./issue-06-search.md) — 카테고리 chip 필터와 결합

### 디자인 참조
- `C:\Users\user\Desktop\fe\펀딩 탐색.html` — chip 필터 UI
- `C:\Users\user\Desktop\fe\프로젝트 등록.html` — 카테고리 pill 선택

---

## 배경

> **사용자 지시**: "카테고리 기능을 붙여나갈 것" · Cabbage-Market-10 벤치마크.

- 현재 `Product.category`는 단순 `String` 필드 (예: `"신선식품"`) — 필드 값이 자유 문자열이라 오탈자·중복·계층 표현 불가능
- 카테고리 트리(예: `식품 > 신선식품 > 채소`) UX 요구가 곧 발생 (검색 필터 이슈 #06과 직결)
- 상품 등록·목록 조회에서 카테고리별 필터가 없음 → 검색·둘러보기 이탈률 리스크
- 관리자가 카테고리를 CRUD할 UI가 아직 없으므로 초기엔 seed 데이터 + read-only API로 충분

## 조사 결과 — Cabbage-Market-10 벤치마크

| 항목 | Cabbage 방식 | 우리 대응 |
|---|---|---|
| 엔티티 | `Category(id, name, sortOrder, isActive)` 단일 · `BaseEntity` 상속 | 유사 · **계층 지원 추가** |
| 계층 (parent_id) | `docs/business-rules.md`에는 명시 · **실 엔티티 코드에는 미구현** (문서 vs 코드 불일치) | 우리는 도입 시점부터 구현 |
| API | `GET /api/categories?page=&size=` — 페이지 조회만 · 등록/수정/삭제 없음 | 유사 · 추가로 depth별 필터 |
| Soft Delete | 없음 · `isActive=false` 비활성화만 | 채택 |
| Product 연관 | `Item.category`가 `Category` 참조 | `Product.category`를 String → `Category` FK로 마이그레이션 |

## 옵션 비교

**Option A — 자기 참조 계층 (`parent_id` self FK) + 별도 Category 도메인 신설** `(채택)`
- 장점: 도메인 모델 단순 · 대부분 DB 지원 · JPA `@ManyToOne(self)` 표준
- 비용: 트리 조회 시 재귀 쿼리 필요 (초기엔 depth ≤ 3 제한으로 단순 순회 OK)
- 카테고리 트리 UX 지원 · 검색 필터 확장 가능

**Option B — Nested Set Model (`lft` · `rgt` 컬럼)**
- 거부 이유: 삽입·수정 시 전체 lft/rgt 재계산 부담 · 계층이 얕고 변경 적은 우리 스코프에 오버킬

**Option C — Materialized Path (`path` VARCHAR 컬럼 · `"1/4/12"` 형식)**
- 거부 이유: 조회는 빠르나 rename·이동 시 path 일괄 갱신 부담 · JPA와 어색

## 선택: Option A

## 부속 결정

### 도메인 컨텍스트
- 신규 컨텍스트: `nbc.c1oud_mall.category.*` (4레이어)
- Product 도메인은 Category를 FK로 참조 (직접 호출 · ADR 006)
- 초기 관리자 UI 없음 → seed 데이터 (`DummyDataInit`에 카테고리 트리 8~12건 초기화)

### 엔티티 · 스키마
```sql
CREATE TABLE category (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100) NOT NULL,
  parent_id   BIGINT       NULL,
  depth       INT          NOT NULL DEFAULT 0,   -- 0 (root) · 1 · 2 · 3
  sort_order  INT          NOT NULL DEFAULT 0,
  is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at  TIMESTAMP    NOT NULL,
  updated_at  TIMESTAMP    NOT NULL,
  PRIMARY KEY (id),
  KEY idx_category_parent_sort (parent_id, sort_order),
  UNIQUE KEY uk_category_name_parent (name, parent_id),   -- 같은 부모 아래 이름 중복 금지
  CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category(id)
);

-- Product 테이블 마이그레이션
ALTER TABLE product ADD COLUMN category_id BIGINT NULL AFTER category;
ALTER TABLE product ADD KEY idx_product_category (category_id);
-- (마이그레이션 배치로 기존 String category → category_id 매핑)
-- 완료 후: ALTER TABLE product DROP COLUMN category;
```

### 도메인 모델
```java
// category.domain.Category (Aggregate root)
@Entity
public class Category extends BaseEntity {
    @Id @GeneratedValue Long id;
    String name;
    @ManyToOne(fetch = LAZY) @JoinColumn(name = "parent_id")
    Category parent;   // nullable · root면 null
    int depth;         // 0 · 1 · 2 · 3
    int sortOrder;
    boolean isActive;

    public static Category root(String name, int sortOrder) { ... }
    public Category createChild(String name, int sortOrder) {
        if (depth >= 3) throw new BusinessException(ErrorCode.CATEGORY_MAX_DEPTH_EXCEEDED);
        Category child = new Category();
        child.parent = this;
        child.depth = this.depth + 1;
        child.name = name;
        child.sortOrder = sortOrder;
        return child;
    }
    public void deactivate() { this.isActive = false; }
}
```

### API 표면
| 메서드 | 경로 | 용도 |
|---|---|---|
| GET | `/api/v1/categories` | 전체 카테고리 트리 (depth 순 flat 리스트 · FE에서 트리 조립) |
| GET | `/api/v1/categories?parentId=` | 특정 부모의 자식 목록 |
| GET | `/api/v1/categories/{id}/breadcrumb` | 경로 (root → self) · UI breadcrumb 용 |

### Product 연동
- `Product.category(String)` → `Product.categoryId(Long)` FK 필드로 마이그레이션
- 상품 목록 조회 시 `?categoryId=`로 하위 트리 전부 필터링 (재귀 CTE 또는 앱 레벨 `IN` 절)
- 이슈 #06 검색과 결합: `SearchService`가 `Category.getDescendantIds(id)`를 활용

### ErrorCode (신규)
- `CAT001` CATEGORY_NOT_FOUND (404)
- `CAT002` CATEGORY_MAX_DEPTH_EXCEEDED (400 · depth > 3)
- `CAT003` CATEGORY_HAS_CHILDREN (409 · 자식 있는 카테고리 비활성화 시도)

### 초기 seed (`DummyDataInit` 확장)
```
식품/
├── 신선식품/
│   ├── 채소
│   ├── 과일
│   └── 수산물
├── 가공식품/
│   ├── 라면
│   └── 통조림
└── 음료
가전
생활용품
```

## 이관 산출물

- **BE-Story #02-1**: `category` 컨텍스트 신규 패키지 + `Category` 엔티티 + Repository
- **BE-Story #02-2**: `CategoryService.getTree()` · `getChildren(parentId)` · `getBreadcrumb(id)`
- **BE-Story #02-3**: `CategoryController` REST 3개 엔드포인트 (조회 전용)
- **BE-Story #02-4**: `Product.categoryId` FK 필드 추가 + 마이그레이션 스크립트 (String → Long 매핑)
- **BE-Story #02-5**: 이후 마이그레이션 완료 후 `Product.category(String)` DROP (별도 릴리즈 · M3)
- **BE-Story #02-6**: `DummyDataInit`에 카테고리 seed 데이터 8~12건 추가
- **BE-Story #02-7**: `ErrorCode.CAT001~003` 등록
- **BE-Story #02-8**: 상품 목록 조회 API에 `?categoryId=` 필터 추가 (하위 트리 포함)
- **FE-Story #02-1**: `src/features/category/CategoryTree.tsx` 컴포넌트 (재귀 렌더)
- **FE-Story #02-2**: 상품 목록 페이지에 카테고리 사이드바 필터 추가
- **Docs-Story #02-1**: `backend-boundary/error-codes.md` CAT001~003 UX 매핑 추가
- **SDD 개정**: 향후 `product-catalog.md` 작성 시 흡수

## 관련 이슈 / 문서

- 다음 이슈: [#06 검색](./issue-06-search.md) — 검색 필터에 categoryId 활용 (하위 트리 포함)
- 관련: [#04 좋아요](./issue-04-product-like.md), [#05 리뷰](./issue-05-review.md) — Product 연관 기능들과 조회 지표(likeCount·rating avg) 카테고리별 집계 후속
- 뒤집는 이슈: 없음 (신규)
- 벤치마크 원본: `pcb2002/Cabbage-Market-10` — `src/main/java/com/example/cabbagemarket10/domain/category/` (계층 미구현 상태 · 문서상 명시)
- 규범 참조: `.claude/rules/architecture.md` (4레이어 도메인 신설 절차)
