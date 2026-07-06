# 첫 크라우드 펀딩 사용자 릴리스 — 0.1.0v MVP (4주 사이클)

> **릴리스 문서의 역할**: `workflows/task/milestones/version/`이 **주간 작업량**을 다룬다면, 본 `release/0.0.1v/`는 **"첫 사용자에게 노출할 크라우드 펀딩 기능 라인 + 릴리스 시점"** 을 결정한다.
>
> 원칙: 전부 완성 X. **초기 3명 사용자가 크라우드 펀딩 사이클을 완주할 수 있을 정도**의 최소 기능 세트. Learn-in-production 사이클을 빠르게 열어 v2 튜닝 근거 확보.
>
> **릴리스 순번**: 이 릴리스는 **2번째 릴리스 포인트**. 첫 릴리스(0.0.1v · 2026-06 M1)는 shopping-mall 기반 결제·주문 인프라 완성. 본 0.1.0v는 크라우드 펀딩 도메인 완성 후 최초 사용자 노출.
>
> **밀도 결정**: 팀 정책 "1 Epic = 1 PR = 1 주"를 **한 Product 안에서 밀도 있게 적용**. 4 SDD 각각의 4 Epic을 하나의 마일스톤에 담아 4주 만에 완결. Epic 밀도는 System_Author 프로젝트 `0.0.4v/milestone.md` 참조 (5 Epic PR/주 근접).

---

## 타겟 릴리즈

| 항목 | 값 |
|---|---|
| **릴리즈 버전** | `0.1.0v` (첫 크라우드 펀딩 사용자 릴리스 · MINOR bump) |
| **타겟 일정** | **2026-08-05 (수) ± 3일** — M6 (0.0.6v) 완료 직후 |
| **대상 사용자** | 초기 3명 (내부 테스트 · 사용자님 지정) |
| **환경** | dev(로컬 H2) → prod(AWS + MySQL RDS) |
| **롤아웃 방식** | 3명에게 개별 안내 · 사용 관찰 · 이슈 수집 |
| **첫 릴리스 대비 델타** | shopping-mall 도메인 → crowdfunding 도메인 완전 재정의 (Product→Project 리네임 · Wallet · Reward · Pledge 신규 4 SDD) |

**왜 8월 5일**: 4 in-progress SDD (Wallet · Project · Reward · Pledge) 각 4 Epic = 16 Epic. 팀 정책 "1 Epic = 1 PR"과 System_Author 프로젝트 `0.0.4v/milestone.md` Epic 밀도(4~5 Epic PR/주)를 참조하여 **한 Product = 한 마일스톤 = 한 주** 압축. 0.0.2v(M2 · 07-01 준비) 이후 0.0.3v(07-06 Wallet) → 0.0.4v(07-13 Project) → 0.0.5v(07-20 Reward) → 0.0.6v(07-27 Pledge) → 08-05 발행. 4주 사이클.

---

## MVP 컨셉 — "선불 예치금 → 후원 → All-or-Nothing 정산 사이클"

**릴리스 시점 사용자가 완주할 수 있어야 할 최소 사이클**:

```
1. 회원가입 · 로그인 (M1 · 0.0.1v 완료분)
       ↓
2. Wallet 충전 (M3 · 0.0.3v · Wallet Product)
   ↳ PortOne 결제 창 → 확정 → 잔액 반영 · 4개 REST · SSOT 배치 · 지표 5종
       ↓
3. Project 등록 (M4 · 0.0.4v · Project Product) — 메이커가 프로젝트 launch
   ↳ product → project 리네임 · slug URL · 10-state 상태기계 · 반정규화 5필드 · 8개 REST
       ↓
4. Reward Tier 등록 (M5 · 0.0.5v · Reward Tier Product) — 다양한 가격대 리워드
   ↳ 재고 관리 · LIVE 이후 편집 잠금 · "가장 인기" 자동 갱신 · 5개 REST
       ↓
5. Pledge 후원 (M6 · 0.0.6v · Pledge Product) — 3-way 통합 (Wallet + Project + Reward)
   ↳ 락 순서 1→2→3→4 · Wallet 즉시 차감 · CONFIRMED 상태 · 4개 REST
       ↓
6. 프로젝트 마감 → All-or-Nothing 자동 정산 (M6 · 0.0.6v · Pledge Epic 3)
   - 목표 달성 → FUNDED (실제 지급)
   - 목표 미달 → REFUNDED (자동 환불 · Wallet 원상)
       ↓
7. 후원 이력 확인 (M6 · 0.0.6v · Pledge Epic 4 · REST)
```

