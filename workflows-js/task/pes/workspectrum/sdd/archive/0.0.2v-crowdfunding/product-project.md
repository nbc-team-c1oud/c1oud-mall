# [Product 6] 프로젝트 (Project · 크라우드 펀딩 대상 도메인)

## Product Vision
> c1oud-mall의 모든 크라우드 펀딩 대상. 대학생 개발자가 자신의 아이디어를 "프로젝트"로 등록·전시·펀딩하고, 후원자가 발견·후원한다. 기존 `Product` 도메인을 `Project`로 재정의하고, URL slug(`c1oud.io/p/{slug}`) · 커버·갤러리 · 10-state 펀딩 상태기계 · GitHub·카테고리·미디어 연동을 갖춘 크라우드 펀딩 컨셉의 핵심 Aggregate로 확립한다.

## 배경 및 문제
- 현재 상황 (As-Is)
  - `nbc.c1oud_mall.product.*` 컨텍스트가 **일반 쇼핑몰의 상품** 개념으로 설계됨 (재고·가격·카테고리 String 필드)
  - 상태 enum: `SALE / SOLD_OUT` — 크라우드 펀딩과 무관
  - `DummyDataInit`에 상품 32건 (신선식품 등) — 컨셉 재정의(0.0.2v)로 무의미해짐
  - Cart · Order · Payment 등이 `Product`를 참조 (마이그레이션 시 파장)
- 발생하는 문제
  - 크라우드 펀딩 컨셉(GitHub 기반 대학생 프로젝트 후원)과 도메인 언어 완전 불일치 → 신규 개발자·리뷰어 혼란
  - 상품(재고·판매)과 프로젝트(펀딩 목표·모금)의 개념 차이가 코드 곳곳에 침투 → 새로운 필드(`targetAmount`, `raisedAmount`, `backerCount`) 추가 시 도메인 정체성 흐림
  - URL이 `/products/{id}` (숫자) — SEO·공유 UX 열악
  - 상태기계 부재 · DRAFT → LIVE → SUCCESSFUL/FAILED 등 흐름 표현 불가
  - 이슈 #10 Pledge(후원)가 Project를 전제로 설계 → Project 성립 전엔 Pledge·Reward·Chat·Follow·Like 모두 대기
- 왜 지금 해결해야 하는가
  - 크라우드 펀딩 컨셉의 근본 도메인 · 후속 SDD(Reward · Pledge · Chat · Maker Profile)의 전제
  - 초기 사용자 없어 마이그레이션 비용 최소 (dummy 32건 폐기 가능)
  - 이슈 #02 카테고리, #04 좋아요, #05 리뷰, #11 메이커, #13 미디어, #14 GitHub — 모두 Project FK로 결합

## 목표 (To-Be)
- `nbc.c1oud_mall.product.*` → **`nbc.c1oud_mall.project.*`** 컨텍스트 리네임 (패키지·테이블·클래스 일괄)
- 신규 필드: `slug` (URL) · `maker_user_id` (FK) · `short_description` · `story` · `cover_image_url` · 펀딩 필드 5종 · GitHub FK · 반정규화 5종
- **10-state `FundingStatus` enum** + 상태 전이 도메인 메서드 (`launch · markSuccessful · markFailed · markFunded · markRefunded · complete · cancel`)
- URL Slug 자동 생성기 (kebab-case · UNIQUE 재시도 · 예약어 금지)
- REST 8개 엔드포인트 (CRUD + `launch` + slug lookup + my projects)
- 반정규화 필드(`raisedAmount`, `backerCount`, `likeCount`, `ratingAvg`, `ratingCount`) — Pledge · Like · Review에서 원자적 갱신
- `ErrorCode.PRJ001~006` 등록
- 락 순서 규범 반영 (`Wallet → Project → RewardTier → Pledge`)

## 설계 결정 (Design Decisions)
> 큰 갈림길의 결정. 거부된 옵션도 합리적 근거가 있었음을 명시.

- **`product` 컨텍스트를 `project`로 완전 리네임 (신규 도메인 신설 X · 필드만 추가 X)**
  - 크라우드 펀딩 컨셉과 도메인 언어 완전 일치 · 신규 개발자·리뷰어 오해 제거
  - 초기 사용자 없어 마이그레이션 비용 최소
- **URL Slug 채택 (`c1oud.io/p/{slug}`) · 수동 입력 + 자동 생성 병행**
  - 사용자가 slug 지정 가능 (선택) · 미지정 시 제목에서 자동 생성
  - UNIQUE 제약 · 충돌 시 서버가 접미 랜덤(-1234) 재시도
  - 예약어 리스트 유지 (`admin · api · login · p · me · settings` 등)
- **10-state 펀딩 상태기계** — `DRAFT → SUBMITTED → UNDER_REVIEW → UPCOMING → LIVE → SUCCESSFUL / FAILED → FUNDED / REFUNDED → COMPLETED / CANCELLED`
  - Kickstarter 규범 기반 · v0.0.5+ 관리자 승인 반영을 대비해 `UNDER_REVIEW` 포함
  - 상태 전이는 도메인 메서드만 · 외부 직접 setter 금지
