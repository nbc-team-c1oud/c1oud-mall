# Issue: GitHub 연동 — URL 등록 (v0.0.3) → OAuth (v0.0.4) → Webhook (v0.0.5) 3단계

## 배경

> **R4 결정 (Round 5)**: **초기 URL만 등록 · v0.0.3 OAuth · v0.0.4 Webhook** — 점진 도입.
> **컨셉 핵심**: GitHub 기반 대학생 프로젝트 크라우드 펀딩 → GitHub 연동이 브랜드의 대표 특징.

- 디자인(`프로젝트 상세.html`) 확인 요소:
  - Repo: `driftwood-dev / driftwood`
  - Description
  - Stars 2,431 · Forks 187 · 마지막 커밋 2시간 전 · 주간 +42 commits
  - Language stats bar (TypeScript 54% · JavaScript 24% · HTML 14% · CSS 8%)
  - Recent commits (hash · msg · when · 3개)
- 등록 페이지에서 "GitHub 계정 연결" 버튼 · 연결 후 "동기화 완료" 표시
- GitHub API 연동은 흥미로우나 스코프가 커서 3단계 점진 도입

## 조사 결과 — GitHub API 벤치마크

| 요소 | GitHub API | 인증 필요 | Rate Limit |
|---|---|---|---|
| Repo 메타 (name·desc·stars) | `GET /repos/{owner}/{repo}` | 없음 (public) | 60/hr |
| Languages | `GET /repos/{owner}/{repo}/languages` | 없음 | 60/hr |
| Recent commits | `GET /repos/{owner}/{repo}/commits` | 없음 | 60/hr |
| Contributors | `GET /repos/{owner}/{repo}/contributors` | 없음 | 60/hr |
| Webhook 수신 | Webhook 등록 (repo 관리 권한) | OAuth 필요 | - |
| 인증 후 rate | Personal Access Token or OAuth | 필요 | 5,000/hr |

**결론**: 초기엔 인증 없이 public API (60/hr · 캐싱 필요) → OAuth 도입으로 5,000/hr 확보

## 옵션 비교

### 갈림길 1: 3단계 도입 시점

**Option A (채택) — R4 결정 준수: URL(v0.0.3) → OAuth(v0.0.4) → Webhook(v0.0.5)**

| 단계 | 버전 | 내용 | 인증 |
|---|---|---|---|
| **1** | v0.0.3 | URL 파싱 · public API 캐시 조회 | 없음 (rate 60/hr) |
| **2** | v0.0.4 | GitHub OAuth · Personal 리포 목록 · 자동 sync | OAuth (rate 5000/hr) |
| **3** | v0.0.5 | Webhook 수신 (push · release) · 실시간 갱신 | OAuth + Webhook secret |

**Option B — 초기부터 OAuth**
- 거부 이유: OAuth 도입 오버헤드 · rate limit 초기엔 불필요

### 갈림길 2: 캐싱 전략

**Option A (채택) — 5분 캐시 + 명시적 refresh 버튼**
- Repo 메타는 자주 안 바뀜 · 5분 캐시로 충분
- Rate limit 회피
- 초기: in-memory (Caffeine) · v0.0.5+: Redis

**Option B — 매 조회마다 API 호출**
- 거부 이유: Rate limit 즉시 도달

### 갈림길 3: URL 파싱

**Option A (채택) — 정규식 + owner/repo 추출 + validation**
- `https://github.com/{owner}/{repo}` 또는 `https://github.com/{owner}/{repo}.git` 형식만 허용
- 정규식: `^https?://github\.com/([\w-]+)/([\w.-]+?)(?:\.git)?/?$`
- 유효한 owner/repo 추출 후 GitHub API로 실 존재 검증

## 선택: Option A (모든 갈림길)

## 부속 결정 (v0.0.3 스코프 위주)

### 도메인 컨텍스트
- 신규 컨텍스트: `nbc.c1oud_mall.github.*` (4레이어)
- Project와 결합 (Project.githubRepoId FK)

