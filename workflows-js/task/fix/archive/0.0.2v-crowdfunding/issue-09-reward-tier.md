# Issue: 리워드 티어 (Reward Tier) 도메인 신설 — Kickstarter 방식

## 배경

> **디자인 발견 (Round 5)**: 프로젝트 상세 페이지에 리워드 3티어 (서포터 · 얼리 어답터 · 팀 플랜) 존재.
> Pledge 시 리워드 티어 선택 필수 · 티어별 재고·발송 예정일 관리.

- 크라우드 펀딩 컨셉에서 후원자는 프로젝트에 그냥 돈만 넣는 게 아니라 **특정 리워드 티어를 선택**함 (Kickstarter 방식)
- 각 티어: 가격 · 이름 · 설명 · 재고 · 후원자 수 · 발송 예정일 · "가장 인기" 뱃지
- 티어별 재고 관리 (예: 얼리 어답터 12개 · 팀 플랜 4개)
- 디자인에서 채팅으로도 "이 리워드로 후원" 카드를 발송 가능 (이슈 #12와 결합)
- 초기 브레인스토밍(이슈 #01~06)에는 완전히 누락된 개념 → **필수 신규 이슈**

## 조사 결과 — Kickstarter · 텀블벅 · Wadiz 벤치마크

| 항목 | Kickstarter | 텀블벅 | Wadiz | c1oud-mall 대응 |
|---|---|---|---|---|
| 티어 개수 | 1~15개 | 1~10개 | 1~10개 | 1~10개 (초기 · 확장 가능) |
| 재고 관리 | 총 수량 · 남은 수량 | 총 수량 · 남은 수량 | 총 수량 · 남은 수량 | 채택 |
| 후원자 수 | 티어별 카운트 | 티어별 | 티어별 | 반정규화 카운트 |
| 발송 예정일 | 필수 | 필수 | 필수 | 필수 (`estimatedDeliveryAt`) |
| "가장 인기" 뱃지 | 자동 계산 | 없음 | 없음 | 자동 (backerCount 최대 티어) or 수동 (isFeatured) |
| 디지털 vs 실물 | 구분 | 구분 | 구분 | v0.0.4+ (초기 단순) |
| 배송비 | 별도 | 별도 | 별도 | v0.0.5+ (초기 없음) |
| 다중 티어 후원 | 불가 (한 프로젝트에 1티어) | 불가 | 불가 | 불가 (표준 채택) |
| 티어 수정 | LIVE 후에는 불가 | 불가 | 불가 | 채택 (DRAFT/UPCOMING만 편집 가능) |

## 옵션 비교

### 갈림길 1: 재고 관리 방식

**Option A (채택) — 총 수량 · 남은 수량 컬럼 병존**
- `total_quantity` (총 발행 · 초기 설정) · `remaining_quantity` (남은 수량 · 후원 시 감소)
- 장점: 명확 · 조회 성능 · 락 대상 명확
- 비용: 데이터 일관성 검증 필요 (remaining = total - pledged)

**Option B — 총 수량만 · 남은 수량은 실시간 계산**
- 매 조회마다 `COUNT(pledge)` — 조회 성능 저하

### 갈림길 2: 재고 0 처리

**Option A (채택) — 재고 0 티어는 UI에서 "품절" 뱃지 · 후원 불가 (`REWARD_SOLD_OUT`)**
- 명확 · 사용자 이해 쉬움

**Option B — 재고 0 티어는 UI에서 숨김**
- 거부 이유: "품절" 정보도 UX 가치 · Kickstarter도 표시

### 갈림길 3: "가장 인기" 뱃지 결정 방식

**Option A (채택) — 자동 (해당 프로젝트에서 backerCount 최대 티어)**
- Kickstarter 방식 · 데이터 기반
- 재계산 방식: 조회 시 계산 or 반정규화 (`is_featured` 컬럼 · 후원 갱신 시)

**Option B — 수동 (메이커가 "인기" 표시 선택)**
- 거부 이유: 조작 가능성 · 신뢰도 낮음

## 선택: Option A (모든 갈림길)

## 부속 결정

### 도메인 컨텍스트
- 신규 컨텍스트: `nbc.c1oud_mall.reward.*` (4레이어)
- Project와 결합 (RewardTier가 Project에 속함 · Aggregate 소속 검토)
- **결정**: RewardTier를 별도 Aggregate root (Project와 참조 관계만 · 이유: Reward 단독 조회·수정·재고 관리가 자주 필요)

### 엔티티 · 스키마
```sql
CREATE TABLE reward_tier (
  id                     BIGINT       NOT NULL AUTO_INCREMENT,
  project_id             BIGINT       NOT NULL,           -- Project FK
  title                  VARCHAR(100) NOT NULL,
  description            TEXT         NOT NULL,
  price                  BIGINT       NOT NULL,           -- 티어 가격 (Wallet 포인트)
  total_quantity         INT          NULL,               -- NULL = 무제한
  remaining_quantity     INT          NULL,               -- NULL = 무제한
  backer_count           INT          NOT NULL DEFAULT 0, -- 반정규화
  estimated_delivery_at  DATE         NULL,               -- 발송 예정일
  is_featured            BOOLEAN      NOT NULL DEFAULT FALSE,   -- "가장 인기" (자동 갱신)
  display_order          INT          NOT NULL DEFAULT 0, -- 목록 정렬 순서
  created_at             TIMESTAMP    NOT NULL,
  updated_at             TIMESTAMP    NOT NULL,
  PRIMARY KEY (id),
  KEY idx_reward_project_order (project_id, display_order),
  CONSTRAINT chk_reward_price_positive CHECK (price > 0),
  CONSTRAINT chk_reward_remaining_valid CHECK (remaining_quantity IS NULL OR remaining_quantity >= 0)
);
```

### 도메인 모델
```java
// reward.domain.RewardTier (Aggregate root)
@Entity
public class RewardTier extends BaseEntity {
    @Id @GeneratedValue Long id;
    Long projectId;
    String title;
    @Column(columnDefinition = "TEXT") String description;
    long price;
    Integer totalQuantity;      // nullable (무제한)
    Integer remainingQuantity;  // nullable
    int backerCount;
    LocalDate estimatedDeliveryAt;
    boolean isFeatured;
    int displayOrder;

    public static RewardTier create(Long projectId, String title, String description,
                                     long price, Integer totalQuantity,
                                     LocalDate deliveryAt, int displayOrder) {
        if (price <= 0) throw new BusinessException(ErrorCode.REWARD_INVALID_PRICE);
        // ...
    }

    public void reservePledge() {   // 후원 시 재고 차감
        if (remainingQuantity != null) {
            if (remainingQuantity <= 0)
                throw new BusinessException(ErrorCode.REWARD_SOLD_OUT);
            this.remainingQuantity--;
        }
        this.backerCount++;
    }

    public void releasePledge() {   // 취소/환불 시 재고 복구
        if (remainingQuantity != null) {
            this.remainingQuantity++;
        }
        this.backerCount--;
    }

    public boolean isSoldOut() {
        return remainingQuantity != null && remainingQuantity <= 0;
    }
}
```

### API 표면
| 메서드 | 경로 | 인증 | 용도 |
|---|---|---|---|
| GET | `/api/v1/projects/{projectId}/rewards` | 공개 | 프로젝트의 리워드 티어 목록 |
| POST | `/api/v1/projects/{projectId}/rewards` | JWT | 티어 추가 (프로젝트 DRAFT/UPCOMING만) |
| PATCH | `/api/v1/rewards/{rewardId}` | JWT | 티어 수정 (LIVE 이후 불가) |
| DELETE | `/api/v1/rewards/{rewardId}` | JWT | 티어 삭제 (LIVE 이후 불가) |
| PATCH | `/api/v1/rewards/{rewardId}/order` | JWT | 표시 순서 변경 |

### ErrorCode (신규)
- `RWD001` REWARD_NOT_FOUND (404)
- `RWD002` REWARD_SOLD_OUT (409 · 재고 0)
- `RWD003` REWARD_INVALID_PRICE (400 · 0 이하)
- `RWD004` REWARD_LOCKED (409 · LIVE 이후 수정/삭제 시도)
- `RWD005` REWARD_OWNERSHIP_FAILED (403 · 프로젝트 메이커 아님)
- `RWD006` REWARD_QUANTITY_INVALID (400 · 음수 등)

### 정합성 · 멱등성
- **재고 감소 · 후원 카운트 증가는 원자적** (같은 TX in Pledge 생성 시)
- 락 순서 (consistency §5): `Wallet → Project → RewardTier → Pledge`
- 티어 수정 락: 프로젝트 상태 검증 (DRAFT/UPCOMING만) · 도메인 메서드 방어
- "가장 인기" 자동 갱신: 후원 시 backerCount 증가 후 프로젝트 내 최대 티어 재계산 (배치 or 실시간)
  - 초기 v0.0.3: **배치 (일간)**
  - v0.0.4+: 후원 이벤트 시 실시간 갱신 검토

### 채팅에서 리워드 카드 발송 (이슈 #12와 결합)
- 메이커가 채팅 중 특정 티어를 발송 → 후원자가 "이 리워드로 후원" 버튼 클릭 → 결제
- 카드 스키마: `{rewardId, title, price, thumbnailUrl?}` (이슈 #12 상세)

### 관측
- `reward.pledged.total{project_id, reward_id}` counter (프로젝트별 티어별 · 카디널리티 주의)
- `reward.sold_out.total{project_id}` counter

## 이관 산출물

- **BE-Story #09-1**: `reward` 컨텍스트 신규 패키지 + `RewardTier` 엔티티 + Repository
- **BE-Story #09-2**: `RewardTier.reservePledge`·`releasePledge` 도메인 메서드
- **BE-Story #09-3**: `RewardTierService` (CRUD + 순서 변경 + 상태 검증)
- **BE-Story #09-4**: `RewardTierController` (5개 엔드포인트)
- **BE-Story #09-5**: `ErrorCode.RWD001~006` 등록
- **BE-Story #09-6**: 통합 테스트 (재고 0 후원 시도 · LIVE 이후 수정 차단 · 동시 후원 race)
- **BE-Story #09-7**: "가장 인기" 자동 갱신 배치 스케줄러 (일간)
- **BE-Story #09-8**: Pledge 생성 시 RewardTier.reservePledge 호출 통합 (이슈 #10)
- **FE-Story #09-1**: `src/features/reward/RewardTierList.tsx` (상세 페이지 티어 목록)
- **FE-Story #09-2**: `src/features/reward/RewardTierEditor.tsx` (등록 페이지 티어 추가)
- **FE-Story #09-3**: `src/features/reward/RewardCard.tsx` (채팅 발송 카드 · 이슈 #12와 공유)
- **Docs-Story #09-1**: `backend-boundary/error-codes.md` RWD001~006 매핑
- **SDD 개정**: 향후 `product-reward.md` 신규 (M3 진입 시)

## 관련 이슈 / 문서

- 선행: [#08 Project](./issue-08-project-domain.md) — 리워드는 프로젝트에 속함
- 다음: [#10 Pledge 상태기계](./issue-10-pledge-state-machine.md) — Pledge 생성 시 티어 선택 · 재고 차감
- 관련: [#12 Chat Rich Message](./issue-12-chat-rich-message.md) — 채팅으로 리워드 카드 발송
- 관련: [#07 Wallet](./issue-07-wallet-prepaid-balance.md) — 티어 가격 = Wallet 차감액
- 벤치마크 원본: Kickstarter · 텀블벅 · Wadiz
- 규범 참조: `.claude/rules/consitency.md` §5 (락 순서 갱신)

## 디자인 참조
- `C:\Users\user\Desktop\fe\프로젝트 상세.html` — 3티어 UI (서포터 · 얼리 어답터🔥 · 팀 플랜)
- `C:\Users\user\Desktop\fe\메이커 채팅.html` — 리워드 카드 발송 (card-bubble)
- `C:\Users\user\Desktop\fe\프로젝트 등록.html` — 티어 추가 폼 (v0.0.3 구현 스코프)
