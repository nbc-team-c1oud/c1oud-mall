# M4 / 0.0.4v — Admin Backoffice · Week of 2026-07-06 ~ 2026-07-26 (D1 = 2026-07-06 Mon)

> **마일스톤의 역할**: M3(0.0.3v)에서 백오피스 프레이밍이 확정되고 workflows 리셋(크라우드 펀딩 아카이브 + 14 이슈 골격 + 5 SDD in-progress)이 완료됐다. 본 M4는 Ops Backend 상위 로드맵 21일의 첫 마일스톤으로 **product-admin-backoffice 전 Epic 완주**를 목표로 한다. 관리자 인증·롤 분기·대시보드 통합의 완결로 이후 모든 백오피스 이슈(타임세일 · 쿠폰 · CS 채팅)의 인가 전제를 마련.
>
> 한 버전 = `version/0.0.Xv/` 폴더 하나. 본 버전(0.0.4v)에는 다음 파일이 들어간다:
> - `milestone.md` *(본 문서)* — 잡힌 양 + 일정 + 의존 + Epic PR 매트릭스
> - `outcome.md`·`review.md` — 완주 후 소급 작성 (Product 완주 시점)
>
> `infra.md`·`performance.md`·`cost.md`는 본 버전에서 스킵. 배포 미포함 (로컬 개발) · 성능 실측은 M8 perf-lab 담당 · 비용 0.

**릴리스 대응**: 본 M4는 첫 백오피스 완주(0.1.0v · ~2026-07-26) 21일 로드맵의 **첫 번째** 마일스톤. 상위 M3(`../0.0.3v/milestone.md`) §Week별 로드맵 참조.

**SDD 원본**: `workflows/task/pes/workspectrum/sdd/in-progress/product-admin-backoffice.md` — Product 4 Admin 백오피스 · 관리자 롤 · 대시보드 (2 Epic · Story 8개 · SP 7.5).

---

## 0.0.4v 스코프 결정 (M3 이후, 2026-07-06)

**M3 착지 결과 요약**:
- ✅ 백오피스 프레이밍 확정 (Ops Backend 정체성 · 회의록 §1~10차)
- ✅ 크라우드 펀딩 자산 아카이브 (15 이슈 + 4 SDD + 이전 milestone)
- ✅ 0.0.3v/ 14 이슈 골격 + M3 상위 milestone 완성
- ✅ 5 SDD in-progress 착지 (admin-backoffice · search-cache · timesale-concurrency · cs-chat · perf-lab)
- ✅ 규범 참조 정합 (`.claude/rules/consitency.md` §5 락 순서 · `idempotency.md` §2 카탈로그)

**M4 축 결정 — product-admin-backoffice 전 Epic 완주**:

Admin BC는 백오피스 정체성의 진입점이자 후속 4 Product의 인가 전제. 관리자 롤 · JWT · SecurityConfig 없이는 타임세일(M6)·쿠폰(M6)·CS 채팅(M7) 이슈가 진행 불가. **주력 (~100%)**.

**Epic 단위 PR 2건 예정 (본주 목표)**:

| Epic PR | 대응 | 예상 SP | 종료 목표 |
|---|---|---|---|
| PR#1 (ADM-E1-CTX-ROLE) | Admin Epic 1 Story 1-1~1-4 (컨텍스트 골격 · Role 확장 · JWT claim · SecurityConfig · AdminController · Seed) | 2.5 | D2 |
| PR#2 (ADM-E2-DASHBOARD) | Admin Epic 2 Story 2-1~2-4 (Overview 통합 카드 · 세부 5개 API · Micrometer · 선택 Grafana docker-compose) | 3.5 | D20 |

**Total: 2 Epic PR · 총 6~7 SP** (M-Admin은 M3 원본 SDD의 필수 Product이나 볼륨은 상대적 경량. Epic 2 (Dashboard)는 후속 SDD 결과(인기 검색어·타임세일 지표·CS 통계·재고)에 의존하므로 D20에 완주하는 후행 배치).

