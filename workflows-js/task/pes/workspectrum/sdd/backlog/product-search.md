# [Product 18] 검색 (Search · Project · Maker 통합 · v0.0.4+ Elasticsearch 로드맵)

## Product Vision
> 후원자·메이커·방문자가 c1oud-mall의 프로젝트·메이커를 통합 검색할 수 있는 **검색 도메인**. 초기(v0.0.3)는 MySQL LIKE + 필터·정렬 조합으로 시작하고, 사용자 증가·데이터 규모 도달 시(v0.0.4+) Elasticsearch/Meilisearch로 승격한다. 최근 검색어 저장(사용자별)과 인기 검색어(일간 배치 집계)로 UX를 강화하고, 카테고리(Product 14)·태그·정렬 축(likeCount·ratingAvg)을 종합적으로 활용한다.

## 배경 및 문제
- 현재 상황
  - Project·Maker 목록 조회는 각 도메인에 개별 · 통합 검색 UX 없음
  - Discover는 있으나 그건 신호 노출 · 프로젝트 자체 검색 아님
  - 사용자가 "AI 관련 프로젝트" 등을 자연어로 찾을 수단 부재
- 문제
  - 후원 이탈률 · 원하는 프로젝트에 도달하지 못함
  - 인기 검색어·최근 검색어 UX 부재로 재방문 유도 부족
- 왜 지금
  - Project·Maker·Category·Like·Review 모든 축 완결 · 검색 필터·정렬 축 확보
  - v0.0.3 MVP 스코프: LIKE 기반 · 사용자 100명 이하 여유
  - v0.0.4+ Elasticsearch 승격 시점 확정 여유

## 목표 (To-Be)
- 신규 컨텍스트: `nbc.c1oud_mall.search.*` (4레이어)
- **통합 검색**: Project + Maker Profile · 별개 결과 or 함께 노출
- **필터**: 카테고리 · 태그(Discovery 사전) · 상태(LIVE · SUCCESSFUL 등)
- **정렬 축**: 최신순 · 인기순(likeCount) · 평점순(ratingAvg) · 마감임박순(closesAt asc) · 달성률순(raisedAmount/goalAmount)
- **최근 검색어** — 사용자별 최근 10개 저장
- **인기 검색어** — 일간 배치 집계 · 최근 7일 상위 20개
- REST 6개
- `ErrorCode.SRC001~002` (2개)
- 관측 지표 4종
- v0.0.4+ Elasticsearch 승격 로드맵 명시

## 설계 결정

- **v0.0.3: MySQL LIKE 기반 · Full-text index 활용**
  - `MATCH (title, description) AGAINST ('키워드' IN NATURAL LANGUAGE MODE)` (MySQL 5.6+ InnoDB FULLTEXT)
  - LIKE `%keyword%`는 인덱스 미활용 시 느림 → FULLTEXT 우선
- **v0.0.4+: Elasticsearch/Meilisearch 승격**
  - 인터페이스 (`SearchPort`) 분리 · Port/Adapter 재사용
  - 초기 어댑터 `MySqlSearchAdapter` · 이후 `ElasticsearchAdapter` 교체
- **검색 결과 통합 vs 분리**
  - 초기: **분리 응답** (Project 결과 + Maker 결과 각각 페이지네이션)
  - v0.0.4+: 통합 스코어링·리랭킹
- **`SearchHistory` 엔티티** — 사용자별 최근 검색어 저장 · 최대 10건 · 초과 시 오래된 것 삭제
- **`TrendingKeyword` 엔티티** — 배치 집계 결과 · 최근 7일 상위 20개
- **동의어·오타 처리** — 초기 X · v0.0.4+ Elasticsearch 승격 시
- **개인화** — 초기 X · Like/Follow 이력 참조 v0.0.5+

## 대안 검토

### v0.0.3 검색 엔진
**Option A — MySQL FULLTEXT (선택)** — 인프라 재사용 · 신입 스코프
**Option B — 초기부터 Elasticsearch** — 운영 부담 · 오버킬
**Option C — LIKE `%keyword%` (인덱스 없음)** — 성능 최악

