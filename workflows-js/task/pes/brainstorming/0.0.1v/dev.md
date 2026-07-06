# [PES · Brainstorming] Dev — 0.0.1v (2026-07-02)

> 개발 컨벤션 · 코드 품질 · 리팩토링 방향 관련 크로스 컷팅 후보.

---

## [Candidate 1] 도메인 = JPA Entity 통합 (방식 A) 유지 [resolved]

> `.claude/rules/persistence.md`의 방식 (A)와 방식 (B) 중 팀은 (A) 확정

### 배경
- 팀 규모 · 스피드 상 도메인·엔티티 분리(방식 B)의 오버헤드 큼
- 단순 CRUD가 많고 비즈니스 로직 위주 도메인이 소수
- 이미 memory에 확정 기록됨 (`project_persistence_combined_approach.md`)

### 후보안
- **A안 (선택)**: 도메인 = JPA Entity 통합. Repository는 infrastructure의 Spring Data 인터페이스 하나. VO는 `@Embeddable` 클래스.
  - 보상: 빠른 구현, 단순 스택
  - 비용: domain 패키지가 `jakarta.persistence` 의존 → 엄격한 DDD 위반
- **B안**: 완전 분리 (POJO domain + JpaEntity + RepositoryImpl 3계층)
  - 거부 이유: 팀 규모 대비 오버헤드 큼

### 1차 권장
- **A 확정** (팀 합의 · memory 반영)
- 예외: 도메인 로직이 극도로 복잡해지는 컨텍스트는 개별적으로 B로 분리 검토 가능

### PES 승격 경로
- 모든 신규 Product SDD §5(설계 결정)에 명시적으로 인용
- `.claude/rules/persistence.md` 문서화 (완료)

---

## [Candidate 2] BusinessException + ErrorCode 단일 예외 정책 [resolved]

### 배경
- `.claude/rules/exception.md`와 `common.exception.*` 4개 파일이 SSOT
- 도메인마다 별도 Exception 클래스 남발 방지

### 후보안
- **A안 (선택)**: `BusinessException + ErrorCode enum + GlobalExceptionHandler + ApiResponse` 4개 파일 조합만 사용
- **B안**: 도메인별 Custom Exception 클래스 생성

### 1차 권장
- **A 확정** — CLAUDE.md §8, `.claude/rules/exception.md`, `.claude/rules/forbidden.md`에서 강제

### PES 승격 경로
- 모든 Product SDD §8(실패 모드) 표는 반드시 `ErrorCode` 기반
- 신규 도메인 시 `ErrorCode`에 항목 먼저 추가 후 사용

---

## [Candidate 3] ApiResponse 응답 래퍼 통일 [resolved]

### 배경
- 모든 HTTP 응답(성공/실패)이 `ApiResponse<T>`로 통일
- 정적 팩토리만 사용 (`success`, `successNoContent`, `error`)

### 후보안
- **A안 (선택)**: 모든 컨트롤러 반환 = `ResponseEntity<ApiResponse<XxxResponse>>`
- **B안**: 원시 DTO 반환 + Filter/Advice로 후처리 래핑

### 1차 권장
- **A 확정** — CLAUDE.md §4, `.claude/rules/dto.md`, `.claude/rules/forbidden.md`에서 강제
- `ApiResponses.ok(...)`, `ApiResponses.created(...)` 헬퍼 사용 강제

### PES 승격 경로
- 모든 Product SDD Story의 AC/DoD에 `ApiResponse` 래핑 명시

---

## [Candidate 4] 도메인 ADR 이중 위치 [resolved]

> 도메인 ADR은 `src/main/java/.../docs/adr/` 근처(도메인 하위)에 두고, 전역 `docs/adr/`로의 이동은 개발자 명시 시에만

### 배경
- memory에 확정 (`project_adr_location.md`)
- 도메인별 컨텍스트 유지 · 전역 도메인 문서와의 경계 명확화

### 후보안
- **A안 (선택)**: 도메인 하위 (`workflows/living-docs/Ai-adr/*`)에 먼저 두고 필요 시 전역 승격
- **B안**: 모두 전역 `docs/adr/`
- **C안**: 도메인별 완전 분리

### 1차 권장
- **A 확정**
- 신규 ADR은 `workflows/living-docs/Ai-adr/NNN-<slug>.md`로 생성

### PES 승격 경로
- Product SDD §14(관련 문서)에서 ADR 위치 표기

---

## [Candidate 5] Story = 1 PR 규칙 유지 [resolved]

### 배경
- `.claude/rules/workflow.md` §9에서 확정
- 리뷰 부담 관리·롤백 지점 명확화

### 후보안
- **A안 (선택)**: Story 하나 = PR 하나 (Squash merge)
- **B안**: Epic 단위 PR (여러 Story 묶음)

### 1차 권장
- **A 확정** — 예외 없음

---

## [Candidate 6] Custom BC 간 협력 = 직접 호출 vs 이벤트 [pending]

### 배경
- ADR 006 (`bc-collaboration-direct-call.md`)에서 현재 **직접 호출** 채택
- 트래픽·복잡도 증가 시 이벤트 기반 전환 검토 필요

### 후보안
- **A안 (현재)**: 직접 호출 (`OrderService`가 `PaymentContext`를 직접 호출)
- **B안**: Domain Event + ApplicationEventPublisher (in-process)
- **C안**: 외부 Message Broker (Kafka/SQS)

### 1차 권장
- **현재는 A** 유지
- 트리거: 결제·환불 트랜잭션 경계가 3개 BC 이상 얽히면 B로 전환 검토
- C는 트래픽 스케일 문제 발생 시

### PES 승격 경로
- 트리거 감지 시 신규 Product 후보 ("BC 간 이벤트 도입")

---

## 참조

- 팀 컨벤션 SSOT: `CLAUDE.md`, `.claude/rules/*.md`
- 스냅샷: `snapshot.md`
- Product SDD 정의: `../../workspectrum/sdd/sdd.md`
