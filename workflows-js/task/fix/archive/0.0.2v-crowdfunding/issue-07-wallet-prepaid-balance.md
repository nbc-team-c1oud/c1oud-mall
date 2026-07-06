# Issue: 선불 지갑 (Wallet · Prepaid Balance) 도메인 신설 — Point 확장

## 배경

> **사용자 지시 (Round 5)**: "이미 구현한 payment를 기반으로 돈을 먼저 넣어놓고, 그 돈을 기반으로 서로 크라우드 펀딩·아이디어에 지불하는 관점의 쇼핑몰"
> **R1 결정 (Round 5)**: **가상 포인트 + PortOne 충전** — 실 자금은 지갑 충전만, 이후 내부는 가상 포인트

- 크라우드 펀딩 모드로 컨셉 확정 → 후원자는 프로젝트에 "지불"하는데 매번 PortOne 결제 창을 띄우는 UX는 부담
- 미리 지갑에 예치금(가상 포인트)을 넣어두고 · 후원 시엔 지갑 잔액에서 즉시 차감 = **선불 지갑 모델**
- 실제 자금이 이동하지 않으므로 전자금융업 규제 회피 (신입 스코프 안전)
- 이미 있는 `Point` 도메인은 잔액 개념만 있고 감사 이력·비관 락·전용 트랜잭션 타입이 부족 → **확장 or 신규 도메인**

## 조사 결과 — Point BC 현황 + 벤치마크

| 항목 | Point 현재 상태 | 벤치마크 (토스머니·페이코·카카오페이머니) |
|---|---|---|
| 잔액 관리 | `PointAccount(userId, balance)` | 별도 Wallet 엔티티 · 잔액 컬럼 |
| 이체 이력 | `PointHistory` (USE · EARN · CANCEL 4종) | Transaction 이력 (충전·이체·환불·시스템조정) |
| 동시성 | 낙관 락(추정) or 없음 | 비관 락(`SELECT FOR UPDATE`) 필수 |
| 잔액 SSOT | `PointAccount.balance` 컬럼 | balance 컬럼 + 이력 합산 검증 (invariant) |
| 정산 | 없음 | 감사 · 회계 리포트 · 정기 검증 |
| 외부 결제 연동 | 없음 | 충전 API (실 결제 후 잔액 증가) |

## 옵션 비교

**Option A — Point 확장 (Point 도메인에 Transaction 이력 강화 · 새 트랜잭션 타입 추가)** `(채택)`
- 장점: 기존 자산 최대 재활용 · 마이그레이션 부담 최소 · Point 도메인 유지
- 비용: Point 이름이 `Wallet` 개념까지 확장 (약간의 명명 부담)
- 사용자 관점: "포인트"라는 익숙한 개념 · UX 자연스러움

**Option B — 신규 Wallet 도메인 신설 (Point 도메인은 유지 · 별개)**
- 거부 이유: 두 잔액 도메인 병존 → 정합성·중복 리스크 · 이력 분리 부담

**Option C — Point 이름을 Wallet으로 완전 리네임**
- 거부 이유: 리네임 마이그레이션 부담 큼 · 기존 Payment 확정 흐름에도 영향

## 선택: Option A

## 부속 결정

### 도메인 컨텍스트
- 기존 `nbc.c1oud_mall.point.*` 컨텍스트 확장
- 명칭 유지 (`point` 패키지) · 내부 개념은 "Wallet"으로 확장 (JavaDoc에 명시)
- **잔액 SSOT**: `PointAccount.balance` 컬럼 · `PointTransaction` 합계와 정기 검증 (invariant)

