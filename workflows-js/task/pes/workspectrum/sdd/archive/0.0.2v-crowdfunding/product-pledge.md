# [Product 8] 후원 (Pledge · Wallet+Project+Reward 통합 흐름)

## Product Vision
> 후원자가 프로젝트의 특정 리워드 티어를 선택하여 후원한 이력을 관리하는 도메인. **Kickstarter All-or-Nothing** 방식으로 프로젝트 목표 달성 시만 실제 지급하고, 미달성 시 자동으로 전원 환불한다. Wallet(잔액) · Project(모금액·상태) · RewardTier(재고)의 3-way 통합 진입점이자, 자동 종료 스케줄러와 배치 환불로 크라우드 펀딩 컨셉의 핵심 자금 흐름을 안전하게 성립시킨다.

## 배경 및 문제
- 현재 상황 (As-Is)
  - Wallet(Product 5) · Project(Product 6) · RewardTier(Product 7) 각 도메인의 도메인 메서드는 완성됐으나 **통합 흐름 부재**
  - Order 도메인이 일반 쇼핑몰 개념(재고 차감 + 결제 확정)으로 설계됨 · 크라우드 펀딩 후원 개념 없음
  - 후원자의 "후원 이력" 조회 · 취소 UX 부재
  - 프로젝트 종료 판정 자동화 없음 (마감일 지나도 자동 상태 전이 X)
- 발생하는 문제
  - Wallet + Project + RewardTier가 독립적으로 완성됐지만 **후원 요청 하나로 3개 도메인을 원자적으로 갱신**하는 유스케이스 진입점 없음
  - Kickstarter 규범의 핵심(목표 달성 여부에 따른 지급/환불)이 코드에 없어 크라우드 펀딩 컨셉 완결 불가
  - 후원자 UX: "내가 어디에 얼마 후원했지?" 조회 불가
  - 목표 미달성 시 수동 환불 처리 부담 (프로젝트 100건 실패 = 후원자 수천 명 수동 환불 불가)
  - 이슈 #12 채팅 REWARD_CARD 발송 후 후원 진입점 부재 · Chat SDD 진입 어려움
- 왜 지금 해결해야 하는가
  - Wallet + Project + Reward Tier 완결 직후의 자연스러운 순차 · 크라우드 펀딩 컨셉의 통합 지점
  - 백엔드 스토리 파괴력 최상 (상태기계 · 배치 · 락 순서 · Kickstarter 방식)
  - 이후 SDD(Chat · Maker · GitHub · Discovery)는 본 Product 완결 후 순수 부가 도메인 · 크라우드 펀딩 코어 완결
  - 초기 사용자 없어 정합성 규범을 코드에 굳혀둘 마지막 기회

## 목표 (To-Be)
- 신규 컨텍스트: `nbc.c1oud_mall.pledge.*` (4레이어)
- `Pledge` **Aggregate root** · 7-state `PledgeStatus` enum
- 7개 상태: `DRAFT · PENDING · CONFIRMED · FUNDED · REFUNDED · CANCELLED · FAILED`
- 도메인 메서드 6개: `confirm(walletTxId) · markFunded() · markRefunded() · cancel(backerId) · markFailed(reason)` 등
- `PledgeService.pledge` — **Wallet + Project + Reward 3-way 통합 흐름** (락 순서 준수)
- `PledgeService.closeProject` — 프로젝트 종료 판정 + All-or-Nothing 지급/환불
- `ProjectClosingScheduler` — 5분 주기 · 마감된 프로젝트 자동 종료
- `PledgeService.cancel` — 후원자 명시 취소 (LIVE 중만) + 재고/모금액 복구
- REST 4개 엔드포인트: 후원 생성 · 취소 · 내 이력 · 프로젝트 후원자 수
- `ErrorCode.PLG001~005` 등록
- 락 순서 최종 완성 (`Wallet(1) → Project(2) → RewardTier(3) → Pledge(4)`)
- 관측 지표 4종 (created · funded · refunded · closing.duration)
- **All-or-Nothing 정책 · Idempotency S+ 등급** — 카탈로그(idempotency.md §2) 추가

## 설계 결정 (Design Decisions)
> 큰 갈림길의 결정. 거부된 옵션도 합리적 근거가 있었음을 명시.