**본 버전 제외 사유**:
- **Epic 2 조기 진입** — Dashboard 지표 원천이 M5/M6/M7 완주에 의존 (인기 검색어 M5 · 타임세일 M6 · CS 통계 M7). D20 배치 유지.
- **관리자 활동 감사 테이블** — 초기 로그 파일로 충분 · v0.0.4v+ 미정 (Product SDD §Out of Scope 준수).
- **2FA/MFA** — v0.0.5v+ (SDD §Out of Scope).
- **RBAC 매트릭스 세분화** — 초기 이원 롤(ADMIN·SUPER_ADMIN) 충분.
- **Grafana 실 세팅** — Epic 2 Story 2-4 선택 (여유 시).

---

## 진행 중 Product 잔여 인벤토리 (Before/After — M4 진입 시 vs 종료 후 예상)

**해결율 계산**: `M4 대상 / M4 진입 시 잔여 × 100%`.

| Product | 총 Story | M4 진입 시 완료 | M4 진입 시 잔여 | M4 대상 | **M4 종료 후 예상 잔여** | **해결율** |
| --- | --- | --- | --- | --- | --- | --- |
| 1. 인증 (`common/auth`) | — | 완주 (M1) | 0 | — | 0 | ✅ 완주 (M1) |
| 2. Payment·Refund·Cart·Order·Product | — | 완주 (M1) | 0 | — | 0 | ✅ M1 완결 |
| 3. **Admin Backoffice** (`product-admin-backoffice.md` · 2 Epic) | **8** | 0 | 8 | **8 Story (E1·E2)** | **0** | **100%** ✅ 완주 |
| 4. Search & Cache (`product-search-cache.md` · 3 Epic) | ~14 | 0 | ~14 | — | ~14 | 0% (M5) |
| 5. Timesale Concurrency (`product-timesale-concurrency.md` · 3 Epic) | ~15 | 0 | ~15 | — | ~15 | 0% (M6) |
| 6. CS Chat (`product-cs-chat.md` · 2 Epic) | ~8 | 0 | ~8 | — | ~8 | 0% (M7) |
| 7. Perf Lab (`product-perf-lab.md` · 3 Epic) | ~13 | 0 | ~13 | — | ~13 | 0% (M8) |
| **합계** | **~58** | **0** | **~58** | **8 Story · 2 Epic PR · 6~7 SP** | **~50** | **14%** ↑ |

### 📊 M4 예상 성과 카드

- **총 해결 대상**: 8 Story (전체 in-progress 백오피스 ~58의 **14% 소진**)
- **완주 예상 SDD Product**:
  - `product-admin-backoffice.md` Epic 1·2 — **완주 100%** (Admin BC 완결)
- **완주 예상 산출물**:
  - `nbc.c1oud_mall.admin.*` 컨텍스트 골격 (4레이어)
  - `Role` enum 확장 (USER · ADMIN · SUPER_ADMIN) + JWT claim
  - `JwtUtil` / `JwtAuthFilter` role 삽입·추출
  - Spring Security `@PreAuthorize` 관리자 경로 보호
  - `AdminController` (프로필 · 유저 목록 · 롤 변경)
  - `AdminAccountInit` (dev 시드 · SUPER_ADMIN 1건 + ADMIN 2건)
  - `AdminDashboardController` (Overview + 세부 5개 API)
  - Micrometer `admin.api.total{endpoint, role, result}` · `admin.dashboard.duration_seconds{card}`
  - `ErrorCode.ADM001~003` 등록
  - **ADR 012 발행** (Ops Backend 정체성 재정의)
- **M4 종료 후 남는 것** (다음 마일스톤 트라젝토리):
  - Search & Cache (M5 · ~14 Story) — 검색 v1/v2 · 인기 검색어 · Redis Remote
  - Timesale Concurrency (M6 · ~15 Story) — 락 3전략 · 이력서 파괴력 최상
  - CS Chat (M7 · ~8 Story) — STOMP + 상태기계
  - Perf Lab (M8 · ~13 Story) — 시딩 · k6 · 인덱싱

---

## Epic PR 매트릭스 (본주 잡힌 양)

Epic 단위 PR 진행 원칙 (workflow.md §9.4 준수):
- **한 Epic PR = 한 논리 단위 = 한 base 브랜치**. Squash merge 권장.
- Epic PR 안의 Story 커밋은 순차 누적 (`feat(admin): ...`).

### Epic PR #1 — `ADM-E1-CTX-ROLE` (Admin 컨텍스트 · Role 확장 · JWT · SecurityConfig)

**Base 브랜치**: `feature/admin-context-and-role`