### 결과 방식
**Option A — Project · Maker 분리 응답 (선택)** — UX 명확 · 초기 구현 간결
**Option B — 통합 응답** — 스코어링 필요 · v0.0.4+

### 최근 검색어 저장
**Option A — DB (SearchHistory · 사용자별) (선택)** — 백엔드 관리
**Option B — 브라우저 LocalStorage** — 다중 기기 동기화 불가

## 전체 아키텍처

```
presentation ──▶ application ──▶ domain ◀── infrastructure
SearchController   SearchService     SearchHistory       MySqlSearchAdapter
- search           - search          TrendingKeyword     - searchProjects(...)
- recentKeywords   - recentKeywords                      - searchMakers(...)
- trending         - trending                            SearchHistoryRepository
- deleteRecent     - deleteRecent                        TrendingKeywordRepository
                   TrendingKeywordScheduler              SearchLogAppender (선택)
                   - runDaily()                          - append(userId, keyword)
                                                         SearchPort (interface)

External refs:
- Project (Product 6) · Category (14) · Like (16) · Review (17) — 필터·정렬 축
- Maker Profile (10) — Maker 검색 대상
```

### 핵심 플로우

**1. 통합 검색**
```
FE → GET /api/v1/search?q=&category=&sort=&type=all|project|maker&page=&size=
   → SearchService.search(query)
     ├── 최근 검색어 기록 (SearchHistory)
     ├── searchLog 로깅 (인기 검색어 집계용)
     ├── if type=all or project: searchProjects(...)
     └── if type=all or maker:   searchMakers(...)
   ← SearchResponse{projects: Page, makers: Page}
```

**2. 최근 검색어**
```
GET /api/v1/search/recent · JWT
  → 사용자별 최근 10건
```

**3. 인기 검색어**
```
GET /api/v1/search/trending?limit=20 · 공개
  → TrendingKeyword 조회
```

**4. 최근 검색어 삭제**
```
DELETE /api/v1/search/recent/{id} · JWT
```

**5. 인기 검색어 배치**
```
@Scheduled(cron = "0 0 4 * * *")
TrendingKeywordScheduler.runDaily()
  ├── 최근 7일 SearchHistory 집계 · SELECT keyword, COUNT(*) GROUP BY keyword ORDER BY COUNT DESC LIMIT 20
  └── TrendingKeyword 테이블 UPSERT
```

### Out-of-Process 의존
- **RDS (MySQL)** — `search_history · trending_keyword` 테이블 · Project·Maker Profile FULLTEXT 인덱스

## 실패 모드

| 시나리오 | ErrorCode | HTTP |
|---|---|---|
| 검색어 너무 짧음 (2자 미만) | `SRC001` SEARCH_QUERY_TOO_SHORT | 400 |
| 검색어 너무 길음 (100자+) | `SRC002` SEARCH_QUERY_TOO_LONG | 400 |

## 관측 지표
- `search.query.total{type=project|maker|all}` — counter
- `search.duration_seconds{type}` — histogram
- `search.zero_result.total` — counter (결과 0건 검색어)
- `search.trending.batch.duration_seconds` — histogram

## Scope

**In (v0.0.3)**:
- MySQL FULLTEXT 기반 검색 (Project · Maker Profile)
- `SearchHistory` · `TrendingKeyword` 엔티티
- REST 6개
- 최근 검색어 · 인기 검색어
- `ErrorCode.SRC001~002`
- 관측 4종

**Out (v0.0.4+)**:
- **Elasticsearch/Meilisearch 승격** — 도메인·인터페이스는 지금 확정
- **동의어·오타 처리** — 승격 시
- **자동 완성 (Autocomplete)** — 승격 시
- **개인화 추천** — Like·Follow 참조 · v0.0.5+
- **필터 UX 확장** — 국가·언어 등 · v0.0.5+

