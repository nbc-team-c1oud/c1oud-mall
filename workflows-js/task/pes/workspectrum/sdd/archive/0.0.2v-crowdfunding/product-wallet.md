# [Product 5] 지갑 (Wallet · 선불 예치금)

## Product Vision
> 후원자·메이커가 PortOne 실 결제 없이도 프로젝트에 즉시 후원할 수 있도록 **선불 예치금(가상 포인트) 지갑**을 제공한다. 매 후원마다 지갑 잔액을 원자적으로 차감하고, 프로젝트가 목표 미달성 시 자동으로 전원 환불한다. 전자금융업 규제를 회피하면서도 정합성 100%를 비관 락으로 보장한다.

## 배경 및 문제
- 현재 상황 (As-Is)
  - `nbc.c1oud_mall.point.*` 컨텍스트 — 잔액(`PointAccount.balance`) · 이력(`PointHistory`) 존재
  - 트랜잭션 타입: `USE · EARN · USE_CANCEL · EARN_CANCEL` 4종만 (결제 확정 흐름 부수효과용)
  - 잔액 동시성 제어 없음 (락 미도입 · 낙관 락 여부도 명시 X)
  - `WalletService`·충전 흐름 부재
- 발생하는 문제
  - 크라우드 펀딩 컨셉 재정의(0.0.2v)에서 후원자가 프로젝트마다 PortOne 결제 창을 띄우는 UX는 부담 (한 사용자가 하루 5개 프로젝트 후원 시 결제 5회)
  - 실 결제 이체 시 **전자금융업 규제** 리스크 (신입 스코프·법적 검토 부담)
  - 동시성 없이 잔액 갱신 시 race condition (같은 사용자 동시 후원 시 잔액 초과 리스크)
  - 잔액 SSOT 검증 부재 → 이력 합계와 잔액 컬럼 불일치 발생해도 감지 어려움
- 왜 지금 해결해야 하는가
  - 이슈 #10 Pledge(후원)가 지갑 차감을 전제로 설계됨 → Pledge 도입 전 지갑 성립 필수
  - 이슈 #08 Project · #09 Reward Tier가 후원 흐름의 후속이므로 지갑이 없으면 크라우드 펀딩 컨셉 전체가 미완성
  - 초기 트래픽 없는 지금이 락 정책·SSOT 검증을 코드에 굳혀둘 가장 저렴한 시점

## 목표 (To-Be)
- `PointTransactionType` enum에 신규 5종 추가 — `CHARGE · PLEDGE · PLEDGE_REFUND · PLEDGE_CANCEL · SYSTEM_ADJUST`
- `PointAccount` 도메인 메서드 추가 — `charge(amount)` · `pledge(amount)` · `refundPledge(amount)` · `systemAdjust(delta, reason)`
- `PointAccountRepository.findByUserIdForUpdate` — 비관 락 (`SELECT FOR UPDATE`)
- `WalletService` (application layer) — 충전 · 후원 · 환불 · 이력 4개 흐름 유스케이스
- 충전 확정 = 기존 Payment 확정 흐름 재사용 (ADR 002·005 규범 그대로)
- 잔액 SSOT invariant 배치 — 주간 검증 (`balance == SUM(history.amount)`)
- 락 순서 규범 갱신 — `Wallet(PointAccount) → Project → RewardTier → Pledge → Product`

## 설계 결정 (Design Decisions)
> 큰 갈림길의 결정. 거부된 옵션도 합리적 근거가 있었음을 명시.

- **Point 도메인 확장 유지 (신규 Wallet 도메인 신설 X · 리네임 X)**
  - 기존 자산 최대 재활용 · 마이그레이션 부담 0 · 사용자 관점 "포인트"가 익숙
  - 명명 확장(Wallet 개념)은 JavaDoc·문서에 명시
- **비관 락(`SELECT FOR UPDATE`) 채택 · 낙관 락은 병행 안 함**
  - 동시 후원·충전 race 방지 · 정합성 우선
  - 락 hold time 짧음(외부 호출 X) · 데드락 리스크 낮음
- **잔액 SSOT = `PointAccount.balance` 컬럼 + 배치 검증**
  - 실시간은 `balance` 컬럼 · 정합성 검증은 주간 배치가 `SUM(PointHistory.amount)` 대조
  - 불일치 발견 시 알람 · `SYSTEM_ADJUST` 이력으로 수동 보정
- **충전 확정 흐름 = Payment 확정 흐름 재사용**
  - ADR 002(외부 호출 TX 밖) · ADR 005(웹훅 멱등) 규범 그대로
  - 새 코드 최소 · 신입 리뷰 부담 감소
- **락 순서 최상위에 Wallet 배치**
  - `.claude/rules/consitency.md` §5 락 순서 표에 `Wallet` 추가 · 순서: `Wallet → Project → RewardTier → Pledge → Product`
  - 이유: 후원 흐름의 시작이 잔액 차감이므로

## 대안 검토 (Alternatives Considered)

### 도메인 배치
**Option A — Point 컨텍스트 확장 (선택)**
- 비용: `point` 패키지에 "지갑" 개념 혼재 · JavaDoc 명시로 커버
- 보상: 기존 자산 100% 재활용 · 마이그레이션 부담 0

**Option B — 신규 `nbc.c1oud_mall.wallet.*` 컨텍스트 신설**
- 거부 이유: 두 잔액 도메인(point · wallet) 병존 → 정합성·중복 리스크 · 이력 분리 부담

**Option C — `point` 컨텍스트를 `wallet`으로 완전 리네임**
- 거부 이유: 대규모 리네임 · 기존 Payment 확정 흐름 (`pointEarnedAmount` 등 필드명) 파장 큼

### 동시성 제어
**Option A — 비관 락 (`SELECT FOR UPDATE`) (선택)**
- 비용: 커넥션 hold time (외부 호출 없어 짧음)
- 보상: race 방지 확실 · 이해·구현 단순

**Option B — 낙관 락 (`@Version`) + 재시도**
- 거부 이유: 후원 트래픽 몰릴 시 재시도 폭증 · UX 지연 · 사용자 혼란

**Option C — 비관 락 + 낙관 락 병행**
- 거부 이유: 과공학 · 신입 스코프에 오버킬

### 잔액 SSOT
**Option A — `balance` 컬럼 + 배치 SSOT 검증 (선택)**
- 비용: 배치 필요 · 불일치 시 수동 보정 절차 정의
- 보상: 실시간 조회 성능 · 감사 이력 유지

**Option B — 이력 합산만 (컬럼 없음)**
- 거부 이유: 매 조회마다 `SUM(history)` — 이력 증가 시 성능 저하

## 전체 아키텍처 (High-Level Architecture)

### 컴포넌트 배치
```
presentation ──▶ application ──▶ domain ◀── infrastructure
WalletController  WalletService     PointAccount    PointAccountRepository
- balance         - charge()        - charge()      - findByUserIdForUpdate ⭐
- history         - pledge()        - pledge()      PointHistoryRepository
- charge          - refundPledge()  - refundPledge() PointHistoryJpaRepository
- confirm         - getHistory()    - systemAdjust() (Payment 흐름 재사용)
                                    PointHistory
                                    PointTransactionType (5종 추가 · 총 9종)

Payment 확정 흐름 (기존) ──▶ WalletService.charge (충전 확정 진입점)
Pledge 흐름 (이슈 #10) ──▶ WalletService.pledge · refundPledge
```

