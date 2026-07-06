# Issue: Discovery Signal Collector — 한국 application 소프트웨어 인기 데이터 수집 (RSS + Gemini)

## 배경

> **사용자 지시 (Round 6~7)**: "웹 크롤링·API·MCP·AI 기반으로 프로젝트에 좋을만한 소프트웨어 소식·문제·problem을 collecting해서 모아서 분석하는 공간 · **한국 시장 한정** · **application 소프트웨어 관련 데이터 중 인기 데이터만** · Gemini 기반 · 처음엔 크지 않게"

- 크라우드 펀딩 흐름(이슈 #08 Project · #10 Pledge)에서 "무엇을 만들면 좋을까?"·"이런 문제가 있어" 시그널이 필요
- 초기 사용자(대학생 개발자)의 프로젝트 아이디어 발굴·검증 도구로 작동
- **한국 시장에 특화** → 긱뉴스(GeekNews) + 대기업 기술 블로그 5개 = 6개 RSS로 시작
- Gemini 2.0 Flash로 한국어 3줄 요약 + 태그 자동 분류
- 이슈 #09 AI Suggestion에서 확정된 Port/Adapter 이중 어댑터(Gemini + Static) 재사용
- 이슈 #08 프로젝트 등록 "이거로 프로젝트 만들기" CTA로 결합 → 크라우드 펀딩 UX 자연스러운 강화

## 조사 결과 — 한국 소스 · Gemini · 이슈 #09 재사용 가능성

### 한국 소스 6개 (모두 RSS · robots 안전 · 크롤링 없음)

| 소스 | source_type | RSS URL 후보 | category | 데이터 성격 |
|---|---|---|---|---|
| **긱뉴스 (GeekNews)** | `GEEKNEWS` | `news.hada.io/rss/news` | `NEWS` | 한국판 Hacker News · IT/개발 인기 링크 + 짧은 요약 |
| 네이버 D2 | `NAVER_D2` | `d2.naver.com/d2rss` | `CORPORATE_BLOG` | 대기업 심층 아티클 |
| 카카오 기술 블로그 | `KAKAO_TECH` | `tech.kakao.com/atom` | `CORPORATE_BLOG` | 대기업 심층 아티클 |
| 토스 기술 블로그 | `TOSS_TECH` | `toss.tech/rss.xml` | `CORPORATE_BLOG` | 핀테크 개발 실무 |
| 우아한형제들 기술 블로그 | `WOOWA_TECH` | `techblog.woowahan.com/feed` | `CORPORATE_BLOG` | 이커머스·백엔드 실무 |
| 라인 엔지니어링 | `LINE_ENG` | `engineering.linecorp.com/ko/atom.xml` | `CORPORATE_BLOG` | 글로벌 서비스 개발 실무 |

> RSS URL 정확성은 구현 시 검증 (v0.0.3 착수 시점) · 변동 있으면 조정

### Gemini 2.0 Flash 무료 tier

| 항목 | Free tier |
|---|---|
| 요청 상한 | **15,000 / 일** · 60 / 분 |
| 컨텍스트 | 1M 토큰 |
| Structured Output | 지원 (`response_schema`) |
| 비용 | 무료 (한도 내) |
| 총 요청량 예상 | 60~120 req/일 (6 소스 × 10~20건) — **한도 0.8% 사용** |

### 이슈 #09 이중 어댑터 (재사용 가능)

이미 확정된 규범:
- `SuggestionPort` 인터페이스 (application layer)
- `StaticSuggestionAdapter` · `LlmSuggestionAdapter` 이중 구현
- `@ConditionalOnProperty` 스위칭
- Fallback = "빈 목록 + 안내 플래그" 원칙

본 이슈는 유사 패턴을 `SignalSummaryPort`로 확장:
- `GeminiSignalSummaryAdapter` (LLM)
- `StaticSignalSummaryAdapter` (Fallback · 원본 제목을 요약으로)

### robots.txt · 저작권 사전 조사

- RSS 표준은 **명시적으로 배포 목적으로 제공**되는 데이터 (robots 문제 없음)
- 하지만 재분배 시 **원문 링크 · 출처 표시**는 저작권상 필수 (뉴스 · 블로그 공통)
- User-Agent 명시 필수 (`c1oud-mall Discovery Bot/1.0 (+https://c1oud-mall.dev)`)

## 옵션 비교

### 갈림길 1: 수집 목표

**Option A — A(아이디어) + B(문제) 조합** `(채택)`
- 사용자 지시("소프트웨어 소식·문제·problem")에 정확히 부합
- application 소프트웨어에 초점

**Option B — 기술 트렌드 중심**
- 거부 이유: Product Hunt 급 벤치마크 부재 · Octoverse 등 글로벌 지표는 스코프 밖

**Option C — 사용자 활동만 (내부 로그)**
- 거부 이유: 초기 사용자 없어 데이터 원천 부재

### 갈림길 2: 초기 소스 개수

**Option A — 6개 RSS (긱뉴스 + 대기업 블로그 5개)** `(채택)`
- 모두 RSS · 크롤링 없음 · 인증 없음 · 안전
- 뉴스 + 심층 아티클 다양성 확보
- Rate 부담 극소

**Option B — 긱뉴스 1개만**
- 안전하지만 데이터 다양성 부족

**Option C — 6개 + 디스콰이엇/OKKY 크롤링**
- 거부 이유: 크롤링 부담 · robots 확인 필요 · 초기 스코프 오버

### 갈림길 3: 수집 방식

**Option A — 배치 스케줄러 (매일 새벽 3시)** `(채택)`
- Spring Scheduler · 신입 스코프 딱
- Rate 관리 명확

**Option B — 실시간 조회 (사용자 요청 시 API 호출)**
- 거부 이유: Rate limit 즉시 도달 · 캐싱 어려움

### 갈림길 4: AI 활용 시점

**Option A — 초기 포함 (Gemini 요약·분류)** `(채택)`
- 이력서 스토리 파괴력 · Port/Adapter 규범 이슈 #09 재사용
- 무료 tier 안 · 스코프 관리 가능

**Option B — v0.0.4+ 이월**
- 거부 이유: Discover 카드 UX 약해짐 · 이슈 두 번 뽑기 오버

### 갈림길 5: 노출 위치

**Option A — Discover 페이지 단독 (헤더 네비 신설)** `(채택)`
- 명확한 진입점 · "이거로 프로젝트 만들기" CTA로 이슈 #08과 결합

**Option B — 프로젝트 등록 사이드바 (아이디어 힌트만)**
- 거부 이유: 사용 시나리오 좁음

### 갈림길 6: 시장

**Option A — 한국 시장 한정** `(채택 · 사용자 지시)`
- application 소프트웨어 · 한국어 카드 · 한국어 요약·태그

**Option B — 글로벌**
- 거부 이유: 사용자가 명시 반대

### 갈림길 7: AI 제공자

**Option A — Gemini 2.0 Flash + Static Fallback (이슈 #09 재사용)** `(채택)`
- 무료 tier · 이슈 #09 어댑터 규범 재사용
- 이력서: "재사용 가능한 LLM 어댑터 규범"

**Option B — OpenAI GPT-4o-mini**
- 거부 이유: 유료 · 학생 비용 부담

**Option C — Ollama 로컬**
- 거부 이유: 인프라 오버킬 · GPU 부담

## 선택: Option A (모든 갈림길)

## 부속 결정

### 도메인 컨텍스트
- 신규: `nbc.c1oud_mall.discovery.*` (4레이어)
- 4개 서브패키지:
  - `presentation` — `DiscoveryController`
  - `application` — `DiscoveryService` · `SignalSummaryPort` · Command/Query
  - `domain` — `DiscoveryItem` · `DiscoverySourceType` · `SourceCategory` · `AiStatus`
  - `infrastructure` — `DiscoverySignalCollectorPort` 구현체 6개 · Gemini/Static 어댑터 · Repository

### 엔티티 · 스키마

```sql
CREATE TABLE discovery_item (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  source_type       VARCHAR(30)  NOT NULL,           -- GEEKNEWS · NAVER_D2 · KAKAO_TECH · TOSS_TECH · WOOWA_TECH · LINE_ENG
  source_category   VARCHAR(30)  NOT NULL,           -- NEWS · CORPORATE_BLOG
  source_id         VARCHAR(200) NOT NULL,           -- 원본 GUID (RSS <guid> 또는 URL hash)
  title             VARCHAR(500) NOT NULL,
  url               VARCHAR(500) NOT NULL,
  author            VARCHAR(100) NULL,
  original_score    INT          NULL,               -- 긱뉴스만 있음 · 블로그는 NULL
  ai_summary_ko     VARCHAR(500) NULL,               -- Gemini 3줄 요약 (한국어)
  ai_tags           JSON         NULL,               -- ["웹", "AI", ...] 최대 3개
  ai_status         VARCHAR(20)  NOT NULL,           -- PENDING · COMPLETED · FAILED
  ai_provider       VARCHAR(20)  NULL,               -- gemini · static (fallback 발동 감사)
  published_at      TIMESTAMP    NOT NULL,           -- 원본 발행
  collected_at      TIMESTAMP    NOT NULL,
  deleted_at        TIMESTAMP    NULL,               -- 30일 후 Soft Delete
  PRIMARY KEY (id),
  UNIQUE KEY uk_discovery_source (source_type, source_id),
  KEY idx_discovery_category_published (source_category, published_at DESC),
  KEY idx_discovery_source_published (source_type, published_at DESC),
  KEY idx_discovery_ai_status (ai_status),
  KEY idx_discovery_deleted (deleted_at)
);
```

### 도메인 모델

```java
// discovery.domain.DiscoveryItem (Aggregate root)
@Entity
@Table(name = "discovery_item")
@SQLDelete(sql = "UPDATE discovery_item SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class DiscoveryItem extends BaseEntity {
    @Id @GeneratedValue Long id;
    @Enumerated(STRING) DiscoverySourceType sourceType;
    @Enumerated(STRING) SourceCategory sourceCategory;
    String sourceId;
    String title;
    String url;
    String author;
    Integer originalScore;    // 긱뉴스만 사용
    String aiSummaryKo;
    @Column(columnDefinition = "JSON") String aiTags;   // JSON 문자열
    @Enumerated(STRING) AiStatus aiStatus;
    String aiProvider;
    LocalDateTime publishedAt;
    LocalDateTime collectedAt;
    LocalDateTime deletedAt;

    public static DiscoveryItem createPending(RawSignal raw) {
        DiscoveryItem item = new DiscoveryItem();
        item.sourceType = raw.sourceType();
        item.sourceCategory = raw.sourceType().category();
        item.sourceId = raw.sourceId();
        item.title = raw.title();
        item.url = raw.url();
        item.author = raw.author();
        item.originalScore = raw.score();
        item.aiStatus = AiStatus.PENDING;
        item.publishedAt = raw.publishedAt();
        item.collectedAt = LocalDateTime.now();
        return item;
    }

    public void applySummary(String summaryKo, List<String> tags, String provider) {
        this.aiSummaryKo = summaryKo;
        this.aiTags = toJson(tags);
        this.aiStatus = AiStatus.COMPLETED;
        this.aiProvider = provider;
    }

    public void markAiFailed() {
        this.aiStatus = AiStatus.FAILED;
    }
}

// discovery.domain.DiscoverySourceType
public enum DiscoverySourceType {
    GEEKNEWS(SourceCategory.NEWS),
    NAVER_D2(SourceCategory.CORPORATE_BLOG),
    KAKAO_TECH(SourceCategory.CORPORATE_BLOG),
    TOSS_TECH(SourceCategory.CORPORATE_BLOG),
    WOOWA_TECH(SourceCategory.CORPORATE_BLOG),
    LINE_ENG(SourceCategory.CORPORATE_BLOG);

    private final SourceCategory category;
    // ...
}

public enum SourceCategory { NEWS, CORPORATE_BLOG }
public enum AiStatus { PENDING, COMPLETED, FAILED }
```

### 소스별 수집기 (Port/Adapter)

```java
// discovery.application.port.DiscoverySignalCollectorPort
public interface DiscoverySignalCollectorPort {
    DiscoverySourceType getSourceType();
    List<RawSignal> collect(int limit);   // limit = 상위 N개 (10~20)
}

// discovery.application.dto.RawSignal (record)
public record RawSignal(
    DiscoverySourceType sourceType,
    String sourceId,
    String title,
    String url,
    String author,
    Integer score,      // 긱뉴스만 값 있음
    LocalDateTime publishedAt,
    String rawDescription   // Gemini에 넘겨줄 짧은 설명 (본문 저장 X)
) {}

// discovery.infrastructure.collector.GeekNewsCollector
@Component
public class GeekNewsCollector implements DiscoverySignalCollectorPort {
    private static final String RSS_URL = "https://news.hada.io/rss/news";
    private final RssParser rssParser;

    @Override
    public DiscoverySourceType getSourceType() { return GEEKNEWS; }

    @Override
    public List<RawSignal> collect(int limit) {
        List<RssItem> items = rssParser.fetch(RSS_URL, USER_AGENT);
        return items.stream()
            .limit(limit)
            .map(this::toRawSignal)
            .toList();
    }
    // ...
}

// 나머지 5개도 동일 패턴 (NaverD2Collector · KakaoTechCollector 등)
```

### AI 어댑터 (이슈 #09 재사용 · SignalSummaryPort)

```java
// discovery.application.port.SignalSummaryPort
public interface SignalSummaryPort {
    SignalSummaryResult summarize(RawSignal raw);
}

// discovery.application.dto.SignalSummaryResult
public record SignalSummaryResult(
    String summaryKo,          // 3줄 요약 (한국어)
    List<String> tags,         // 태그 배열 (최대 3개)
    String provider            // "gemini" or "static"
) {}

// discovery.infrastructure.summary.GeminiSignalSummaryAdapter
@Component
@ConditionalOnProperty(name = "c1oudmall.discovery.provider", havingValue = "llm", matchIfMissing = false)
@Primary
public class GeminiSignalSummaryAdapter implements SignalSummaryPort {
    private final GeminiClient geminiClient;
    // 프롬프트 · Structured Output · JSON 파싱 · Fallback 시 static 위임
}

// discovery.infrastructure.summary.StaticSignalSummaryAdapter
@Component
@ConditionalOnProperty(name = "c1oudmall.discovery.provider", havingValue = "static", matchIfMissing = true)
public class StaticSignalSummaryAdapter implements SignalSummaryPort {
    @Override
    public SignalSummaryResult summarize(RawSignal raw) {
        // 원본 제목을 요약으로 · 태그 없음
        return new SignalSummaryResult(raw.title(), List.of(), "static");
    }
}
```

### Gemini 프롬프트 (한국어 · Structured Output)

```
당신은 c1oud-mall Discovery 시스템의 요약 도우미입니다.
아래 한국 IT 기사의 3줄 요약과 태그 목록을 JSON으로 응답하세요.

제목: {title}
본문/설명: {rawDescription}
출처: {sourceType}

응답 스키마 (JSON):
{
  "summary": "3줄 요약 (한국어 · 각 줄 마침표로 종료 · 총 200자 이내)",
  "tags": ["웹", "AI"]  (아래 사전에서만 선택, 최대 3개)
}

태그 사전:
["웹", "모바일앱", "SaaS", "AI", "데이터", "개발도구",
 "인프라·DevOps", "생산성", "커뮤니티", "핀테크", "커머스",
 "게임", "미디어", "교육", "보안"]

주의사항:
- application 소프트웨어와 관련 없는 내용이면 tags를 빈 배열로 응답
- 태그 사전에 없는 태그는 사용하지 마세요
- summary는 반드시 3줄 · 각 줄 마침표
```

### 배치 스케줄러

```java
// discovery.infrastructure.DiscoveryCollectionScheduler
@Component
@RequiredArgsConstructor
public class DiscoveryCollectionScheduler {
    private final List<DiscoverySignalCollectorPort> collectors;
    private final DiscoveryService discoveryService;

    private static final int LIMIT_PER_SOURCE = 20;   // 각 소스 상위 20개
    private static final Duration REQUEST_INTERVAL = Duration.ofSeconds(1);   // 예의상 1초 대기

    @Scheduled(cron = "0 0 3 * * *")   // 매일 새벽 3시
    public void collectAll() {
        for (DiscoverySignalCollectorPort collector : collectors) {
            try {
                List<RawSignal> signals = collector.collect(LIMIT_PER_SOURCE);
                for (RawSignal signal : signals) {
                    discoveryService.upsertAndSummarize(signal);
                }
                Thread.sleep(REQUEST_INTERVAL.toMillis());
            } catch (Exception e) {
                log.error("DISCOVERY_COLLECT_FAILED source={}", collector.getSourceType(), e);
            }
        }
    }
}

// discovery.application.DiscoveryService
@Service
@RequiredArgsConstructor
@Transactional
public class DiscoveryService {
    private final DiscoveryItemRepository repository;
    private final SignalSummaryPort signalSummaryPort;

    public void upsertAndSummarize(RawSignal raw) {
        // 1. UNIQUE 검증 (source_type + source_id) — S+ 멱등
        if (repository.existsBySourceTypeAndSourceId(raw.sourceType(), raw.sourceId())) {
            return;   // 이미 수집됨 · silent skip
        }

        // 2. PENDING 저장
        DiscoveryItem item = DiscoveryItem.createPending(raw);
        repository.save(item);

        // 3. AI 요약 · 실패 시 Static fallback
        try {
            SignalSummaryResult result = signalSummaryPort.summarize(raw);
            item.applySummary(result.summaryKo(), result.tags(), result.provider());
        } catch (Exception e) {
            log.warn("DISCOVERY_AI_FAILED itemId={}, err={}", item.getId(), e.getMessage());
            item.markAiFailed();
        }
    }
}
```

### 30일 Soft Delete 배치

```java
@Scheduled(cron = "0 30 3 * * *")   // 매일 새벽 3시 30분 (수집 이후)
public void softDeleteOldItems() {
    LocalDateTime threshold = LocalDateTime.now().minusDays(30);
    int deleted = repository.softDeleteByCollectedAtBefore(threshold);
    log.info("DISCOVERY_SOFT_DELETE count={}", deleted);
}
```

### API 표면

| 메서드 | 경로 | 인증 | 용도 |
|---|---|---|---|
| GET | `/api/v1/discovery/items?category=&tag=&page=&size=&sort=` | 공개 | Discover 리스트 |
| GET | `/api/v1/discovery/items/{id}` | 공개 | 상세 (원본 링크 · 요약 · 태그) |
| GET | `/api/v1/discovery/tags/trending?limit=20` | 공개 | 인기 태그 워드클라우드 |
| POST | `/api/v1/discovery/items/{id}/use-for-project` | JWT | "이거로 프로젝트 만들기" — 이슈 #08 등록 페이지로 초기 데이터 반환 |
| POST | `/api/v1/admin/discovery/collect?source=` | 관리자 | 수동 트리거 (테스트) |

정렬 화이트리스트:
- `latest` — `published_at DESC` (기본)
- `score` — `original_score DESC` (긱뉴스만 유효 · 없으면 latest fallback)

### ErrorCode (신규)
- `DSC001` DISCOVERY_ITEM_NOT_FOUND (404)
- `DSC002` DISCOVERY_SOURCE_RATE_LIMITED (503)
- `DSC003` DISCOVERY_AI_FAILED (500 · 내부 로그만 · 사용자에겐 노출 X · Fallback 트리거)
- `DSC004` DISCOVERY_INVALID_SOURCE (400)
- `DSC005` DISCOVERY_INVALID_SORT (400)

### 정합성 · 멱등성

- `UNIQUE (source_type, source_id)` — 재수집 시 S+ 멱등 (idempotency §4)
- 배치 실행 중복 방지: v0.0.3 단일 인스턴스 전제 · v0.0.5+ ShedLock 도입 검토
- AI 실패는 부분 실패 · 나머지 계속 처리 (Failure Isolation)

### 최소 권한 · 저작권 · 프라이버시 (필수 준수)

1. **RSS 표준 준수**
   - robots.txt 확인 · RSS는 명시적 배포 목적이라 통과
   - User-Agent: `c1oud-mall Discovery Bot/1.0 (+https://c1oud-mall.dev)`
   - 요청 간 1초 대기 (예의)
2. **요약만 저장 · 본문 저장 X**
   - `title` · `url` · `author` · `ai_summary_ko` · `ai_tags`만 DB에 저장
   - 원문 전체는 저장하지 않음 · 저작권 준수
3. **원본 출처 표시** — Discover 카드 UI에 소스명 + 저자 + 원본 링크 필수
4. **30일 Soft Delete** — 오래된 데이터 자동 정리
5. **개인정보 X** — 저자명은 RSS `<author>` 필드만 (공개 정보)
6. **한국어 요약 → 한국어 태그** — 지역화 준수

### 관측

- `discovery.collect.total{source, result}` counter
- `discovery.ai.total{provider=gemini|static, result=success|fail}` counter
- `discovery.item.gauge{source}` — 저장된 item 수 (소스별)
- `discovery.batch.duration_seconds` histogram — 배치 처리 시간
- `discovery.rate_limit.remaining{source}` gauge — 소스별 rate 여유 (해당 시)

## 이관 산출물

- **BE-Story #15-1**: `discovery` 컨텍스트 신규 패키지 골격 + 4개 서브패키지
- **BE-Story #15-2**: `DiscoveryItem` 엔티티 · 3개 enum (`DiscoverySourceType` · `SourceCategory` · `AiStatus`) · Repository
- **BE-Story #15-3**: `DiscoveryItem` 도메인 메서드 (`createPending` · `applySummary` · `markAiFailed`) · Soft Delete 어노테이션
- **BE-Story #15-4**: `RssParser` 유틸 (ROME Library or 자체 XML 파서 · User-Agent 명시)
- **BE-Story #15-5**: `DiscoverySignalCollectorPort` 인터페이스 + 6개 어댑터 (GeekNews · NaverD2 · KakaoTech · TossTech · WoowaTech · LineEng)
- **BE-Story #15-6**: `SignalSummaryPort` 인터페이스 + `GeminiSignalSummaryAdapter` + `StaticSignalSummaryAdapter` (이슈 #09 재사용)
- **BE-Story #15-7**: `DiscoveryService.upsertAndSummarize` (UNIQUE 사전조회 + PENDING 저장 + AI 요약 · fallback)
- **BE-Story #15-8**: `DiscoveryCollectionScheduler` (@Scheduled cron · 소스별 순차 · 예의 대기)
- **BE-Story #15-9**: 30일 Soft Delete 배치 스케줄러
- **BE-Story #15-10**: `DiscoveryController` REST 5개 엔드포인트
- **BE-Story #15-11**: `ErrorCode.DSC001~005` 등록
- **BE-Story #15-12**: 통합 테스트 (6개 소스 mock RSS · Gemini mock · UNIQUE 재수집 · Fallback 트리거)
- **BE-Story #15-13**: 태그 트렌딩 쿼리 (JSON 필드 집계 · MySQL JSON 함수 or 배치 반정규화)
- **BE-Story #15-14**: "use-for-project" API — 이슈 #08 Project 등록 초기 데이터 반환 (제목 · 요약 · 태그 · 원본 링크)
- **BE-Story #15-15**: 관측 지표 등록 (`discovery.collect.*` · `discovery.ai.*` · `discovery.batch.*`)
- **FE-Story #15-1**: `src/features/discovery/DiscoverPage.tsx` (헤더 네비 신설 · 카드 리스트 · 필터 사이드바)
- **FE-Story #15-2**: `src/features/discovery/DiscoveryCard.tsx` (카드 UI · 소스 뱃지 · 요약 · 태그 · CTA)
- **FE-Story #15-3**: `src/features/discovery/TagCloud.tsx` (인기 태그 워드클라우드)
- **FE-Story #15-4**: "이거로 프로젝트 만들기" CTA → 이슈 #08 등록 페이지로 라우팅 + 초기 데이터 전달
- **Docs-Story #15-1**: `backend-boundary/error-codes.md` DSC001~005 매핑
- **Docs-Story #15-2**: RSS URL 리스트 · User-Agent 규범 문서 (`docs/discovery-sources.md`)
- **SDD 개정**: 향후 `product-discovery.md` 신규 (M3 진입 시)

## 관련 이슈 / 문서

- **재사용 원본**: [#09 AI Suggestion](./issue-09-reward-tier.md · 이름 유사하지만 이슈 09가 리워드로 재정의됨 · AI Suggestion 원본은 별도 참조) — Port/Adapter 이중 어댑터 규범
- **결합 대상**: [#08 Project 도메인](./issue-08-project-domain.md) — "이거로 프로젝트 만들기" CTA → 프로젝트 등록 초기 데이터
- **향후 확장 (v0.0.4+)**: [#14 GitHub Integration](./issue-14-github-integration.md) — GitHub Trending 통합 시 OAuth 재사용
- **잠재 확장 (v0.0.5+)**: 디스콰이엇 · OKKY (웹 크롤링 · robots 확인 후)
- **잠재 확장 (v0.1.0+ · 글로벌 시장)**: Hacker News · Reddit · Product Hunt
- **관련 규범**:
  - `.claude/rules/exception.md` (BusinessException + ErrorCode)
  - `.claude/rules/idempotency.md` §4 S+ 감지 등급 (UNIQUE + 사전조회)
  - `.claude/rules/consitency.md` §7 (외부 시스템 정합성)

## 디자인 참조

- **디자인 파일 없음** — 이번 이슈는 백엔드·데이터 파이프라인 중심 · FE UI는 후속 (v0.0.3+ FE Story 개별 진행)
- 참고: `C:\Users\user\Desktop\fe\펀딩 탐색.html` — 카드 UI 패턴은 이 페이지 스타일 재사용 (기본 grid · badge · maker 등)
- 헤더 네비 추가 위치: `펀딩 탐색.html` 헤더의 `nav` 요소에 `Discover` 링크 추가 (`둘러보기 · 인기 프로젝트 · 카테고리 · **Discover** · 프로젝트 올리기`)