### 엔티티 · 스키마 (v0.0.3)
```sql
CREATE TABLE github_repo (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  owner               VARCHAR(80)  NOT NULL,           -- driftwood-dev
  repo                VARCHAR(100) NOT NULL,           -- driftwood
  full_name           VARCHAR(200) NOT NULL,           -- driftwood-dev/driftwood
  html_url            VARCHAR(500) NOT NULL,           -- https://github.com/...
  description         VARCHAR(500) NULL,
  stars               INT          NOT NULL DEFAULT 0,
  forks               INT          NOT NULL DEFAULT 0,
  primary_language    VARCHAR(50)  NULL,
  languages_json      JSON         NULL,               -- {"TypeScript": 54, "JavaScript": 24, ...}
  last_commit_at      TIMESTAMP    NULL,
  recent_commits_json JSON         NULL,               -- [{"hash": "a3f9", "msg": "...", "when": "..."}]
  last_synced_at      TIMESTAMP    NOT NULL,
  created_at          TIMESTAMP    NOT NULL,
  updated_at          TIMESTAMP    NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_github_full_name (full_name),
  KEY idx_github_synced (last_synced_at)
);

-- Project에 FK
ALTER TABLE project ADD COLUMN github_repo_id BIGINT NULL AFTER cover_image_url;
ALTER TABLE project ADD KEY idx_project_github (github_repo_id);
```

### 도메인 모델 (v0.0.3)
```java
// github.domain.GitHubRepo (Aggregate root)
@Entity
public class GitHubRepo extends BaseEntity {
    @Id @GeneratedValue Long id;
    String owner;
    String repo;
    @Column(unique = true) String fullName;
    String htmlUrl;
    String description;
    int stars;
    int forks;
    String primaryLanguage;
    @Column(columnDefinition = "JSON") String languagesJson;
    LocalDateTime lastCommitAt;
    @Column(columnDefinition = "JSON") String recentCommitsJson;
    LocalDateTime lastSyncedAt;

    public static GitHubRepo fromUrl(String url) {
        GitHubUrlParser.ParseResult parsed = GitHubUrlParser.parse(url);
        return builder()...
    }

    public void applyMetadata(RepoMetadata metadata) {
        this.description = metadata.description();
        this.stars = metadata.stars();
        this.forks = metadata.forks();
        this.primaryLanguage = metadata.primaryLanguage();
        // ...
        this.lastSyncedAt = LocalDateTime.now();
    }
}
```

### URL 파서
```java
// github.domain.GitHubUrlParser
public class GitHubUrlParser {
    private static final Pattern PATTERN = Pattern.compile(
        "^https?://github\\.com/([\\w-]+)/([\\w.-]+?)(?:\\.git)?/?$"
    );

    public static ParseResult parse(String url) {
        Matcher m = PATTERN.matcher(url);
        if (!m.matches())
            throw new BusinessException(ErrorCode.GITHUB_URL_INVALID);
        return new ParseResult(m.group(1), m.group(2));
    }

    public record ParseResult(String owner, String repo) {}
}
```

### GitHub API 어댑터 (v0.0.3)
```java
// github.infrastructure.GitHubApiAdapter
@Component
@RequiredArgsConstructor
public class GitHubApiAdapter {
    private final RestClient restClient;   // no auth · public API

    @Cacheable(value = "github-repo-metadata", key = "#owner + '/' + #repo")
    public RepoMetadata fetchRepoMetadata(String owner, String repo) {
        try {
            return restClient.get()
                .uri("https://api.github.com/repos/{owner}/{repo}", owner, repo)
                .retrieve()
                .body(RepoMetadata.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND);
        } catch (HttpClientErrorException.Forbidden e) {
            // Rate limit
            throw new BusinessException(ErrorCode.GITHUB_RATE_LIMITED);
        }
    }

    @Cacheable(value = "github-languages", key = "#owner + '/' + #repo")
    public Map<String, Long> fetchLanguages(String owner, String repo) { ... }

    @Cacheable(value = "github-commits", key = "#owner + '/' + #repo")
    public List<CommitSummary> fetchRecentCommits(String owner, String repo, int limit) { ... }
}
```

### 캐시 설정
```java
@Configuration
@EnableCaching
public class GitHubCacheConfig {
    @Bean
    public CacheManager githubCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
            "github-repo-metadata", "github-languages", "github-commits"
        );
        manager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)   // 5분
            .maximumSize(1000)
        );
        return manager;
    }
}
```

### API 표면 (v0.0.3)
| 메서드 | 경로 | 인증 | 용도 |
|---|---|---|---|
| POST | `/api/v1/github-repos` | JWT | URL 등록 (body: `{url}`) → 파싱 + 검증 + 저장 |
| GET | `/api/v1/github-repos/{repoId}` | 공개 | 캐시된 메타데이터 조회 |
| POST | `/api/v1/github-repos/{repoId}/refresh` | JWT | 캐시 무효화 · 재조회 |

