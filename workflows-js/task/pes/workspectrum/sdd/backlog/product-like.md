# [Product 16] 좋아요 · 위시리스트 (Project Like · Denormalized Count)

## Product Vision
> 후원자가 관심 프로젝트를 좋아요·위시리스트에 담을 수 있는 **Project Like 도메인**. **좋아요와 위시리스트를 통합**해 하나의 도메인으로 단순화하고, Project (Product 6)의 `like_count` 반정규화 필드에 원자적으로 반영해 프로젝트 목록·상세·탐색에서 즉시 활용한다.

## 배경 및 문제
- 현재 상황
  - Project (Product 6)에 `like_count` 필드 있음 · 원천 도메인 부재
  - 후원자가 마음에 드는 프로젝트를 저장·재방문할 수단 없음
  - Kickstarter는 "Save" (위시리스트) · Indiegogo는 "Follow"로 유사 UX
- 문제
  - Project 재방문 유도 부족
  - 인기 프로젝트 순위 산정 신호(좋아요 수) 부재
  - 후원자 관심사 파악 불가 (Discover 개인화의 원천)
- 왜 지금
  - Project 완결 후 사용자 인터랙션 도입 자연스러움
  - Search (Product 18) 정렬 축 확보

## 목표 (To-Be)
- 신규 컨텍스트: `nbc.c1oud_mall.like.*` (4레이어)
- `ProjectLike` 엔티티 · UNIQUE(user_id, project_id)
- REST 4개: like · unlike · myLikes · isLiked
- **Project.like_count 원자적 갱신** (like → +1 · unlike → -1) · Product 6 확장
- `ErrorCode.LKE001~003`
- 관측 지표 2종
- 좋아요 = 위시리스트 통합 (초기 확정)

## 설계 결정
- **좋아요·위시리스트 통합** — 별도 도메인 관리 부담 X · UX 단순
  - "좋아요" 문구 · 그러나 마이페이지 "저장된 프로젝트" 목록으로 노출
- **Project.like_count 반정규화 갱신** — Reward Tier에서와 같은 패턴 (원자적 · TX 참여)
  - `Project.incrementLikeCount() · decrementLikeCount()` 도메인 메서드 추가
- **UNIQUE(user_id, project_id)** — 중복 방지
- **Soft Delete X** — 실 삭제 (unlike)
- **자기 프로젝트 좋아요 허용** — 메이커도 자기 프로젝트 좋아요 가능 (Kickstarter도 허용)

## 대안 검토

### 좋아요 vs 위시리스트 분리
**Option A — 통합 (선택)** — 단순 · UX 명확
**Option B — 별개 도메인** — 오버킬 · UI 혼란

### 카운트 갱신 방식
**Option A — 반정규화 · Project.like_count 원자적 갱신 (선택)** — 조회 성능
**Option B — 실시간 COUNT** — 상세·목록 매번 조회 부담

## 전체 아키텍처

```
presentation ──▶ application ──▶ domain ◀── infrastructure
LikeController    LikeService     ProjectLike       ProjectLikeRepository
- like            - like          - create()        - findByUserAndProject
- unlike          - unlike        (Project 확장)    - existsByUserAndProject
- myLikes         - myLikes       Project           - findByUserOrderByCreatedAt
- isLiked         - isLiked       - incrementLike() - deleteByUserAndProject
                                  - decrementLike()

External refs:
- Project (Product 6) — like_count 반정규화 갱신
- Search (Product 18) — like_count 정렬 축
```

### 핵심 플로우

**1. 좋아요**
```
POST /api/v1/projects/{id}/like  · JWT
  → LikeService.like(userId, projectId)
    ├── 중복 검증 · LKE002
    ├── Project 조회 · LKE003 (프로젝트 없음 or COMPLETE/CANCELLED 상태에서 좋아요 방지?)
    │   → 초기: 모든 상태 허용 · 정책 여지 열어둠
    ├── ProjectLike.create(...)  → save
    └── project.incrementLikeCount()  → Product 6 도메인 메서드
```