- **Wallet 즉시 차감 방식 채택** (Option A · 이슈 #10 확정)
  - 후원 순간 즉시 잔액 감소 → 후원자 지갑에 "예약" 상태 표시 (사용자 관점 "결제 완료")
  - 프로젝트 실패 시 지갑 자동 환불
  - Kickstarter는 카드 청구 시점을 프로젝트 성공 후로 미루지만, 우리는 **가상 포인트 지갑** 모델이므로 즉시 차감이 자연스러움 · 실패 케이스도 훨씬 단순 (지갑 이체만)
  - 대안 (예약만 · 성공 시 일괄 차감)은 후원자 잔액 부족 케이스 대량 발생 리스크
- **7-state 상태기계 채택** — `DRAFT · PENDING · CONFIRMED · FUNDED · REFUNDED · CANCELLED · FAILED`
  - 이슈 #10에서 확정된 상태 목록 (DRAFT 포함)
  - `DRAFT`: 결제 진입 · 아직 지갑 차감 안 됨 (UI에서 취소해도 부수효과 X)
  - `PENDING`: 지갑 차감 진행 중 (짧은 시간 · 실패 시 자동 롤백)
  - `CONFIRMED`: 지갑 차감 완료 · 프로젝트 종료 대기
  - `FUNDED`: 프로젝트 성공 → 지급 완료
  - `REFUNDED`: 프로젝트 실패 → 자동 환불 완료
  - `CANCELLED`: 후원자 명시 취소 (LIVE 중) → 환불 완료
  - `FAILED`: 지갑 차감 실패 등 시스템 오류 (관리자 개입)
- **All-or-Nothing 정책 (Kickstarter 방식)**
  - 목표 달성 시만 프로젝트에 지급 (Pledge → FUNDED)
  - 미달성 시 자동 환불 (Pledge → REFUNDED · 지갑 원상 복구)
  - 정책은 도메인 서비스에서 강제 · Project.isFundingSuccessful() 판정 재사용
- **자동 종료 스케줄러 5분 주기** — `@Scheduled(fixedDelay = 5 * 60 * 1000)`
  - `endedAt < now` 프로젝트 자동 판정 (성공/실패)
  - 배치 환불은 프로젝트 단위 · 개별 pledge는 `REQUIRES_NEW` (Failure Isolation)
- **중복 실행 방지**: 초기 v0.0.3 단일 인스턴스 전제 → 자체 DB 락 · v0.0.5+ ShedLock 도입 검토
- **후원 취소 정책**: LIVE 중에만 가능 · SUCCESSFUL 이후 취소 불가 (정책 신뢰성)
- **후원 수정 없음**: 티어 변경 or 금액 변경 = 취소 후 재후원 (Kickstarter 규범)
- **락 순서**: `Wallet(1) → Project(2) → RewardTier(3) → Pledge(4)` — Product 5·6·7과 동일
  - 후원 흐름의 진입점 · 데드락 방지 · `.claude/rules/consitency.md` §5 최종 확정
- **Idempotency S+ 등급**: 서버 채번 Pledge id + 사전조회 + DB 유니크 → `idempotency.md` §2 카탈로그 추가

## 대안 검토 (Alternatives Considered)

### Wallet 차감 시점
**Option A — Pledge 즉시 지갑 차감 · 실패 시 환불 (선택)**
- 비용: 후원자 잔액 즉시 감소 (UI로 명확히 표시)
- 보상: 자금 흐름 명확 · 취소/환불 처리 단순 (지갑 이체만) · Kickstarter 카드 청구 방식과 다르지만 가상 포인트 지갑 모델에 자연스러움

**Option B — Pledge는 "예약"만 · 성공 시 일괄 차감**
- 거부 이유: 성공 시점 후원자 잔액 부족 시 재청구 불가 · 대량 실패 처리 복잡

### 상태기계 개수
**Option A — 7-state 상세 (선택)**
- Kickstarter 규범 + FAILED 시스템 오류 포함
- DRAFT (결제 진입) · PENDING (차감 진행) 세밀 · 사용자 UX 명확

**Option B — 5-state 최소 (CONFIRMED · FUNDED · REFUNDED · CANCELLED · FAILED)**
- 거부 이유: DRAFT · PENDING 없이는 "결제 창 열었다 닫힌" 케이스 처리 어려움

### 자동 종료 스케줄러
**Option A — 5분 주기 배치 (선택)**
- 부하 낮음 · 신입 스코프 · 종료 시각과 오차 최대 5분 · 후원 마감 UX 상 무해

**Option B — 이벤트 기반 (endedAt 도달 시 정확히 트리거)**
- 거부 이유: Spring Scheduler로는 지원 안 함 · Quartz + 개별 잡 도입 오버킬

### 후원 취소 정책
**Option A — LIVE 중에만 가능 (선택)**
- Kickstarter 규범
- SUCCESSFUL 이후 자금 흐름이 확정된 상태 · 취소 시 프로젝트 재정 부담

**Option B — SUCCESSFUL 후에도 24시간 취소 가능**
- 거부 이유: 신뢰성 침해 · 자금 정합성 관리 부담

### 후원 수정
**Option A — 수정 없음 (선택)**
- Kickstarter 규범 · 취소 후 재후원 UX
- 도메인 단순 · 상태기계 복잡도 감소

**Option B — 금액 · 티어 수정 가능**
- 거부 이유: 원자적 갱신 복잡 · 재고 재조정 · 지갑 delta 처리 부담

## 전체 아키텍처 (High-Level Architecture)

### 컴포넌트 배치
```
presentation ──▶ application ──▶ domain ◀── infrastructure
PledgeController  PledgeService              Pledge (Aggregate)         PledgeRepository
- create          - pledge()  ⭐             - confirm()                 - findByIdForUpdate ⭐
- cancel          - cancel()                 - markFunded()              - findByProjectIdAndStatus
- history         - closeProject() ⭐         - markRefunded()            ProjectClosingScheduler
- backers         - getHistory()             - markFailed()              - runEvery5Min()
                  - getBackersCount()        - cancel(backerId)
                                             PledgeStatus (7-state)
                                             PledgeDomainService
                                             - transitionRules

External协力 (Wallet · Project · RewardTier 통합):
- Wallet.pledge() (Product 5) — 잔액 차감 진입점
- Project.addPledgeAmount() / removePledgeAmount() (Product 6) — 반정규화 갱신
- Project.markSuccessful() / markFailed() (Product 6) — 종료 판정
- RewardTier.reservePledge() / releasePledge() (Product 7) — 재고 차감
- Wallet.refundPledge() (Product 5) — 환불 진입점
```

### 핵심 플로우
**1. 후원 생성 (`pledge`) · 3-way 통합**
```
Backer → PledgeController.create(projectId, request)
       → PledgeService.pledge(backerId, cmd)
         ├── Project FOR UPDATE  (락 2 · isLive 검증)  → PLG004 (프로젝트 LIVE 아님)
         ├── RewardTier FOR UPDATE  (락 3 · reservePledge)  → RWD002 (재고 0)
         ├── Wallet FOR UPDATE  (락 1 · pledge)  → WAL002 (잔액 부족)
         │     ⚠️ 실제로는 Wallet(1) → Project(2) → RewardTier(3) 락 순서 (consistency §5)
         ├── project.addPledgeAmount(tier.price)
         ├── Pledge DRAFT 생성 → PENDING → confirm(walletTxId) → CONFIRMED
         └── Pledge 저장  (락 4)
       ← 201 Created + Location
```

**2. 후원 취소 (`cancel`) · LIVE 중**
```
Backer → PledgeController.cancel(pledgeId)
       → PledgeService.cancel(backerId, pledgeId)
         ├── Pledge FOR UPDATE
         ├── pledge.verifyOwnership(backerId)  → PLG003
         ├── Project 상태 검증 (LIVE만)  → PLG002 (상태 위반)
         ├── Wallet FOR UPDATE → wallet.refundPledge(amount)
         ├── Project FOR UPDATE → project.removePledgeAmount(amount)
         ├── RewardTier FOR UPDATE → tier.releasePledge()
         └── pledge.cancel(backerId) → CANCELLED
       ← 200 OK
```

**3. 프로젝트 종료 판정 (`closeProject`) · 스케줄러 진입**
```
@Scheduled(fixedDelay = 5 * 60 * 1000)
ProjectClosingScheduler.closeExpired()
  → expired = projectRepository.findByFundingStatusAndEndedAtBefore(LIVE, now)
  For each project:
    PledgeService.closeProject(projectId)
      ├── Project FOR UPDATE
      ├── if (project.isFundingSuccessful()):
      │     project.markSuccessful()
      │     For each CONFIRMED pledge (개별 TX · REQUIRES_NEW):
      │       pledge.markFunded()
      │     project.markFunded() (지급 완료 후)
      │
      │ else:  (실패)
      │     project.markFailed()
      │     For each CONFIRMED pledge (개별 TX · REQUIRES_NEW):
      │       ├── Wallet FOR UPDATE → wallet.refundPledge(amount)
      │       └── pledge.markRefunded()
      │     project.markRefunded() (환불 완료 후)
```

**4. 후원 이력 조회 (`getHistory`)**
```
Backer → PledgeController.myHistory(status?)
       → PledgeService.getHistory(backerId, statusFilter, pageable)
         └── pledgeRepository.findByBackerUserId(backerId, statusFilter, pageable)
       ← Page<PledgeResponse>
```

### Out-of-Process 의존
- **RDS (MySQL)** — `pledge` 테이블 · `SELECT FOR UPDATE` · 인덱스 다수
- (참조) Wallet · Project · RewardTier 도메인 (모두 c1oud-mall 내부 · BC 협력 직접 호출)

## 실패 모드 / 운영 관측 (Failure Modes & Observability)

### 실패 시나리오와 응답
| 시나리오 | ErrorCode | HTTP | 클라이언트 권장 동작 |
| --- | --- | --- | --- |
| 후원 없음 | `PLG001` PLEDGE_NOT_FOUND | 404 | 이력 새로고침 |
| 상태 위반 (예: SUCCESSFUL 이후 취소) | `PLG002` PLEDGE_INVALID_STATUS | 400 | 상태 확인 후 재시도 |
| 본인 후원 아님 (취소 시) | `PLG003` PLEDGE_OWNERSHIP_FAILED | 403 | 접근 거부 |
| 프로젝트 LIVE 아님 (후원 시) | `PLG004` PROJECT_NOT_LIVE | 409 | 프로젝트 상태 확인 |
| 중복 후원 정책 위반 (`{backerId + projectId + rewardTierId}` 중복) | `PLG005` PLEDGE_ALREADY_EXISTS | 409 | 기존 후원 확인 · 필요 시 취소 후 재후원 |
| 잔액 부족 | `WAL002` (Product 5 재사용) | 409 | 충전 유도 |
| 재고 0 | `RWD002` (Product 7 재사용) | 409 | 다른 티어 유도 |
| 배치 환불 개별 실패 | (내부 로그 마커) | - | 운영자 대응 · `PLEDGE_REFUND_FAILED` |
| 스케줄러 중복 실행 (다중 인스턴스) | (락 실패 · 조용히 무시) | - | v0.0.5+ ShedLock 도입 |

### 로깅 정책
- **항상 기록**:
  - `requestId` · `pledgeId` · `backerUserId` · `projectId` · `rewardTierId` · `amount` · `status` 전이 · `walletTxId`
- **debug**: 스케줄러 실행 시각 · 처리 프로젝트 수 · 처리 pledge 수
- **절대 금지**:
  - `message` (응원 메시지 · 개인정보 포함 가능) 원문 로그
- **특수 마커**: `PLEDGE_REFUND_FAILED pledgeId={} projectId={} amount={}` — 개별 환불 실패 시 알람 대상

### 관측 지표
- `pledge.created.total{status=confirmed|failed}` — counter — 후원 생성 성공/실패
- `pledge.funded.total` — counter — 프로젝트 성공 시 지급 완료 카운트
- `pledge.refunded.total{reason=project_failed|user_cancelled}` — counter — 환불 원인 분포
- `pledge.closing.duration_seconds` — histogram — 프로젝트 종료 배치 처리 시간
- `pledge.scheduler.batch.total{result=success|partial_fail|full_fail}` — counter — 스케줄러 실행 결과

## 롤아웃 / 마이그레이션 (Rollout)

### 전제
- Product 5·6·7 완결 상태 · 도메인 메서드 이용 가능
- 초기 사용자 없음 · 신규 테이블만 · JPA ddl-auto 자동 반영
- Order 도메인은 리네임 or 폐기 대상 (별도 결정 · 본 SDD는 신규 `pledge` 컨텍스트만 관리 · Order 폐기는 Product 6 리네임 시 처리)

### Product 의존성
- **선행**: **Wallet(5)** · **Project(6)** · **Reward Tier(7)** — 도메인 메서드 완결 필수
- **후행**: **Chat(9)** — REWARD_CARD 발송 후 후원 진입 · **Review(이슈 #05)** — FUNDED pledge만 리뷰 가능
- **밀접 참조**: Wallet · Project · RewardTier의 모든 도메인 메서드

### Epic·Story 의존성 그래프
```
Epic 1 (엔티티·상태기계) ──► Epic 2 (Service 통합 흐름)
                                  ├─► Epic 3 (스케줄러·배치 환불)
                                  └─► Epic 4 (REST + 관측 + ADR)
```

### 환경별 설정 분기
| 항목 | dev (H2) | prod (RDS MySQL) |
| --- | --- | --- |
| 테이블·인덱스 | JPA ddl-auto | 동일 |
| `SELECT FOR UPDATE` | H2 MySQL 호환 모드 | 완전 지원 |
| ProjectClosingScheduler 주기 | 1분 (테스트 편의) | 5분 |
| 스케줄러 중복 방지 | 단일 인스턴스 전제 | 단일 인스턴스 전제 · v0.0.5+ ShedLock |
| DummyDataInit | 프로젝트당 후원 5~10건 (다양 상태) | 활성 (초기 UX 유도) |

## 성공 지표 (KPI)
| 지표 | 목표 값 | 측정 방법 |
| --- | --- | --- |
| 후원 생성 성공률 | ≥ 95% (잔액·재고 확인 후) | `pledge.created.total{status=confirmed}` / 전체 |
| 프로젝트 종료 배치 환불 누락 | 0건 | 배치 완료 후 CONFIRMED pledge 잔여 조회 · 항상 0 (실패 프로젝트) |
| 후원 정합성 (Project.raisedAmount = SUM(FUNDED/CONFIRMED pledge.amount)) | 100% | 주간 배치 검증 (본 Product 담당) |
| 후원 취소 흐름 데드락 | 0건 | 락 순서 검증 · Chaos 통합 테스트 |
| 스케줄러 처리 시간 P95 | ≤ 30초 (100 프로젝트 · 각 100 pledge) | `pledge.closing.duration_seconds` P95 |

## Scope
**In Scope**:
- 신규 컨텍스트 `nbc.c1oud_mall.pledge.*` (4레이어)
- `Pledge` Aggregate root · 7-state `PledgeStatus` enum
- 6개 도메인 메서드 (`confirm · markFunded · markRefunded · markFailed · cancel · isCancellable`)
- `PledgeService.pledge` — Wallet + Project + RewardTier 통합 흐름
- `PledgeService.cancel` — 후원자 명시 취소 (LIVE 중만)
- `PledgeService.closeProject` — 프로젝트 종료 판정 + All-or-Nothing 지급/환불
- `PledgeService.getHistory · getBackersCount`
- `ProjectClosingScheduler` — 5분 주기 · 단일 인스턴스 전제
- REST 4개 엔드포인트
- `ErrorCode.PLG001~005`
- 락 순서 최종 확정 (`consistency.md` §5 최종본)
- Idempotency S+ 카탈로그 (`idempotency.md` §2 갱신)
- 관측 지표 5종
- 반정규화 SSOT 배치 (`Project.raisedAmount == SUM(pledge.amount)` 주간 검증)

**Out of Scope**:
- **후원 수정** — 금액·티어 변경 = 취소 후 재후원 (Kickstarter 규범)
- **응원 메시지 편집** — 후원 생성 시 1회만 · v0.0.5+ 재검토
- **관리자 강제 상태 변경** — 초기 SQL 수동 · v0.0.5+ 관리자 API
- **배치 환불 자동 재시도** — 개별 실패 시 로그 마커 · 운영자 수동 · v0.0.5+ 재시도 큐 검토
- **Milestone 부분 지급** — Kickstarter도 안 하는 확장 · v0.0.6+
- **후원 선물** — 다른 사람 이름으로 후원 · v0.0.5+
- **다중 인스턴스 스케줄러** — ShedLock · v0.0.5+
- **자동 종료 이벤트 기반 트리거** — endedAt 정확히 도달 시 · Quartz · v0.0.5+

## 대상 사용자
- **후원자 (Backer)** — 프로젝트에 후원 · 이력 조회 · LIVE 중 취소
- **메이커 (Maker)** — 자기 프로젝트의 후원자 수·모금액 조회 (Project.backerCount · raisedAmount)
- **운영자** — 스케줄러 실패 대응 · 배치 환불 실패 대응 · SSOT 검증 배치 모니터링
- **후속 SDD 작성자** — Chat REWARD_CARD → Pledge 진입 · Review는 FUNDED 검증
- **개발/QA** — Wallet · Project · RewardTier 통합 회귀 · 상태기계 매트릭스

## 연결된 Epic 목록
- [ ] Epic 1: `Pledge` Aggregate + 7-state 상태기계 + 6개 도메인 메서드 + 비관 락 + ErrorCode
- [ ] Epic 2: `PledgeService.pledge` 통합 흐름 + `cancel` + 이력 조회
- [ ] Epic 3: `PledgeService.closeProject` + `ProjectClosingScheduler` + 배치 환불
- [ ] Epic 4: REST 4개 엔드포인트 + 관측 지표 + ADR + 규범 최종 확정

## 관련 문서
- **원본 이슈**: `workflows/task/fix/brainstorming/version/0.0.2v/issue-10-pledge-state-machine.md`
- **선행 SDD**: `product-wallet.md` · `product-project.md` · `product-reward.md`
- **후행 SDD**:
  - `product-chat.md` (Product 9 · REWARD_CARD → Pledge 진입점)
  - `product-review.md` (이슈 #05 · FUNDED pledge만 리뷰 가능)
  - `product-maker.md` (Product 11 · 응답률 계산에 pledge 이력 부수적)
- **관련 이슈**:
  - `issue-07-wallet-prepaid-balance.md` — Wallet 도메인 메서드
  - `issue-08-project-domain.md` — Project 상태·반정규화
  - `issue-09-reward-tier.md` — RewardTier 재고
  - `issue-12-chat-rich-message.md` — REWARD_CARD 발송 후 후원 진입점
- **벤치마크**:
  - Kickstarter · 텀블벅 · Wadiz — All-or-Nothing 규범
- **신규 ADR 후보**:
  - "Pledge Wallet 즉시 차감 (Kickstarter 카드 청구 방식과의 차이)"
  - "7-state PledgeStatus 상태기계 · 전이 규칙"
  - "ProjectClosingScheduler 5분 주기 · 단일 인스턴스 전제 · 다중화 로드맵"
  - "배치 환불 개별 TX (REQUIRES_NEW) · Failure Isolation"
- **규범 갱신 예정**:
  - `.claude/rules/consitency.md` §5 락 순서 표 **최종 확정** (`Wallet → Project → RewardTier → Pledge → Order → Payment → Product`)
  - `.claude/rules/consitency.md` §2 zone 매핑에 "후원 확정 zone · 프로젝트 종료 zone" 추가
  - `.claude/rules/idempotency.md` §2 카탈로그에 "Pledge 생성 (S+)" · "프로젝트 종료 판정 (상태 기반 · A 등급)" 추가
  - `workflows/backend-boundary/error-codes.md` PLG001~005 매핑 추가

## 열린 질문 (Open Questions)
- **중복 후원 정책 최종 결정** — 같은 후원자가 같은 프로젝트의 같은 티어에 재후원 가능? · 초기 결정: **불가 (PLG005)** · v0.0.4+ 재검토 (Kickstarter는 허용)
- **스케줄러 중복 실행 방지 우선순위** — 단일 인스턴스 전제로 초기 진행 · 다중 인스턴스 배포 시점(v0.0.5+)에 ShedLock 도입
- **배치 환불 실패 시 재시도** — 초기 로그 마커 + 수동 대응 · 사용자 100+ 시 자동 재시도 큐 (Kafka? Redis?) 검토
- **후원 취소 유예 시간** — 후원 직후 몇 분 이내는 취소 자유? · 초기 결정: LIVE 중 언제든 가능 · 프로젝트 성공 임박(≤ 24시간) 시 제한 검토 v0.0.5+
- **응원 메시지 최대 길이** — 초기 500자 · Kickstarter 대비 조정 여지
- **Anonymous 후원** — 후원자 이름 숨김 옵션? · v0.0.5+ 사용자 요구 시

## 제품 수준 완료 기준 (Product-level DoD)
- [ ] 모든 Epic DoD 통과
- [ ] E2E 시나리오 1 (성공): 프로젝트 생성 → 후원 3회 (목표 초과) → 스케줄러 발동 → SUCCESSFUL → 모든 pledge FUNDED
- [ ] E2E 시나리오 2 (실패): 프로젝트 생성 → 후원 2회 (목표 미달) → 스케줄러 발동 → FAILED → 모든 pledge REFUNDED · 지갑 원상 복구
- [ ] E2E 시나리오 3 (취소): 후원 → 사용자 취소 → 재고·모금액·지갑 정확히 복구
- [ ] 동시성 통합 테스트 (재고 1개 남은 티어에 5명 동시 후원 → 1명만 성공)
- [ ] 통합 테스트: 100명 후원 → 프로젝트 실패 → 배치 환불 100명 성공 · 개별 TX 격리 확인
- [ ] 데드락 통합 테스트: 락 순서 준수 검증 (Chaos)
- [ ] SSOT 배치 통과 (`Project.raisedAmount = SUM(pledge.amount)` 100%)
- [ ] ADR 최소 3건 발행 (Wallet 차감 정책 · 상태기계 · 배치 정책)
- [ ] `.claude/rules/consitency.md` §5 · §2 최종 확정
- [ ] `.claude/rules/idempotency.md` §2 카탈로그 갱신
- [ ] `backend-boundary/error-codes.md` PLG001~005 매핑
- [ ] 관측 지표 5개 프로덕션 노출

---

# [Epic 1] `Pledge` Aggregate + 7-state 상태기계 + 6개 도메인 메서드 + 비관 락 + ErrorCode

## 목표
`Pledge` Aggregate root와 7-state `PledgeStatus` enum, 6개 상태 전이 도메인 메서드, 비관 락 리포지터리 메서드, `ErrorCode.PLG001~005`를 구축하여 후속 Epic이 안정적으로 확장할 수 있는 기반을 마련한다.

## 배경
- 크라우드 펀딩 후원 흐름의 근본 데이터 · 정확한 상태 전이 필수
- 도메인 로직(소유권·상태 검증)은 엔티티에서 강제 · Service는 조합만
- 비관 락으로 동시성 방어 · Wallet·Project·Reward와 동일 패턴

## 포함 Story
- Story 1-1: `Pledge` 엔티티 + `PledgeStatus` 7-state enum + `PledgeRepository` 기본 CRUD
- Story 1-2: 도메인 메서드 6개 (`confirm · markFunded · markRefunded · markFailed · cancel · isCancellable`)
- Story 1-3: `PledgeRepository.findByIdForUpdate` (비관 락) + `findByProjectIdAndStatus` + 기타 쿼리 + `ErrorCode.PLG001~005`

## Epic 인수 시나리오
- Given Pledge(status=DRAFT) · When `confirm(walletTxId=100)` · Then status=CONFIRMED · confirmedAt 설정 · walletTxId 저장
- *(엣지)* Given status=CONFIRMED · When `confirm(...)` · Then `PLG002` (이미 확정됨)

## Epic 완료 기준 (DoD)
- [ ] 3개 Story 완료
- [ ] 상태 전이 매트릭스 테스트 (7-state × 6-method = 42 케이스 · 유효/무효 분류)
- [ ] `@DataJpaTest` 슬라이스 · 비관 락 검증

---

## [Story 1-1] `Pledge` 엔티티 + `PledgeStatus` enum + Repository CRUD

### User Story
- As a Pledge 도메인 개발자
- I want `Pledge` Aggregate root · `PledgeStatus` 7-state enum · 기본 Repository
- so that 서비스 계층에서 후원 이력 저장·조회 가능

### 설명
- 위치: `nbc.c1oud_mall.pledge.domain.Pledge`
- 7-state enum: `DRAFT · PENDING · CONFIRMED · FUNDED · REFUNDED · CANCELLED · FAILED`
- Aggregate root · Project · RewardTier · User는 논리적 FK 참조만
- `BaseEntity` 상속 (JPA Auditing)

**핵심 스키마**:
```sql
CREATE TABLE pledge (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  project_id        BIGINT       NOT NULL,
  reward_tier_id    BIGINT       NOT NULL,
  backer_user_id    BIGINT       NOT NULL,
  amount            BIGINT       NOT NULL,           -- 후원 금액 (RewardTier.price 스냅샷)
  status            VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
  message           VARCHAR(500) NULL,               -- 응원 메시지 (선택)
  wallet_tx_id      BIGINT       NULL,               -- 지갑 차감 이력 참조 (PointHistory.id)
  fail_reason       VARCHAR(200) NULL,               -- FAILED 상태 시 원인
  confirmed_at      TIMESTAMP    NULL,
  funded_at         TIMESTAMP    NULL,
  refunded_at       TIMESTAMP    NULL,
  cancelled_at      TIMESTAMP    NULL,
  failed_at         TIMESTAMP    NULL,
  created_at        TIMESTAMP    NOT NULL,
  updated_at        TIMESTAMP    NOT NULL,
  PRIMARY KEY (id),
  KEY idx_pledge_project_status (project_id, status),
  KEY idx_pledge_backer_created (backer_user_id, created_at DESC),
  KEY idx_pledge_status_project (status, project_id),   -- closeProject 시 CONFIRMED 조회
  KEY idx_pledge_tier (reward_tier_id, status),
  UNIQUE KEY uk_pledge_unique_active (backer_user_id, project_id, reward_tier_id, status),   -- v0.0.4+ 재검토 (임시 · PLG005용)
  CONSTRAINT chk_pledge_amount_positive CHECK (amount > 0)
);
```

**엔티티**:
```java
@Entity
@Table(name = "pledge")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Pledge extends BaseEntity {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long rewardTierId;

    @Column(nullable = false)
    private Long backerUserId;

    @Column(nullable = false)
    private long amount;

    @Enumerated(STRING)
    @Column(nullable = false, length = 20)
    private PledgeStatus status;

    @Column(length = 500)
    private String message;

    @Column
    private Long walletTxId;

    @Column(length = 200)
    private String failReason;

    @Column
    private LocalDateTime confirmedAt;
    @Column
    private LocalDateTime fundedAt;
    @Column
    private LocalDateTime refundedAt;
    @Column
    private LocalDateTime cancelledAt;
    @Column
    private LocalDateTime failedAt;

    public static Pledge draft(Long projectId, Long rewardTierId, Long backerId,
                                long amount, String message) {
        if (amount <= 0) throw new BusinessException(ErrorCode.C001);
        Pledge p = new Pledge();
        p.projectId = projectId;
        p.rewardTierId = rewardTierId;
        p.backerUserId = backerId;
        p.amount = amount;
        p.message = message;
        p.status = PledgeStatus.DRAFT;
        return p;
    }
}

public enum PledgeStatus {
    DRAFT,       // 결제 진입 · 지갑 차감 안 됨
    PENDING,     // 지갑 차감 진행 중
    CONFIRMED,   // 지갑 차감 완료 · 종료 대기
    FUNDED,      // 성공 · 지급 완료
    REFUNDED,    // 실패 · 환불 완료
    CANCELLED,   // 사용자 취소 · 환불 완료
    FAILED       // 시스템 오류 (예: Wallet 차감 실패 후 롤백 안 됨) · 관리자 개입
}
```

**Repository**:
```java
public interface PledgeRepository extends JpaRepository<Pledge, Long> {
    List<Pledge> findByProjectIdAndStatus(Long projectId, PledgeStatus status);
    Page<Pledge> findByBackerUserId(Long backerUserId, Pageable pageable);
    Page<Pledge> findByBackerUserIdAndStatus(Long backerUserId, PledgeStatus status, Pageable pageable);
    long countByProjectId(Long projectId);
    boolean existsByBackerUserIdAndProjectIdAndRewardTierIdAndStatusIn(
        Long backerId, Long projectId, Long tierId, List<PledgeStatus> statuses
    );   // PLG005 중복 검증
}
```

### 완료 기준 (AC)
- Given 유효 파라미터 · When `Pledge.draft(...)` · Then 인스턴스 반환 · `status=DRAFT`
- Given `amount=0` · When `draft` · Then `BusinessException(C001)`
- Given Project 100에 Pledge 3건 (CONFIRMED) · When `findByProjectIdAndStatus(100, CONFIRMED)` · Then 3건
- Given 존재 확인 · When `existsByBackerUserIdAndProjectIdAndRewardTierIdAndStatusIn(...)` · Then `true`

### Definition of Done
- [ ] `Pledge` 엔티티
- [ ] `PledgeStatus` enum
- [ ] `PledgeRepository` 인터페이스 · 기본 CRUD + 조회 메서드
- [ ] DDL 확인 (JPA · dev H2)
- [ ] 단위 테스트: `Pledge.draft` 성공·예외

### 스토리 포인트
1d

### 의존성
- 선행: Product 5·6·7 (Wallet·Project·RewardTier)
- 후행: Story 1-2 · 1-3 · Epic 2

---

## [Story 1-2] 6개 도메인 메서드 (상태 전이)

### User Story
- As a Pledge 도메인 개발자
- I want 6개 도메인 메서드로 7-state 상태 전이 규칙을 강제
- so that 유효하지 않은 전이 자동 차단

### 설명
- 6개 메서드:
  - `confirm(Long walletTxId)` — `DRAFT` or `PENDING` → `CONFIRMED`
  - `markFunded()` — `CONFIRMED` → `FUNDED` (프로젝트 성공)
  - `markRefunded()` — `CONFIRMED` → `REFUNDED` (프로젝트 실패)
  - `markFailed(String reason)` — 어느 상태에서든 → `FAILED` (시스템 오류)
  - `cancel(Long backerId)` — `CONFIRMED` → `CANCELLED` (사용자 취소 · 소유권 검증)
  - `isCancellable()` — `CONFIRMED` 상태이면 true

**주요 메서드**:
```java
public void confirm(Long walletTxId) {
    if (status != DRAFT && status != PENDING)
        throw new BusinessException(ErrorCode.PLG002);
    this.status = CONFIRMED;
    this.confirmedAt = LocalDateTime.now();
    this.walletTxId = walletTxId;
}

public void markFunded() {
    if (status != CONFIRMED)
        throw new BusinessException(ErrorCode.PLG002);
    this.status = FUNDED;
    this.fundedAt = LocalDateTime.now();
}

public void markRefunded() {
    if (status != CONFIRMED)
        throw new BusinessException(ErrorCode.PLG002);
    this.status = REFUNDED;
    this.refundedAt = LocalDateTime.now();
}

public void markFailed(String reason) {
    // 어떤 상태에서든 FAILED로 전이 (시스템 오류 · 관리자 개입 대상)
    if (status == FUNDED || status == REFUNDED || status == CANCELLED)
        throw new BusinessException(ErrorCode.PLG002);
    this.status = FAILED;
    this.failedAt = LocalDateTime.now();
    this.failReason = reason;
}

public void cancel(Long backerId) {
    verifyOwnership(backerId);
    if (status != CONFIRMED)
        throw new BusinessException(ErrorCode.PLG002);
    this.status = CANCELLED;
    this.cancelledAt = LocalDateTime.now();
}

public boolean isCancellable() {
    return status == CONFIRMED;
}

public void verifyOwnership(Long userId) {
    if (!Objects.equals(this.backerUserId, userId))
        throw new BusinessException(ErrorCode.PLG003);
}
```

### 완료 기준 (AC)
- Given `status=DRAFT` · When `confirm(100)` · Then `status=CONFIRMED` · `confirmedAt` 설정 · `walletTxId=100`
- *(예외)* Given `status=CONFIRMED` · When `confirm(200)` · Then `PLG002`
- Given `status=CONFIRMED` · When `markFunded()` · Then `status=FUNDED` · `fundedAt`
- *(예외)* Given `status=FUNDED` · When `markRefunded()` · Then `PLG002`
- Given `status=CONFIRMED` · 소유자 · When `cancel(backerId)` · Then `status=CANCELLED`
- *(예외)* Given 타 사용자 · When `cancel(otherId)` · Then `PLG003`
- Given `status=CONFIRMED` · When `isCancellable()` · Then `true`
- Given `status=FUNDED` · When `isCancellable()` · Then `false`
- Given `status=FUNDED` · When `markFailed(...)` · Then `PLG002` (종단 상태에서 FAILED 불가)

### Definition of Done
- [ ] 6개 도메인 메서드 구현
- [ ] 단위 테스트: 상태 전이 매트릭스 (7 × 6 = 42 케이스 · 유효/무효)
- [ ] 프로퍼티 기반 테스트: 상태 전이 후 이전 상태로 롤백 안 됨

### 스토리 포인트
1.5d

### 의존성
- 선행: Story 1-1
- 후행: Epic 2·3

---

## [Story 1-3] `findByIdForUpdate` + 조회 쿼리 + `ErrorCode.PLG001~005`

### User Story
- As a Pledge 서비스
- I want 비관 락 조회 · 상태별 조회 · 중복 검증 쿼리 + 5개 ErrorCode
- so that Service에서 원자적 처리 · 일관된 예외 응답

### 설명
- 비관 락: `@Lock(LockModeType.PESSIMISTIC_WRITE)` (Wallet · Project · Reward와 동일 패턴)
- `ErrorCode`:
  - `PLG001` PLEDGE_NOT_FOUND (404)
  - `PLG002` PLEDGE_INVALID_STATUS (400)
  - `PLG003` PLEDGE_OWNERSHIP_FAILED (403)
  - `PLG004` PROJECT_NOT_LIVE (409)
  - `PLG005` PLEDGE_ALREADY_EXISTS (409)

**주요 메서드**:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Pledge p WHERE p.id = :id")
Optional<Pledge> findByIdForUpdate(@Param("id") Long id);

@Query("SELECT p FROM Pledge p WHERE p.projectId = :projectId AND p.status = :status")
List<Pledge> findByProjectIdAndStatus(@Param("projectId") Long projectId,
                                      @Param("status") PledgeStatus status);
```

### 완료 기준 (AC)
- Given `Pledge(id=1)` 저장 · When `findByIdForUpdate(1)` (TX 안) · Then Optional non-empty · 락 획득
- *(동시성)* 2 트랜잭션 동시 진입 · Then 순차 처리
- Given ErrorCode 등록 · When `errorCode.getCode()` · Then "PLG001" · "PLG002" ...

### Definition of Done
- [ ] `findByIdForUpdate` · 조회 메서드
- [ ] `ErrorCode` enum에 5개 추가 (Pledge 섹션 주석 구분선)
- [ ] `@DataJpaTest` 슬라이스 · 동시성 통합 테스트

### 스토리 포인트
1d

### 의존성
- 선행: Story 1-1 · 1-2
- 후행: Epic 2·3·4

---

# [Epic 2] `PledgeService` 통합 흐름 + 취소 + 이력 조회

## 목표
`PledgeService`를 application 계층에 배치하여 후원 생성(Wallet + Project + RewardTier 3-way 통합) · 취소 · 이력 조회를 제공한다. 락 순서 규범을 엄격히 준수한다.

## 배경
- Epic 1의 도메인·리포지터리 위에서 유스케이스 조합
- 후원 흐름은 Wallet · Project · RewardTier 모두 원자적으로 갱신 · 락 순서 필수
- 취소는 원자적 롤백 (지갑·모금액·재고 복구)

## 포함 Story
- Story 2-1: `PledgeService.pledge` — 3-way 통합 흐름
- Story 2-2: `PledgeService.cancel` — 후원자 취소 (원자적 롤백)
- Story 2-3: `PledgeService.getHistory` + `getBackersCount`

## Epic 인수 시나리오
- Given Wallet(balance=10000) · Project(LIVE, raisedAmount=0) · RewardTier(price=3000, remaining=10)
- When `pledge(backerId, cmd)`
- Then Pledge 저장 (CONFIRMED) · Wallet.balance=7000 · Project.raisedAmount=3000 · Tier.remaining=9

*(예외)* Given Wallet(balance=1000) · When 위 동일 요청 · Then `WAL002` · 모든 상태 원상

## Epic 완료 기준 (DoD)
- [ ] 3개 Story 완료
- [ ] 3-way 통합 흐름 통합 테스트 (성공 · 각 도메인별 실패 격리)
- [ ] 동시성 테스트 (재고 1개 · 5명 동시 후원)
- [ ] 취소 흐름 원자적 롤백 검증

## Epic 기술 결정 / 대안 (Epic-Level Alternatives)
- **`REQUIRED` 전파 (단일 TX)** — 후원 흐름 전체가 하나의 TX · 도중 실패 시 전체 롤백
- 대안 `REQUIRES_NEW`는 부분 커밋 리스크 · 거부

---

## [Story 2-1] `PledgeService.pledge` — 3-way 통합 흐름

### User Story
- As a 후원자
- I want 티어 하나를 선택하여 후원 · Wallet·Project·RewardTier가 원자적으로 갱신
- so that 후원 완료 상태에서 프로젝트 종료 대기

### 설명
- 검증 순서:
  1. Project 조회 (읽기) · `fundingStatus == LIVE` 확인 → `PLG004`
  2. RewardTier 조회 · projectId 일치 확인 (`PLG005` or 유효성)
  3. **중복 후원 검증** (초기: 같은 backer+project+tier에 활성 상태 존재? · `PLG005`)
  4. Pledge DRAFT 생성 (지갑 차감 전)
  5. **락 순서 획득** (`Wallet(1) → Project(2) → RewardTier(3)`):
     - Wallet FOR UPDATE → `wallet.pledge(amount)` → `WAL002` 시 롤백
     - Project FOR UPDATE → `project.addPledgeAmount(amount)`
     - RewardTier FOR UPDATE → `tier.reservePledge()` → `RWD002` 시 롤백
  6. Pledge `confirm(walletTxId)` → CONFIRMED
  7. Pledge 저장

**핵심 메서드**:
```java
@Service
@RequiredArgsConstructor
@Transactional
public class PledgeService {
    private final PledgeRepository pledgeRepository;
    private final WalletService walletService;
    private final ProjectRepository projectRepository;
    private final RewardTierRepository tierRepository;

    public Long pledge(Long backerId, PledgeCreateCommand cmd) {
        // 1. 조회·검증 (락 없이)
        Project project = projectRepository.findById(cmd.projectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRJ001));
        if (project.getFundingStatus() != FundingStatus.LIVE)
            throw new BusinessException(ErrorCode.PLG004);

        RewardTier tier = tierRepository.findById(cmd.rewardTierId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RWD001));
        if (!Objects.equals(tier.getProjectId(), cmd.projectId()))
            throw new BusinessException(ErrorCode.PLG002);

        // 2. 중복 후원 검증
        if (pledgeRepository.existsByBackerUserIdAndProjectIdAndRewardTierIdAndStatusIn(
                backerId, cmd.projectId(), cmd.rewardTierId(),
                List.of(PledgeStatus.CONFIRMED, PledgeStatus.PENDING)))
            throw new BusinessException(ErrorCode.PLG005);

        // 3. Pledge DRAFT 생성
        Pledge pledge = Pledge.draft(cmd.projectId(), cmd.rewardTierId(), backerId,
                                      tier.getPrice(), cmd.message());

        // 4. 락 순서 획득 (Wallet → Project → RewardTier)
        long walletTxId = walletService.pledge(backerId, tier.getPrice(),
                                                cmd.projectId(), null /* pledgeId 없음 · 아래 저장 후 갱신 or 참조 우회 */);
        //   → WAL001, WAL002 예외 상위 전파

        Project projectForUpdate = projectRepository.findByIdForUpdate(cmd.projectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRJ001));
        projectForUpdate.addPledgeAmount(tier.getPrice());

        RewardTier tierForUpdate = tierRepository.findByIdForUpdate(cmd.rewardTierId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RWD001));
        tierForUpdate.reservePledge();
        //   → RWD002 (재고 0) 시 상위 TX 전체 롤백 · Wallet·Project 원상

        // 5. Pledge 확정
        pledge.confirm(walletTxId);
        return pledgeRepository.save(pledge).getId();
    }
}
```

### 완료 기준 (AC)
- Given Wallet(10000) · Project(LIVE, raised=0) · Tier(price=3000, remaining=10) · When `pledge` · Then Pledge CONFIRMED · Wallet=7000 · Project.raised=3000 · Tier.remaining=9
- *(예외 · 프로젝트 LIVE 아님)* Given Project=DRAFT · Then `PLG004` · 모든 상태 유지
- *(예외 · 잔액 부족)* Given Wallet(1000) · Then `WAL002` · 모든 상태 유지
- *(예외 · 재고 0)* Given Tier(remaining=0) · Then `RWD002` · 모든 상태 유지 (Wallet·Project 롤백)
- *(예외 · 중복 후원)* Given 이미 CONFIRMED pledge 존재 · Then `PLG005`
- *(동시성)* 재고 1개 · 5명 동시 후원 · Then 1명 CONFIRMED · 4명 `RWD002`

### Definition of Done
- [ ] `PledgeService.pledge` 구현
- [ ] `PledgeCreateCommand` (record)
- [ ] 통합 테스트: 성공 · 각 예외 격리
- [ ] 동시성 통합 테스트 (Wallet · Reward 각 락 자원 경합)
- [ ] E2E: 후원 완료 후 Wallet · Project · Reward · Pledge 모두 정확

### 스토리 포인트
2d

### 의존성
- 선행: Epic 1 완결 · Product 5·6·7 완결
- 후행: Story 3-1 (스케줄러)

### [명세 변경 이력]
- (초안 · 실 구현 시 락 순서 미세 조정 기록)

---

## [Story 2-2] `PledgeService.cancel` — 후원자 취소

### User Story
- As a 후원자
- I want 프로젝트 LIVE 중 내 후원을 취소하여 지갑·재고·모금액 원상 복구
- so that 변심 시 자유로운 취소 (신뢰 UX)

### 설명
- 검증:
  1. Pledge 조회 · 소유권 · CONFIRMED 상태 확인
  2. Project LIVE 상태 확인 → `PLG002` (SUCCESSFUL 이후 취소 불가)
- 원자적 복구 (같은 락 순서):
  1. Pledge FOR UPDATE
  2. Wallet FOR UPDATE → `refundPledge(amount)`
  3. Project FOR UPDATE → `removePledgeAmount(amount)`
  4. RewardTier FOR UPDATE → `releasePledge()`
  5. Pledge `cancel(backerId)` → CANCELLED

**핵심 메서드**:
```java
public void cancel(Long backerId, Long pledgeId) {
    Pledge pledge = pledgeRepository.findByIdForUpdate(pledgeId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PLG001));

    // 소유권 검증
    pledge.verifyOwnership(backerId);
    if (!pledge.isCancellable())
        throw new BusinessException(ErrorCode.PLG002);

    Project project = projectRepository.findByIdForUpdate(pledge.getProjectId())
            .orElseThrow(() -> new BusinessException(ErrorCode.PRJ001));
    if (project.getFundingStatus() != FundingStatus.LIVE)
        throw new BusinessException(ErrorCode.PLG002);

    // 원자적 복구
    walletService.refundPledge(backerId, pledge.getAmount(),
                                pledge.getProjectId(), pledgeId);
    project.removePledgeAmount(pledge.getAmount());
    RewardTier tier = tierRepository.findByIdForUpdate(pledge.getRewardTierId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RWD001));
    tier.releasePledge();

    pledge.cancel(backerId);
}
```

### 완료 기준 (AC)
- Given CONFIRMED pledge · LIVE Project · When `cancel(backerId, pledgeId)` · Then CANCELLED · Wallet·Project·Reward 원상
- *(예외)* Given FUNDED pledge · When `cancel` · Then `PLG002`
- *(예외)* Given SUCCESSFUL Project · When `cancel` · Then `PLG002`
- *(예외)* Given 타 사용자 · When `cancel` · Then `PLG003`

### Definition of Done
- [ ] `PledgeService.cancel`
- [ ] 통합 테스트: 원자적 복구 · 상태·소유권 위반

### 스토리 포인트
1d

### 의존성
- 선행: Story 2-1
- 후행: Story 4-2 (Controller)

---

## [Story 2-3] `PledgeService.getHistory` + `getBackersCount`

### User Story
- As a 후원자·메이커
- I want 내 후원 이력을 상태·페이징으로 조회 · 프로젝트별 후원자 수 조회
- so that 이력 화면 · 프로젝트 상세 지표 표시

### 설명
- `getHistory(backerId, statusFilter?, pageable)` — 상태 nullable · 페이징
- `getBackersCount(projectId)` — Project.backerCount 반정규화 값 우선 사용 (Project 도메인 참조) · 없으면 실시간 COUNT (fallback)

**주요 메서드**:
```java
@Transactional(readOnly = true)
public Page<PledgeResponse> getHistory(Long backerId, PledgeStatus statusFilter, Pageable pageable) {
    Page<Pledge> pledges = (statusFilter == null)
        ? pledgeRepository.findByBackerUserId(backerId, pageable)
        : pledgeRepository.findByBackerUserIdAndStatus(backerId, statusFilter, pageable);
    return pledges.map(PledgeResponse::from);
}