이 사이클을 "부드럽게 돌리는 데 필요한 최소 기능"만 v1에 포함.

---

## Product별 릴리즈 스코프 (In/Out 매트릭스)

### 1. Auth (인증) — ✅ 이미 완주 (M1 · 0.0.1v)
- **In**: 회원가입 · 로그인 · JWT
- **Out**: 없음

### 2. Payment · Refund · Cart · Order — ✅ M1 완료분 유지 · M4에서 참조 리네임
- **In (유지)**: PortOne 결제 · 환불 · 이력
- **In (갱신)**: `productId` → `projectId` 참조 리네임 (M4 · Project Epic 1)
- **Out**: 일반 쇼핑 카트 UX (크라우드 펀딩 컨셉에선 Cart 미사용)

### 3. Wallet (선불 예치금) — 핵심 IN
**In (릴리스 대상 · M3 · 12.5 SP · 14 Story · 4 Epic PR)**:
- `PointTransactionType` 9종 (CHARGE/PLEDGE/PLEDGE_REFUND/PLEDGE_CANCEL/SYSTEM_ADJUST 5종 신규)
- `PointAccount` 도메인 메서드 4개 + 비관 락
- `WalletService` 4개 유스케이스 (충전 · 후원 · 환불 · 이력)
- REST 4개 엔드포인트
- 잔액 SSOT 주간 배치
- 관측 지표 5종
- ADR 001 (Wallet 잔액 SSOT · 비관 락)

**Out (v1 제외)**:
- 인출 (`WITHDRAW`) — 전자금융업 규제 · v0.0.5+
- 관리자 SYSTEM_ADJUST UI — SQL 수동 · v0.0.5+
- 신규 가입 축하 지급 — 정책 미확정
- 사용자 간 이체 (자금세탁 리스크)

### 4. Project (크라우드 펀딩 대상) — 핵심 IN
**In (릴리스 대상 · M4 · 14.0 SP · 15+ Story · 4 Epic PR)**:
- `product` → `project` 컨텍스트 리네임 (완결)
- 신규 필드 15종 · 10-state `FundingStatus` · Slug 자동생성기
- 반정규화 5필드 갱신 도메인 메서드 6종 · 비관 락
- REST 8개 엔드포인트 · QueryDSL 검색
- 관측 지표 5종
- ADR 002 (URL Slug · 10-state 상태기계)

**Out (v1 제외)**:
- 관리자 승인 워크플로우 (`UNDER_REVIEW`) — enum만 · v0.0.5+
- Milestone 부분 지급 — v0.0.6+
- 프로젝트 수정 이력 감사 — v0.0.5+
- 다국어 지원 — 한국어만

### 5. Reward Tier — 핵심 IN
**In (릴리스 대상 · M5 · 11.5 SP · 15 Story · 4 Epic PR)**:
- `RewardTier` Aggregate root + 도메인 메서드 4개
- 재고 관리 (`total_quantity` + `remaining_quantity` 병존 · NULL=무제한)
- LIVE 이후 편집 잠금
- "가장 인기" 일간 배치 자동 갱신
- REST 5개 엔드포인트
- 관측 지표 4종
- ADR 013 · 014

**Out (v1 제외)**:
- 배송비 계산 — v0.0.5+
- 디지털 vs 실물 구분 — v0.0.4+
- 다중 티어 후원 (Kickstarter 표준 · 구조상 불가)
- 국제 배송 · 통관
- 티어 수정 이력 감사

