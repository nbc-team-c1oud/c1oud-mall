# Issue: Pledge (후원) 도메인 신설 · 상태기계 · All-or-Nothing · 자동 종료 스케줄러

## 배경

> **R3 결정 (Round 5)**: **All-or-Nothing (Kickstarter 방식)** — 목표 달성 시만 지급 · 실패 시 전원 자동 환불.
> **컨셉 재정의**: Order(주문) → Pledge(후원) 리매핑.

- 크라우드 펀딩의 핵심 도메인 · 후원자가 프로젝트 특정 티어에 지불
- 지금 당장 프로젝트에 돈이 가는 게 아니라 **프로젝트 종료 시 목표 달성 여부에 따라 지급 or 환불** 결정
- 상태기계: `PENDING → CONFIRMED → (프로젝트 대기) → FUNDED / REFUNDED`
- 프로젝트 마감일 스케줄러 · 자동 판정 · 배치 환불 = **BE 스토리 파괴력 최대** 지점

## 조사 결과 — Kickstarter · 텀블벅 벤치마크

| 항목 | Kickstarter (All-or-Nothing) | 텀블벅 | c1oud-mall 대응 |
|---|---|---|---|
| Pledge 시점 | 결제 정보 등록만 (실 청구 X) | 즉시 계좌 예약 | **Wallet 잔액 즉시 차감 (예약)** — 지갑 모델이므로 |
| 프로젝트 실패 시 | 결제 취소 | 예약 취소 | 지갑 자동 환불 (`PLEDGE_REFUND` 트랜잭션) |
| 프로젝트 성공 시 | 결제 실 청구 | 실 결제 | **지갑 → 프로젝트 잔액 이체** (지갑 이미 차감 상태이므로 프로젝트에 이체 확정) |
| 후원 취소 | 프로젝트 종료 전 가능 | 종료 전 가능 | 프로젝트 LIVE 중에만 가능 · 재고(리워드) 복구 |
| 상태 개수 | ~5개 | ~4개 | **7개** (아래 결정) |

## 옵션 비교

### 갈림길 1: Wallet 차감 시점

**Option A (채택) — Pledge 즉시 지갑 차감 · 프로젝트 실패 시 환불**
- 장점: 명확한 자금 흐름 · 후원 취소·환불 처리 단순
- 비용: 후원자 지갑에 즉시 잔액 감소 (UX는 "결제 완료" 형태)
- 후원자 트래픽 대비 Wallet 락 부담 → 비관 락 필수

**Option B — Pledge는 "예약"만 · 프로젝트 성공 시 일괄 차감**
- 거부 이유: 후원자 지갑 잔액 부족 케이스 대량 발생 리스크 · 성공 시점 전원 결제 실패 처리 어려움

### 갈림길 2: 상태기계 개수

**Option A (채택) — 7개 상태**
```
DRAFT → PENDING → CONFIRMED ─┬─► FUNDED (프로젝트 성공 · 지급 완료)
                             │
                             ├─► REFUNDED (프로젝트 실패 · 자동 환불)
                             │
                             └─► CANCELLED (후원자 취소 · 환불)
```

- DRAFT: 결제 화면 진입 (미확정)
- PENDING: 지갑 차감 진행 중
- CONFIRMED: 지갑 차감 완료 (프로젝트 종료 대기)
- FUNDED: 프로젝트 성공 · 지급 완료
- REFUNDED: 프로젝트 실패 · 지갑 환불
- CANCELLED: 후원자 취소 (LIVE 중에만) · 지갑 환불

**Option B — 5개 상태 (DRAFT 없이 즉시 CONFIRMED)**
- 거부 이유: DRAFT 없이 만들면 결제 페이지 이탈 케이스 처리 어려움

## 선택: Option A (모든 갈림길)

## 부속 결정

### 도메인 컨텍스트
- 신규 컨텍스트: `nbc.c1oud_mall.pledge.*` (4레이어)
- 기존 Order 도메인은 v0.0.3에 리네임 or 폐기 (초기라 데이터 없으면 폐기)

### 엔티티 · 스키마
```sql
CREATE TABLE pledge (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  project_id        BIGINT       NOT NULL,
  reward_tier_id    BIGINT       NOT NULL,
  backer_user_id    BIGINT       NOT NULL,
  amount            BIGINT       NOT NULL,           -- 후원 금액 (Wallet 차감)
  status            VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
  message           VARCHAR(500) NULL,               -- 응원 메시지 (선택)
  wallet_tx_id      BIGINT       NULL,               -- 지갑 차감 이력 참조 (PointHistory.id)
  confirmed_at      TIMESTAMP    NULL,
  funded_at         TIMESTAMP    NULL,
  refunded_at       TIMESTAMP    NULL,
  cancelled_at      TIMESTAMP    NULL,
  created_at        TIMESTAMP    NOT NULL,
  updated_at        TIMESTAMP    NOT NULL,
  PRIMARY KEY (id),
  KEY idx_pledge_project_status (project_id, status),
  KEY idx_pledge_backer (backer_user_id, created_at DESC),
  KEY idx_pledge_status_project (status, project_id),   -- 프로젝트 종료 시 CONFIRMED 조회
  CONSTRAINT chk_pledge_amount_positive CHECK (amount > 0)
);
```

