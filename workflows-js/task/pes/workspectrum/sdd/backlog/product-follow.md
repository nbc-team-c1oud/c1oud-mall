# [Product 15] 팔로우 (Follow · 메이커 팔로우 관계 · 반정규화 로드맵)

## Product Vision
> 후원자가 관심 메이커를 팔로우하고, 메이커는 자기 팬층 규모를 파악하는 **팔로우 관계 도메인**을 제공한다. 초기 실시간 COUNT로 팔로워 수를 노출하고, 팔로워 1000+ 사용자 발생 시(v0.0.4+) Maker Profile에 `follower_count` 반정규화로 승격한다. Notification (v0.0.5+)에서 팔로우한 메이커의 신규 프로젝트 알림 진입점 확보.

## 배경 및 문제
- 현재 상황 (As-Is)
  - c1oud-mall은 메이커·후원자 관계가 채팅·후원 이력만으로 표현 · 지속 관계 부재
  - 후원자가 좋아하는 메이커의 신규 프로젝트를 놓치기 쉬움
  - Maker Profile (Product 10)의 aside 카드에 "팔로워 1.2k" UI 있음 · 원천 도메인 부재
- 발생하는 문제
  - 재방문·재후원 유도 부족 · 사용자 이탈률
  - 메이커가 자기 팬층 파악 불가
- 왜 지금 해결해야 하는가
  - Maker Profile 완결 · 팔로워 수 UI 요청됨
  - Notification (v0.0.5+) 진입점 필요
  - 초기 사용자 없어 반정규화 시점 조율 여유

## 목표 (To-Be)
- 신규 컨텍스트: `nbc.c1oud_mall.follow.*` (4레이어)
- `Follow` 엔티티 (관계 저장 · Follower_id + Following_id)
- REST 5개: follow · unfollow · followers · followings · isFollowing
- Maker Profile 팔로워 수 실시간 COUNT (초기) · v0.0.4+ 반정규화 승격
- `ErrorCode.FLW001~003`
- 관측 지표 3종
- 자기 자신 팔로우 방지

## 설계 결정 (Design Decisions)

- **`Follow` 별도 컨텍스트** — User 확장이 아닌 관계 도메인
- **UNIQUE(follower_id, following_id)** — 중복 팔로우 방지
- **팔로우 대상 = 모든 사용자** (Maker만 X · Backer도 팔로우 가능 · 향후 확장 여지)
  - 초기 UI는 메이커 팔로우 UX만 노출 · 도메인은 general
- **초기 실시간 COUNT** — 팔로워 1000+ 발생 시 반정규화 (v0.0.4+)
  - `Maker Profile.follower_count` 반정규화 추가 예정 · 지금은 실시간 조회
- **자기 자신 팔로우 방지** — 도메인 메서드에서 검증
- **Soft Delete X** — Follow 관계는 실 삭제 (unfollow) · 이력은 별도 audit 로그 (v0.0.5+)
- **팔로워 수 조회는 별도 API** — Maker Profile Response에 포함할지 결정 · 초기 별도 조회 (`/users/{id}/followers/count`)

## 대안 검토

### 관계 저장
**Option A — 단일 Follow 테이블 (선택)** — 단순
**Option B — Follower Aggregate + FollowRelation** — 오버킬

### 반정규화 시점
**Option A — 초기 실시간 · v0.0.4+ 승격 (선택)** — 신입 스코프 · 후속 여지
**Option B — 처음부터 반정규화** — 이벤트 처리 부담

## 전체 아키텍처

### 컴포넌트 배치
```
presentation ──▶ application ──▶ domain ◀── infrastructure
FollowController   FollowService     Follow          FollowRepository
- follow           - follow          - create()      - findByFollowerAndFollowing
- unfollow         - unfollow        - verifySelf()  - countByFollowing
- followers        - followers                       - countByFollower
- followings       - followings                      - findFollowers(...)
- isFollowing      - isFollowing                     - findFollowings(...)

External refs:
- User (auth · 기존) — follower_id, following_id
- Maker Profile (Product 10) — v0.0.4+ 반정규화 승격 시 갱신 훅
- Notification (v0.0.5+) — 팔로우한 메이커 신규 프로젝트 알림 진입
```

### 핵심 플로우

