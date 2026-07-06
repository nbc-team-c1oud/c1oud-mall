# [Product 3] AI 제안 (AI Suggestion)

## Product Vision
> 사용자가 직업적 컨셉만 입력하면 AI가 축·주제 후보를 제안하여 학습 시작 마찰을 낮추되, AI 호출 실패가 서비스 전체 실패로 이어지지 않는 안전망(Static Fallback)을 제공한다.

## 배경 및 문제
- 현재 상황 (As-Is)
  - `nbc.c1oud_mall.aisuggestion.*` 컨텍스트는 미존재 — 신규 생성
  - LearningFacade / AxisTopic 도메인 자체는 존재하지만 수동 입력만 지원
- 발생하는 문제
  - 사용자가 처음 학습 트리를 만들 때 무엇부터 입력할지 감이 없음 → 이탈 발생
  - LLM 응답은 지연·형식 흔들림·quota 리스크 → 순진하게 도입 시 서비스 신뢰도 저하
- 왜 지금 해결해야 하는가
  - 초기 사용자 온보딩 마찰 해소 → 리텐션 방어
  - AI 도입 초반에 Port/Adapter 구조를 확정해야 이후 공급자 교체(Gemini ↔ OpenAI)나 Phase 3 RAG 도입에 유연

## 목표 (To-Be)
- Application Layer에 `AxisSuggestionPort`·`AxisTopicSuggestionPort` 인터페이스 도입
- `StaticAxisSuggestionAdapter`·`StaticAxisTopicSuggestionAdapter` — Fallback 경로 + 테스트 환경
- Gemini 2.5 Flash 기반 `LlmAxisSuggestionAdapter`·`LlmAxisTopicSuggestionAdapter`
- 기능 플래그 (`thirdtool.suggestion.provider=static|llm`)로 어댑터 스위칭
- ErrorCode 접두어 `AI001~AI099` 신설 (SUGGEST 관련) — CLAUDE.md §8

## 설계 결정 (Design Decisions)

- **Port/Adapter 이중 어댑터 (Static + LLM)**
  - 개발 초기 진입 쉬움, 장애 시 즉시 Static Fallback
- **Fallback은 "빈 목록 + 안내 플래그"** — 하드코딩 응답 X
  - 사용자의 수동 입력 경로 항상 열려있음
- **Concept 제안 제외** (v1 스코프)
  - 하드코딩 목록으로 충분
- **Gemini 2.5 Flash (v1)** — 저비용·저지연 우선
- **Structured Output (JSON Schema)** — 응답 형식 안정성 확보
- **Prompt는 리소스 파일** (`src/main/resources/prompts/*.txt`) — 코드 하드코딩 X

## 대안 검토 (Alternatives Considered)

### LLM 공급자
**Option A — OpenAI GPT-4o**
- 거부 이유: 비용 큼

**Option B (선택) — Gemini 2.5 Flash**
- 비용: quota 관리 필요
- 보상: 저지연·저비용·structured output 지원

**Option C — 자체 파인튜닝 모델**
- 거부 이유: 데이터·리소스 부족

### Fallback 정책
**Option A (선택) — 런타임 자동 fallback (5xx/timeout/파싱 실패 시 Static)**
- 비용: 두 어댑터 유지
- 보상: 장애 즉시 대응, 사용자 서비스 지속

**Option B — Circuit Breaker (Resilience4j)**
- 거부 이유: 초기 오버킬. 트래픽 늘면 재검토

**Option C — 수동 스위치**
- 거부 이유: 실시간 대응 불가

### Prompt 관리
**Option A (선택) — 리소스 파일 (`resources/prompts/`)** + Git 버전 관리
**Option B — DB + 관리자 UI**
- 거부 이유: 오버엔지니어링
**Option C — LangSmith 등 SaaS**
- 거부 이유: 외부 종속성 추가

## 전체 아키텍처 (High-Level Architecture)

### 컴포넌트 배치
```
presentation ──▶ application ──▶ domain ◀── infrastructure
Controller       AxisSuggestionService     (사용 안 함)   StaticAdapter
- POST suggest   AxisSuggestionPort ─┐                     LlmAdapter
                                    ├── @Primary            (Gemini)
                                    │   @ConditionalOnProperty
                                    │   provider=static|llm
                                    └── LlmAxisSuggestionAdapter
                                                            GeminiClient
                                                            PromptLoader
                                                            SuggestionResponseParser
```