**SDD 위치**:
- 파일: `workflows/task/pes/workspectrum/sdd/in-progress/product-admin-backoffice.md`
- Epic: `# [Epic 1] Admin 컨텍스트 · Role 확장 · JWT · SecurityConfig`
- Story 범위: `## [Story 1-1]` ~ `## [Story 1-4]`
- 상위 이슈 원천: `workflows/task/fix/brainstorming/version/0.0.3v/issue-01-admin-context-and-role.md`

| # | Story | 한 줄 | SP |
|---|---|---|---|
| 1 | ADM E1 S1-1 | `nbc.c1oud_mall.admin.*` 4레이어 골격 + `Role` enum 확장 (USER·ADMIN·SUPER_ADMIN) + User 엔티티 role 필드 확인 | 0.5 |
| 2 | ADM E1 S1-2 | `JwtUtil.generateToken(userId, role)` role claim 삽입 + `JwtAuthFilter` claim 추출 + `SecurityContext GrantedAuthority("ROLE_" + role)` 세팅 | 0.5 |
| 3 | ADM E1 S1-3 | `SecurityConfig` `@EnableMethodSecurity` + `/api/v1/admin/**` 필터체인 + `@PreAuthorize("hasRole('ADMIN')")` 활성 | 0.5 |
| 4 | ADM E1 S1-4 | `AdminController` (GET /admin/me · GET /admin/users · PATCH /admin/users/{id}/role) + `AdminAccountInit` `@Profile("dev\|local")` 시드 + `ErrorCode.ADM001~003` | 1 |

**PR 종료 신호**:
- `Role.values().length == 3` (USER · ADMIN · SUPER_ADMIN)
- 부팅 시 SUPER_ADMIN 1건 + ADMIN 2건 시드 확인 (dev 프로파일)
- `POST /auth/login` (관리자) → JWT claim에 `role=ADMIN` 삽입 확인
- `GET /admin/me` (USER 토큰) → 403 · `ADM001` or `C003`
- `GET /admin/me` (ADMIN 토큰) → 200 · 프로필 반환
- `PATCH /admin/users/{id}/role` (ADMIN 토큰 · SUPER_ADMIN 필요) → 403
- 기존 M1 auth 통합 테스트 회귀 없음 (`./gradlew test --tests "*Auth*"` BUILD SUCCESSFUL)

**병렬 진입 조건**: 없음 (M4 첫 PR · 이후 모든 백오피스 이슈의 전제)

### Epic PR #2 — `ADM-E2-DASHBOARD` (Overview + 세부 API + Micrometer)

**Base 브랜치**: `feature/admin-dashboard-observability`

**SDD 위치**:
- 파일: `workflows/task/pes/workspectrum/sdd/in-progress/product-admin-backoffice.md`
- Epic: `# [Epic 2] Dashboard 통합 + 세부 API + Micrometer 지표`
- Story 범위: `## [Story 2-1]` ~ `## [Story 2-4]`
- 상위 이슈 원천: `workflows/task/fix/brainstorming/version/0.0.3v/issue-13-admin-dashboard-observability.md`

| # | Story | 한 줄 | SP |
|---|---|---|---|
| 1 | ADM E2 S2-1 | `AdminDashboardController.overview` + `AdminDashboardService.overview()` (Order · TimeSaleEvent · UserCoupon · ChatRoom · PopularSearch 통합 · try-catch 격리) + `DashboardOverviewResponse` record 6 카드 | 1 |
| 2 | ADM E2 S2-2 | 세부 5개 API (`/popular-search` · `/cs-stats` · `/timesale-stats` · `/coupon-usage` · `/low-stock`) | 1 |
| 3 | ADM E2 S2-3 | Micrometer 커스텀 지표 (재사용: `chat.message.total` · `timesale.purchase.total` · `coupon.issue.total` · `search.popular.record.total` + 신규 `admin.dashboard.duration_seconds{card}`) | 0.5 |
| 4 | ADM E2 S2-4 | (선택) docker-compose Prometheus + Grafana + Dashboard JSON export + README 스크린샷 | 1 |

