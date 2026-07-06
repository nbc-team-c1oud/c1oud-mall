# [Product 12] GitHub 연동 (Repository Link · OAuth · Webhook 3단계 로드맵)

## Product Vision
> c1oud-mall이 GitHub 기반 학생 프로젝트 크라우드 펀딩 플랫폼임을 실체화하는 **GitHub 도메인**. 초기(v0.0.3) 단순 URL 저장·검증 · 중기(v0.0.4) OAuth 로그인·리포지토리 소유권 인증 · 후기(v0.0.5+) Webhook 릴리스·커밋 동기화의 3단계 로드맵으로 점진 확장한다. 후원자가 프로젝트의 실 개발 활동을 즉각 확인해 신뢰도를 판단할 수 있게 하고, 메이커가 홍보 자료를 수동으로 만들 필요 없이 GitHub 활동이 그대로 크라우드 펀딩 콘텐츠로 흘러가게 한다.

## 배경 및 문제
- 현재 상황 (As-Is)
  - Project (Product 6)의 `githubRepoUrl` 필드는 스키마에만 존재 · 검증 X · 활용 X
  - GitHub Repo 링크는 자유 텍스트 저장 → 잘못된 URL, 존재하지 않는 리포, 오타 대량 유입 가능
  - 프로젝트 상세에 GitHub 활동 (star · commit · release) 반영 없음 → "이 프로젝트 정말 살아있는가?" 판단 불가
  - 크라우드 펀딩 본질(*GitHub 프로젝트 = 후원 대상*)이 제품 UX에 안 드러남
  - 이슈 #14 R4 결정: 초기 URL만 · v0.0.3 OAuth · v0.0.4 Webhook (기존) → 재검토 필요
- 발생하는 문제
  - 후원자 신뢰 지표 부족 · 후원 이탈률 상승
  - 메이커가 GitHub와 c1oud-mall 사이 정보 수동 동기화 · 이중 관리 부담
  - 사기·허위 프로젝트 방지 장치 없음 (v0.0.5+ 이상 시 심각)
  - 프로젝트 검색·필터에 GitHub 활동 신호 부재 · Discovery 품질 저하
- 왜 지금 해결해야 하는가
  - Product 6·11 완결로 프로젝트·이미지 기반 성립 · GitHub 연동은 자연스러운 신뢰 계층
  - 3단계 로드맵의 v0.0.3 (URL 저장·검증만)은 low risk · high value · 즉시 착수 가능
  - OAuth·Webhook은 후속 스코프이지만 도메인·인터페이스 설계는 지금 확정 필요 (M3 시점 · 나중에 뒤집으면 비용 큼)
  - Discovery (Product 13)이 use-for-project 흐름으로 이 GitHub 도메인을 소비 예정

## 목표 (To-Be)
- 신규 컨텍스트: `nbc.c1oud_mall.githubintegration.*` (4레이어)
  - 패키지명 `github`이 아닌 `githubintegration` — 언어 예약어·클래스명 충돌 회피
- **3단계 로드맵**:
  - **v0.0.3 (M3 스코프 · 이번 SDD 핵심)**: `GitHubRepository` 도메인 + URL 검증 + GitHub REST API 조회 (public repo · 인증 X)
  - **v0.0.4**: OAuth 로그인 · 사용자 GitHub 계정 인증 · 소유권 검증
  - **v0.0.5+**: Webhook · Push·Release 이벤트 · 자동 동기화
- `GitHubRepository` 엔티티 · Project와 1:1 관계
- 필드: `id · projectId · owner · repoName · fullName · description · stars · forks · language · defaultBranch · lastCommittedAt · lastSyncedAt · syncStatus`
- 3개 enum: `SyncStatus (PENDING · SYNCED · FAILED · STALE)`
- `GitHubApiClient` (Port) · `RestGitHubApiClient` (Adapter · REST v3 · public API)
- `RepositorySyncScheduler` — 매일 새벽 5시 · 활성 프로젝트의 GitHub 데이터 갱신
- REST 3개 엔드포인트: 조회 · 수동 재동기화 · URL 검증
- `ErrorCode.GHB001~005` (5개)
- Rate Limit 관측 (public API 60/hr · 인증 시 5000/hr)
- Project 등록 흐름 확장 · 4단계 워크플로우 Step 1에서 URL 입력 시 실시간 검증

