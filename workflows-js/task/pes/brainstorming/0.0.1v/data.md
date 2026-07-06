# [PES · Brainstorming] Data — 0.0.1v (2026-07-02)

> 데이터 모델 · 스키마 진화 · 성능 관련 크로스 컷팅 후보.

---

## [Candidate 1] Flyway 도입 여부 [pending]

### 배경
- 현재 JPA `ddl-auto=update`로 prod 스키마 자동 진화 (커밋 `fcc67db`)
- update는 컬럼 삭제·rename에 무력 → 첫 실 사고 리스크
- Flyway 도입은 baseline 스크립트 · CI 검증 · migration convention 정착 필요 (오버헤드 큼)

### 후보안
- **A안 (지금 도입)**: 지금 Flyway + baseline 스크립트 준비 → 나중 사고 예방
- **B안 (지연)**: 첫 컬럼 삭제/rename 이슈 발생 시 도입 → 지금은 update 유지
- **C안 (절충)**: `hibernate schema validate` 전환 + 수동 SQL 관리 → Flyway 도입 준비 단계

### 1차 권장
- **B안** (지연) — 팀 스피드 우선
- 트리거: 첫 컬럼 삭제/rename 이슈 발생 시점 또는 유저 100명 도달 시

### PES 승격 경로
- 신규 Product 후보: "Flyway 마이그레이션 도입"
- Fix 이슈 트래킹: `fix/brainstorming/version/0.0.1v/infra.md` Issue 2

### 미결 질문
- 현재 update가 신규 컬럼 추가에는 잘 동작하는데 rename에서 실패했을 때 팀 대응 절차 있는가?

---

## [Candidate 2] 재고 서비스 도입 (Inventory Service) [pending]

### 배경
- `fix/brainstorming/order.md` Issue 1: mock inventory 사용 중
- 결제 성공 후 재고 부족 발견 → 보상 취소 유발 (사용자 UX 저하)
- 여러 상품 락 획득 순서 미정 → deadlock 리스크

### 후보안
- **A안 (선택 후보)**: Product BC에 `InventoryService` 도메인 서비스 + productId 오름차순 정렬 락 (`SELECT ... FOR UPDATE`)
  - 보상: 정합성 강함
  - 비용: 커넥션 점유
- **B안**: Redis atomic `DECR` — 성능 우선, 정합성 후처리
- **C안**: 낙관적 락 (`@Version`) + 재시도

### 1차 권장
- **A안** — 지금은 트래픽 낮으므로 커넥션 점유 크지 않음
- 트리거: 결제 100건/일 초과 시 B안 재검토

### PES 승격 경로
- **`pes` tier** — Order + Product 크로스 BC (3~5 Story)
  - Story 1: `InventoryService` port + impl
  - Story 2: productId 정렬 락 로직
  - Story 3: 통합 테스트 (동시 주문 시나리오)

### 미결 질문
- Product BC 소유자와의 협의 필요
- 재고 감소 시점: 주문 생성 vs 결제 확정 후 어느 쪽?

---

## [Candidate 3] Payment 상태 기계 명시화 [pending]

### 배경
- `fix/brainstorming/payment.md` Issue 1: 결제 확정 순서 vs 락 순서 불일치
- 상태 전이(PENDING → CONFIRMED / FAILED / CANCELLED)가 코드 여러 곳에 분산

### 후보안
- **A안 (선택)**: `PaymentStatus` enum + 도메인 메서드(`confirm()`, `cancel()`, `fail()`)에 전이 규칙 캡슐화
- **B안**: 별도 `PaymentStateMachine` 클래스 (Spring StateMachine 등)

### 1차 권장
- **A안** — 단순, 팀 컨벤션 정합
- B안은 과함 (현 규모에서)

### PES 승격 경로
- Fix: payment.md Issue 1 `pes` tier 승격 시 흡수

---

## [Candidate 4] N+1 쿼리 방지 정책 [pending]

### 배경
- 상품 목록 32건 + Category · Image 연관 조회 시 N+1 발생 가능
- 현재 코드에는 명시적 fetch join 없음

### 후보안
- **A안 (선택)**: 조회 케이스별 fetch join 또는 `@EntityGraph` — 명시적
- **B안**: hibernate `default_batch_fetch_size` 설정 — 전역

### 1차 권장
- **A안** — 조회별 명시가 유지보수성 좋음
- B안은 최후 방어선으로 병행

### PES 승격 경로
- 신규 Product 후보 없음 — 각 도메인 리팩토링 시 개별 반영

---

## 참조

- 스냅샷: `snapshot.md`
- Persistence 규범: `.claude/rules/persistence.md`
- Fix 인프라 이슈: `../../fix/brainstorming/version/0.0.1v/infra.md`
- Fix Order 이슈: `../../fix/brainstorming/version/0.0.1v/order.md`
