# [Product 7] 리워드 티어 (Reward Tier · Kickstarter 방식)

## Product Vision
> 프로젝트 소유자(메이커)가 후원자에게 제공할 다양한 가격대의 리워드 티어를 등록·관리하고, 후원자는 티어 하나를 명시적으로 선택하여 후원한다. Kickstarter·텀블벅 규범을 따라 티어별 재고·발송 예정일·"가장 인기" 뱃지·LIVE 이후 편집 잠금을 제공하고, 재고 차감/복구를 도메인 메서드로 강제하여 동시성 상황에서도 정합성을 보장한다.

## 배경 및 문제
- 현재 상황 (As-Is)
  - Project(Product 6)는 완성되었으나 **후원자가 무엇을 받는지** 정의할 도메인 부재
  - Pledge(이슈 #10)는 티어 선택을 전제로 설계 → RewardTier 없이는 후원 흐름 시작 불가
  - Payment 도메인에서 "티어 개념" 자체가 없음 (일반 쇼핑몰 잔재)
  - 크라우드 펀딩 시각으로 결정적 도메인 요소 누락 상태
- 발생하는 문제
  - 후원자가 "그냥 돈을 넣는다"는 UX는 크라우드 펀딩과 다름 (Kickstarter·텀블벅 등 모든 표준이 티어 선택 강제)
  - 프로젝트 상세 페이지에서 후원 결정 시점의 UX 결정 지점 부재
  - 이슈 #12 채팅 리워드 카드 발송(`REWARD_CARD` 메시지 타입) 불가 · Chat SDD 진입 어려움
  - 이슈 #10 Pledge · 이슈 #12 Chat이 본 도메인 완결을 대기
- 왜 지금 해결해야 하는가
  - Project 완결(Product 6) 직후 순차적 · 크라우드 펀딩 흐름의 마지막 근본 조각
  - Pledge 진입점의 두 락 자원 중 하나(다른 하나는 Wallet · Project) — Pledge SDD 작성 전 완결 필수
  - 초기 사용자 없어 스키마 유연 · 지금이 정합성 규범 코드에 굳혀둘 최적 시점

## 목표 (To-Be)
- 신규 컨텍스트: `nbc.c1oud_mall.reward.*` (4레이어)
- `RewardTier` **별도 Aggregate root** (Project는 참조만 · Aggregate 소속 X)
- 필수 필드: `projectId · title · description · price · totalQuantity · remainingQuantity · backerCount · estimatedDeliveryAt · isFeatured · displayOrder`
- 도메인 메서드 4개: `reservePledge()` · `releasePledge()` · `isSoldOut()` · `updateEditableFields(...)` (LIVE 이후 필드 제한)
- **재고 관리**: `total_quantity` + `remaining_quantity` 병존 · 무제한(NULL) 지원
- **재고 0 처리**: "품절" 뱃지 · 후원 불가 (`RWD002 REWARD_SOLD_OUT`)
- **LIVE 이후 편집 잠금**: 프로젝트 상태 검증 · DRAFT/UPCOMING만 편집 가능 (`RWD004 REWARD_LOCKED`)
- **"가장 인기" 자동 갱신**: 프로젝트별 backerCount 최대 티어 · 일간 배치 (`is_featured` 컬럼 갱신)
- REST 5개 엔드포인트: 목록 · 생성 · 수정 · 삭제 · 순서 변경
- `ErrorCode.RWD001~006` 등록
- 락 순서 반영 (`Wallet(1) → Project(2) → RewardTier(3) → Pledge(4)`)
- 관측 지표 3종 (pledged · sold_out · featured_updates)

## 설계 결정 (Design Decisions)
> 큰 갈림길의 결정. 거부된 옵션도 합리적 근거가 있었음을 명시.

- **RewardTier를 별도 Aggregate root로 배치** (Project Aggregate 내부 X)
  - RewardTier 단독 조회·수정·재고 차감 자주 필요 (프로젝트 로드 없이 티어 락만 획득 가능해야 함)
  - Project ↔ RewardTier는 논리적 FK 참조 관계만 · Project 로드 없이 티어 조작 가능
  - Kickstarter도 티어를 별도 리소스로 취급 (API 별도 · `/rewards/{id}`)
- **재고 관리 = `total_quantity` + `remaining_quantity` 병존**
  - `total_quantity`: 최초 발행 · 불변 (통계용 · UI에 "12/50 남음" 표시)
  - `remaining_quantity`: 후원 시 감소 · 취소 시 복구 · 락 대상
  - `NULL` = 무제한 (수량 제한 없음 · UI에 "제한 없음")
  - 대안(총 수량만 · 매 조회 `COUNT(pledge)`)은 조회 성능 저하 · 락 대상 모호
- **재고 0 처리 = "품절" 뱃지 노출 + 후원 불가** (`RWD002` 명시적 예외)
  - Kickstarter도 소진된 티어를 목록에 표시 (강조 UX)
  - 숨김보다 노출이 사용자 이해에 유리 · "인기 티어" 시그널로도 작동
- **"가장 인기" = 자동 갱신 (해당 프로젝트 내 backerCount 최대)**
  - 수동 지정 시 조작 가능성 · 신뢰도 낮음
  - 배치 갱신 (v0.0.3 일간 배치) · v0.0.4+ 후원 이벤트 실시간 갱신 검토
  - 프로젝트별 최대 1개만 `is_featured=true` (중복 방지 · 배치가 보장)
- **LIVE 이후 편집·삭제 잠금** (`RWD004 REWARD_LOCKED`)
  - Kickstarter 규범 준수 · 후원자가 본 조건 유지 필수 (계약 신뢰성)
  - 예외: 메이커가 실수로 오탈자 발견해도 수정 불가 (사용자 문의로만 대응)
  - 도메인 메서드 `updateEditableFields`에서 프로젝트 상태 검증 강제
- **락 순서**: `Wallet(1) → Project(2) → RewardTier(3) → Pledge(4)`
  - Pledge 흐름에서 위 순서 준수 시 데드락 방지
  - `.claude/rules/consitency.md` §5 갱신 대상 · Product 5(Wallet) 이미 갱신 예정 · 여기서 RewardTier 추가

## 대안 검토 (Alternatives Considered)

### 도메인 배치
**Option A — 별도 Aggregate root (선택)**
- 비용: Project 로드 없이 티어 조작 · Aggregate 응집도 낮음
- 보상: 재고 차감 흐름에서 티어 단독 락 획득 가능 · 검색·조회 성능 우수 · 단독 관리 자유
- Pledge 흐름의 락 순서가 명확 (Wallet → Project → RewardTier → Pledge)

**Option B — Project Aggregate 내부 (`@OneToMany` 관계 · Cascade)**
- 거부 이유: 티어 하나 락 획득에 Project 로드 필수 · 락 부담 증가 · 조회 시 항상 티어 전체 로드 (N+1 or fetch join 관리 부담)

### 재고 관리
**Option A — `total_quantity` + `remaining_quantity` 병존 (선택)**
- 비용: 데이터 일관성 검증 필요 (`remaining ≤ total`)
- 보상: 조회 성능 · 락 대상 명확 · UI에 "12/50 남음" 즉시 표시

**Option B — 총 수량만 · 남은 수량은 실시간 `COUNT(pledge)` 계산**
- 거부 이유: 매 조회마다 COUNT · Pledge 트래픽 증가 시 성능 저하

**Option C — 무제한 티어만 지원 (수량 제한 없음)**
- 거부 이유: "얼리 어답터 12개 한정" 등 크라우드 펀딩 표준 UX 불가

### 재고 0 처리
**Option A — "품절" 뱃지 노출 · 후원 불가 (`RWD002`) (선택)**
- 명확 · 사용자 이해 쉬움 · "인기" 시그널

**Option B — UI에서 숨김 (0인 티어 목록 제외)**
- 거부 이유: "품절" 정보도 UX 가치 · Kickstarter도 표시 · 후원자의 인기 판단 도움

### "가장 인기" 결정 방식
**Option A — 자동 (프로젝트 내 backerCount 최대 · 배치 갱신) (선택)**
- 데이터 기반 · 신뢰성 · 조작 방지

**Option B — 수동 (메이커가 표시 선택)**
- 거부 이유: 조작 가능성 · 후원자 신뢰 저하

**Option C — 자동 · 실시간 갱신 (후원 이벤트 시)**
- 초기 스코프 오버 · v0.0.4+ 검토 (배치 부하 관측 후 이관)

### LIVE 이후 편집
**Option A — 완전 잠금 (선택)**
- Kickstarter 규범 · 후원 계약 신뢰성 우선

**Option B — 오탈자 등 일부 필드만 편집 가능**
- 거부 이유: "일부"의 경계 모호 · 신뢰성 침해 위험 · v0.0.5+ 재검토 대상

## 전체 아키텍처 (High-Level Architecture)

### 컴포넌트 배치
```
presentation ──▶ application ──▶ domain ◀── infrastructure
RewardTierController  RewardTierService     RewardTier               RewardTierRepository
- list                - create              (Aggregate)              - findByIdForUpdate ⭐
- create              - update              - reservePledge()        - findByProjectId
- update              - delete              - releasePledge()        - findByProjectIdAndDisplayOrder
- delete              - changeOrder         - isSoldOut()            RewardFeaturedScheduler
- changeOrder                               - updateEditableFields() - runDaily()
                                            RewardTierDomainService
                                            - verifyProjectEditable
                                              (Project 상태 검증)

External refs (참조만 · 실 검증은 각 도메인):
- Project (Product 6) — projectId FK · 상태 검증 (DRAFT/UPCOMING만 편집)
- Pledge (Product 8 후속) — 재고 차감/복구 진입점
- Chat (이슈 #12) — REWARD_CARD payload 원천
```

### 핵심 플로우
**1. 티어 생성 (Project DRAFT/UPCOMING만)**
```
Maker → RewardTierController.create(projectId, request)
      → RewardTierService.create(makerId, projectId, cmd)
        ├── Project 조회 (읽기만 · 락 불필요)
        ├── verifyOwnership(makerId, project)      → PRJ004
        ├── verifyEditable(project.fundingStatus)  → RWD004 (LIVE 이후)
        ├── RewardTier.create(projectId, cmd)      도메인 메서드
        └── rewardTierRepository.save
      ← 201 Created + Location
```

**2. 재고 차감 (Pledge 진입)**
```
PledgeService.pledge (Product 8 · 이슈 #10)
  ├── Wallet FOR UPDATE (락 1) → wallet.pledge()
  ├── Project FOR UPDATE (락 2) → project.addPledgeAmount()
  └── RewardTier FOR UPDATE (락 3) ⭐ 본 Product 진입점
       ├── tier.reservePledge()
       │     ├── isSoldOut() 검증 → RWD002
       │     ├── remainingQuantity--
       │     └── backerCount++
       └── (Pledge 저장 · 락 4)
```

**3. 재고 복구 (Pledge 취소·환불)**
```
PledgeService.cancel or refundPledge
  ├── Wallet FOR UPDATE
  └── RewardTier FOR UPDATE
       └── tier.releasePledge()
             ├── remainingQuantity++  (NULL이면 no-op)
             └── backerCount--        (0 미만 방지)
```

**4. "가장 인기" 배치 갱신**
```
@Scheduled(cron = "0 30 3 * * *")   -- 매일 3:30 (수집 배치 이후)
RewardFeaturedScheduler.runDaily()
  For each active Project (LIVE · UPCOMING):
    - 프로젝트의 티어 목록 조회
    - backerCount 최대 티어 선정 (동률 시 displayOrder 낮은 순)
    - 해당 티어의 is_featured=true · 나머지 false (UPDATE)
    - MeterRegistry counter 증가
```

### Out-of-Process 의존
- **RDS (MySQL)** — `reward_tier` 테이블 · 인덱스 · `SELECT FOR UPDATE`
- (참조) Project — projectId FK로 참조만 · BC 협력은 직접 호출 (ADR 006)

## 실패 모드 / 운영 관측 (Failure Modes & Observability)

### 실패 시나리오와 응답
| 시나리오 | ErrorCode | HTTP | 클라이언트 권장 동작 |
| --- | --- | --- | --- |
| 티어 없음 | `RWD001` REWARD_NOT_FOUND | 404 | 목록 새로고침 |
| 재고 0 (후원 시) | `RWD002` REWARD_SOLD_OUT | 409 | "품절" 표시 · 다른 티어 유도 |
| 유효하지 않은 가격 (0 이하) | `RWD003` REWARD_INVALID_PRICE | 400 | 폼 재입력 |
| LIVE 이후 수정·삭제 시도 | `RWD004` REWARD_LOCKED | 409 | "펀딩 중에는 편집 불가" 안내 |
| 프로젝트 소유자 아님 | `RWD005` REWARD_OWNERSHIP_FAILED | 403 | 접근 거부 |
| 유효하지 않은 수량 (음수 등) | `RWD006` REWARD_QUANTITY_INVALID | 400 | 폼 재입력 |
| 재고 복구 시 언더플로우 (backerCount<0) | `C002` INTERNAL_ERROR | 500 | 로그 마커 · 운영자 개입 |

### 로깅 정책
- **항상 기록**:
  - `requestId` · `rewardTierId` · `projectId` · `makerUserId` · `remainingQuantityBefore/After` · `backerCountBefore/After` · 액션(create/update/delete/reserve/release)
- **debug**: `is_featured` 배치 갱신 결과 (프로젝트별 · 티어별)
- **절대 금지**:
  - description 원문 로그 (개인정보·마케팅 문구 포함 가능)

### 관측 지표
- `reward.pledged.total{result=reserved|sold_out}` — counter — 재고 차감 성공/실패
- `reward.released.total{reason=cancelled|refunded}` — counter — 재고 복구 원인 분포
- `reward.featured.updated.total` — counter — 배치 갱신 시 `is_featured` 변경 횟수
- `reward.editable_locked.total` — counter — LIVE 이후 편집 시도 (RWD004 응답 카운트)
- `reward.tier.count.gauge{project_id}` — gauge — 프로젝트별 티어 수 (카디널리티 주의 · 초기 미도입 검토)

## 롤아웃 / 마이그레이션 (Rollout)

### 전제
- Product 6(Project) 완결 상태 · Project.fundingStatus 조회 가능
- 초기 사용자 없음 · 기존 데이터 무 · 신규 테이블만 생성
- 일괄 배포 · JPA `ddl-auto`로 자동 스키마 반영

### Product 의존성
- **선행**: **Wallet Product 5** · **Project Product 6**
- **후행**: **Pledge Product 8** (재고 차감 진입점) · **Chat Product 9** (REWARD_CARD 메시지 페이로드)
- **동시 대응**: 이슈 #12 Chat Rich Message SDD 진입 시 본 도메인 참조 검증

### Epic·Story 의존성 그래프
```
Epic 1 (도메인·리포지터리) ──► Epic 2 (CRUD 서비스)
                                    ├─► Epic 3 (REST API)
                                    └─► Epic 4 (배치 + 관측)
```

### 환경별 설정 분기
| 항목 | dev (H2) | prod (RDS MySQL) |
| --- | --- | --- |
| 테이블 · 인덱스 | JPA ddl-auto | 동일 |
| `SELECT FOR UPDATE` | H2 MySQL 호환 모드 | 완전 지원 |
| `RewardFeaturedScheduler` cron | 5분 (테스트 편의) | 매일 03:30 (수집 배치 이후) |
| DummyDataInit | 프로젝트당 3티어 (서포터·얼리 어답터🔥·팀 플랜) | 활성 |

## 성공 지표 (KPI)
| 지표 | 목표 값 | 측정 방법 |
| --- | --- | --- |
| 재고 차감 정합성 (backerCount = SUM(pledge count)) | 100% | 주간 배치 검증 (Pledge Product에서 담당) |
| 재고 0 시 후원 시도 차단 | 100% | `reward.pledged.total{result=sold_out}` — 재고 0인데 차감 성공 = 0 |
| LIVE 이후 편집 시도 차단 | 100% | `RWD004` 응답 시 실제 DB 변경 0건 |
| "가장 인기" 배치 정확성 | 프로젝트별 최대 1개 `is_featured=true` · 100% | 주간 배치 후 SQL 검증 |
| 티어 조회 응답 시간 P95 | ≤ 100ms | 로그 · 지표 |

## Scope
**In Scope**:
- 신규 컨텍스트 `nbc.c1oud_mall.reward.*` (4레이어)
- `RewardTier` 엔티티 · Aggregate root
- 도메인 메서드 4개 (`reservePledge · releasePledge · isSoldOut · updateEditableFields`)
- `RewardTierRepository.findByIdForUpdate` (비관 락)
- `RewardTierService` — 생성 · 수정 · 삭제 · 순서 변경
- REST 5개 엔드포인트
- `ErrorCode.RWD001~006`
- `RewardFeaturedScheduler` (일간 배치)
- 관측 지표 4종
- 락 순서 규범 반영
- 이슈 #12 채팅 REWARD_CARD 페이로드 참조 원천 (실 페이로드 저장은 Chat Product)

**Out of Scope**:
- **배송비 계산** — v0.0.5+ 별도 도메인 · 초기엔 배송비 없음 (`price`가 최종 금액)
- **디지털 vs 실물 구분** — v0.0.4+ · 초기엔 단순 (모든 티어 동등)
- **다중 티어 후원** — 구조상 불가 (Pledge 하나 = 티어 하나 · Kickstarter 표준)
- **국제 배송·통관** — 한국 시장 컨셉 · 배제
- **티어 수정 이력 감사** — v0.0.5+
- **관리자 강제 재고 조정** — 초기 SQL 수동 · v0.0.5+ 관리자 도메인 추가
- **후원자가 티어 없이 순수 후원** — Kickstarter 방식 준수 · 티어 선택 강제

## 대상 사용자
- **메이커 (Maker)** — 프로젝트 등록 시 티어 3~5개 생성 · DRAFT 중 수정 · LIVE 이후 조회만
- **후원자 (Backer)** — 티어 목록 조회 · 선택 · 후원 (Pledge 진입)
- **후속 SDD 작성자** — Pledge · Chat SDD에서 본 도메인 참조
- **운영자** — 배치 결과 관측 · SSOT 배치 (Pledge Product) 감독
- **개발/QA** — 재고 차감/복구 동시성 테스트 · LIVE 이후 잠금 회귀 검증

## 연결된 Epic 목록
- [ ] Epic 1: `RewardTier` 엔티티 + 도메인 메서드 + 비관 락 리포지터리 + ErrorCode
- [ ] Epic 2: `RewardTierService` CRUD (Project 상태·소유권 검증 포함)
- [ ] Epic 3: `RewardTierController` REST 5개 엔드포인트
- [ ] Epic 4: `RewardFeaturedScheduler` (일간 배치) + 관측 지표 + ADR + 규범 갱신

## 관련 문서
- **원본 이슈**: `workflows/task/fix/brainstorming/version/0.0.2v/issue-09-reward-tier.md`
- **선행 SDD**: `product-wallet.md` (Product 5) · `product-project.md` (Product 6)
- **후행 SDD (M3~M4)**:
  - `product-pledge.md` (Product 8 · 재고 차감/복구 진입점)
  - `product-chat.md` (Product 9 · REWARD_CARD 페이로드)
- **관련 이슈**:
  - `issue-08-project-domain.md` — Project 상태 검증
  - `issue-10-pledge-state-machine.md` — 재고 차감 흐름
  - `issue-12-chat-rich-message.md` — REWARD_CARD 페이로드 원천
- **관련 벤치마크**:
  - Kickstarter Reward Tiers 규범 · 텀블벅 리워드 정책
- **신규 ADR 후보**:
  - "RewardTier Aggregate 배치 (Project 별도)"
  - "재고 관리 정책 (total + remaining · NULL=무제한)"
  - "LIVE 이후 편집 잠금 (Kickstarter 준수)"
  - "가장 인기 자동 갱신 · 배치 방식"
- **규범 갱신 예정**:
  - `.claude/rules/consitency.md` §5 락 순서 표에 RewardTier(3번째) 추가
  - `workflows/backend-boundary/error-codes.md` RWD001~006 매핑 추가

## 열린 질문 (Open Questions)
- **"가장 인기" 실시간 갱신 트리거 시점** — v0.0.4+ 검토 · 배치 부하가 문제되지 않으면 배치 유지
- **티어 최대 개수 제한** — 초기 무제한 · Kickstarter는 10개 권장 · UI 성능 위해 v0.0.4+ 상한 (예: 10개) 도입 검토
- **삭제 vs Soft Delete** — 초기 결정: DRAFT 중에만 완전 삭제 · LIVE 이후는 `RWD004`로 잠금 · Soft Delete 도입 여부 v0.0.5+
- **동률 시 "가장 인기" 티브레이커** — backerCount 동률 시 `displayOrder` 낮은 순 (임의 규칙 · 문서화)
- **재고 무제한 티어 (`remainingQuantity=NULL`) `isSoldOut()` 처리** — 항상 `false` (도메인 메서드 명시)
- **채팅 REWARD_CARD 발송 시 재고 검증** — 발송 시점에는 검증 X · 후원 클릭 시점 · Chat SDD에서 결정

## 제품 수준 완료 기준 (Product-level DoD)
- [ ] 모든 Epic DoD 통과
- [ ] E2E 시나리오: DRAFT 프로젝트에 티어 3개 생성 → 오픈(LIVE) → 후원 3회 (2건 티어A · 1건 티어B) → 취소 1회 → 재고·backerCount 정확
- [ ] 동시성 통합 테스트: 재고 1개 남은 티어에 5명 동시 후원 → 1명만 성공 · 4명 `RWD002`
- [ ] LIVE 상태 프로젝트 티어에 편집·삭제 시도 → 100% `RWD004` (실제 DB 변경 0건)
- [ ] `RewardFeaturedScheduler` 배치 실행 · 프로젝트별 최대 1개 `is_featured=true` 확인
- [ ] ADR 최소 2건 발행 (Aggregate 배치 · LIVE 잠금)
- [ ] `.claude/rules/consitency.md` §5 락 순서 갱신 완료
- [ ] `backend-boundary/error-codes.md` RWD001~006 매핑 완료
- [ ] 관측 지표 4개 프로덕션 노출

---

# [Epic 1] `RewardTier` 엔티티 + 도메인 메서드 + 비관 락 리포지터리 + ErrorCode

## 목표
`RewardTier` Aggregate root와 4개 도메인 메서드(`reservePledge · releasePledge · isSoldOut · updateEditableFields`), 비관 락 리포지터리 메서드, `ErrorCode.RWD001~006`을 구축하여 후속 Epic이 안정적으로 확장할 수 있는 기반을 마련한다.

## 배경
- 크라우드 펀딩 후원 흐름의 티어 차감 진입점
- 도메인 로직(재고·상태·소유권)은 엔티티에서 강제 · Service는 조합만
- 비관 락으로 동시성 방어 · Wallet·Project와 동일 패턴

## 포함 Story
- Story 1-1: `RewardTier` 엔티티 + `RewardTierRepository` 기본 CRUD
- Story 1-2: `reservePledge()` · `releasePledge()` · `isSoldOut()` 도메인 메서드
- Story 1-3: `updateEditableFields()` 도메인 메서드 (LIVE 이후 필드 제한)
- Story 1-4: `RewardTierRepository.findByIdForUpdate` (비관 락) + `ErrorCode.RWD001~006`

## Epic 인수 시나리오
- Given `RewardTier(totalQuantity=10, remainingQuantity=10, backerCount=0)` 존재
- When `reservePledge()` 호출
- Then `remainingQuantity=9, backerCount=1` · 예외 없음

*(엣지)* Given `remainingQuantity=0` · When `reservePledge()` · Then `BusinessException(RWD002)` · 값 유지

## Epic 완료 기준 (DoD)
- [ ] 4개 Story 완료
- [ ] 단위 테스트: 4개 도메인 메서드 × (성공·엣지·예외) 매트릭스
- [ ] `@DataJpaTest` 슬라이스: `findByIdForUpdate` 락 획득 검증
- [ ] `ErrorCode` enum 검증 (6개 코드 등록 · HTTP 매핑 정확)

---

## [Story 1-1] `RewardTier` 엔티티 + Repository 기본 CRUD

### User Story
- As a Reward 도메인 개발자
- I want `RewardTier` Aggregate root 엔티티와 기본 CRUD Repository를 구축
- so that 서비스 계층에서 티어 생성·수정·조회 가능

### 설명
- 엔티티 위치: `nbc.c1oud_mall.reward.domain.RewardTier`
- Repository: `nbc.c1oud_mall.reward.infrastructure.RewardTierRepository extends JpaRepository`
- `BaseEntity` 상속 (`createdAt · updatedAt` JPA Auditing)
- Aggregate root · Project는 논리적 FK 참조만 (`Long projectId` · JPA 관계 X)

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.reward.domain.RewardTier`
- `nbc.c1oud_mall.reward.infrastructure.RewardTierRepository`

**주요 스키마**:
```sql
CREATE TABLE reward_tier (
  id                     BIGINT       NOT NULL AUTO_INCREMENT,
  project_id             BIGINT       NOT NULL,
  title                  VARCHAR(100) NOT NULL,
  description            TEXT         NOT NULL,
  price                  BIGINT       NOT NULL,
  total_quantity         INT          NULL,               -- NULL = 무제한
  remaining_quantity     INT          NULL,               -- NULL = 무제한
  backer_count           INT          NOT NULL DEFAULT 0,
  estimated_delivery_at  DATE         NULL,
  is_featured            BOOLEAN      NOT NULL DEFAULT FALSE,
  display_order          INT          NOT NULL DEFAULT 0,
  created_at             TIMESTAMP    NOT NULL,
  updated_at             TIMESTAMP    NOT NULL,
  PRIMARY KEY (id),
  KEY idx_reward_project_order (project_id, display_order),
  KEY idx_reward_project_featured (project_id, is_featured),
  CONSTRAINT chk_reward_price_positive CHECK (price > 0),
  CONSTRAINT chk_reward_remaining_valid CHECK (remaining_quantity IS NULL OR remaining_quantity >= 0)
);
```

**엔티티**:
```java
@Entity
@Table(name = "reward_tier")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class RewardTier extends BaseEntity {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private long price;

    @Column
    private Integer totalQuantity;   // nullable

    @Column
    private Integer remainingQuantity;   // nullable

    @Column(nullable = false)
    private int backerCount;

    @Column
    private LocalDate estimatedDeliveryAt;

    @Column(nullable = false)
    private boolean isFeatured;

    @Column(nullable = false)
    private int displayOrder;

    // 정적 팩토리
    public static RewardTier create(Long projectId, String title, String description,
                                     long price, Integer totalQuantity,
                                     LocalDate deliveryAt, int displayOrder) {
        if (price <= 0)
            throw new BusinessException(ErrorCode.RWD003);
        if (totalQuantity != null && totalQuantity <= 0)
            throw new BusinessException(ErrorCode.RWD006);

        RewardTier tier = new RewardTier();
        tier.projectId = projectId;
        tier.title = title;
        tier.description = description;
        tier.price = price;
        tier.totalQuantity = totalQuantity;
        tier.remainingQuantity = totalQuantity;   // 초기값 = 총 수량
        tier.backerCount = 0;
        tier.estimatedDeliveryAt = deliveryAt;
        tier.isFeatured = false;
        tier.displayOrder = displayOrder;
        return tier;
    }
}
```

**Repository**:
```java
public interface RewardTierRepository extends JpaRepository<RewardTier, Long> {
    List<RewardTier> findByProjectIdOrderByDisplayOrderAsc(Long projectId);
    long countByProjectId(Long projectId);
}
```

### 완료 기준 (AC)
- Given 유효 파라미터 · When `RewardTier.create(...)` · Then 인스턴스 반환 · `remainingQuantity == totalQuantity`
- Given `price=0` · When `create` · Then `BusinessException(RWD003)`
- Given `totalQuantity=-5` · When `create` · Then `BusinessException(RWD006)`
- Given `totalQuantity=null` · When `create` · Then 성공 · `remainingQuantity=null` (무제한)
- Given Project 100 · 티어 3건 저장 · When `findByProjectIdOrderByDisplayOrderAsc(100)` · Then `displayOrder ASC`

### Definition of Done
- [ ] `RewardTier` 엔티티 (`src/main/java/nbc/c1oud_mall/reward/domain/RewardTier.java`)
- [ ] `RewardTierRepository` 인터페이스 · 기본 CRUD + `findByProjectIdOrderByDisplayOrderAsc`
- [ ] DDL 확인 (JPA ddl-auto · dev H2)
- [ ] 단위 테스트: `RewardTier.create` 성공·예외 5+ 케이스
- [ ] `@DataJpaTest` 슬라이스: 저장·조회·정렬

### 스토리 포인트
1d

### 의존성
- 선행: Product 6 (Project 존재)
- 후행: Story 1-2 · 1-3 · 1-4 · Epic 2·3·4

---

## [Story 1-2] `reservePledge()` · `releasePledge()` · `isSoldOut()` 도메인 메서드

### User Story
- As a Pledge 서비스 (후속 Product 8)
- I want `RewardTier`에 재고 차감/복구 도메인 메서드를 두어 원자적 갱신
- so that 후원 흐름에서 재고 정합성 보장 (동시성 하에서도)

### 설명
- 3개 도메인 메서드:
  - `reservePledge()` — 재고 1 차감 + backerCount 1 증가 (재고 0 또는 언더플로우 시 `RWD002`)
  - `releasePledge()` — 재고 1 복구 + backerCount 1 감소 (backerCount 0 미만 방지)
  - `isSoldOut()` — 재고 0 여부 (`remainingQuantity=null` 무제한이면 항상 `false`)
- `remainingQuantity=null` (무제한) 처리:
  - `reservePledge`: `remainingQuantity` 변경 없음 · backerCount만 증가
  - `releasePledge`: 동일
  - `isSoldOut`: 항상 `false`

**주요 메서드**:
```java
public void reservePledge() {
    if (isSoldOut())
        throw new BusinessException(ErrorCode.RWD002);
    if (remainingQuantity != null) {
        this.remainingQuantity--;
    }
    this.backerCount++;
}

public void releasePledge() {
    if (this.backerCount <= 0) {
        // 시스템 이상 (Pledge 취소인데 backerCount가 이미 0) · 로그 마커
        log.error("REWARD_BACKER_COUNT_UNDERFLOW rewardTierId={}", id);
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
    if (remainingQuantity != null) {
        this.remainingQuantity++;
        if (totalQuantity != null && this.remainingQuantity > totalQuantity) {
            // 시스템 이상 · 로그 마커
            log.error("REWARD_REMAINING_OVERFLOW rewardTierId={} remaining={} total={}",
                id, remainingQuantity, totalQuantity);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
    this.backerCount--;
}

public boolean isSoldOut() {
    return remainingQuantity != null && remainingQuantity <= 0;
}
```

### 완료 기준 (AC)
- Given `remainingQuantity=10, backerCount=0` · When `reservePledge()` · Then `remainingQuantity=9, backerCount=1`
- Given `remainingQuantity=0` · When `reservePledge()` · Then `BusinessException(RWD002)` · 값 유지
- Given `remainingQuantity=null` (무제한) · When `reservePledge()` · Then `backerCount++` · `remainingQuantity=null` 유지 · 예외 없음
- Given `remainingQuantity=5, backerCount=1` · When `releasePledge()` · Then `remainingQuantity=6, backerCount=0`
- Given `backerCount=0` · When `releasePledge()` · Then `INTERNAL_ERROR` · 로그 마커
- Given `remainingQuantity=10, totalQuantity=10, backerCount=0` · When `releasePledge()` (시스템 이상) · Then `INTERNAL_ERROR` · 로그 마커
- Given `remainingQuantity=0` · When `isSoldOut()` · Then `true`
- Given `remainingQuantity=null` · When `isSoldOut()` · Then `false`

### Definition of Done
- [ ] 3개 도메인 메서드 구현
- [ ] 단위 테스트: 성공·엣지·예외 각 케이스 (8+ 케이스)
- [ ] 로그 마커 검증 (`REWARD_BACKER_COUNT_UNDERFLOW`, `REWARD_REMAINING_OVERFLOW`)
- [ ] `ErrorCode.RWD002` 사용 확인

### 스토리 포인트
0.5d

### 의존성
- 선행: Story 1-1
- 후행: Pledge Product 8 (재고 차감 진입점)

---

## [Story 1-3] `updateEditableFields()` 도메인 메서드 (LIVE 이후 필드 제한)

### User Story
- As a Reward 서비스
- I want `updateEditableFields()` 도메인 메서드로 편집 가능한 필드만 수정 + 프로젝트 상태 검증
- so that LIVE 이후 후원자 신뢰 보호 (Kickstarter 규범)

### 설명
- 프로젝트 상태 검증은 Service가 담당 (엔티티는 Project 모름)
- 본 도메인 메서드는 편집 가능 필드만 갱신 (title · description · estimatedDeliveryAt · displayOrder)
- 편집 불가 필드는 파라미터 자체를 받지 않음 (컴파일 타임 강제)
- `price`, `totalQuantity`, `remainingQuantity`, `backerCount`, `isFeatured`, `projectId`는 편집 불가

**주요 메서드**:
```java
public void updateEditableFields(String title, String description,
                                  LocalDate estimatedDeliveryAt, int displayOrder) {
    if (title == null || title.isBlank())
        throw new BusinessException(ErrorCode.C001);   // Validation은 Bean Validation이 담당하나 방어
    this.title = title;
    this.description = description;
    this.estimatedDeliveryAt = estimatedDeliveryAt;
    this.displayOrder = displayOrder;
}
```

### 완료 기준 (AC)
- Given `RewardTier` 존재 · When `updateEditableFields("New Title", ...)` · Then 필드 갱신
- Given `title=""` · When 호출 · Then `BusinessException(C001)` (방어적 검증)
- Given `price` · `remainingQuantity` 변경 시도 (파라미터 없음) · Then 컴파일 오류 (컴파일 타임 강제 · 문서화)

### Definition of Done
- [ ] `updateEditableFields` 도메인 메서드
- [ ] 단위 테스트
- [ ] 편집 불가 필드는 파라미터 자체 없음 (문서화)

### 스토리 포인트
0.5d

### 의존성
- 선행: Story 1-1
- 후행: Epic 2 Story 2-2 (Service에서 프로젝트 상태 검증)

---

## [Story 1-4] `findByIdForUpdate` (비관 락) + `ErrorCode.RWD001~006`

### User Story
- As a Reward · Pledge 서비스
- I want `RewardTierRepository.findByIdForUpdate`로 비관 락 획득 + 6개 신규 ErrorCode
- so that 동시성 방어 · 일관된 예외 응답

### 설명
- Wallet · Project와 동일 패턴 (`@Lock(LockModeType.PESSIMISTIC_WRITE)`)
- `ErrorCode` enum 확장:
  - `RWD001` REWARD_NOT_FOUND (404)
  - `RWD002` REWARD_SOLD_OUT (409)
  - `RWD003` REWARD_INVALID_PRICE (400)
  - `RWD004` REWARD_LOCKED (409)
  - `RWD005` REWARD_OWNERSHIP_FAILED (403)
  - `RWD006` REWARD_QUANTITY_INVALID (400)

**주요 메서드**:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT r FROM RewardTier r WHERE r.id = :id")
Optional<RewardTier> findByIdForUpdate(@Param("id") Long id);
```

### 완료 기준 (AC)
- Given `RewardTier(id=1)` 저장 · When `findByIdForUpdate(1)` (TX 안) · Then Optional non-empty · 락 획득
- *(동시성)* 2개 트랜잭션 동시 진입 · Then 순차 처리 · 락 hold time 측정 가능
- Given `ErrorCode.RWD001~006` 등록 · When `errorCode.getCode()` · Then "RWD001" · "RWD002" ...

### Definition of Done
- [ ] `findByIdForUpdate` 메서드 추가
- [ ] `ErrorCode` enum에 6개 추가 (Reward 섹션 주석 구분선)
- [ ] `@DataJpaTest` 슬라이스: 락 획득
- [ ] 동시성 통합 테스트 (2 스레드 · CountDownLatch)
- [ ] `.claude/rules/exception.md` §5 사용 예시에 RWD 계열 추가 (선택)

### 스토리 포인트
1d

### 의존성
- 선행: Story 1-2
- 후행: Epic 2·3·4 · Pledge Product 8

---

# [Epic 2] `RewardTierService` CRUD (Project 상태·소유권 검증 포함)

## 목표
`RewardTierService`를 application 계층에 배치하여 티어 생성·수정·삭제·순서 변경 유스케이스를 제공하고, Project 상태(DRAFT/UPCOMING만)와 소유권을 검증하여 LIVE 이후 편집 잠금을 강제한다.

## 배경
- Epic 1의 도메인·리포지터리 위에서 유스케이스 조합
- Project 상태 검증은 Service가 담당 (도메인 응집도 유지)
- 소유권 검증은 Project.verifyOwnership 재사용

## 포함 Story
- Story 2-1: `RewardTierService.create` (Project 상태·소유권 검증)
- Story 2-2: `RewardTierService.update` (LIVE 이후 잠금 · 편집 필드 제한)
- Story 2-3: `RewardTierService.delete` (LIVE 이후 잠금)
- Story 2-4: `RewardTierService.changeOrder` (표시 순서 변경 · LIVE 이후 잠금)

## Epic 인수 시나리오
- Given Project(id=100, status=DRAFT, makerUserId=1) · When `create(1, 100, cmd)` · Then RewardTier 저장 · projectId=100
- *(예외)* Project(status=LIVE) · When `update(1, tierId, cmd)` · Then `RWD004`

## Epic 완료 기준 (DoD)
- [ ] 4개 Story 완료
- [ ] 각 유스케이스 통합 테스트 (Project 상태 조합 · 소유권 위반)
- [ ] LIVE 이후 편집 시도 100% 잠금 검증

---

## [Story 2-1] `RewardTierService.create`

### User Story
- As a 메이커
- I want Project DRAFT/UPCOMING 상태에서만 티어 생성 가능
- so that LIVE 이후 후원 계약 신뢰성 보호

### 설명
- 검증 순서:
  1. Project 조회 (읽기)
  2. `project.verifyOwnership(makerId)` → `PRJ004` (Project 도메인 재사용)
  3. Project 상태 검증 (`DRAFT` or `UPCOMING`만) → `RWD004`
  4. `RewardTier.create(...)` → `RWD003 · RWD006`
  5. save

**핵심 메서드**:
```java
@Service
@RequiredArgsConstructor
@Transactional
public class RewardTierService {
    private final RewardTierRepository tierRepository;
    private final ProjectRepository projectRepository;

    public Long create(Long makerId, Long projectId, RewardTierCreateCommand cmd) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRJ001));
        project.verifyOwnership(makerId);
        verifyProjectEditable(project);

        RewardTier tier = RewardTier.create(
            projectId,
            cmd.title(), cmd.description(), cmd.price(),
            cmd.totalQuantity(), cmd.estimatedDeliveryAt(),
            cmd.displayOrder()
        );
        return tierRepository.save(tier).getId();
    }

    private void verifyProjectEditable(Project project) {
        FundingStatus status = project.getFundingStatus();
        if (status != FundingStatus.DRAFT && status != FundingStatus.UPCOMING)
            throw new BusinessException(ErrorCode.RWD004);
    }
}
```

### 완료 기준 (AC)
- Given DRAFT Project · 유효 cmd · 본인 메이커 · When `create(1, 100, cmd)` · Then 티어 저장 · id 반환
- *(예외)* Given LIVE Project · When `create` · Then `RWD004`
- *(예외)* Given 타 메이커 · When `create` · Then `PRJ004`
- *(예외)* Given Project 없음 · When `create` · Then `PRJ001`
- *(예외)* Given `price=0` · When `create` · Then `RWD003` (도메인 위임)

### Definition of Done
- [ ] `RewardTierService.create` 구현
- [ ] `RewardTierCreateCommand` (record · application layer DTO)
- [ ] `verifyProjectEditable` private 메서드
- [ ] 통합 테스트: 4개 상태(DRAFT · UPCOMING · LIVE · SUCCESSFUL) 조합 × 소유권 조합

### 스토리 포인트
1d

### 의존성
- 선행: Epic 1 완결 · Product 6 (Project) 완결
- 후행: Story 3-2 (Controller)

---

## [Story 2-2] `RewardTierService.update`

### User Story
- As a 메이커
- I want Project DRAFT/UPCOMING 상태에서만 티어 수정 가능 · 편집 가능 필드만
- so that LIVE 이후 후원 조건 변경 불가

### 설명
- Story 2-1과 동일한 검증 순서 + 편집 필드 제한 (도메인 `updateEditableFields`)
- `price · totalQuantity` 변경 요청 시: 파라미터 자체 없음 (컴파일 타임 강제 · 요청 DTO에 없음)

**핵심 메서드**:
```java
public void update(Long makerId, Long tierId, RewardTierUpdateCommand cmd) {
    RewardTier tier = tierRepository.findById(tierId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RWD001));

    Project project = projectRepository.findById(tier.getProjectId())
            .orElseThrow(() -> new BusinessException(ErrorCode.PRJ001));
    project.verifyOwnership(makerId);
    verifyProjectEditable(project);

    tier.updateEditableFields(cmd.title(), cmd.description(),
                               cmd.estimatedDeliveryAt(), cmd.displayOrder());
}
```

### 완료 기준 (AC)
- Given DRAFT Project · 본인 메이커 · When `update` · Then 필드 갱신 (dirty checking)
- *(예외)* Given LIVE Project · When `update` · Then `RWD004`
- *(예외)* Given 타 메이커 · When `update` · Then `PRJ004`
- *(예외)* Given 티어 없음 · When `update` · Then `RWD001`

### Definition of Done
- [ ] `RewardTierService.update`
- [ ] `RewardTierUpdateCommand` (record · price · totalQuantity 없음)
- [ ] 통합 테스트

### 스토리 포인트
1d

### 의존성
- 선행: Story 2-1
- 후행: Story 3-3 (Controller)

---

## [Story 2-3] `RewardTierService.delete`

### User Story
- As a 메이커
- I want Project DRAFT/UPCOMING 상태에서만 티어 삭제 가능
- so that LIVE 이후 후원자 티어 유지

### 설명
- Story 2-1과 동일 검증 + 실 삭제
- Soft Delete 여부: 초기 Hard Delete (DRAFT 중이므로 후원 이력 없어 안전) · v0.0.5+ 재검토

**핵심 메서드**:
```java
public void delete(Long makerId, Long tierId) {
    RewardTier tier = tierRepository.findById(tierId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RWD001));

    Project project = projectRepository.findById(tier.getProjectId())
            .orElseThrow(() -> new BusinessException(ErrorCode.PRJ001));
    project.verifyOwnership(makerId);
    verifyProjectEditable(project);

    tierRepository.delete(tier);
}
```

### 완료 기준 (AC)
- Given DRAFT Project · 본인 메이커 · When `delete` · Then 삭제 · 이후 조회 시 `RWD001`
- *(예외)* Given LIVE · When `delete` · Then `RWD004`

### Definition of Done
- [ ] `RewardTierService.delete`
- [ ] 통합 테스트

### 스토리 포인트
0.5d

### 의존성
- 선행: Story 2-1
- 후행: Story 3-3

---

## [Story 2-4] `RewardTierService.changeOrder`

### User Story
- As a 메이커
- I want 티어의 표시 순서(`displayOrder`)를 변경
- so that 프로젝트 상세 페이지에 원하는 순서로 티어 노출

### 설명
- 단일 티어의 displayOrder만 변경 (다른 티어들 정렬은 UI에서 처리)
- Story 2-1과 동일 검증 (Project 상태 · 소유권)

**핵심 메서드**:
```java
public void changeOrder(Long makerId, Long tierId, int newOrder) {
    RewardTier tier = tierRepository.findById(tierId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RWD001));

    Project project = projectRepository.findById(tier.getProjectId())
            .orElseThrow(() -> new BusinessException(ErrorCode.PRJ001));
    project.verifyOwnership(makerId);
    verifyProjectEditable(project);

    tier.updateEditableFields(
        tier.getTitle(), tier.getDescription(),
        tier.getEstimatedDeliveryAt(), newOrder
    );
}
```

### 완료 기준 (AC)
- Given DRAFT · newOrder=2 · When `changeOrder` · Then `displayOrder=2`
- *(예외)* Given LIVE · When 호출 · Then `RWD004`

### Definition of Done
- [ ] `RewardTierService.changeOrder`
- [ ] 통합 테스트

### 스토리 포인트
0.5d

### 의존성
- 선행: Story 2-1
- 후행: Story 3-4

---

# [Epic 3] `RewardTierController` REST 5개 엔드포인트

## 목표
사용자 노출 REST API 5개(목록·생성·수정·삭제·순서 변경)를 제공하고, 모든 응답을 `ResponseEntity<ApiResponse<T>>`로 래핑한다.

## 배경
- Epic 2가 유스케이스를 완성했으므로 프레젠테이션 계층
- 목록 조회는 공개 · 생성/수정/삭제/순서는 인증 필요

## 포함 Story
- Story 3-1: `GET /api/v1/projects/{projectId}/rewards` (목록)
- Story 3-2: `POST /api/v1/projects/{projectId}/rewards` (생성)
- Story 3-3: `PATCH /api/v1/rewards/{rewardId}` · `DELETE /api/v1/rewards/{rewardId}`
- Story 3-4: `PATCH /api/v1/rewards/{rewardId}/order`

## Epic 완료 기준 (DoD)
- [ ] 5개 엔드포인트 완료
- [ ] `ResponseEntity<ApiResponse<T>>` 100% 준수
- [ ] `@WebMvcTest` 슬라이스 전 케이스
- [ ] E2E 시나리오: 프로젝트에 티어 3개 생성 → 순서 변경 → 수정 → 삭제

---

## [Story 3-1] `GET /projects/{projectId}/rewards` (목록)

### User Story
- As a 후원자
- I want 프로젝트의 리워드 티어 목록을 표시 순서대로 조회
- so that 프로젝트 상세 페이지 티어 카드 리스트 렌더링

### 설명
- 공개 (인증 불필요)
- `displayOrder ASC` 정렬
- Response: `List<RewardTierResponse>` (record)
- `isSoldOut` 계산해서 응답 필드에 포함

**핵심 메서드**:
```java
@GetMapping("/api/v1/projects/{projectId}/rewards")
public ResponseEntity<ApiResponse<List<RewardTierResponse>>> list(@PathVariable Long projectId) {
    List<RewardTierResponse> tiers = rewardTierService.findByProjectId(projectId);
    return ApiResponses.ok(tiers);
}
```

### 완료 기준 (AC)
- Given Project 100에 티어 3건 · When `GET /projects/100/rewards` · Then 200 · 3건 · displayOrder 순
- Given Project 없음 or 티어 없음 · When 호출 · Then 200 · 빈 배열 (예외 X)

### Definition of Done
- [ ] `RewardTierController.list`
- [ ] `RewardTierResponse` record (모든 필드 + `isSoldOut` 계산)
- [ ] `@WebMvcTest`

### 스토리 포인트
0.5d

### 의존성
- 선행: Epic 1
- 후행: 없음

---

## [Story 3-2] `POST /projects/{projectId}/rewards` (생성)

### User Story
- As a 메이커
- I want 티어를 등록하여 프로젝트에 후원 옵션 추가
- so that 후원자가 티어 선택 후 후원 가능

### 설명
- 인증 필요 · `@AuthenticationPrincipal`
- Request: `RewardTierCreateRequest { title, description, price, totalQuantity?, estimatedDeliveryAt?, displayOrder }`
- Bean Validation (`@NotBlank title` · `@Positive price` 등)
- Response: 201 Created + Location `/rewards/{id}` + body: `RewardTierResponse`

### 완료 기준 (AC)
- Given 인증 · DRAFT Project · 유효 request · When `POST` · Then 201 · Location + body
- *(예외 · 미인증)* Then 401 · `C004`
- *(예외 · 유효성)* Given `price=0` · When 호출 · Then 400 · `C001` (`@Positive` 위반)

### Definition of Done
- [ ] `RewardTierController.create`
- [ ] `RewardTierCreateRequest` (record + Bean Validation)
- [ ] `@WebMvcTest`

### 스토리 포인트
1d

### 의존성
- 선행: Epic 2 Story 2-1
- 후행: 없음

---

## [Story 3-3] `PATCH /rewards/{rewardId}` · `DELETE /rewards/{rewardId}`

### User Story
- As a 메이커
- I want 티어를 수정하거나 삭제
- so that DRAFT 중 조정 가능

### 설명
- 인증 · 메이커 본인만 (Service가 검증)
- PATCH: `RewardTierUpdateRequest { title, description, estimatedDeliveryAt, displayOrder }` (price · totalQuantity 없음)
- DELETE: 응답 204 No Content

### 완료 기준 (AC)
- Given DRAFT · 본인 · When `PATCH` · Then 200 · 갱신된 body
- *(예외 · LIVE)* When 호출 · Then 409 · `RWD004`
- Given DRAFT · 본인 · When `DELETE` · Then 204
- *(예외 · 타 메이커)* When 호출 · Then 403 · `PRJ004`

### Definition of Done
- [ ] `RewardTierController.update · delete`
- [ ] `RewardTierUpdateRequest` (record)
- [ ] `@WebMvcTest`

### 스토리 포인트
1d

### 의존성
- 선행: Epic 2 Story 2-2 · 2-3
- 후행: 없음

---

## [Story 3-4] `PATCH /rewards/{rewardId}/order`

### User Story
- As a 메이커
- I want 티어의 표시 순서를 조정
- so that 프로젝트 상세에서 원하는 순서로 노출

### 설명
- Request: `RewardTierOrderRequest { displayOrder }`

### 완료 기준 (AC)
- Given DRAFT · When `PATCH .../order {displayOrder: 2}` · Then 200 · `displayOrder=2`
- *(예외 · LIVE)* Then `RWD004`

### Definition of Done
- [ ] `RewardTierController.changeOrder`
- [ ] `@WebMvcTest`

### 스토리 포인트
0.5d

### 의존성
- 선행: Epic 2 Story 2-4
- 후행: 없음

---

# [Epic 4] `RewardFeaturedScheduler` (일간 배치) + 관측 + ADR

## 목표
`RewardFeaturedScheduler`를 도입해 프로젝트별 "가장 인기" 티어를 자동 갱신하고, 4개 관측 지표를 프로덕션에 노출하며, 도메인 정책을 ADR로 굳혀 후속 Product(Pledge · Chat)의 참조 근거를 확립한다.

## 배경
- Epic 1~3이 실 흐름을 완성 · 이제 운영 안전망 · 규범 문서화
- 배치 갱신은 후원 트래픽과 별개로 · 부하 분산

## 포함 Story
- Story 4-1: `RewardFeaturedScheduler` (일간 배치)
- Story 4-2: 관측 지표 4종 등록
- Story 4-3: ADR 2건 발행 · `.claude/rules/consitency.md` §5 갱신 · `backend-boundary/error-codes.md` 갱신

## Epic 완료 기준 (DoD)
- [ ] 배치 실 실행 · dev 환경 검증
- [ ] 4개 지표 Actuator 노출 확인
- [ ] ADR 2건 발행
- [ ] 규범 문서 2건 갱신

---

## [Story 4-1] `RewardFeaturedScheduler` (일간 배치)

### User Story
- As a 운영자
- I want 일간 배치로 프로젝트별 "가장 인기" 티어를 자동 갱신
- so that 후원 UX가 데이터 기반 인기 시그널 제공

### 설명
- 활성 프로젝트(`LIVE`, `UPCOMING`) 순회
- 프로젝트별 티어 로드 · `backerCount` 최대 티어 선정
- 동률 시 `displayOrder` 낮은 순 (임의 규칙 · 문서화)
- 선정 티어: `is_featured=true` · 나머지: `is_featured=false`
- 트랜잭션: 프로젝트 단위 (Failure Isolation)
- Cron: `0 30 3 * * *` (매일 03:30 · Wallet 배치 이후)

**주요 클래스**:
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class RewardFeaturedScheduler {
    private final ProjectRepository projectRepository;
    private final RewardTierRepository tierRepository;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "0 30 3 * * *")
    public void runDaily() {
        long totalUpdated = 0;
        List<Project> activeProjects = projectRepository.findByFundingStatusIn(
            List.of(FundingStatus.LIVE, FundingStatus.UPCOMING)
        );

        for (Project project : activeProjects) {
            try {
                totalUpdated += updateFeatured(project.getId());
            } catch (Exception e) {
                log.error("REWARD_FEATURED_UPDATE_FAILED projectId={}", project.getId(), e);
            }
        }

        meterRegistry.counter("reward.featured.updated.total").increment(totalUpdated);
        log.info("REWARD_FEATURED_BATCH_DONE totalProjects={} totalUpdated={}",
            activeProjects.size(), totalUpdated);
    }

    @Transactional
    protected long updateFeatured(Long projectId) {
        List<RewardTier> tiers = tierRepository.findByProjectIdOrderByDisplayOrderAsc(projectId);
        if (tiers.isEmpty()) return 0;

        RewardTier newFeatured = tiers.stream()
            .max(Comparator.comparingInt(RewardTier::getBackerCount)
                            .thenComparing(Comparator.comparingInt(RewardTier::getDisplayOrder).reversed()))
            .orElse(null);
        if (newFeatured == null) return 0;

        long updated = 0;
        for (RewardTier tier : tiers) {
            boolean shouldFeature = tier.getId().equals(newFeatured.getId());
            if (tier.isFeatured() != shouldFeature) {
                tier.setFeatured(shouldFeature);   // package-private setter · 도메인 메서드 대신 (배치용)
                updated++;
            }
        }
        return updated;
    }
}
```

### 완료 기준 (AC)
- Given LIVE Project · 티어 3건 (backerCount 10 · 5 · 2) · When 배치 · Then 1번째 티어(10) `is_featured=true` · 나머지 false
- Given 동률 (10 · 10 · 2) · When 배치 · Then `displayOrder` 낮은 티어 선정
- Given Project 없음 (활성 0) · When 배치 · Then 정상 종료 · counter=0
- *(엣지 · 부분 실패)* Given 프로젝트 3건 중 1건 실패 · Then 나머지 2건 계속 · 실패 로그 마커

### Definition of Done
- [ ] `RewardFeaturedScheduler` 구현
- [ ] `RewardTier.setFeatured` package-private setter (배치용 · 도메인 메서드 아님)
- [ ] `ProjectRepository.findByFundingStatusIn` 쿼리
- [ ] 통합 테스트: 정상 갱신 · 동률 · 부분 실패
- [ ] dev 환경에서 수동 트리거 검증

### 스토리 포인트
1.5d

### 의존성
- 선행: Epic 1
- 후행: 없음

---

## [Story 4-2] 관측 지표 4종 등록

### User Story
- As a 운영자
- I want RewardTier 관련 4개 지표가 프로덕션에서 노출됨
- so that Grafana/Actuator로 상시 관측

### 설명
- 4개 지표:
  - `reward.pledged.total{result=reserved|sold_out}` counter
  - `reward.released.total{reason=cancelled|refunded}` counter
  - `reward.featured.updated.total` counter (Story 4-1)
  - `reward.editable_locked.total` counter (`RWD004` 응답 카운트)
- 이슈 #10 Pledge · 이슈 #12 Chat에서도 참조 (진입 시 counter 증가)

### 완료 기준 (AC)
- Given 재고 차감 성공 · When `reservePledge` · Then `reward.pledged.total{result=reserved}` 1 증가
- Given 재고 0 후원 시도 · When `reservePledge` · Then `reward.pledged.total{result=sold_out}` 1 증가
- Given Prometheus 엔드포인트 · When `curl /actuator/prometheus` · Then 4개 지표 노출

### Definition of Done
- [ ] `RewardTierService`·`RewardFeaturedScheduler`에 `MeterRegistry` 주입
- [ ] counter 호출 지점 (성공·실패 case별)
- [ ] 통합 테스트: 지표 값 검증

### 스토리 포인트
0.5d

### 의존성
- 선행: Epic 2·3 · Story 4-1
- 후행: 없음

---

## [Story 4-3] ADR 2건 발행 + 규범 문서 2건 갱신

### User Story
- As a 팀 리더
- I want RewardTier의 정책·설계를 ADR과 규범 문서에 굳혀둠
- so that 후속 Product(Pledge · Chat)가 참조 가능한 진실 소스

### 설명
- 신규 ADR:
  - `013-reward-tier-aggregate-and-inventory.md` — 별도 Aggregate 배치 · 재고 관리 정책
  - `014-reward-tier-live-lock-and-featured-auto.md` — LIVE 이후 잠금 · "가장 인기" 자동 갱신
- `.claude/rules/consitency.md` §5 락 순서 표 갱신 (RewardTier 3번째)
- `workflows/backend-boundary/error-codes.md`에 RWD001~006 UX 매핑 추가

### 완료 기준 (AC)
- Given ADR 파일 · When 확인 · Then §1(배경)~§9(참고) 완결
- Given `.claude/rules/consitency.md` §5 · When 확인 · Then `Wallet → Project → RewardTier → Pledge → ...`
- Given `backend-boundary/error-codes.md` · When 확인 · Then RWD001~006 존재

### Definition of Done
- [ ] ADR 013 · 014 파일
- [ ] `.claude/rules/consitency.md` §5 갱신
- [ ] `backend-boundary/error-codes.md` RWD 섹션 추가
- [ ] 팀 공유 (PR 리뷰)

### 스토리 포인트
0.5d

### 의존성
- 선행: Epic 1~3 완료
- 후행: 없음

---

## 요약

| Epic | Story | SP 합계 |
|---|---|---|
| Epic 1: 도메인·리포지터리 | 4 | 3.0 |
| Epic 2: Service CRUD | 4 | 3.0 |
| Epic 3: REST API | 4 | 3.0 |
| Epic 4: 배치·관측·ADR | 3 | 2.5 |
| **합계** | **15** | **11.5 SP** |

**진행 순서 (필수)**: Epic 1 → 2 → 3 → 4
- Epic 1은 모든 후속의 전제
- Epic 2·3은 병렬 진행 가능 (Story 2-1 완료 후 3-2 착수)
- Epic 4는 마지막 (관측·규범 문서 굳힘)
