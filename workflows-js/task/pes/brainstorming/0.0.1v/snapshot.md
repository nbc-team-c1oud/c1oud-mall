# [PES · Brainstorming] Snapshot — 0.0.1v (2026-07-02)

> 이번 버전(0.0.1v)에서 브레인스토밍된 크로스 컷팅 후보들의 **한눈에 보기** 표.
> 각 후보의 상세는 도메인별 파일 참조.

---

## 이번 버전 후보 요약

| Candidate | 도메인 | 상태 | 승격 대상 | 우선도 |
|---|---|---|---|---|
| C1: 도메인 = JPA Entity 통합 (방식 A) 유지 | dev | **resolved** (팀 확정, memory 반영) | 모든 Product SDD §5에 명시 | 최상 |
| C2: BusinessException + ErrorCode 단일 예외 정책 | dev | **resolved** | 모든 Product SDD §8 (실패 모드) | 최상 |
| C3: ApiResponse 응답 래퍼 통일 | dev | **resolved** | 모든 Product SDD §8 | 최상 |
| C4: 도메인 ADR 이중 위치 (도메인 하위 + 전역 docs) | dev | **resolved** (memory 반영) | ADR 문서 위치 규정 | 중 |
| C5: MDC 요청 컨텍스트 (requestId · userId) 도입 | ops | **pending** | product-log Epic 2 | 상 |
| C6: 구조화 로깅 JSON 포맷 (logstash-logback-encoder) | ops | **pending** | product-log Epic 1 | 상 |
| C7: Flyway 도입 검토 | data | **pending** | (신규 Product 또는 유지 문서화) | 중 |
| C8: 재고 서비스 도입 (Redis vs 비관적 락 vs 낙관적 락) | data | **pending** | Order + Product 크로스 BC | 상 |
| C9: Gemini 2.5 Flash static/LLM 이중 어댑터 패턴 | ai | **pending** → 승격 예정 | product-ai-suggestion Epic 7 | 최상 |
| C10: Fallback 정책 (`provider=static` 트리거 조건) | ai | **pending** | product-ai-suggestion Epic 11 | 상 |
| C11: prod SecurityConfig 프로파일 분기 (CORS · JWT) | deploy | **pending** | fix/brainstorming/infra.md Issue 1 | 상 |
| C12: Secrets Manager 도입 | deploy | **pending** | 신규 Product 후보 | 중 |

---

## 상태 통계

- **resolved**: 4건 (팀 컨벤션으로 확정, memory 반영)
- **pending**: 8건 (Product SDD로 승격 대기)
- **promoted**: 0건 (다음 버전에서 이동)
- **deprecated / merged**: 0건

---

## 다음 버전(0.0.2v)에서 하고 싶은 것

1. **C5·C6 승격**: product-log Product를 정식 SDD로 완성 → in-progress로 이동
2. **C9·C10 승격**: product-ai-suggestion Epic 7~11 착수 및 SDD 완성
3. **C8 착수**: Order + Product 크로스 BC 브레인스토밍 심화 (pes 티어)
4. **C11 실행**: infra fix 티어로 즉시 처리

---

## 참조

- 상세 후보 파일: `dev.md`, `ops.md`, `data.md`, `ai.md`, `deploy.md`
- Product SDD: `../../workspectrum/sdd/sdd.md`
- Fix 이슈: `../../fix/brainstorming/version/0.0.1v/`
- 마일스톤: `../../milestones/version/0.0.1v/`