**1. 팔로우**
```
FE → POST /api/v1/users/{id}/follow
   → FollowService.follow(followerId, followingId)
     ├── 자기 자신 검증 · FLW003
     ├── 이미 팔로우? · FLW002
     ├── Follow.create(...)  → save
     └── (v0.0.4+) MakerProfile.incrementFollowerCount
   ← 201
```

**2. 언팔로우**
```
FE → DELETE /api/v1/users/{id}/follow
   → FollowService.unfollow(followerId, followingId)
     ├── 관계 조회 · 없으면 FLW001
     ├── repository.delete
     └── (v0.0.4+) MakerProfile.decrementFollowerCount
   ← 204
```

**3. 팔로워 목록 · 팔로잉 목록**
```
GET /api/v1/users/{id}/followers?page=&size=  · 공개
GET /api/v1/users/{id}/followings?page=&size= · 공개
```

**4. 팔로우 여부**
```
GET /api/v1/users/{id}/is-following · JWT · 로그인 사용자가 대상을 팔로우하는지
```

## 실패 모드

| 시나리오 | ErrorCode | HTTP |
|---|---|---|
| 팔로우 관계 없음 (언팔 시) | `FLW001` FOLLOW_NOT_FOUND | 404 |
| 이미 팔로우 | `FLW002` FOLLOW_ALREADY_EXISTS | 409 |
| 자기 자신 팔로우 시도 | `FLW003` FOLLOW_SELF_NOT_ALLOWED | 400 |

## 관측 지표
- `follow.total{action=follow|unfollow}` — counter
- `follow.count.gauge{userId}` — 초기 X · v0.0.4+ 활성
- `follow.batch.duration_seconds` — 반정규화 배치 (v0.0.4+)

## Scope

**In (v0.0.3)**:
- Follow 엔티티 · Repository · Service · REST 5개
- 실시간 COUNT
- `ErrorCode.FLW001~003`

**Out (v0.0.4+)**:
- Maker Profile follower_count 반정규화
- 팔로우 이력 audit
- 팔로우 추천 (당신이 좋아할 만한 메이커)
- Mute · Block 기능 (v0.0.5+)
- Notification 진입점 활성화 (v0.0.5+)

## Epic
- [ ] Epic 1: `Follow` + Repository + `ErrorCode.FLW001~003`
- [ ] Epic 2: `FollowService` + REST 5개 + Maker Profile 통합 조회

## 관련 문서
- **이슈**: `issue-03-follow.md`
- **선행 SDD**: `product-maker.md` (Product 10)

## 열린 질문
- **팔로워 수 표시 방법** — 정확한 숫자 vs "1.2k" 등 축약
- **반정규화 승격 시점** — 팔로워 1000+ 자연 발생? or 정책적으로 v0.0.4+ 획일
- **비공개 팔로우** — Twitter의 protected 사용자 유사 · v0.0.5+

## Product-level DoD
- [ ] Epic 2개 완료
- [ ] E2E: follow → followers 조회 → unfollow → is-following false
- [ ] 자기 자신 팔로우 검증
- [ ] `backend-boundary/error-codes.md` FLW001~003

---

# [Epic 1] `Follow` + Repository + ErrorCode

## Story
- 1-1: 엔티티 · 도메인 메서드
- 1-2: Repository · ErrorCode

---

## [Story 1-1] 엔티티

### 스키마
```sql
CREATE TABLE follow (
  id             BIGINT    NOT NULL AUTO_INCREMENT,
  follower_id    BIGINT    NOT NULL,
  following_id   BIGINT    NOT NULL,
  created_at     TIMESTAMP NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_follow_pair (follower_id, following_id),
  KEY idx_follow_following (following_id, created_at DESC),
  KEY idx_follow_follower (follower_id, created_at DESC)
);
```

**엔티티**:
```java
@Entity
@Table(name = "follow")
@NoArgsConstructor(access = PROTECTED)
@Getter
public class Follow {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long followerId;

    @Column(nullable = false)
    private Long followingId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static Follow create(Long followerId, Long followingId) {
        if (Objects.equals(followerId, followingId))
            throw new BusinessException(ErrorCode.FLW003);
        Follow f = new Follow();
        f.followerId = followerId;
        f.followingId = followingId;
        f.createdAt = LocalDateTime.now();
        return f;
    }
}
```

### AC
- `create(1, 2)` 성공
- `create(1, 1)` → FLW003

### DoD
- [ ] 엔티티
- [ ] 단위 테스트

### SP: 0.5d