**2. 좋아요 취소**
```
DELETE /api/v1/projects/{id}/like  · JWT
  → 관계 없으면 LKE001
  → repository.delete + project.decrementLikeCount()
```

**3. 내 좋아요 목록**
```
GET /api/v1/users/me/likes?page=&size= · JWT
  → 최신 순
```

**4. 좋아요 여부**
```
GET /api/v1/projects/{id}/is-liked · JWT
```

## 실패 모드

| 시나리오 | ErrorCode | HTTP |
|---|---|---|
| 좋아요 없음 (unlike) | `LKE001` LIKE_NOT_FOUND | 404 |
| 이미 좋아요 | `LKE002` LIKE_ALREADY_EXISTS | 409 |
| 프로젝트 없음 (like 시도) | `LKE003` PROJECT_NOT_FOUND (Project · PRJ001 재사용 or 신규) | 404 |

## 관측 지표
- `like.total{action=like|unlike}` — counter
- `like.project.gauge{projectId}` — 초기 X · Grafana 조회 · 필요 시 등록

## Scope

**In**:
- ProjectLike 엔티티 · Repository · Service · REST 4개
- Product 6 Project 확장 · like_count 원자적 갱신
- `ErrorCode.LKE001~003`

**Out**:
- **컬렉션 (Collection) 여러 개** — Kickstarter의 "My List" 유사 · v0.0.5+
- **좋아요 알림** — 메이커에게 알림 · v0.0.5+
- **좋아요 순 랭킹** — Product 18 Search에 편입

## Epic
- [ ] Epic 1: `ProjectLike` + Repository + `ErrorCode.LKE001~003`
- [ ] Epic 2: `LikeService` + Project 확장 (like_count 도메인 메서드) + REST 4개 + ADR

## 관련 문서
- **이슈**: `issue-04-project-like.md`
- **선행 SDD**: `product-project.md` (Product 6)

## 열린 질문
- **COMPLETED/CANCELLED 프로젝트 좋아요 허용?** — 초기 허용 (재방문 UX)
- **like_count 표시 정확성** — Big number 축약 vs 정확 (초기 정확 · UI에서 축약)
- **취소된 후원자에게 좋아요 유지?** — 유지 (별개 관계)

## Product-level DoD
- [ ] Epic 2개 완료
- [ ] E2E: like → project.like_count += 1 → myLikes 조회 → unlike → -=1
- [ ] SSOT invariant: Project.like_count == COUNT(ProjectLike WHERE project_id=X)
- [ ] `backend-boundary/error-codes.md` LKE001~003

---

# [Epic 1] `ProjectLike` + Repository + ErrorCode

## Story
- 1-1: 엔티티
- 1-2: Repository + ErrorCode

---

## [Story 1-1] 엔티티

### 스키마
```sql
CREATE TABLE project_like (
  id             BIGINT    NOT NULL AUTO_INCREMENT,
  user_id        BIGINT    NOT NULL,
  project_id     BIGINT    NOT NULL,
  created_at     TIMESTAMP NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pl_pair (user_id, project_id),
  KEY idx_pl_user (user_id, created_at DESC),
  KEY idx_pl_project (project_id, created_at DESC)
);
```

**엔티티**:
```java
@Entity
@Table(name = "project_like")
@NoArgsConstructor(access = PROTECTED)
@Getter
public class ProjectLike {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ProjectLike create(Long userId, Long projectId) {
        ProjectLike l = new ProjectLike();
        l.userId = userId;
        l.projectId = projectId;
        l.createdAt = LocalDateTime.now();
        return l;
    }
}
```

### AC
- `create(1, 100)` 성공
- 중복 (userId, projectId) 저장 시 UNIQUE 위반

### DoD
- [ ] 엔티티
- [ ] 단위 테스트

### SP: 0.5d

---

## [Story 1-2] Repository + ErrorCode

### Repository
```java
public interface ProjectLikeRepository extends JpaRepository<ProjectLike, Long> {
    Optional<ProjectLike> findByUserIdAndProjectId(Long userId, Long projectId);
    boolean existsByUserIdAndProjectId(Long userId, Long projectId);
    Page<ProjectLike> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    long countByProjectId(Long projectId);
}
```