### 핵심 플로우
**1. 충전 (Charge)**
```
Client → WalletController.charge(amount)
       → PortOne 결제 창 URL 발급 (기존 Payment 재사용)
       ← Response { portonePaymentId, sdkParams }

Client → PortOne SDK 결제 완료
       → WalletController.confirmCharge(portonePaymentId)
       → PaymentConfirmationService.confirm  (기존 · ADR 002·004)
         ├── PortOne 재조회 (TX 밖)
         └── (TX 시작)
             Payment.markCompleted
             WalletService.charge(userId, amount, paymentId) ⭐
               ├── PointAccount FOR UPDATE
               ├── account.charge(amount)
               └── PointHistory INSERT (CHARGE type)
       ← 200 { balance }
```

**2. 후원 (Pledge)**
```
PledgeService.pledge (이슈 #10)
  ├── Project FOR UPDATE (락 순서 2번)
  ├── RewardTier FOR UPDATE (락 순서 3번 · 재고 차감)
  └── WalletService.pledge(userId, amount, projectId, pledgeId)
        ├── PointAccount FOR UPDATE (락 순서 1번 · 실 최상위)
        ├── account.pledge(amount)  → 잔액 부족 시 WALLET_INSUFFICIENT_BALANCE
        └── PointHistory INSERT (PLEDGE type · reference=pledgeId)
```

**3. 프로젝트 실패 · 자동 환불 (Refund)**
```
ProjectClosingScheduler (이슈 #10)
  → PledgeService.closeProject(projectId)
    → 프로젝트 목표 미달성 판정
      → CONFIRMED pledge 목록 조회
        for each pledge:
          WalletService.refundPledge(userId, amount, projectId, pledgeId)
            ├── PointAccount FOR UPDATE
            ├── account.refundPledge(amount)
            └── PointHistory INSERT (PLEDGE_REFUND type)
          pledge.markRefunded()
```

### Out-of-Process 의존
- **PortOne V2 REST API** — 충전 시 Payment 확정 흐름 재사용 · `PortOnePaymentQueryPort`
- **RDS (MySQL)** — `point_account` · `point_history` 영속화 · `SELECT FOR UPDATE`

## 실패 모드 / 운영 관측 (Failure Modes & Observability)

### 실패 시나리오와 응답
| 시나리오 | ErrorCode | HTTP | 클라이언트 권장 동작 |
| --- | --- | --- | --- |
| 잔액 부족 (후원 시) | `WAL002` WALLET_INSUFFICIENT_BALANCE | 409 | 충전 유도 · 부족액 표시 |
| 지갑 없음 (조회 시) | `WAL001` WALLET_NOT_FOUND | 404 | 자동 초기화 or 계정 확인 |
| 유효하지 않은 금액 (0 or 음수) | `WAL003` WALLET_INVALID_AMOUNT | 400 | 폼 재입력 |
| 충전 확정 실패 (Payment 흐름) | `WAL004` WALLET_CHARGE_FAILED | 500 | 재시도 · 문의 안내 |
| 락 획득 실패 (타임아웃) | `C002` INTERNAL_ERROR | 500 | 재시도 |
| SSOT 불일치 감지 (배치) | (내부 알람) | - | 운영 대응 · `SYSTEM_ADJUST` 이력 수동 반영 |

### 로깅 정책
- **항상 기록**:
  - `requestId` · `userId` · `amount` · `transactionType` · 잔액 before/after · `referenceId` (paymentId · pledgeId)
- **debug**: 락 획득 시각 · 락 해제 시각 (성능 분석)
- **절대 금지**:
  - PortOne accessToken · 카드번호 · 개인정보 원문

### 관측 지표
- `wallet.charge.total{result=success|fail}` — counter — 충전 성공/실패
- `wallet.pledge.total{result=success|insufficient|fail}` — counter — 후원 처리 결과
- `wallet.refund.total{reason=project_failed|user_cancelled}` — counter — 환불 원인 분포
- `wallet.balance.check.total{result=match|mismatch}` — counter — SSOT 배치 결과
- `wallet.lock.duration_seconds` — histogram — 비관 락 hold time (성능 지표)

## 롤아웃 / 마이그레이션 (Rollout)

### 전제
- 초기 트래픽 낮음 · 기존 Point 실 사용자 없음 (컨셉 재정의 시점)
- 일괄 배포 (단일 프로파일 전환)
- 기존 4개 트랜잭션 타입(USE · EARN 등)은 유지 · 신규 5종 추가만

### Product 의존성
- 선행: **Payment Product** (충전 확정 흐름 재사용)
- 후행: **Project Product** · **Reward Tier Product** · **Pledge Product** — 후원 흐름의 핵심 참조자

### Epic·Story 의존성 그래프
```
Epic 1 (도메인 확장) ──► Epic 2 (Service 흐름) ──► Epic 3 (Controller REST)
                                    │
                                    └─► Epic 4 (정합성·관측)
```

### 환경별 설정 분기
| 항목 | dev (H2) | prod (RDS MySQL) |
| --- | --- | --- |
| DataSource | H2 in-memory | RDS endpoint |
| `SELECT FOR UPDATE` 지원 | H2 MySQL 호환 모드 | 완전 지원 |
| SSOT 배치 cron | 매일 (테스트 편의) | 매주 월요일 새벽 4시 |
| 초기 잔액 (DummyDataInit) | 사용자별 10,000 포인트 | 없음 (실 사용자만) |

## 성공 지표 (KPI)
| 지표 | 목표 값 | 측정 방법 |
| --- | --- | --- |
| 잔액 SSOT 정합성 (balance = SUM(history)) | 100% | 주간 배치 결과 · `wallet.balance.check.total{result=mismatch}` == 0 |
| 충전 성공률 | ≥ 99% (PortOne 장애 제외) | `wallet.charge.total{result=success}` / 전체 |
| 후원 흐름 잔액 부족 오류율 | ≤ 5% (사용자 UX 지표) | `wallet.pledge.total{result=insufficient}` / 전체 |
| 비관 락 hold time P95 | ≤ 100ms | `wallet.lock.duration_seconds` P95 |
| 프로젝트 실패 배치 환불 누락 | 0건 | 배치 완료 후 CONFIRMED pledge 잔여 조회 · 항상 0 |

## Scope
**In Scope**:
- `PointTransactionType` 5종 추가 (`CHARGE · PLEDGE · PLEDGE_REFUND · PLEDGE_CANCEL · SYSTEM_ADJUST`)
- `PointAccount` 도메인 메서드 4개 (`charge · pledge · refundPledge · systemAdjust`)
- `PointAccountRepository.findByUserIdForUpdate` (비관 락)
- `WalletService` — 충전 · 후원 · 환불 · 이력 조회 4개 유스케이스
- 충전 확정 = Payment 확정 흐름 재사용 (기존 코드 확장만)
- 잔액 SSOT invariant 주간 배치 + 알람
- REST 4개 엔드포인트 (balance · history · charge · confirm)
- 관측 지표 5개
- `ErrorCode.WAL001~004` 등록
- 락 순서 규범 갱신 (`.claude/rules/consitency.md` §5)

**Out of Scope**:
- **인출(`WITHDRAW`)** — 실 자금 인출은 전자금융업 규제 · v0.0.5+ 별도 Product
- **다중 통화·환율** — 원화 정수 포인트만
- **정산 리포트** — 관리자 정산 UI는 v0.0.5+ (메이커·후원자 세금 리포트 등)
- **선물·양도** — 사용자 간 지갑 이체는 스코프 밖 (자금세탁 리스크)
- **크레딧카드 자동 재충전** — 사용자 명시 충전만
- **관리자 SYSTEM_ADJUST UI** — 초기엔 SQL 수동 · v0.0.4+ 관리자 API