@Transactional(readOnly = true)
public long getBackersCount(Long projectId) {
    // Project.backerCount 반정규화 우선 사용
    Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRJ001));
    return project.getBackerCount();
}
```

### 완료 기준 (AC)
- Given 후원자 이력 5건 (CONFIRMED 3 · REFUNDED 2) · When `getHistory(backerId, null, ...)` · Then 5건
- Given `getHistory(backerId, CONFIRMED, ...)` · Then 3건
- Given Project(backerCount=100) · When `getBackersCount(projectId)` · Then 100

### Definition of Done
- [ ] 2개 조회 메서드
- [ ] `PledgeResponse` record
- [ ] 단위 테스트 · 필터 · 페이징

### 스토리 포인트
0.5d

### 의존성
- 선행: Epic 1
- 후행: Story 4-3

---

# [Epic 3] `PledgeService.closeProject` + `ProjectClosingScheduler` + 배치 환불

## 목표
프로젝트 마감일 도달 시 자동으로 종료 판정하고, All-or-Nothing 정책으로 성공/실패 처리하며, 실패 시 배치 환불로 후원자 지갑 원상 복구를 수행한다.

## 배경
- 크라우드 펀딩 컨셉의 핵심 자동화 · 사용자 개입 없이 지급/환불
- 스케줄러가 정기적으로 종료된 프로젝트 스캔
- 배치 환불은 개별 pledge 단위 격리 (Failure Isolation)

## 포함 Story
- Story 3-1: `PledgeService.closeProject` — 종료 판정 + 성공/실패 분기
- Story 3-2: 배치 환불 (개별 TX `REQUIRES_NEW` · Failure Isolation)
- Story 3-3: `ProjectClosingScheduler` — 5분 주기

## Epic 인수 시나리오
- Given Project(LIVE, target=10000, raised=15000, endedAt=어제) · When `closeProject` · Then Project SUCCESSFUL · 모든 CONFIRMED pledge → FUNDED
- Given Project(LIVE, target=10000, raised=5000, endedAt=어제) · CONFIRMED pledge 3건 · When `closeProject` · Then Project FAILED · 3건 REFUNDED · 각 Wallet 원상

## Epic 완료 기준 (DoD)
- [ ] 3개 Story 완료
- [ ] E2E: 100명 후원 → 성공/실패 각각 배치 검증
- [ ] 개별 환불 실패 격리 검증
- [ ] 스케줄러 수동 트리거 검증

---

## [Story 3-1] `PledgeService.closeProject` — 종료 판정

### User Story
- As a ProjectClosingScheduler
- I want 지정 프로젝트를 종료 판정하고 성공/실패에 따라 분기 처리
- so that All-or-Nothing 규범 자동화

### 설명
- Project FOR UPDATE 락 획득
- 이미 종료된 프로젝트(LIVE 아님) → 조용히 skip (멱등)
- Project.isFundingSuccessful()로 판정
- 성공: `markSuccessful()` + CONFIRMED pledge 순회 → `markFunded()` (개별 TX)
- 실패: `markFailed()` + CONFIRMED pledge 순회 → 배치 환불 (Story 3-2)
- 최종적으로 Project.markFunded() or markRefunded() (모든 pledge 처리 후)

**주요 메서드**:
```java
public void closeProject(Long projectId) {
    Project project = projectRepository.findByIdForUpdate(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRJ001));

    if (project.getFundingStatus() != FundingStatus.LIVE) {
        // 이미 종료됨 · 멱등 skip
        return;
    }

    List<Pledge> confirmedPledges = pledgeRepository.findByProjectIdAndStatus(
        projectId, PledgeStatus.CONFIRMED
    );

    if (project.isFundingSuccessful()) {
        project.markSuccessful();
        for (Pledge pledge : confirmedPledges) {
            fundIndividualPledge(pledge.getId());   // 개별 TX
        }
        project.markFunded();   // 이 라인의 TX는 상위 · 별도 검토
    } else {
        project.markFailed();
        for (Pledge pledge : confirmedPledges) {
            refundIndividualPledge(pledge.getId());   // 개별 TX (Story 3-2)
        }
        project.markRefunded();
    }
}
```

### 완료 기준 (AC)
- Given LIVE Project · isFundingSuccessful=true · CONFIRMED pledge 3건 · When `closeProject` · Then Project FUNDED · 3건 모두 FUNDED
- Given LIVE Project · isFundingSuccessful=false · CONFIRMED pledge 3건 · When `closeProject` · Then Project REFUNDED · 3건 모두 REFUNDED · Wallet 원상
- Given 이미 SUCCESSFUL Project · When `closeProject` · Then no-op (멱등)

### Definition of Done
- [ ] `PledgeService.closeProject` 구현
- [ ] 멱등 검증 (재실행 안전)
- [ ] 통합 테스트: 성공 · 실패 · 이미 종료

### 스토리 포인트
1.5d

### 의존성
- 선행: Epic 2
- 후행: Story 3-2 · 3-3

---

## [Story 3-2] 배치 환불 (개별 TX `REQUIRES_NEW`)

### User Story
- As a `closeProject` 흐름
- I want 실패 프로젝트의 각 pledge를 개별 TX로 환불하여 실패 격리
- so that 한 pledge 환불 실패가 다른 pledge에 영향 없음

### 설명
- 개별 pledge마다 `REQUIRES_NEW` TX
- 실패 시 로그 마커 `PLEDGE_REFUND_FAILED pledgeId={} projectId={} amount={}`
- 상위 스케줄러는 개별 실패를 catch하고 계속 진행

**주요 메서드**:
```java
@Transactional(propagation = REQUIRES_NEW)
protected void refundIndividualPledge(Long pledgeId) {
    Pledge pledge = pledgeRepository.findByIdForUpdate(pledgeId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PLG001));

    if (pledge.getStatus() != PledgeStatus.CONFIRMED) return;   // 멱등

    try {
        walletService.refundPledge(pledge.getBackerUserId(), pledge.getAmount(),
                                    pledge.getProjectId(), pledgeId);
        pledge.markRefunded();
    } catch (Exception e) {
        log.error("PLEDGE_REFUND_FAILED pledgeId={} projectId={} amount={}",
            pledgeId, pledge.getProjectId(), pledge.getAmount(), e);
        // 개별 TX 롤백 · 상위는 계속
        throw e;
    }
}

