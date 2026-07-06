# Issue: Project 도메인 신설 — Product 리네임 (URL slug · 커버 · 갤러리 · 펀딩 필드)

## 배경

> **컨셉 재정의 (Round 5)**: 상품(Product)은 이제 "프로젝트(Project)" · 판매가 아닌 크라우드 펀딩 대상.

- 기존 `Product` 도메인은 재고·가격·카테고리 필드만 있음
- 크라우드 펀딩 컨셉에서는:
  - 프로젝트 소개 페이지 (커버 이미지 · 스크린샷 갤러리 · 상세 스토리)
  - **펀딩 필드** (목표금액 · 모금액 · 후원자수 · 시작·종료일 · 상태)
  - **GitHub 리포지터리 연동** (이슈 #14)
  - **URL slug** (`c1oud.io/p/{slug}` · 짧고 SEO 친화)
  - **메이커(Maker)** 참조 (User FK · 이슈 #11)
- Product 도메인을 그대로 Project로 리네임 vs 신규 신설 결정 필요

## 조사 결과 — Product BC 현황 + 벤치마크

| 항목 | Product 현재 | Kickstarter/텀블벅 벤치마크 |
|---|---|---|
| 기본 필드 | id · name · price · stockQuantity · category · status · description | id · title · slug · category · maker(FK) · targetAmount · raisedAmount · backerCount · startDate · endDate · status · story(HTML) · shortDesc · coverImageUrl |
| 이미지 | 없음 (단일 name only) | 커버 이미지 (16:9) · 갤러리 (4~10장) · 데모 GIF/영상 |
| 펀딩 지표 | 없음 | targetAmount(long) · raisedAmount(long) · backerCount(int) 반정규화 |
| 상태 | SALE/SOLD_OUT | DRAFT · UNDER_REVIEW · UPCOMING · LIVE · SUCCESSFUL · FAILED · COMPLETED · CANCELLED |
| URL | `/products/{id}` (숫자) | `/p/{slug}` (문자열 슬러그) |
| GitHub 연동 | 없음 | Repo URL · stars · forks · commits · languages |

## 옵션 비교

**Option A — Product 도메인 리네임 → `Project` (컨텍스트 · 테이블 · 클래스 모두 리네임)** `(채택)`
- 장점: 컨셉 일치 · 도메인 명확 · 신규 개발자 진입 시 오해 없음
- 비용: 대규모 리네임 마이그레이션 (Cart · Order · Payment 등 참조 코드 수정)
- 마이그레이션 스크립트 필요 (테이블 rename 또는 CREATE + 데이터 복사)

**Option B — Product 유지 + Project는 별도 도메인 신설**
- 거부 이유: 두 도메인 병존 → 검색·주문 흐름에서 혼란 · 재고(중고니까 1개) 개념도 애매

**Option C — Product를 그대로 두고 크라우드 펀딩 관련 필드만 추가 (내부 개념만 Project)**
- 거부 이유: 코드베이스와 도메인 언어 불일치 · 유지보수 부담

## 선택: Option A

## 부속 결정

### 도메인 컨텍스트 리네임
- 기존 `nbc.c1oud_mall.product.*` → `nbc.c1oud_mall.project.*`
- 리네임 시점: v0.0.3 (SDD 진입 시 배치 리네임)
- 임시 v0.0.2v: `Project` 이슈로 이관 · 실 코드 리네임은 SDD 진입 후

### 엔티티 · 스키마
```sql
-- 리네임 (또는 새 테이블 생성 후 데이터 이관)
RENAME TABLE product TO project;

ALTER TABLE project
  -- 기본 확장
  ADD COLUMN slug VARCHAR(80) NOT NULL UNIQUE AFTER id,           -- c1oud.io/p/{slug}
  ADD COLUMN maker_user_id BIGINT NOT NULL AFTER slug,             -- 메이커 (User FK)
  ADD COLUMN short_description VARCHAR(200) NOT NULL AFTER name,   -- 한 줄 소개
  ADD COLUMN story LONGTEXT NULL AFTER description,                -- 프로젝트 스토리 (마크다운 or HTML)
  ADD COLUMN cover_image_url VARCHAR(500) NULL AFTER story,        -- 대표 이미지

  -- 펀딩 필드
  ADD COLUMN target_amount BIGINT NOT NULL DEFAULT 0 AFTER cover_image_url,  -- 목표 금액
  ADD COLUMN raised_amount BIGINT NOT NULL DEFAULT 0,                        -- 모금액 (반정규화 · Pledge 누적)
  ADD COLUMN backer_count INT NOT NULL DEFAULT 0,                            -- 후원자 수 (반정규화)
  ADD COLUMN started_at TIMESTAMP NULL,                                       -- 펀딩 시작
  ADD COLUMN ended_at TIMESTAMP NULL,                                         -- 펀딩 종료 (마감일)

  -- 상태
  ADD COLUMN funding_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',              -- FundingStatus enum

  -- 카테고리 (이슈 #02와 결합)
  ADD COLUMN category_id BIGINT NULL,

  -- 반정규화 (이슈 #04·#05)
  ADD COLUMN like_count BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN rating_avg DECIMAL(3,2) NOT NULL DEFAULT 0.00,
  ADD COLUMN rating_count BIGINT NOT NULL DEFAULT 0,

  -- 인덱스
  ADD KEY idx_project_status_ended (funding_status, ended_at),
  ADD KEY idx_project_maker (maker_user_id, funding_status),
  ADD KEY idx_project_category (category_id, funding_status);

-- 기존 컬럼 정리
ALTER TABLE project DROP COLUMN price;         -- 크라우드 펀딩엔 무의미
ALTER TABLE project DROP COLUMN stock_quantity;-- 재고 개념 없음
-- description은 유지 (짧은 설명) or short_description으로 이관 후 삭제
```

### 도메인 모델
```java
// project.domain.Project (Aggregate root · 기존 Product 리네임)
@Entity
@Table(name = "project")
public class Project extends BaseEntity {
    @Id @GeneratedValue Long id;
    @Column(unique = true) String slug;         // URL slug
    Long makerUserId;                            // 메이커 (이슈 #11)
    String name;                                 // 프로젝트 제목
    String shortDescription;                     // 한 줄 소개
    @Column(columnDefinition = "LONGTEXT") String story;   // 상세 스토리
    String coverImageUrl;

    long targetAmount;                           // 목표 금액
    long raisedAmount;                           // 모금액 (반정규화 · Pledge 누적)
    int backerCount;                             // 후원자 수 (반정규화)
    LocalDateTime startedAt;
    LocalDateTime endedAt;

    @Enumerated(STRING) FundingStatus fundingStatus;
    Long categoryId;
    long likeCount;
    BigDecimal ratingAvg;
    long ratingCount;

    public static Project draft(Long makerId, String name, String slug, ...) { ... }

    public void launch() {
        if (fundingStatus != DRAFT) throw new BusinessException(ErrorCode.PROJECT_INVALID_STATUS);
        this.fundingStatus = LIVE;
        this.startedAt = LocalDateTime.now();
    }

    public void addPledgeAmount(long amount) {
        this.raisedAmount += amount;
        this.backerCount++;
    }

    public void removePledgeAmount(long amount) {
        this.raisedAmount -= amount;
        this.backerCount--;
    }

    public boolean isFundingSuccessful() {
        return raisedAmount >= targetAmount;
    }
}

// project.domain.FundingStatus (enum)
public enum FundingStatus {
    DRAFT,           // 초안 (등록 중)
    UNDER_REVIEW,    // 심사 중 (v0.0.5+)
    UPCOMING,        // 펀딩 예정 (승인됨 · startedAt 대기)
    LIVE,            // 펀딩 진행 중
    SUCCESSFUL,      // 목표 달성 · 지급 대기
    FUNDED,          // 지급 완료 (프로젝트 진행 중)
    FAILED,          // 목표 미달성 · 환불 대기
    REFUNDED,        // 환불 완료
    COMPLETED,       // 프로젝트 완료 (리워드 발송 등)
    CANCELLED        // 메이커 취소
}
```

### API 표면
| 메서드 | 경로 | 인증 | 용도 |
|---|---|---|---|
| GET | `/api/v1/projects` | 공개 | 프로젝트 목록 (페이징 · 카테고리 · 상태 필터) |
| GET | `/api/v1/projects/{id}` | 공개 | 상세 조회 (id) |
| GET | `/api/v1/projects/p/{slug}` | 공개 | 상세 조회 (slug) — 디자인 URL |
| POST | `/api/v1/projects` | JWT | 프로젝트 생성 (초안 · 자동 저장) |
| PATCH | `/api/v1/projects/{id}` | JWT | 프로젝트 수정 (메이커 본인만) |
| POST | `/api/v1/projects/{id}/launch` | JWT | 프로젝트 오픈 (DRAFT → LIVE 또는 UPCOMING) |
| GET | `/api/v1/projects/search?keyword=&categoryId=&status=&sort=` | 공개 | 통합 검색 (이슈 #06) |
| GET | `/api/v1/users/me/projects` | JWT | 내가 만든 프로젝트 목록 |

### ErrorCode (신규)
- `PRJ001` PROJECT_NOT_FOUND (404)
- `PRJ002` PROJECT_SLUG_DUPLICATE (409 · slug 중복)
- `PRJ003` PROJECT_INVALID_STATUS (400 · 유효하지 않은 상태 전이)
- `PRJ004` PROJECT_OWNERSHIP_FAILED (403 · 메이커 본인 아님)
- `PRJ005` PROJECT_TARGET_AMOUNT_INVALID (400 · 목표 0 이하)
- `PRJ006` PROJECT_DURATION_INVALID (400 · 기간 오류)

### URL Slug 규칙
- 최소 3자 · 최대 80자
- 알파벳 소문자 · 숫자 · 하이픈만 (`^[a-z0-9-]+$`)
- 예약어 금지 (`api`, `admin`, `login`, `p` 등)
- 자동 생성 옵션: 프로젝트 제목 → 슬러그 변환 (한글 → 로마자 or 랜덤 접미)

### 정합성 · 멱등성
- Slug UNIQUE 제약 (DB 레벨)
- 프로젝트 생성 = 서버 채번 id + slug UNIQUE로 멱등 (idempotency §4 S 등급)
- 상태 전이는 도메인 메서드 (`launch()`, `markSuccessful()`, `markFailed()`, `complete()`)에서만 · 외부 직접 상태 변경 금지
- 락 순서 (consistency §5): `Wallet → Project → Pledge → Product(기존)`

### 관측
- `project.created.total{status}` counter
- `project.launched.total` counter
- `project.status.gauge{status}` — 상태별 프로젝트 수

## 이관 산출물

- **BE-Story #08-1**: `product` 컨텍스트 → `project` 컨텍스트 리네임 (SDD 진입 시 · 실 코드는 이슈 별도)
- **BE-Story #08-2**: `Project` 엔티티 필드 확장 (slug · maker · story · cover · funding 필드 · status enum)
- **BE-Story #08-3**: 마이그레이션 스크립트 (기존 Product 데이터 이관 or DROP · 초기라 폐기 검토)
- **BE-Story #08-4**: `Project.launch`·`markSuccessful`·`markFailed`·`addPledgeAmount` 도메인 메서드
- **BE-Story #08-5**: `ProjectService` (생성 · 수정 · launch · 조회) · `ProjectController` (8개 엔드포인트)
- **BE-Story #08-6**: URL slug 자동 생성기 + UNIQUE 재시도 로직
- **BE-Story #08-7**: `ErrorCode.PRJ001~006` 등록
- **BE-Story #08-8**: 통합 테스트 (생성 → launch → 상태 전이 · slug 중복 · 소유권)
- **FE-Story #08-1**: `src/features/project/ProjectDetailPage.tsx` (디자인 `프로젝트 상세.html` 기반)
- **FE-Story #08-2**: `src/features/project/ProjectEditorPage.tsx` (4단계 등록 · 자동 저장)
- **FE-Story #08-3**: `src/features/project/ProjectExplorePage.tsx` (디자인 `펀딩 탐색.html` 기반)
- **Docs-Story #08-1**: `backend-boundary/error-codes.md` PRJ001~006 매핑
- **Docs-Story #08-2**: URL slug 예약어 리스트 문서화
- **SDD 개정**: 향후 `product-project.md` 신규 · Product SDD는 폐기 or 아카이브

## 관련 이슈 / 문서

- 선행: [#07 Wallet](./issue-07-wallet-prepaid-balance.md) — 후원 결제 대상
- 다음: [#09 Reward Tier](./issue-09-reward-tier.md) — Project 하위 리워드 티어
- 다음: [#10 Pledge 상태기계](./issue-10-pledge-state-machine.md) — Pledge.projectId 참조
- 관련: [#02 카테고리](./issue-02-category-hierarchy.md) — Project.categoryId FK
- 관련: [#11 Maker Profile](./issue-11-maker-profile-response-stats.md) — Project.makerUserId 참조
- 관련: [#14 GitHub 연동](./issue-14-github-integration.md) — Project와 Repo 매핑
- 관련: [#13 Media Upload](./issue-13-media-upload-gallery.md) — 커버 · 갤러리 저장

## 디자인 참조
- `C:\Users\user\Desktop\fe\프로젝트 상세.html` — 상세 페이지 필드
- `C:\Users\user\Desktop\fe\프로젝트 등록.html` — 4단계 등록 워크플로우
- `C:\Users\user\Desktop\fe\펀딩 탐색.html` — 목록 카드 필드
