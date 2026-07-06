# [0.0.3v · Issue 14] ADR 시리즈 · README · Bounded Context 다이어그램 · 성능 리포트 통합

> **역할**: 이력서 파괴력의 상당 부분이 문서 정합성에서 나옴. 최종 정리·통합.
> **Week**: 3 · **Day**: 21
> **상태**: pending
> **Tier**: `one-line-spec` (문서만)
> **캠프 요구사항 매핑**: 필수·도전 통합 문서화 (락 3전략 README · 검색 캐시 근거 README · k6 리포트 · EXPLAIN 리포트 · DDL)

---

## 배경

리뷰어가 GitHub 열어봤을 때 30초 안에 인상을 잡는 마지막 관문. Week 1~3 산출물을 서사로 엮어 README·ADR·다이어그램으로 통합.

## 핵심 스코프

### README 재작성 (`README.md`)

**첫 화면 (3초 인상)**:
- 프로젝트 정체성 한 줄 (Ops Backend)
- 아키텍처 다이어그램 (Mermaid or PNG)
- 기술 스택 뱃지 (Spring Boot 4 · Java 21 · MySQL · Redis · QueryDSL · JWT · WebSocket + STOMP · k6)
- 성능 리포트 요약 표 (P95 · TPS · 인덱싱 개선률)

**주요 섹션**:
1. Overview — Ops Backend 정체성 · third-tool과의 상보
2. Architecture — 4레이어 DDD · Bounded Context 맵
3. Domains — auth · product · order · payment · admin · event · coupon · chat 요약
4. Concurrency Strategy — 락 3전략 비교 표 (이슈 07 결과)
5. Caching — v1 vs v2 (Caffeine) vs v2 (Redis) 성능
6. Real-time (CS Chat) — STOMP + 상태기계 다이어그램
7. Performance — k6 리포트 (그래프 · P50/P95/P99 · TPS · Saturation)
8. Indexing — EXPLAIN Before/After 표 · DDL
9. Local Setup — docker-compose · dev 부팅 절차
10. ADR List — 링크

### ADR 시리즈 확장

기존 11건 (`docs/adr/0001~0011`) 에 추가:
- **ADR 012** — Ops Backend 정체성 재정의 (07-05 회의 결과)
- **ADR 013** — 동시성 락 3전략 비교·최종 선택
- **ADR 014** — v2 Local → Redis Remote 전환 근거 (Scale-out)
- **ADR 015** — CS 채팅 상태기계 (WAITING/IN_PROGRESS/COMPLETED)
- **ADR 016** — 인덱싱 전략 (복합·커버링·Leftmost Prefix)

### Bounded Context 다이어그램

- Mermaid or draw.io
- 8개 컨텍스트 (auth · product · order · payment · refund · point · admin · event · coupon · chat)
- BC 간 협력 방향 (직접 호출 · 이벤트)

### 성능 리포트 통합

`docs/perf/`:
- `search-v1-vs-v2-report.md` (이슈 11)
- `indexing-explain-report.md` (이슈 12)
- 통합 요약 페이지 (`docs/perf/README.md`)

## 산출물

- BE-14-1: `README.md` 재작성 (10 섹션)
- BE-14-2: `docs/architecture/bc-map.md` (Mermaid)
- BE-14-3: ADR 012~016 신규 5건 (`docs/adr/`)
- BE-14-4: `docs/perf/README.md` 통합 요약
- BE-14-5: README 이미지 자산 (`.github/assets/`) — 스크린샷·다이어그램

## 관련 이슈 / 문서

- 선행: 이슈 01~13 전부
- 규범: `.claude/rules/git.md` (문서 관련 commit)

## 상세 (착수 시 채움)

_pending_