## 대상 사용자
- **후원자 (Backer)** — 지갑 충전 후 프로젝트에 후원 · 프로젝트 실패 시 자동 환불받음
- **메이커 (Maker)** — v0.0.5+에 프로젝트 성공 시 지갑 수령 (인출은 별도 Product)
- **운영자** — SSOT 불일치 알람 대응 · `SYSTEM_ADJUST` 이력 수동 반영
- **개발/QA** — Payment 확정 흐름 재사용 검증 · 동시성 통합 테스트

## 연결된 Epic 목록
- [ ] Epic 1: `PointTransactionType` 확장 + `PointAccount` 도메인 메서드 + 비관 락 리포지터리
- [ ] Epic 2: `WalletService` 흐름 (충전·후원·환불·이력) + Payment 흐름 재사용
- [ ] Epic 3: `WalletController` REST API 4개 + 충전 확정 통합
- [ ] Epic 4: 잔액 SSOT invariant 배치 + 관측 지표 + ADR

## 관련 문서
- **선행 Product**: `product-payment.md` (충전 확정 흐름 재사용) · `product-refund.md` (참조 · 환불 규범)
- **후행 Product**: `product-project.md` · `product-reward.md` · `product-pledge.md` (M3 이후 작성)
- **원본 이슈**: `workflows/task/fix/brainstorming/version/0.0.2v/issue-07-wallet-prepaid-balance.md`
- **관련 이슈**: `issue-08-project-domain.md` · `issue-09-reward-tier.md` · `issue-10-pledge-state-machine.md`
- **관련 ADR (기존 재사용)**: ADR 002 (`payment-confirmation-side-effects-order`) · ADR 004 (`payment-confirmation-validation-order`) · ADR 005 (`webhook-idempotency-insert-first`)
- **신규 ADR 후보**: "Wallet 잔액 SSOT · 비관 락 · 이력 검증 invariant" · "락 순서 최상위에 Wallet 배치"
- **규범 갱신 예정**:
  - `.claude/rules/consitency.md` §5 락 순서 표에 Wallet 추가
  - `.claude/rules/idempotency.md` §2 카탈로그에 "지갑 충전" 항목 (Payment 확정과 동일 규범 재사용)
- **CLAUDE.md 참조**: §4 (ApiResponse) · §8 (BusinessException + ErrorCode) · `.claude/rules/persistence.md` (JPA 규범)
- **backend-boundary**: `workflows/backend-boundary/error-codes.md` WAL001~004 매핑 추가

## 열린 질문 (Open Questions)
- **잔액 SSOT 배치 주기 최종 결정** — 매주 vs 매일? (초기엔 매주 · 오류 발생 시 매일로 좁힘)
- **`WITHDRAW` 도입 시점** — v0.0.5+ 예정 · 실 자금 이동 = 전자금융업 규제 검토 필요 · 트리거는 "메이커가 프로젝트 성공 후 지갑 잔액을 실 계좌로 이체 요청"
- **관리자 `SYSTEM_ADJUST` API 노출 시점** — 초기 SQL 수동 · v0.0.4+ 별도 관리자 도메인과 함께
- **초기 무료 지급(가입 축하) 정책** — 신규 가입 시 10,000 포인트 지급할지? (DummyDataInit에는 개발용 · 실 사용자 정책 미정)
- **PointAccount 자동 초기화 시점** — User 생성 시 자동 or 지갑 첫 조회 시 lazy? (Lazy 채택 시 findByUserIdForUpdate에 orElseGet 필요)

## 제품 수준 완료 기준 (Product-level DoD)
- [ ] 모든 Epic DoD 통과
- [ ] E2E 통합 테스트: 충전 → 후원 → 프로젝트 실패 → 자동 환불 (잔액 원상 복구 확인)
- [ ] 동시성 통합 테스트: 같은 사용자 동시 충전·후원 5회 → 잔액 정확성 100%
- [ ] 잔액 SSOT invariant 배치 통과 (`balance = SUM(history)` 100%)
- [ ] `.claude/rules/consitency.md` §5 락 순서 표 갱신 완료
- [ ] `.claude/rules/idempotency.md` §2 카탈로그 갱신 완료
- [ ] `workflows/backend-boundary/error-codes.md` WAL001~004 매핑 완료
- [ ] ADR 최소 1건 발행 (Wallet 잔액 SSOT · 락 정책)
- [ ] 관측 지표 5개 프로덕션 노출 · Grafana 또는 로그 검색 가능

---

# [Epic 1] `PointTransactionType` 확장 + `PointAccount` 도메인 강화

## 목표
`PointTransactionType` enum에 크라우드 펀딩 5종 트랜잭션을 추가하고, `PointAccount`에 지갑 관점 도메인 메서드(`charge · pledge · refundPledge · systemAdjust`) + 비관 락 리포지터리를 도입하여 후속 Epic 2(Service 흐름)의 기반을 마련한다.

## 배경
- 현재 `PointTransactionType`은 4종만 (USE · EARN · USE_CANCEL · EARN_CANCEL) · 결제 확정 흐름 부수효과 전용
- `PointAccount`는 잔액 조회·저장만 · 도메인 메서드 부재
- 동시성 락 미도입 상태
- Epic 2 이하 모든 흐름의 전제

## 포함 Story
- Story 1-1: `PointTransactionType` enum 5종 확장 (CHARGE · PLEDGE · PLEDGE_REFUND · PLEDGE_CANCEL · SYSTEM_ADJUST)
- Story 1-2: `PointAccount` 도메인 메서드 4개 추가 (`charge · pledge · refundPledge · systemAdjust`) + `@Version` 유지 여부 결정
- Story 1-3: `PointAccountRepository.findByUserIdForUpdate` 비관 락 쿼리 추가

## Epic 인수 시나리오
- Given `PointAccount(balance=10000)` 존재
- When `account.pledge(3000)` 호출
- Then `balance == 7000` · 예외 없음

*(엣지)* Given `PointAccount(balance=1000)` · When `account.pledge(3000)` · Then `BusinessException(WAL002)` · 잔액 미변경

## Epic 완료 기준 (DoD)
- [ ] 포함 Story 3개 모두 완료
- [ ] 단위 테스트: 4개 도메인 메서드 × (성공 · 잔액 부족 · 유효하지 않은 금액) 매트릭스
- [ ] `@DataJpaTest` 슬라이스: `findByUserIdForUpdate` 락 획득 검증 (H2 MySQL 호환 모드)
- [ ] 신규 ADR 후보 초안: "PointAccount 락 정책 (비관 · balance SSOT)"

---

## [Story 1-1] `PointTransactionType` enum 5종 확장

### User Story
- As a Wallet 도메인 개발자
- I want 크라우드 펀딩 흐름에 필요한 트랜잭션 타입(CHARGE · PLEDGE · PLEDGE_REFUND · PLEDGE_CANCEL · SYSTEM_ADJUST)을 enum에 추가
- so that 이력 저장 시 타입별 필터·집계·감사 가능

### 설명
- 기존: `USE · EARN · USE_CANCEL · EARN_CANCEL` (결제 확정 부수효과)
- 신규 추가:
  - `CHARGE` — PortOne 충전으로 잔액 증가
  - `PLEDGE` — 프로젝트 후원 (지갑 → 프로젝트 pending)
  - `PLEDGE_REFUND` — 프로젝트 실패 · 자동 환불 (프로젝트 → 지갑)
  - `PLEDGE_CANCEL` — 후원자 명시 취소 (LIVE 중)
  - `SYSTEM_ADJUST` — 관리자 수동 조정 (SSOT 불일치 · CS 대응)