@Transactional(propagation = REQUIRES_NEW)
protected void fundIndividualPledge(Long pledgeId) {
    Pledge pledge = pledgeRepository.findByIdForUpdate(pledgeId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PLG001));
    if (pledge.getStatus() != PledgeStatus.CONFIRMED) return;   // 멱등
    pledge.markFunded();
}
```

### 완료 기준 (AC)
- Given CONFIRMED pledge · When `refundIndividualPledge` · Then Wallet 복구 · Pledge REFUNDED
- *(엣지 · 중복 호출)* Given 이미 REFUNDED · When 재호출 · Then no-op (멱등)
- *(예외 · Wallet 이상)* Given Wallet 이상 · When 호출 · Then 로그 마커 · 개별 TX 롤백 · 예외 상위 전파 (상위가 catch)

### Definition of Done
- [ ] `refundIndividualPledge` · `fundIndividualPledge` 구현
- [ ] `REQUIRES_NEW` 검증 (통합 테스트에서 부모 TX와 격리 확인)
- [ ] 로그 마커 검증
- [ ] 100건 환불 중 1건 실패 시 99건 성공 검증

### 스토리 포인트
1d

### 의존성
- 선행: Story 3-1
- 후행: Story 3-3

---

## [Story 3-3] `ProjectClosingScheduler` (5분 주기)

### User Story
- As a 운영자
- I want 5분 주기로 마감된 프로젝트를 자동 종료
- so that 종료 판정·환불 자동화 · 수동 개입 없음

### 설명
- `@Scheduled(fixedDelay = 5 * 60 * 1000)`
- `Project.findByFundingStatusAndEndedAtBefore(LIVE, now)` 조회
- 각 프로젝트마다 `closeProject` 호출 (개별 실패 격리 · 상위는 try/catch)
- 성능 지표 · 처리 프로젝트 수 · 실패 수 로깅

**주요 클래스**:
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectClosingScheduler {
    private final ProjectRepository projectRepository;
    private final PledgeService pledgeService;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelay = 5 * 60 * 1000)   // 5분
    public void closeExpired() {
        long start = System.currentTimeMillis();
        List<Project> expired = projectRepository
            .findByFundingStatusAndEndedAtBefore(FundingStatus.LIVE, LocalDateTime.now());

        long success = 0, fail = 0;
        for (Project project : expired) {
            try {
                pledgeService.closeProject(project.getId());
                success++;
            } catch (Exception e) {
                log.error("PROJECT_CLOSING_FAILED projectId={}", project.getId(), e);
                fail++;
            }
        }

        long duration = System.currentTimeMillis() - start;
        meterRegistry.timer("pledge.closing.duration_seconds").record(duration, MILLISECONDS);

        String result = (fail == 0) ? "success" : (success > 0 ? "partial_fail" : "full_fail");
        meterRegistry.counter("pledge.scheduler.batch.total", "result", result).increment();

        log.info("PROJECT_CLOSING_BATCH_DONE total={} success={} fail={} duration={}ms",
            expired.size(), success, fail, duration);
    }
}
```

