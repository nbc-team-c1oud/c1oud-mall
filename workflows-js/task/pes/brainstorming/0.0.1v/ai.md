# [PES · Brainstorming] AI — 0.0.1v (2026-07-02)

> AI 연동 · Prompt · Fallback 정책 관련 크로스 컷팅 후보.

---

## [Candidate 1] Gemini 2.5 Flash · Static/LLM 이중 어댑터 패턴 [pending → 승격 예정]

### 배경
- AI 기능(축 제안, AxisTopic 제안)이 사용자 경험의 중심이지만 LLM 응답은 안정성 낮음
- API quota · 응답시간 · 예상치 못한 응답 형식 위험

### 후보안
- **A안 (선택)**: `SuggestionPort` 인터페이스 + `StaticSuggestionAdapter`(fallback) + `LlmSuggestionAdapter`(Gemini) 이중 구현. `provider=static|llm` 프로파일 스위치.
  - 보상: 개발 초기 진입 쉬움, 장애 시 즉시 fallback
  - 비용: 두 어댑터 유지 부담
- **B안**: LLM 단일 어댑터 + 실패 시 예외 반환
- **C안**: 여러 LLM 제공사 조합 (OpenAI + Gemini + Claude 라운드로빈)

### 1차 권장
- **A안** — 팀 규모·리스크 고려 시 이중 어댑터가 최선
- C안은 오버엔지니어링

### PES 승격 경로
- **product-ai-suggestion Epic 7**: Port/Adapter 뼈대
- **product-ai-suggestion Epic 8**: Gemini Client 인프라
- 필요 ADR: "AI Suggestion Fallback Strategy"

---

## [Candidate 2] Fallback 트리거 조건 [pending]

### 배경
- Candidate 1에서 static fallback을 언제 발동할지 정책 필요
- 항상 static: LLM 미사용 → 무의미
- 항상 LLM: 장애 시 서비스 다운

### 후보안
- **A안 (선택)**: `provider=static|llm` 프로파일 · **런타임 자동 fallback** (LLM 5xx / timeout / 파싱 실패 시 즉시 static)
- **B안**: Circuit Breaker (Resilience4j) — 특정 실패율 초과 시 static
- **C안**: 수동 스위치만 (운영자가 config 변경)

### 1차 권장
- **A안** 착수 → 트래픽 늘면 B안 병행
- 초기에는 A로 단순 시작

### PES 승격 경로
- product-ai-suggestion Epic 11 (관측성 및 장애 대응 안정화)

---

## [Candidate 3] Prompt 리소스 관리 [pending]

### 배경
- LLM 프롬프트는 자주 튜닝됨 → 코드 안에 하드코딩하면 배포 필요
- Version 관리 · A/B 테스트 필요성 대두

### 후보안
- **A안 (선택)**: `src/main/resources/prompts/*.txt` 파일 + `@Value("classpath:prompts/xxx.txt")` 로딩. Git 버전 관리.
- **B안**: DB 저장 · 관리자 UI 편집
- **C안**: Prompt Registry 외부 SaaS (LangSmith 등)

### 1차 권장
- **A안** — 팀 스피드 · 배포 프로세스 정합
- B/C는 사용자 · 프롬프트 수 늘면 재검토

### PES 승격 경로
- product-ai-suggestion Epic 8 (Gemini 공통 인프라)

---

## [Candidate 4] Structured Output Parsing (JSON Schema) [pending]

### 배경
- Gemini에게 자유 응답 요청 시 형식 흔들림 → 파싱 실패
- Structured output 강제(JSON Schema) 필요

### 후보안
- **A안 (선택)**: Gemini API의 `response_schema` 지정 + 서버측 이중 검증 (Jackson `readValue` + 필드 존재 확인)
- **B안**: 정규식 후처리 · 자유 텍스트 파싱

### 1차 권장
- **A안** — Gemini SDK 지원 시 즉시

### PES 승격 경로
- product-ai-suggestion Epic 8

---

## 참조

- 스냅샷: `snapshot.md`
- product-ai-suggestion (기존): `workflows/products/product-aisuggestion.md`
- Fix AI 이슈: (아직 없음 — 필요 시 `../../fix/brainstorming/version/0.0.1v/ai-suggestion.md` 생성)
