# [Product 4] Admin 백오피스 · 관리자 롤 · 대시보드

## Product Vision
> 관리자가 백오피스에서 타임세일·쿠폰·CS를 발동·모니터링할 수 있도록 인증·인가·역할 분기의 골격을 완성하고, 모든 도메인 지표(인기 검색어·이벤트 판매·쿠폰 사용률·CS 통계·재고 임박)를 통합 조회하는 대시보드 API로 백오피스 정체성의 시각적 무대를 완성한다.

## 배경 및 문제

- 현재 상황 (As-Is)
  - `nbc.c1oud_mall.auth.*`에 슈퍼어드민 골격은 존재하나 관리자 롤 세분화 없음 (M1 완료)
  - 관리자 전용 API·컨트롤러 미분리 · 일반 API와 혼재 위험
  - 도메인별 통계 조회 API가 산발적 · 통합 대시보드 없음
- 발생하는 문제
  - 백오피스 정체성의 시작점 — 관리자 인증·역할 분기 없이는 타임세일·쿠폰·CS 이슈 진행 불가
  - `@PreAuthorize("hasRole('ADMIN')")` 강제 없이는 관리자 API가 일반 사용자에게 노출 위험
  - 대시보드 없으면 리뷰어가 GitHub 열어봤을 때 백오피스 정체성 시각 인상 얕음
- 왜 지금 해결해야 하는가
  - Day 2 첫 착수 대상 · 이후 이슈 06/08/09/13의 전제
  - 시연 화면 (관리자 대시보드) 없으면 README 스크린샷 파괴력 낮음

## 목표 (To-Be)

- 신규 컨텍스트 `nbc.c1oud_mall.admin.*` (4레이어)
- `Role` enum 확장: 기존 `USER` + 신규 `ADMIN` · `SUPER_ADMIN`
- JWT claim에 role 삽입 · `JwtUtil` 확장 · `JwtAuthFilter` role 추출 · Spring Security `@PreAuthorize` 활용
- `AdminController` 골격 (`/api/v1/admin/**`) · SecurityConfig 관리자 경로 롤 검증
- `AdminAccountInit` — dev/prod 프로파일 조건부 관리자 계정 시드
- `AdminDashboardController` — 통합 카드 6개 (오늘 매출 · 총 주문 · 활성 타임세일 · 대기 CS · 발급 쿠폰 · 인기 검색어 Top 5) + 세부 엔드포인트
- ErrorCode `ADM001~003`

## 설계 결정 (Design Decisions)

- **JWT claim에 role 삽입** — 매 요청 DB 조회 없이 인가 판정 (M1 인증 그대로 확장)
- **Spring Security `@PreAuthorize("hasRole('ADMIN')")`** — 어노테이션 기반이라 각 컨트롤러 메서드에서 명시적
- **SUPER_ADMIN vs ADMIN 이원 롤** — 롤 변경 · 계정 삭제 등 위험 액션은 SUPER_ADMIN만 (초기: 롤 변경만 · v0.0.4v+ 확장)
- **관리자 계정 시드** — 로컬 개발용 필수 (테스트 계정 하드코딩은 dev/local만 · prod는 초기 SUPER_ADMIN 1건 SQL 수동)
- **대시보드 = 통합 카드 API + 세부 API 병존** — 첫 화면 로딩 최소 요청 (통합) + 상세 드릴다운 (세부)
- **대시보드는 read-only + 30초 캐시** — Micrometer 지표 실시간성 요구 X · 관리자 새로고침 30초 주기 예상

## 대안 검토 (Alternatives Considered)

### 관리자 롤 관리

**Option A (선택) — JWT claim 확장 + Spring Security @PreAuthorize**
- 비용: JWT 확장 · claim 파싱
- 보상: 매 요청 DB 조회 없음 · 표준 · 어노테이션 명확

**Option B — DB 조회 기반 인가 (매 요청)**
- 거부: 성능 저하 · JWT의 stateless 이점 상실

**Option C — 세션 기반 롤**
- 거부: 기존 JWT 정책과 상충

### 관리자 컨트롤러 분리

**Option A (선택) — `nbc.c1oud_mall.admin.*` 별도 컨텍스트**
- 비용: 컨트롤러 클래스 다수 (도메인별 AdminXController)
- 보상: 도메인별 관리자 로직 응집 · URL prefix `/api/v1/admin/**` 일관

**Option B — 기존 도메인 컨트롤러에 관리자 엔드포인트 추가**
- 거부: URL 인가 정책 혼재 · 도메인 컨트롤러 responsibility 폭발