### 6. Pledge (후원) — 핵심 IN
**In (릴리스 대상 · M6 · 14.0 SP · 14 Story · 4 Epic PR)**:
- `Pledge` Aggregate + 7-state 상태기계
- 도메인 메서드 6개
- `PledgeService.pledge` 3-way 통합 (Wallet + Project + Reward)
- `PledgeService.cancel` LIVE 중 취소
- `PledgeService.closeProject` All-or-Nothing 정산
- `ProjectClosingScheduler` 5분 주기 자동 종료
- 배치 환불 (`REQUIRES_NEW` · Failure Isolation)
- SSOT 주간 검증
- REST 4개 엔드포인트
- 관측 지표 5종
- ADR 015 · 016 · 017
- Idempotency S+ 등급

**Out (v1 제외)**:
- 후원 수정 (Kickstarter 규범 · 취소 후 재후원)
- 응원 메시지 편집 — v0.0.5+
- 관리자 강제 상태 변경 — v0.0.5+
- Milestone 부분 지급 — v0.0.6+
- 후원 선물 (다른 사람 이름) — v0.0.5+
- 다중 인스턴스 스케줄러 (ShedLock) — v0.0.5+

### 7. Chat · Follow · Like · Review · Search · Media · Discovery · Maker · GitHub — ❌ v1 OUT
**Out**: 전량. 크라우드 펀딩 코어(Wallet+Project+Reward+Pledge) 완결 후 v0.1.1v+ 후속 SDD로 이관.

### 8. 배포 라인 (인프라) — 유지
**In (유지 · 0.0.1v M1 완료분)**:
- Docker · GitHub Actions CI/CD
- EC2 + MySQL RDS
- HTTPS · 도메인
- 프로덕션 프로파일

**Out**:
- 오토스케일링 세부 정책
- 다중 리전 · CDN 세밀 최적화

### 9. 관측 (Log · Op) — 기본선 유지
**In**:
- Actuator + Prometheus baseline (0.0.1v M1)
- 각 도메인 관측 지표 노출 (**본 릴리즈에서 19개 신규 지표 추가**)

**Out**:
- Grafana 세밀 대시보드 — v0.1.1v+
- APM (분산 트레이싱) — v0.1.1v+

---

## 사전 이관 필요 마일스톤 (M3 ~ M6)

각 마일스톤이 릴리즈 스코프의 어느 조각을 담당하는지 요약. 상세 스코프는 `../../version/0.0.Xv/milestone.md` 참조.

| 마일스톤 | 기간 | Product | Epic PR 수 | SP | 릴리즈 기여 |
|---|---|---|---|---|---|
| **M3 / 0.0.3v** | 07-06 ~ 07-12 | Wallet | 4 (E1·E2·E3·E4) | 12.5 | Wallet Product 완결 · ADR 001 · `consistency.md` §5 Wallet(1) · `idempotency.md` §2 지갑 충전 |
| **M4 / 0.0.4v** | 07-13 ~ 07-19 | Project | 4 (E1·E2·E3·E4) | 14.0 | Project Product 완결 · 리네임 · 10-state · Slug · ADR 002 · `consistency.md` §5 Project(2) |
| **M5 / 0.0.5v** | 07-20 ~ 07-26 | Reward | 4 (E1·E2·E3·E4) | 11.5 | Reward Product 완결 · 재고 · 편집 잠금 · Featured 배치 · ADR 013·014 · `consistency.md` §5 RewardTier(3) |
| **M6 / 0.0.6v** | 07-27 ~ 08-02 | Pledge | 4 (E1·E2·E3·E4) | 14.0 | Pledge Product 완결 · 3-way 통합 · All-or-Nothing · ADR 015·016·017 · `consistency.md` §5 최종 확정 · `idempotency.md` §2 S+ · 릴리스 준비 |
| **🚀 릴리즈** | **08-05 (수)** | — | 16 Epic PR 완결 | **52 SP** | **0.1.0v 발행** |

**변수·리스크**:
- 한 Product 완결에 실패하면 **1주 지연** = 릴리스 1주 지연
- M4 Project 리네임 (Cart/Order/Payment 참조 파장) 최고 리스크
- M6 Pledge 3-way 통합 데드락 리스크 최고

**완충 방안**:
- 매 마일스톤 D5 진척률 판정. 지연 시 Epic 4 (ADR·규범) 우선순위 조정.
- M6 D5 최종 GO/NO-GO 판정 (§릴리즈 성공 기준 6가지 신호).