## Epic
- [ ] Epic 1: `SearchHistory` + `TrendingKeyword` + Repository + `ErrorCode.SRC001~002`
- [ ] Epic 2: `SearchPort` + `MySqlSearchAdapter` (Project · Maker FULLTEXT)
- [ ] Epic 3: `SearchService` (search · recent · trending) + `TrendingKeywordScheduler`
- [ ] Epic 4: REST 6개 + 관측 4개 + ADR

## 관련 문서
- **이슈**: `issue-06-search.md`
- **선행 SDD**: Project (6) · Maker (10) · Category (14) · Like (16) · Review (17)
- **디자인**: `펀딩 탐색.html` search bar · 필터·정렬 UX

## 열린 질문
- **FULLTEXT MySQL 성능** — 데이터 10만건까지 감내 · 100k+ 시 Elasticsearch 승격
- **한국어 형태소 분석기** — MySQL Ngram · ES는 nori
- **최근 검색어 프라이버시** — 사용자 삭제 가능 · 자동 만료 정책 (90일?)
- **인기 검색어 부적절어 필터** — v0.0.5+
- **결과 0건 시 대안** — Discover 신호 노출? v0.0.5+ 아이디어

## Product-level DoD
- [ ] Epic 4개 완료
- [ ] E2E: keyword 검색 → Project · Maker 결과 · SearchHistory 저장
- [ ] TrendingKeyword 배치 검증
- [ ] `backend-boundary/error-codes.md` SRC001~002

---

# [Epic 1] `SearchHistory` + `TrendingKeyword` + Repository + ErrorCode

## Story
- 1-1: `SearchHistory` 엔티티
- 1-2: `TrendingKeyword` 엔티티
- 1-3: Repository + ErrorCode

---

## [Story 1-1] SearchHistory

### 스키마
```sql
CREATE TABLE search_history (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  user_id      BIGINT       NOT NULL,
  keyword      VARCHAR(100) NOT NULL,
  searched_at  TIMESTAMP    NOT NULL,
  PRIMARY KEY (id),
  KEY idx_sh_user_searched (user_id, searched_at DESC),
  KEY idx_sh_keyword_searched (keyword, searched_at DESC)   -- 인기 검색어 집계용
);
```

**엔티티**:
```java
@Entity
@Table(name = "search_history")
@NoArgsConstructor(access = PROTECTED)
@Getter
public class SearchHistory {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false, length = 100)
    private String keyword;
    @Column(nullable = false)
    private LocalDateTime searchedAt;

    public static SearchHistory create(Long userId, String keyword) {
        SearchHistory h = new SearchHistory();
        h.userId = userId;
        h.keyword = keyword;
        h.searchedAt = LocalDateTime.now();
        return h;
    }
}
```

### DoD
- [ ] 엔티티
- [ ] 단위 테스트

### SP: 0.5d

---

## [Story 1-2] TrendingKeyword

### 스키마
```sql
CREATE TABLE trending_keyword (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  keyword      VARCHAR(100) NOT NULL,
  hit_count    BIGINT       NOT NULL,
  rank_no      INT          NOT NULL,   -- 배치 시점의 순위
  window_days  INT          NOT NULL DEFAULT 7,
  computed_at  TIMESTAMP    NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tk_keyword (keyword),
  KEY idx_tk_rank (rank_no)
);
```

**엔티티**:
```java
@Entity
@Table(name = "trending_keyword")
@NoArgsConstructor(access = PROTECTED)
@Getter
public class TrendingKeyword {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String keyword;
    @Column(nullable = false)
    private long hitCount;
    @Column(nullable = false)
    private int rankNo;
    @Column(nullable = false)
    private int windowDays;
    @Column(nullable = false)
    private LocalDateTime computedAt;

    public static TrendingKeyword create(String keyword, long hitCount, int rankNo, int windowDays) {
        TrendingKeyword tk = new TrendingKeyword();
        tk.keyword = keyword;
        tk.hitCount = hitCount;
        tk.rankNo = rankNo;
        tk.windowDays = windowDays;
        tk.computedAt = LocalDateTime.now();
        return tk;
    }

    public void update(long hitCount, int rankNo) {
        this.hitCount = hitCount;
        this.rankNo = rankNo;
        this.computedAt = LocalDateTime.now();
    }
}
```