### 대시보드 응답 형태

**Option A (선택) — 통합 카드 + 세부 API 병존**
- 통합: `GET /admin/dashboard/overview` (6개 카드)
- 세부: `GET /admin/dashboard/popular-search` · `/cs-stats` · `/timesale-stats` 등
- 보상: 첫 로딩 빠름 · 드릴다운도 가능

**Option B — 통합만**
- 거부: 응답 페이로드 큼 · 부분 갱신 불가

**Option C — 세부만 (프론트가 병렬 호출)**
- 거부: 첫 로딩 요청 6번 · 사용자 지연

## 전체 아키텍처 (High-Level Architecture)

### 컴포넌트 배치
```
presentation ──▶ application ──▶ domain ◀── infrastructure
  AdminController      AdminUserService    Role enum     UserRepository
  AdminDashboardCtrl   AdminDashboardSvc                 (기존 재사용)
  (@PreAuthorize)      (도메인별 지표 조합)
                                                        각 도메인
                                                        Repository 재사용
                                                        (Timesale · Coupon · Chat · Search)

  SecurityConfig · JwtAuthFilter (기존 확장)
```

### 핵심 플로우

**1. 관리자 인증 · 롤 세팅 (Epic 1)**
```
Admin → POST /api/v1/auth/login {email, password}
      → AuthService.login (기존 M1)
      → user.role = ADMIN (or SUPER_ADMIN) — DB 조회 시
      → JwtUtil.generateToken(userId, role=ADMIN)
      ← 200 { accessToken, role: "ADMIN" }

Admin → GET /api/v1/admin/dashboard/overview
      → JwtAuthFilter — token 검증 · claim에서 role=ADMIN 추출
      → SecurityContext에 GrantedAuthority("ROLE_ADMIN") 세팅
      → @PreAuthorize("hasRole('ADMIN')") 통과
      → AdminDashboardController.overview
      ← 200 통합 카드
```

**2. 통합 대시보드 조회 (Epic 2)**
```
Admin → GET /admin/dashboard/overview
      → AdminDashboardService.overview
        → parallelize {
            todaySales = OrderRepository.sumSalesByDate(today)
            totalOrders = OrderRepository.countByDate(today)
            activeTimesales = TimeSaleEventRepository.countByStatus(OPEN)
            waitingCsCount = ChatRoomRepository.countByStatus(WAITING)
            weeklyCoupons = UserCouponRepository.countByIssuedAtAfter(weekAgo)
            popularTop5 = PopularSearchService.getTop("daily", 5)
        }
      ← DashboardOverviewResponse
```

### Out-of-Process 의존
- **RDS (MySQL) / H2 (dev)**: 각 도메인 Repository 재사용
- **Redis (Lettuce)**: 인기 검색어 ZSet 조회 (product-search-cache와 공유)
- **JWT**: 기존 M1 인증 확장

## 실패 모드 / 운영 관측 (Failure Modes & Observability)

### 실패 시나리오와 응답
| 시나리오 | ErrorCode | HTTP | 클라이언트 권장 동작 |
| --- | --- | --- | --- |
| 관리자 롤 없이 관리자 API 접근 | `C003` (ACCESS_DENIED) or `ADM001` | 403 | 로그아웃 · 재로그인 |
| SUPER_ADMIN 필요한 액션 (롤 변경) · ADMIN만 가진 사용자 | `ADM001` | 403 | 안내 |
| 롤 변경 대상 유저 없음 | `ADM003` | 404 | 목록 재조회 |
| 잘못된 롤 값 (INVALID_ROLE) | `ADM002` | 400 | 롤 재선택 |
| 대시보드 부분 실패 (한 지표 조회 실패) | (없음) | 200 | 그 지표만 null · 나머지 정상 반환 (graceful degrade) |

### 로깅 정책
- **항상 기록**: `requestId` · `userId` · `role` · `endpoint` · `duration_ms`
- **debug**: 대시보드 지표별 개별 조회 시간 (병목 감지)

### 관측 지표
- `admin.api.total{endpoint, role, result}` — counter (관리자 API 호출 빈도)
- `admin.dashboard.duration_seconds{card}` — histogram (통합 카드 개별 지표 로딩 시간)

## 롤아웃 / 마이그레이션 (Rollout)

### 전제
- Day 2 착수 · M1 인증 기반
- 관리자 계정 시드 필요 (dev/local)