### 도메인 모델
```java
// pledge.domain.Pledge (Aggregate root)
@Entity
public class Pledge extends BaseEntity {
    @Id @GeneratedValue Long id;
    Long projectId;
    Long rewardTierId;
    Long backerUserId;
    long amount;
    @Enumerated(STRING) PledgeStatus status;
    String message;
    Long walletTxId;
    LocalDateTime confirmedAt;
    LocalDateTime fundedAt;
    LocalDateTime refundedAt;
    LocalDateTime cancelledAt;

    public static Pledge draft(Long projectId, Long rewardTierId, Long backerId,
                                long amount, String message) { ... }

    public void confirm(Long walletTxId) {
        if (status != DRAFT && status != PENDING)
            throw new BusinessException(ErrorCode.PLEDGE_INVALID_STATUS);
        this.status = CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
        this.walletTxId = walletTxId;
    }

    public void markFunded() {
        if (status != CONFIRMED) throw new BusinessException(ErrorCode.PLEDGE_INVALID_STATUS);
        this.status = FUNDED;
        this.fundedAt = LocalDateTime.now();
    }

    public void markRefunded() {
        if (status != CONFIRMED) throw new BusinessException(ErrorCode.PLEDGE_INVALID_STATUS);
        this.status = REFUNDED;
        this.refundedAt = LocalDateTime.now();
    }

    public void cancel(Long backerId) {
        if (!Objects.equals(this.backerUserId, backerId))
            throw new BusinessException(ErrorCode.PLEDGE_OWNERSHIP_FAILED);
        if (status != CONFIRMED)
            throw new BusinessException(ErrorCode.PLEDGE_INVALID_STATUS);
        this.status = CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }
}
```

### 후원 생성 흐름 (`PledgeService.pledge`)
```java
@Transactional
public Long pledge(Long backerId, PledgeCreateCommand cmd) {
    // 1. Project 조회 · 상태 검증 (LIVE만)
    Project project = projectRepository.findByIdForUpdate(cmd.projectId())
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    if (project.getFundingStatus() != LIVE)
        throw new BusinessException(ErrorCode.PROJECT_NOT_LIVE);

    // 2. RewardTier 조회 · 재고 검증 (락 획득 후)
    RewardTier tier = rewardTierRepository.findByIdForUpdate(cmd.rewardTierId())
            .orElseThrow(() -> new BusinessException(ErrorCode.REWARD_NOT_FOUND));
    tier.reservePledge();   // 재고 차감 · SOLD_OUT 방어

    // 3. Wallet 잔액 차감 (비관 락)
    walletService.pledge(backerId, tier.getPrice(), cmd.projectId(), null /* pledgeId */);

    // 4. Pledge 저장 (CONFIRMED 상태로 · Wallet 이력 참조)
    Pledge pledge = Pledge.draft(cmd.projectId(), cmd.rewardTierId(), backerId,
                                  tier.getPrice(), cmd.message());
    pledge.confirm(walletTxId);
    pledgeRepository.save(pledge);

    // 5. Project 반정규화 갱신 (raisedAmount · backerCount)
    project.addPledgeAmount(tier.getPrice());

    return pledge.getId();
}
```

### 자동 종료 스케줄러 (핵심 스토리)
```java
// pledge.infrastructure.ProjectClosingScheduler
@Component
@RequiredArgsConstructor
public class ProjectClosingScheduler {
    private final ProjectRepository projectRepository;
    private final PledgeService pledgeService;

    // 매 5분마다 · 종료된 프로젝트 판정
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void closeExpiredProjects() {
        List<Project> expired = projectRepository
            .findByFundingStatusAndEndedAtBefore(LIVE, LocalDateTime.now());

        for (Project project : expired) {
            try {
                pledgeService.closeProject(project.getId());   // 아래 참조
            } catch (Exception e) {
                log.error("Failed to close project {}", project.getId(), e);
            }
        }
    }
}

// pledge.application.PledgeService.closeProject
@Transactional
public void closeProject(Long projectId) {
    Project project = projectRepository.findByIdForUpdate(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

    if (project.getFundingStatus() != LIVE) return;   // 이미 닫힘 (멱등)

    if (project.isFundingSuccessful()) {
        // 성공: 모든 CONFIRMED → FUNDED
        project.markSuccessful();
        pledgeRepository.findByProjectIdAndStatus(projectId, CONFIRMED)
            .forEach(Pledge::markFunded);
    } else {
        // 실패: 모든 CONFIRMED → REFUNDED (배치 환불)
        project.markFailed();
        for (Pledge pledge : pledgeRepository.findByProjectIdAndStatus(projectId, CONFIRMED)) {
            walletService.refundPledge(pledge.getBackerUserId(), pledge.getAmount(),
                                        projectId, pledge.getId());
            pledge.markRefunded();
        }
    }
}
```