---

## 릴리즈 성공 기준 (GO/NO-GO 신호)

릴리즈 직전(M6 D7 · 2026-08-02 Sun) 판정. 다음 6가지 신호 중 **5개 이상 성립** 시 릴리즈 GO.

- [ ] **Wallet SSOT 신호**: `SUM(point_history.amount) == point_account.balance` 검증 배치 무결 (프로덕션 dry-run)
- [ ] **Project 상태기계 신호**: 10-state 전이 시나리오 24개 통과 (DRAFT → LIVE → SUCCESSFUL/FAILED → FUNDED/REFUNDED → COMPLETED/CANCELLED)
- [ ] **Reward Tier 재고 신호**: 동시 후원 100건에서 재고 초과 판매 0건 (`reward.pledged.total{result=sold_out}` — 재고 0 상태 후원 성공 = 0)
- [ ] **Pledge All-or-Nothing 신호**: 성공 프로젝트 지급 · 실패 프로젝트 자동 환불 각 3회 실측 완주 · 잔여 CONFIRMED = 0
- [ ] **락 순서 신호**: Wallet(1) → Project(2) → RewardTier(3) → Pledge(4) 순서 위반 0건 (100 스레드 동시 후원 시 데드락 0건 · 스레드덤프 3회 관찰)
- [ ] **핵심 사이클 신호**: 초기 3명 사용자가 프로덕션에서 §MVP 컨셉 7단계 사이클 순회 완주

**NO-GO 조건** (하나라도 해당 시 릴리즈 연기):
- 프로덕션 배포 시 DB 마이그레이션 실패
- Pledge 3-way 통합 흐름 데드락/재시도 실패
- 3명 사용자 중 1명 이상이 로그인 · Wallet 충전 · Project 발견 · Reward 선택 · 후원 5단계 중 실패
- Wallet/Project/Reward/Pledge 단위 테스트 커버리지 < 80%
- SSOT 배치 불일치 발견 (Wallet balance ≠ SUM(history) or Project.raisedAmount ≠ SUM(pledge))

---

## 사전 검증 시나리오 (M6 D5~D7 UX 테스트)

M6 D5~D7에 3명 시뮬레이션 사용자로 다음 5개 시나리오 각각 완주 확인.

**시나리오 1 — 첫 후원자 사이클**
1. 회원가입 → 로그인
2. Wallet 충전 (PortOne 결제 완료 → 확정 → balance=10,000원)
3. Project 검색 → 원하는 프로젝트 발견 (`GET /projects` or slug lookup)
4. Reward Tier 선택 (`GET /projects/{id}/rewards`)
5. 후원 (`POST /pledges` → 201 · Wallet 자동 차감 · Pledge CONFIRMED)

**시나리오 2 — 첫 메이커 사이클**
1. 회원가입 → 로그인 (메이커)
2. Project 등록 (`POST /projects` — DRAFT · slug 자동 생성)
3. Reward Tier 3개 등록 (서포터 · 얼리 어답터 · 팀 플랜)
4. Project launch (`POST /projects/{id}/launch` — LIVE)
5. 후원자 유입 · Project.raisedAmount 실시간 갱신 확인

**시나리오 3 — All-or-Nothing 성공**
1. 시나리오 2의 프로젝트 · 목표 초과 후원 도달 (backer 3명 각 50,000원 · 목표 100,000원)
2. 프로젝트 마감일 도달
3. `ProjectClosingScheduler` 5분 이내 발동
4. Project status → SUCCESSFUL → FUNDED · 모든 pledge → FUNDED
5. 지표: `pledge.funded.total +3`

**시나리오 4 — All-or-Nothing 실패**
1. 프로젝트 · 목표 미달 후원 (backer 1명 50,000원 · 목표 100,000원)
2. 프로젝트 마감일 도달
3. `ProjectClosingScheduler` 발동
4. Project status → FAILED → REFUNDED · pledge → REFUNDED
5. Wallet 잔액 자동 환불 · balance 원상 확인