### DoD
- [ ] 엔티티
- [ ] 단위 테스트

### SP: 0.5d

---

## [Story 1-3] Repository + ErrorCode

### Repository
```java
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {
    Page<SearchHistory> findByUserIdOrderBySearchedAtDesc(Long userId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM SearchHistory h WHERE h.userId = :userId AND h.id NOT IN " +
        "(SELECT h2.id FROM SearchHistory h2 WHERE h2.userId = :userId ORDER BY h2.searchedAt DESC LIMIT 10)")
    void trimToLatest10(@Param("userId") Long userId);

    @Query(value = """
        SELECT keyword, COUNT(*) AS cnt
        FROM search_history
        WHERE searched_at > :since
        GROUP BY keyword
        ORDER BY cnt DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> aggregateTrending(@Param("since") LocalDateTime since, @Param("limit") int limit);
}

public interface TrendingKeywordRepository extends JpaRepository<TrendingKeyword, Long> {
    Optional<TrendingKeyword> findByKeyword(String keyword);
    List<TrendingKeyword> findAllByOrderByRankNoAsc(Pageable pageable);
}
```

### ErrorCode
- SRC001 SEARCH_QUERY_TOO_SHORT (400)
- SRC002 SEARCH_QUERY_TOO_LONG (400)

### DoD
- [ ] Repository
- [ ] ErrorCode
- [ ] `@DataJpaTest`

### SP: 0.5d

---

# [Epic 2] `SearchPort` + `MySqlSearchAdapter`

## Story
- 2-1: `SearchPort` + `SearchQuery` + `SearchResult` DTO
- 2-2: `MySqlSearchAdapter` (Project · FULLTEXT + 필터·정렬)
- 2-3: `MySqlSearchAdapter` (Maker Profile · FULLTEXT)

---

## [Story 2-1] Port + DTO

### 설명
```java
public interface SearchPort {
    Page<ProjectSearchResult> searchProjects(ProjectSearchQuery query);
    Page<MakerSearchResult> searchMakers(MakerSearchQuery query);
}

public record ProjectSearchQuery(
    String keyword,
    Long categoryId,   // null OK
    List<FundingStatus> statuses,
    ProjectSortAxis sort,   // LATEST · POPULAR · RATING · CLOSING · PROGRESS
    Pageable pageable
) {}

public record MakerSearchQuery(String keyword, Pageable pageable) {}

public record ProjectSearchResult(
    Long id, String title, String summary,
    long raisedAmount, long goalAmount, BigDecimal progressPct,
    long backerCount, long likeCount,
    BigDecimal ratingAvg, long ratingCount,
    FundingStatus status, LocalDateTime closesAt
) {}

public record MakerSearchResult(
    Long userId, String displayName, String bio,
    BigDecimal responseRate, int activeProjectCount, boolean verified
) {}

public enum ProjectSortAxis { LATEST, POPULAR, RATING, CLOSING, PROGRESS }
```

### DoD
- [ ] Port · DTO 5개
- [ ] 단위 테스트

### SP: 0.5d

---

## [Story 2-2] MySqlSearchAdapter · Project

### 설명
- MySQL FULLTEXT 인덱스 필수: Project 테이블 `title, description` 컬럼
- Migration: `ALTER TABLE project ADD FULLTEXT INDEX ft_project_title_desc (title, description) WITH PARSER ngram;`
- QueryDSL 활용:
  - keyword FULLTEXT
  - categoryId 등호
  - statuses IN
  - sort 축별 order by

**핵심 파일**:
- `nbc.c1oud_mall.search.infrastructure.MySqlSearchAdapter`