### 엔티티 · 스키마
```sql
-- 기존 point_account 유지 (필드 추가)
ALTER TABLE point_account ADD COLUMN version BIGINT NOT NULL DEFAULT 0;  -- 낙관 락 (선택)
ALTER TABLE point_account ADD COLUMN last_charged_at TIMESTAMP NULL;      -- 최근 충전 시각

-- point_history 확장 (트랜잭션 타입 추가)
-- 기존: USE · EARN · USE_CANCEL · EARN_CANCEL
-- 추가: CHARGE (충전) · WITHDRAW (인출 · v0.0.5+) · PLEDGE (후원 차감) · PLEDGE_REFUND (환불) · SYSTEM_ADJUST (시스템 조정)
ALTER TABLE point_history MODIFY COLUMN transaction_type VARCHAR(30) NOT NULL;
```

### 신규 트랜잭션 타입 (`PointTransactionType` enum 확장)
```java
public enum PointTransactionType {
    // 기존
    USE, EARN, USE_CANCEL, EARN_CANCEL,
    // 신규 (0.0.2v)
    CHARGE,          // PortOne 충전으로 잔액 증가
    PLEDGE,          // 프로젝트 후원 (지갑 → 프로젝트 pending)
    PLEDGE_REFUND,   // 프로젝트 실패 · 자동 환불 (프로젝트 → 지갑)
    PLEDGE_CANCEL,   // 후원 취소 (사용자 명시)
    // 향후
    WITHDRAW,        // 인출 (v0.0.5+ · 필요 시)
    SYSTEM_ADJUST    // 관리자 수동 조정
}
```

### 도메인 모델
```java
// point.domain.PointAccount (기존 확장)
@Entity
public class PointAccount {
    @Id @GeneratedValue Long id;
    Long userId;
    long balance;   // 잔액 SSOT
    @Version Long version;   // 낙관 락 (선택 · 비관 락 병행 검토)
    LocalDateTime lastChargedAt;

    public void charge(long amount, String txReferenceId) {
        if (amount <= 0) throw new BusinessException(ErrorCode.WALLET_INVALID_AMOUNT);
        this.balance += amount;
        this.lastChargedAt = LocalDateTime.now();
    }

    public void pledge(long amount, Long projectId, Long pledgeId) {
        if (amount <= 0) throw new BusinessException(ErrorCode.WALLET_INVALID_AMOUNT);
        if (this.balance < amount) throw new BusinessException(ErrorCode.WALLET_INSUFFICIENT_BALANCE);
        this.balance -= amount;
    }

    public void refundPledge(long amount, Long projectId, Long pledgeId) {
        this.balance += amount;
    }
}

// point.application.WalletService (신규)
@Service
@Transactional
public class WalletService {
    @Transactional
    public void charge(Long userId, long amount, Long paymentId /* PortOne */) {
        // 1. Payment 검증 (COMPLETED 상태 · 본인 결제)
        // 2. PointAccount 비관 락 획득
        PointAccount account = accountRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> PointAccount.create(userId));
        // 3. 잔액 증가
        account.charge(amount, paymentId.toString());
        // 4. 이력 저장 (CHARGE)
        historyRepository.save(PointHistory.of(userId, amount, CHARGE, paymentId.toString()));
    }

    @Transactional
    public void pledge(Long userId, long amount, Long projectId, Long pledgeId) {
        PointAccount account = accountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        account.pledge(amount, projectId, pledgeId);
        historyRepository.save(PointHistory.of(userId, -amount, PLEDGE, pledgeId.toString()));
    }
}
```

### API 표면
| 메서드 | 경로 | 인증 | 용도 |
|---|---|---|---|
| GET | `/api/v1/wallets/me` | JWT | 내 잔액 조회 |
| GET | `/api/v1/wallets/me/history?type=&page=` | JWT | 이력 조회 (필터·페이징) |
| POST | `/api/v1/wallets/charge` | JWT | 충전 요청 (body: `{amount}`) → PortOne 결제 창 URL 반환 |
| POST | `/api/v1/wallets/charge/confirm` | JWT | 충전 확정 (body: `{portonePaymentId}`) — Payment 확정 흐름 재사용 |

### ErrorCode (신규)
- `WAL001` WALLET_NOT_FOUND (404)
- `WAL002` WALLET_INSUFFICIENT_BALANCE (409 · 잔액 부족)
- `WAL003` WALLET_INVALID_AMOUNT (400 · 0 또는 음수)
- `WAL004` WALLET_CHARGE_FAILED (500)

