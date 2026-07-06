# [Product 14] 카테고리 (Category · 2-Depth 계층 · Project 분류)

## Product Vision
> 프로젝트가 어떤 도메인·주제에 속하는지 후원자·메이커가 즉시 파악할 수 있도록 **2-depth 계층 카테고리**를 제공한다. 최상위 카테고리(대분류) + 서브 카테고리(중분류) 구조로 Project에 부여하고, Discover 페이지의 태그와 별개로 프로젝트 자체 분류 축을 확보한다. 관리자가 CRUD로 관리하고, 메이커는 프로젝트 등록 시 선택만 한다.

## 배경 및 문제
- 현재 상황 (As-Is)
  - Project (Product 6)에 `category_id` 참조 컬럼은 스키마에 있으나 카테고리 도메인 부재
  - 후원자가 관심 도메인(핀테크·AI·게임)의 프로젝트를 필터링 불가
  - Discovery(Product 13)의 태그와 Project의 분류 축이 혼재
- 발생하는 문제
  - Project 목록·검색에서 카테고리 필터 UX 없음 → 후원 이탈률 상승
  - Discovery 태그는 신호(뉴스)용 · Project 자체 분류에는 부적합
  - 관리자가 프로젝트를 도메인별로 관리·통계 불가
- 왜 지금 해결해야 하는가
  - Product 6·11·12 완결 · Project 상세 UX 성립 · 카테고리 필터 UX 도입의 적기
  - Search(Product 18)이 카테고리 축을 소비 예정
  - 초기 사용자 없어 카테고리 재편성 자유

## 목표 (To-Be)
- 신규 컨텍스트: `nbc.c1oud_mall.category.*` (4레이어)
- `Category` 엔티티 · 2-depth 계층 (parent 참조)
- 필드: `id · slug · name · parentId · displayOrder · active · createdAt · updatedAt`
- 초기 카테고리 시딩:
  - **테크** (부모)
    - 웹 · 모바일앱 · SaaS · AI/ML · 데이터 · DevOps · 보안
  - **컨텐츠** (부모)
    - 미디어 · 교육 · 게임 · 커뮤니티
  - **비즈니스** (부모)
    - 핀테크 · 커머스 · 생산성
- REST 5개 (공개 조회 + 관리자 CRUD)
- Project 등록 시 서브 카테고리 하나 선택 필수 (Product 6 검증 확장)
- `ErrorCode.CAT001~004` (4개)
- 관측 지표 2종

## 설계 결정 (Design Decisions)
- **2-depth 계층 (부모-자식 · 3-depth+ X)**
  - UX 단순성 · Project는 leaf 카테고리만 선택
- **`parent_id NULL = 대분류` · `parent_id != NULL = 서브 카테고리`**
- **`slug`로 URL 친화** — `/discover?category=fintech`
- **관리자 CRUD** — 초기 시딩 · 이후 관리자 추가/수정
- **삭제는 Soft Delete + Active flag** — 카테고리 삭제 시 소속 Project 참조 유지 · UI에서만 감춤
- **Project는 서브 카테고리만 참조** — 부모는 파생 조회
- **초기 시딩 방식** — `Flyway` migration 대신 `DummyDataInit` 유사 · `CategorySeeder` 부팅 시 실행 · idempotent
- **DisplayOrder**로 UI 순서 결정 · 알파벳 아닌 관리자 지정

## 대안 검104토 (Alternatives Considered)

### 계층 깊이
**Option A — 2-depth (선택)** — UX 단순 · 학생 스코프 적합
**Option B — 무제한 (self-ref tree)** — 오버킬 · 초기 부적합
**Option C — 1-depth (flat)** — 카테고리 개수 폭증 · 그룹핑 부족

### 다중 카테고리
**Option A — 프로젝트당 하나 (선택)** — 명확 · 관리 쉬움
**Option B — 다중 카테고리 (Many-to-Many)** — v0.0.5+ 검토

### 삭제 정책
**Option A — Soft Delete + Active flag (선택)** — 참조 유지 · UI 감춤
**Option B — 실 삭제 · Project.category_id 강제 NULL 처리** — 데이터 손실 리스크

## 전체 아키텍처