- **DB 컬럼 확장 필요**: `point_history.transaction_type VARCHAR(20)` → `VARCHAR(30)` (신규 이름이 15자 이내이므로 20자 유지 가능 · 확인)

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.point.domain.PointTransactionType` (enum · 4종 → 9종)

**주요 메서드**:
- 기존 enum 값 유지 · 신규 5종만 append (`@Enumerated(STRING)` 저장 방식이므로 순서 무관)

### 완료 기준 (AC)
- Given 기존 코드 · When enum 5종 추가 · Then 컴파일 성공 · 기존 결제 확정 흐름 회귀 없음
- Given `PointHistory(transactionType=CHARGE)` 저장 · When JPA persist · Then `transaction_type='CHARGE'` 컬럼에 저장
- *(엣지 · DB 컬럼 크기)* Given 컬럼 `VARCHAR(20)` · When `PLEDGE_REFUND` 저장 · Then 정상 (13자 · 여유)
- *(예외 · 존재하지 않는 값)* Given `transaction_type='FOO'` (DB 수동 삽입) · When 조회 · Then `IllegalArgumentException` (JPA 표준 동작)

### Definition of Done
- [ ] `PointTransactionType` enum 5종 추가 (`src/main/java/nbc/c1oud_mall/point/domain/PointTransactionType.java`)
- [ ] 단위 테스트 (신규 5종이 enum에 존재하는지 · 이름 정확성 · 총 9개)
- [ ] 기존 결제 확정 흐름 통합 테스트 회귀 검증 (`PaymentConfirmationServiceIntegrationTest` 실행)
- [ ] `.claude/rules/exception.md` 갱신 불필요 (enum 확장이지 ErrorCode 아님)

### 스토리 포인트
0.5d

### 의존성
- 선행: 없음
- 후행: Story 1-2 · 1-3 · Epic 2 전체

---

## [Story 1-2] `PointAccount` 도메인 메서드 4개 추가

### User Story
- As a Wallet 도메인 개발자
- I want `PointAccount`에 지갑 관점 도메인 메서드(`charge · pledge · refundPledge · systemAdjust`)를 추가
- so that 서비스 계층에서 잔액 증감을 도메인 규칙(잔액 부족·음수 방지)과 함께 원자적으로 수행

### 설명
- 4개 도메인 메서드 · 각각 잔액 증감 + 유효성 검증 + 도메인 예외 throw
- 신규 예외: `BusinessException(ErrorCode.WAL001~003)`
- `@Version` (낙관 락) 도입 여부: **미도입** (비관 락 채택 · Story 1-3)
- 잔액 SSOT는 `balance` 컬럼 · 도메인 메서드는 이 컬럼만 변경 (이력 저장은 Service가 별도로)

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.point.domain.PointAccount` (기존)

**주요 메서드**:
- `PointAccount.charge(long amount)` — 잔액 증가 (0 이하 → `WAL003`)
- `PointAccount.pledge(long amount)` — 잔액 감소 (부족 → `WAL002`)
- `PointAccount.refundPledge(long amount)` — 잔액 증가 (0 이하 → `WAL003`)
- `PointAccount.systemAdjust(long delta, String reason)` — 잔액 조정 (음수 delta 허용 · 감소도 가능 · 최종 잔액 < 0 → `WAL002`)

```java
public void charge(long amount) {
    if (amount <= 0) throw new BusinessException(ErrorCode.WAL003);
    this.balance += amount;
    this.lastChargedAt = LocalDateTime.now();
}

public void pledge(long amount) {
    if (amount <= 0) throw new BusinessException(ErrorCode.WAL003);
    if (this.balance < amount) throw new BusinessException(ErrorCode.WAL002);
    this.balance -= amount;
}

public void refundPledge(long amount) {
    if (amount <= 0) throw new BusinessException(ErrorCode.WAL003);
    this.balance += amount;
}

public void systemAdjust(long delta, String reason) {
    long newBalance = this.balance + delta;
    if (newBalance < 0) throw new BusinessException(ErrorCode.WAL002);
    this.balance = newBalance;
    // reason은 Service에서 PointHistory.description에 기록
}
```

### 완료 기준 (AC)
- Given `PointAccount(balance=10000)` · When `charge(5000)` · Then `balance == 15000` · `lastChargedAt` 갱신
- Given `PointAccount(balance=10000)` · When `pledge(3000)` · Then `balance == 7000`
- *(예외 · 잔액 부족)* Given `PointAccount(balance=1000)` · When `pledge(3000)` · Then `BusinessException(WAL002)` · 잔액 유지
- *(예외 · 유효하지 않은 금액)* Given `PointAccount(balance=10000)` · When `charge(0)` · Then `BusinessException(WAL003)`
- *(엣지 · systemAdjust 감소)* Given `PointAccount(balance=10000)` · When `systemAdjust(-3000, "CS 보상")` · Then `balance == 7000`
- *(엣지 · systemAdjust 음수 결과)* Given `PointAccount(balance=1000)` · When `systemAdjust(-3000, "감액")` · Then `BusinessException(WAL002)` · 잔액 유지

### Definition of Done
- [ ] 4개 도메인 메서드 구현 (`src/main/java/nbc/c1oud_mall/point/domain/PointAccount.java`)
- [ ] `ErrorCode.WAL001~004` 등록 (`src/main/java/nbc/c1oud_mall/common/exception/ErrorCode.java`)
- [ ] 단위 테스트: 각 메서드 × (성공 · 잔액 부족 · 유효하지 않은 금액 · 엣지) = 12+ 케이스
- [ ] 도메인 예외는 `BusinessException` + `ErrorCode` (커스텀 예외 클래스 금지 · CLAUDE.md §8)

### 스토리 포인트
1d

### 의존성
- 선행: Story 1-1
- 후행: Story 1-3 · Epic 2 전체

---

## [Story 1-3] `PointAccountRepository.findByUserIdForUpdate` 비관 락 쿼리

### User Story
- As a Wallet 서비스 개발자
- I want `PointAccountRepository`에 비관 락(`SELECT FOR UPDATE`) 조회 메서드를 추가
- so that 동시 충전·후원 시 잔액 race 방지

### 설명
- Spring Data JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)` 활용
- 트랜잭션 안에서만 사용 · 트랜잭션 밖 호출 시 `TransactionRequiredException`
- H2도 MySQL 호환 모드에서 `SELECT FOR UPDATE` 문법 지원 (통합 테스트 대상)

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.point.infrastructure.PointAccountRepository` (기존 · 메서드 추가)

**주요 메서드**:
```java
public interface PointAccountRepository extends JpaRepository<PointAccount, Long> {
    Optional<PointAccount> findByUserId(Long userId);   // 기존

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pa FROM PointAccount pa WHERE pa.userId = :userId")
    Optional<PointAccount> findByUserIdForUpdate(@Param("userId") Long userId);   // 신규
}
```

### 완료 기준 (AC)
- Given `PointAccount(userId=1, balance=10000)` 저장됨 · When `findByUserIdForUpdate(1)` 호출 (트랜잭션 안) · Then Optional non-empty · 락 획득 확인
- *(엣지 · 없는 사용자)* Given `userId=999` 미존재 · When 호출 · Then `Optional.empty()`
- *(예외 · TX 밖 호출)* Given 트랜잭션 없음 · When 호출 · Then `TransactionRequiredException` (JPA 표준)
- *(동시성)* Given 두 트랜잭션 동시 진입 · Then 두 번째 트랜잭션은 첫 번째 커밋까지 대기 · 락 hold time 측정 가능