## 설계 결정 (Design Decisions)
- **패키지명 `githubintegration`** — `github`은 라이브러리 충돌 위험
- **3단계 로드맵 명시 · 초기 v0.0.3 스코프만 구현** (이슈 #14 R4 반영)
  - v0.0.3: 익명 REST API 조회만 · 60 req/hr Rate Limit 내
  - v0.0.4: OAuth 승격 지점 · 인터페이스·도메인은 지금 확정
  - v0.0.5+: Webhook 이벤트 처리
- **`GitHubRepository`는 별도 Aggregate root** (Project와 1:1 관계 · 별도 테이블)
  - Project 도메인에 GitHub 필드 다 채우면 응집도 저해 · GitHub API·Rate Limit·재동기화 로직 분리
- **Port/Adapter 패턴** — Discovery(Product 13) · Media(Product 11)의 이중 어댑터 스토리와 정합
  - `GitHubApiClient` 인터페이스 (application layer)
  - `RestGitHubApiClient` 구현 (infrastructure layer · REST v3)
  - v0.0.4+ OAuth 활성화 시 별도 어댑터 or 헤더 추가만
- **Sync 배치 매일 새벽 5시** — Wallet(4시 미배정) · Media(4시) · Maker(3시)와 시간 분산
- **Rate Limit 관측 · Timer 등록** — 60/hr 접근 시 로그 마커 + 지표
- **Stale 판정 · 7일 이상 미동기화 시 STALE** — UI에서 "정보가 오래됐습니다" 표시
- **URL 파싱 정규식 + REST 검증 이중 방어**
  - 정규식: `^https?://github\.com/([\w-]+)/([\w.-]+?)(?:\.git)?/?$`
  - REST HEAD 호출: 404면 `GHB003`
- **Project와 1:1 관계** — 한 프로젝트 = 하나의 Repo · v0.0.5+ 멀티 Repo 검토

## 대안 검토 (Alternatives Considered)

### 저장 방식
**Option A — 별도 `GitHubRepository` 엔티티 + Project 1:1 (선택)**
- 도메인 응집도 · Sync 로직 분리 · 확장 여지

**Option B — Project에 GitHub 컬럼 다 넣기 (flat)**
- 거부 이유: 응집도 저해 · Project 스키마 팽창 · Sync 부하

### GitHub API 호출 방식
**Option A — 초기 익명 REST API + 배치 (선택)**
- OAuth 학습·구현 비용 지연 · 60/hr 스코프 안

**Option B — 초기부터 OAuth**
- 거부 이유: v0.0.3 스코프 오버 · 사용자 없어 OAuth 흐름 검증 어려움

**Option C — GraphQL API**
- 거부 이유: 익명 접근 불가 · 오버킬

### 동기화 주기
**Option A — 매일 새벽 5시 배치 + 수동 재동기화 (선택)**
- Rate Limit 안전 · 신입 스코프 적합

**Option B — 프로젝트 상세 조회 시 실시간**
- 거부 이유: 조회당 API 소비 · Rate Limit 초과 위험

**Option C — Webhook (v0.0.5+)**
- v0.0.5+ 로드맵 · 초기 스코프 밖

## 전체 아키텍처 (High-Level Architecture)

### 컴포넌트 배치
```
presentation ──▶ application ──▶ domain ◀── infrastructure
GitHubController      GitHubService        GitHubRepository       GitHubRepositoryRepo
- getByProjectId      - fetchAndSave       - createFromApi()      - findByProjectId
- resync              - resync             - markSynced()         - findStale (배치)
- validateUrl         - validateUrl        - markFailed()         RestGitHubApiClient (Adapter)
                      - findStale          - markStale()          - fetchRepo(owner, name)
                      RepositorySyncScheduler                     - checkExists(owner, name)
                      - runDaily()         GitHubApiClient        - Rate Limit 관측
                                             (Port · interface)   GitHubUrlParser
                                                                  - parse(url) → RepoRef

External refs:
- Project (Product 6) — Project.githubRepositoryId FK
- Discovery (Product 13) — use-for-project 흐름에서 GitHub URL 자동 채우기
```

### 핵심 플로우
**1. Project 등록 시 URL 검증**
```
Project 등록 4단계 워크플로우 Step 1
  FE → POST /api/v1/github/validate-url {url: "https://github.com/user/repo"}
    → GitHubUrlParser.parse(url)  → RepoRef(owner="user", name="repo")
    → githubApiClient.checkExists(owner, name)  → true/404
  ← 200 {valid: true, owner: "user", name: "repo"}
     or 400 · GHB003
```

**2. Project 생성 완료 시 GitHub 정보 초기 조회**
```
ProjectService.create (Product 6)
  ├── Project 저장 (githubRepoUrl)
  └── (부수효과) GitHubService.fetchAndSave(projectId, url)
        ├── parse(url) → RepoRef
        ├── githubApiClient.fetchRepo(owner, name) → RepoData
        ├── GitHubRepository.createFromApi(projectId, repoData) 저장
        └── Project.linkGitHubRepository(githubRepoId)
```

**3. 매일 배치 재동기화**
```
@Scheduled(cron = "0 0 5 * * *")
RepositorySyncScheduler.runDaily()
  ├── LIVE·SUCCESSFUL·FUNDED 상태 프로젝트의 GitHubRepository 조회
  ├── For each:
  │     try:
  │       RepoData data = githubApiClient.fetchRepo(...)
  │       repo.markSynced(data)
  │     catch (RateLimitException):
  │       break · 다음 배치 재시도
  │     catch (Exception):
  │       repo.markFailed()
  └── 지표: github.sync.batch.duration + counter{result}
```

**4. 수동 재동기화 (프로젝트 상세 · 메이커 대시보드)**
```
FE → POST /api/v1/github/repos/{id}/resync
  → GitHubService.resync(userId, repoId)
    ├── 소유권 검증 (Project.makerUserId == userId)
    ├── 최근 재동기화 시각 확인 (rate limit 방지 · 5분 쿨다운)
    └── githubApiClient.fetchRepo(...) → repo.markSynced(...)
```

### Out-of-Process 의존
- **GitHub REST API v3** — `https://api.github.com/repos/{owner}/{repo}`
- **RDS (MySQL)** — `github_repository` 테이블
- Project · Discovery 참조 컬럼

## 실패 모드 / 운영 관측

### 실패 시나리오와 응답
| 시나리오 | ErrorCode | HTTP | 클라이언트 권장 동작 |
| --- | --- | --- | --- |
| GitHub 리포 정보 없음 (Project에 연결 X) | `GHB001` GITHUB_REPO_NOT_FOUND | 404 | Project.githubRepoUrl 확인 |
| URL 형식 오류 | `GHB002` GITHUB_INVALID_URL | 400 | 재입력 |
| GitHub에 실 리포 없음 (404) | `GHB003` GITHUB_REPO_NOT_EXISTS | 400 | 재입력 · 리포 공개 여부 확인 |
| GitHub API Rate Limit 초과 | `GHB004` GITHUB_RATE_LIMITED | 503 | 잠시 후 재시도 (X-RateLimit-Reset 참조) |
| 재동기화 쿨다운 (5분 내 재요청) | `GHB005` GITHUB_RESYNC_COOLDOWN | 429 | 5분 대기 |
| GitHub API 서버 에러 (5xx) | `C002` INTERNAL_ERROR | 500 | 재시도 |

### 로깅 정책
- **항상 기록**: `requestId · projectId · repoId · owner/name · 액션`
- **debug**: API 응답 raw · Rate Limit remaining/reset
- **금지**: OAuth 토큰 (v0.0.4+)
- **특수 마커**:
  - `GITHUB_RATE_LIMIT_APPROACHING remaining={} reset={}` — 10건 이하
  - `GITHUB_SYNC_FAILED repoId={} reason={}` — 개별 실패

### 관측 지표
- `github.api.total{endpoint, result}` — counter
- `github.rate_limit.remaining` — gauge (매 호출 후 업데이트)
- `github.sync.batch.duration_seconds` — histogram
- `github.sync.total{result=success|partial_fail|full_fail}` — counter
- `github.repo.stale.total` — gauge (7일+ 미동기화)

## 롤아웃

### 전제
- Project (Product 6) 완결
- GitHub API 익명 접근 60/hr 스코프 안 (초기 사용자 없어 여유)
- 초기 사용자 없음 · 신규 테이블만
- v0.0.3 스코프만 · OAuth·Webhook은 후속

### Product 의존성
- **선행**: Project (6)
- **후행**: Discovery (13) — use-for-project 흐름 · v0.0.4+ OAuth
- **동시 대응**: `application.yml`에 `c1oudmall.github.api-base-url` (test override 편의)

### Epic·Story 의존성
```
Epic 1 (도메인·enum·ErrorCode) ──► Epic 2 (Port/Adapter·URL 검증)
                                        └─► Epic 3 (Service·Project 연동)
                                              └─► Epic 4 (배치·REST·관측·ADR)
```

### 환경별 설정
| 항목 | dev | prod |
| --- | --- | --- |
| GitHub API endpoint | 실 · WireMock override 가능 | 실 |
| Rate Limit | 60/hr | 60/hr (v0.0.4+ OAuth 5000/hr) |
| 재동기화 쿨다운 | 1분 (테스트) | 5분 |
| 배치 cron | 5분 (테스트) | 05:00 |

## 성공 지표 (KPI)
| 지표 | 목표 | 측정 |
| --- | --- | --- |
| URL 검증 응답 P95 | ≤ 800ms | `github.api.total` timer |
| 배치 성공률 | ≥ 95% | `github.sync.total{result=success}` / total |
| Rate Limit 초과 발생 | 월 ≤ 2회 | `GITHUB_RATE_LIMIT_APPROACHING` 로그 |
| Stale 리포 비율 | ≤ 5% | `github.repo.stale.total` / total |
| 정보 정합성 (star·commit) | 24시간 내 최신 | 배치 시점 검증 |

## Scope
**In (v0.0.3)**:
- `githubintegration.*` 4레이어
- `GitHubRepository` 엔티티 · Project 1:1
- URL 파서 + REST API 조회 (익명)
- Sync 배치 (일간)
- REST 3개 · `ErrorCode.GHB001~005`
- 관측 지표 5종

**Out (v0.0.4+)**:
- OAuth 로그인 · 사용자 계정 인증
- 리포지토리 소유권 검증
- Webhook · Release·Push 이벤트 처리
- 커밋 활동 그래프 · Contribution
- 멀티 Repo 지원
- GitHub Actions 배지
- Private Repo 지원

## 대상 사용자
- **메이커**: URL 등록 · 재동기화 · 신뢰 지표 제공
- **후원자**: 프로젝트 상세에서 GitHub 활동 확인 (star · language · lastCommit)
- **Discovery use-for-project 흐름**: GitHub URL 자동 세팅
- **운영자**: Rate Limit·배치 실패 대응

## 연결된 Epic 목록
- [ ] Epic 1: `GitHubRepository` + `SyncStatus` enum + Repository + `ErrorCode.GHB001~005`
- [ ] Epic 2: `GitHubApiClient` Port + `RestGitHubApiClient` Adapter + `GitHubUrlParser`
- [ ] Epic 3: `GitHubService` (fetchAndSave · resync · validateUrl) + Project 등록 부수효과
- [ ] Epic 4: `RepositorySyncScheduler` + REST 3개 + 관측 5개 + ADR + 규범 갱신

## 관련 문서
- **원본 이슈**: `issue-14-github-integration.md`
- **선행 SDD**: `product-project.md` (Product 6)
- **후행 SDD**: `product-discovery.md` (Product 13 · use-for-project)
- **관련 이슈**: `issue-08-project-domain.md` · `issue-15-discovery-signal-collector.md`
- **디자인 참조**: `프로젝트 상세.html` GitHub info 카드 · `프로젝트 등록.html` Step 1 URL 입력
- **외부 참조**:
  - GitHub REST API v3: `https://docs.github.com/en/rest`
  - Rate Limit: `https://docs.github.com/en/rest/rate-limit`
- **신규 ADR 후보**:
  - "GitHub 도메인 3단계 로드맵 (v0.0.3 익명 REST · v0.0.4 OAuth · v0.0.5+ Webhook)"
  - "Port/Adapter로 GitHubApiClient 분리 · 재사용성 확보"
- **규범 갱신**:
  - `backend-boundary/error-codes.md` GHB001~005

## 열린 질문
- **Rate Limit 초과 시 fallback** — 60/hr 초과 시 스킵 or 다음 배치 · 초기 스킵
- **Stale 판정 임계값 7일 최적성** — 유저 피드백 후 조정
- **초기 URL 검증 강도** — REST HEAD로 검증 필수 or 정규식만 (초기: HEAD 필수)
- **OAuth 도입 시점** — 사용자 100명+ 도달 시 · Fake maker 방지 시급성
- **Webhook 도입 조건** — 실 후원자 유입 시 신뢰 강화 필요
- **멀티 Repo 지원** — Monorepo 프로젝트 대응 · v0.0.5+
- **Private Repo** — OAuth 도입 시 함께 · v0.0.4+

## Product-level DoD
- [ ] Epic 4개 완료
- [ ] E2E: URL 입력 → 검증 → Project 생성 → GitHub 정보 조회 → 프로젝트 상세 노출
- [ ] Rate Limit 초과 시나리오 통합 테스트 (WireMock)
- [ ] 배치 실행 · 100건 리포 갱신 검증
- [ ] Stale 판정·복구 시나리오
- [ ] ADR 2건 · `backend-boundary/error-codes.md` 갱신

---

# [Epic 1] `GitHubRepository` + `SyncStatus` + Repository + ErrorCode

## 목표
GitHub 도메인 기반 엔티티·enum·Repository·5개 ErrorCode 확보.

## 포함 Story
- Story 1-1: `GitHubRepository` 엔티티 + `SyncStatus` enum
- Story 1-2: 도메인 메서드 (`createFromApi · markSynced · markFailed · markStale · canResync`)
- Story 1-3: Repository + `ErrorCode.GHB001~005`

---

## [Story 1-1] 엔티티 + enum

### 설명
- 위치: `nbc.c1oud_mall.githubintegration.domain.GitHubRepository`
- Project와 1:1 관계 (`project_id` UNIQUE)

**스키마**:
```sql
CREATE TABLE github_repository (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  project_id          BIGINT       NOT NULL,
  owner               VARCHAR(100) NOT NULL,
  repo_name           VARCHAR(100) NOT NULL,
  full_name           VARCHAR(200) NOT NULL,           -- "owner/repo_name"
  description         VARCHAR(500) NULL,
  html_url            VARCHAR(500) NOT NULL,
  homepage            VARCHAR(500) NULL,
  stars               INT          NOT NULL DEFAULT 0,
  forks               INT          NOT NULL DEFAULT 0,
  open_issues         INT          NOT NULL DEFAULT 0,
  language            VARCHAR(50)  NULL,
  default_branch      VARCHAR(100) NOT NULL DEFAULT 'main',
  license             VARCHAR(100) NULL,
  last_committed_at   TIMESTAMP    NULL,
  last_synced_at      TIMESTAMP    NULL,
  sync_status         VARCHAR(20)  NOT NULL,          -- PENDING · SYNCED · FAILED · STALE
  sync_failure_count  INT          NOT NULL DEFAULT 0,
  created_at          TIMESTAMP    NOT NULL,
  updated_at          TIMESTAMP    NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ghrepo_project (project_id),
  UNIQUE KEY uk_ghrepo_fullname (full_name),
  KEY idx_ghrepo_status_synced (sync_status, last_synced_at)
);
```

**엔티티**:
```java
@Entity
@Table(name = "github_repository")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class GitHubRepository extends BaseEntity {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 100)
    private String owner;

    @Column(nullable = false, length = 100)
    private String repoName;

    @Column(nullable = false, length = 200)
    private String fullName;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 500)
    private String htmlUrl;

    @Column(length = 500)
    private String homepage;

    @Column(nullable = false)
    private int stars;

    @Column(nullable = false)
    private int forks;

    @Column(nullable = false)
    private int openIssues;

    @Column(length = 50)
    private String language;

    @Column(nullable = false, length = 100)
    private String defaultBranch;

    @Column(length = 100)
    private String license;

    @Column
    private LocalDateTime lastCommittedAt;

    @Column
    private LocalDateTime lastSyncedAt;

    @Enumerated(STRING)
    @Column(nullable = false, length = 20)
    private SyncStatus syncStatus;

    @Column(nullable = false)
    private int syncFailureCount;
}

public enum SyncStatus { PENDING, SYNCED, FAILED, STALE }
```

### 완료 기준 (AC)
- Given `createFromApi(...)` · When 저장 · Then syncStatus=SYNCED
- Given projectId 중복 · Then UNIQUE 위반

### DoD
- [ ] 엔티티 + enum
- [ ] DDL 확인

### SP: 0.5d

---

## [Story 1-2] 도메인 메서드

### 설명
```java
public static GitHubRepository createFromApi(Long projectId, RepoData data) {
    GitHubRepository g = new GitHubRepository();
    g.projectId = projectId;
    g.owner = data.owner();
    g.repoName = data.name();
    g.fullName = data.fullName();
    g.description = data.description();
    g.htmlUrl = data.htmlUrl();
    g.homepage = data.homepage();
    g.stars = data.stars();
    g.forks = data.forks();
    g.openIssues = data.openIssues();
    g.language = data.language();
    g.defaultBranch = data.defaultBranch();
    g.license = data.license();
    g.lastCommittedAt = data.lastCommittedAt();
    g.lastSyncedAt = LocalDateTime.now();
    g.syncStatus = SyncStatus.SYNCED;
    g.syncFailureCount = 0;
    return g;
}

public void markSynced(RepoData data) {
    this.stars = data.stars();
    this.forks = data.forks();
    this.openIssues = data.openIssues();
    this.language = data.language();
    this.description = data.description();
    this.homepage = data.homepage();
    this.license = data.license();
    this.lastCommittedAt = data.lastCommittedAt();
    this.lastSyncedAt = LocalDateTime.now();
    this.syncStatus = SyncStatus.SYNCED;
    this.syncFailureCount = 0;
}

public void markFailed() {
    this.syncFailureCount++;
    this.syncStatus = (syncFailureCount >= 3) ? SyncStatus.FAILED : this.syncStatus;
}

public void markStale() {
    this.syncStatus = SyncStatus.STALE;
}

public boolean isStale() {
    if (lastSyncedAt == null) return true;
    return lastSyncedAt.isBefore(LocalDateTime.now().minusDays(7));
}

public boolean canResync(Duration cooldown) {
    if (lastSyncedAt == null) return true;
    return lastSyncedAt.plus(cooldown).isBefore(LocalDateTime.now());
}
```

### AC
- Given SYNCED · When `markFailed()` 3회 · Then FAILED
- Given `lastSyncedAt = 8일 전` · When `isStale()` · Then true
- Given `lastSyncedAt = 3분 전` · cooldown=5분 · When `canResync` · Then false

### DoD
- [ ] 6개 메서드
- [ ] 단위 테스트

### SP: 1d

---

## [Story 1-3] Repository + ErrorCode

### 설명
```java
public interface GitHubRepositoryRepository extends JpaRepository<GitHubRepository, Long> {
    Optional<GitHubRepository> findByProjectId(Long projectId);

    @Query("SELECT g FROM GitHubRepository g WHERE g.projectId IN :projectIds AND g.syncStatus IN :statuses")
    List<GitHubRepository> findForBatch(@Param("projectIds") List<Long> projectIds,
                                         @Param("statuses") List<SyncStatus> statuses);

    @Query("SELECT g FROM GitHubRepository g WHERE g.lastSyncedAt < :threshold")
    List<GitHubRepository> findStale(@Param("threshold") LocalDateTime threshold);
}
```

- ErrorCode:
  - `GHB001` GITHUB_REPO_NOT_FOUND (404)
  - `GHB002` GITHUB_INVALID_URL (400)
  - `GHB003` GITHUB_REPO_NOT_EXISTS (400)
  - `GHB004` GITHUB_RATE_LIMITED (503)
  - `GHB005` GITHUB_RESYNC_COOLDOWN (429)

### DoD
- [ ] Repository · 3개 쿼리
- [ ] ErrorCode 등록
- [ ] `@DataJpaTest`

### SP: 0.5d

---

# [Epic 2] `GitHubApiClient` Port + `RestGitHubApiClient` + `GitHubUrlParser`

## 목표
GitHub REST v3와 통신하는 Adapter · URL 파서.

## 포함 Story
- Story 2-1: `GitHubUrlParser` (정규식 · RepoRef 반환)
- Story 2-2: `GitHubApiClient` Port + `RestGitHubApiClient` Adapter (RestTemplate/WebClient)
- Story 2-3: Rate Limit 관측 + 예외 매핑

---

## [Story 2-1] `GitHubUrlParser`

### 설명
```java
@Component
public class GitHubUrlParser {
    private static final Pattern PATTERN = Pattern.compile(
        "^https?://github\\.com/([\\w-]+)/([\\w.-]+?)(?:\\.git)?/?$"
    );

    public RepoRef parse(String url) {
        if (url == null || url.isBlank())
            throw new BusinessException(ErrorCode.GHB002);
        Matcher m = PATTERN.matcher(url.trim());
        if (!m.matches())
            throw new BusinessException(ErrorCode.GHB002);
        return new RepoRef(m.group(1), m.group(2));
    }

    public record RepoRef(String owner, String name) {
        public String fullName() { return owner + "/" + name; }
    }
}
```

### AC
- Given `https://github.com/user/repo` · Then `RepoRef("user", "repo")`
- Given `https://github.com/user/repo.git` · Then `RepoRef("user", "repo")`
- Given `not-a-url` · Then GHB002
- Given `https://gitlab.com/user/repo` · Then GHB002

### DoD
- [ ] 파서 · 프로퍼티 테스트

### SP: 0.5d

---

## [Story 2-2] Port + REST Adapter

### 설명
```java
public interface GitHubApiClient {
    RepoData fetchRepo(String owner, String name);
    boolean checkExists(String owner, String name);
}

@Component
@Slf4j
public class RestGitHubApiClient implements GitHubApiClient {
    private final RestClient restClient;
    private final MeterRegistry meterRegistry;

    public RestGitHubApiClient(@Value("${c1oudmall.github.api-base-url:https://api.github.com}") String baseUrl,
                                 MeterRegistry meterRegistry) {
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Accept", "application/vnd.github+json")
            .defaultHeader("User-Agent", "c1oud-mall/1.0")
            .build();
        this.meterRegistry = meterRegistry;
    }

    @Override
    public RepoData fetchRepo(String owner, String name) {
        try {
            var response = restClient.get()
                .uri("/repos/{owner}/{name}", owner, name)
                .retrieve()
                .toEntity(RepoResponse.class);

            recordRateLimit(response.getHeaders());
            meterRegistry.counter("github.api.total", "endpoint", "repos", "result", "success").increment();

            return toRepoData(response.getBody());
        } catch (HttpClientErrorException.NotFound e) {
            meterRegistry.counter("github.api.total", "endpoint", "repos", "result", "not_found").increment();
            throw new BusinessException(ErrorCode.GHB003);
        } catch (HttpClientErrorException.Forbidden e) {
            // Rate Limit 초과
            meterRegistry.counter("github.api.total", "endpoint", "repos", "result", "rate_limit").increment();
            throw new BusinessException(ErrorCode.GHB004);
        }
    }

    @Override
    public boolean checkExists(String owner, String name) {
        try {
            restClient.get().uri("/repos/{owner}/{name}", owner, name)
                .retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    private void recordRateLimit(HttpHeaders headers) {
        String remaining = headers.getFirst("X-RateLimit-Remaining");
        if (remaining != null) {
            int r = Integer.parseInt(remaining);
            meterRegistry.gauge("github.rate_limit.remaining", r);
            if (r <= 10) {
                log.warn("GITHUB_RATE_LIMIT_APPROACHING remaining={} reset={}",
                    r, headers.getFirst("X-RateLimit-Reset"));
            }
        }
    }
}
```

### AC
- Given owner/repo 존재 · When `fetchRepo` · Then RepoData 반환 · Rate Limit gauge
- Given 404 · Then GHB003
- Given 403 (Rate Limit) · Then GHB004

### DoD
- [ ] Port · Adapter
- [ ] WireMock 통합 테스트

### SP: 1.5d

---

## [Story 2-3] Rate Limit 관측 + 예외 매핑

### 설명
- Story 2-2에 포함된 로직 정리 + 통합 지표
- Timer로 API 호출 latency
- Gauge로 remaining 실시간
- Counter로 결과 분류

### DoD
- [ ] 지표 4종 (전 통합)
- [ ] 통합 테스트 검증

### SP: 0.5d

---

# [Epic 3] `GitHubService` + Project 등록 부수효과

## 목표
`fetchAndSave · resync · validateUrl` 3개 유스케이스 · Project 등록 흐름과 결합.

## 포함 Story
- Story 3-1: `GitHubService.validateUrl` (Project 등록 Step 1)
- Story 3-2: `GitHubService.fetchAndSave` (Project 생성 후 부수효과)
- Story 3-3: `GitHubService.resync` (수동 재동기화 · 쿨다운)

---

## [Story 3-1] validateUrl

### 설명
```java
public UrlValidationResponse validateUrl(String url) {
    RepoRef ref = urlParser.parse(url);
    boolean exists = githubApiClient.checkExists(ref.owner(), ref.name());
    if (!exists) throw new BusinessException(ErrorCode.GHB003);
    return new UrlValidationResponse(true, ref.owner(), ref.name(), ref.fullName());
}
```

### AC
- Given 유효 URL · 실 리포 · Then 200 · valid=true
- *(예외)* Given 존재 안 함 · Then GHB003

### DoD
- [ ] 서비스 메서드
- [ ] 통합 테스트

### SP: 0.5d

---

## [Story 3-2] fetchAndSave (Project 부수효과)

### 설명
```java
@Transactional
public GitHubRepository fetchAndSave(Long projectId, String url) {
    RepoRef ref = urlParser.parse(url);
    RepoData data = githubApiClient.fetchRepo(ref.owner(), ref.name());
    GitHubRepository entity = GitHubRepository.createFromApi(projectId, data);
    return repository.save(entity);
}
```

- ProjectService.create 확장: 이 부수효과 호출 (REQUIRED tx 참여)
- 실패 시 Project 생성 전체 롤백? or GitHub 정보 없이도 생성 허용? → **후자 채택** (별도 tx REQUIRES_NEW · 실패 무시 · Sync 배치가 나중에 회복)

### AC
- Given Project 생성 완료 · GitHub API 성공 · Then GitHubRepository 저장
- Given GitHub API 실패 (Rate Limit) · Then Project 생성 유지 · GitHub 정보 나중에 배치

### DoD
- [ ] 서비스 메서드 · REQUIRES_NEW TX
- [ ] Product 6 SDD Epic 3 Story 3-1에 부수효과 추가 반영

### SP: 1d

---

## [Story 3-3] resync + 쿨다운

### 설명
```java
@Transactional
public GitHubRepository resync(Long userId, Long repoId) {
    GitHubRepository repo = repository.findById(repoId)
        .orElseThrow(() -> new BusinessException(ErrorCode.GHB001));

    // 소유권 검증 (Project.makerUserId == userId)
    Project project = projectRepository.findById(repo.getProjectId()).orElseThrow(...);
    if (!Objects.equals(project.getMakerUserId(), userId))
        throw new BusinessException(ErrorCode.ACCESS_DENIED);

    // 쿨다운 검증
    if (!repo.canResync(Duration.ofMinutes(5)))
        throw new BusinessException(ErrorCode.GHB005);

    RepoData data = githubApiClient.fetchRepo(repo.getOwner(), repo.getRepoName());
    repo.markSynced(data);
    return repo;
}
```

### AC
- Given 5분 초과 · 본인 · Then 재동기화 성공
- *(예외)* 5분 내 재요청 · Then GHB005
- *(예외)* 타인 · Then C003

### DoD
- [ ] 서비스 메서드
- [ ] 통합 테스트

### SP: 1d

---

# [Epic 4] `RepositorySyncScheduler` + REST 3개 + 관측 + ADR

## 포함 Story
- Story 4-1: `RepositorySyncScheduler` (매일 05:00 · 활성 프로젝트 · Rate Limit 관리)
- Story 4-2: `GitHubController` REST 3개
- Story 4-3: ADR 2건 + `backend-boundary/error-codes.md` GHB001~005

---

## [Story 4-1] 배치 스케줄러

### 설명
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class RepositorySyncScheduler {
    private final GitHubRepositoryRepository repository;
    private final ProjectRepository projectRepository;
    private final GitHubApiClient githubApiClient;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "0 0 5 * * *")
    public void runDaily() {
        long start = System.currentTimeMillis();
        // 활성 프로젝트 조회 (LIVE · SUCCESSFUL · FUNDED)
        List<Long> activeProjectIds = projectRepository.findActiveIds();
        List<GitHubRepository> repos = repository.findForBatch(activeProjectIds,
            List.of(SyncStatus.SYNCED, SyncStatus.FAILED, SyncStatus.STALE));

        long success = 0, fail = 0;
        for (GitHubRepository repo : repos) {
            try {
                RepoData data = githubApiClient.fetchRepo(repo.getOwner(), repo.getRepoName());
                repo.markSynced(data);
                success++;
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.GHB004) {
                    log.warn("GITHUB_SYNC_RATE_LIMITED break batch");
                    break;   // Rate Limit이면 배치 중단 · 다음 배치 회복
                }
                log.error("GITHUB_SYNC_FAILED repoId={}", repo.getId(), e);
                repo.markFailed();
                fail++;
            }
        }

        // Stale 판정 별도 pass
        List<GitHubRepository> stales = repository.findStale(LocalDateTime.now().minusDays(7));
        stales.forEach(GitHubRepository::markStale);
        meterRegistry.gauge("github.repo.stale.total", stales.size());

        long duration = System.currentTimeMillis() - start;
        meterRegistry.timer("github.sync.batch.duration_seconds").record(duration, MILLISECONDS);

        String result = (fail == 0) ? "success" : (success > 0 ? "partial_fail" : "full_fail");
        meterRegistry.counter("github.sync.total", "result", result).increment();
        log.info("GITHUB_SYNC_BATCH_DONE success={} fail={} stale={} duration={}ms",
            success, fail, stales.size(), duration);
    }
}
```

### AC
- 100건 리포 · When 배치 · Then 성공률 95%+ · gauge/counter 정확
- Rate Limit 초과 시 배치 중단 · 로그 마커
- 7일+ 미동기화 → STALE

### DoD
- [ ] 스케줄러
- [ ] 통합 테스트

### SP: 1.5d

---

## [Story 4-2] REST 3개

### 엔드포인트
- `GET /api/v1/projects/{projectId}/github-repository` · 공개 · 리포 정보 조회
- `POST /api/v1/github/repos/{repoId}/resync` · 인증 · 소유권 검증 · 쿨다운
- `POST /api/v1/github/validate-url` · 공개 · body `{url}` · 검증

### DoD
- [ ] Controller · Response
- [ ] `@WebMvcTest`

### SP: 1d

---

## [Story 4-3] ADR + 규범

### 내용
- ADR 2건:
  - `026-github-integration-3-phase-roadmap.md`
  - `027-github-api-port-adapter-and-rate-limit-strategy.md`
- `backend-boundary/error-codes.md` GHB001~005

### SP: 0.5d

---

## 요약

| Epic | Story | SP |
|---|---|---|
| Epic 1 | 3 | 2.0 |
| Epic 2 | 3 | 2.5 |
| Epic 3 | 3 | 2.5 |
| Epic 4 | 3 | 3.0 |
| **합계** | **12** | **10.0 SP** |

**진행 순서**: Epic 1 → 2 → 3 → 4

## 완결 → 후속 참조
- Discovery (Product 13): use-for-project 흐름 · GitHub URL 자동 설정
- v0.0.4+: OAuth 승격 · 소유권 인증
- v0.0.5+: Webhook · Release·Push 자동 동기화
