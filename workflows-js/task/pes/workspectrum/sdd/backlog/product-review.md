# [Product 17] 리뷰 (Project Review · FUNDED Pledge 검증 · 평점 반정규화)

## Product Vision
> 후원자가 실제로 FUNDED 완료된 프로젝트에만 리뷰를 남길 수 있는 **인증 리뷰 도메인**. 리뷰 자격은 Pledge (Product 8)의 `FUNDED` 상태로 검증하고, 평점 (`rating 1~5`)과 텍스트 (`content 2000자`)를 저장하며, Project.`ratingAvg · ratingCount` 반정규화 필드를 원자적으로 갱신한다. 후원자가 "실제 배송받은 사람만 리뷰 가능"임을 신뢰할 수 있게 하여 리뷰 품질을 담보한다.

## 배경 및 문제
- 현재 상황
  - Project (Product 6)에 `ratingAvg · ratingCount` 필드 있음 · 리뷰 원천 도메인 부재
  - Pledge (Product 8) FUNDED 상태 존재 · 실 리워드 지급 검증 가능
  - Kickstarter는 후원 완료자만 프로젝트 코멘트 · 이 프로젝트에선 정식 리뷰로 격상
- 문제
  - 후원자가 프로젝트 신뢰도 판단 지표 부족
  - 메이커 응답 지표(Maker Profile Product 10)와 함께 신뢰 신호 필요
- 왜 지금
  - Pledge 완결 · FUNDED 상태 판정 가능 시점
  - Maker Profile 완결 · 리뷰가 응답 지표 등과 함께 신뢰 종합 지수 형성

## 목표 (To-Be)
- 신규 컨텍스트: `nbc.c1oud_mall.review.*` (4레이어)
- `Review` 엔티티 · UNIQUE(user_id, project_id) · 프로젝트당 사용자 1건
- 필드: `id · userId · projectId · rating (1~5) · content (2000자) · deletedAt · createdAt · updatedAt`
- **자격 검증**: 사용자의 Pledge가 해당 프로젝트에서 FUNDED 상태여야 함
- REST 5개: create · update · delete · listByProject · myReviews
- **Project.ratingAvg · ratingCount 원자적 갱신** (Product 6 확장)
- `ErrorCode.RVW001~005` (5개)
- 관측 지표 3종
- Soft Delete (부적절 리뷰 관리자 조치 여지)

## 설계 결정
- **FUNDED Pledge 검증 필수** — 실 지급 검증된 후원자만
  - 자기 프로젝트 리뷰 방지 (메이커가 자기 프로젝트에 후원 못 함)
- **프로젝트당 사용자 1건** — UNIQUE(user_id, project_id)
información  - 여러 리워드 티어 후원해도 리뷰는 1건
- **rating 1~5 정수** — 별점 UX 표준
- **content 2000자** — 리뷰 상세 서술 가능
- **평점 반정규화** — Project.ratingAvg (DECIMAL 3,2) · ratingCount
  - 리뷰 CRUD 시 원자적 갱신 · avg 재계산
- **Soft Delete** — 부적절 리뷰 · 관리자 조치 · 삭제해도 통계 재계산
- **수정 시 평점 재계산** — 트리거 시점 유의
- **자기 리뷰 삭제 허용** — 삭제 시 avg/count 재계산

## 대안 검토

### 자격
**Option A — FUNDED Pledge (선택)** — 실 지급자만 · 리뷰 품질 담보
**Option B — 후원한 사용자 모두 (PLEDGED · PAID 등)** — 배송 전 리뷰 · 품질 저하

### 수정 정책
**Option A — 무제한 수정 허용 (선택)** — UX 유연
**Option B — 1회만** — 초심자 실수 대응 어려움

### 평점 저장
**Option A — DECIMAL(3,2) 반정규화 (선택)** — 소수점 2자리 (4.35)
**Option B — 실시간 AVG(rating) 조회** — 조회 부담

## 전체 아키텍처