### Definition of Done
- [ ] `findByUserIdForUpdate` 메서드 추가
- [ ] `@DataJpaTest` 슬라이스 테스트: 락 획득 · Optional 반환 정확성
- [ ] 동시성 통합 테스트: 2개 스레드에서 동시 호출 → 순차 처리 확인 (`@SpringBootTest` + `CountDownLatch`)
- [ ] H2 MySQL 호환 모드 검증 (`application-test.yml`에 `MODE=MySQL` 설정)

### 스토리 포인트
1d

### 의존성
- 선행: Story 1-2
- 후행: Epic 2 전체 (`WalletService`가 이 메서드 사용)

---

# [Epic 2] `WalletService` 흐름 (충전 · 후원 · 환불 · 이력)

## 목표
`WalletService`를 신규 도입하여 4개 유스케이스(충전 · 후원 · 환불 · 이력 조회)를 제공하고, 충전은 기존 Payment 확정 흐름을 재사용하며, 후원·환불은 이슈 #10 Pledge에서 호출되는 통합 진입점이 된다.

## 배경
- Epic 1이 도메인·리포지터리를 갖췄으므로, 이제 application layer에서 유스케이스 조합
- 충전 확정은 새 코드 없이 Payment 흐름 확장만 (ADR 002·004·005 재사용)
- 후원/환불은 이슈 #10 Pledge에서 호출 · Wallet에서 노출