### Product 의존성
- 선행: M1 auth (JWT · SecurityConfig)
- 후행: **모든 백오피스 이슈** (06/08/09/13은 관리자 롤 없이 진행 불가)
- 대시보드 후행: 이슈 05 (인기 검색어) · 이슈 06 (타임세일) · 이슈 08 (쿠폰) · 이슈 09 (CS)

### Epic·Story 의존성 그래프
```
Epic 1 (Admin 컨텍스트 · Role · JWT · SecurityConfig) ──► Epic 2 (Dashboard 통합·세부 API)
                                                            │
                                                            └─► (Epic 2 완주는 Week 3 Day 20)
```

### 환경별 설정 분기
| 항목 | dev (H2) | prod (RDS) |
| --- | --- | --- |
| 관리자 시드 | `AdminAccountInit` (SUPER_ADMIN 1 · ADMIN 2 하드코딩) | 초기 SUPER_ADMIN 1건 SQL 수동 |
| JWT 토큰 유효 시간 | 개발 편의 (12h) | 프로덕션 (1h + Refresh) |
| 대시보드 캐시 TTL | 없음 (실시간) | 30초 |

## 성공 지표 (KPI)
| 지표 | 목표 값 | 측정 방법 |
| --- | --- | --- |
| 관리자 API 인가 정확도 | **100%** (일반 사용자 접근 시 403) | 통합 테스트 |
| 대시보드 overview 응답 시간 P95 | ≤ **500ms** | Micrometer histogram |
| 대시보드 부분 실패 시 graceful degrade | **100%** (한 지표 실패해도 나머지 반환) | 통합 테스트 |
| 관리자 롤 변경 감사 로그 | 모든 롤 변경 이력 | `admin.api.total{endpoint=role_change} +1` per 변경 |

## Scope

**In Scope**:
- `nbc.c1oud_mall.admin.*` 컨텍스트
- Role enum 확장 (USER · ADMIN · SUPER_ADMIN)
- JWT claim role · JwtAuthFilter 확장
- SecurityConfig 관리자 경로 보호
- @PreAuthorize 어노테이션 사용
- `AdminController` (관리자 프로필 · 유저 목록 · 롤 변경)
- `AdminAccountInit` (dev 시드)
- `AdminDashboardController` (overview + 세부 6개 엔드포인트)
- ErrorCode `ADM001~003`

**Out of Scope**:
- 관리자 활동 로그 감사 테이블 — 사유: 초기 로그 파일로 충분 (v0.0.4v+)
- 세분화된 권한 (RBAC 매트릭스) — 사유: 초기 이원 롤로 충분 (v0.0.5v+)
- 관리자 초대 API — 사유: 초기 SQL 수동
- 2FA/MFA — 사유: 스코프 오버 (v0.0.5v+)
- Grafana 시각화 — 사유: 이슈 13에서 선택 (초기 API만)

## 대상 사용자
- **관리자 (ADMIN)** — 타임세일·쿠폰·CS 응대 · 대시보드 조회
- **슈퍼어드민 (SUPER_ADMIN)** — 관리자 계정 관리 · 롤 변경
- **일반 사용자 (USER)** — 관리자 API 접근 불가 (403)

## 연결된 Epic 목록
- [ ] Epic 1: Admin 컨텍스트 · Role 확장 · JWT · SecurityConfig
- [ ] Epic 2: Dashboard 통합 + 세부 API + Micrometer 지표

## 관련 문서
- 대응 이슈:
  - [issue-01-admin-context-and-role](../../../fix/brainstorming/version/0.0.3v/issue-01-admin-context-and-role.md)
  - [issue-13-admin-dashboard-observability](../../../fix/brainstorming/version/0.0.3v/issue-13-admin-dashboard-observability.md)
- 관련 ADR (발행 예정):
  - `ADR 012` — Ops Backend 정체성 재정의 (07-05 회의 결과)
- 재활용 조각 (아카이브):
  - `../archive/0.0.2v-crowdfunding/product-wallet.md` §Epic 4 — Micrometer 5개 지표 등록 방식 (대시보드 지표에 응용)
- 관련 Product: `./product-timesale-concurrency.md` · `./product-search-cache.md` · `./product-cs-chat.md` (모두 대시보드 지표 원천)
- 관련 milestone: `../../../milestones/version/0.0.3v/milestone.md`