```
presentation ──▶ application ──▶ domain ◀── infrastructure
ReviewController   ReviewService     Review           ReviewRepository
- create           - create          - create()       - findByUserAndProject
- update           - update          - update()       - findByProjectId
- delete           - delete          - softDelete()   - findByUserId
- listByProject    - listByProject   Project 확장     - findAvgAndCountByProjectId
- myReviews        - myReviews       - updateRatingStats()
                                     - decrementRatingStats()

External refs:
- Project (Product 6) — ratingAvg · ratingCount 갱신
- Pledge (Product 8) — FUNDED 상태 검증
```

### 핵심 플로우

**1. 리뷰 작성**
```
POST /api/v1/projects/{id}/reviews {rating, content}  · JWT
  → ReviewService.create(userId, projectId, cmd)
    ├── FUNDED Pledge 존재 검증 · RVW003
    ├── 중복 검증 (이미 리뷰) · RVW002
    ├── Review.create(...)  → save
    └── recalcProjectRating(projectId) → project.updateRatingStats(newAvg, newCount)
  ← 201
```

**2. 리뷰 수정**
```
PATCH /api/v1/reviews/{id} {rating, content}  · JWT
  → ReviewService.update(userId, reviewId, cmd)
    ├── 본인 검증 · RVW004
    ├── review.update(...)
    └── recalcProjectRating(projectId)
```

**3. 리뷰 삭제 (Soft)**
```
DELETE /api/v1/reviews/{id}
  → 본인 or 관리자 · review.softDelete()
  → recalcProjectRating(projectId)
```

**4. 프로젝트 리뷰 목록**
```
GET /api/v1/projects/{id}/reviews?page=&sort=  · 공개
```

**5. 내 리뷰 목록**
```
GET /api/v1/users/me/reviews?page=  · JWT
```

## 실패 모드

| 시나리오 | ErrorCode | HTTP |
|---|---|---|
| 리뷰 없음 | `RVW001` REVIEW_NOT_FOUND | 404 |
| 이미 리뷰 존재 | `RVW002` REVIEW_ALREADY_EXISTS | 409 |
| FUNDED Pledge 없음 (자격 미달) | `RVW003` REVIEW_NOT_ELIGIBLE | 403 |
| 본인 아님 | `RVW004` REVIEW_OWNERSHIP_FAILED | 403 |
| rating 범위 위반 (1~5 외) | `RVW005` REVIEW_INVALID_RATING | 400 |

## 관측 지표
- `review.total{action=create|update|delete}` — counter
- `review.rating.gauge` — 전체 평균 평점
- `review.by_project.gauge{projectId}` — 프로젝트별 리뷰 수 (필요 시)

## Scope

**In**:
- Review 엔티티 · Repository · Service · REST 5개
- Project 확장 · ratingAvg/ratingCount 갱신
- `ErrorCode.RVW001~005`

**Out**:
- **리뷰 이미지 첨부** — Media Product 11 확장 · v0.0.4+
- **메이커 응답 (Reply)** — v0.0.5+
- **리뷰 좋아요 · 신고** — v0.0.5+
- **정렬 축 다양화** (도움됨 순) — v0.0.5+
- **AI 요약 (리뷰 대량 시)** — v0.0.5+

## Epic
- [ ] Epic 1: `Review` + Repository + `ErrorCode.RVW001~005`
- [ ] Epic 2: `ReviewService` (자격 검증 · Project 갱신 · CRUD) + REST 5개 + ADR

## 관련 문서
- **이슈**: `issue-05-project-review.md`
- **선행 SDD**: `product-project.md` (6) · `product-pledge.md` (8)
- **연결**: `product-maker.md` (10) 신뢰 지표 종합

## 열린 질문
- **부정 리뷰 조작 대응** — v0.0.5+ 신고·관리자 조치 정책
- **리뷰 언어 제한** — 초기 한국어 · 다국어 v0.0.5+
- **평균 산정 정확도** — DECIMAL(3,2) 반올림 정책 (HALF_UP)