### 핵심 플로우
**1. 축 제안 요청**
```
Client → SuggestionController.suggestAxis(concept, existingAxis, limit)
       → AxisSuggestionService.suggest
       → AxisSuggestionPort (구현체 선택)
         ├── (provider=llm) LlmAdapter → Gemini → parse → List<AxisSuggestion>
         │                                    ↓ (실패 시)
         │                                    └── Fallback to Static
         └── (provider=static) StaticAdapter → 빈 목록 or 힌트
       ← ApiResponse.success(response)
```

### Out-of-Process 의존
- **Gemini 2.5 Flash API**: REST · WebClient 호출 · `GeminiClient` 캡슐화
- **Google ADC (Application Default Credentials)**: 인증 (환경변수 또는 서비스 어카운트)
- (Prompt 파일은 in-process resource)

## 실패 모드 / 운영 관측 (Failure Modes & Observability)

### 실패 시나리오와 응답
| 시나리오 | ErrorCode | HTTP | 클라이언트 권장 동작 |
| --- | --- | --- | --- |
| LLM 요청 초과 (rate limit) | `AI001` | 429 | Fallback 응답(빈 목록) · 수동 입력 |
| LLM 5xx / timeout | `AI002` | 502→200 (fallback) | Static 응답 표시 · 안내 플래그 |
| LLM 응답 파싱 실패 | `AI003` | 200 (fallback) | Static 응답 표시 |
| 입력 검증 실패 (concept 빈값) | `C001` | 400 | 재입력 유도 |
| 인증 실패 | `C004` | 401 | 로그인 유도 |

### 로깅 정책
- **항상 기록**: `requestId`, `provider`(static/llm), `latency`, `outcome`(success/fallback/failure), tokenCount(추정)
- **debug**: LLM 프롬프트 · 원본 응답 (개인정보 제거 후)
- **절대 금지**: Gemini API key · 사용자 개인정보 원문

### 관측 지표
- `ai_suggest_total{type=axis|axistopic,provider=static|llm,outcome=success|fallback|failure}` — counter
- `ai_suggest_duration_seconds{provider}` — histogram — 응답 시간
- `ai_llm_error_total{errorCode}` — counter — 에러 코드별 카운트
- `ai_fallback_total` — counter — Fallback 발동 카운트

## 롤아웃 / 마이그레이션 (Rollout)

### 전제
- Gemini API 키·프로젝트 확보
- 초기 트래픽 낮음 → quota 걱정 없음
- `provider=static`으로 시작해 점진적으로 `llm` 전환

### Product 의존성
- 선행: LearningFacade / AxisTopic 도메인 (기존)
- 후행: Phase 3 RAG · Interactive Roadmap

### Epic·Story 의존성 그래프
```
Epic 7 (Port + Static) ──► Epic 9 (Axis 축 제안)
        │                       │
        │                       └─► Epic 11 (관측성)
        │
        └─► Epic 8 (Gemini 공통 인프라)
                │
                └─► Epic 10 (AxisTopic 제안)
```

### 환경별 설정 분기
| 항목 | dev (H2) | prod (RDS MySQL) |
| --- | --- | --- |
| `thirdtool.suggestion.provider` | `static` (기본) | `llm` (Gemini 키 확보 후) |
| Gemini API 키 | (개발용) | (운영용, Secrets Manager) |
| Prompt 파일 | classpath 리소스 | classpath 리소스 |
| 로그 레벨 | DEBUG | INFO |

## 성공 지표 (KPI)
| 지표 | 목표 값 | 측정 방법 |
| --- | --- | --- |
| AI 제안 응답 성공률 (LLM) | ≥ 95% | `ai_suggest_total{outcome=success}` / 전체 |
| Fallback 발동율 (초기 목표) | ≤ 5% | `ai_fallback_total` / 전체 |
| LLM 응답 시간 P95 | ≤ 3s | `ai_suggest_duration_seconds{provider=llm}` |
| 축 제안 → 실제 채택률 | ≥ 30% | (프로덕트 지표 · 사용자 클릭 로그) |

## Scope
**In Scope**:
- Axis 제안 (Epic 9) · AxisTopic 제안 (Epic 10)
- Static Adapter + LLM Adapter 이중 구현
- 기능 플래그 스위칭 (`provider`)
- Gemini 2.5 Flash 클라이언트 인프라
- ErrorCode `AI001~AI099` 등록
- 관측성 (metrics + fallback 절차)