### 완료 기준 (AC)
- Given LIVE Project · endedAt=어제 · When 스케줄러 실행 · Then 프로젝트 종료 판정
- Given 프로젝트 3건 (endedAt=어제) · 1건 실패 · When 실행 · Then 2건 성공 · 1건 로그 마커 · counter `partial_fail` 증가
- Given 마감 프로젝트 0건 · When 실행 · Then 정상 종료 · counter=0

### Definition of Done
- [ ] `ProjectClosingScheduler` 구현
- [ ] `ProjectRepository.findByFundingStatusAndEndedAtBefore` 쿼리 (Product 6 반영)
- [ ] Timer/Counter 등록
- [ ] 통합 테스트: 정상 · 부분 실패 · 마감 프로젝트 0

### 스토리 포인트
1d

### 의존성
- 선행: Story 3-1 · 3-2
- 후행: 없음

---

# [Epic 4] REST 4개 엔드포인트 + 관측 지표 + ADR + 규범 최종 확정

## 목표
사용자 노출 REST API 4개를 제공하고, 5개 관측 지표를 프로덕션에 노출하며, `.claude/rules/consitency.md`·`idempotency.md` 최종 확정 및 ADR 3건으로 크라우드 펀딩 도메인 규범을 완성한다.

## 포함 Story
- Story 4-1: `POST /api/v1/projects/{projectId}/pledges` (후원 생성)
- Story 4-2: `DELETE /api/v1/pledges/{pledgeId}` (취소)
- Story 4-3: `GET /api/v1/users/me/pledges` (내 이력) + `GET /api/v1/projects/{projectId}/backers/count`
- Story 4-4: 관측 지표 5개 + ADR 3건 + 규범 최종

