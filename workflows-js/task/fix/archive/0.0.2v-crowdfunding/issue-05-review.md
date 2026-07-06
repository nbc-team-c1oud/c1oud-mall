# Issue: 상품 리뷰 도메인 신설 (구매자 검증 · 소프트 딜리트 · 별점)

## 크라우드 펀딩 컨셉 재해석 (0.0.2v 확장)

> 이 이슈는 초기 일반 쇼핑몰 컨텍스트로 작성되었으나, 0.0.2v 컨셉 확정(크라우드 펀딩)에 따라 재해석함.

### 도메인 리매핑
| 기존 | 재해석 |
|---|---|
| 상품 리뷰 (구매자만) | **프로젝트 리뷰 (후원자만)** |
| 결제 확정(`Payment.COMPLETED`) 검증 | **Pledge 확정(FUNDED) + 프로젝트 종료(COMPLETED) 검증** |
| 상품 리뷰 시점 | **프로젝트 완료 후** (리워드 발송 완료 or SUCCESSFUL 상태) |

### 리뷰 UX (디자인 검토)
디자인(`프로젝트 상세.html`) 검토 결과:
- 상세 페이지에 명확한 리뷰 UI 없음 (커뮤니티 섹션이 채팅 중심)
- **결정**: 리뷰 UI는 v0.0.4+에 추가 (프로젝트 SUCCESSFUL 후 후원자에게 리뷰 요청)
- 초기 v0.0.3에는 도메인/API만 갖춰두고 UI 후속

### 검증 순서 재정의
1. Pledge 조회 · 소유권 검증 (본인이 후원)
2. Pledge 상태 = `FUNDED` (지급 완료)
3. Project 상태 = `SUCCESSFUL` or `COMPLETED` (실패한 프로젝트에 리뷰 X)
4. 중복 리뷰 방지 (`(project_id, reviewer_user_id)` UNIQUE)

