# Issue: 사용자 간 팔로우 도메인 신설

## 크라우드 펀딩 컨셉 재해석 (0.0.2v 확장)

> 이 이슈는 초기 일반 쇼핑몰 컨텍스트로 작성되었으나, 0.0.2v 컨셉 확정(크라우드 펀딩)에 따라 재해석함.

### 도메인 리매핑
| 기존 | 재해석 |
|---|---|
| 사용자 간 팔로우 | **후원자 → 메이커 팔로우** (또는 사용자 간 대칭) |
| 팔로잉 목록 | **팔로우한 메이커의 신규 프로젝트 알림 대상** |

### 팔로우 UX (디자인 반영)
디자인(`프로젝트 상세.html`) 검토:

- 메이커 프로필 옆 "+ 팔로우" 버튼 (프로젝트 상세)
- 메이커의 팔로워 수 표기 ("팔로워 1,204")
- **프로젝트 팔로우도 함께 검토 필요** (신상 알림 대상)

### 확장 필요 (본 이슈 스코프 결정 필요)
- **User → User 팔로우** (본 이슈)
- **User → Project 팔로우** (프로젝트 팔로우) → 별도 검토 · 필요 시 본 이슈 확장
- 초기: User → User만 · 프로젝트 팔로우는 향후 v0.0.4+