## Epic 완료 기준 (DoD)
- [ ] 4개 엔드포인트 완료
- [ ] 5개 지표 노출
- [ ] ADR 3건 발행
- [ ] `.claude/rules/consitency.md` §5·§2 최종
- [ ] `.claude/rules/idempotency.md` §2 카탈로그 갱신
- [ ] `backend-boundary/error-codes.md` PLG 매핑

---

## [Story 4-1] `POST /api/v1/projects/{projectId}/pledges` (후원 생성)

### User Story
- As a 후원자
- I want 프로젝트에 후원 요청 · body에 티어·메시지 전달
- so that 통합 흐름으로 원자적 후원 완료

### 설명
- 인증 필요
- Request: `PledgeCreateRequest { rewardTierId, message? }` (Bean Validation)
- Response: 201 Created + Location `/pledges/{id}` + body `PledgeResponse`

### 완료 기준 (AC)
- Given 인증 · LIVE Project · 유효 request · When `POST` · Then 201 · Location + body
- *(예외 · 미인증)* Then 401 · `C004`
- *(예외 · Project not LIVE)* Then 409 · `PLG004`
- *(예외 · 재고 0)* Then 409 · `RWD002`
- *(예외 · 잔액 부족)* Then 409 · `WAL002`

### Definition of Done
- [ ] `PledgeController.create`
- [ ] `PledgeCreateRequest` (record + Bean Validation)
- [ ] `@WebMvcTest` 슬라이스