**PR 종료 신호**:
- `GET /admin/dashboard/overview` (ADMIN) → 200 · 6 카드 필드 모두 반환 (지표 부재 시 null · graceful degrade)
- 한 지표 강제 실패 시나리오 → 나머지 5개 정상 반환 · null 필드 명시
- 세부 5개 엔드포인트 각 200 응답 · 관련 도메인 데이터 반환
- `curl /actuator/prometheus | grep admin_` → `admin_dashboard_duration_seconds_bucket` · `admin_api_total` 노출
- (선택 · S2-4 완주 시) Grafana 대시보드 스크린샷 README `.github/assets/`에 삽입
- **의존**: PR#1 완주 (Admin 컨텍스트 · JWT role) + M5·M6·M7 완주 (지표 원천). D20에 진입.

**Reviewer 세션**: 5관점 발사 (Domain / Architecture / API-Exception / Test / Sceptical). **특히 Sceptical Reviewer가 "부분 실패 graceful degrade가 UX 관점에서 명확한가"를 판정**.

---

## Story 카테고리별 합계 (SDD Story 단위)

| 카테고리 | SDD Epic/Story | Story 수 | SP | 비중 |
| --- | --- | --- | --- | --- |
| Admin Epic 1 (컨텍스트 · 롤 · JWT) | ADM E1 S1-1~S1-4 | 4 | 2.5 | 36% |
| Admin Epic 2 (Dashboard) | ADM E2 S2-1~S2-4 | 4 | 3.5 | 64% (S2-4 선택 · 완주 여부 별도) |
| **합계** | | **8** | **6** | 100% |

**분배 근거**:
- **Epic 1 (컨텍스트·롤·JWT) 36%** — Day 2 하루 완주 · 필수 · 후속 모든 이슈의 전제
- **Epic 2 (Dashboard) 64%** — D20 배치 · 지표 원천이 M5~M7 완주에 의존 · Story 2-4 (Grafana docker-compose) 선택
- **2개 Epic PR**: 각 PR = SDD Epic 하위 Story 묶음. 신규 Story 추가 없음 · SDD 원문 그대로.

---

## 종료 신호 — "Admin 컨텍스트 완주 + Dashboard 통합 API 도착"

본 M4 종료 시점에 다음이 모두 성립해야 한다. (7 신호 중 5개 이상 → 0.0.4v 동결)

- [ ] **머지 신호**: Epic PR 2개 모두 머지 (100%) or PR#1 필수 · PR#2 최소 Story 2-1~2-3 완주 (S2-4 선택)
- [ ] **컨텍스트 신호**: `nbc.c1oud_mall.admin.*` 4레이어 골격 존재 · `Role.values()` 3개 확인
- [ ] **인증 신호**: JWT claim에 role 삽입 · JwtAuthFilter role 추출 · SecurityContext ROLE_ADMIN 세팅 확인
- [ ] **인가 신호**: `@PreAuthorize` 적용 · 일반 USER가 관리자 API 접근 시 403 · ADMIN이 SUPER_ADMIN API 접근 시 403
- [ ] **시드 신호**: dev 프로파일 부팅 시 SUPER_ADMIN 1 + ADMIN 2 시드 확인
- [ ] **Dashboard 신호**: `GET /admin/dashboard/overview` → 6 카드 반환 · 부분 실패 graceful degrade · Micrometer 지표 노출
- [ ] **테스트 신호**: `./gradlew test` BUILD SUCCESSFUL · 기존 auth 통합 테스트 회귀 없음 · 신규 테스트 8건 이상 (해피 · 엣지 · 예외)
- [ ] **ADR 신호**: ADR 012 발행 (Ops Backend 정체성 재정의)

**미합격 처리**: 5 신호 미만 시 M4를 0.0.4v로 동결하지 않고 0.0.4.1v 패치 발행 → M5 진입 지연. 특히 PR#2 (Dashboard)가 M5~M7 미완주로 지표 원천 부재 시 S2-1만 실행하고 S2-2/S2-3/S2-4는 M5~M7 완주 후 재진입.

---

## 의존 chain

```
[선행: M3 착지]
백오피스 프레이밍 확정 ✅ 완료
크라우드 펀딩 자산 아카이브 ✅ 완료
5 SDD in-progress ✅ 완료

[D1: 착수]
PR#1 (ADM-E1-CTX-ROLE)  단독 진입
  ADM E1 S1-1 ~ S1-4
     │
     ▼
Admin 컨텍스트 · 롤 · JWT · SecurityConfig 완주
     │
     ▼
(M5 · M6 · M7 진행) — 각 Product Dashboard 지표 원천 확보
     │
     ▼
PR#2 (ADM-E2-DASHBOARD) — D20 진입
  ADM E2 S2-1 ~ S2-4
     │
     ▼
Admin Product 완주 · Dashboard 도착 · M8 진입 준비
```