### 관련 신규 이슈
- [#08 Project 도메인](./issue-08-project-domain.md) — Project.ratingAvg 반정규화
- [#10 Pledge 상태기계](./issue-10-pledge-state-machine.md) — Pledge.status FUNDED 검증
- [#06 검색](./issue-06-search.md) — "별점순" 정렬

### 디자인 참조
- `C:\Users\user\Desktop\fe\프로젝트 상세.html` — 향후 리뷰 섹션 추가 검토

---

## 배경

> **사용자 지시**: "리뷰 기능을 붙여나갈 것" · Cabbage-Market-10 벤치마크.

- 현재 c1oud-mall은 결제 확정 후 별도 후기 채널 부재 · 신뢰 신호(사회적 증거) 없음
- 리뷰는 상품 상세 UX·검색 정렬(별점순)·판매자 평판의 핵심 데이터
- 리뷰 어뷰징(구매 안 한 자의 악성 리뷰) 방지가 도입 시점부터 규칙화 필요 → **구매 확정된 자만 리뷰 가능**
- 별점 평균·리뷰 개수가 상품 조회마다 필요 → 반정규화 필드 (`product.rating_avg` · `product.rating_count`) 도입

## 조사 결과 — Cabbage-Market-10 벤치마크

| 항목 | Cabbage 방식 | 우리 대응 |
|---|---|---|
| 엔티티 | `Review(item, reviewer, reviewee, rating, content, isDeleted)` | 유사 · `reviewee`(판매자) 필드는 초기 스코프 제외 (seller 도메인 미확정) |
| 검증 순서 | (1) 거래 완료(`SOLD_OUT`) · (2) 판매자 본인 X · (3) 구매자 일치 (일반 거래) or 낙찰자 일치 (경매) | 우리 대응: **결제 확정(`Payment.COMPLETED`) + Order 소유자 일치** |
| 중복 방지 | `UniqueConstraint(item_id, reviewer_id)` + `DataIntegrityViolationException catch → REVIEW_ALREADY_EXISTS` (S+ 이중) | 채택 · `(product_id, reviewer_user_id)` UNIQUE |
| Soft Delete | `@SQLDelete` UPDATE isDeleted=true + `@SQLRestriction("is_deleted = false")` | 채택 |
| 파사드 | `ReviewFacade`가 Item + Client + AuctionStatus 조회를 조합 | 우리: `ReviewService`에서 Order·Payment 참조 (직접 호출) |
| REST | `POST /api/items/{itemId}/reviews` (작성) · 수정/삭제 API는 코드에 미구현 (문서 명시) | 우리는 초기부터 CRUD 4종 지원 (본인만) |

## 옵션 비교

**Option A — 결제 확정 검증 + 상품당 1인 1리뷰 + Soft Delete + 별점 반정규화** `(채택)`
- 장점: 어뷰징 방지 · 반정규화로 조회 성능 · Cabbage 실 사례
- 비용: 리뷰 작성/삭제 시 `product.rating_avg` · `rating_count` 재계산 (동일 TX)
- 결제 확정 후 UX 흐름 자연스러움

**Option B — 검증 없이 누구나 리뷰**
- 거부 이유: 어뷰징 취약 · 신뢰도 낮음

**Option C — 하나의 결제당 1리뷰 (상품당 여러 결제면 여러 리뷰 가능)**
- 거부 이유: 상세 화면 노이즈 · 스팸 유도

**Option D — Elasticsearch 별점 집계**
- 거부 이유: 초기 규모 오버킬 · 반정규화 컬럼으로 충분

## 선택: Option A

## 부속 결정

### 도메인 컨텍스트
- 신규 컨텍스트: `nbc.c1oud_mall.review.*` (4레이어)
- Product · Order · Payment 참조는 직접 호출 (`OrderQueryService.hasCompletedPurchase(userId, productId)`)

### 엔티티 · 스키마
```sql
CREATE TABLE review (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  product_id        BIGINT       NOT NULL,
  reviewer_user_id  BIGINT       NOT NULL,
  order_item_id     BIGINT       NOT NULL,   -- 어느 주문의 어느 항목 기반 리뷰인지
  rating            TINYINT      NOT NULL,   -- 1~5 (@Min 1 @Max 5)
  content           TEXT         NOT NULL,
  is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at        TIMESTAMP    NOT NULL,
  updated_at        TIMESTAMP    NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_review_product_reviewer (product_id, reviewer_user_id),   -- 상품당 1인 1리뷰
  KEY idx_review_product_created (product_id, created_at DESC),
  KEY idx_review_reviewer (reviewer_user_id, created_at DESC),
  KEY idx_review_deleted (is_deleted)
);

ALTER TABLE product ADD COLUMN rating_avg DECIMAL(3, 2) NOT NULL DEFAULT 0.00 AFTER like_count;
ALTER TABLE product ADD COLUMN rating_count BIGINT NOT NULL DEFAULT 0;
```

### 도메인 모델
```java
// review.domain.Review
@Entity
@Table(name = "review")
@SQLDelete(sql = "UPDATE review SET is_deleted = TRUE WHERE id = ?")
@SQLRestriction("is_deleted = FALSE")
public class Review extends BaseEntity {
    @Id @GeneratedValue Long id;
    Long productId;
    Long reviewerUserId;
    Long orderItemId;
    @Column(columnDefinition = "TINYINT") int rating;   // 1~5
    @Column(columnDefinition = "TEXT") String content;
    boolean isDeleted;

    public static Review of(Long productId, Long userId, Long orderItemId, int rating, String content) {
        if (rating < 1 || rating > 5)
            throw new BusinessException(ErrorCode.REVIEW_RATING_INVALID);
        Review r = new Review();
        r.productId = productId;
        r.reviewerUserId = userId;
        r.orderItemId = orderItemId;
        r.rating = rating;
        r.content = content;
        return r;
    }

    public void update(int rating, String content) {
        verifyOwnership();
        this.rating = rating;
        this.content = content;
    }

    public void verifyOwnership(Long userId) {
        if (!Objects.equals(this.reviewerUserId, userId))
            throw new BusinessException(ErrorCode.REVIEW_OWNERSHIP_FAILED);
    }
}

// product.domain.Product (기존 · 별점 반정규화 필드 추가)
public class Product {
    ...
    BigDecimal ratingAvg;   // 0.00 ~ 5.00
    long ratingCount;

    public void addRating(int rating) {
        BigDecimal newTotal = ratingAvg.multiply(BigDecimal.valueOf(ratingCount))
                                       .add(BigDecimal.valueOf(rating));
        this.ratingCount++;
        this.ratingAvg = newTotal.divide(BigDecimal.valueOf(ratingCount), 2, RoundingMode.HALF_UP);
    }

    public void removeRating(int rating) {
        if (ratingCount <= 0)
            throw new BusinessException(ErrorCode.PRODUCT_RATING_COUNT_UNDERFLOW);
        BigDecimal newTotal = ratingAvg.multiply(BigDecimal.valueOf(ratingCount))
                                       .subtract(BigDecimal.valueOf(rating));
        this.ratingCount--;
        this.ratingAvg = ratingCount == 0
                ? BigDecimal.ZERO
                : newTotal.divide(BigDecimal.valueOf(ratingCount), 2, RoundingMode.HALF_UP);
    }

    public void changeRating(int oldRating, int newRating) {
        BigDecimal newTotal = ratingAvg.multiply(BigDecimal.valueOf(ratingCount))
                                       .subtract(BigDecimal.valueOf(oldRating))
                                       .add(BigDecimal.valueOf(newRating));
        this.ratingAvg = newTotal.divide(BigDecimal.valueOf(ratingCount), 2, RoundingMode.HALF_UP);
    }
}
```

### 서비스 (핵심 흐름 · 검증 순서)
```java
// review.application.ReviewService
@Transactional
public Long createReview(Long userId, Long productId, ReviewCreateCommand cmd) {
    // 1. 결제 완료 여부 검증 (Order·Payment 협력 · 직접 호출)
    OrderItem completed = orderQueryService
        .findCompletedOrderItem(userId, productId, cmd.orderItemId())
        .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_PURCHASED));

    // 2. 중복 리뷰 사전조회 (S+ 1단계)
    if (reviewRepository.existsByProductIdAndReviewerUserId(productId, userId))
        throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);

    // 3. Product 비관 락 (별점 반정규화 원자성)
    Product product = productRepository.findByIdForUpdate(productId)
        .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

    // 4. Review 생성
    Review review;
    try {
        review = reviewRepository.saveAndFlush(
            Review.of(productId, userId, cmd.orderItemId(), cmd.rating(), cmd.content()));
    } catch (DataIntegrityViolationException e) {
        // S+ 2단계: 사전조회 통과 후 race 도착 → UNIQUE 위반
        throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
    }

    // 5. 별점 반정규화
    product.addRating(cmd.rating());

    return review.getId();
}
```

### API 표면
| 메서드 | 경로 | 인증 | 용도 |
|---|---|---|---|
| POST | `/api/v1/products/{productId}/reviews` | JWT | 리뷰 작성 (body: `{orderItemId, rating, content}`) |
| PATCH | `/api/v1/reviews/{reviewId}` | JWT | 본인 리뷰 수정 (rating · content) |
| DELETE | `/api/v1/reviews/{reviewId}` | JWT | 본인 리뷰 Soft Delete |
| GET | `/api/v1/products/{productId}/reviews?page=&sort=` | 공개 | 상품 리뷰 목록 (최신순 · 별점순) |
| GET | `/api/v1/users/me/reviews` | JWT | 내가 쓴 리뷰 목록 |

### ErrorCode (신규)
- `RV001` REVIEW_NOT_PURCHASED (403 · 구매 안 한 상품)
- `RV002` REVIEW_ALREADY_EXISTS (409 · 중복 · S+ 이중 방어)
- `RV003` REVIEW_NOT_FOUND (404)
- `RV004` REVIEW_OWNERSHIP_FAILED (403 · 타인 리뷰 수정/삭제)
- `RV005` REVIEW_RATING_INVALID (400 · rating < 1 or > 5)
- `LIKE001` (기존 이슈 #04와 동일 카테고리) `PRODUCT_RATING_COUNT_UNDERFLOW` (500)

### 정합성 · 멱등성
- POST 리뷰 = **비즈 식별자 (product_id + reviewer_user_id)** · S+ (사전조회 + DB UNIQUE) · Explicit reject 409
- 별점 반정규화 원자성 = `Product FOR UPDATE` 락 (좋아요와 동일 락)
- PATCH 수정 시 `oldRating` · `newRating` 차이만 반영 (`product.changeRating(old, new)`)
- DELETE Soft → `product.removeRating(rating)` 반영 (일관성 유지)

### 락 순서 (consistency §5 갱신)
- Review 생성 흐름: `Product FOR UPDATE` → `Review INSERT`
- 이슈 #04(좋아요)와 동일 · `Product` 락 자원 표에 통합

## 이관 산출물

- **BE-Story #05-1**: `review` 컨텍스트 신규 패키지 + `Review` 엔티티 + Repository
- **BE-Story #05-2**: `Product.ratingAvg`·`ratingCount` 컬럼 추가 + 도메인 메서드 `addRating`·`removeRating`·`changeRating`
- **BE-Story #05-3**: `ReviewService.createReview`·`update`·`softDelete` (S+ 멱등 + 별점 반정규화)
- **BE-Story #05-4**: Order·Payment 협력 포트: `OrderQueryService.findCompletedOrderItem(userId, productId, orderItemId)` (직접 호출)
- **BE-Story #05-5**: `ReviewController` REST 5개 엔드포인트
- **BE-Story #05-6**: `ErrorCode.RV001~005` 등록
- **BE-Story #05-7**: 통합 테스트 — 구매 안 한 리뷰 거부 · 중복 리뷰 race · 별점 평균 정확도 (반올림 프로퍼티 기반)
- **BE-Story #05-8**: 상품 조회 API 응답에 `ratingAvg`·`ratingCount` 필드 추가
- **FE-Story #05-1**: `src/features/review/ReviewList.tsx` (별점 · 페이지네이션 · 정렬)
- **FE-Story #05-2**: `src/features/review/ReviewForm.tsx` (별점 1~5 · 텍스트 폼)
- **FE-Story #05-3**: 상품 상세 페이지에 별점 요약 (평균 · 개수 · 분포 · v2)
- **FE-Story #05-4**: 마이페이지에 "내 리뷰" 리스트
- **Docs-Story #05-1**: `backend-boundary/error-codes.md` RV001~005 매핑
- **Docs-Story #05-2**: `.claude/rules/idempotency.md` §2 카탈로그에 "리뷰 작성" 추가 (S+ · 비즈 식별자 `(product_id, user_id)`)
- **SDD 개정**: 향후 `product-review.md` 작성 시 흡수

## 관련 이슈 / 문서

- 관련: [#04 좋아요](./issue-04-product-like.md) — Product 락 자원 공유 · 반정규화 카운트 패턴 동일
- 관련: [#06 검색](./issue-06-search.md) — 정렬 옵션 "별점순" (ratingAvg desc)
- 관련: [#03 팔로우](./issue-03-follow.md) — 팔로우한 사용자의 리뷰 피드 (v2)
- 관련: [#01 채팅](./issue-01-websocket-chat.md) — 리뷰 이전 판매자·구매자 사전 소통 채널
- 벤치마크 원본: `pcb2002/Cabbage-Market-10` — `application/facade/ReviewFacade.java` · `domain/review/`
- 규범 참조: `.claude/rules/idempotency.md` §2·§4 (S+ 등급), `.claude/rules/consitency.md` §5 (Product 락)