- **반정규화 컬럼 (5종) 채택 · 실시간 조회 성능 우선**
  - `raisedAmount` · `backerCount` — Pledge에서 원자적 갱신 (락 순서 준수)
  - `likeCount` — ProductLike(#04)에서 갱신
  - `ratingAvg` · `ratingCount` — Review(#05)에서 갱신
  - SSOT 검증 배치는 각 도메인 Product에서 담당 (본 Product에서는 컬럼만)
- **초기 데이터 폐기 (dummy 32건 삭제 · 신규 dummy 프로젝트 3~5건 생성)**
  - 크라우드 펀딩 컨셉과 신선식품 32건은 무관 · 유지 시 사용자 혼란
  - `DummyDataInit` 재작성 · 프로젝트 예시 (Driftwood 등 디자인 참조) 초기 3건

## 대안 검토 (Alternatives Considered)

### 리네임 방식
**Option A — `product` 컨텍스트 리네임 → `project` (선택)**
- 비용: 대규모 리네임 (Cart · Order · Payment 참조 · import 갱신)
- 보상: 도메인 언어 명확 · 신규 개발자 오해 없음
- 초기 사용자 없어 마이그레이션 비용 감내 가능

**Option B — Product 유지 + Project 별도 도메인 신설**
- 거부 이유: 두 도메인 병존 → 상품·프로젝트 개념 혼재 · Cart·Order 재설계 시 혼란

**Option C — Product 유지 + 크라우드 펀딩 필드만 추가**
- 거부 이유: 코드베이스와 도메인 언어 불일치 · 유지보수 시 지속적 혼선

### URL 방식
**Option A — Slug 채택 (`/p/{slug}`) (선택)**
- 비용: 자동 생성 로직 · UNIQUE 재시도 · 예약어 관리
- 보상: SEO 친화 · 공유 URL 사람이 읽기 쉬움 · Kickstarter/텀블벅 표준

**Option B — 숫자 id 유지 (`/projects/{id}`)**
- 거부 이유: SEO 열악 · 공유 URL 볼품없음 · 크라우드 펀딩 관례 미부합

**Option C — Slug + id 혼합 (`/projects/{id}/{slug}`)**
- 거부 이유: 중복 정보 · slug 변경 시 redirect 복잡

### 상태기계 개수
**Option A — 10-state 상세 (선택)**
- 비용: enum 정의·전이 규칙 코드 증가
- 보상: v0.0.5+ 관리자 승인(`UNDER_REVIEW`) · 리워드 지급(`FUNDED` vs `COMPLETED` 분리) 등 확장 여지

**Option B — 5-state 최소 (DRAFT · LIVE · SUCCESSFUL · FAILED · CANCELLED)**
- 거부 이유: 추후 확장 시 enum 재정의 · 마이그레이션 부담

## 전체 아키텍처 (High-Level Architecture)

### 컴포넌트 배치
```
presentation ──▶ application ──▶ domain ◀── infrastructure
ProjectController  ProjectService     Project (Aggregate)     ProjectRepository
- create           - create           - launch()              - findByIdForUpdate ⭐
- update           - update           - markSuccessful()      - findBySlug
- launch           - launch           - markFailed()          - existsBySlug
- list/search      - findAll          - addPledgeAmount()     ProjectSearchRepository (QueryDSL)
- getBySlug        - findBySlug       - removePledgeAmount()  - search(query, pageable)
- my               - findByMakerId    - addLike/removeLike()  SlugGenerator
                   SlugGenerator      - addRating/removeRating() - kebab-case
                   - generate()       - complete/cancel()      - reservedWords
                                       FundingStatus (10)
                                       ProjectDomainService
                                       - transitionRules

External integrations (참조만 · 실 이관은 각 이슈):
- Category (이슈 #02) · Media (이슈 #13) · GitHubRepo (이슈 #14)
- Pledge (이슈 #10) — 반정규화 raisedAmount·backerCount 갱신 진입점
- ProductLike (이슈 #04) — likeCount 갱신
- Review (이슈 #05) — ratingAvg·ratingCount 갱신
```

### 핵심 플로우
**1. 프로젝트 생성 (DRAFT)**
```
Maker → ProjectController.create(request)
      → ProjectService.create(makerId, cmd)
        ├── SlugGenerator.generate(title)  → "driftwood-wiki"
        │     ├── kebab-case 변환
        │     ├── 예약어 검증 (admin/api/... 금지)
        │     └── UNIQUE 재시도 (충돌 시 -1234 접미)
        ├── Project.draft(makerId, name, slug, ...) 도메인 메서드
        └── projectRepository.save
      ← 201 Created + Location: /projects/{id}
```

**2. 프로젝트 오픈 (DRAFT → LIVE)**
```
Maker → ProjectController.launch(projectId)
      → ProjectService.launch(makerId, projectId)
        ├── Project FOR UPDATE 락
        ├── project.verifyOwnership(makerId)
        ├── project.launch()  → DRAFT/UPCOMING → LIVE
        │     ├── startedAt = now()
        │     ├── endedAt 검증 (targetAmount · duration 등)
        │     └── FundingStatus 전이
        └── projectRepository.save (dirty checking)
      ← 200 OK
```

**3. 반정규화 갱신 (Pledge 진입점)**
```
PledgeService.pledge (이슈 #10)
  ├── Wallet FOR UPDATE
  └── Project FOR UPDATE (락 순서 2번)
       ├── project.addPledgeAmount(amount)
       │     ├── raisedAmount += amount
       │     └── backerCount++
       └── (RewardTier · Pledge 순차)
```

**4. Slug Lookup**
```
Client → GET /api/v1/projects/p/{slug}
       → ProjectController.getBySlug
       → ProjectService.findBySlug
         └── projectRepository.findBySlug  (인덱스 활용)
       ← 200 OK + ProjectDetailResponse
```

### Out-of-Process 의존
- **RDS (MySQL)** — `project` 테이블 · slug UNIQUE · 다중 인덱스
- (참조) Category, Media, GitHubRepo — 각 이슈 SDD에서 정의 (본 Product는 FK 컬럼만)

## 실패 모드 / 운영 관측 (Failure Modes & Observability)

### 실패 시나리오와 응답
| 시나리오 | ErrorCode | HTTP | 클라이언트 권장 동작 |
| --- | --- | --- | --- |
| 프로젝트 없음 | `PRJ001` PROJECT_NOT_FOUND | 404 | 목록으로 이동 |
| Slug 중복 (수동 입력) | `PRJ002` PROJECT_SLUG_DUPLICATE | 409 | 다른 slug 입력 유도 |
| 유효하지 않은 상태 전이 (예: LIVE → DRAFT) | `PRJ003` PROJECT_INVALID_STATUS | 400 | 상태 확인 후 재시도 |
| 메이커 본인 아님 (수정·오픈 등) | `PRJ004` PROJECT_OWNERSHIP_FAILED | 403 | 접근 거부 modal |
| 목표 금액 0 이하 | `PRJ005` PROJECT_TARGET_AMOUNT_INVALID | 400 | 폼 재입력 |
| 기간 오류 (endedAt < startedAt · 최소 1일 미만 등) | `PRJ006` PROJECT_DURATION_INVALID | 400 | 폼 재입력 |
| Slug 자동 생성 재시도 초과 (10회) | `C002` INTERNAL_ERROR | 500 | 재시도 안내 |

### 로깅 정책
- **항상 기록**:
  - `requestId` · `projectId` · `slug` · `makerUserId` · `fundingStatus` 전이 · `targetAmount` · 액션(create/launch/update/cancel)
- **debug**: Slug 생성 시도 로그 (충돌 재시도 감지)
- **절대 금지**:
  - 개인정보(story 원문에 사용자 이메일 등 포함될 시 로그에 그대로 출력 X)

### 관측 지표
- `project.created.total{status}` — counter — 생성된 프로젝트 (DRAFT · UNDER_REVIEW 등 최초 상태)
- `project.launched.total` — counter — DRAFT → LIVE 전이
- `project.status.gauge{status}` — gauge — 상태별 프로젝트 수 (배치 갱신)
- `project.slug.retry.total` — counter — Slug 자동 생성 재시도 횟수 (많으면 예약어·UNIQUE 정책 재검토)
- `project.pledge_amount.updated.total` — counter — 반정규화 갱신 진입 횟수

## 롤아웃 / 마이그레이션 (Rollout)

### 전제
- 초기 사용자 없음 · 기존 `product` 테이블 dummy 32건 폐기 가능
- 일괄 배포 (단일 프로파일 전환)
- Payment · Cart · Order 등 참조 코드는 리네임과 동시 갱신 (하나의 PR 세트)

### Product 의존성
- **선행**: **Wallet Product** (Pledge에서 라이언트 검증 시 잔액 확인 · 실 결합은 Pledge에서)
- **후행**: **Reward Tier Product** · **Pledge Product** · **Chat** · **Maker Profile** · **GitHub** · **Media**
- **동시 대응**: Cart · Order 리네임 (Product 참조 필드명 `product_id` → `project_id`)

### Epic·Story 의존성 그래프
```
Epic 1 (리네임·마이그레이션) ──► Epic 2 (도메인 확장)
                                       ├─► Epic 3 (반정규화 5필드)
                                       └─► Epic 4 (REST API + Slug)
```

### 환경별 설정 분기
| 항목 | dev (H2) | prod (RDS MySQL) |
| --- | --- | --- |
| 테이블명 | `project` (마이그레이션 후) | 동일 |
| Slug UNIQUE 제약 | 컬럼 UNIQUE + 인덱스 | 동일 |
| DummyDataInit | 프로젝트 3~5건 (Driftwood · Palette Lab · Pixel Drift 등 디자인 예시 재사용) | 활성 (초기 사용자 유도용) |
| ProjectStatusScheduler (반정규화 gauge 갱신) | 5분 주기 (편의) | 1시간 주기 |
| 예약어 리스트 위치 | `application.yml` 또는 상수 클래스 | 동일 |

## 성공 지표 (KPI)
| 지표 | 목표 값 | 측정 방법 |
| --- | --- | --- |
| 프로젝트 생성 성공률 | ≥ 99% (Slug 자동 생성 정상 동작 기준) | `project.created.total{result=success}` / 전체 |
| Slug 자동 생성 재시도율 | ≤ 5% (재시도 필요 케이스) | `project.slug.retry.total` / `project.created.total` |
| 유효하지 않은 상태 전이 시도 | ≤ 1% (사용자 실수 감지) | `PRJ003` 응답 카운트 / 전체 상태 전이 시도 |
| 반정규화 정합성 (`raisedAmount == SUM(pledge.amount)`) | 100% | 주간 배치 검증 (Pledge Product에서 담당) |
| 프로젝트 상세 조회 응답 시간 P95 | ≤ 200ms | 로그 · 지표 |

## Scope
**In Scope**:
- `product` 컨텍스트 → `project` 컨텍스트 리네임 (패키지·테이블·클래스·참조 코드)
- 신규 필드 15종 (`slug` · `makerUserId` · `shortDescription` · `story` · `coverMediaId` · `targetAmount` · `raisedAmount` · `backerCount` · `startedAt` · `endedAt` · `fundingStatus` · `categoryId` · `likeCount` · `ratingAvg` · `ratingCount`)
- `FundingStatus` 10-state enum + 도메인 메서드
- Slug 자동 생성기 + 예약어 리스트
- REST 8개 엔드포인트
- 반정규화 5필드 갱신 도메인 메서드 (실 갱신 호출은 각 이슈 Product에서)
- `ErrorCode.PRJ001~006`
- 락 순서 규범 반영 (Project는 2번째)
- 마이그레이션 스크립트 (기존 테이블 rename or drop+create)
- DummyDataInit 재작성 (프로젝트 3~5건)

**Out of Scope**:
- **관리자 승인 워크플로우** (`UNDER_REVIEW`) — enum에 포함하지만 실 전이 로직은 v0.0.5+ 별도 관리자 도메인
- **Milestone 부분 지급** — Kickstarter도 안 하는 확장 · v0.0.4+ 후속
- **프로젝트 수정 이력 (감사 로그)** — v0.0.5+ 필요 시
- **다국어 지원** — 한국어 필드만
- **관리자 강제 상태 변경 API** — v0.0.5+
- **Cart 도메인 대응** — 크라우드 펀딩에서는 Cart 미사용 (Selection으로 재해석 · 이슈 #08에 명시된 대로 초기 미사용) · 다만 Cart의 `product_id` 컬럼은 리네임 필요 (동시 대응 스코프)

## 대상 사용자
- **메이커 (Maker)** — 프로젝트 생성·수정·오픈·완료 처리
- **후원자 (Backer)** — 프로젝트 발견·상세 조회·slug 공유
- **관리자** — v0.0.5+에 `UNDER_REVIEW` 승인 (본 Product에서는 상태만 정의)
- **개발/QA** — 리네임 회귀 검증 · 상태 전이 규칙 통합 테스트
- **후속 SDD 작성자** — Reward Tier · Pledge · Chat 등의 참조 대상

## 연결된 Epic 목록
- [ ] Epic 1: `product` → `project` 리네임 + 마이그레이션 + `DummyDataInit` 재작성
- [ ] Epic 2: 도메인 확장 (URL slug + 메이커 FK + 10-state 상태기계 + 펀딩 필드)
- [ ] Epic 3: 반정규화 5필드 + 도메인 메서드 (`addPledgeAmount · removePledgeAmount · addLike · addRating` 등)
- [ ] Epic 4: REST API 8개 + `ProjectController` + `ProjectSearchRepository`

## 관련 문서
- **원본 이슈**: `workflows/task/fix/brainstorming/version/0.0.2v/issue-08-project-domain.md`
- **선행 SDD**: `product-wallet.md` (Product 5)
- **후행 SDD (M3 진행 예정)**:
  - `product-reward.md` (Product 7 · Project 하위 리워드 티어)
  - `product-pledge.md` (Product 8 · Project.raisedAmount·backerCount 반정규화 갱신 진입점)
  - `product-chat.md` (Chat Rich Message · ChatRoom.projectId FK)
  - `product-maker.md` (Maker Profile · Project.makerUserId 참조)
  - `product-github.md` (GitHub Repo · Project.githubRepoId FK)
  - `product-media.md` (Media · Project.coverMediaId 참조)
  - `product-discovery.md` (Discovery · use-for-project 흐름)
- **관련 이슈**: `issue-02-category-hierarchy.md` · `issue-04-product-like.md` · `issue-05-review.md` · `issue-06-search.md` · `issue-11~14`
- **신규 ADR 후보**:
  - "Product → Project 리네임 · 도메인 언어 정합성"
  - "URL Slug 정책 · 예약어 · 재시도"
  - "10-state FundingStatus 상태기계 · 전이 규칙"
- **규범 갱신 예정**:
  - `.claude/rules/consitency.md` §5 락 순서 · Project 2번째 명시 (Wallet 다음)
  - `workflows/backend-boundary/error-codes.md` PRJ001~006 매핑 추가

## 열린 질문 (Open Questions)
- **관리자 승인(`UNDER_REVIEW`) 도입 시점** — v0.0.5+ 예정 · 상태 enum 포함하나 실 워크플로우는 후속 SDD
- **Project 수정 이력 감사 로그** — 초기 미도입 · 언제 필요? (트리거 조건: 사용자 100+ 시)
- **Slug 변경 허용 여부** — 초기 결정: 오픈 후 slug 변경 금지 (SEO·공유 URL 안정성) · v0.0.5+ 재검토
- **`FundingStatus` `UPCOMING` 자동 전이 시점** — startedAt 도달 시 자동 → LIVE? · 스케줄러 필요 여부 (이슈 #10 ProjectClosingScheduler와 결합)
- **프로젝트 삭제 정책** — Soft Delete? · DRAFT만 삭제 허용? (초기: DRAFT만 삭제 가능 · LIVE 이후는 CANCELLED 상태로만)
- **최대 스크린샷 개수** — 이슈 #13 Media 상수(MAX_SCREENSHOT_COUNT=4) 준수

## 제품 수준 완료 기준 (Product-level DoD)
- [ ] 모든 Epic DoD 통과
- [ ] `product` 컨텍스트 완전 삭제 · `project` 컨텍스트로 이관 확인 (grep 검사)
- [ ] Cart · Order · Payment 참조 코드 리네임 완료 · 통합 테스트 회귀 없음
- [ ] E2E 시나리오: 프로젝트 생성(DRAFT) → 오픈(LIVE) → 반정규화 갱신 (Pledge 통합 시) → SUCCESSFUL/FAILED 전이 → COMPLETED
- [ ] Slug 자동 생성 · 예약어 · 재시도 정확성 통합 테스트
- [ ] 10-state 상태 전이 규칙 매트릭스 테스트 (각 상태 × 유효/무효 전이)
- [ ] ADR 최소 2건 발행 (리네임 · Slug 정책)
- [ ] `.claude/rules/consitency.md` §5 락 순서 갱신
- [ ] `workflows/backend-boundary/error-codes.md` PRJ001~006 매핑
- [ ] 관측 지표 5개 프로덕션 노출

---

# [Epic 1] `product` → `project` 리네임 + 마이그레이션 + `DummyDataInit`

## 목표
`nbc.c1oud_mall.product.*` 컨텍스트를 `nbc.c1oud_mall.project.*`로 완전 리네임하고, 기존 `product` 테이블을 `project`로 재구성하며, 후속 Epic이 안정적으로 확장할 수 있는 기반을 마련한다.

## 배경
- 컨셉 재정의(0.0.2v)로 도메인 언어 완전 변경 필요
- Cart · Order · Payment 등 참조 코드 대량 수정 필요 (한 세트 PR)
- 초기 사용자 없어 dummy 32건 폐기 · 신규 dummy 프로젝트 생성

## 포함 Story
- Story 1-1: `product` 패키지 · 클래스 리네임 (`Product` → `Project` 등) + import 갱신
- Story 1-2: 테이블·컬럼 마이그레이션 (`product` → `project` · 참조 컬럼 `product_id` → `project_id`)
- Story 1-3: `DummyDataInit` 재작성 (프로젝트 3~5건 · 디자인 예시 활용)

## Epic 인수 시나리오
- Given 리네임 완료 상태
- When `grep -r "product" src/main/java`
- Then 크라우드 펀딩 관련 참조 0건 (테스트·주석은 제외 · 별도 카운트)

*(엣지)* Given 기존 통합 테스트 실행 · Then Payment 확정 흐름 · Cart 담기 등 회귀 없음 (전 테스트 통과)

## Epic 완료 기준 (DoD)
- [ ] 3개 Story 완료
- [ ] 모든 기존 통합 테스트 통과 (회귀 없음)
- [ ] `product` 컨텍스트 폴더 삭제 확인
- [ ] `project` 테이블 · 인덱스 스키마 확인

## Epic 기술 결정 / 대안 (Epic-Level Alternatives)
- **마이그레이션 전략**: `RENAME TABLE product TO project` (Option A · MySQL 지원 · 데이터 보존) vs DROP + CREATE (초기 데이터 폐기 시)
  - 채택: **DROP + CREATE** — 초기 사용자 없어 기존 데이터(dummy 32건) 무의미 · 신규 dummy 3~5건 재구성

---

## [Story 1-1] `product` 패키지 · 클래스 리네임 + import 갱신

### User Story
- As a c1oud-mall 개발자
- I want `product` 패키지의 모든 클래스를 `project`로 리네임하고 참조 코드를 갱신
- so that 크라우드 펀딩 컨셉의 도메인 언어가 코드베이스 전체에 반영됨

### 설명
- 리네임 대상 (예상 목록):
  - `nbc.c1oud_mall.product` → `nbc.c1oud_mall.project` 패키지
  - `Product` → `Project` 엔티티
  - `ProductStatus` → `FundingStatus` (별개 이슈로 재정의 · Story 2-3 참조)
  - `ProductJpaRepository` → `ProjectJpaRepository`
  - `ProductJpaRepositoryCustom` → `ProjectJpaRepositoryCustom`
  - `ProductJpaRepositoryImpl` → `ProjectJpaRepositoryImpl`
  - `ProductService` → `ProjectService`
  - `ProductController` → `ProjectController`
  - `ProductResponse` · `ProductListResponse` 등 DTO 전부 리네임
- 참조 코드 갱신 (Cart · Order · Payment · Refund 등):
  - `import nbc.c1oud_mall.product.*` → `import nbc.c1oud_mall.project.*`
  - `Product product` 변수명 → `Project project`
  - `productId` → `projectId` (Cart · Order 등)
- IDE 자동 리네임 도구 활용 권장 (IntelliJ Shift+F6)

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.project.domain.Project` (기존 Product 리네임)
- `nbc.c1oud_mall.project.application.ProjectService`
- `nbc.c1oud_mall.project.presentation.ProjectController`
- 등 전체 리네임 (~15개 클래스 예상)

### 완료 기준 (AC)
- Given 리네임 완료 · When `grep -rn "class Product" src/main/java` · Then 결과 0건
- Given 리네임 완료 · When `grep -rn "class Project" src/main/java` · Then 최소 1건 (엔티티 존재)
- Given Cart 통합 테스트 · When 실행 · Then `Project` 참조로 정상 통과
- *(회귀)* Given Payment 확정 흐름 통합 테스트 · When 실행 · Then 리네임에도 정상 통과

### Definition of Done
- [ ] 15개 이상 클래스 리네임 완료 (`src/main/java/nbc/c1oud_mall/project/...`)
- [ ] `src/main/java/nbc/c1oud_mall/product/` 폴더 완전 삭제 확인
- [ ] Cart · Order · Payment · Refund 참조 코드 갱신 (`productId` → `projectId` 등)
- [ ] 모든 기존 통합 테스트 회귀 없이 통과
- [ ] git diff 리뷰 시 리네임 스코프 명확 (다른 변경 섞이지 않음)

### 스토리 포인트
2d (파장이 큰 리네임 · 회귀 검증 포함)

### 의존성
- 선행: 없음
- 후행: Story 1-2 · Story 1-3 · Epic 2 전체

---

## [Story 1-2] 테이블·컬럼 마이그레이션 (`product` → `project`)

### User Story
- As a c1oud-mall 인프라 담당
- I want `product` 테이블을 `project`로 재구성하고 참조 컬럼(`product_id` → `project_id`)을 일괄 갱신
- so that DB 스키마가 도메인 언어와 일치

### 설명
- 초기 사용자 없어 **DROP + CREATE 전략** 채택 (`RENAME TABLE` 대신)
- 새 테이블 스키마는 Story 2-1 이후 최종 확정 · 여기서는 리네임만 (기존 필드 유지)
- Cart · Order · Payment · Refund 등 FK 참조 컬럼도 갱신
- **JPA `ddl-auto=update`** 사용 (0.0.1v 결정 · 커밋 `fcc67db`)
  - dev/local: `create-drop` — 자동 새 스키마
  - prod: `update` — 컬럼 rename은 자동 안 됨 · 수동 SQL 스크립트 필요 (별도 Story or Flyway 검토)
- **결정**: prod는 아직 실 사용자 없으므로 `create-drop` 임시 전환 or 데이터 폐기 후 자동 update

**핵심 파일**:
- `src/main/resources/db/migration/` 또는 SQL 스크립트 (Flyway 도입 시)
- 기존 dummy 데이터 정리 · `DummyDataInit` 대체 (Story 1-3)

**주요 마이그레이션 SQL (초기 방향)**:
```sql
-- 기존 테이블 폐기 (초기 사용자 없어 안전)
DROP TABLE IF EXISTS product;

-- 새 project 테이블 생성 (JPA ddl-auto가 자동 · 참조용 스키마)
-- Story 2-1에서 최종 스키마 확정 · 여기서는 기존 필드 유지
CREATE TABLE project (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  name              VARCHAR(255) NOT NULL,
  description       TEXT         NULL,
  status            VARCHAR(30)  NOT NULL,   -- FundingStatus enum · Story 2-3에서 확장
  category          VARCHAR(100) NOT NULL,   -- Story 2-1에서 categoryId FK로 변경
  price             BIGINT       NULL,       -- Story 2-1에서 DROP
  stock_quantity    INT          NULL,       -- Story 2-1에서 DROP
  created_at        TIMESTAMP    NOT NULL,
  updated_at        TIMESTAMP    NOT NULL,
  PRIMARY KEY (id)
);

-- Cart · Order · Refund 참조 컬럼 갱신
ALTER TABLE cart_item CHANGE COLUMN product_id project_id BIGINT NOT NULL;
ALTER TABLE order_item CHANGE COLUMN product_id project_id BIGINT NOT NULL;
ALTER TABLE refund_item CHANGE COLUMN product_id project_id BIGINT NOT NULL;
```

### 완료 기준 (AC)
- Given 마이그레이션 완료 · When `SHOW TABLES` (또는 H2 상당) · Then `project` 테이블 존재 · `product` 없음
- Given Cart 담기 통합 테스트 · When 실행 · Then `cart_item.project_id` 컬럼 참조 정상
- Given `DummyDataInit` (Story 1-3 이후) · When prod 부팅 · Then `project` 테이블에 dummy 3~5건 삽입

### Definition of Done
- [ ] `product` 테이블 폐기 확인
- [ ] `project` 테이블 · 인덱스 스키마 확인 (JPA ddl-auto or SQL 스크립트)
- [ ] Cart · Order · Refund 등 참조 컬럼 `project_id`로 갱신
- [ ] 통합 테스트 회귀 없음
- [ ] prod 배포 시 마이그레이션 문서화 (READMe 또는 별도 문서)

### 스토리 포인트
1d

### 의존성
- 선행: Story 1-1
- 후행: Story 1-3 · Epic 2

---

## [Story 1-3] `DummyDataInit` 재작성 (프로젝트 3~5건)

### User Story
- As a c1oud-mall QA / 초기 사용자
- I want prod·dev 환경에서 초기 예시 프로젝트 3~5건 자동 생성
- so that 사용자가 첫 방문 시 빈 화면 대신 실감 있는 UX 확인

### 설명
- 기존 `DummyDataInit`의 상품 32건 폐기
- 신규 프로젝트 3~5건 생성 (디자인 참조 이름 재사용):
  - "Driftwood — 셀프호스팅 팀 위키" (개발도구)
  - "Palette Lab — 개발자용 컬러 토큰 생성기" (라이브러리·SDK)
  - "Pixel Drift — 로컬 LLM 추론 서버 프레임워크" (AI·데이터)
  - "Cloudkey — 오픈소스 API 키 관리자" (개발도구)
  - "Lumen — 셀프호스팅 상태 모니터링 대시보드" (인프라·DevOps)
- 각 프로젝트 필드:
  - `slug` 수동 지정
  - 임의 메이커 (`makerUserId` · 초기 admin or dummy user)
  - `targetAmount` 500만~2000만원 랜덤
  - `fundingStatus` 다양 (2건 LIVE · 1건 SUCCESSFUL · 1건 UPCOMING · 1건 DRAFT)
- prod 프로파일에서도 활성 (초기 사용자 유도용 · 이슈 #08 결정 반영)

**핵심 파일**:
- `nbc.c1oud_mall.infrastructure.DummyDataInit` (기존 확장)

### 완료 기준 (AC)
- Given prod 프로파일 부팅 · When `DummyDataInit` 실행 · Then `project` 테이블에 3~5건 삽입 확인
- Given 재부팅 · When `DummyDataInit` 재실행 · Then 중복 삽입 없음 (slug UNIQUE로 감지)
- *(엣지 · 이미 존재)* Given `slug='driftwood-wiki'` 이미 존재 · When 재삽입 시도 · Then silent skip (UNIQUE 위반 catch)

### Definition of Done
- [ ] `DummyDataInit` 재작성 (기존 상품 32건 로직 폐기)
- [ ] 프로젝트 3~5건 생성 로직 (slug · targetAmount · status 다양성)
- [ ] prod 부팅 시 정상 삽입 검증
- [ ] 중복 삽입 방어 (`existsBySlug`)

### 스토리 포인트
0.5d

### 의존성
- 선행: Story 1-1 · 1-2 · Epic 2 완료 (스키마 최종 확정 후)
- 후행: 없음 (Product-level 완결)

### [명세 변경 이력]
- (Epic 2 완료 후 실 스키마 확정 시 재조정)

---

# [Epic 2] Project 도메인 확장 (URL slug + 메이커 FK + 10-state 상태기계 + 펀딩 필드)

## 목표
`Project` 엔티티에 크라우드 펀딩 컨셉 필드를 확장하고, `FundingStatus` 10-state 상태기계와 도메인 메서드(`launch · markSuccessful · markFailed` 등)를 도입하여 후속 Product(Reward · Pledge)의 참조 근거를 확립한다.

## 배경
- Epic 1이 리네임을 완료했으므로 이제 필드 확장
- Slug · 메이커 FK · 펀딩 필드 · 상태기계는 크라우드 펀딩의 핵심
- 반정규화 필드는 Epic 3에서 별도 처리 (구현 순서 분리)

## 포함 Story
- Story 2-1: URL slug 필드 + `SlugGenerator` (자동 생성 + 예약어 + UNIQUE 재시도)
- Story 2-2: 메이커 FK (`makerUserId`) + 소유권 검증 도메인 메서드
- Story 2-3: `FundingStatus` 10-state enum + 상태 전이 도메인 메서드
- Story 2-4: 펀딩 필드 (`targetAmount · startedAt · endedAt · shortDescription · story · coverMediaId · categoryId · githubRepoId`) + Bean Validation

## Epic 인수 시나리오
- Given 메이커 `userId=1` 로그인
- When `POST /api/v1/projects {name: "My Project", targetAmount: 1000000, ...}`
- Then 201 + `slug` 자동 생성 · `fundingStatus=DRAFT` · `makerUserId=1`

*(엣지)* Given `slug` 수동 입력 · 중복 · When 생성 · Then `PRJ002` · 다른 slug 유도

## Epic 완료 기준 (DoD)
- [ ] 4개 Story 완료
- [ ] 10-state 상태 전이 매트릭스 테스트 (매트릭스 자동 생성 or 수동 목록)
- [ ] Slug 자동 생성 · 예약어 · UNIQUE 재시도 통합 테스트
- [ ] ADR 초안 작성 (Slug 정책 · 상태기계)

---

## [Story 2-1] URL slug + `SlugGenerator` (자동 생성 + 예약어 + UNIQUE 재시도)

### User Story
- As a 메이커
- I want 프로젝트 생성 시 URL slug을 자동 생성 (또는 수동 지정)
- so that `c1oud.io/p/{slug}` 형태로 SEO 친화 URL 확보

### 설명
- 필드: `slug VARCHAR(80) NOT NULL UNIQUE`
- **자동 생성 규칙**:
  - 프로젝트 제목 → 소문자 · 공백 → 하이픈 · 특수문자 제거
  - 한글은 로마자 변환 (Naver Papago API? · 초기엔 랜덤 접미로 처리 · 단순화)
  - 최소 3자 · 최대 80자
  - 예약어(`admin · api · login · p · me · settings · discovery` 등) 금지 · 충돌 시 접미(-1234)
  - UNIQUE 충돌 시 최대 10회 재시도 (`slug-1234` 등 랜덤 접미)
- **수동 지정 가능**: 사용자가 slug 입력 시 검증 후 사용 (중복 시 `PRJ002`)
- 정규식: `^[a-z0-9-]{3,80}$` · 앞뒤 하이픈 금지 · 연속 하이픈 금지

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.project.application.SlugGenerator`
- `nbc.c1oud_mall.project.domain.Project` (slug 필드 추가)

**주요 메서드**:
```java
@Component
@RequiredArgsConstructor
public class SlugGenerator {
    private static final Set<String> RESERVED_WORDS = Set.of(
        "admin", "api", "login", "logout", "signup", "p", "me",
        "settings", "discovery", "search", "notifications"
    );
    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{1,78}[a-z0-9])$");
    private static final int MAX_RETRY = 10;

    private final ProjectRepository projectRepository;

    public String generate(String title) {
        String base = normalize(title);   // 소문자·하이픈 변환
        if (RESERVED_WORDS.contains(base)) base = base + "-project";

        String candidate = base;
        for (int i = 0; i < MAX_RETRY; i++) {
            if (!projectRepository.existsBySlug(candidate)) return candidate;
            candidate = base + "-" + randomSuffix();   // 4자리 랜덤
            log.debug("Slug retry: {} → {}", base, candidate);
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);   // 재시도 초과 (극히 드묾)
    }

    public void validateManual(String slug) {
        if (!VALID_PATTERN.matcher(slug).matches())
            throw new BusinessException(ErrorCode.PRJ002);
        if (RESERVED_WORDS.contains(slug))
            throw new BusinessException(ErrorCode.PRJ002);
        if (projectRepository.existsBySlug(slug))
            throw new BusinessException(ErrorCode.PRJ002);
    }
}
```

### 완료 기준 (AC)
- Given title="Driftwood Wiki" · When `SlugGenerator.generate(title)` · Then `"driftwood-wiki"` 반환 (충돌 없을 시)
- Given title="Admin Tool" · When 생성 · Then `"admin-project"` 반환 (예약어 회피)
- Given `existsBySlug("driftwood-wiki")=true` · When `generate("Driftwood Wiki")` · Then `"driftwood-wiki-{랜덤}"` 반환 (재시도)
- Given slug="Bad Slug" (수동) · When `validateManual` · Then `BusinessException(PRJ002)` · 대소문자 위반
- Given slug="api" (수동) · When `validateManual` · Then `PRJ002` · 예약어

### Definition of Done
- [ ] `SlugGenerator` 구현 (자동 생성 · 예약어 · 재시도)
- [ ] `Project.slug` 컬럼 · UNIQUE 제약 (JPA `@Column(unique=true)`)
- [ ] `ProjectRepository.existsBySlug` · `findBySlug` 메서드
- [ ] `ErrorCode.PRJ002` 등록
- [ ] 단위 테스트: 정규 · 예약어 · 재시도 · 수동 검증 각 케이스
- [ ] 통합 테스트: 100건 동시 생성 시 slug 정확성

### 스토리 포인트
1.5d

### 의존성
- 선행: Epic 1
- 후행: Story 4-1 (Controller create)

---

## [Story 2-2] 메이커 FK (`makerUserId`) + 소유권 검증 도메인 메서드

### User Story
- As a 프로젝트 서비스
- I want 프로젝트에 메이커(User FK) 필드를 두고 소유권 검증 도메인 메서드 제공
- so that 수정·오픈·삭제 시 본인 확인 가능

### 설명
- 필드: `maker_user_id BIGINT NOT NULL` + 인덱스 (`idx_project_maker (maker_user_id, funding_status)`)
- 도메인 메서드: `verifyOwnership(userId)` — 위반 시 `PRJ004`
- User FK는 논리적 참조만 (DB FK 제약 X · BC 협력은 직접 호출 · ADR 006)
- 실 User 존재 검증은 Service에서 (별도)

**핵심 클래스/인터페이스**:
- `Project` (필드 추가 + `verifyOwnership` 메서드)

**주요 메서드**:
```java
public void verifyOwnership(Long userId) {
    if (!Objects.equals(this.makerUserId, userId))
        throw new BusinessException(ErrorCode.PRJ004);
}
```

### 완료 기준 (AC)
- Given `project.makerUserId=1` · When `verifyOwnership(1)` · Then 정상 (예외 없음)
- Given `project.makerUserId=1` · When `verifyOwnership(2)` · Then `BusinessException(PRJ004)`

### Definition of Done
- [ ] `Project.makerUserId` 필드 + 인덱스
- [ ] `verifyOwnership` 도메인 메서드
- [ ] `ErrorCode.PRJ004` 등록
- [ ] 단위 테스트: 소유권 성공 · 위반

### 스토리 포인트
0.5d

### 의존성
- 선행: Epic 1
- 후행: 수정·오픈·삭제 관련 Story 전체

---

## [Story 2-3] `FundingStatus` 10-state enum + 상태 전이 도메인 메서드

### User Story
- As a 프로젝트 도메인 개발자
- I want 10-state `FundingStatus` enum과 상태 전이 도메인 메서드(`launch · markSuccessful · markFailed · markFunded · markRefunded · complete · cancel`)를 제공
- so that Pledge · 관리자 승인 · 후속 흐름이 상태에 안전하게 접근

### 설명
- 10-state enum:
  ```
  DRAFT · SUBMITTED · UNDER_REVIEW · UPCOMING · LIVE
  · SUCCESSFUL · FUNDED · FAILED · REFUNDED · COMPLETED · CANCELLED
  ```
- **전이 매트릭스** (허용 전이만 도메인 메서드로 노출):
  - `DRAFT → SUBMITTED` (제출 · v0.0.5+ 관리자 승인 있을 시)
  - `DRAFT → LIVE` (초기 자동 등록 시 · v0.0.3~4)
  - `SUBMITTED → UNDER_REVIEW → UPCOMING → LIVE` (v0.0.5+ 워크플로우)
  - `LIVE → SUCCESSFUL` (목표 달성 판정) or `LIVE → FAILED` (미달성 · ProjectClosingScheduler)
  - `SUCCESSFUL → FUNDED` (Pledge 지급 완료)
  - `FAILED → REFUNDED` (Pledge 환불 완료)
  - `FUNDED → COMPLETED` (프로젝트 최종 종료 · 리워드 발송 등)
  - 모든 상태 → `CANCELLED` (메이커 취소 · LIVE 이전만)

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.project.domain.FundingStatus` (enum)
- `Project` (전이 도메인 메서드 7개)

**주요 메서드**:
```java
public void launch() {
    if (fundingStatus != DRAFT && fundingStatus != UPCOMING)
        throw new BusinessException(ErrorCode.PRJ003);
    if (targetAmount <= 0)
        throw new BusinessException(ErrorCode.PRJ005);
    if (endedAt == null || endedAt.isBefore(LocalDateTime.now().plusDays(1)))
        throw new BusinessException(ErrorCode.PRJ006);
    this.fundingStatus = LIVE;
    this.startedAt = LocalDateTime.now();
}

public void markSuccessful() {
    if (fundingStatus != LIVE) throw new BusinessException(ErrorCode.PRJ003);
    this.fundingStatus = SUCCESSFUL;
}

public void markFailed() {
    if (fundingStatus != LIVE) throw new BusinessException(ErrorCode.PRJ003);
    this.fundingStatus = FAILED;
}

public void markFunded() {
    if (fundingStatus != SUCCESSFUL) throw new BusinessException(ErrorCode.PRJ003);
    this.fundingStatus = FUNDED;
}

public void markRefunded() {
    if (fundingStatus != FAILED) throw new BusinessException(ErrorCode.PRJ003);
    this.fundingStatus = REFUNDED;
}

public void complete() {
    if (fundingStatus != FUNDED) throw new BusinessException(ErrorCode.PRJ003);
    this.fundingStatus = COMPLETED;
}

public void cancel(Long makerId) {
    verifyOwnership(makerId);
    if (fundingStatus == FUNDED || fundingStatus == COMPLETED)
        throw new BusinessException(ErrorCode.PRJ003);
    this.fundingStatus = CANCELLED;
}
```

### 완료 기준 (AC)
- Given `fundingStatus=DRAFT`·`targetAmount=1000000`·`endedAt=+30일` · When `launch()` · Then `fundingStatus=LIVE`·`startedAt` 설정
- *(예외)* Given `fundingStatus=LIVE` · When `launch()` · Then `PRJ003`
- *(예외)* Given `targetAmount=0` · When `launch()` · Then `PRJ005`
- *(예외)* Given `endedAt=+0.5일` · When `launch()` · Then `PRJ006`
- *(매트릭스)* 각 상태 × 각 전이 메서드 조합 100% 검증

### Definition of Done
- [ ] `FundingStatus` enum 10-state
- [ ] 7개 전이 도메인 메서드 (`Project.launch·markSuccessful·markFailed·markFunded·markRefunded·complete·cancel`)
- [ ] `ErrorCode.PRJ003·005·006` 등록
- [ ] 단위 테스트: 상태 전이 매트릭스 (10 × 7 = 70+ 케이스 · 유효/무효 분류)
- [ ] ADR 초안: "10-state FundingStatus 상태기계 · 전이 규칙"

### 스토리 포인트
1.5d

### 의존성
- 선행: Story 2-2 (verifyOwnership 사용)
- 후행: Story 4-3 (Controller launch), Pledge Product

---

## [Story 2-4] 펀딩 필드 + 참조 FK + Bean Validation

### User Story
- As a 프로젝트 서비스
- I want Project에 크라우드 펀딩 관련 필드(`shortDescription · story · coverMediaId · targetAmount · startedAt · endedAt · categoryId · githubRepoId`)를 추가하고 Bean Validation을 적용
- so that Story · 리워드 · Pledge 흐름이 안전하게 참조

### 설명
- 신규 필드:
  - `short_description VARCHAR(200) NOT NULL` — 한 줄 소개
  - `story LONGTEXT NULL` — 상세 스토리 (마크다운)
  - `cover_media_id BIGINT NULL` — 커버 (이슈 #13 Media FK)
  - `target_amount BIGINT NOT NULL` — 목표 금액 (`@Positive`)
  - `started_at TIMESTAMP NULL` — 펀딩 시작 (`launch` 시 설정)
  - `ended_at TIMESTAMP NULL` — 펀딩 종료 (사용자 지정)
  - `category_id BIGINT NULL` — 카테고리 (이슈 #02 FK)
  - `github_repo_id BIGINT NULL` — GitHub (이슈 #14 FK)
- Bean Validation:
  - `@NotBlank name` (기존)
  - `@NotBlank @Size(max=200) shortDescription`
  - `@Positive @Max(1_000_000_000) targetAmount` (최대 10억)
  - `@Future endedAt` (미래만) — 생성 시점 · launch 시점에도 재검증

**핵심 파일**:
- `Project` 엔티티 확장

### 완료 기준 (AC)
- Given `Project` 생성 · When 저장 · Then 신규 필드 컬럼 확인
- Given `targetAmount=0` · When Bean Validation · Then `@Positive` 위반 · `C001` (Controller)
- Given `endedAt` = 어제 · When Bean Validation · Then `@Future` 위반 · `C001`

### Definition of Done
- [ ] 8개 신규 필드 · JPA · Bean Validation
- [ ] `Project` 정적 팩토리 `Project.draft(...)` 확장 (신규 필드 파라미터)
- [ ] 단위 테스트: 유효성 검증 각 필드

### 스토리 포인트
1d

### 의존성
- 선행: Story 2-1 · 2-2 · 2-3
- 후행: Epic 3 · 4

---

# [Epic 3] 반정규화 5필드 + 도메인 메서드

## 목표
Project에 반정규화 카운트 필드 5종을 추가하고, 각 진입점(Pledge · Like · Review)이 호출할 도메인 메서드를 제공하여 조회 성능을 확보하면서 정합성을 도메인에서 강제한다.

## 배경
- 프로젝트 목록·상세 조회 시 `raisedAmount`, `backerCount`, `likeCount`, `ratingAvg`, `ratingCount`가 매번 필요
- 매 조회마다 `COUNT(*)`/`SUM(*)`은 부하 · 반정규화 필수
- 각 진입점에서 도메인 메서드 호출로 원자적 갱신 · 트랜잭션 안 락 준수

## 포함 Story
- Story 3-1: `raisedAmount` · `backerCount` 반정규화 + `addPledgeAmount · removePledgeAmount` 도메인 메서드
- Story 3-2: `likeCount` · `ratingAvg` · `ratingCount` 반정규화 + `addLike · removeLike · addRating · removeRating · changeRating` 도메인 메서드
- Story 3-3: `ProjectRepository.findByIdForUpdate` 비관 락 쿼리

## Epic 인수 시나리오
- Given `project(raisedAmount=0, backerCount=0)` · When `project.addPledgeAmount(5000)` · Then `raisedAmount=5000`·`backerCount=1`

*(엣지)* Given `raisedAmount=5000, backerCount=1` · When `removePledgeAmount(5000)` · Then `raisedAmount=0, backerCount=0`

## Epic 완료 기준 (DoD)
- [ ] 3개 Story 완료
- [ ] 도메인 메서드 단위 테스트 (성공 · 언더플로우 · 정확성)
- [ ] 비관 락 통합 테스트

---

## [Story 3-1] `raisedAmount` · `backerCount` 반정규화 + Pledge 진입 메서드

### User Story
- As a Pledge 서비스 (이슈 #10)
- I want Project에 `addPledgeAmount(amount)` · `removePledgeAmount(amount)` 도메인 메서드를 두어 원자적으로 반정규화 갱신
- so that 후원 흐름의 프로젝트 반정규화 필드가 정확

### 설명
- 필드: `raised_amount BIGINT NOT NULL DEFAULT 0`, `backer_count INT NOT NULL DEFAULT 0`
- 도메인 메서드:
  - `addPledgeAmount(long amount)` — Pledge 확정 시 (`raisedAmount += amount; backerCount++`)
  - `removePledgeAmount(long amount)` — Pledge 취소·환불 시 (언더플로우 방지)
- 유효성:
  - `amount <= 0` → `WAL003` 재사용 or 신규 `PRJ005`
  - `raisedAmount < amount` (removePledgeAmount 시) → 시스템 이상 · 로그 마커 + `INTERNAL_ERROR`

**핵심 클래스/인터페이스**:
- `Project` (필드 · 메서드)

**주요 메서드**:
```java
public void addPledgeAmount(long amount) {
    if (amount <= 0) throw new BusinessException(ErrorCode.PRJ005);
    this.raisedAmount += amount;
    this.backerCount++;
}

public void removePledgeAmount(long amount) {
    if (amount <= 0) throw new BusinessException(ErrorCode.PRJ005);
    if (this.raisedAmount < amount || this.backerCount <= 0) {
        log.error("PROJECT_RAISED_AMOUNT_UNDERFLOW projectId={} amount={} current={}",
            id, amount, raisedAmount);
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
    this.raisedAmount -= amount;
    this.backerCount--;
}

public boolean isFundingSuccessful() {
    return raisedAmount >= targetAmount;
}
```

### 완료 기준 (AC)
- Given `raisedAmount=0, backerCount=0` · When `addPledgeAmount(5000)` · Then `raisedAmount=5000, backerCount=1`
- Given `raisedAmount=10000, backerCount=2` · When `removePledgeAmount(5000)` · Then `raisedAmount=5000, backerCount=1`
- *(예외)* Given `raisedAmount=1000` · When `removePledgeAmount(5000)` · Then `INTERNAL_ERROR` · 로그 마커
- Given `raisedAmount=100000, targetAmount=100000` · When `isFundingSuccessful()` · Then true

### Definition of Done
- [ ] 2개 필드 · 3개 도메인 메서드
- [ ] 단위 테스트: 성공 · 언더플로우 · isFundingSuccessful
- [ ] `ErrorCode.PRJ005` 등록 (이미 등록됐다면 재사용)

### 스토리 포인트
0.5d

### 의존성
- 선행: Epic 2
- 후행: Pledge Product

---

## [Story 3-2] `likeCount` · `ratingAvg` · `ratingCount` 반정규화 + Like·Review 진입 메서드

### User Story
- As a Like/Review 도메인
- I want Project에 좋아요·별점 관련 반정규화 도메인 메서드를 두어 원자적 갱신
- so that 프로젝트 카드 UI에 매번 COUNT/AVG 없이 즉시 표시

### 설명
- 필드:
  - `like_count BIGINT NOT NULL DEFAULT 0`
  - `rating_avg DECIMAL(3,2) NOT NULL DEFAULT 0.00`
  - `rating_count BIGINT NOT NULL DEFAULT 0`
- 도메인 메서드:
  - `incrementLike()` — Like 추가 시 (이슈 #04 재해석)
  - `decrementLike()` — Like 취소 시 (언더플로우 방지)
  - `addRating(int rating)` — Review 작성 시 (이슈 #05)
  - `removeRating(int rating)` — Review 삭제 시
  - `changeRating(int oldRating, int newRating)` — Review 수정 시

**주요 메서드**:
```java
public void incrementLike() { this.likeCount++; }

public void decrementLike() {
    if (this.likeCount <= 0) {
        log.error("PROJECT_LIKE_COUNT_UNDERFLOW projectId={}", id);
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
    this.likeCount--;
}

public void addRating(int rating) {
    if (rating < 1 || rating > 5) throw new BusinessException(ErrorCode.RV005);
    BigDecimal newTotal = ratingAvg.multiply(BigDecimal.valueOf(ratingCount))
                                   .add(BigDecimal.valueOf(rating));
    this.ratingCount++;
    this.ratingAvg = newTotal.divide(BigDecimal.valueOf(ratingCount), 2, RoundingMode.HALF_UP);
}

public void removeRating(int rating) {
    if (ratingCount <= 0) {
        log.error("PROJECT_RATING_COUNT_UNDERFLOW projectId={}", id);
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
    BigDecimal newTotal = ratingAvg.multiply(BigDecimal.valueOf(ratingCount))
                                   .subtract(BigDecimal.valueOf(rating));
    this.ratingCount--;
    this.ratingAvg = ratingCount == 0
            ? BigDecimal.ZERO
            : newTotal.divide(BigDecimal.valueOf(ratingCount), 2, RoundingMode.HALF_UP);
}

public void changeRating(int oldRating, int newRating) {
    if (newRating < 1 || newRating > 5) throw new BusinessException(ErrorCode.RV005);
    BigDecimal newTotal = ratingAvg.multiply(BigDecimal.valueOf(ratingCount))
                                   .subtract(BigDecimal.valueOf(oldRating))
                                   .add(BigDecimal.valueOf(newRating));
    this.ratingAvg = newTotal.divide(BigDecimal.valueOf(ratingCount), 2, RoundingMode.HALF_UP);
}
```

### 완료 기준 (AC)
- Given `likeCount=0` · When `incrementLike()` · Then `likeCount=1`
- Given `likeCount=5` · When `decrementLike()` · Then `likeCount=4`
- *(예외)* Given `likeCount=0` · When `decrementLike()` · Then `INTERNAL_ERROR`
- Given `ratingCount=0, ratingAvg=0` · When `addRating(4)` · Then `ratingCount=1, ratingAvg=4.00`
- Given `ratingCount=1, ratingAvg=4.00` · When `addRating(5)` · Then `ratingCount=2, ratingAvg=4.50`
- *(정확성)* 프로퍼티 기반 테스트: 임의 N개 rating 추가 → 평균 정확성

### Definition of Done
- [ ] 3개 필드 · 5개 도메인 메서드
- [ ] 단위 테스트: 각 메서드 성공 · 언더플로우 · 정확성 (프로퍼티 기반)
- [ ] 이슈 #04(Like) · #05(Review) SDD 작성 시 본 메서드 호출 지점 명시

### 스토리 포인트
1d

### 의존성
- 선행: Epic 2
- 후행: Like Product · Review Product

---

## [Story 3-3] `ProjectRepository.findByIdForUpdate` 비관 락 쿼리

### User Story
- As a Pledge · Like · Review 서비스
- I want `ProjectRepository.findByIdForUpdate`로 비관 락 획득
- so that 반정규화 갱신 시 동시성 race 방지

### 설명
- Wallet Story 1-3와 동일 패턴 (`@Lock(LockModeType.PESSIMISTIC_WRITE)`)
- 락 순서 (`.claude/rules/consitency.md` §5): Wallet(1) → Project(2)

**주요 메서드**:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Project p WHERE p.id = :id")
Optional<Project> findByIdForUpdate(@Param("id") Long id);
```

### 완료 기준 (AC)
- Given `Project(id=1)` 저장 · When `findByIdForUpdate(1)` (TX 안) · Then Optional non-empty · 락 획득
- *(동시성)* 2개 트랜잭션 동시 진입 · Then 순차 처리
- *(예외 · TX 밖)* Then `TransactionRequiredException`

### Definition of Done
- [ ] `findByIdForUpdate` 메서드 추가
- [ ] `@DataJpaTest` 슬라이스 · 동시성 통합 테스트

### 스토리 포인트
0.5d

### 의존성
- 선행: Epic 1
- 후행: Story 3-1 · 3-2 사용, Pledge · Like · Review Product 전체

---

# [Epic 4] REST API 8개 + `ProjectController` + `ProjectSearchRepository`

## 목표
프로젝트 생성·조회·수정·오픈·검색·my projects 사용자 노출 API 8개를 제공하고, 이슈 #06 검색 SDD의 진입점을 갖춘다.

## 배경
- Epic 1~3이 도메인·리포지터리를 완성했으므로 이제 프레젠테이션
- 검색은 이슈 #06(Search Product)의 세부이나, 기본 목록 조회는 본 Product 스코프

## 포함 Story
- Story 4-1: `POST /projects` (생성 · DRAFT) + `PATCH /projects/{id}` (수정)
- Story 4-2: `POST /projects/{id}/launch` (DRAFT → LIVE) + `POST /projects/{id}/cancel`
- Story 4-3: `GET /projects/{id}` + `GET /projects/p/{slug}` (Slug lookup) + `GET /users/me/projects`
- Story 4-4: `GET /projects` (목록 · 기본 필터 · 이슈 #06 상세 검색은 후속)

## Epic 완료 기준 (DoD)
- [ ] 4개 Story · 8개 엔드포인트 완료
- [ ] `ResponseEntity<ApiResponse<T>>` 100% 준수
- [ ] `@WebMvcTest` 슬라이스 전 케이스
- [ ] E2E: 프로젝트 생성 → 오픈 → 조회 (slug) → 수정 → 취소

---

## [Story 4-1] `POST /projects` (생성) + `PATCH /projects/{id}` (수정)

### User Story
- As a 메이커
- I want 프로젝트를 생성(DRAFT)하고 이후 수정 가능
- so that 등록 4단계 워크플로우 (디자인 참조 · 자동 저장) UX 구현

### 설명
- **`POST /api/v1/projects`**:
  - 인증 필요 (`@AuthenticationPrincipal`)
  - Request: `ProjectCreateRequest { name, shortDescription, story?, targetAmount, endedAt, categoryId?, coverMediaId?, githubRepoId?, slug? }`
  - 응답: `ResponseEntity<ApiResponse<ProjectResponse>>` · 201 Created · Location 헤더
  - Slug 자동 생성 (수동 지정 시 검증)
  - 초기 상태: `DRAFT`
- **`PATCH /api/v1/projects/{id}`**:
  - 인증 · 메이커 본인만
  - Request: `ProjectUpdateRequest` (부분 업데이트 · 각 필드 nullable)
  - `LIVE 이후 상태`에서는 story · coverMediaId 등 일부만 수정 가능 (도메인 규칙 · 별도 결정)
  - **결정**: 초기 v0.0.3에는 DRAFT만 수정 가능 · LIVE 이후는 수정 잠금 (`PRJ003`) · v0.0.4+ 세밀화

**핵심 클래스**:
- `ProjectController.create` · `update`
- `ProjectCreateRequest` · `ProjectUpdateRequest` · `ProjectResponse` (record)

### 완료 기준 (AC)
- Given 인증 · 유효 request · When `POST /projects` · Then 201 · `ApiResponse.success(projectResponse)` · Location
- Given 유효한 slug 지정 · When 생성 · Then slug 사용 · 200
- *(예외)* Given slug 중복 · When 생성 · Then 409 · `PRJ002`
- *(예외)* Given 미인증 · When 호출 · Then 401 · `C004`
- *(예외 · 수정)* Given LIVE 상태 · When `PATCH` · Then 400 · `PRJ003`
- *(예외 · 소유권)* Given 타 메이커 · When `PATCH` · Then 403 · `PRJ004`

### Definition of Done
- [ ] `ProjectController.create · update`
- [ ] Request/Response DTO (record)
- [ ] `ProjectService.create · update`
- [ ] `@WebMvcTest` 슬라이스 (성공 · 미인증 · slug 중복 · 소유권 · 상태 위반)
- [ ] OpenAPI 문서 갱신

### 스토리 포인트
1.5d

### 의존성
- 선행: Epic 2 전체
- 후행: 없음

---

## [Story 4-2] `POST /projects/{id}/launch` + `POST /projects/{id}/cancel`

### User Story
- As a 메이커
- I want 프로젝트를 오픈(`launch`) · 취소(`cancel`)
- so that 상태기계 전이를 명시적 API로 트리거

### 설명
- **`POST /api/v1/projects/{id}/launch`** (DRAFT → LIVE):
  - 메이커 본인 · Project.launch() 호출
  - 유효성 (targetAmount · endedAt) 검증
  - 응답: `ProjectResponse` (전이 후 상태)
- **`POST /api/v1/projects/{id}/cancel`** (모든 상태 → CANCELLED):
  - 메이커 본인 · FUNDED/COMPLETED 이후는 불가

### 완료 기준 (AC)
- Given DRAFT · targetAmount=1000000 · endedAt=+30일 · When `launch` · Then 200 · fundingStatus=LIVE
- *(예외)* Given LIVE · When `launch` · Then 400 · `PRJ003`
- *(예외)* Given targetAmount=0 · When `launch` · Then 400 · `PRJ005`
- Given LIVE · When `cancel` · Then 200 · fundingStatus=CANCELLED

### Definition of Done
- [ ] `ProjectController.launch · cancel`
- [ ] `@WebMvcTest`: 성공 · 상태 위반 · 유효성

### 스토리 포인트
0.5d

### 의존성
- 선행: Story 4-1 · Epic 2
- 후행: 없음

---

## [Story 4-3] `GET /projects/{id}` + `GET /projects/p/{slug}` + `GET /users/me/projects`

### User Story
- As a 후원자·메이커
- I want 프로젝트를 id 또는 slug로 조회하고, 내가 만든 프로젝트 목록 확인
- so that 상세·공유·마이페이지 UX 구현

### 설명
- **`GET /api/v1/projects/{id}`** — id로 조회 · 공개
- **`GET /api/v1/projects/p/{slug}`** — slug로 조회 · 공개 · 디자인 URL 규격
- **`GET /api/v1/users/me/projects?status=&page=&size=`** — 내가 메이커인 프로젝트 목록 · 인증 필요 · 상태 필터

### 완료 기준 (AC)
- Given `Project(id=1)` 존재 · When `GET /projects/1` · Then 200 · `ProjectDetailResponse`
- *(예외)* Given 없는 id · When 호출 · Then 404 · `PRJ001`
- Given slug="driftwood-wiki" · When `GET /projects/p/driftwood-wiki` · Then 200 · 동일 프로젝트
- Given makerUserId=1 · 프로젝트 3건 · When `GET /users/me/projects` · Then 3건 반환

### Definition of Done
- [ ] `ProjectController.get · getBySlug · myProjects`
- [ ] `ProjectDetailResponse` record (모든 필드 노출)
- [ ] `@WebMvcTest` 슬라이스

### 스토리 포인트
0.5d

### 의존성
- 선행: Epic 2
- 후행: 없음

---

## [Story 4-4] `GET /projects` (기본 목록)

### User Story
- As a 후원자
- I want 프로젝트 기본 목록을 페이징으로 조회
- so that 탐색 페이지 카드 그리드 표시

### 설명
- Query: `?page=0&size=20&sort=latest`
- 상세 검색·필터는 이슈 #06 검색 SDD (`GET /projects/search`) 스코프
- 본 엔드포인트는 최소 기본 목록 (기본 필터: `fundingStatus IN (LIVE, UPCOMING)`)
- 정렬 화이트리스트: `latest` (createdAt DESC) · `dday_asc` (endedAt ASC) — 이슈 #06 규범

### 완료 기준 (AC)
- Given 프로젝트 10건 (다양 상태) · When `GET /projects` · Then LIVE·UPCOMING만 반환 (기본 필터)
- Given `?sort=dday_asc` · When 호출 · Then endedAt 오름차순

### Definition of Done
- [ ] `ProjectController.list`
- [ ] `ProjectListResponse` record (요약 필드만)
- [ ] `@WebMvcTest`

### 스토리 포인트
0.5d

### 의존성
- 선행: Epic 2
- 후행: 이슈 #06 Search SDD (확장)

---

## 요약

| Epic | Story | SP 합계 |
|---|---|---|
| Epic 1: 리네임·마이그레이션 | 3 | 3.5 |
| Epic 2: 도메인 확장 | 4 | 4.5 |
| Epic 3: 반정규화 | 3 | 2.0 |
| Epic 4: REST API | 4 | 3.0 |
| **합계** | **14** | **13.0 SP** |

**진행 순서 (필수)**: Epic 1 → 2 → 3 → 4
- Epic 1 완료 없이 Epic 2 이하 불가능
- Epic 2 완료 후 Epic 3·4는 병렬 진행 가능
- Epic 1 Story 1-3 (DummyDataInit)는 Epic 2 완료 후로 미룰 수도 있음 (스키마 최종 확정 후)