### 관련 신규 이슈
- [#11 메이커 프로필](./issue-11-maker-profile-response-stats.md) — 팔로워 수 노출 UX
- [#08 Project 도메인](./issue-08-project-domain.md) — 향후 Project 팔로우 검토 시

### 디자인 참조
- `C:\Users\user\Desktop\fe\프로젝트 상세.html` — 메이커 팔로우 버튼

---

## 배경

> **사용자 지시**: "팔로우 기능을 붙여나갈 것" · Cabbage-Market-10 벤치마크.

- 현재 c1oud-mall의 `User` 도메인은 인증·프로필만 관리 · 사용자 간 관계 표현 부재
- 리뷰(이슈 #05) · 좋아요(이슈 #04) 등 사용자 활동이 활발해질 예정이므로, "이 사용자의 활동을 팔로우"라는 저에너지 참여 기능이 리텐션에 유효
- 현재는 관리자·구매자 정도의 얕은 역할 구분만 있으나, 향후 seller 확장 시 "seller 팔로우 → 신상품·할인 알림" UX로 이어질 여지 (알림 인프라는 별도 · v2)
- 자기 팔로우 · 중복 팔로우 · 언팔로우 후 재팔로우 등 엣지 케이스를 도입 시점부터 명확히 잡을 필요

## 조사 결과 — Cabbage-Market-10 벤치마크

| 항목 | Cabbage 방식 | 우리 대응 |
|---|---|---|
| 엔티티 | `Follow(follower, following)` · `@IdClass(FollowId)` 복합키 | 채택 |
| 자기 팔로우 방지 | `@Table(check = "follower_id <> following_id")` DB CHECK | 채택 |
| 서비스 | 별도 `FollowService`·`FollowFacade` 없음 · `ClientService`에서 `FollowRepository` 직접 사용 | **분리 (`FollowService` 별도)** — 후속 알림·집계 기능 확장 여지 |
| 컨트롤러 | `ClientController`에 통합 (`POST /api/clients/{clientId}/follows`) | **분리 (`FollowController` 별도)** — REST 리소스 명확화 |
| Soft Delete | 없음 · Hard delete | 채택 (재팔로우 시 새 row) |
| 감사 | `@CreatedDate createdAt` | 채택 · updatedAt은 불필요 (관계는 create/delete만) |

## 옵션 비교

**Option A — 복합키 (`@IdClass`) + DB CHECK + `FollowService`·`FollowController` 분리** `(채택)`
- 장점: DB 레벨 중복·자기참조 차단 · 서비스 분리로 알림·집계 후속 확장 여지
- 비용: `ClientController`에 통합하는 Cabbage 방식보다 파일 몇 개 늘어남
- 팔로우 관련 로직이 User 도메인에 몰리지 않아 유지보수성 우수

**Option B — Cabbage 방식 그대로 (User Controller 통합)**
- 거부 이유: 팔로우가 커지면 `UserController`가 responsibility 폭발 · REST 리소스도 `/api/v1/users/{id}/follows`로 강제됨

**Option C — 서로게이트 PK + `UNIQUE(follower_id, following_id)`**
- 거부 이유: 복합키가 더 의미적 · 서로게이트는 관계 자체를 조회 대상으로 삼을 때만 이점

## 선택: Option A

## 부속 결정

### 도메인 컨텍스트
- 신규 컨텍스트: `nbc.c1oud_mall.follow.*` (4레이어)
- User 도메인 참조는 `userId`(Long) FK만 (직접 호출 · ADR 006)

### 엔티티 · 스키마
```sql
CREATE TABLE follow (
  follower_id   BIGINT    NOT NULL,   -- 팔로우 하는 자
  following_id  BIGINT    NOT NULL,   -- 팔로우 받는 자
  created_at    TIMESTAMP NOT NULL,
  PRIMARY KEY (follower_id, following_id),
  CONSTRAINT chk_follow_not_self CHECK (follower_id <> following_id),
  KEY idx_follow_following_created (following_id, created_at DESC),   -- 팔로워 목록 조회
  KEY idx_follow_follower_created  (follower_id,  created_at DESC)    -- 팔로잉 목록 조회
);
```

### 도메인 모델
```java
// follow.domain.Follow (Aggregate root)
@Entity
@IdClass(FollowId.class)
@Table(name = "follow")
public class Follow {
    @Id Long followerId;
    @Id Long followingId;
    @CreatedDate LocalDateTime createdAt;

    public static Follow of(Long followerId, Long followingId) {
        if (Objects.equals(followerId, followingId))
            throw new BusinessException(ErrorCode.FOLLOW_SELF_NOT_ALLOWED);
        Follow f = new Follow();
        f.followerId = followerId;
        f.followingId = followingId;
        return f;
    }
}

// follow.domain.FollowId (복합키 · Serializable · equals/hashCode)
public record FollowId(Long followerId, Long followingId) implements Serializable {}
```

### API 표면
| 메서드 | 경로 | 인증 | 용도 |
|---|---|---|---|
| POST | `/api/v1/users/{userId}/follows` | JWT | 대상 사용자 팔로우 (본인은 request principal) |
| DELETE | `/api/v1/users/{userId}/follows` | JWT | 언팔로우 |
| GET | `/api/v1/users/me/followings` | JWT | 내가 팔로우한 사용자 목록 (페이징) |
| GET | `/api/v1/users/me/followers` | JWT | 나를 팔로우하는 사용자 목록 |
| GET | `/api/v1/users/{userId}/followings/count` | 공개 | 대상 사용자의 팔로잉 수 (프로필 UI) |
| GET | `/api/v1/users/{userId}/followers/count` | 공개 | 대상 사용자의 팔로워 수 |

### ErrorCode (신규)
- `FOL001` FOLLOW_SELF_NOT_ALLOWED (400)
- `FOL002` FOLLOW_ALREADY_EXISTS (409 · 중복 팔로우 시도)
- `FOL003` FOLLOW_NOT_FOUND (404 · 언팔로우 대상 없음)
- `FOL004` FOLLOW_TARGET_NOT_FOUND (404 · 대상 userId가 존재하지 않음)

### 멱등성 · 정합성
- POST는 **DB 유니크 제약(복합키) + 사전조회 이중** (S+ · idempotency §4)
  - 이미 존재 → `FOL002 409` (Explicit reject) 반환
- DELETE는 상태 기반 · 존재하지 않으면 `FOL003 404`
- Zone: 팔로우는 TX-bound zone 미포함 (독립 · consistency.md §2에 신규 zone 추가 필요 없음)

### 성능 · 카운트 정책
- 팔로워/팔로잉 카운트는 **초기엔 `COUNT(*)` 실시간 쿼리** · 인덱스로 커버
- 대량 팔로워 사용자(1만+) 발생 시 반정규화 카운트 컬럼(`User.followerCount` · `User.followingCount`) 도입 검토 (M3 · 좋아요 이슈 #04와 동일 패턴)

### 관측
- `follow.action.total{type=follow|unfollow}` counter — 참여율 지표

## 이관 산출물

- **BE-Story #03-1**: `follow` 컨텍스트 신규 패키지 + `Follow`·`FollowId` + Repository
- **BE-Story #03-2**: `FollowService.follow(followerId, followingId)` · `unfollow` · `getFollowings(userId, pageable)` · `getFollowers(userId, pageable)`
- **BE-Story #03-3**: `FollowController` REST 4~6개 엔드포인트
- **BE-Story #03-4**: `ErrorCode.FOL001~004` 등록
- **BE-Story #03-5**: 통합 테스트 (자기 팔로우 · 중복 팔로우 · 언팔 후 재팔로우)
- **FE-Story #03-1**: `src/features/follow/FollowButton.tsx` (토글 · 낙관 업데이트)
- **FE-Story #03-2**: 프로필 페이지에 팔로워/팔로잉 카운트 · 리스트 모달
- **FE-Story #03-3**: `useFollow(userId)` · `useFollowers(userId)` 훅
- **Docs-Story #03-1**: `backend-boundary/error-codes.md` FOL001~004 UX 매핑
- **SDD 개정**: 향후 `product-social.md` (Follow + Like 묶음) 작성 시 흡수

## 관련 이슈 / 문서

- 관련: [#04 좋아요](./issue-04-product-like.md) — 반정규화 카운트 패턴 재사용 (M3 진입 시)
- 관련: [#05 리뷰](./issue-05-review.md) — 팔로우한 사용자의 리뷰 피드 (v2 후속)
- 짝 이슈: 없음 · 독립 신규 도메인
- 벤치마크 원본: `pcb2002/Cabbage-Market-10` — `src/main/java/com/example/cabbagemarket10/domain/follow/`
- 규범 참조: `.claude/rules/idempotency.md` §2 (신규 카탈로그 항목 추가 필요), `.claude/rules/consitency.md`