### 스토리 포인트
1d

### 의존성
- 선행: Epic 2 Story 2-1
- 후행: 없음

---

## [Story 4-2] `DELETE /api/v1/pledges/{pledgeId}` (취소)

### User Story
- As a 후원자
- I want 내 후원을 취소
- so that 변심·오후원 시 원상 복구

### 설명
- 인증 필요 · 본인만
- Response: 204 No Content

### 완료 기준 (AC)
- Given CONFIRMED · 본인 · LIVE Project · When `DELETE` · Then 204 · Wallet·Project·Reward 원상
- *(예외 · 상태)* Given FUNDED · Then 400 · `PLG002`
- *(예외 · 소유권)* Given 타 사용자 · Then 403 · `PLG003`

### Definition of Done
- [ ] `PledgeController.cancel`
- [ ] `@WebMvcTest`

### 스토리 포인트
0.5d

### 의존성
- 선행: Epic 2 Story 2-2
- 후행: 없음

---

## [Story 4-3] `GET /users/me/pledges` + `GET /projects/{id}/backers/count`

### User Story
- As a 후원자·메이커·모든 사용자
- I want 내 후원 이력 조회 (인증 필요) · 프로젝트 후원자 수 조회 (공개)
- so that 마이페이지 · 프로젝트 상세 지표 표시