## Product-level DoD
- [ ] Epic 2개 완료
- [ ] E2E: FUNDED pledge → review 작성 → project.ratingAvg 갱신
- [ ] 자격 미달 시 RVW003
- [ ] SSOT: `AVG(review.rating WHERE project_id=X, deleted_at IS NULL) == project.ratingAvg`
- [ ] `backend-boundary/error-codes.md` RVW001~005

---

# [Epic 1] `Review` + Repository + ErrorCode

## Story
- 1-1: 엔티티 + 도메인 메서드
- 1-2: Repository + ErrorCode

---

## [Story 1-1] 엔티티

### 스키마
```sql
CREATE TABLE review (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  user_id      BIGINT       NOT NULL,
  project_id   BIGINT       NOT NULL,
  rating       INT          NOT NULL,
  content      VARCHAR(2000) NOT NULL,
  deleted_at   TIMESTAMP    NULL,
  created_at   TIMESTAMP    NOT NULL,
  updated_at   TIMESTAMP    NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_review_user_project (user_id, project_id),
  KEY idx_review_project (project_id, created_at DESC),
  KEY idx_review_user (user_id, created_at DESC),
  KEY idx_review_deleted (deleted_at),
  CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5)
);
```

**엔티티**:
```java
@Entity
@Table(name = "review")
@SQLDelete(sql = "UPDATE review SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = PROTECTED)
@Getter
public class Review extends BaseEntity {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column
    private LocalDateTime deletedAt;

    public static Review create(Long userId, Long projectId, int rating, String content) {
        validateRating(rating);
        Review r = new Review();
        r.userId = userId;
        r.projectId = projectId;
        r.rating = rating;
        r.content = content;
        return r;
    }

    public void update(int rating, String content) {
        validateRating(rating);
        this.rating = rating;
        this.content = content;
    }

    public void verifyOwnership(Long userId) {
        if (!Objects.equals(this.userId, userId))
            throw new BusinessException(ErrorCode.RVW004);
    }

    private static void validateRating(int rating) {
        if (rating < 1 || rating > 5)
            throw new BusinessException(ErrorCode.RVW005);
    }
}
```

### AC
- `create(1,100,5,"굿")` 성공
- rating=6 · RVW005
- 중복 (user, project) · UNIQUE 위반

### DoD
- [ ] 엔티티 + 3개 메서드
- [ ] 단위 테스트

### SP: 1d

---

## [Story 1-2] Repository + ErrorCode

### Repository
```java
public interface ReviewRepository extends JpaRepository<Review, Long> {
    Optional<Review> findByUserIdAndProjectId(Long userId, Long projectId);
    boolean existsByUserIdAndProjectId(Long userId, Long projectId);

    Page<Review> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);
    Page<Review> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT COUNT(r), AVG(r.rating) FROM Review r WHERE r.projectId = :projectId AND r.deletedAt IS NULL")
    Object[] findCountAndAvgByProjectId(@Param("projectId") Long projectId);
}
```

### ErrorCode
- RVW001~005 (§실패 모드)

### DoD
- [ ] Repository
- [ ] ErrorCode
- [ ] `@DataJpaTest`

### SP: 0.5d

---

# [Epic 2] `ReviewService` + Project 확장 + REST 5개

## Story
- 2-1: Project 확장 (`updateRatingStats`)
- 2-2: ReviewService (create · update · delete · 자격 검증 · 평점 재계산)
- 2-3: ReviewService (list · my) + REST 5개 + ADR

---

## [Story 2-1] Project 확장

### 설명
Product 6 Project 엔티티에 추가:
```java
public void updateRatingStats(BigDecimal avg, long count) {
    this.ratingAvg = avg;
    this.ratingCount = count;
}
```

- Product 6 SDD Epic 3 개정 반영

### DoD
- [ ] Project 도메인 메서드
- [ ] 단위 테스트

### SP: 0.5d

---

## [Story 2-2] ReviewService CRUD + 자격 검증