### 컴포넌트 배치
```
presentation ──▶ application ──▶ domain ◀── infrastructure
CategoryController   CategoryService     Category           CategoryRepository
- listTree           - listTree          - createTopLevel() - findByParentIdIsNull
- getById            - getCategory       - createSub(parent)- findByParentId
- adminCreate        - create            - deactivate()     - findBySlug
- adminUpdate        - update            - reactivate()     CategorySeeder
- adminDeactivate    - deactivate        - reorder()        - seed() @PostConstruct

External refs:
- Project (Product 6) — Project.categoryId FK
- Search (Product 18) — 카테고리 필터
```

### 핵심 플로우

**1. 부팅 시 시딩**
```
@PostConstruct
CategorySeeder.seed()
  For each seed data:
    if !exists(slug): save
```

**2. 트리 조회 (Discover / 프로젝트 등록)**
```
FE → GET /api/v1/categories?includeInactive=false
   → CategoryService.listTree()
     ├── 대분류 조회 (parentId=null · active=true)
     └── 각 대분류에 자식 매핑
   ← CategoryTreeResponse[]
```

**3. Project 등록 시 카테고리 검증 (Product 6 확장)**
```
ProjectService.create (Product 6 확장)
  ├── categoryId 검증: exists · sub-category (parentId != null)
  └── Project 저장
```

**4. 관리자 CRUD**
```
POST /api/v1/admin/categories {slug, name, parentId?, displayOrder}
  → CategoryService.create
```

### Out-of-Process 의존
- **RDS (MySQL)** — `category` 테이블

## 실패 모드

| 시나리오 | ErrorCode | HTTP |
|---|---|---|
| 카테고리 없음 | `CAT001` CATEGORY_NOT_FOUND | 404 |
| 슬러그 중복 | `CAT002` CATEGORY_SLUG_DUPLICATED | 409 |
| Project 참조하는 카테고리를 실 삭제 시도 | `CAT003` CATEGORY_IN_USE | 409 |
| 대분류 아닌 카테고리를 parent로 지정 | `CAT004` CATEGORY_INVALID_PARENT | 400 |

## 관측 지표
- `category.count.gauge{level=top|sub}` — 활성 카테고리 개수
- `category.project.gauge{category_slug}` — 카테고리별 프로젝트 수 (주간 집계)

## Scope

**In**:
- `category.*` 4레이어
- `Category` 엔티티 · 2-depth
- 초기 시딩 (부팅 시)
- REST 5개
- Project.categoryId FK 검증 (Product 6 확장)
- `ErrorCode.CAT001~004`

**Out**:
- **다중 카테고리 (Many-to-Many)** — v0.0.5+
- **카테고리 통계·리더보드** — v0.0.5+
- **카테고리 아이콘·이미지** — Media Product 11 활용 · v0.0.4+
- **사용자별 관심 카테고리** — v0.0.5+
- **다국어 카테고리명** — 한국어만

## Epic 목록
- [ ] Epic 1: `Category` 엔티티 + Repository + `ErrorCode.CAT001~004`
- [ ] Epic 2: `CategoryService` + `CategorySeeder` (초기 시딩)
- [ ] Epic 3: REST 5개 + Project.categoryId 검증 확장 + ADR

## 관련 문서
- **원본 이슈**: `issue-02-category.md`
- **선행 SDD**: `product-project.md` (Product 6)
- **후행 SDD**: `product-search.md` (Product 18)
- **디자인**: `펀딩 탐색.html` 카테고리 필터

## 열린 질문
- **카테고리 마이그레이션 정책** — 시딩 재실행 시 slug 유지 조건
- **카테고리 순서 변경 UX** — 관리자가 drag-drop or displayOrder 수동
- **참조 있는 카테고리 이동** — parent 변경 시 자식 처리

## Product-level DoD
- [ ] Epic 3개 완료
- [ ] 시딩 데이터 정확 (16개 카테고리)
- [ ] Project 생성 시 카테고리 검증 성공
- [ ] `backend-boundary/error-codes.md` CAT001~004

---

# [Epic 1] `Category` + Repository + ErrorCode

## Story
- 1-1: 엔티티 + 도메인 메서드
- 1-2: Repository + ErrorCode

---

## [Story 1-1] 엔티티 + 도메인 메서드