**주요 메서드**:
```java
@Component
@RequiredArgsConstructor
public class MySqlSearchAdapter implements SearchPort {
    private final JPAQueryFactory queryFactory;

    @Override
    public Page<ProjectSearchResult> searchProjects(ProjectSearchQuery q) {
        QProject p = QProject.project;

        BooleanBuilder where = new BooleanBuilder();
        if (q.keyword() != null && !q.keyword().isBlank()) {
            // FULLTEXT MATCH ... AGAINST · Native or Template
            where.and(Expressions.booleanTemplate(
                "function('match_against', {0}, {1}, {2})",
                p.title, p.description, q.keyword()));
        }
        if (q.categoryId() != null) where.and(p.categoryId.eq(q.categoryId()));
        if (q.statuses() != null && !q.statuses().isEmpty())
            where.and(p.status.in(q.statuses()));

        OrderSpecifier<?> orderBy = switch (q.sort()) {
            case LATEST   -> p.createdAt.desc();
            case POPULAR  -> p.likeCount.desc();
            case RATING   -> p.ratingAvg.desc();
            case CLOSING  -> p.closesAt.asc();
            case PROGRESS -> new OrderSpecifier<>(Order.DESC,
                Expressions.numberTemplate(BigDecimal.class,
                    "({0} / {1})", p.raisedAmount, p.goalAmount));
        };

        List<Project> results = queryFactory.selectFrom(p)
            .where(where)
            .orderBy(orderBy)
            .offset(q.pageable().getOffset())
            .limit(q.pageable().getPageSize())
            .fetch();

        long total = Optional.ofNullable(queryFactory.select(p.count()).from(p).where(where).fetchOne())
            .orElse(0L);

        List<ProjectSearchResult> mapped = results.stream()
            .map(this::toResult).toList();
        return new PageImpl<>(mapped, q.pageable(), total);
    }

    private ProjectSearchResult toResult(Project p) {
        BigDecimal pct = (p.getGoalAmount() == 0)
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(p.getRaisedAmount() * 100.0 / p.getGoalAmount())
                          .setScale(1, RoundingMode.HALF_UP);
        return new ProjectSearchResult(p.getId(), p.getTitle(), p.getSummary(),
            p.getRaisedAmount(), p.getGoalAmount(), pct,
            p.getBackerCount(), p.getLikeCount(),
            p.getRatingAvg(), p.getRatingCount(),
            p.getStatus(), p.getClosesAt());
    }
}
```

### AC
- Given 프로젝트 10건 · keyword="AI" · Then FULLTEXT 결과
- Given category 필터 · Then 해당 카테고리만
- Given sort=POPULAR · Then likeCount desc

### DoD
- [ ] Adapter · MATCH_AGAINST 함수 등록 (JPA custom function)
- [ ] FULLTEXT 인덱스 마이그레이션
- [ ] 통합 테스트

### SP: 2d

---

## [Story 2-3] MySqlSearchAdapter · Maker

### 설명
- Maker Profile `bio, display_name` FULLTEXT 인덱스
- 유사한 패턴

```java
@Override
public Page<MakerSearchResult> searchMakers(MakerSearchQuery q) {
    // FULLTEXT on maker_profile.bio + user.display_name
    // ...
}
```

### AC
- keyword 매칭 Maker 반환

### DoD
- [ ] Adapter 확장
- [ ] Maker Profile FULLTEXT 인덱스 (Product 10 마이그레이션 확장)
- [ ] 통합 테스트

### SP: 1d

---

# [Epic 3] `SearchService` + `TrendingKeywordScheduler`

## Story
- 3-1: SearchService (search · recent · trim · delete)
- 3-2: SearchService (trending) + `TrendingKeywordScheduler`

---

## [Story 3-1] search · recent · delete

