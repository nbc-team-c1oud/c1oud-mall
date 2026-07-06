# [Product 13] Discovery Signal Collector (한국 IT 신호 수집 · Gemini 요약 · Discover 페이지)

## Product Vision
> c1oud-mall의 Discover 페이지에 노출할 **한국 시장 application 소프트웨어 인기 신호**를 매일 자동 수집·요약한다. 긱뉴스 + 대기업 기술 블로그 5개 RSS를 배치로 수집하고, Gemini 2.0 Flash로 한국어 3줄 요약·태그 부여를 자동화하며, Static Fallback을 두어 AI 실패 시에도 UX가 유지된다. 사용자가 관심 신호에서 즉시 크라우드 펀딩 프로젝트를 시작할 수 있는 **`use-for-project`** 흐름으로 c1oud-mall의 크라우드 펀딩 컨셉과 유기적으로 결합한다.

## 배경 및 문제
- 현재 상황 (As-Is)
  - c1oud-mall은 크라우드 펀딩 대상(*GitHub 학생 프로젝트*)이 명확하지만 **후원자·메이커 모두 아이디어를 어디서 찾아야 하는지 부재**
  - 후원자는 "어떤 프로젝트가 지금 뜨는지" 자체 검색해야 함 · 이탈률 상승
  - 메이커는 "지금 문제로 떠오르는 것"을 알아야 프로젝트 방향 잡기 유리 · 정보 격차
  - 초기 사용자 부족 시 Discovery 콘텐츠가 상시 노출되면 사용자 유입 확보
  - 이슈 #15 R7 확정: **긱뉴스 + 대기업 기술 블로그 5개 (D2 · 카카오 · 토스 · 우아한형제들 · 라인)** · 모두 RSS · 크롤링 X
- 발생하는 문제
  - 사용자가 c1oud-mall 방문 → 후원할 프로젝트가 없거나 유사 프로젝트만 반복 노출 → 이탈
  - Discovery 부재 시 프로젝트 방향 결정 · 아이디어 발상 지원 부족
  - 저작권·robots.txt 리스크 회피 필요 (크롤링 X)
- 왜 지금 해결해야 하는가
  - 초기 사용자 유입 관점에서 콘텐츠 상시 노출 필수
  - Gemini 무료 tier 활용 가능한 시점 (15000 req/day)
  - 이슈 #09 (AI Port/Adapter 재사용) 인프라 활용
  - v0.0.5+ 글로벌 소스 확장 여지 열어두기