### 설명
- `GET /users/me/pledges?status=&page=&size=` — 인증 필요 · 페이징
- `GET /projects/{projectId}/backers/count` — 공개

### 완료 기준 (AC)
- Given 후원자 · 이력 5건 · When `GET /me/pledges` · Then 5건 · 페이징
- Given Project.backerCount=100 · When `GET /projects/{id}/backers/count` · Then `{count: 100}`

### Definition of Done
- [ ] `PledgeController.myHistory · getBackersCount`
- [ ] `@WebMvcTest`

### 스토리 포인트
0.5d

### 의존성
- 선행: Epic 2 Story 2-3
- 후행: 없음

---

## [Story 4-4] 관측 지표 5개 + ADR 3건 + 규범 최종 확정

### User Story
- As a 팀 리더 · 운영자
- I want Pledge 관측 지표 5개 등록 · ADR 3건 · 규범 최종 확정
- so that 크라우드 펀딩 도메인 완결

### 설명
- 지표 5종 (`§관측 지표` 참조)
- ADR 3건:
  - `015-pledge-wallet-immediate-deduction.md` — Wallet 즉시 차감 정책
  - `016-pledge-state-machine-and-locking.md` — 7-state 상태기계 · 락 순서 최종
  - `017-project-closing-scheduler-and-batch-refund.md` — 스케줄러 5분 주기 · 배치 환불 격리
- 규범 최종:
  - `.claude/rules/consitency.md` §5 락 순서 표: `Wallet → Project → RewardTier → Pledge → Order → Payment → Product`
  - `.claude/rules/consitency.md` §2 zone 매핑: "후원 확정 zone" · "프로젝트 종료 zone" 추가
  - `.claude/rules/idempotency.md` §2 카탈로그: "Pledge 생성 (S+)" · "프로젝트 종료 판정 (A · 상태 기반)"
  - `backend-boundary/error-codes.md` PLG001~005

### 완료 기준 (AC)
- Given 프로덕션 · `curl /actuator/prometheus` · Then 5개 지표 노출
- Given ADR 3건 · When 확인 · Then 각각 완결
- Given `consistency.md` §5 · When 확인 · Then 최종 락 순서 반영

### Definition of Done
- [ ] 5개 지표 counter/timer 등록
- [ ] ADR 3건 파일
- [ ] `.claude/rules/consitency.md` §5·§2 갱신
- [ ] `.claude/rules/idempotency.md` §2 카탈로그 갱신
- [ ] `backend-boundary/error-codes.md` PLG 섹션 추가

### 스토리 포인트
1d

### 의존성
- 선행: Epic 1~3 완결
- 후행: 없음 (Product-level 완결)

---

## 요약

| Epic | Story | SP 합계 |
|---|---|---|
| Epic 1: 엔티티·상태기계·ErrorCode | 3 | 3.5 |
| Epic 2: Service 통합 흐름 | 3 | 3.5 |
| Epic 3: 스케줄러·배치 환불 | 3 | 3.5 |
| Epic 4: REST·관측·ADR·규범 | 4 | 3.0 |
| **합계** | **13** | **13.5 SP** |

**진행 순서 (필수)**: Epic 1 → 2 → 3 → 4
- Epic 1은 모든 후속의 전제
- Epic 2 완료 후 Epic 3·4는 병렬 가능
- Epic 4는 마지막 (규범 최종 굳힘 · 크라우드 펀딩 도메인 완결)

## 크라우드 펀딩 도메인 완결

본 Product 8(Pledge) 완결 시 크라우드 펀딩 컨셉의 근본 자금 흐름 전체(Wallet+Project+RewardTier+Pledge)가 코드로 성립. 이후 SDD(Chat · Maker · Media · GitHub · Discovery)는 순수 부가 도메인 · 크라우드 펀딩 코어와 독립적 확장.
