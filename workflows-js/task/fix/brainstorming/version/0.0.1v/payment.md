# [Fix · Brainstorming] Payment BC — 0.0.1v (2026-07-02)

> **역할**: 결제(Payment) BC의 실행 중 발견 이슈를 가설·결정으로 축적
> **짝 파일**: [`order.md`](./order.md), [`refund.md`](./refund.md)
> **다음 단계**: 각 이슈 → 결정 → 정식 fix tier 문서로 승격

---

## 추적 컨텍스트

| 항목 | 사실 |
|---|---|
| Scope | 결제 도메인 (`nbc.c1oud_mall.payment.*`) + PortOne v2 연동 |
| 진행 중 작업 | branch `chore/dummy-products-prod-expansion` (더미 데이터 확장) |
| 발견 시점 | 2026-06-09 페어리뷰 (`workflows/review/state/` 참조) |
| fix tier 정의 | `../../../pes/workspectrum/sdd/sdd.md` |

---

## [Issue 1] — 결제 확정 순서 vs 락 순서 불일치 [pending]

### 현상 / 트리거
`2026-06-09-payment-confirmation-order-vs-lock-order.md` (review/state) 페어리뷰에서 확인: 결제 확정 로직에서 도메인 검증 → DB 잠금 → PG 호출 순서가 명세와 실제 코드 사이 어긋남. 동시성 상황에서 이중 승인 리스크 존재.

### 원인 가설
| # | 가설 | 개연성 근거 |
|---|---|---|
| (a) | 잠금 없이 SELECT 후 승인 처리 — 이중 승인 창 존재 | 코드 확인으로 즉시 검증 가능 |
| (b) | `@Transactional` 안에서 PortOne HTTP 호출 → 트랜잭션 장시간 유지 | 로그의 TX 시간으로 확인 |
| (c) | 도메인 검증(선검증)이 트랜잭션 밖에서 수행되어 stale read | Service 스택트레이스 검토 |

**최고 개연성**: (a)+(b) 결합. 현행 코드가 트랜잭션 안에서 외부 호출을 하고 있을 가능성 큼.

### 영향 범위
- 결제 확정 (Epic 2)
- 웹훅 재수신 시 멱등성 (Epic 3) — 잠금이 없으면 웹훅과 사용자 확정 요청이 경쟁 상태
- Refund와 공유되는 Payment 상태 전이

### 결정해야 할 것
- **(A) 도메인 검증 → DB에 PENDING 상태 커밋(짧은 TX) → 별도 non-tx 컨텍스트로 PortOne 호출 → 결과 반영(짧은 TX)** — 3단 분리
- **(B) 단일 트랜잭션 유지 + 비관적 락 + 외부 호출 타임아웃 강제** — 단순하지만 커넥션 점유
- **(C) 낙관적 락 + 재시도** — 성공률 낮은 케이스에서 사용자 경험 저하

### 권장 fix 방향 (1차)
1. Step 1: `PaymentService.confirm` 현재 트랜잭션 경계 확인
2. Step 2: PortOne 호출을 트랜잭션 밖으로 뺄 수 있는지 검증 (Adapter 위치 조정)
3. Step 3: 결정 (A) 채택 시 `pes` tier 문서로 승격 → Refund BC와 동일 패턴 정렬

### workspectrum tier 추천
- (A) 채택: **`pes`** (Payment + Refund 동시 정렬, 3~5 Story)
- (B) 채택: **`feature-story`** (단일 Service 수정)

---

## [Issue 2] — 결제 보상 트랜잭션 self-injection 함정 [pending]

### 현상 / 트리거
`2026-06-09-payment-compensation-self-injection-trap.md` (review/state): 결제 보상(compensation) 로직에서 self-injection 없이 `this.compensate()` 호출 시 프록시 우회로 `@Transactional`이 안 걸림.

### 원인 가설
| # | 가설 | 개연성 근거 |
|---|---|---|
| (a) | AOP 프록시 우회 (Spring 표준 함정) | 100% 재현 가능 |
| (b) | 보상 로직이 원 트랜잭션에 실려 롤백 시 함께 롤백됨 | 로그 확인 |

**최고 개연성**: (a). 즉시 개선 가능.

### 영향 범위
- 결제 확정 실패 시 롤백 후 보상이 실행되지 않을 수 있음 → 사용자에게 결제 실패 응답은 갔는데 PortOne에는 승인 상태 잔존 가능

### 결정해야 할 것
- **(A) `TransactionTemplate` 명시적 사용** — Bean 주입 없이 트랜잭션 경계 명확화
- **(B) 별도 `PaymentCompensationService` Bean으로 분리** — 자연스러운 프록시 경유
- **(C) self-injection (`@Lazy` + 자기 자신)** — 관례적이지만 가독성 저하

### 권장 fix 방향 (1차)
1. Step 1: 보상 로직을 새 `PaymentCompensationService`로 분리 (결정 B)
2. Step 2: `REQUIRES_NEW` 전파 속성 명시
3. Step 3: 통합 테스트로 원 TX 롤백 → 보상 커밋 시나리오 검증

### workspectrum tier 추천
- **`feature-story`** — 단일 서비스 리팩토링 + 통합 테스트 1건

---

## [Issue 3] — PortOne 웹훅 멱등성: INSERT-first 전략 확인 필요 [promoted → ADR 005]

### 현상 / 트리거
ADR 005 (`webhook-idempotency-insert-first.md`)에서 결정된 INSERT-first 패턴이 코드에 완전히 반영되었는지 확인 필요.

### 상태
**promoted** — ADR 005로 결정 완료. 남은 작업은 준수 검증만.

### 잔여 액션
- [ ] 웹훅 핸들러 코드에서 `INSERT ON CONFLICT DO NOTHING` (또는 유니크 제약 위반 catch) 패턴 확인
- [ ] 통합 테스트: 동일 `paymentId`로 웹훅 2회 → 두 번째는 no-op 확인

---

## [Issue 4] — CORS 하드코딩 및 JWT secret 함정 [promoted → 별도 티어]

### 상태
**promoted** — `2026-06-09-cors-hardcoded-and-jwt-secret-trap.md`로 분리됨. 여기서는 링크만 유지.

### 승격 대상 문서
- 인프라·설정 이슈이므로 [`infra.md`](./infra.md)로 이동 예정

---

## 누적 메모 (Free-form Memo)
- 2026-06-09 — 페어리뷰 4건 식별 (Issue 1·2·3·4)
- 2026-07-02 — 브레인스토밍 파일 정식 등록
- (추후 추가)

---

## 참조
- 짝 파일: [`order.md`](./order.md), [`refund.md`](./refund.md)
- Fix tier 정의: `../../../pes/workspectrum/sdd/sdd.md` §fix 레이어
- 대상 마일스톤: `../../../milestones/version/0.0.1v/milestone.md`
- ErrorCode 원본: `src/main/java/nbc/c1oud_mall/common/exception/ErrorCode.java` (`PAY001`, `PAY002`)
- 예외 처리 규범: `.claude/rules/exception.md`
- ADR: `workflows/living-docs/Ai-adr/002~005-payment-*.md`