## 열린 질문 (Open Questions)
- **Q1**: 관리자 활동 감사 로그 별도 테이블 필요 시점? (초기 로그 파일 · v0.0.4v+ 검토)
- **Q2**: 대시보드 실시간 스트리밍 (WebSocket push) 필요? (초기 폴링 30초 · v0.0.5v+ 검토)
- **Q3**: SUPER_ADMIN 액션 확장 범위 (초기: 롤 변경만 · v0.0.4v+ 이벤트 강제 종료 등)?
- **Q4**: Grafana 대시보드 실제 세팅 시점 (초기 API만 노출 · Grafana docker-compose는 이슈 13 선택)

## 제품 수준 완료 기준 (Product-level DoD)
- [ ] Epic 1·2 완료
- [ ] ADR 012 발행
- [ ] 관리자 계정 시드 dev 자동 · prod 매뉴얼
- [ ] 대시보드 overview + 세부 6개 엔드포인트 노출
- [ ] `.claude/rules/*` 갱신 없음 (기존 규범 준수)

---

# [Epic 1] Admin 컨텍스트 · Role 확장 · JWT · SecurityConfig

## 목표
`nbc.c1oud_mall.admin.*` 컨텍스트를 신설하고 Role enum 확장 · JWT claim · SecurityConfig 관리자 경로 보호를 완성해 이후 백오피스 이슈들의 전제를 마련한다.

## 배경
Day 2 첫 착수. 모든 백오피스 이슈의 인가 전제.

## 포함 Story
- Story 1-1: `nbc.c1oud_mall.admin.*` 골격 + `Role` enum 확장 + `User` 엔티티 role 필드
- Story 1-2: JwtUtil / JwtAuthFilter 확장 (claim role 삽입·추출)
- Story 1-3: SecurityConfig `@PreAuthorize` 활성화 · 관리자 경로 보호
- Story 1-4: `AdminController` 골격 + `AdminAccountInit` 시드 + ErrorCode ADM001~003

## Epic 인수 시나리오
- Given SUPER_ADMIN 시드 계정
- When `POST /auth/login` 후 `GET /admin/me`
- Then 200 · 프로필 반환

- Given 일반 USER
- When 위 요청
- Then 403 · `ADM001` (또는 `C003`)

- Given ADMIN
- When SUPER_ADMIN 전용 `PATCH /admin/users/{id}/role`
- Then 403 · `ADM001`

## Epic 완료 기준 (DoD)
- [ ] 4개 Story 완료
- [ ] ErrorCode `ADM001~003` 등록
- [ ] 통합 테스트 (역할별 접근)

## [Story 1-1] Admin 컨텍스트 골격 · Role enum 확장

### User Story
- As a 개발자
- I want admin 컨텍스트 골격과 Role enum 확장을 완성해서
- so that 이후 관리자 인증·인가·시드가 이 기반 위에서 진행된다