**Out of Scope**:
- Concept 제안 — 하드코딩 목록으로 충분
- 캐싱(Redis) · Rate Limiting — Epic 11 또는 Phase 2
- Multi-provider 라운드로빈 — 오버엔지니어링
- RAG (검색 증강 생성) — Phase 3
- Prompt A/B 테스트 인프라 — 향후

## 대상 사용자
- **신규 사용자**: 학습 시작 마찰 낮춤 · 축·주제 힌트 획득
- **기존 사용자**: 새 학습 트리 생성 시 아이디어 힌트
- **운영자**: Fallback 발동율·응답 성공률 관측 가능
- **개발/QA**: Port 기반 테스트 (Static Adapter로 결정적 검증)

## 연결된 Epic 목록
- [ ] Epic 7: AI 제안 기반 구조 구축 (Port/Adapter · Static · ErrorCode)
- [ ] Epic 8: Gemini 2.5 Flash 연동 공통 인프라 (GeminiClient · PromptLoader · ResponseParser)
- [ ] Epic 9: 축 제안 기능 (Axis suggestion E2E)
- [ ] Epic 10: AxisTopic 제안 기능 (하위 주제 제안 E2E)
- [ ] Epic 11: 관측성 및 장애 대응 안정화

## 관련 문서
- 도메인 참조: LearningFacade · AxisTopic (기존)
- 관련 ADR (예정): "AI Suggestion Port/Adapter", "AI Suggestion Fallback Strategy", "Gemini Client 인증·quota", "Prompt Resource 관리"
- 위치: `workflows/living-docs/Ai-adr/`
- CLAUDE.md §8 (BusinessException + ErrorCode)
- Topology 참조: `workflows/topologys/Architecture-Style-Topology.md`
- Brainstorming 참조: `workflows/task/pes/brainstorming/0.0.1v/ai.md`

## 열린 질문 (Open Questions)
- Gemini API 키·quota 확보 시점?
- LLM 응답 시간 P95 3초 목표가 UX 관점에서 허용되는지 (동기 vs 비동기 · SSE 스트리밍)?
- Prompt 버전 관리를 Git만으로 충분한가, 아니면 DB로 이관해야 하는 시점?
- Circuit Breaker 도입 트리거 (fallback 발동율 몇 % 초과 시)?

## 제품 수준 완료 기준 (Product-level DoD)
- [ ] 모든 Epic DoD 통과
- [ ] Static Adapter만으로 E2E 테스트 통과 (개발 환경 · 결정적)
- [ ] Gemini 샌드박스 E2E 통과 (실 LLM 호출)
- [ ] Fallback 발동 시나리오 통합 테스트
- [ ] ADR 4건 발행
- [ ] 관측 지표 대시보드 초기 설정
- [ ] `.claude/rules/exception.md` §5.4에 `AI001~AI099` 접두어 등록

---

# [Epic 7] AI 제안 기반 구조 구축

## 목표
도메인을 오염시키지 않고 AI 제안 기능을 확장할 수 있는 Port/Adapter 기반 구조를 구축하고, AI 호출 실패가 유저 입력 자체의 실패로 이어지지 않도록 Fallback 경로와 에러 체계를 먼저 정의한다.

## 배경
Product의 골격. 이후 Gemini(Epic 8) · Axis(Epic 9) · AxisTopic(Epic 10) · 관측성(Epic 11)이 얹혀지는 기반.

## 포함 Story
- Story 7-1: SuggestionPort 인터페이스 정의 (`AxisSuggestionPort`·`AxisTopicSuggestionPort` + VO)
- Story 7-2: Static Suggestion Adapter 구현 (Fallback + 테스트용)
- Story 7-3: SuggestionErrorCode 체계 정의 (`AI001~AI099`)

## Epic 인수 시나리오
- Given `provider=static` 설정
- When Application이 Suggest 호출
- Then Static Adapter 응답 반환 (LLM 호출 없이)

## Epic 완료 기준 (DoD)
- [ ] `AxisSuggestionPort`·`AxisTopicSuggestionPort` 인터페이스 존재
- [ ] Static Adapter 2개 구현 완료
- [ ] `AI001~AI099` ErrorCode 등록
- [ ] 기능 플래그(`thirdtool.suggestion.provider`) 스위칭 검증

---

## [Story 7-1] SuggestionPort 인터페이스 정의

### User Story
- As a Application 개발자
- I want AI 제안 요청 규격을 도메인과 분리된 Port로 정의
- so that 도메인이 AI의 존재를 모르게 유지하면서 이후 어떤 Adapter로든 교체 가능