## 목표 (To-Be)
- 신규 컨텍스트: `nbc.c1oud_mall.discovery.*` (4레이어)
- **소스**: 긱뉴스 + 대기업 기술 블로그 5개 · 총 6개 (모두 RSS · 인증 X)
- `DiscoveryItem` 엔티티 (신호 항목 · 스키마 §3.6)
- 3개 enum: `SourceType(GEEKNEWS·NAVER_D2·KAKAO_TECH·TOSS_TECH·WOOWA_TECH·LINE_ENG)` · `SourceCategory(NEWS · CORPORATE_BLOG)` · `AiStatus(PENDING · COMPLETED · FAILED)`
- **Port/Adapter 패턴** (이슈 #09 재사용):
  - `DiscoverySignalCollectorPort` · 6개 어댑터 (`GeekNewsCollector · NaverD2Collector` 등)
  - `SignalSummaryPort` · `GeminiSignalSummaryAdapter` + `StaticSignalSummaryAdapter`
- **배치 스케줄러** `DiscoveryCollectScheduler` — 매일 새벽 3시 · 소스별 순차 · 각 상위 10~20건 · 총 60~120건
- **Gemini 프롬프트**: 3줄 요약 · 15개 태그 사전 중 최대 3개 선택 (한국어)
- **Discover API 5개**:
  - `GET /api/v1/discovery/items?category=&tag=&sort=&page=` · 공개
  - `GET /api/v1/discovery/items/{id}` · 공개 · 상세
  - `GET /api/v1/discovery/tags/trending?limit=20` · 공개 · 인기 태그
  - `POST /api/v1/discovery/items/{id}/use-for-project` · JWT · 이슈 #08 등록 초기 데이터 전달
  - `POST /api/v1/admin/discovery/collect?source=` · 관리자 · 수동 트리거
- `ErrorCode.DSC001~004` (4개)
- 관측 지표 4종
- Soft Delete + 30일 후 실 삭제 (Media Product 11 패턴 재사용)
- **저작권 준수**: 요약만 저장 · 원문 본문 저장 X · 출처 링크 필수

## 설계 결정 (Design Decisions)

- **RSS 표준만 · 크롤링 X** (이슈 #15 R7 확정)
  - robots.txt 무관 · 저작권 안전 · 소스 명시 허용
  - 초기 6개 소스 모두 RSS 지원 (긱뉴스 · 대기업 기술 블로그)
- **한국 시장·application 소프트웨어 스코프**
  - 소스 자체가 이미 한국 소스 · 초기 필터 X
  - 태그 사전이 한국어 · UI 한국어 UX
- **AI 이중 어댑터** (이슈 #09 정합)
  - `SignalSummaryPort` 인터페이스
  - `GeminiSignalSummaryAdapter` — provider=llm · Gemini 2.0 Flash · 무료 tier 15k/일
  - `StaticSignalSummaryAdapter` — provider=static · 원본 제목 = 요약 · 태그 없음
  - `@ConditionalOnProperty(name="c1oudmall.discovery.provider", value="gemini", matchIfMissing=false)` · 실패 시 fallback
- **AI 실패 시 Static Fallback 자동 승격**
  - Gemini 실패 → `ai_status=FAILED` → Static Adapter로 재요약 → `ai_status=COMPLETED` (provider 태깅)
- **소스별 어댑터 → 통합 Collector 서비스**
  - 각 어댑터가 RSS 파싱·표준 DTO 반환 · 서비스가 저장 지휘
- **배치 순차 실행 (소스별)** — 병렬 X · Rate Limit 예의
  - 매일 새벽 3시 · 총 60~120건 · 요청 간 1초 대기
- **AI 요약은 배치 안에서 순차** — 총 60~120회 · Gemini 무료 tier(15k/일) 내
- **Soft Delete + 30일 실 삭제** (Media Product 11 패턴)
- **`use-for-project` 흐름**
  - Discovery item → Project 등록 페이지 사전 채움 (`title, description, tags`)
  - 이슈 #08 연결 · 크라우드 펀딩 흐름 완결
- **사용자 인터랙션 저장 X (초기)** — 좋아요·북마크는 Like(Product 16)와 별도 · Discovery는 노출만
- **인기 태그 산정** — 최근 30일 태그 발생 빈도 상위 20개

## 대안 검토 (Alternatives Considered)

### 데이터 소스
**Option A — 긱뉴스 + 대기업 기술 블로그 5개 · 모두 RSS (선택 · R7)**
- 저작권 안전 · robots.txt 안전 · 한국 IT 커뮤니티 신뢰도

**Option B — 웹 크롤링 (디스콰이엇 · OKKY 등)**
- 거부 이유: robots.txt 확인 부담 · 저작권 리스크 · v0.0.5+ 검토

**Option C — GitHub Trending · HN · Reddit (글로벌 소스)**
- 거부 이유: 한국 시장 스코프 · v0.0.4+ 글로벌 확장

### 요약 방식
**Option A — Gemini 2.0 Flash + Static Fallback (선택 · R5)**
- 무료 tier · 한국어 우수 · 이슈 #09 인프라 재사용

**Option B — Claude Haiku**
- 거부 이유: 요금 · 학생 프로젝트 스코프 오버

**Option C — 원본 요약만 (AI X)**
- 거부 이유: 이미 있는 요약은 자막·긴 문장 · UX 저하

### 배치 주기
**Option A — 매일 새벽 3시 (선택)**
- Gemini 무료 tier 안 · 다른 도메인 배치와 시간 겹침 낮음

**Option B — 6시간 (하루 4회)**
- 거부 이유: Gemini 무료 tier 초과 위험

**Option C — 실시간 (RSS Webhook)**
- 거부 이유: RSS Webhook 미지원 · Poll 부담

### 노출 방식
**Option A — Discover 페이지 단독 (선택 · R4)**
- 헤더 네비 신설 · 스코프 명확

**Option B — 프로젝트 상세 페이지에도 관련 신호 노출**
- 거부 이유: 스코프 오버 · v0.0.5+ 검토

## 전체 아키텍처

### 컴포넌트 배치
```
presentation ──▶ application ──▶ domain ◀── infrastructure
DiscoveryController  DiscoveryService     DiscoveryItem      DiscoveryItemRepository
- listItems          - listItems          - createPending()  - findByCategory(...)
- getItem            - getItem            - completeAi()     - findTrendingTags
- trendingTags       - trendingTags       - failAi()         - findByAiStatus
- useForProject      - useForProject      - softDelete()     6 Collectors (Adapters):
- adminCollect       - triggerCollect     SourceType          - GeekNewsCollector
                     DiscoveryCollector   SourceCategory     - NaverD2Collector
                     - collectAll         AiStatus            - KakaoTechCollector
                     - collectOne(src)                        - TossTechCollector
                     DiscoveryCollectScheduler                - WoowaTechCollector
                     - runDaily()                              - LineEngCollector
                     DiscoveryCleanupScheduler                DiscoverySignalCollectorPort
                     - runDaily()                              (interface)
                     SignalSummaryPort                        GeminiSignalSummaryAdapter
                     - summarize(...)                         StaticSignalSummaryAdapter
```

### 핵심 플로우

**1. 배치 수집 (매일 새벽 3시)**
```
@Scheduled(cron = "0 0 3 * * *")
DiscoveryCollectScheduler.runDaily()
  For each source (GEEKNEWS → LINE_ENG):
    List<SignalDto> signals = collector.collect()  (상위 10~20건)
    For each signal:
      if exists(source_type, source_id): skip
      item = DiscoveryItem.createPending(signal)
      save(item)  → ai_status=PENDING
    Thread.sleep(1000)   -- 예의 대기

  For each PENDING item:
    try:
      SummaryDto s = signalSummaryPort.summarize(item.title, item.description)
      item.completeAi(s.summary, s.tags, s.provider)
    catch (Exception):
      log.error("DISCOVERY_AI_FAILED itemId={}", item.id)
      item.failAi()
      -- 폴백 Static Adapter로 자동 재요약 or 다음 배치에서
```

**2. Discover 페이지 조회**
```
FE → GET /api/v1/discovery/items?category=NEWS&tag=AI&sort=latest&page=0
   → DiscoveryService.listItems(query)
     → repository.findByCategoryAndTag(...)
   ← Page<DiscoveryItemResponse>
```

**3. 인기 태그 조회**
```
FE → GET /api/v1/discovery/tags/trending?limit=20
   → DiscoveryService.trendingTags(30일)
     → repository.findTrendingTags(since, limit)   -- 최근 30일 태그 발생 빈도
   ← List<TagCountResponse>
```

**4. `use-for-project` 흐름**
```
FE (Discover 상세) → POST /api/v1/discovery/items/{id}/use-for-project
  → DiscoveryService.useForProject(userId, itemId)
    ├── item 조회
    ├── ProjectDraftInitData 생성 · session or 임시 저장
    └── 반환 · FE는 이 데이터로 프로젝트 등록 페이지로 라우팅
  ← {redirectUrl: "/projects/new?draft=xyz", initData: {title, description, tags, sourceUrl}}
```

**5. 30일 배치 정리**
```
@Scheduled(cron = "0 0 4 * * *")   -- Media(4시)와 겹치나 다른 리소스
DiscoveryCleanupScheduler.runDaily()
  ├── created_at 30일 초과 · softDelete()
  └── deleted_at 60일 초과 · DB 실 DELETE
```

### Out-of-Process 의존
- **RSS 소스 6개**: 각 소스의 RSS URL (v0.0.3 착수 시 검증)
- **Gemini API** (v1beta · Google AI Studio 무료 tier)
- **RDS (MySQL)** — `discovery_item` 테이블

## 실패 모드 / 관측

### 실패 시나리오
| 시나리오 | ErrorCode | HTTP |
| --- | --- | --- |
| item 없음 | `DSC001` DISCOVERY_ITEM_NOT_FOUND | 404 |
| 소스 Rate Limit | `DSC002` DISCOVERY_SOURCE_RATE_LIMITED | 503 |
| AI 요약 실패 (Fallback도 실패) | `DSC003` DISCOVERY_AI_FAILED | 500 |
| 잘못된 소스 파라미터 (관리자 트리거) | `DSC004` DISCOVERY_INVALID_SOURCE | 400 |

### 로깅
- **항상**: requestId · itemId · source · action
- **debug**: RSS 원본 응답 크기 · Gemini 요청/응답 raw
- **금지**: Gemini API Key · 원본 본문 raw (라이선스 이슈)
- **특수 마커**:
  - `DISCOVERY_COLLECT_FAILED source={} reason={}`
  - `DISCOVERY_AI_FAILED itemId={} provider={}`
  - `DISCOVERY_AI_FALLBACK itemId={}` (Gemini → Static 승격)

### 관측 지표
- `discovery.collect.total{source, result}` — counter
- `discovery.ai.total{provider=gemini|static, result}` — counter
- `discovery.item.gauge{source}` — 저장 item 수
- `discovery.batch.duration_seconds` — histogram

## 롤아웃

### 전제
- Gemini API Key 확보 · `application-prod.yml`에 secret 관리
- 6개 RSS URL 실 접근 검증 (v0.0.3 착수)
- 초기 사용자 없음 · 신규 테이블만

### Product 의존성
- **선행**: 없음 (Project·GitHub 참조 X · use-for-project는 후속 흐름)
- **후행**: Project (Product 6) `use-for-project` 사전 채움 · GitHub (12)와 향후 결합
- **동시 대응**: 이슈 #09 AI Port/Adapter 인프라 재활용

### Epic·Story 의존성
```
Epic 1 (도메인·enum·ErrorCode) ──► Epic 2 (Collector · SignalSummary Port + Adapter)
                                        └─► Epic 3 (배치 · Service · use-for-project)
                                              └─► Epic 4 (REST · 관측 · 정리 배치 · ADR)
```

## KPI
| 지표 | 목표 | 측정 |
| --- | --- | --- |
| 배치 성공률 | ≥ 95% | `discovery.collect.total{result=success}` |
| Gemini 요약 성공률 | ≥ 90% | `discovery.ai.total{provider=gemini,result=success}` |
| Static Fallback 발동률 | ≤ 10% | `discovery.ai.total{provider=static}` / total |
| 배치 처리 시간 P95 (120건) | ≤ 5분 | histogram |
| `use-for-project` 전환율 | ≥ 5% | Discover 조회 → Project 시작 비율 |

## Scope

**In**:
- `discovery.*` 4레이어
- 6개 Collector 어댑터
- Gemini + Static SignalSummary 이중 어댑터
- 배치 수집 · 요약 · 정리
- REST 5개
- `ErrorCode.DSC001~004`
- 관측 4종
- 태그 사전 (한국어 15개)

**Out**:
- **글로벌 소스** (HN · Reddit · GitHub Trending) — v0.0.4+
- **웹 크롤링 소스** (디스콰이엇 · OKKY) — v0.0.5+
- **사용자 인터랙션 저장 (좋아요·북마크)** — Like Product 16 스코프
- **개인화 추천** — v0.0.5+
- **번역** — 한국 소스만이라 불필요
- **알림** (관심 태그 신호 발생 시) — v0.0.5+
- **Webhook / 실시간** — RSS 미지원

## 대상 사용자
- **후원자 · 예비 후원자**: Discover에서 아이디어 발견 · 트렌드 파악
- **메이커 · 예비 메이커**: 프로젝트 방향 발상 · `use-for-project` 초기 데이터
- **일반 방문자**: c1oud-mall 유입 콘텐츠
- **운영자**: 배치·AI 실패 대응 · 소스 관리
- **후속 SDD 작성자**: Like·Notification 연결점

## Epic 목록
- [ ] Epic 1: `DiscoveryItem` + 3 enum + Repository + `ErrorCode.DSC001~004` + 태그 사전
- [ ] Epic 2: 6개 Collector 어댑터 + `SignalSummaryPort` + `GeminiSignalSummaryAdapter` + `StaticSignalSummaryAdapter`
- [ ] Epic 3: `DiscoveryService` + `DiscoveryCollectScheduler` + `use-for-project` 흐름
- [ ] Epic 4: REST 5개 + `DiscoveryCleanupScheduler` + 관측 4개 + ADR

## 관련 문서
- **원본 이슈**: `issue-15-discovery-signal-collector.md`
- **선행 이슈**: `issue-09-ai-integration.md` (Port/Adapter 재사용)
- **연결**: `issue-08-project-domain.md` (`use-for-project`)
- **디자인**: `펀딩 탐색.html` (grid layout · 카드 UX 재사용)
- **외부 참조**:
  - Gemini API: `https://ai.google.dev/gemini-api/docs`
  - ROME RSS 파서 or Rome library (`com.rometools:rome`)
- **ADR 후보**:
  - "Discovery 소스 스코프 (한국 RSS 6개)"
  - "Gemini + Static Fallback 이중 어댑터"
  - "Soft Delete + 30일 정리 배치"

## 열린 질문
- **RSS URL 정확성** — v0.0.3 착수 시 실 검증 (문서 URL이 오래된 경우 있음)
- **Gemini 요금 폭증 대비** — 무료 tier 초과 시 Static Fallback 자동 전환 or 배치 중단
- **태그 사전 확장 정책** — 초기 15개 · 사용 데이터 축적 후 조정
- **원본 본문 저장 여부** — 초기 X · 요약만 · 저작권 안전
- **인기 태그 산정 기간 30일** — 짧으면 노이즈 · 길면 최신성 부족
- **`use-for-project` 초기 데이터 세션 만료** — 30분? 24시간?
- **다국어 확장** — 한국 소스만이라 초기 미지원 · v0.0.5+

## Product-level DoD
- [ ] Epic 4개 완료
- [ ] E2E 시나리오 1: 6개 소스 배치 → 60~120건 저장 → Gemini 요약 성공 90%+
- [ ] E2E 시나리오 2: Gemini 실패 → Static Fallback 자동 → item 완결
- [ ] E2E 시나리오 3: Discover 조회 → 상세 → use-for-project → Project 등록 페이지 라우팅
- [ ] E2E 시나리오 4: 30일 초과 · Soft Delete
- [ ] ADR 3건 · `backend-boundary/error-codes.md` DSC001~004
- [ ] Gemini 무료 tier 안 동작 검증

---

# [Epic 1] `DiscoveryItem` + 3 enum + Repository + `ErrorCode.DSC001~004`

## 포함 Story
- Story 1-1: `DiscoveryItem` 엔티티 + 3개 enum + 태그 사전
- Story 1-2: 도메인 메서드 5개 (`createPending · completeAi · failAi · softDelete · isExpired`)
- Story 1-3: Repository + `ErrorCode.DSC001~004`

---

## [Story 1-1] 엔티티 + enum + 태그 사전

### 스키마
```sql
CREATE TABLE discovery_item (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  source_type       VARCHAR(30)  NOT NULL,          -- GEEKNEWS · NAVER_D2 · KAKAO_TECH · TOSS_TECH · WOOWA_TECH · LINE_ENG
  source_category   VARCHAR(30)  NOT NULL,          -- NEWS · CORPORATE_BLOG
  source_id         VARCHAR(200) NOT NULL,          -- 원본 GUID
  title             VARCHAR(500) NOT NULL,
  url               VARCHAR(500) NOT NULL,
  author            VARCHAR(100) NULL,
  original_score    INT          NULL,              -- 긱뉴스만 (블로그 NULL)
  ai_summary_ko     VARCHAR(500) NULL,
  ai_tags           JSON         NULL,              -- ["웹","AI"] · 최대 3개
  ai_provider       VARCHAR(20)  NULL,              -- gemini | static
  ai_status         VARCHAR(20)  NOT NULL,          -- PENDING · COMPLETED · FAILED
  published_at      TIMESTAMP    NOT NULL,
  collected_at      TIMESTAMP    NOT NULL,
  deleted_at        TIMESTAMP    NULL,
  created_at        TIMESTAMP    NOT NULL,
  updated_at        TIMESTAMP    NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_disc_source (source_type, source_id),
  KEY idx_disc_category_published (source_category, published_at DESC),
  KEY idx_disc_ai_status (ai_status),
  KEY idx_disc_deleted (deleted_at)
);
```

**enum**:
```java
public enum SourceType { GEEKNEWS, NAVER_D2, KAKAO_TECH, TOSS_TECH, WOOWA_TECH, LINE_ENG }
public enum SourceCategory { NEWS, CORPORATE_BLOG }
public enum AiStatus { PENDING, COMPLETED, FAILED }

public final class DiscoveryTags {
    public static final Set<String> DICTIONARY = Set.of(
        "웹", "모바일앱", "SaaS", "AI", "데이터", "개발도구",
        "인프라·DevOps", "생산성", "커뮤니티", "핀테크", "커머스",
        "게임", "미디어", "교육", "보안"
    );
    public static final int MAX_TAGS_PER_ITEM = 3;
}
```

### AC
- Given source_type=GEEKNEWS · source_id=GUID · 중복 저장 시도 · Then UNIQUE 위반
- Given `DiscoveryItem.createPending(...)` · Then ai_status=PENDING

### DoD
- [ ] 엔티티 + 3 enum + 태그 사전 클래스
- [ ] DDL 확인

### SP: 1d

---

## [Story 1-2] 도메인 메서드 5개

### 설명
```java
public static DiscoveryItem createPending(SignalDto signal) {
    DiscoveryItem d = new DiscoveryItem();
    d.sourceType = signal.sourceType();
    d.sourceCategory = signal.sourceCategory();
    d.sourceId = signal.sourceId();
    d.title = signal.title();
    d.url = signal.url();
    d.author = signal.author();
    d.originalScore = signal.originalScore();
    d.publishedAt = signal.publishedAt();
    d.collectedAt = LocalDateTime.now();
    d.aiStatus = AiStatus.PENDING;
    return d;
}

public void completeAi(String summaryKo, List<String> tags, String provider) {
    // 태그 검증
    for (String t : tags) {
        if (!DiscoveryTags.DICTIONARY.contains(t))
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);   // 프롬프트 위반 감지
    }
    if (tags.size() > DiscoveryTags.MAX_TAGS_PER_ITEM)
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    this.aiSummaryKo = summaryKo;
    this.aiTags = tags;
    this.aiProvider = provider;
    this.aiStatus = AiStatus.COMPLETED;
}

public void failAi() {
    this.aiStatus = AiStatus.FAILED;
}

public void softDelete() {
    this.deletedAt = LocalDateTime.now();
}

public boolean isExpired(Duration retention) {
    return createdAt.plus(retention).isBefore(LocalDateTime.now());
}
```

### AC
- Given PENDING · When `completeAi(..., ["웹","AI"], "gemini")` · Then COMPLETED
- Given 사전 밖 태그 · Then INTERNAL_ERROR + 로그 마커 (프롬프트 검증)
- Given 4개 태그 · Then INTERNAL_ERROR

### DoD
- [ ] 5개 메서드
- [ ] 단위 테스트

### SP: 1d

---

## [Story 1-3] Repository + `ErrorCode.DSC001~004`

### 설명
```java
public interface DiscoveryItemRepository extends JpaRepository<DiscoveryItem, Long> {
    boolean existsBySourceTypeAndSourceId(SourceType type, String id);

    Page<DiscoveryItem> findBySourceCategoryAndAiStatus(SourceCategory category,
        AiStatus status, Pageable pageable);

    @Query("SELECT d FROM DiscoveryItem d WHERE d.aiStatus = 'PENDING' ORDER BY d.collectedAt")
    List<DiscoveryItem> findPending(Pageable pageable);

    // 인기 태그: 최근 N일 태그 발생 빈도
    @Query(value = """
        SELECT tag, COUNT(*) as cnt
        FROM (
          SELECT JSON_UNQUOTE(JSON_EXTRACT(ai_tags, CONCAT('$[', numbers.n, ']'))) AS tag
          FROM discovery_item
          CROSS JOIN (SELECT 0 AS n UNION SELECT 1 UNION SELECT 2) AS numbers
          WHERE ai_status = 'COMPLETED'
            AND published_at > :since
            AND deleted_at IS NULL
        ) t
        WHERE tag IS NOT NULL
        GROUP BY tag
        ORDER BY cnt DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findTrendingTags(@Param("since") LocalDateTime since,
        @Param("limit") int limit);
}
```

- ErrorCode:
  - `DSC001` DISCOVERY_ITEM_NOT_FOUND (404)
  - `DSC002` DISCOVERY_SOURCE_RATE_LIMITED (503)
  - `DSC003` DISCOVERY_AI_FAILED (500)
  - `DSC004` DISCOVERY_INVALID_SOURCE (400)

### DoD
- [ ] Repository
- [ ] ErrorCode 등록
- [ ] `@DataJpaTest`

### SP: 1d

---

# [Epic 2] 6개 Collector + `SignalSummaryPort` + `GeminiSignalSummaryAdapter` + `StaticSignalSummaryAdapter`

## 포함 Story
- Story 2-1: `DiscoverySignalCollectorPort` + 6개 Collector 어댑터 (Rome library 활용)
- Story 2-2: `SignalSummaryPort` + `StaticSignalSummaryAdapter` (Fallback 우선)
- Story 2-3: `GeminiSignalSummaryAdapter` (프롬프트 · JSON 파싱)

---

## [Story 2-1] Port + 6 Collector

### 설명
- `build.gradle.kts` 추가: `implementation("com.rometools:rome:2.1.0")`
- 각 Collector 어댑터는 RSS URL을 파싱 · `SignalDto` 리스트 반환

```java
public interface DiscoverySignalCollectorPort {
    SourceType sourceType();
    List<SignalDto> collect(int maxItems);
}

public record SignalDto(
    SourceType sourceType,
    SourceCategory sourceCategory,
    String sourceId,
    String title,
    String url,
    String author,
    Integer originalScore,
    LocalDateTime publishedAt,
    String descriptionSnippet   // AI 요약에 활용 · 저장 X
) {}

@Component
@RequiredArgsConstructor
@Slf4j
public class GeekNewsCollector implements DiscoverySignalCollectorPort {
    private static final String RSS_URL = "https://news.hada.io/rss/news";

    @Override
    public SourceType sourceType() { return SourceType.GEEKNEWS; }

    @Override
    public List<SignalDto> collect(int maxItems) {
        try (XmlReader reader = new XmlReader(new URL(RSS_URL))) {
            SyndFeed feed = new SyndFeedInput().build(reader);
            return feed.getEntries().stream()
                .limit(maxItems)
                .map(this::toSignal)
                .toList();
        } catch (Exception e) {
            log.error("DISCOVERY_COLLECT_FAILED source={} reason={}", sourceType(), e.getMessage());
            throw new BusinessException(ErrorCode.DSC002);
        }
    }

    private SignalDto toSignal(SyndEntry entry) {
        return new SignalDto(
            sourceType(), SourceCategory.NEWS,
            entry.getUri(),   // GUID
            entry.getTitle(),
            entry.getLink(),
            entry.getAuthor(),
            null,   // 원본 스코어는 파싱 성공 시 별도 추출
            entry.getPublishedDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
            Optional.ofNullable(entry.getDescription())
                    .map(SyndContent::getValue).orElse(null)
        );
    }
}

// 5개 대기업 블로그 Collector도 동일 패턴 · sourceType과 RSS_URL만 다름
// NaverD2Collector: SourceType.NAVER_D2 · "https://d2.naver.com/d2rss"
// KakaoTechCollector: SourceType.KAKAO_TECH · "https://tech.kakao.com/atom"
// TossTechCollector: SourceType.TOSS_TECH · "https://toss.tech/rss.xml"
// WoowaTechCollector: SourceType.WOOWA_TECH · "https://techblog.woowahan.com/feed"
// LineEngCollector: SourceType.LINE_ENG · "https://engineering.linecorp.com/ko/atom.xml"
```

### AC
- Given RSS URL 접근 성공 · Then maxItems 이하 반환
- Given 접근 실패 · Then DSC002 + 로그 마커
- Given 같은 GUID · Then 중복 저장 방지 (Story 1-3)

### DoD
- [ ] Port + 6개 Adapter
- [ ] 각 Collector 단위 테스트 (Mock RSS)

### SP: 2d

---

## [Story 2-2] SignalSummaryPort + StaticSignalSummaryAdapter

### 설명
```java
public interface SignalSummaryPort {
    SummaryDto summarize(String title, String description);
    String providerName();
}

public record SummaryDto(String summaryKo, List<String> tags, String provider) {}

@Component
@ConditionalOnProperty(name = "c1oudmall.discovery.summary.provider",
                        havingValue = "static", matchIfMissing = false)
public class StaticSignalSummaryAdapter implements SignalSummaryPort {

    @Override
    public SummaryDto summarize(String title, String description) {
        String summary = title.length() > 200 ? title.substring(0, 200) : title;
        return new SummaryDto(summary + ".", List.of(), "static");
    }

    @Override
    public String providerName() { return "static"; }
}
```

### AC
- Given static provider 활성 · When `summarize` · Then title 앞 200자 · 태그 없음

### DoD
- [ ] Port · Static Adapter
- [ ] `@ConditionalOnProperty` 검증

### SP: 0.5d

---

## [Story 2-3] GeminiSignalSummaryAdapter

### 설명
```java
@Component
@ConditionalOnProperty(name = "c1oudmall.discovery.summary.provider",
                        havingValue = "gemini", matchIfMissing = true)
@Slf4j
public class GeminiSignalSummaryAdapter implements SignalSummaryPort {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    private static final String PROMPT_TEMPLATE = """
        당신은 c1oud-mall Discovery 시스템의 요약 도우미입니다.
        아래 한국 IT 기사의 3줄 요약과 태그 목록을 JSON으로 응답하세요.

        제목: {title}
        본문/설명: {description}

        응답 스키마 (JSON):
        {
          "summary": "3줄 요약 (한국어 · 각 줄 마침표로 종료 · 총 200자 이내)",
          "tags": ["웹", "AI"] (아래 사전에서만 선택, 최대 3개)
        }

        태그 사전: ["웹","모바일앱","SaaS","AI","데이터","개발도구",
                    "인프라·DevOps","생산성","커뮤니티","핀테크","커머스",
                    "게임","미디어","교육","보안"]

        JSON만 응답하세요. 다른 설명 X.
        """;

    public GeminiSignalSummaryAdapter(
            @Value("${c1oudmall.gemini.api-key}") String apiKey,
            @Value("${c1oudmall.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            ObjectMapper mapper) {
        this.apiKey = apiKey;
        this.objectMapper = mapper;
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .build();
    }

    @Override
    public SummaryDto summarize(String title, String description) {
        try {
            String prompt = PROMPT_TEMPLATE
                .replace("{title}", title)
                .replace("{description}", Optional.ofNullable(description).orElse(""));

            var response = restClient.post()
                .uri("/v1beta/models/gemini-2.0-flash:generateContent?key={key}", apiKey)
                .contentType(APPLICATION_JSON)
                .body(Map.of("contents", List.of(
                    Map.of("parts", List.of(Map.of("text", prompt)))
                )))
                .retrieve()
                .body(Map.class);

            String text = extractText(response);
            GeminiSummaryResponse parsed = objectMapper.readValue(text, GeminiSummaryResponse.class);
            return new SummaryDto(parsed.summary(),
                                   List.copyOf(parsed.tags()),
                                   "gemini");
        } catch (Exception e) {
            log.error("DISCOVERY_AI_FAILED provider=gemini reason={}", e.getMessage());
            throw new BusinessException(ErrorCode.DSC003);
        }
    }

    @Override
    public String providerName() { return "gemini"; }

    private record GeminiSummaryResponse(String summary, List<String> tags) {}
}
```

### AC
- Given 정상 응답 · When `summarize` · Then SummaryDto · tags 사전 포함
- Given 429 (Rate Limit) or 실패 · Then DSC003

### DoD
- [ ] Adapter
- [ ] WireMock 통합 테스트

### SP: 1.5d

---

# [Epic 3] `DiscoveryService` + `CollectScheduler` + `use-for-project`

## 포함 Story
- Story 3-1: `DiscoveryService.listItems · getItem · trendingTags`
- Story 3-2: `DiscoveryCollectScheduler` (배치 · 순차 수집 · AI 요약 · Fallback)
- Story 3-3: `DiscoveryService.useForProject` (Project 등록 초기 데이터)

---

## [Story 3-1] listItems · getItem · trendingTags

### 설명
```java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DiscoveryService {
    private final DiscoveryItemRepository repository;

    public Page<DiscoveryItemResponse> listItems(DiscoveryListQuery query) {
        Pageable pageable = PageRequest.of(query.page(), query.size(),
            query.sort() == Sort.LATEST ? Sort.by("publishedAt").descending()
                                          : Sort.by("originalScore").descending().and(Sort.by("publishedAt").descending()));
        return repository.findBySourceCategoryAndAiStatus(query.category(), AiStatus.COMPLETED, pageable)
            .map(DiscoveryItemResponse::from);
    }

    public DiscoveryItemResponse getItem(Long id) {
        DiscoveryItem item = repository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.DSC001));
        return DiscoveryItemResponse.from(item);
    }

    public List<TagCountResponse> trendingTags(int limit) {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        return repository.findTrendingTags(since, limit).stream()
            .map(row -> new TagCountResponse((String) row[0], ((Number) row[1]).longValue()))
            .toList();
    }
}
```

### AC
- Given COMPLETED item 여러 · When `listItems(category=NEWS)` · Then NEWS만 페이지
- Given 태그 발생 이력 · When `trendingTags(10)` · Then 상위 10개

### DoD
- [ ] Service 메서드 3개
- [ ] `@WebMvcTest` 통합

### SP: 1d

---

## [Story 3-2] `DiscoveryCollectScheduler`

### 설명
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class DiscoveryCollectScheduler {
    private final List<DiscoverySignalCollectorPort> collectors;
    private final DiscoveryItemRepository repository;
    private final SignalSummaryPort primarySummary;   // Gemini or Static
    private final StaticSignalSummaryAdapter fallback;   // 항상 주입
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "0 0 3 * * *")
    public void runDaily() {
        long start = System.currentTimeMillis();
        int totalCollected = 0;

        // 1단계: 수집
        for (DiscoverySignalCollectorPort c : collectors) {
            try {
                List<SignalDto> signals = c.collect(15);
                for (SignalDto s : signals) {
                    if (repository.existsBySourceTypeAndSourceId(s.sourceType(), s.sourceId())) continue;
                    repository.save(DiscoveryItem.createPending(s));
                    totalCollected++;
                }
                meterRegistry.counter("discovery.collect.total", "source", c.sourceType().name(), "result", "success").increment();
                Thread.sleep(1000);   // 예의
            } catch (Exception e) {
                log.error("DISCOVERY_COLLECT_FAILED source={}", c.sourceType(), e);
                meterRegistry.counter("discovery.collect.total", "source", c.sourceType().name(), "result", "fail").increment();
            }
        }

        // 2단계: AI 요약
        List<DiscoveryItem> pending = repository.findPending(PageRequest.of(0, 150));
        for (DiscoveryItem item : pending) {
            trySummarize(item);
        }

        long duration = System.currentTimeMillis() - start;
        meterRegistry.timer("discovery.batch.duration_seconds").record(duration, MILLISECONDS);
        log.info("DISCOVERY_BATCH_DONE collected={} pending={} duration={}ms", totalCollected, pending.size(), duration);
    }

    private void trySummarize(DiscoveryItem item) {
        try {
            SummaryDto s = primarySummary.summarize(item.getTitle(), null);
            item.completeAi(s.summaryKo(), s.tags(), s.provider());
            meterRegistry.counter("discovery.ai.total", "provider", s.provider(), "result", "success").increment();
        } catch (Exception e) {
            log.warn("DISCOVERY_AI_FALLBACK itemId={} reason={}", item.getId(), e.getMessage());
            meterRegistry.counter("discovery.ai.total", "provider", primarySummary.providerName(), "result", "fail").increment();
            try {
                SummaryDto s = fallback.summarize(item.getTitle(), null);
                item.completeAi(s.summaryKo(), s.tags(), s.provider());
                meterRegistry.counter("discovery.ai.total", "provider", "static", "result", "success").increment();
            } catch (Exception e2) {
                log.error("DISCOVERY_AI_FAILED itemId={}", item.getId(), e2);
                item.failAi();
            }
        }
    }
}
```

### AC
- Given 6개 소스 정상 · When 배치 · Then 60~120건 저장 · Gemini 성공률 90%+
- Given Gemini 실패 · Then Static Fallback · COMPLETED
- Given 다 실패 · Then FAILED

### DoD
- [ ] 스케줄러 · Fallback 로직
- [ ] 통합 테스트 (WireMock)

### SP: 1.5d

---

## [Story 3-3] `useForProject`

### 설명
```java
@Transactional(readOnly = true)
public UseForProjectResponse useForProject(Long userId, Long itemId) {
    DiscoveryItem item = repository.findById(itemId)
        .orElseThrow(() -> new BusinessException(ErrorCode.DSC001));

    ProjectDraftInitData init = new ProjectDraftInitData(
        item.getTitle(),
        item.getAiSummaryKo(),
        item.getAiTags(),
        item.getUrl()   // 원본 소스 링크 · 프로젝트 등록 시 참고
    );

    String draftId = UUID.randomUUID().toString();
    // 세션 or Redis 저장 (초기: 세션 저장 · v0.0.5+ Redis)
    // ...

    return new UseForProjectResponse("/projects/new?draft=" + draftId, init);
}
```

### AC
- Given item 존재 · Then redirect + init data
- *(예외)* item 없음 · Then DSC001

### DoD
- [ ] Service · Response
- [ ] 통합 테스트

### SP: 0.5d

---

# [Epic 4] REST 5개 + `CleanupScheduler` + 관측 + ADR

## 포함 Story
- Story 4-1: `DiscoveryController` REST 5개
- Story 4-2: `DiscoveryCleanupScheduler` (30일 Soft Delete · 60일 실 삭제)
- Story 4-3: ADR + `backend-boundary/error-codes.md` DSC001~004

---

## [Story 4-1] REST 5개

### 엔드포인트
| Method | Path | 인증 |
|---|---|---|
| GET | `/api/v1/discovery/items` | 공개 |
| GET | `/api/v1/discovery/items/{id}` | 공개 |
| GET | `/api/v1/discovery/tags/trending` | 공개 |
| POST | `/api/v1/discovery/items/{id}/use-for-project` | JWT |
| POST | `/api/v1/admin/discovery/collect` | 관리자 |

### DoD
- [ ] Controller
- [ ] `@WebMvcTest`

### SP: 1d

---

## [Story 4-2] CleanupScheduler

### 설명
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class DiscoveryCleanupScheduler {
    private final DiscoveryItemRepository repository;

    @Scheduled(cron = "0 30 4 * * *")   -- Media(4시)와 겹치나 다른 리소스
    public void runDaily() {
        LocalDateTime softThreshold = LocalDateTime.now().minusDays(30);
        List<DiscoveryItem> oldItems = repository.findByCreatedAtBeforeAndDeletedAtIsNull(softThreshold);
        oldItems.forEach(DiscoveryItem::softDelete);

        LocalDateTime hardThreshold = LocalDateTime.now().minusDays(60);
        int hardDeleted = repository.deleteByDeletedAtBefore(hardThreshold);
        log.info("DISCOVERY_CLEANUP_DONE soft={} hard={}", oldItems.size(), hardDeleted);
    }
}
```

### DoD
- [ ] 스케줄러
- [ ] 통합 테스트

### SP: 0.5d

---

## [Story 4-3] ADR

- ADR 3건:
  - `028-discovery-korean-rss-sources.md`
  - `029-discovery-gemini-static-dual-adapter.md`
  - `030-discovery-soft-delete-cleanup.md`
- `backend-boundary/error-codes.md` DSC001~004

### SP: 0.5d

---

## 요약
| Epic | Story | SP |
|---|---|---|
| Epic 1 | 3 | 3.0 |
| Epic 2 | 3 | 4.0 |
| Epic 3 | 3 | 3.0 |
| Epic 4 | 3 | 2.0 |
| **합계** | **12** | **12.0 SP** |

## 완결 → 후속 참조
- Project (Product 6): `use-for-project` 흐름 · 사전 채움 데이터
- GitHub (Product 12): 나중에 Discovery 소스에 GitHub Trending 추가 시 재사용
- Notification (v0.0.5+): 관심 태그 신호 발생 시 알림