### ErrorCode
- LKE001 LIKE_NOT_FOUND (404)
- LKE002 LIKE_ALREADY_EXISTS (409)
- LKE003 PROJECT_NOT_FOUND (404) - Project.PRJ001 재사용 or 신규

### DoD
- [ ] Repository
- [ ] ErrorCode
- [ ] `@DataJpaTest`

### SP: 0.5d

---

# [Epic 2] `LikeService` + Project 확장 + REST 4개

## Story
- 2-1: Project 도메인 확장 (`incrementLikeCount · decrementLikeCount`)
- 2-2: LikeService (like · unlike)
- 2-3: LikeService (myLikes · isLiked) + REST 4개 + ADR

---

## [Story 2-1] Project 확장

### 설명
Product 6 Project 엔티티에 추가:
```java
public void incrementLikeCount() {
    this.likeCount++;
}

public void decrementLikeCount() {
    if (this.likeCount <= 0) {
        log.error("PROJECT_LIKE_UNDERFLOW projectId={}", id);
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
    this.likeCount--;
}
```

- Product 6 SDD Epic 3 (반정규화 필드) 개정 반영

### AC
- `incrementLikeCount()` · likeCount += 1
- decrement `likeCount = 0` · underflow · INTERNAL_ERROR

### DoD
- [ ] Product 6 도메인 메서드 2개
- [ ] Product 6 SDD 개정 반영 표기

### SP: 0.5d

---

## [Story 2-2] like · unlike

### 설명
```java
@Service
@Transactional
@RequiredArgsConstructor
public class LikeService {
    private final ProjectLikeRepository likeRepository;
    private final ProjectRepository projectRepository;
    private final MeterRegistry meterRegistry;

    public void like(Long userId, Long projectId) {
        if (likeRepository.existsByUserIdAndProjectId(userId, projectId))
            throw new BusinessException(ErrorCode.LKE002);
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.LKE003));
        likeRepository.save(ProjectLike.create(userId, projectId));
        project.incrementLikeCount();
        meterRegistry.counter("like.total", "action", "like").increment();
    }

    public void unlike(Long userId, Long projectId) {
        ProjectLike like = likeRepository.findByUserIdAndProjectId(userId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.LKE001));
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.LKE003));
        likeRepository.delete(like);
        project.decrementLikeCount();
        meterRegistry.counter("like.total", "action", "unlike").increment();
    }
}
```

### AC
- like 성공 · Project.likeCount 증가
- 이미 like · LKE002
- unlike 성공 · Project.likeCount 감소
- 없음 · LKE001

### DoD
- [ ] Service
- [ ] 통합 테스트

### SP: 1d

---

## [Story 2-3] myLikes · isLiked + REST 4개 + ADR

### Service
```java
@Transactional(readOnly = true)
public Page<ProjectLikeResponse> myLikes(Long userId, Pageable pageable) {
    return likeRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        .map(ProjectLikeResponse::from);
}

public boolean isLiked(Long userId, Long projectId) {
    return likeRepository.existsByUserIdAndProjectId(userId, projectId);
}
```

### REST
| Method | Path | 인증 |
|---|---|---|
| POST | `/api/v1/projects/{id}/like` | JWT |
| DELETE | `/api/v1/projects/{id}/like` | JWT |
| GET | `/api/v1/users/me/likes` | JWT |
| GET | `/api/v1/projects/{id}/is-liked` | JWT |

### ADR
- `033-project-like-unified-and-count-denormalization.md`

### DoD
- [ ] Service · Controller · Response
- [ ] `@WebMvcTest`
- [ ] SSOT invariant 통합 테스트
- [ ] ADR

### SP: 1d

---

## 요약
| Epic | Story | SP |
|---|---|---|
| Epic 1 | 2 | 1.0 |
| Epic 2 | 3 | 2.5 |
| **합계** | **5** | **3.5 SP** |

## 완결 → 후속
- Project (6): like_count 활성 · 목록·상세 즉시 노출
- Search (18): like_count 정렬 축
- Discovery 개인화 (v0.0.5+): 관심 프로젝트 기반 추천