**시나리오 5 — 후원 취소**
1. 시나리오 1의 pledge (CONFIRMED)
2. 후원자가 LIVE 중 취소 (`POST /pledges/{id}/cancel`)
3. pledge → CANCELLED · Wallet 환불 · RewardTier remaining 복구 · Project raisedAmount 감소

**성공 기준**: 각 시나리오 3명 시뮬레이션 모두 완주 (조작 미스 무관, 시스템 오류 없이).

---

## 릴리즈 리스크와 관찰 포인트

| 영역 | 리스크 | 완화 |
|---|---|---|
| 4주간 순차 마일스톤 (한 Product = 한 주) | 한 마일스톤 지연 시 릴리즈 전체 밀림 (1주씩) | 매 D5 진척률 판정 · 지연 시 Epic 4 (ADR·규범) 스코프 축소 (릴리스 후 소급 검토) |
| M4 Project 리네임 | Cart/Order/Payment 참조 손상 파장 큼 · 회귀 위험 최고 | Epic 1 단독 PR + grep 회귀 · 회귀 실패 시 즉시 rollback · Test Reviewer가 M4 D2~D3 실검증 |
| M4 10-state 상태기계 | 상태 전이 규칙 복잡도 · 매트릭스 부담 | `ProjectDomainService.transitionRules` 표로 명시 · 24 케이스 매트릭스 테스트 · `@ParameterizedTest` |
| M5 Reward "가장 인기" 배치 | 다중 인스턴스 시 중복 실행 | 초기 단일 인스턴스 전제 · ADR 014에 명시 · v0.0.5+ ShedLock |
| M6 Pledge 3-way 통합 | 락 순서 위반 데드락 (본 M6 최고 리스크) | 통합 테스트에 명시적 락 순서 검증 · Chaos 100 threads · JVM 스레드덤프 3회 · Architecture Reviewer 심층 판정 |
| M6 배치 환불 | `REQUIRES_NEW` self-invocation 프록시 실수 | 환불 로직 별도 빈 분리 · Sceptical Reviewer 재검증 (M3 Wallet Epic 2에서 이미 검증된 패턴) |
| M6 성능 (100 프로젝트 × 100 pledge) | 배치 P95 초과 · dev 실측만 가능 | 지표 `pledge.closing.duration_seconds` 관측 · 미달 시 청크 배치 리팩토링 v0.0.5+ 이관 |
| 규범 문서 4회차 갱신 | `.claude/rules/*` 팀 공유 · ownership 협의 필요 | PR 사전 공유 · Sceptical Reviewer 판정 · 팀 승인 후 머지 · 지연 시 릴리스 연기 |
| 사용자 3명 확보 | 실제 사용자 릴리즈 시점 준비 필요 | 사용자님 개인 네트워크 · 릴리즈 1주 전 안내 확정 · M6 D5~D7은 시뮬레이션 |
| Static PortOne 테스트 키에서 프로덕션 키 전환 | 결제 실패 리스크 | D-2 (2026-08-03 월)에 소액 (100원) 테스트 결제 실검증 |
| 프로덕션 DB Flyway 실검증 부재 | dev H2에서만 검증 · prod MySQL 실제 마이그레이션 실행 여부 | D-1 (2026-08-04 화)에 스테이징 dry-run |
| 롤백 준비 부족 | 프로덕션 배포 후 롤백 절차 미검증 | D-1 롤백 리허설 · Git tag 되돌리기 · DB 백업 확인 |

---

## 릴리즈 이후 로드맵 (참고)