**병렬 진입 가능 묶음**:
- **A** (D1 · 07-06 Mon): PR#1 착수 (ADM E1 S1-1 컨텍스트 골격 + Role 확장)
- **B** (D2 · 07-07 Tue): PR#1 S1-2 (JWT claim) + S1-3 (SecurityConfig) + S1-4 (AdminController + Seed + ErrorCode) 완주 → **PR#1 머지** · M5 진입 준비
- **D3~D19** (M5·M6·M7 진행 · Dashboard 지표 원천 확보)
- **F** (D20 · 07-25 Sat): PR#2 착수 (ADM E2 S2-1 Overview 통합)
- **G** (D21 · 07-26 Sun): PR#2 S2-2 (세부 5 API) + S2-3 (Micrometer) + S2-4 (Grafana 선택) 완주 → **PR#2 머지** · 통합 로컬 검증 · 0.0.4v 동결 · `outcome.md`·`review.md` 골격

---

## 작업 일정 (Epic PR 단위 체크리스트)

D1 = 2026-07-06 (Mon). PR#1 완주 D2 · PR#2 진입 D20 · 종료 D21 = 2026-07-26 (Sun).

| 일 | 날짜 | 잡힌 작업 (Epic PR 진행) — SDD Story 기준 |
| --- | --- | --- |
| D1 (월) | 07-06 | PR#1 착수 (**ADM E1 S1-1** `nbc.c1oud_mall.admin.*` 골격 + `Role` enum 3값) — 단독 진입 · Admin 첫 코드 |
| D2 (화) | 07-07 | PR#1 **ADM E1 S1-2** (JwtUtil/JwtAuthFilter role claim) + **S1-3** (SecurityConfig @PreAuthorize) + **S1-4** (AdminController + Seed + ErrorCode) 완주 → **PR#1 머지** |
| D3~D19 | 07-08~07-24 | (M5·M6·M7 진행 · Dashboard 지표 원천 확보 · 본 마일스톤 대상 없음) |
| D20 (토) | 07-25 | PR#2 착수 (**ADM E2 S2-1** `AdminDashboardController.overview` + `DashboardOverviewResponse` + try-catch 격리) |
| D21 (일) | 07-26 | PR#2 **ADM E2 S2-2** (세부 5 API) + **S2-3** (Micrometer 지표) + **S2-4** (선택 Grafana docker-compose) 완주 → **PR#2 머지** · 통합 로컬 검증 (`curl /admin/dashboard/overview` 6 카드) · 0.0.4v 동결 · ADR 012 발행 |

**Reviewer 세션 규칙**: 각 Epic PR 머지 직전 5관점 병렬 발사 (Domain / Architecture / API-Exception / Test / Sceptical). 사용자 확인 후 머지.

---

## 리스크와 관찰 포인트

| 영역 | 리스크 | 관찰 포인트·완화 |
| --- | --- | --- |
| 기존 M1 auth 회귀 | Role enum 확장 · JWT claim 확장이 기존 USER 흐름 파괴 리스크 | `./gradlew test --tests "*Auth*"` 회귀 확인 · JwtAuthFilter 변경 시 fallback 로직 유지 (role 없을 시 USER default) |
| SecurityConfig 필터체인 순서 | 관리자 경로 필터체인이 기존 필터와 순서 충돌 시 인가 우회 리스크 | `SecurityFilterChain` `@Order` 명시 · 통합 테스트 (일반 USER → 관리자 API 403 확인) |
| Seed 계정 하드코딩 | dev 프로파일에서만 실행되도록 확실 · prod 실수 실행 방지 | `@Profile("dev \| local")` 명시 · prod는 초기 SUPER_ADMIN 1건 SQL 수동 (README 안내) |
| Dashboard 지표 원천 부재 | Epic 2가 D20에 진입할 때 M5~M7 완주 못하면 지표 조회 실패 다수 | try-catch로 각 카드 격리 · 부재 시 null · S2-2 세부 API도 개별 실패 격리 |
| Grafana docker-compose 시간 부담 | S2-4 선택 · 완주 위해 스코프 조율 필요 | S2-4는 명시적 선택 · 시간 부족 시 M5·M6·M7 진행 우선 · v0.0.5v+ 이관 |
| ADR 012 파일 위치 | `docs/adr/` vs `src/main/java/.../docs/adr/` (memory `project_adr_location`) | 백오피스 정체성 재정의는 프로젝트 전역이므로 `docs/adr/012-ops-backend-identity.md` 배치 |
| Micrometer 지표 카디널리티 | `admin.dashboard.duration_seconds{card}` 카드가 6개 · 안전 | 카디널리티 낮음 · 문제 없음 |