### 설명
```java
@Service
@Transactional
@RequiredArgsConstructor
public class SearchService {
    private final SearchPort searchPort;
    private final SearchHistoryRepository historyRepository;
    private final TrendingKeywordRepository trendingRepository;
    private final MeterRegistry meterRegistry;

    @Transactional
    public SearchResponse search(Long userId, SearchCommand cmd) {
        validate(cmd.keyword());
        recordHistory(userId, cmd.keyword());
        Page<ProjectSearchResult> projects = null;
        Page<MakerSearchResult> makers = null;
        long start = System.currentTimeMillis();

        if (cmd.type() == SearchType.ALL || cmd.type() == SearchType.PROJECT)
            projects = searchPort.searchProjects(toProjectQuery(cmd));
        if (cmd.type() == SearchType.ALL || cmd.type() == SearchType.MAKER)
            makers = searchPort.searchMakers(toMakerQuery(cmd));

        long duration = System.currentTimeMillis() - start;
        meterRegistry.timer("search.duration_seconds", "type", cmd.type().name())
                     .record(duration, MILLISECONDS);
        meterRegistry.counter("search.query.total", "type", cmd.type().name()).increment();
        if ((projects == null || projects.isEmpty()) && (makers == null || makers.isEmpty()))
            meterRegistry.counter("search.zero_result.total").increment();

        return new SearchResponse(projects, makers);
    }

    private void validate(String keyword) {
        if (keyword == null || keyword.trim().length() < 2)
            throw new BusinessException(ErrorCode.SRC001);
        if (keyword.length() > 100)
            throw new BusinessException(ErrorCode.SRC002);
    }

    private void recordHistory(Long userId, String keyword) {
        if (userId == null) return;
        historyRepository.save(SearchHistory.create(userId, keyword.trim()));
        historyRepository.trimToLatest10(userId);
    }

    @Transactional(readOnly = true)
    public List<RecentKeywordResponse> recentKeywords(Long userId) {
        return historyRepository.findByUserIdOrderBySearchedAtDesc(userId, PageRequest.of(0, 10))
            .stream()
            .map(h -> new RecentKeywordResponse(h.getId(), h.getKeyword(), h.getSearchedAt()))
            .toList();
    }

    public void deleteRecent(Long userId, Long historyId) {
        SearchHistory h = historyRepository.findById(historyId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        if (!Objects.equals(h.getUserId(), userId))
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        historyRepository.delete(h);
    }
}
```

### AC
- 유효 keyword 검색 · 결과 반환 · SearchHistory 저장
- 짧은 keyword · SRC001
- 긴 keyword · SRC002

### DoD
- [ ] Service · Command · Response
- [ ] 통합 테스트

### SP: 1.5d

---

## [Story 3-2] trending + Scheduler

### 설명
```java
@Transactional(readOnly = true)
public List<TrendingKeywordResponse> trending(int limit) {
    return trendingRepository.findAllByOrderByRankNoAsc(PageRequest.of(0, limit))
        .stream()
        .map(tk -> new TrendingKeywordResponse(tk.getKeyword(), tk.getHitCount(), tk.getRankNo()))
        .toList();
}

@Component
@RequiredArgsConstructor
@Slf4j
public class TrendingKeywordScheduler {
    private final SearchHistoryRepository historyRepository;
    private final TrendingKeywordRepository trendingRepository;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void runDaily() {
        long start = System.currentTimeMillis();
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<Object[]> rows = historyRepository.aggregateTrending(since, 20);

        int rank = 1;
        for (Object[] row : rows) {
            String kw = (String) row[0];
            long cnt = ((Number) row[1]).longValue();
            trendingRepository.findByKeyword(kw)
                .map(tk -> {
                    tk.update(cnt, rank);
                    return tk;
                })
                .orElseGet(() -> trendingRepository.save(
                    TrendingKeyword.create(kw, cnt, rank, 7)
                ));
            rank++;
        }

        long duration = System.currentTimeMillis() - start;
        meterRegistry.timer("search.trending.batch.duration_seconds")
                     .record(duration, MILLISECONDS);
        log.info("SEARCH_TRENDING_BATCH_DONE processed={} duration={}ms",
            rows.size(), duration);
    }
}
```

