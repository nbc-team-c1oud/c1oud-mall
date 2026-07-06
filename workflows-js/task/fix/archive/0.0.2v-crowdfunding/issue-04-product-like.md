# Issue: 상품 좋아요 도메인 신설 (반정규화 카운트 · 토글)

## 크라우드 펀딩 컨셉 재해석 (0.0.2v 확장)

> 이 이슈는 초기 일반 쇼핑몰 컨텍스트로 작성되었으나, 0.0.2v 컨셉 확정(크라우드 펀딩)에 따라 재해석함.

### 도메인 리매핑
| 기존 | 재해석 |
|---|---|
| Product 좋아요 | **Project 좋아요** (관심 표시) |
| product.like_count | **project.like_count** (반정규화 카운트) |

### 좋아요 vs 찜(Wishlist) 구분 필요 (디자인 발견)
디자인(`펀딩 탐색.html`) 검토 결과 **하트 아이콘 = 찜(Wishlist)** 성격:
- 프로젝트 카드 우상단 하트 (`pcard-fav`)
- "저장·북마크" 의미 (관심 표시와 구분)

**결정 사항**:
- **Option A (채택)** — 좋아요 · 찜 통합 (하트 = 좋아요 = 찜) · 스코프 관리 우선
- Option B — 분리 (좋아요=참여 지표 · 찜=저장 북마크) · 도메인 명확 · 나중 검토
- 초기 v0.0.3에는 통합으로 시작 · v0.0.4+에 분리 검토