**0.1.1v (~2026-08-31, 4주 후) — Chat · Follow · Like**
- Chat SDD (Product 9) · REWARD_CARD 페이로드
- Follow SDD · Like SDD (backlog · 이슈 #03, #04)
- 사용자 간 상호작용 초기 조각

**0.2.0v (~2026-10-31, 12주 후) — Review · Search · Media · Discovery · Maker · GitHub**
- Review SDD (이슈 #05 · FUNDED pledge만 리뷰)
- Search SDD (이슈 #06 · 프로젝트 · Card 검색)
- Media SDD (이슈 #13 · 이미지 업로드 · CDN)
- Discovery SDD (이슈 #15 · 신호 수집)
- Maker Profile · GitHub Integration

**0.3.0v (~2027-01-31) — 관측 · 관리자 도메인**
- Grafana 세밀 대시보드
- 관리자 API · SYSTEM_ADJUST UI
- APM 도입
- Log SDD 확장

**1.0.0v** — 아키텍처 시프트 (미정 · 사용자 관찰 결과에 따라 결정)

---

## 관련 문서

- **마일스톤 원본**: `../../version/0.0.Xv/milestone.md` (X = 3 ~ 6)
- **UX 확인 가이드**: `../../version/0.0.Xv/ux-check.md`
- **Product SDD 원천**:
  - `workflows/task/pes/workspectrum/sdd/in-progress/product-wallet.md`
  - `workflows/task/pes/workspectrum/sdd/in-progress/product-project.md`
  - `workflows/task/pes/workspectrum/sdd/in-progress/product-reward.md`
  - `workflows/task/pes/workspectrum/sdd/in-progress/product-pledge.md`
- **원본 이슈**:
  - `workflows/task/fix/brainstorming/version/0.0.2v/issue-07-wallet-prepaid-balance.md`
  - `issue-08-project-domain.md`
  - `issue-09-reward-tier.md`
  - `issue-10-pledge-state-machine.md`
- **이전 릴리스**: 첫 릴리스 (2026-06 · M1 · shopping-mall 기반 · 별도 태그)
- **참고 벤치마크**: Kickstarter · 텀블벅 · Wadiz — All-or-Nothing 규범
- **양식 참조**: System_Author 프로젝트 `workflow/task/milestones/release/version/0.0.1v/release.md` (릴리스 문서 구조) · `0.0.4v/milestone.md` (Epic 밀도)

---

## 자가 점검 체크리스트 (M6 D7 GO/NO-GO 판정 시)

- [ ] MVP 사이클 7단계 3명 시뮬레이션 완주 검증됨
- [ ] Product 릴리스 스코프 In/Out 표가 코드·문서와 일치
- [ ] M3~M6 각 마일스톤 산출물이 Product 스코프의 어느 조각을 담당하는지 트레이스 명확
- [ ] 성공 기준 6개 신호 각각 정량 측정 가능 (관측 지표 · 테스트 시나리오)
- [ ] NO-GO 조건 명시적으로 판정 가능
- [ ] 관측 지표 19종 (Wallet 5 + Project 5 + Reward 4 + Pledge 5) 프로덕션 노출
- [ ] ADR 7건 (001·002·013·014·015·016·017) 완결
- [ ] `.claude/rules/consistency.md` §5 락 순서 최종 확정 · `.claude/rules/idempotency.md` §2 S+ 카탈로그 완결
- [ ] `workflows/backend-boundary/error-codes.md`에 전 도메인 ErrorCode UX 매핑
- [ ] 릴리즈 이후 로드맵 (0.1.1v ~ 0.3.0v)이 v1 out of scope 항목을 커버
- [ ] Backup · Rollback 절차 검증 (D-1 리허설)

---

## 릴리즈 절차 (D-Day: 2026-08-05 수)

**D-3 (금 · 07-31 · M6 D5)**: 진척률 판정 · 시나리오 A·B 사전 실행
**D-2 (토 · 08-01 · M6 D6)**: PR#3 머지 · 시나리오 C·D 실행 · 스테이징 dry-run · PortOne 프로덕션 키 소액 검증
**D-1 (일 · 08-02 · M6 D7)**: PR#4 머지 · 규범 최종 확정 · 시나리오 E 실행 · **🚀 GO/NO-GO 판정** · 롤백 리허설 · DB 백업 확인
**D-Day (수 · 08-05)**:
1. 09:00 — `develop` → `main` 머지 · Git tag `v0.1.0`
2. 09:30 — CI/CD 파이프라인 실행 · 프로덕션 배포
3. 10:00 — 초기 3명 사용자에게 안내 (개별 DM · Slack)
4. 10:00~16:00 — 실사용 관찰 (Prometheus 지표 실시간 · 로그 스캔 · 이슈 수집)
5. 16:00 — 첫날 회고 · 급한 hotfix 여부 판정
**D+7 (수 · 08-12)**: 1주 사용자 관찰 정리 · 0.1.1v 스코프 확정