### 설명
```java
@Service
@Transactional
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final ProjectRepository projectRepository;
    private final PledgeRepository pledgeRepository;   // Product 8
    private final MeterRegistry meterRegistry;

    public ReviewResponse create(Long userId, Long projectId, ReviewCreateCommand cmd) {
        // 자격 검증
        if (!pledgeRepository.existsByUserIdAndProjectIdAndStatus(userId, projectId, PledgeStatus.FUNDED))
            throw new BusinessException(ErrorCode.RVW003);
        // 중복 검증
        if (reviewRepository.existsByUserIdAndProjectId(userId, projectId))
            throw new BusinessException(ErrorCode.RVW002);

        Review review = Review.create(userId, projectId, cmd.rating(), cmd.content());
        reviewRepository.save(review);

        recalcProjectRating(projectId);
        meterRegistry.counter("review.total", "action", "create").increment();
        return ReviewResponse.from(review);
    }

    public ReviewResponse update(Long userId, Long reviewId, ReviewUpdateCommand cmd) {
        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RVW001));
        review.verifyOwnership(userId);
        review.update(cmd.rating(), cmd.content());
        recalcProjectRating(review.getProjectId());
        meterRegistry.counter("review.total", "action", "update").increment();
        return ReviewResponse.from(review);
    }

    public void delete(Long userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RVW001));
        review.verifyOwnership(userId);
        Long projectId = review.getProjectId();
        reviewRepository.delete(review);   // Soft Delete (SQLDelete)
        recalcProjectRating(projectId);
        meterRegistry.counter("review.total", "action", "delete").increment();
    }

    private void recalcProjectRating(Long projectId) {
        Object[] result = (Object[]) reviewRepository.findCountAndAvgByProjectId(projectId);
        long count = ((Number) result[0]).longValue();
        BigDecimal avg = (result[1] == null)
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(((Number) result[1]).doubleValue())
                .setScale(2, RoundingMode.HALF_UP);
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.LKE003));   // Project 참조
        project.updateRatingStats(avg, count);
    }
}
```

### AC
- FUNDED pledge 없음 · create · RVW003
- 정상 create · Project.ratingAvg 갱신
- 본인 아님 · update · RVW004
- 여러 리뷰 후 avg 재계산 정확 (프로퍼티 테스트)

### DoD
- [ ] Service · Command
- [ ] 통합 테스트
- [ ] SSOT invariant 테스트

### SP: 1.5d

---

## [Story 2-3] list · my + REST 5개 + ADR

### Service
```java
@Transactional(readOnly = true)
public Page<ReviewResponse> listByProject(Long projectId, Pageable pageable) {
    return reviewRepository.findByProjectIdOrderByCreatedAtDesc(projectId, pageable)
        .map(ReviewResponse::from);
}

public Page<ReviewResponse> myReviews(Long userId, Pageable pageable) {
    return reviewRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        .map(ReviewResponse::from);
}
```

### REST
| Method | Path | 인증 |
|---|---|---|
| POST | `/api/v1/projects/{id}/reviews` | JWT |
| PATCH | `/api/v1/reviews/{id}` | JWT |
| DELETE | `/api/v1/reviews/{id}` | JWT |
| GET | `/api/v1/projects/{id}/reviews` | 공개 |
| GET | `/api/v1/users/me/reviews` | JWT |

### ADR
- `034-review-funded-pledge-verification-and-rating-denormalization.md`

### DoD
- [ ] Service · Controller
- [ ] `@WebMvcTest`
- [ ] ADR

### SP: 1d

---

## 요약
| Epic | Story | SP |
|---|---|---|
| Epic 1 | 2 | 1.5 |
| Epic 2 | 3 | 3.0 |
| **합계** | **5** | **4.5 SP** |

## 완결 → 후속
- Project (6): 목록·상세에 평점 표시 활성
- Maker Profile (10): 신뢰 지표 종합에 평점 편입
- Search (18): 평점 순 정렬 축
- Review 이미지 (v0.0.4+): Media Product 11 확장