---

## [Story 1-2] Repository + ErrorCode

### Repository
```java
public interface FollowRepository extends JpaRepository<Follow, Long> {
    Optional<Follow> findByFollowerIdAndFollowingId(Long follower, Long following);
    boolean existsByFollowerIdAndFollowingId(Long follower, Long following);
    long countByFollowingId(Long followingId);
    long countByFollowerId(Long followerId);

    Page<Follow> findByFollowingIdOrderByCreatedAtDesc(Long followingId, Pageable pageable);
    Page<Follow> findByFollowerIdOrderByCreatedAtDesc(Long followerId, Pageable pageable);
}
```

### ErrorCode
- FLW001 FOLLOW_NOT_FOUND (404)
- FLW002 FOLLOW_ALREADY_EXISTS (409)
- FLW003 FOLLOW_SELF_NOT_ALLOWED (400)

### SP: 0.5d

---

# [Epic 2] `FollowService` + REST 5개

## Story
- 2-1: FollowService (follow · unfollow)
- 2-2: FollowService (followers · followings · isFollowing)
- 2-3: `FollowController` REST 5개 + ADR

---

## [Story 2-1] follow · unfollow

### 설명
```java
@Service
@Transactional
@RequiredArgsConstructor
public class FollowService {
    private final FollowRepository repository;
    private final MeterRegistry meterRegistry;

    public void follow(Long followerId, Long followingId) {
        if (repository.existsByFollowerIdAndFollowingId(followerId, followingId))
            throw new BusinessException(ErrorCode.FLW002);
        Follow f = Follow.create(followerId, followingId);
        repository.save(f);
        meterRegistry.counter("follow.total", "action", "follow").increment();
    }

    public void unfollow(Long followerId, Long followingId) {
        Follow f = repository.findByFollowerIdAndFollowingId(followerId, followingId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FLW001));
        repository.delete(f);
        meterRegistry.counter("follow.total", "action", "unfollow").increment();
    }
}
```

### AC
- follow 성공 · 중복 시 FLW002
- unfollow 성공 · 없을 시 FLW001
- 자기 자신 follow · FLW003

### DoD
- [ ] Service
- [ ] 통합 테스트

### SP: 0.5d

---

## [Story 2-2] followers · followings · isFollowing

### 설명
```java
@Transactional(readOnly = true)
public Page<FollowUserResponse> followers(Long userId, Pageable pageable) {
    return repository.findByFollowingIdOrderByCreatedAtDesc(userId, pageable)
        .map(f -> new FollowUserResponse(f.getFollowerId(), f.getCreatedAt()));
}

public Page<FollowUserResponse> followings(Long userId, Pageable pageable) {
    return repository.findByFollowerIdOrderByCreatedAtDesc(userId, pageable)
        .map(f -> new FollowUserResponse(f.getFollowingId(), f.getCreatedAt()));
}

public boolean isFollowing(Long followerId, Long followingId) {
    return repository.existsByFollowerIdAndFollowingId(followerId, followingId);
}

public FollowStatsResponse stats(Long userId) {
    return new FollowStatsResponse(
        repository.countByFollowingId(userId),
        repository.countByFollowerId(userId)
    );
}
```

### DoD
- [ ] Service · Response
- [ ] 통합 테스트

### SP: 0.5d

---

## [Story 2-3] REST 5개 + ADR

### 엔드포인트
| Method | Path | 인증 |
|---|---|---|
| POST | `/api/v1/users/{id}/follow` | JWT |
| DELETE | `/api/v1/users/{id}/follow` | JWT |
| GET | `/api/v1/users/{id}/followers` | 공개 |
| GET | `/api/v1/users/{id}/followings` | 공개 |
| GET | `/api/v1/users/{id}/follow-stats` | 공개 |

### ADR
- `032-follow-domain-and-follower-count-denormalization-roadmap.md`

### DoD
- [ ] Controller
- [ ] `@WebMvcTest`
- [ ] ADR

### SP: 1d

---

## 요약
| Epic | Story | SP |
|---|---|---|
| Epic 1 | 2 | 1.0 |
| Epic 2 | 3 | 2.0 |
| **합계** | **5** | **3.0 SP** |

## 완결 → 후속
- Maker Profile (10): v0.0.4+ follower_count 반정규화 승격
- Notification (v0.0.5+): 팔로우한 메이커 신규 프로젝트 알림