## 포함 Story
- Story 2-1: `WalletService.charge` — Payment 확정 흐름 확장 (외부 호출 진입점은 Payment · Wallet은 부수효과)
- Story 2-2: `WalletService.pledge` — 후원 시 잔액 차감 + 이력 저장 (이슈 #10에서 호출)
- Story 2-3: `WalletService.refundPledge` — 프로젝트 실패 배치에서 호출 (이슈 #10에서 호출)
- Story 2-4: `WalletService.getHistory` — 이력 조회 (타입 필터 · 페이징)

## Epic 인수 시나리오
- Given `userId=1` PortOne 충전 확정 (10,000원) 흐름
- When `WalletService.charge(1, 10000, paymentId=42)` 호출 (Payment 확정 TX 안 부수효과)
- Then `PointAccount.balance += 10000` · `PointHistory` INSERT (CHARGE · reference=42) · 같은 TX

*(엣지)* Given 잔액 3000 · When `pledge(1, 5000, ...)` · Then `WAL002` · 잔액 유지

## Epic 완료 기준 (DoD)
- [ ] 4개 Story 완료
- [ ] 통합 테스트: 충전 → 잔액 증가 · 이력 저장 원자성
- [ ] 통합 테스트: 후원 · 실패 시나리오 (잔액 부족)
- [ ] 통합 테스트: 환불 배치 (다수 pledge 환불 원자성)
- [ ] ADR 001 (Wallet 잔액 SSOT · 락 정책) 초안 작성

## Epic 기술 결정 / 대안 (Epic-Level Alternatives)
- **잔액 검증 위치**: `PointAccount.pledge` 도메인 메서드 안 (Service는 조합만) — 도메인 응집성 우선
- **이력 저장 시점**: Service가 도메인 메서드 호출 후 이력 저장 · 도메인은 이력 저장 안 함 (도메인 순수성)

---

## [Story 2-1] `WalletService.charge` — Payment 확정 흐름 확장

### User Story
- As a Payment 확정 서비스
- I want 결제 확정 TX 안에서 `WalletService.charge`를 부수효과로 호출
- so that 사용자 지갑에 원자적으로 충전 반영

### 설명
- Payment 확정 흐름의 부수효과 (`PaymentConfirmationService.confirm` 안에서 호출)
- 락 순서 최상위: `Wallet FOR UPDATE → 도메인 charge · 이력 저장`
- 이력의 `referenceId`는 Payment id (감사 추적용)
- 실패 시 상위 Payment TX 전체 롤백 (원자성)

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.point.application.WalletService` (신규)
- `PaymentConfirmationService` (기존 · 호출 추가)

**주요 메서드**:
```java
@Service
@RequiredArgsConstructor
@Transactional
public class WalletService {
    private final PointAccountRepository accountRepository;
    private final PointHistoryRepository historyRepository;

    @Transactional   // 상위 TX 참여 · REQUIRED
    public void charge(Long userId, long amount, Long paymentId) {
        PointAccount account = accountRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> accountRepository.save(PointAccount.create(userId)));

        long balanceBefore = account.getBalance();
        account.charge(amount);   // 도메인 메서드 · 유효성 검증

        historyRepository.save(PointHistory.of(
            userId, amount, PointTransactionType.CHARGE,
            paymentId != null ? paymentId.toString() : null,
            balanceBefore, account.getBalance()
        ));
    }
}
```

### 완료 기준 (AC)
- Given `userId=1` 미존재 (신규 사용자) · When `charge(1, 10000, 42)` · Then `PointAccount` 자동 생성 · balance=10000 · 이력 저장
- Given `userId=1` balance=5000 · When `charge(1, 3000, 42)` · Then balance=8000 · 이력 저장 (`transactionType=CHARGE · reference_id='42'`)
- *(예외 · 유효하지 않은 금액)* Given `userId=1` · When `charge(1, 0, 42)` · Then `BusinessException(WAL003)` · 상위 TX 롤백
- *(엣지 · 락 대기)* Given 두 트랜잭션 동시 charge · Then 두 번째는 첫 번째 커밋 대기 · 최종 balance 정확

### Definition of Done
- [ ] `WalletService.charge` 구현 (`src/main/java/nbc/c1oud_mall/point/application/WalletService.java`)
- [ ] `PaymentConfirmationService`에 `walletService.charge` 호출 추가 (지갑 충전 결제인 경우에만 · 결제 목적 구분 필요 · 별도 검토)
- [ ] 통합 테스트: `PaymentConfirmationService` E2E — 충전 결제 완료 시 잔액 반영 확인
- [ ] 동시성 통합 테스트: 같은 사용자 동시 충전 5회 → 최종 balance 정확

### 스토리 포인트
2d

### 의존성
- 선행: Epic 1 전체 · `PaymentConfirmationService` 확장 협의
- 후행: Story 3-3 (Controller `POST /wallets/charge`), Story 3-4 (`confirm`)

### [명세 변경 이력]
- (초안 · 실 구현 시 추가)

---

## [Story 2-2] `WalletService.pledge` — 후원 시 잔액 차감

### User Story
- As a Pledge 서비스 (이슈 #10)
- I want 후원 생성 시 `WalletService.pledge`를 호출하여 지갑 잔액을 원자적으로 차감
- so that 프로젝트 후원 흐름의 자금 이동을 지갑에서 안전하게 처리

### 설명
- 이슈 #10의 `PledgeService.pledge` 흐름 안에서 호출
- 락 순서: `Wallet(1) → Project(2) → RewardTier(3) → Pledge(4)`
- 잔액 부족 시 `WAL002` throw · 상위 TX 롤백 (Project·RewardTier 락 해제)
- 이력 `referenceId`는 pledge id (Pledge 저장 후 id 확보 시점 필요 · Service 순서 조율)

**핵심 클래스/인터페이스**:
- `WalletService.pledge(Long userId, long amount, Long projectId, Long pledgeId)`

**주요 메서드**:
```java
@Transactional
public void pledge(Long userId, long amount, Long projectId, Long pledgeId) {
    PointAccount account = accountRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.WAL001));

    long balanceBefore = account.getBalance();
    account.pledge(amount);   // 잔액 부족 시 WAL002

    historyRepository.save(PointHistory.of(
        userId, -amount, PointTransactionType.PLEDGE,
        pledgeId != null ? "pledge-" + pledgeId : "project-" + projectId,
        balanceBefore, account.getBalance()
    ));
}
```

### 완료 기준 (AC)
- Given balance=10000 · When `pledge(1, 3000, 100, 500)` · Then balance=7000 · 이력 `-3000`
- *(예외 · 잔액 부족)* Given balance=1000 · When `pledge(1, 3000, ...)` · Then `WAL002` · 상위 TX 롤백
- *(예외 · 지갑 미존재)* Given userId=999 · When `pledge(999, ...)` · Then `WAL001`

### Definition of Done
- [ ] `WalletService.pledge` 구현
- [ ] 이슈 #10 `PledgeService.pledge` 흐름에서 호출 지점 통합 (Project · RewardTier 다음)
- [ ] 통합 테스트: `PledgeServiceIntegrationTest`에서 잔액 차감 검증
- [ ] 동시성: 같은 사용자 동시 후원 2개 프로젝트 → 잔액 race 없음

### 스토리 포인트
1d

### 의존성
- 선행: Epic 1 · Story 2-1
- 후행: 이슈 #10 Pledge 구현

---

## [Story 2-3] `WalletService.refundPledge` — 프로젝트 실패 배치 환불

### User Story
- As a Project Closing Scheduler (이슈 #10)
- I want 프로젝트 실패 시 confirmed pledge 목록을 순회하며 `WalletService.refundPledge` 호출
- so that 후원자 지갑에 원자적으로 환불

### 설명
- 이슈 #10 `PledgeService.closeProject` 안에서 CONFIRMED pledge 순회
- 각 pledge마다 개별 TX or 배치 TX (결정 필요)
- **결정**: 개별 TX (`REQUIRES_NEW`) — 한 pledge 환불 실패가 다른 pledge에 영향 없음 · Failure Isolation
- 이력 `referenceId`는 pledge id
- 실패 시 로그 마커 (`WALLET_REFUND_FAILED`)

**핵심 클래스/인터페이스**:
- `WalletService.refundPledge(Long userId, long amount, Long projectId, Long pledgeId)`

**주요 메서드**:
```java
@Transactional(propagation = REQUIRES_NEW)   // 개별 TX · Failure Isolation
public void refundPledge(Long userId, long amount, Long projectId, Long pledgeId) {
    PointAccount account = accountRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.WAL001));

    long balanceBefore = account.getBalance();
    account.refundPledge(amount);

    historyRepository.save(PointHistory.of(
        userId, amount, PointTransactionType.PLEDGE_REFUND,
        "pledge-" + pledgeId,
        balanceBefore, account.getBalance()
    ));
}
```

### 완료 기준 (AC)
- Given balance=7000 (pledge 3000 이후) · When `refundPledge(1, 3000, 100, 500)` · Then balance=10000 (원상)
- Given 프로젝트 실패 · CONFIRMED pledge 3건 (각 5000) · When 배치 실행 · Then 3건 모두 환불 · 총 15000 반환
- *(엣지 · 개별 실패)* Given 3건 중 2번째 실패 (지갑 유실 등) · Then 1·3번째는 성공 · 2번째만 로그 마커

### Definition of Done
- [ ] `WalletService.refundPledge` 구현 (`REQUIRES_NEW` 명시)
- [ ] 이슈 #10 `PledgeService.closeProject` 배치 흐름에 호출 지점 통합
- [ ] 통합 테스트: 배치 환불 3건 성공 · 중간 실패 시 격리 검증
- [ ] 로그 마커 `WALLET_REFUND_FAILED` 검증

### 스토리 포인트
1d

### 의존성
- 선행: Story 2-2
- 후행: 이슈 #10 Pledge 구현

---

## [Story 2-4] `WalletService.getHistory` — 이력 조회

### User Story
- As a 사용자 (후원자/메이커)
- I want 내 지갑 이력을 타입 필터·페이지로 조회
- so that 충전·후원·환불 내역 확인 가능

### 설명
- 페이징 (Spring Data `Pageable`)
- 타입 필터 (nullable · null이면 전체)
- 정렬: `createdAt DESC` (최신순 기본)
- Response DTO: `PointHistoryResponse` (record · 응답 필드는 id · type · amount · balanceAfter · referenceId · description · createdAt)

**핵심 클래스/인터페이스**:
- `WalletService.getHistory(Long userId, PointTransactionType typeFilter, Pageable pageable)`
- `nbc.c1oud_mall.point.presentation.dto.PointHistoryResponse` (record)

**주요 메서드**:
```java
@Transactional(readOnly = true)
public Page<PointHistoryResponse> getHistory(Long userId, PointTransactionType typeFilter, Pageable pageable) {
    Page<PointHistory> histories = (typeFilter == null)
        ? historyRepository.findByUserId(userId, pageable)
        : historyRepository.findByUserIdAndTransactionType(userId, typeFilter, pageable);
    return histories.map(PointHistoryResponse::from);
}
```

### 완료 기준 (AC)
- Given 이력 5건 · When `getHistory(userId, null, PageRequest.of(0, 10))` · Then Page 5건 · 최신순
- Given 이력 5건 (CHARGE 3 · PLEDGE 2) · When `getHistory(userId, CHARGE, ...)` · Then 3건만
- *(엣지 · 이력 없음)* Given 이력 0건 · When 조회 · Then empty Page (예외 X)

### Definition of Done
- [ ] `WalletService.getHistory` 구현
- [ ] `PointHistoryResponse` record
- [ ] `PointHistoryRepository`에 `findByUserIdAndTransactionType` 메서드 추가
- [ ] 단위 테스트: 필터 · 페이징 · empty

### 스토리 포인트
0.5d

### 의존성
- 선행: Epic 1
- 후행: Story 3-2 (Controller history 엔드포인트)

---

# [Epic 3] `WalletController` REST API + 충전 확정 통합

## 목표
사용자 노출용 REST API 4개(balance · history · charge · confirm)를 제공하고, `POST /wallets/charge` 진입점이 기존 Payment 흐름과 정합되도록 통합한다.

## 배경
- Epic 2가 서비스 유스케이스를 완성했으므로 이제 프레젠테이션 계층
- 충전 시작(`POST /wallets/charge`)은 PortOne 결제 창을 열기 위한 파라미터 반환 · 실 결제 확정은 기존 `PaymentConfirmationService`

## 포함 Story
- Story 3-1: `GET /wallets/me` — 내 잔액 조회
- Story 3-2: `GET /wallets/me/history` — 이력 조회
- Story 3-3: `POST /wallets/charge` — 충전 요청 (PortOne 결제 창 URL 발급)
- Story 3-4: `POST /wallets/charge/confirm` — 충전 확정 (Payment 확정 흐름 호출)

## Epic 인수 시나리오
- Given 인증 사용자 · When `POST /wallets/charge {amount: 10000}` · Then 200 · `{portonePaymentId, sdkParams}` 반환
- When PortOne SDK 결제 완료 후 `POST /wallets/charge/confirm {portonePaymentId}` · Then 200 · `{balance: 10000}` 반환

*(엣지)* Given 미인증 · When 호출 · Then 401 · `C004`

## Epic 완료 기준 (DoD)
- [ ] 4개 엔드포인트 · `@WebMvcTest` 슬라이스 테스트 통과
- [ ] `ResponseEntity<ApiResponse<T>>` 100% 준수 (CLAUDE.md §4)
- [ ] OpenAPI 문서 갱신 (또는 문서 파일 갱신)
- [ ] E2E: 충전 요청 → PortOne 결제 → 확정 → 잔액 반영

---

## [Story 3-1] `GET /api/v1/wallets/me` — 잔액 조회

### User Story
- As a 사용자
- I want 내 지갑 잔액을 조회
- so that 후원 전에 잔액 확인 가능

### 설명
- 인증 필요 (`@AuthenticationPrincipal`)
- Response: `WalletBalanceResponse { balance, lastChargedAt }` (record)
- 지갑 미초기화 시 자동 생성 (또는 balance=0으로 반환 · 결정)
- **결정**: 자동 생성 (lazy init) · 이후 `WalletService.getBalance`가 없으면 `PointAccount.create(userId)` 저장

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.point.presentation.WalletController`
- `WalletBalanceResponse` (record)