### ErrorCode (신규)
- `GHR001` GITHUB_URL_INVALID (400 · 정규식 불일치)
- `GHR002` GITHUB_REPO_NOT_FOUND (404 · GitHub에 존재 X)
- `GHR003` GITHUB_RATE_LIMITED (429 · rate limit 도달)
- `GHR004` GITHUB_API_FAILED (502 · API 호출 실패)

### 정합성 · 멱등성
- `full_name` UNIQUE · 중복 등록 방지 (같은 리포 다른 프로젝트에서 참조 가능)
- URL 등록 = S 등급 멱등 (이미 존재하면 기존 반환)
- 캐시 실패 시 stale-while-revalidate 검토 (v0.0.5+)

### v0.0.4 확장 (OAuth · 초안)
- Spring Security OAuth 2.0 클라이언트 · GitHub provider
- `POST /oauth2/authorization/github` → GitHub 로그인 → callback
- 토큰 저장 (`user_github_credentials` 테이블 · access_token 암호화)
- 인증된 rate 5000/hr 사용

### v0.0.5 확장 (Webhook · 초안)
- 사용자 리포에 Webhook 등록 (OAuth 필요)
- `POST /api/v1/github/webhooks` (수신 엔드포인트 · HMAC 검증)
- push · release 이벤트 수신 → GitHubRepo 데이터 즉시 갱신
- Payment 웹훅 규범(ADR 005) 재사용 (INSERT-first 멱등)

### 관측
- `github.api.call.total{endpoint, result}` counter
- `github.cache.hit.total` counter
- `github.rate_limit.remaining` gauge (헤더 파싱)

## 이관 산출물 (v0.0.3 스코프)

- **BE-Story #14-1**: `github` 컨텍스트 신규 패키지 + `GitHubRepo` 엔티티 + Repository
- **BE-Story #14-2**: `GitHubUrlParser` (정규식 + validation)
- **BE-Story #14-3**: `GitHubApiAdapter` (public API + @Cacheable)
- **BE-Story #14-4**: Caffeine 캐시 설정 (`GitHubCacheConfig`)
- **BE-Story #14-5**: `GitHubRepoService.register` (URL → 파싱 → API 조회 → 저장)
- **BE-Story #14-6**: `GitHubRepoController` (3개 엔드포인트)
- **BE-Story #14-7**: `ErrorCode.GHR001~004` 등록
- **BE-Story #14-8**: 통합 테스트 (URL 파싱 · 실 GitHub API 조회 · 캐시 검증)
- **BE-Story #14-9**: Project.githubRepoId FK 통합 (이슈 #08과 함께)
- **FE-Story #14-1**: 프로젝트 등록 페이지에 GitHub URL 입력 필드 (디자인 반영)
- **FE-Story #14-2**: `src/features/github/GitHubRepoCard.tsx` (상세 페이지 다크 테마 카드)
- **FE-Story #14-3**: `src/features/github/LanguageStatsBar.tsx`
- **FE-Story #14-4**: `src/features/github/RecentCommitsList.tsx`
- **Docs-Story #14-1**: `backend-boundary/error-codes.md` GHR001~004 매핑
- **Docs-Story #14-2**: 3단계 로드맵 문서 (v0.0.3 URL · v0.0.4 OAuth · v0.0.5 Webhook)
- **SDD 개정**: 향후 `product-github-integration.md` 신규 (M3 진입 시)

## 관련 이슈 / 문서

- 관련: [#08 Project](./issue-08-project-domain.md) — Project.githubRepoId FK
- v0.0.5 관련: [#05 Payment 웹훅 멱등](../../../pes/workspectrum/sdd/done/sdd-payment-integration-report.md) — Webhook 규범 재사용
- 벤치마크: GitHub REST API v3 · Kickstarter (GitHub 미연동 · 우리 차별화)

## 디자인 참조
- `C:\Users\user\Desktop\fe\프로젝트 상세.html` — GitHub 다크 카드 (stars · forks · languages bar · recent commits)
- `C:\Users\user\Desktop\fe\프로젝트 등록.html` — GitHub 연결 UI (연결 버튼 · 연결됨 상태 표시)