### 설명
- `nbc.c1oud_mall.aisuggestion.application.port.out.suggestion/` 신설
- 두 Port + 두 VO:
  - `AxisSuggestionPort.suggest(concept, existingAxis, limit): List<AxisSuggestion>`
  - `AxisTopicSuggestionPort.suggest(concept, axisName, existingTopics, limit): List<AxisTopicSuggestion>`
  - `AxisSuggestion(description, rationale)` — record
  - `AxisTopicSuggestion(description, rationale)` — record
- 도메인 엔티티는 Port 직접 참조 금지 (application service만 참조)
- VO Compact Constructor에서 `description` null/blank 검증 → `IllegalArgumentException`

### 완료 기준 (AC)
- Given `port/out/suggestion/` 컴파일 / When 코드 컴파일 / Then Port 2개 · VO 2개 존재
- Given 도메인 엔티티 grep / When 검사 / Then Port 참조 없음
- Given VO description blank / When 생성 / Then `IllegalArgumentException`
- *(엣지)* rationale null 허용

### Definition of Done
- [ ] 2 Port 인터페이스 + 2 VO record
- [ ] 단위 테스트: VO 불변식 · 도메인 계층 의존 없음 검증
- [ ] 코드 리뷰 완료

### 스토리 포인트
2 SP

### 의존성
- 선행: 없음
- 후행: Story 7-2, 7-3, Epic 9·10

---

## [Story 7-2] Static Suggestion Adapter 구현

### User Story
- As a 개발자·운영자
- I want Static Adapter로 LLM 없이 안전한 응답 반환
- so that 테스트·Fallback 경로 확보

### 설명
- `nbc.c1oud_mall.aisuggestion.infrastructure.StaticAxisSuggestionAdapter`
- `@Component + @ConditionalOnProperty(name="thirdtool.suggestion.provider", havingValue="static", matchIfMissing=true)`
- 반환 기본값: 빈 목록 + `warning` 필드 (fallback 사용 안내)
- 개발/테스트 환경 결정성 확보

### 완료 기준 (AC)
- Given `provider=static` / When Adapter 호출 / Then 빈 목록 반환 · 예외 없음
- Given 프로파일 미설정 / When 호출 / Then `matchIfMissing=true`로 Static 활성화

### Definition of Done
- [ ] `StaticAxisSuggestionAdapter` + `StaticAxisTopicSuggestionAdapter`
- [ ] `@ConditionalOnProperty` 스위칭 통합 테스트
- [ ] 단위 테스트 (빈 목록 · 예외 없음)

### 스토리 포인트
1 SP

### 의존성
- 선행: Story 7-1
- 후행: Story 7-3, Epic 9·10

---

## [Story 7-3] SuggestionErrorCode 체계 정의 (`AI001~AI099`)

### User Story
- As a 개발자
- I want AI 제안 관련 ErrorCode를 표준 규범(CLAUDE.md §8)에 맞춰 등록
- so that GlobalExceptionHandler가 일관된 `ApiResponse.error(...)` 반환

### 설명
- `common.exception.ErrorCode`에 도메인 섹션 `// ─── AI 제안 ───` 추가
- 초기 등록:
  - `AI001` — LLM 요청 초과 (rate limit) · 429
  - `AI002` — LLM 서버 오류 · 502 (Fallback 트리거)
  - `AI003` — LLM 응답 파싱 실패 · 200 (Fallback 트리거)

### 완료 기준 (AC)
- Given `ErrorCode` enum / When 컴파일 / Then `AI001~AI003` 등록됨
- Given AI 계열 예외 발생 / When Handler / Then `ApiResponse.error("AI0XX", ...)` 반환

### Definition of Done
- [ ] `ErrorCode` enum 등록 (Lombok `@Getter @RequiredArgsConstructor`)
- [ ] 단위 테스트: 각 코드가 status/message 반환

### 스토리 포인트
0.5 SP

### 의존성
- 선행: Story 7-1
- 후행: Epic 8~11

---

# [Epic 8] Gemini 2.5 Flash 연동 공통 인프라

## 목표
Gemini 2.5 Flash API를 안정적으로 호출하기 위한 공통 인프라 (`GeminiClient`, `PromptLoader`, `SuggestionResponseParser`)를 구축하여, Epic 9·10에서 Adapter가 이를 재사용한다.