### AC
- 최근 7일 검색 로그 → 상위 20개 UPSERT
- 재실행 시 idempotent (rank 갱신)

### DoD
- [ ] Service · Scheduler
- [ ] 통합 테스트

### SP: 1d

---

# [Epic 4] REST 6개 + 관측 + ADR

## Story
- 4-1: `SearchController` REST 6개
- 4-2: 관측 지표 4개 · Prometheus 노출
- 4-3: ADR + 규범 갱신

---

## [Story 4-1] REST 6개

### 엔드포인트
| Method | Path | 인증 |
|---|---|---|
| GET | `/api/v1/search?q=&category=&type=&sort=&page=&size=` | 옵션 (JWT는 recent 저장에 활용) |
| GET | `/api/v1/search/recent` | JWT |
| DELETE | `/api/v1/search/recent/{id}` | JWT |
| DELETE | `/api/v1/search/recent` | JWT · 전체 삭제 |
| GET | `/api/v1/search/trending?limit=20` | 공개 |
| GET | `/api/v1/search/autocomplete?q=` | 공개 · **v0.0.4+ · 초기 501 or 안 노출** |

### DoD
- [ ] Controller
- [ ] `@WebMvcTest`

### SP: 1d

---

## [Story 4-2] 관측

### 설명
- 4개 지표 (§관측 지표 참조)
- Prometheus 엔드포인트에서 검증

### DoD
- [ ] MeterRegistry 주입 · 지표 4개
- [ ] Actuator 노출 확인

### SP: 0.5d

---

## [Story 4-3] ADR + 규범

### 설명
- ADR 2건:
  - `035-search-mysql-fulltext-and-elasticsearch-roadmap.md`
  - `036-search-history-and-trending-batch.md`
- `backend-boundary/error-codes.md` SRC001~002

### SP: 0.5d

---

## 요약
| Epic | Story | SP |
|---|---|---|
| Epic 1 | 3 | 1.5 |
| Epic 2 | 3 | 3.5 |
| Epic 3 | 2 | 2.5 |
| Epic 4 | 3 | 2.0 |
| **합계** | **11** | **9.5 SP** |

## 완결 → 후속
- 사용자가 자연어로 프로젝트·메이커 검색 가능
- 최근 검색어·인기 검색어 UX 확보
- v0.0.4+: Elasticsearch 승격 · 자동완성 · 개인화 검색 진입점

---

# 전체 SDD 완결 요약 (Product 5 ~ 18)

`workflows/task/pes/workspectrum/sdd/in-progress/`에 14개 SDD 완결:

| # | Product | 스토리 포인트 | 역할 요약 |
|---|---|---|---|
| 5 | Wallet | 11 | 지갑·잔액 SSOT·PortOne V2 |
| 6 | Project | 12 | 10-state 크라우드 펀딩 도메인 |
| 7 | Reward Tier | 11 | Kickstarter 티어·재고 |
| 8 | Pledge | 13 | 3-way 통합·All-or-Nothing |
| 9 | Chat | 13 | WebSocket + Rich Message |
| 10 | Maker Profile | 11 | 응답 지표 배치·온라인 상태 |
| 11 | Media | 11.5 | S3 Pre-signed 2단계·MinIO/S3 |
| 12 | GitHub | 10 | 3단계 로드맵·REST v3 |
| 13 | Discovery | 12 | 한국 RSS 6개·Gemini + Static |
| 14 | Category | 4.5 | 2-depth·초기 시딩 |
| 15 | Follow | 3 | 팔로우 관계·반정규화 로드맵 |
| 16 | Like | 3.5 | 좋아요·위시리스트 통합 |
| 17 | Review | 4.5 | FUNDED Pledge 검증·평점 반정규화 |
| 18 | Search | 9.5 | MySQL FULLTEXT·인기 검색어 배치 |

**총 스토리 포인트**: 약 129 SP (M3 스코프)
**Epic 개수 합계**: 약 45개
**Story 개수 합계**: 약 115개