---

## 다음 마일스톤 (M5 / 0.0.5v) 후보

**M5 / 0.0.5v (07-08 ~ 07-21) — Search & Cache 전 Epic 완주**
- **Search Epic 1 (검색 v1)** — QueryDSL BooleanBuilder 동적 · LIKE · 커서 페이징 · count 쿼리 분리 · Projection DTO
- **Search Epic 2 (검색 v2 Caffeine + 인기 검색어)** — `@Cacheable` + Caffeine · Redis ZSet ZINCRBY/ZREVRANGE · dedup SET NX
- **Search Epic 3 (Redis Remote + Eviction)** — Serializer (String+GenericJackson2Json+JavaTimeModule) · @CacheEvict/@CachePut · TTL 정책

**Epic PR 예상 3건** (Search v1 · v2 Caffeine · Redis Remote · 총 ~8.5 SP).

---

## Product 상태 전환 신호 (M4 종료 시)

- `in-progress/product-admin-backoffice.md` — **Epic 1·2 완주** 표기 (Admin Product 완결 · 릴리스 대상 In)
- `in-progress/product-search-cache.md` — 진입 X, 상태 유지 (M5 진입 대기)
- `in-progress/product-timesale-concurrency.md` — 진입 X, 상태 유지 (M6 진입 대기)
- `in-progress/product-cs-chat.md` — 진입 X, 상태 유지 (M7 진입 대기)
- `in-progress/product-perf-lab.md` — 진입 X, 상태 유지 (M8 · perf-lab §Epic 1 시딩은 M5 진입 시 병행)
- `fix/brainstorming/version/0.0.3v/issue-01-admin-context-and-role.md` → **resolved** (Admin Epic 1 완주)
- `fix/brainstorming/version/0.0.3v/issue-13-admin-dashboard-observability.md` → **resolved** (Admin Epic 2 완주)
- **ADR 012 발행 완료** (Ops Backend 정체성 재정의)

---

## brainstorming 트리거

본 M4 완료 후 `workflows/task/pes/brainstorming/0.0.4v/` 신설 (선택):
- 관리자 활동 감사 테이블 도입 시점 결정 (`brainstorming/0.0.4v/admin-audit-log-taxonomy.md`) — v0.0.5v+ 검토
- SUPER_ADMIN 액션 확장 (이벤트 강제 종료 · 계정 잠금) (`brainstorming/0.0.4v/superadmin-action-scope.md`) — v0.0.5v+

---

## 참고

- 잔여 Story 인벤토리 출처: `workflows/task/pes/workspectrum/sdd/in-progress/` 5 Product 파일
- M3 pivot 이슈 원천: `workflows/task/fix/brainstorming/version/0.0.3v/issue-01·issue-13`
- 마일스톤 패키지 의도: `workflows/task/milestones/README.md`
- 상위 마일스톤: `workflows/task/milestones/version/0.0.3v/milestone.md` — M3 Ops Backend 상위 로드맵 21일
- 이전 마일스톤: `workflows/task/milestones/version/0.0.1v/milestone.md` — M1 · 첫 배포 릴리즈 (frozen)
- 회의록: `C:\Users\user\.claude\plans\splendid-finding-feather.md` (1~11차 세션)
- 본 버전 산출물 2종: `milestone.md`(본 문서) · `outcome.md`·`review.md`(완주 후 소급)
- 양식 진화: 본 milestone은 third-tool `workflow/task/milestones/version/0.0.5v/milestone.md` 양식을 미러링 (Epic PR 매트릭스 · 진행 중 Product 잔여 인벤토리 · 종료 신호 · 의존 chain · 리스크 · 다음 마일스톤 후보 · Product 상태 전환 · brainstorming 트리거).
- **주요 SDD 참조 비율**:
  - `product-admin-backoffice.md` **100%** (Epic 1·2 · Story 1-1~2-4 · 8 Story · SP 6)