### 정합성 · 멱등성
- **비관 락 필수** — `SELECT FOR UPDATE` on point_account (동시 충전·후원 race 방지)
- 락 순서 (consistency §5 갱신): **Wallet(PointAccount) → Project → Order/Pledge → Product**
- 충전 확정 = Payment 확정 흐름 재사용 (`portonePaymentId` 멱등 · ADR 005)
- 이력 저장 원자성 (잔액 변경 + history insert 같은 TX)

### 잔액 SSOT 검증 (invariant)
- 배치 스케줄러 (주간): `PointAccount.balance == SUM(PointHistory.amount WHERE user_id)` 검증
- 불일치 시 알람 · SYSTEM_ADJUST 이력으로 수동 조정

### 관측
- `wallet.charge.total{result=success|fail}` counter
- `wallet.pledge.total{result=success|fail}` counter
- `wallet.balance.gauge` — 미공개 (개인정보)

## 이관 산출물

- **BE-Story #07-1**: `PointTransactionType` enum 확장 (`CHARGE`, `PLEDGE`, `PLEDGE_REFUND`, `PLEDGE_CANCEL`, `SYSTEM_ADJUST`)
- **BE-Story #07-2**: `PointAccount` 도메인 메서드 추가 (`charge`, `pledge`, `refundPledge`) + `@Version` (선택)
- **BE-Story #07-3**: `WalletService` 신규 (충전 · 후원 · 환불 · 조회 · 이력)
- **BE-Story #07-4**: `PointAccountRepository.findByUserIdForUpdate` (비관 락) 추가
- **BE-Story #07-5**: `WalletController` REST 4개 엔드포인트
- **BE-Story #07-6**: `ErrorCode.WAL001~004` 등록
- **BE-Story #07-7**: 충전 확정 흐름 = Payment 확정 흐름 재사용 · 통합 테스트 (charge → confirm → balance 증가)
- **BE-Story #07-8**: 동시성 통합 테스트 (같은 사용자 동시 충전·후원 → 잔액 정확성)
- **BE-Story #07-9**: 배치 스케줄러 (잔액 SSOT invariant 주간 검증)
- **FE-Story #07-1**: `src/features/wallet/WalletPage.tsx` — 잔액 · 이력 조회
- **FE-Story #07-2**: `src/features/wallet/ChargeModal.tsx` — 충전 (PortOne SDK 재사용)
- **Docs-Story #07-1**: `backend-boundary/error-codes.md` WAL001~004 UX 매핑
- **Docs-Story #07-2**: `.claude/rules/consitency.md` §5 락 순서 표에 Wallet(PointAccount) 추가 (최상위)
- **SDD 개정**: 향후 `product-wallet.md` 신규 작성 (M3 진입 시)

## 관련 이슈 / 문서

- 다음: [#08 Project 도메인](./issue-08-project-domain.md) — Wallet 후원 대상
- 다음: [#10 Pledge 상태기계](./issue-10-pledge-state-machine.md) — Wallet.pledge 호출 진입점
- 관련: [#09 Reward Tier](./issue-09-reward-tier.md) — Pledge가 Reward를 선택 · Wallet에서 티어 금액 차감
- **원본 Payment 도메인**: 충전 확정 시 재사용 · 별도 리네임 없음
- 규범 참조: `.claude/rules/consitency.md` §5 (락 순서 갱신 필요), `.claude/rules/idempotency.md` §2 (충전 = 결제 확정과 동일 규범)
- ADR 후보: "Wallet 잔액 SSOT · 비관 락 · 이력 검증 invariant"

## 디자인 참조
- `C:\Users\user\Desktop\fe\프로젝트 상세.html` — 후원 시점의 지갑 잔액 노출 (향후 UX)
- 지갑 페이지 자체 디자인은 아직 없음 (v0.0.3에 추가 검토)