### 관련 신규 이슈
- [#08 Project 도메인](./issue-08-project-domain.md) — Project.likeCount 반정규화
- [#06 검색](./issue-06-search.md) — "인기순" 정렬 (likeCount desc)

### 디자인 참조
- `C:\Users\user\Desktop\fe\펀딩 탐색.html` — `pcard-fav` 하트 아이콘
- `C:\Users\user\Desktop\fe\프로젝트 상세.html` — `fund-fav` 찜 버튼

---

## 배경

> **사용자 지시**: "좋아요 기능을 붙여나갈 것" · Cabbage-Market-10 벤치마크.

- 현재 c1oud-mall `Product`는 담기(cart)·구매(order) 외에 참여 시그널 없음 · 리텐션·재방문 지표 부족
- 좋아요는 최저 마찰 참여 신호로 → 재방문 알림(v2) · 개인화 추천(v3) 데이터의 원천
- 좋아요 카운트 표시가 상품 목록에 매번 나타나야 하므로 매 조회마다 `COUNT(*)` 하면 목록 쿼리 폭발 → **반정규화 카운트 컬럼(`product.like_count`) 필수**
- 동시성: 여러 사용자가 같은 상품에 동시 좋아요 시 카운트 증감 race 발생 리스크

## 조사 결과 — Cabbage-Market-10 벤치마크

| 항목 | Cabbage 방식 | 우리 대응 |
|---|---|---|
| 엔티티 | `ItemLike(client, item)` · `@IdClass(ItemLikeId)` 복합키 | 채택 |
| 컨트롤러 | `ItemController.POST /api/items/{itemId}/likes` (토글) + `ClientController.GET /api/clients/me/likes` (내 좋아요 목록) | **분리** (`ProductLikeController`) · REST 리소스 명확화 |
| 서비스·파사드 | `ItemLikeService` (얇은 wrapper) + `ItemLikeFacade` (비즈 로직) — `getItemForUpdate` 비관 락 + 반정규화 카운트 증감 | 채택 (Facade 대신 `ProductLikeService` 하나로) |
| 반정규화 | `item.like_count` 컬럼을 서비스에서 동기 증감 | 채택 (`product.like_count`) |
| 락 | `ItemService.getItemForUpdate` (비관 락) | 채택 (`SELECT ... FOR UPDATE`) |
| Soft Delete | 없음 · Hard delete | 채택 |

## 옵션 비교

**Option A — 복합키 + 반정규화 카운트 + 비관 락 (Cabbage 방식)** `(채택)`
- 장점: 조회 성능 우수 · 목록 페이지에서 `like_count` 즉시 노출 · Cabbage 실 사례 검증됨
- 비용: 토글 시 락 획득 + 두 테이블(`product_like` · `product.like_count`) 갱신 (동일 TX)
- 락 순서: **`Product FOR UPDATE` → `ProductLike INSERT/DELETE`** (consistency §5 락 순서 표에 추가 필요)

**Option B — Redis atomic counter (`INCR/DECR`)**
- 거부 이유: 초기 규모 오버킬 · Redis 인프라 없음 · 정합성 후처리 필요

**Option C — 낙관 락 (`@Version`) + 재시도**
- 거부 이유: 좋아요 트래픽 몰릴 시(핫 상품) 재시도 폭증 · 사용자 UX 지연

**Option D — 카운트 없이 매번 `COUNT(*)` 실시간**
- 거부 이유: 상품 목록 쿼리 매번 좋아요 조인 필요 · 32건 목록 페이지에서 32개 서브쿼리 폭탄

## 선택: Option A

## 부속 결정

### 도메인 컨텍스트
- 신규 컨텍스트: `nbc.c1oud_mall.productlike.*` (4레이어)
  - 또는 `product.like.*` 서브패키지 검토 (팀 컨벤션 확인 · **별도 컨텍스트 채택** 이유: 향후 `Post` · `Comment` 등 다른 대상에 좋아요 확장 여지)
- Product FK만 참조 (직접 호출)

### 엔티티 · 스키마
```sql
CREATE TABLE product_like (
  user_id     BIGINT    NOT NULL,
  product_id  BIGINT    NOT NULL,
  created_at  TIMESTAMP NOT NULL,
  PRIMARY KEY (user_id, product_id),
  KEY idx_product_like_product (product_id, created_at DESC),   -- 상품별 좋아요 목록
  KEY idx_product_like_user (user_id, created_at DESC)          -- 내 좋아요 목록
);

ALTER TABLE product ADD COLUMN like_count BIGINT NOT NULL DEFAULT 0 AFTER stock_quantity;
```

### 도메인 모델
```java
// productlike.domain.ProductLike (Aggregate root)
@Entity
@IdClass(ProductLikeId.class)
@Table(name = "product_like")
public class ProductLike {
    @Id Long userId;
    @Id Long productId;
    @CreatedDate LocalDateTime createdAt;

    public static ProductLike of(Long userId, Long productId) { ... }
}

public record ProductLikeId(Long userId, Long productId) implements Serializable {}

// product.domain.Product (기존 · 필드 추가 + 메서드)
public class Product {
    ...
    long likeCount;

    public void incrementLike() { this.likeCount++; }
    public void decrementLike() {
        if (this.likeCount <= 0)
            throw new BusinessException(ErrorCode.PRODUCT_LIKE_COUNT_UNDERFLOW);
        this.likeCount--;
    }
}
```

### 서비스 (핵심 흐름)
```java
// productlike.application.ProductLikeService
@Service
@RequiredArgsConstructor
public class ProductLikeService {
    private final ProductLikeRepository likeRepository;
    private final ProductRepository productRepository;

    @Transactional
    public ProductLikeToggleResponse toggle(Long userId, Long productId) {
        // 1. Product 비관 락 획득 (락 순서: Product 먼저)
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        // 2. 기존 좋아요 존재 확인
        var id = new ProductLikeId(userId, productId);
        boolean existed = likeRepository.existsById(id);

        if (existed) {
            likeRepository.deleteById(id);
            product.decrementLike();
            return new ProductLikeToggleResponse(productId, false, product.getLikeCount());
        } else {
            likeRepository.save(ProductLike.of(userId, productId));
            product.incrementLike();
            return new ProductLikeToggleResponse(productId, true, product.getLikeCount());
        }
    }
}
```

### API 표면
| 메서드 | 경로 | 인증 | 용도 |
|---|---|---|---|
| POST | `/api/v1/products/{productId}/likes` | JWT | 좋아요 토글 (응답: `{productId, liked, likeCount}`) |
| GET | `/api/v1/users/me/likes` | JWT | 내가 좋아요한 상품 목록 (페이징) |
| GET | `/api/v1/products/{productId}/likes/users` | 선택 | 상품을 좋아요한 사용자 목록 (v2 · 프라이버시 검토) |

### ErrorCode (신규)
- `LIKE001` PRODUCT_LIKE_COUNT_UNDERFLOW (500 · 카운트 음수 시도 · 시스템 이상)
- `LIKE002` (예약)

### 정합성 · 멱등성
- 토글 = 상태 기반 멱등 (같은 요청 2회 → 순수 토글로 결과 왕복 · Idempotent response 아님 · "액션 성격" · **의도된 비멱등**)
- Race 방지: `SELECT ... FOR UPDATE` (락 순서 `Product → ProductLike`)
- Zone: 좋아요는 자체 TX-bound zone (Product 카운트 + Like 레코드가 같은 TX)

### 락 순서 갱신 (consistency §5)
현재 락 순서: `Order → Payment → Point → Inventory`
→ **추가**: `Product` (좋아요 · 재고 확정 등에서 획득) — **Order보다 앞** 또는 **Inventory와 동급**
→ 결정 필요 · 초안: `Product → Order → Payment → Point`

### 관측
- `product.like.toggle.total{result=liked|unliked}` counter
- `product.like.count.gauge{product_id}` — 카디널리티 위험 · sampling 검토

## 이관 산출물

- **BE-Story #04-1**: `productlike` 컨텍스트 신규 패키지 + `ProductLike`·`ProductLikeId` + Repository
- **BE-Story #04-2**: `Product.likeCount` 컬럼 추가 + 도메인 메서드 `incrementLike/decrementLike`
- **BE-Story #04-3**: `ProductRepository.findByIdForUpdate` (비관 락 · `SELECT ... FOR UPDATE`) 추가
- **BE-Story #04-4**: `ProductLikeService.toggle` (락 → 토글 → 카운트 증감 원자적)
- **BE-Story #04-5**: `ProductLikeController` (2개 엔드포인트)
- **BE-Story #04-6**: `ErrorCode.LIKE001~` 등록
- **BE-Story #04-7**: 동시성 통합 테스트 (같은 상품에 5명 동시 토글 → 카운트 정확성)
- **BE-Story #04-8**: 상품 목록 응답에 `likeCount` · `isLikedByMe` 필드 추가 (인증 사용자 시)
- **FE-Story #04-1**: `src/features/product/LikeButton.tsx` (토글 · 낙관 업데이트 · 에러 시 롤백)
- **FE-Story #04-2**: 상품 카드/상세에 좋아요 카운트 표시
- **FE-Story #04-3**: `/users/me/likes` 마이페이지 UI
- **Docs-Story #04-1**: `backend-boundary/error-codes.md` LIKE001 매핑
- **Docs-Story #04-2**: `.claude/rules/consitency.md` §5 락 순서 표에 Product 추가
- **SDD 개정**: 향후 `product-social.md` (Follow + Like 묶음)

## 관련 이슈 / 문서

- 짝 이슈: [#03 팔로우](./issue-03-follow.md) — 반정규화 카운트 패턴 · 참여 시그널 UX 함께
- 관련: [#02 카테고리](./issue-02-category-hierarchy.md) — 카테고리별 인기 상품(likeCount 기준) 정렬 (검색 이슈 #06과 결합)
- 관련: [#06 검색](./issue-06-search.md) — 정렬 옵션 "인기순" (likeCount desc)
- 벤치마크 원본: `pcb2002/Cabbage-Market-10` — `application/facade/ItemLikeFacade.java` · `domain/itemLike/`
- 규범 참조: `.claude/rules/consitency.md` §5 (락 순서 갱신 필요), `.claude/rules/idempotency.md` §1 (토글은 "액션 성격" · Idempotent response 아님)