### 완료 기준 (AC)
- Given 인증 사용자 · balance=15000 · When `GET /api/v1/wallets/me` · Then 200 · `ApiResponse.success({balance: 15000, ...})`
- Given 인증 사용자 · 지갑 미초기화 · When 호출 · Then 200 · balance=0 · 자동 생성
- *(예외 · 미인증)* Given 미인증 · When 호출 · Then 401 · `ApiResponse.error("C004", ...)`

### Definition of Done
- [ ] `WalletController.getBalance` 구현
- [ ] `WalletBalanceResponse` record
- [ ] `@WebMvcTest` 슬라이스 테스트 (성공 · 미인증 · 자동 생성)
- [ ] `ResponseEntity<ApiResponse<WalletBalanceResponse>>` 반환 준수

### 스토리 포인트
0.5d

### 의존성
- 선행: Epic 2
- 후행: 없음

---

## [Story 3-2] `GET /api/v1/wallets/me/history` — 이력 조회

### User Story
- As a 사용자
- I want 지갑 이력을 타입 필터·페이징으로 조회
- so that 내 충전·후원·환불 내역 확인

### 설명
- Query: `type=CHARGE|PLEDGE|PLEDGE_REFUND|PLEDGE_CANCEL|SYSTEM_ADJUST` (nullable) · `page=0&size=20`
- Response: `Page<PointHistoryResponse>` · `ApiResponse.success(page)` 래핑
- 정렬 화이트리스트: `createdAt DESC` 기본

### 완료 기준 (AC)
- Given 이력 5건 · When `GET /api/v1/wallets/me/history?page=0&size=10` · Then 200 · content 5건 · 최신순
- Given 이력 5건 (CHARGE 3 · PLEDGE 2) · When `?type=CHARGE` · Then 3건만
- *(엣지)* Given size=200 · When 호출 · Then 최대 100으로 자동 제한 (선택 규칙)
- *(예외 · 유효하지 않은 type)* Given `?type=FOO` · When 호출 · Then 400 · `C001` (Validation)

### Definition of Done
- [ ] `WalletController.getHistory` 구현
- [ ] `@WebMvcTest` 슬라이스: 필터 · 페이징 · empty · 유효하지 않은 type
- [ ] `Page<T>` 응답 형태를 `ApiResponse<Page<T>>`로 감쌈 (Response DTO 규범)

### 스토리 포인트
0.5d

### 의존성
- 선행: Epic 2 · Story 2-4
- 후행: 없음

---

## [Story 3-3] `POST /api/v1/wallets/charge` — 충전 요청 (PortOne SDK 파라미터 발급)

### User Story
- As a 사용자
- I want 충전 금액을 입력하고 PortOne 결제 창을 열 파라미터를 받음
- so that 클라이언트가 SDK로 결제 진행