### 스키마
```sql
CREATE TABLE category (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  slug           VARCHAR(50)  NOT NULL,
  name           VARCHAR(100) NOT NULL,
  parent_id      BIGINT       NULL,
  display_order  INT          NOT NULL DEFAULT 0,
  active         BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at     TIMESTAMP    NOT NULL,
  updated_at     TIMESTAMP    NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cat_slug (slug),
  KEY idx_cat_parent (parent_id, display_order),
  KEY idx_cat_active (active)
);
```

**엔티티**:
```java
@Entity
@Table(name = "category")
@NoArgsConstructor(access = PROTECTED)
@Getter
public class Category extends BaseEntity {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String slug;

    @Column(nullable = false, length = 100)
    private String name;

    @Column
    private Long parentId;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;

    public static Category createTopLevel(String slug, String name, int order) {
        Category c = new Category();
        c.slug = slug;
        c.name = name;
        c.parentId = null;
        c.displayOrder = order;
        c.active = true;
        return c;
    }

    public static Category createSub(String slug, String name, Long parentId, int order) {
        Category c = new Category();
        c.slug = slug;
        c.name = name;
        c.parentId = parentId;
        c.displayOrder = order;
        c.active = true;
        return c;
    }

    public boolean isTopLevel() { return parentId == null; }
    public boolean isSub() { return parentId != null; }

    public void update(String name, int displayOrder) {
        this.name = name;
        this.displayOrder = displayOrder;
    }

    public void deactivate() { this.active = false; }
    public void reactivate() { this.active = true; }
}
```

### AC
- `createTopLevel("tech","테크",1)` → parentId=null
- `createSub("web","웹",1L,1)` → parentId=1
- `isTopLevel()`, `isSub()` 정확

### DoD
- [ ] 엔티티 + 정적 팩토리 + 도메인 메서드
- [ ] 단위 테스트

### SP: 1d

---

## [Story 1-2] Repository + `ErrorCode.CAT001~004`

### Repository
```java
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByParentIdIsNullAndActiveTrueOrderByDisplayOrder();
    List<Category> findByParentIdAndActiveTrueOrderByDisplayOrder(Long parentId);
    Optional<Category> findBySlug(String slug);
    boolean existsBySlug(String slug);

    @Query("SELECT COUNT(p) FROM Project p WHERE p.categoryId = :id")
    long countProjectsByCategoryId(@Param("id") Long id);
}
```

### ErrorCode
- CAT001 CATEGORY_NOT_FOUND (404)
- CAT002 CATEGORY_SLUG_DUPLICATED (409)
- CAT003 CATEGORY_IN_USE (409)
- CAT004 CATEGORY_INVALID_PARENT (400)

### DoD
- [ ] Repository
- [ ] ErrorCode 등록
- [ ] `@DataJpaTest`

### SP: 0.5d

---

# [Epic 2] `CategoryService` + `CategorySeeder`

## Story
- 2-1: `CategoryService` (조회 · 관리자 CRUD)
- 2-2: `CategorySeeder` (초기 시딩)

---

## [Story 2-1] CategoryService

### 설명
```java
@Service
@Transactional
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository repository;

    @Transactional(readOnly = true)
    public List<CategoryTreeResponse> listTree() {
        List<Category> tops = repository.findByParentIdIsNullAndActiveTrueOrderByDisplayOrder();
        return tops.stream()
            .map(top -> CategoryTreeResponse.of(top,
                repository.findByParentIdAndActiveTrueOrderByDisplayOrder(top.getId())))
            .toList();
    }

    public CategoryResponse create(CategoryCreateCommand cmd) {
        if (repository.existsBySlug(cmd.slug()))
            throw new BusinessException(ErrorCode.CAT002);
        Category category;
        if (cmd.parentId() == null) {
            category = Category.createTopLevel(cmd.slug(), cmd.name(), cmd.displayOrder());
        } else {
            Category parent = repository.findById(cmd.parentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CAT004));
            if (!parent.isTopLevel())
                throw new BusinessException(ErrorCode.CAT004);
            category = Category.createSub(cmd.slug(), cmd.name(), cmd.parentId(), cmd.displayOrder());
        }
        return CategoryResponse.from(repository.save(category));
    }

    public CategoryResponse update(Long id, CategoryUpdateCommand cmd) {
        Category c = repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.CAT001));
        c.update(cmd.name(), cmd.displayOrder());
        return CategoryResponse.from(c);
    }

    public void deactivate(Long id) {
        Category c = repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.CAT001));
        c.deactivate();
    }

    @Transactional(readOnly = true)
    public void verifyLeafCategory(Long id) {
        Category c = repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.CAT001));
        if (!c.isSub())
            throw BusinessException.withDetail(ErrorCode.CAT004, "leaf 카테고리만 선택 가능");
    }
}
```