### 설명
- 신규 패키지 `nbc.c1oud_mall.admin.presentation` · `application` · `domain` · `infrastructure`
- `Role` enum 확장: `USER` (기존) · `ADMIN` · `SUPER_ADMIN`
- 기존 `User` 엔티티에 `role` 필드 확인/추가 (M1 상태 재확인)

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.auth.domain.Role` (기존 · enum 값 추가)
- `nbc.c1oud_mall.admin.presentation.AdminController` (신규)
- `nbc.c1oud_mall.admin.application.AdminUserService` (신규)

### 완료 기준 (AC)
- Given `Role.values()` / When 호출 / Then `[USER, ADMIN, SUPER_ADMIN]`
- Given DB에 `role='ADMIN'` 저장 / When JPA 조회 / Then `user.getRole() == Role.ADMIN`

### Definition of Done
- [ ] 구현: `admin` 컨텍스트 4레이어 폴더 · `Role` 확장
- [ ] 단위 테스트 (Role enum 값 검증)
- [ ] 기존 M1 auth 회귀 확인

### 스토리 포인트
0.5d

### 의존성
- 후행: Story 1-2·1-3·1-4

## [Story 1-2] JwtUtil / JwtAuthFilter 확장

목록:
- `JwtUtil.generateToken(userId, role)` — role claim 삽입 (`Jwts.builder().claim("role", role.name())`)
- `JwtAuthFilter` — token 파싱 후 `role = claims.get("role", String.class)` 추출
- `SecurityContext`에 `GrantedAuthority("ROLE_" + role)` 세팅

**SP**: 0.5d

## [Story 1-3] SecurityConfig `@PreAuthorize` 활성화

목록:
- `@EnableMethodSecurity(prePostEnabled=true)` (기존 확인 · 없으면 추가)
- SecurityConfig에 `/api/v1/admin/**` 경로 필터체인
- 각 관리자 컨트롤러 메서드에 `@PreAuthorize("hasRole('ADMIN')")` or `hasRole('SUPER_ADMIN')`

**SP**: 0.5d

## [Story 1-4] AdminController + AdminAccountInit + ErrorCode

목록:
- `AdminController`:
  - `GET /api/v1/admin/me` (ADMIN)
  - `GET /api/v1/admin/users?page=&size=` (ADMIN)
  - `PATCH /api/v1/admin/users/{id}/role` (SUPER_ADMIN)
- `AdminAccountInit` `@Component @Profile("dev | local")` · ApplicationRunner 시드 (SUPER_ADMIN 1건 · ADMIN 2건)
- ErrorCode `ADM001~003`

**SP**: 1d

---

# [Epic 2] Dashboard 통합 + 세부 API + Micrometer 지표

## 목표
관리자 대시보드의 통합 카드 6개와 세부 6개 엔드포인트를 완성하고, Micrometer 지표를 노출해 (선택) Grafana 대시보드로 시각화할 기반을 만든다.

## 배경
백오피스 정체성의 시각적 무대. README 스크린샷·시연에 결정적.

## 포함 Story
- Story 2-1: `AdminDashboardController.overview` + `DashboardOverviewResponse` (6 카드 통합)
- Story 2-2: 세부 API (popular-search · cs-stats · timesale-stats · coupon-usage · low-stock)
- Story 2-3: Micrometer 커스텀 지표 등록 (5~10종)
- Story 2-4: (선택) docker-compose Prometheus + Grafana · Dashboard JSON export

## Epic 인수 시나리오
- Given ADMIN 인증 · Redis · DB 활성
- When `GET /api/v1/admin/dashboard/overview`
- Then 200 · 6개 카드 각 값 반환 (한 지표 실패 시 null · 나머지 정상)

- Given ADMIN
- When `GET /api/v1/admin/dashboard/popular-search?scope=daily`
- Then Top 10 반환 (Redis ZSet 조회)

## Epic 완료 기준 (DoD)
- [ ] 4개 Story 완료 (Story 2-4는 선택)
- [ ] 통합 테스트 (6 카드 반환 · 부분 실패 graceful degrade)
- [ ] Micrometer 지표 `/actuator/prometheus`에 노출

## [Story 2-1] AdminDashboardController.overview

목록:
- `GET /api/v1/admin/dashboard/overview`
- `AdminDashboardService.overview()`:
  - Order · TimeSaleEvent · UserCoupon · ChatRoom · PopularSearch 각 Repository 조회
  - CompletableFuture 병렬 or 순차 (초기 순차 · 병렬은 성능 관찰 후)
  - 각 지표 실패 시 null · try-catch로 격리
- `DashboardOverviewResponse` record: todaySales · totalOrders · activeTimesales · waitingCsCount · weeklyCoupons · popularTop5

**SP**: 1d

## [Story 2-2] 세부 API 5~6개

목록:
- `GET /admin/dashboard/popular-search?scope=&limit=` — 이슈 05 재사용
- `GET /admin/dashboard/cs-stats` — 이슈 09 상태별 카운트
- `GET /admin/dashboard/timesale-stats?eventId=` — 이슈 06 이벤트 판매
- `GET /admin/dashboard/coupon-usage` — 이슈 08 발급/사용률
- `GET /admin/dashboard/low-stock?threshold=10` — 재고 임박 상품

**SP**: 1d

## [Story 2-3] Micrometer 커스텀 지표

목록:
- `MeterRegistry` 주입 · 각 도메인 서비스에서 counter·histogram 등록
- `chat.message.total{sender_role, room_status}`
- `timesale.purchase.total{strategy, result}` (Epic 2 product-timesale-concurrency 재사용)
- `coupon.issue.total{result}` (재사용)
- `search.popular.record.total{result}` (재사용)
- `admin.dashboard.duration_seconds{card}` (신규)

**SP**: 0.5d

## [Story 2-4] (선택) Prometheus + Grafana docker-compose

목록:
- `docker-compose.yml`에 `prometheus` · `grafana` 서비스 추가
- Prometheus scrape config: Spring Boot Actuator `/actuator/prometheus`
- Grafana 대시보드 JSON export (`.github/assets/grafana-dashboard.json`)
- README에 스크린샷 삽입

**SP**: 1d (선택 · 여유 시)