### 설명
- Request: `WalletChargeRequest { amount }` (Bean Validation `@Positive`)
- Response: `WalletChargeResponse { portonePaymentId, orderName, amount, sdkParams }` — PortOne SDK 초기화 파라미터
- 내부 처리:
  - Payment 초기화 흐름 재사용 (`PaymentInitiationService.initiate` · orderId는 지갑 충전용 가상 orderId or null)
  - 결제 목적을 `WALLET_CHARGE`로 구분 (Payment 도메인 확장 필요 여부 검토 · 별도 이슈 #16 후보)
- 확정은 별도 엔드포인트 (Story 3-4)

**핵심 클래스/인터페이스**:
- `WalletController.charge`
- `WalletChargeRequest` · `WalletChargeResponse` (record)

### 완료 기준 (AC)
- Given 인증 사용자 · amount=10000 · When `POST /wallets/charge` · Then 200 · portonePaymentId 채번 · SDK 파라미터 반환
- *(예외 · 유효하지 않은 금액)* Given amount=0 or -1 · When 호출 · Then 400 · `C001` (`@Positive` 위반)
- *(예외 · 미인증)* Given 미인증 · When 호출 · Then 401

### Definition of Done
- [ ] `WalletController.charge` 구현
- [ ] Request/Response DTO
- [ ] Payment 초기화 흐름 확장 or 재사용 (별도 검토 · 이슈 후보)
- [ ] `@WebMvcTest` 슬라이스 테스트

### 스토리 포인트
1d

### 의존성
- 선행: Epic 2 · Payment Product 초기화 흐름
- 후행: Story 3-4

### [명세 변경 이력]
- (초안 · Payment 흐름 확장 관련 결정 시 기록)

---

## [Story 3-4] `POST /api/v1/wallets/charge/confirm` — 충전 확정

### User Story
- As a 사용자
- I want PortOne SDK 결제 완료 후 서버 확정 API를 호출하여 잔액 반영 확인
- so that 충전 완료 확인 · 이후 후원 가능

### 설명
- Request: `WalletChargeConfirmRequest { portonePaymentId }`
- 내부: 기존 `PaymentConfirmationService.confirm` 호출 (Payment 확정 흐름 재사용)
- Payment 확정 성공 시 부수효과로 `WalletService.charge` 호출 (Story 2-1)
- Response: `WalletBalanceResponse { balance }` (충전 후 잔액)

### 완료 기준 (AC)
- Given PortOne 결제 완료 · portonePaymentId 유효 · When `POST /wallets/charge/confirm` · Then 200 · balance 반영
- *(예외 · 금액 불일치)* Given PortOne 승인 금액과 서버 pgAmount 다름 · When 확정 · Then 400 · `PAY001` (Payment 흐름)
- *(엣지 · 이미 확정)* Given 이미 COMPLETED · When 재확정 · Then 200 · 잔액 그대로 (멱등)

### Definition of Done
- [ ] `WalletController.confirmCharge` 구현
- [ ] Request DTO
- [ ] `PaymentConfirmationService`가 `WALLET_CHARGE` 목적 결제 시 `WalletService.charge` 호출
- [ ] E2E 통합 테스트: 충전 요청 → PortOne → 확정 → 잔액 반영

### 스토리 포인트
1d

### 의존성
- 선행: Story 3-3 · Epic 2 Story 2-1
- 후행: 없음

---

# [Epic 4] 잔액 SSOT invariant 배치 + 관측 + ADR

## 목표
잔액 SSOT 정합성을 주간 배치로 자동 검증하고, 관측 지표를 프로덕션에 노출하며, 락 정책·SSOT 규범을 ADR로 굳혀 후속 Product(Project · Pledge)에서 참조 가능한 상태로 만든다.

## 배경
- Epic 1~3이 실 흐름을 완성했으므로 이제 운영 안전망 · 규범 문서화
- Log Product(0.0.2v 계획)와 병행 진행 (관측 지표 노출)

## 포함 Story
- Story 4-1: 잔액 SSOT invariant 배치 스케줄러 (주간)
- Story 4-2: Micrometer 관측 지표 5종 등록
- Story 4-3: ADR 작성 · CLAUDE.md 락 순서 갱신 · `backend-boundary/error-codes.md` 갱신

## Epic 완료 기준 (DoD)
- [ ] SSOT 배치 실 실행 · dev 환경 검증
- [ ] 5개 지표 Prometheus/Actuator 노출 확인
- [ ] ADR 1건 이상 발행
- [ ] 규범 문서 3건 갱신 완료

---

## [Story 4-1] 잔액 SSOT invariant 배치 스케줄러

### User Story
- As a 운영자
- I want 주간 배치로 `PointAccount.balance == SUM(PointHistory.amount WHERE user_id)` 정합성 자동 검증
- so that SSOT 불일치 발견 시 즉시 알람 · 수동 보정 절차 진입

### 설명
- `@Scheduled(cron = "0 0 4 * * MON")` (매주 월요일 새벽 4시)
- 모든 활성 사용자 순회 · `SUM(history.amount)` vs `account.balance` 비교
- 불일치 사용자는 로그 마커 (`WALLET_SSOT_MISMATCH userId={} expected={} actual={} delta={}`)
- 알람: 로그 마커 기반 (초기) · Grafana/CloudWatch Alert (v0.0.3+)

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.point.infrastructure.WalletInvariantScheduler`

**주요 메서드**:
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class WalletInvariantScheduler {
    private final PointAccountRepository accountRepository;
    private final PointHistoryRepository historyRepository;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "0 0 4 * * MON")
    public void verifySsot() {
        long totalChecked = 0;
        long totalMismatched = 0;

        for (PointAccount account : accountRepository.findAll()) {
            long historySum = historyRepository.sumAmountByUserId(account.getUserId());
            if (historySum != account.getBalance()) {
                log.error("WALLET_SSOT_MISMATCH userId={} expected={} actual={} delta={}",
                    account.getUserId(), historySum, account.getBalance(),
                    account.getBalance() - historySum);
                totalMismatched++;
            }
            totalChecked++;
        }

        meterRegistry.counter("wallet.balance.check.total", "result", "match")
            .increment(totalChecked - totalMismatched);
        meterRegistry.counter("wallet.balance.check.total", "result", "mismatch")
            .increment(totalMismatched);
    }
}
```

### 완료 기준 (AC)
- Given 사용자 100명 · 모두 정합 · When 배치 실행 · Then `mismatch=0` · 로그 마커 없음
- Given 사용자 100명 · 1명 불일치 (수동 조작) · When 배치 실행 · Then `mismatch=1` · 로그 마커 1건
- *(엣지 · 사용자 없음)* Given 사용자 0명 · When 배치 · Then 정상 종료 · counter 0

### Definition of Done
- [ ] `WalletInvariantScheduler` 구현
- [ ] `PointHistoryRepository.sumAmountByUserId` 쿼리 추가 (`@Query("SELECT SUM(h.amount) FROM PointHistory h WHERE h.userId = :userId")`)
- [ ] 통합 테스트: 정합 100% · 불일치 1건 시 로그 마커
- [ ] dev 환경에서 실 스케줄러 실행 검증 (`ApplicationRunner`로 수동 트리거)

### 스토리 포인트
1d

### 의존성
- 선행: Epic 1
- 후행: 없음

---

## [Story 4-2] Micrometer 관측 지표 5종 등록

### User Story
- As a 운영자
- I want Wallet 관련 5개 지표가 프로덕션에서 노출됨
- so that Grafana 또는 로그 검색으로 상시 관측 · 이상 감지

### 설명
- 5개 지표 (§실패 모드 · §KPI):
  - `wallet.charge.total{result=success|fail}` counter
  - `wallet.pledge.total{result=success|insufficient|fail}` counter
  - `wallet.refund.total{reason=project_failed|user_cancelled}` counter
  - `wallet.balance.check.total{result=match|mismatch}` counter (Story 4-1)
  - `wallet.lock.duration_seconds` histogram
- Micrometer `MeterRegistry` 주입 · `WalletService` 안에서 `counter.increment()` 호출

### 완료 기준 (AC)
- Given 충전 성공 · When `WalletService.charge` 실행 · Then `wallet.charge.total{result=success}` 1 증가
- Given 후원 잔액 부족 · When `WalletService.pledge` 실행 · Then `wallet.pledge.total{result=insufficient}` 1 증가
- Given Prometheus 엔드포인트 · When `curl /actuator/prometheus` · Then 5개 지표 노출 확인 (`wallet.*`)

### Definition of Done
- [ ] `WalletService`·`WalletInvariantScheduler`에 `MeterRegistry` 주입 · counter 호출
- [ ] Timer/Histogram 관측 (`wallet.lock.duration_seconds`) — `Timer.record()` 사용
- [ ] `application.yml`에 `management.endpoints.web.exposure.include=health,info,prometheus` 확인
- [ ] 통합 테스트: 지표 값 검증 (`meterRegistry.get("wallet.charge.total").counter().count()`)

### 스토리 포인트
1d

### 의존성
- 선행: Epic 2·3
- 후행: 없음

---

## [Story 4-3] ADR 작성 · 규범 문서 3건 갱신

### User Story
- As a 팀 리더
- I want Wallet 도메인의 락 정책·SSOT 규범을 ADR과 규범 문서에 굳혀둠
- so that 후속 Product(Project · Pledge)가 참조 가능한 진실 소스 확보

### 설명
- 신규 ADR: `workflows/living-docs/Ai-adr/012-wallet-ssot-and-pessimistic-lock.md`
  - 잔액 SSOT = `balance` 컬럼 · 배치 검증
  - 비관 락 채택 근거 · 낙관 락 미채택
  - 락 순서 최상위에 Wallet 배치
- `.claude/rules/consitency.md` §5 락 순서 표 갱신:
  - 기존: `Order → Payment → Point → Inventory`
  - 신규: `Wallet → Project → RewardTier → Pledge → Order → Payment → Product`
- `.claude/rules/idempotency.md` §2 카탈로그에 "지갑 충전" 항목 (Payment 확정과 동일 규범)
- `workflows/backend-boundary/error-codes.md`에 WAL001~004 UX 매핑 추가

### 완료 기준 (AC)
- Given ADR 파일 · When 확인 · Then §7(원칙)~§9(참고) 완결
- Given `.claude/rules/consitency.md` · When 확인 · Then §5 락 순서 표에 Wallet 최상위
- Given `backend-boundary/error-codes.md` · When 확인 · Then WAL001~004 매핑 존재

### Definition of Done
- [ ] ADR 파일 신설
- [ ] `.claude/rules/consitency.md` §5 갱신
- [ ] `.claude/rules/idempotency.md` §2 카탈로그 갱신
- [ ] `workflows/backend-boundary/error-codes.md` WAL 섹션 추가
- [ ] 팀 공유 (PR 리뷰 시 규범 갱신 확인)

### 스토리 포인트
0.5d

### 의존성
- 선행: Epic 1~3 완료
- 후행: 없음

---

## 요약

| Epic | Story | SP 합계 |
|---|---|---|
| Epic 1: 도메인 확장 | 3 | 2.5 |
| Epic 2: Service 흐름 | 4 | 4.5 |
| Epic 3: Controller REST | 4 | 3.0 |
| Epic 4: 정합성·관측·ADR | 3 | 2.5 |
| **합계** | **14** | **12.5 SP** |

**진행 순서 (필수)**: Epic 1 → 2 → 3 → 4
- Epic 1은 나머지 Epic의 전제
- Epic 2·3은 병렬 진행 가능 (Story 2-1 완료 후 3-4 착수)
- Epic 4는 마지막 (관측·규범 문서는 코드 안정화 후 굳힘)