## 포함 Story
- Story 8-1: `GeminiClient` — WebClient 기반 REST 클라이언트
- Story 8-2: `PromptLoader` — classpath 리소스 프롬프트 로딩
- Story 8-3: `SuggestionResponseParser` — Structured Output (JSON) 파싱

## Epic 인수 시나리오
- Given Gemini API 키 · Prompt 리소스 존재
- When `GeminiClient.chat(request)`
- Then Structured Output 응답 → 파싱 → 도메인 VO 반환

## Epic 완료 기준 (DoD)
- [ ] 3개 인프라 컴포넌트 완성
- [ ] MockWebServer 단위 테스트
- [ ] Gemini 샌드박스 E2E 검증 (수동)

## Epic 기술 결정 / 대안 (Epic-Level Alternatives)
- **HTTP 클라이언트**: `WebClient` (비동기 · reactive-friendly)
- **JSON 파싱**: Jackson `ObjectMapper` (`@JsonInclude(NON_NULL)`)
- **Prompt 위치**: `src/main/resources/prompts/` (Story 8-2)

### [Epic 세부는 후속 세션에서 상세화 — 현재는 Skeleton 상태]

---

# [Epic 9] 축 제안 기능 (Axis Suggestion E2E)

## 목표
사용자의 컨셉과 기존 축 목록을 입력받아, LLM 또는 Static Adapter를 통해 축 후보 목록을 반환하는 REST API를 완성한다.

## 포함 Story
- Story 9-1: `LlmAxisSuggestionAdapter` (Gemini)
- Story 9-2: 응답 검증 (`AxisSuggestion` 후처리 · 중복 제거 · 길이 제한)
- Story 9-3: `AxisSuggestionService` + REST API (`POST /api/v1/ai/axis/suggest`)

## Epic 인수 시나리오
- Given `provider=llm` + 유효한 concept
- When `POST /api/v1/ai/axis/suggest`
- Then 200 · `ApiResponse.success(List<AxisSuggestion>)` (5개, 기존 축과 중복 없음)

## Epic 완료 기준 (DoD)
- [ ] LLM Adapter · 검증 · Service · Controller 완성
- [ ] Fallback 시나리오 통합 테스트 (LLM 실패 시 Static 응답)
- [ ] E2E: Gemini 샌드박스 정상 응답 확인

### [Epic 세부는 후속 세션에서 상세화 — 현재는 Skeleton 상태]

---

# [Epic 10] AxisTopic 제안 기능 (하위 주제 제안 E2E)

## 목표
축 이름과 기존 주제 목록을 입력받아, 축 하위의 주제 후보를 반환하는 REST API를 완성한다.

## 포함 Story
- Story 10-1: `LlmAxisTopicSuggestionAdapter` (Gemini)
- Story 10-2: 응답 검증
- Story 10-3: `AxisTopicSuggestionService` + REST API (`POST /api/v1/ai/axistopic/suggest`)

## Epic 완료 기준 (DoD)
- [ ] LLM Adapter · 검증 · Service · Controller 완성
- [ ] Fallback 통합 테스트
- [ ] E2E: Gemini 샌드박스 확인

### [Epic 세부는 후속 세션에서 상세화 — 현재는 Skeleton 상태]

---

# [Epic 11] 관측성 및 장애 대응 안정화

## 목표
AI 제안 기능의 실 응답 성공률·Fallback 발동율·응답 시간 P95를 관측하고, LLM 장애 시 운영자가 즉시 대응할 수 있는 절차를 확립한다.

## 포함 Story
- Story 11-1: Metrics + 로깅 (`ai_suggest_total`, `ai_suggest_duration_seconds`, `ai_fallback_total`, `ai_llm_error_total`)
- Story 11-2: Fallback 운영 절차 (Runbook + `provider` 전환 매뉴얼)

## Epic 완료 기준 (DoD)
- [ ] Micrometer 지표 노출 (Actuator + Prometheus)
- [ ] Grafana 대시보드 (또는 CloudWatch Insights)
- [ ] Runbook 문서 (`workflows/living-docs/` 하위)

### [Epic 세부는 후속 세션에서 상세화 — 현재는 Skeleton 상태]

---

## 참고 (원본 상세 아카이브)

- 이 Product는 초기 구조(Epic 7~11 골격)만 정리됨.
- 각 Epic의 Story 세부(프롬프트 문안·JSON 스키마·검증 규칙 등)는 in-progress 단계에서 상세화 예정.
- 원본 브레인스토밍 자료는 `workflows/task/pes/brainstorming/0.0.1v/ai.md` 참조.