### AC
- `listTree` 반환 구조 검증
- `create(cmd)` 성공 · 중복 slug 시 CAT002
- 서브 카테고리를 parent로 지정 시 CAT004

### DoD
- [ ] Service · Command · Response
- [ ] 통합 테스트

### SP: 1d

---

## [Story 2-2] `CategorySeeder`

### 설명
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class CategorySeeder implements ApplicationRunner {
    private final CategoryRepository repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedTopWithChildren("tech", "테크", 1, List.of(
            new SubSeed("web","웹",1),
            new SubSeed("mobile-app","모바일앱",2),
            new SubSeed("saas","SaaS",3),
            new SubSeed("ai-ml","AI/ML",4),
            new SubSeed("data","데이터",5),
            new SubSeed("devops","DevOps",6),
            new SubSeed("security","보안",7)
        ));
        seedTopWithChildren("content", "컨텐츠", 2, List.of(
            new SubSeed("media","미디어",1),
            new SubSeed("education","교육",2),
            new SubSeed("game","게임",3),
            new SubSeed("community","커뮤니티",4)
        ));
        seedTopWithChildren("business", "비즈니스", 3, List.of(
            new SubSeed("fintech","핀테크",1),
            new SubSeed("commerce","커머스",2),
            new SubSeed("productivity","생산성",3)
        ));
    }

    private void seedTopWithChildren(String topSlug, String topName, int topOrder, List<SubSeed> subs) {
        Category top = repository.findBySlug(topSlug).orElseGet(() ->
            repository.save(Category.createTopLevel(topSlug, topName, topOrder))
        );
        for (SubSeed s : subs) {
            if (repository.findBySlug(s.slug()).isEmpty()) {
                repository.save(Category.createSub(s.slug(), s.name(), top.getId(), s.order()));
            }
        }
    }

    private record SubSeed(String slug, String name, int order) {}
}
```

### AC
- 부팅 후 카테고리 3(top) + 14(sub) = 17개 존재
- 재부팅 시 중복 저장 없음 (idempotent)

### DoD
- [ ] Seeder
- [ ] 통합 테스트: 재부팅 시 idempotent

### SP: 0.5d

---

# [Epic 3] REST 5개 + Project 검증 확장 + ADR

## Story
- 3-1: `CategoryController` REST 5개
- 3-2: Project.categoryId 검증 (Product 6 확장)
- 3-3: ADR + `backend-boundary/error-codes.md`

---

## [Story 3-1] REST 5개

### 엔드포인트
| Method | Path | 인증 |
|---|---|---|
| GET | `/api/v1/categories` | 공개 |
| GET | `/api/v1/categories/{slug}` | 공개 |
| POST | `/api/v1/admin/categories` | 관리자 |
| PATCH | `/api/v1/admin/categories/{id}` | 관리자 |
| POST | `/api/v1/admin/categories/{id}/deactivate` | 관리자 |

### DoD
- [ ] Controller
- [ ] `@WebMvcTest`

### SP: 0.5d

---

## [Story 3-2] Project 검증 확장

### 설명
- Product 6 `ProjectService.create/update`에 카테고리 검증 추가
- `categoryService.verifyLeafCategory(categoryId)` 호출

### DoD
- [ ] Product 6 Service 확장 반영
- [ ] 통합 테스트

### SP: 0.5d

---

## [Story 3-3] ADR

- ADR 1건: `031-category-2-depth-hierarchy-and-seeder.md`
- `backend-boundary/error-codes.md` CAT001~004

### SP: 0.5d

---

## 요약
| Epic | Story | SP |
|---|---|---|
| Epic 1 | 2 | 1.5 |
| Epic 2 | 2 | 1.5 |
| Epic 3 | 3 | 1.5 |
| **합계** | **7** | **4.5 SP** |

## 완결 → 후속
- Search (Product 18): 카테고리 필터
- Project (Product 6): categoryId 검증 활성화