### 중복 실행 방지 (다중 인스턴스)
- **초기 v0.0.3**: 단일 인스턴스 전제 → 자체 DB 락 (`SELECT FOR UPDATE on project`)
- v0.0.5+: ShedLock 도입 검토 (다중 인스턴스 배포 시)

### API 표면
| 메서드 | 경로 | 인증 | 용도 |
|---|---|---|---|
| POST | `/api/v1/projects/{projectId}/pledges` | JWT | 후원 생성 (body: `{rewardTierId, message?}`) |
| DELETE | `/api/v1/pledges/{pledgeId}` | JWT | 후원 취소 (LIVE 중 · 본인만) |
| GET | `/api/v1/users/me/pledges?status=&page=` | JWT | 내 후원 이력 |
| GET | `/api/v1/projects/{projectId}/pledges/count` | 공개 | 프로젝트 후원자 수 (반정규화 값) |

### ErrorCode (신규)
- `PLG001` PLEDGE_NOT_FOUND (404)
- `PLG002` PLEDGE_INVALID_STATUS (400 · 상태 전이 오류)
- `PLG003` PLEDGE_OWNERSHIP_FAILED (403 · 본인 후원 아님)
- `PLG004` PROJECT_NOT_LIVE (409 · LIVE 아닌 프로젝트에 후원)
- `PLG005` PLEDGE_ALREADY_EXISTS (409 · 중복 후원 · 정책 결정 필요)

### 정합성 · 멱등성
- **락 순서 (consistency §5 갱신)**: `Wallet → Project → RewardTier → Pledge`
- Pledge 생성 = 서버 채번 id (S 등급) · idempotency §2 카탈로그에 추가
- 프로젝트 종료 판정은 멱등 (이미 닫힌 프로젝트는 no-op)
- 배치 환불 실패 처리: 개별 pledge 환불 실패 시 나머지 계속 · 실패 로그 마커 (`PLEDGE_REFUND_FAILED`)

### 관측
- `pledge.created.total{status}` counter
- `pledge.funded.total` counter (프로젝트 성공 시)
- `pledge.refunded.total{reason=failed|cancelled}` counter
- `project.closing.duration_seconds` histogram (배치 처리 시간)

## 이관 산출물

- **BE-Story #10-1**: `pledge` 컨텍스트 신규 패키지 + `Pledge` 엔티티 + `PledgeStatus` enum
- **BE-Story #10-2**: `Pledge.confirm`·`markFunded`·`markRefunded`·`cancel` 도메인 메서드
- **BE-Story #10-3**: `PledgeService.pledge` (Wallet · Project · RewardTier 통합 흐름)
- **BE-Story #10-4**: `PledgeService.closeProject` (프로젝트 종료 판정 · 배치 환불)
- **BE-Story #10-5**: `ProjectClosingScheduler` (@Scheduled 5분 주기)
- **BE-Story #10-6**: `PledgeController` (4개 엔드포인트)
- **BE-Story #10-7**: `ErrorCode.PLG001~005` 등록
- **BE-Story #10-8**: 통합 테스트 (성공 프로젝트 자동 지급 · 실패 프로젝트 배치 환불 · 동시 후원 race · 스케줄러 중복 실행)
- **BE-Story #10-9**: 100명 동시 후원 시뮬레이션 · 스케줄러 판정 후 정합성 검증
- **FE-Story #10-1**: `src/features/pledge/PledgeCheckoutPage.tsx` (후원 폼)
- **FE-Story #10-2**: `src/features/pledge/PledgeHistoryPage.tsx` (내 후원 이력)
- **Docs-Story #10-1**: `backend-boundary/error-codes.md` PLG001~005 매핑
- **Docs-Story #10-2**: `.claude/rules/consitency.md` §2 zone 매핑 갱신 (Pledge 확정 zone · 자동 종료 zone)
- **Docs-Story #10-3**: `.claude/rules/idempotency.md` §2 카탈로그에 Pledge 생성 · 프로젝트 종료 판정 추가
- **SDD 개정**: 향후 `product-pledge.md` 신규 (M3 진입 시)

## 관련 이슈 / 문서

- 선행: [#07 Wallet](./issue-07-wallet-prepaid-balance.md) — 후원 시 지갑 차감
- 선행: [#08 Project](./issue-08-project-domain.md) — 후원 대상 · 상태 판정
- 선행: [#09 Reward Tier](./issue-09-reward-tier.md) — 티어 선택 · 재고 차감
- 관련: [#05 리뷰](./issue-05-review.md) — FUNDED 후원자만 리뷰 가능
- 관련: [#11 Maker Profile](./issue-11-maker-profile-response-stats.md) — 후원자·메이커 관계
- 벤치마크: Kickstarter · 텀블벅 (All-or-Nothing 원조)
- 규범 참조: `.claude/rules/consitency.md` §2 zone 매핑, §5 락 순서, `.claude/rules/idempotency.md`

## 디자인 참조
- `C:\Users\user\Desktop\fe\프로젝트 상세.html` — 후원 버튼 · 후원 상태 (D-7 · 342% 등)
- `C:\Users\user\Desktop\fe\메이커 채팅.html` — 채팅에서 "이 리워드로 후원" 카드
