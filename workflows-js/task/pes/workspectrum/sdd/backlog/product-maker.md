# [Product 10] 메이커 프로필 (Maker Profile · 신뢰 지표 · 응답 지표 · 온라인 상태)

## Product Vision
> 프로젝트 상세·채팅에서 후원자가 후원 결정 전에 확인하는 메이커의 신뢰 지표를 종합 관리한다. User 도메인의 1:1 확장으로 **인증 마크 · 자기소개 · 응답률 · 응답 시간 · 온라인 상태 · 진행 프로젝트 수 · 팔로워 수**를 제공하고, 응답률/응답시간은 Chat(Product 9) 이력을 원천으로 하는 일간 배치로 자동 계산하며, 온라인 상태는 STOMP CONNECT/DISCONNECT 이벤트로 실시간 반영한다.

## 배경 및 문제
- 현재 상황 (As-Is)
  - c1oud-mall User 도메인은 인증·기본 프로필(이름·이메일·전화)만 관리 · 크라우드 펀딩 신뢰 지표 부재
  - Project (Product 6)에 `makerUserId` FK는 있으나 메이커의 신뢰 지표 조회는 매번 별도 계산 필요
  - Chat (Product 9)이 완결됐으나 **응답률·응답시간의 원천 데이터로 활용되는 소비자 없음** · 이력은 저장만 됨
  - 디자인(`프로젝트 상세.html`) 확인: 메이커 aside 카드 (진행 3 · 팔로워 1.2k · 응답률 98%)
  - 디자인(`메이커 채팅.html`) 확인: thread-head 메이커 정보 (verified 마크 · "보통 1시간 내 응답" · 온라인 상태)
- 발생하는 문제
  - 후원자가 후원 결정 시 메이커 신뢰도 판단 불가 → 후원 이탈률 상승
  - 메이커 프로필 요청마다 여러 도메인(Project · Follow · Chat) 개별 쿼리 → 프로젝트 상세 페이지 응답 시간 저하
  - 온라인 상태 관측 없으면 "지금 문의하면 즉시 답 받을 수 있는지" UX 판단 불가
  - Product 3 (기존 · 폐기 예정) · Product 15 (Follow · 후속) 등이 메이커 정보 참조 시 반복 조회 부담
- 왜 지금 해결해야 하는가
  - Chat(Product 9) 완결로 응답률 계산 원천 확보 · 지금이 응답 지표 배치 도입의 최적 시점
  - Project(Product 6) 반정규화 필드와 유사한 패턴으로 확장 · 반정규화 규범 통일
  - Follow(이슈 #03 · Product 15) SDD 진입 전 메이커 프로필 완결이 자연스러움 (팔로워 수 반정규화 갱신 대상)
  - 초기 트래픽 없어 배치 부하 검증 여유

## 목표 (To-Be)
- 신규 서브패키지: `nbc.c1oud_mall.auth.maker.*` (User 도메인 안 서브 · 이슈 #11 결정)
- `MakerProfile` 엔티티 (`@Table(name = "maker_profile")` · User 1:1 확장)
- 필드 9개: `user_id (PK, FK)` · `bio (500자)` · `location (100자)` · `verified · verified_at` · `response_rate (DECIMAL 4,2)` · `avg_response_seconds` · `is_online` · `last_seen_at` · `active_project_count`
- 도메인 메서드 5개: `markOnline()` · `markOffline()` · `updateResponseStats(rate, avgSeconds)` · `incrementActiveProject()` · `decrementActiveProject()` + `responseTimeText()` 뷰 메서드
- **응답률·응답시간 일간 배치** (`MakerResponseStatsScheduler` · 매일 새벽 3시 · 최근 90일 채팅 이력)
- **온라인 상태 실시간** (STOMP CONNECT/DISCONNECT 이벤트 훅 · `MakerOnlineListener`)
- REST 4개 엔드포인트: 프로필 조회 · 내 프로필 수정 · 관리자 인증 · 온라인 메이커 목록
- `ErrorCode.MKR001~002` 등록
- 관측 지표 4종
- User 생성 시 `MakerProfile` 자동 초기화 (User 도메인 훅)

## 설계 결정 (Design Decisions)

- **User 도메인 안 서브패키지 `auth.maker.*` 배치** (신규 별도 컨텍스트 X · 이슈 #11 확정)
  - User와 항상 함께 조회되는 성격 · 완전 분리 시 오버헤드
  - 서브패키지로 응집도 유지 · Maker 관련 도메인·서비스·배치 모두 여기 집중
- **`MakerProfile`은 User와 1:1 확장 테이블**
  - `maker_profile.user_id`가 PK이자 User FK (동일 값)
  - User 생성 시 자동 초기화 (Lazy 초기화가 아닌 Eager · 이유: 아래 R5 참조)
- **응답률·응답시간 = 배치 계산 (일간 · 최근 90일)** (이슈 #11 Option A)
  - 매일 새벽 3시 · 최근 90일 채팅 메시지에서 "후원자→메이커 시작 메시지" · "메이커 응답 메시지" 페어 계산
  - **응답률** = 응답한 메시지 수 / 전체 후원자 시작 메시지 수 (24시간 이내 응답 기준)
  - **평균 응답 시간** = 각 페어의 응답 지연 초 평균 (24시간 초과는 계산에서 제외 · outlier)
  - 실시간 갱신은 v0.0.5+ 부하 관측 후 결정
- **온라인 상태 = STOMP CONNECT/DISCONNECT 이벤트 훅**
  - `SessionConnectEvent` · `SessionDisconnectEvent` 리스너
  - `is_online = true/false` + `last_seen_at` 갱신
  - Chat(Product 9)의 `StompAuthInterceptor` 정합 · 인증된 사용자만 훅 발동
- **`active_project_count` 반정규화** — 진행 중 프로젝트 수 (LIVE · SUCCESSFUL · FUNDED 상태)
  - Project (Product 6) 상태 전이 시 `MakerProfile` 원자적 갱신
  - Project.launch → `incrementActiveProject()` · Project.markFunded/complete/cancel → `decrementActiveProject()`
- **팔로워 수는 반정규화하지 않음** (초기 실시간 COUNT)
  - Follow (Product 15 · 후속) SDD 스코프 · v0.0.4+ 반정규화 검토 (팔로워 1000+ 사용자 발생 시)
- **인증(`verified`)은 관리자 승인** — 초기 SQL 수동 · v0.0.5+ 관리자 API
  - 초기 자동 등록(Project R2 결정)에서는 인증 미제공 · 신뢰 신호를 시간이 지나 부여
- **락 정책은 낙관 락 or 없음** — 배치 갱신·상태 갱신은 동시성 부담 낮음
  - Wallet/Project/Reward/Pledge 락 순서 규범 밖 · 별도 락 자원 등록 안 함
  - 배치 갱신 실패 시 다음 배치에서 회복

## 대안 검토 (Alternatives Considered)

### 도메인 배치
**Option A — User 도메인 안 서브패키지 `auth.maker.*` (선택)**
- 비용: User 패키지 파일 수 증가
- 보상: 응집도 · User 생성 시 훅 · 조회 시 함께 로드

**Option B — 별도 Aggregate `maker.*`**
- 거부 이유: User와 항상 함께 조회 · 분리 오버헤드 · 이슈 #11 검토 반영

### 응답률 계산 방식
**Option A — 배치 (일간 · 최근 90일) (선택)**
- 부하 낮음 · 신입 스코프 · 대략적 지표로 충분

**Option B — 실시간 (메시지 저장 시 즉시 갱신)**
- 거부 이유: 부하 큼 · 락 부담 · 초기 스코프 오버

**Option C — 실시간 이벤트 기반 (AFTER_COMMIT 이벤트)**
- v0.0.5+ 검토 · Kafka 등 이벤트 인프라 도입 시 · 초기엔 배치

### 온라인 상태
**Option A — STOMP 이벤트로 갱신 (선택)**
- Chat 인프라 재사용 · 별도 heartbeat 불필요

**Option B — HTTP heartbeat (5초 주기)**
- 거부 이유: 채팅 STOMP 있어 재사용이 자연 · 추가 API 부담

**Option C — Redis TTL 활용 (WS 세션 = Redis 키)**
- 거부 이유: 다중 인스턴스 · Redis 도입 시점 (v0.0.5+) 검토

### `active_project_count` 반정규화
**Option A — 반정규화 컬럼 (선택)**
- 조회 성능 · Project 프로필 카드 즉시 노출

**Option B — 실시간 COUNT (Project 조회 시 `COUNT(*)` )**
- 거부 이유: 프로젝트 상세 매 조회마다 부담

## 전체 아키텍처 (High-Level Architecture)

### 컴포넌트 배치
```
presentation ──▶ application ──▶ domain ◀── infrastructure
MakerProfileController  MakerProfileService     MakerProfile         MakerProfileRepository
- getProfile            - getProfile            (Entity · 1:1 User)  - findByUserId
- updateMyProfile       - updateMyProfile       - markOnline()       MakerResponseStatsScheduler
- verify (admin)        - verify                - markOffline()      - runDaily()
- listOnline            - listOnline            - updateResponseStats()  - computeStats(makerId)
                                                - incrementActiveProject()  MakerOnlineListener
                                                - decrementActiveProject()  - @EventListener CONNECT
                                                - responseTimeText()        - @EventListener DISCONNECT

External refs (참조만):
- Chat (Product 9) — chat_message 이력 · 응답 지표 계산 원천
- Project (Product 6) — active_project_count 갱신 트리거 (Project.launch/markFunded 등)
- User (기존 auth 도메인) — 1:1 확장 · User 생성 시 자동 초기화
- Follow (Product 15 후속) — 팔로워 수 조회 (실시간 COUNT · 향후 반정규화 검토)
```

### 핵심 플로우
**1. User 생성 시 자동 초기화**
```
AuthService.signup (기존)
  ├── User 저장
  └── MakerProfileService.initialize(userId)
       └── MakerProfile(userId, defaults) 저장
```

**2. STOMP CONNECT 시 온라인 갱신**
```
Client STOMP CONNECT `/ws/chat` (Product 9)
  → StompAuthInterceptor.preSend (Product 9 Epic 1)
    → 인증 성공 → Principal 세팅
  → SessionConnectEvent 발생
    → MakerOnlineListener.onConnect(event)
      → MakerProfileService.markOnline(userId)
        └── profile.markOnline() (is_online=true · last_seen_at=now)
```

**3. STOMP DISCONNECT 시 오프라인 갱신**
```
Client DISCONNECT (or 세션 종료)
  → SessionDisconnectEvent
    → MakerOnlineListener.onDisconnect(event)
      → MakerProfileService.markOffline(userId)
```

**4. 응답률·응답시간 배치 (매일 새벽 3시)**
```
@Scheduled(cron = "0 0 3 * * *")
MakerResponseStatsScheduler.runDaily()
  ├── since = now - 90 days
  ├── For each active maker (findActiveMakerIds):
  │     stats = computeStats(makerId, since)
  │     ├── SQL: 후원자→메이커 시작 메시지 조회 · 각 메시지의 메이커 응답 검색 · 페어링
  │     ├── responseRate = 24시간 내 응답한 페어 수 / 전체 페어 수
  │     └── avgResponseSeconds = 각 페어 응답 지연 초 평균 (24h 초과 제외)
  │     profile.updateResponseStats(rate, avgSeconds)
  └── 지표: maker.stats.batch.duration + counter (processed · updated · failed)
```

**5. `active_project_count` 갱신 (Project 상태 전이)**
```
ProjectService.launch (Product 6 · Epic 4 Story 4-2)
  ├── project.launch()  (DRAFT → LIVE)
  └── (부수효과 · REQUIRED TX 참여)
      MakerProfileService.incrementActiveProject(makerId)
        └── profile.incrementActiveProject() (active_project_count++)

ProjectService.markFunded (or cancel) 등 종단 상태 전이 시
  └── MakerProfileService.decrementActiveProject(makerId)
```

### Out-of-Process 의존
- **RDS (MySQL)** — `maker_profile` 테이블 · 인덱스
- **Chat 이력 조회 SQL** — Product 9의 `chat_message` 테이블 · 서브패키지 접근

## 실패 모드 / 운영 관측 (Failure Modes & Observability)

### 실패 시나리오와 응답
| 시나리오 | ErrorCode | HTTP | 클라이언트 권장 동작 |
| --- | --- | --- | --- |
| 프로필 없음 | `MKR001` MAKER_PROFILE_NOT_FOUND | 404 | User 생성 확인 · 자동 초기화 트리거 |
| 이미 인증됨 (재요청 시) | `MKR002` MAKER_ALREADY_VERIFIED | 409 | 상태 확인 |
| 관리자 아님 (인증 API 호출 시) | `C003` ACCESS_DENIED | 403 | 접근 거부 |
| 배치 개별 메이커 계산 실패 | (내부 로그) | - | 로그 마커 · 다음 배치에서 회복 |
| STOMP 이벤트 처리 실패 (온라인 갱신) | (내부 로그) | - | 다음 CONNECT 시 회복 · 사용자 UX 영향 미미 |
| Project 상태 전이 부수효과 실패 (`incrementActiveProject`) | (내부 로그 · 상위 TX 참여) | - | 상위 Project TX 롤백 · 회귀 |

### 로깅 정책
- **항상 기록**:
  - `requestId` · `userId` (`makerId`) · 액션(profile 조회/수정/verify/online/offline)
  - 배치: `MAKER_STATS_BATCH_START/END userId={} rate={} avgSeconds={}`
- **debug**: 배치 개별 메이커 처리 상세 (페어 수 · 계산 값)
- **절대 금지**:
  - `bio` 원문 (개인정보 포함 가능)
  - 채팅 메시지 원문 (배치 계산 시)
- **특수 마커**:
  - `MAKER_STATS_UPDATE_FAILED userId={} reason={}` — 개별 실패
  - `MAKER_ONLINE_EVENT_FAILED userId={} event={CONNECT|DISCONNECT}` — STOMP 훅 실패

### 관측 지표
- `maker.online.gauge` — gauge — 현재 온라인 메이커 수 (실시간 조회)
- `maker.response_rate.avg` — gauge — 배치 후 전체 메이커 평균 응답률 (분석용)
- `maker.stats_batch.duration_seconds` — histogram — 배치 처리 시간
- `maker.stats_batch.total{result=success|partial_fail|full_fail}` — counter — 배치 결과 분류

## 롤아웃 / 마이그레이션 (Rollout)

### 전제
- Chat (Product 9) 완결 · 채팅 이력 조회 SQL 가능
- Project (Product 6) 완결 · `MakerProfile.incrementActiveProject` 호출 지점 존재
- 초기 사용자 없음 · 신규 테이블만 · JPA ddl-auto 반영
- 기존 User는 마이그레이션 배치로 `MakerProfile` 자동 초기화

### Product 의존성
- **선행**: **Chat (9)** — 채팅 이력 조회 필수 · **Project (6)** — `active_project_count` 반정규화 갱신 트리거
- **후행**: **Follow (Product 15)** — 팔로워 수 조회 진입점 · **Notification (v0.0.5+)** — 오프라인 메시지 알림
- **동시 대응**: User 도메인의 회원가입 흐름에 `MakerProfileService.initialize` 훅 추가

### Epic·Story 의존성 그래프
```
Epic 1 (엔티티·도메인 메서드) ──► Epic 2 (배치 스케줄러)
                                     ├─► Epic 3 (온라인·반정규화 훅)
                                     └─► Epic 4 (REST + 관측 + ADR)
```

### 환경별 설정 분기
| 항목 | dev (H2) | prod (RDS MySQL) |
| --- | --- | --- |
| 테이블·인덱스 | JPA ddl-auto | 동일 |
| 배치 cron | 5분 (테스트 편의) | 매일 03:00 |
| Chat 이력 조회 SQL | H2 MySQL 호환 모드 | 완전 지원 |
| STOMP 이벤트 리스너 | 활성 | 활성 |
| DummyDataInit | 3~5명 메이커에 랜덤 응답률·응답시간 | 활성 (초기 UX) |

## 성공 지표 (KPI)
| 지표 | 목표 값 | 측정 방법 |
| --- | --- | --- |
| 배치 처리 시간 P95 (활성 메이커 100명 · 채팅 이력 10만) | ≤ 5분 | `maker.stats_batch.duration_seconds` P95 |
| 온라인 상태 정확도 (STOMP 세션 = is_online) | 100% | 통합 테스트 · Grafana 상관 관측 |
| `active_project_count` 반정규화 정합성 (SUM(project.status IN LIVE/SUCCESSFUL/FUNDED)) | 100% | 주간 검증 배치 |
| 프로필 조회 응답 시간 P95 | ≤ 100ms | 로그 · 지표 |
| 응답률 계산 정확성 (배치 vs 수동 SQL 검증) | 100% | 통합 테스트 · 프로퍼티 기반 |

## Scope
**In Scope**:
- 서브패키지 `nbc.c1oud_mall.auth.maker.*` (4레이어)
- `MakerProfile` 엔티티 · 1:1 확장 테이블
- 5개 도메인 메서드 + `responseTimeText` 뷰
- `MakerProfileService` — 조회 · 수정 · 인증 · 온라인/오프라인 · 프로젝트 카운트 조정
- `MakerResponseStatsScheduler` — 일간 배치 · 최근 90일 · 24시간 outlier 제외
- `MakerOnlineListener` — STOMP CONNECT/DISCONNECT 훅
- Project 상태 전이 부수효과: `active_project_count` 원자적 갱신 (Product 6 확장)
- REST 4개 엔드포인트
- `ErrorCode.MKR001~002`
- 관측 지표 4종
- User 생성 시 `MakerProfile` 자동 초기화 (`AuthService.signup` 확장)
- 기존 User 마이그레이션 배치 (초기 사용자 없어 단순)

**Out of Scope**:
- **팔로워 수 반정규화** — 초기 실시간 COUNT · Follow (Product 15) 스코프에 이관 · v0.0.4+ 재검토
- **관리자 인증 API 실 사용** — 초기 SQL 수동 · v0.0.5+ 별도 관리자 도메인
- **실시간 응답 지표 갱신** — 초기 배치만 · v0.0.5+ 이벤트 기반 검토
- **프로필 이미지(아바타)** — Media (Product 11) 스코프
- **다중 언어 프로필** — 한국어만
- **메이커 통계 대시보드** — 별도 UI · v0.0.5+
- **메이커 랭킹** — 후원 총액·프로젝트 성공률 등 · v0.0.5+
- **차단·신고 워크플로우** — v0.0.5+
- **Redis 활용 온라인 상태** — 다중 인스턴스 배포 시 · v0.0.5+

## 대상 사용자
- **후원자 (Backer)** — 프로젝트 상세·채팅에서 메이커 신뢰 지표 확인 후 후원 결정
- **메이커 (Maker)** — 자기 프로필 수정 (bio · location) · 인증 마크 신청
- **관리자** — 메이커 인증 승인 (초기 SQL · v0.0.5+ 관리자 API)
- **운영자** — 배치 실패 대응 · 온라인 상태 이상 대응
- **후속 SDD 작성자** — Follow · Notification · Discovery 등에서 참조

## 연결된 Epic 목록
- [ ] Epic 1: `MakerProfile` 엔티티 + 5개 도메인 메서드 + Repository + `ErrorCode.MKR001~002`
- [ ] Epic 2: `MakerResponseStatsScheduler` (일간 배치 · Chat 이력 기반) + 응답 지표 계산 SQL
- [ ] Epic 3: `MakerOnlineListener` (STOMP 훅) + Project 상태 전이 부수효과 (`incrementActiveProject`) 통합
- [ ] Epic 4: REST 4개 엔드포인트 + 4개 관측 지표 + ADR + `backend-boundary/error-codes.md` 갱신

## 관련 문서
- **원본 이슈**: `workflows/task/fix/brainstorming/version/0.0.2v/issue-11-maker-profile-response-stats.md`
- **선행 SDD**: `product-project.md` (Product 6) · `product-chat.md` (Product 9)
- **후행 SDD**:
  - `product-follow.md` (Product 15 · 팔로워 수 조회 진입점)
  - `product-notification.md` (v0.0.5+ · 오프라인 메시지 알림)
- **관련 이슈**:
  - `issue-01-websocket-chat.md` · `issue-12-chat-rich-message.md` — 온라인 상태 원천 (STOMP 이벤트)
  - `issue-03-follow.md` — 팔로워 수
  - `issue-08-project-domain.md` — Project.makerUserId 참조 · `active_project_count` 갱신 트리거
- **디자인 참조**:
  - `프로젝트 상세.html` — 메이커 aside 카드 (진행 3 · 팔로워 1.2k · 응답률 98%)
  - `메이커 채팅.html` — thread-head 인증 마크 · 온라인 · 응답 시간
- **벤치마크**:
  - 카카오 크리에이터 — 평균 응답시간 자동 계산
  - Intercom · Slack — 온라인 상태 실시간
- **신규 ADR 후보**:
  - "MakerProfile User 1:1 확장 · 서브패키지 배치"
  - "응답률·응답시간 배치 계산 방식 (Chat 이력 원천 · 24h outlier 제외)"
  - "STOMP CONNECT/DISCONNECT 온라인 상태 훅 · 다중 인스턴스 로드맵"
- **규범 갱신 예정**:
  - `workflows/backend-boundary/error-codes.md` MKR001~002 매핑
  - Product 6 (`product-project.md`) Epic 4 Story 4-2 (launch) · Epic 3 (반정규화) — `MakerProfile` 갱신 부수효과 추가

## 열린 질문 (Open Questions)
- **`response_rate=null` 초기값 처리** — 이력 없는 신규 메이커는 응답률 0.00 or null? · 초기: null · FE에서 "-" 표시
- **응답 시간 outlier 임계값** — 24시간 초과 응답은 계산 제외 · 조정 가능성 (48시간 등)
- **배치 실패 시 재실행 정책** — 실패 메이커만 재실행 or 전체 · 초기: 다음 배치에서 회복 · v0.0.5+ 재시도 큐
- **다중 인스턴스 온라인 상태** — 단일 인스턴스 전제 · v0.0.5+ Redis Set 활용 or 인스턴스별 상태 병합
- **팔로워 수 반정규화 도입 시점** — 팔로워 1000+ 사용자 발생 시 · Follow (Product 15) 스코프에서 결정
- **`verified` 인증 기준** — 이메일 인증? · 사업자등록? · 신원 확인? · v0.0.5+ 정책 결정
- **디자인 아바타 렌더링** — 초기 이니셜 아바타 (이름 첫 글자 · 그라디언트 배경) · Media (Product 11) 도입 후 사진 지원

## 제품 수준 완료 기준 (Product-level DoD)
- [ ] 모든 Epic DoD 통과
- [ ] E2E 시나리오 1: User 가입 → `MakerProfile` 자동 초기화 → 프로필 조회 정상
- [ ] E2E 시나리오 2: STOMP CONNECT → `is_online=true` · DISCONNECT → `is_online=false`
- [ ] E2E 시나리오 3: 더미 채팅 이력 100건 → 배치 실행 → 응답률·응답시간 정확 계산
- [ ] E2E 시나리오 4: Project.launch → `MakerProfile.active_project_count += 1`
- [ ] 다중 세션 온라인 상태 테스트 (같은 사용자 여러 브라우저 세션 · CONNECT 2회 · DISCONNECT 1회 → 여전히 online)
- [ ] `active_project_count` SSOT 검증 (`COUNT(project.status IN LIVE/SUCCESSFUL/FUNDED where maker_user_id=X) == profile.active_project_count`) 100%
- [ ] ADR 최소 2건 발행
- [ ] `backend-boundary/error-codes.md` MKR001~002 매핑
- [ ] 관측 지표 4개 프로덕션 노출 · Chat Product 9의 `chat.ws.connection.gauge`와 일관

---

# [Epic 1] `MakerProfile` 엔티티 + 5개 도메인 메서드 + Repository + `ErrorCode.MKR001~002`

## 목표
`MakerProfile` 엔티티(User 1:1 확장) · 5개 도메인 메서드 · Repository · 2개 ErrorCode를 구축하여 후속 Epic이 배치·훅·REST를 확장할 수 있는 기반을 마련한다.

## 배경
- User 도메인의 자연 확장 · 서브패키지 배치
- 배치·이벤트 훅의 진입점 되는 도메인 메서드 사전 확정
- 조회 성능 위해 반정규화 필드 포함

## 포함 Story
- Story 1-1: `MakerProfile` 엔티티 + `@Table(name = "maker_profile")` + User FK
- Story 1-2: 5개 도메인 메서드 (`markOnline · markOffline · updateResponseStats · incrementActiveProject · decrementActiveProject`) + `responseTimeText` 뷰
- Story 1-3: `MakerProfileRepository` + User 생성 시 자동 초기화 훅 + `ErrorCode.MKR001~002`

## Epic 완료 기준 (DoD)
- [ ] 3개 Story 완료
- [ ] 5개 도메인 메서드 단위 테스트 (성공·엣지·예외)
- [ ] User 생성 시 `MakerProfile` 자동 초기화 검증
- [ ] `ErrorCode` 등록

---

## [Story 1-1] `MakerProfile` 엔티티

### User Story
- As a Maker Profile 도메인 개발자
- I want User와 1:1 확장 `MakerProfile` 엔티티
- so that 신뢰 지표 · 온라인 상태 · 반정규화 필드 통합 관리

### 설명
- 위치: `nbc.c1oud_mall.auth.maker.domain.MakerProfile`
- 필드 9개 (§목표 참조)
- `@Id` = `user_id` (PK · User FK와 동일 값)
- JPA Auditing (`BaseEntity` 상속) — `createdAt · updatedAt`

**핵심 파일**:
- `nbc.c1oud_mall.auth.maker.domain.MakerProfile`

**주요 스키마**:
```sql
CREATE TABLE maker_profile (
  user_id                 BIGINT       NOT NULL,
  bio                     VARCHAR(500) NULL,
  location                VARCHAR(100) NULL,
  verified                BOOLEAN      NOT NULL DEFAULT FALSE,
  verified_at             TIMESTAMP    NULL,
  response_rate           DECIMAL(4,2) NULL,           -- 0.00 ~ 100.00 · null 허용 (초기값)
  avg_response_seconds    INT          NULL,
  is_online               BOOLEAN      NOT NULL DEFAULT FALSE,
  last_seen_at            TIMESTAMP    NULL,
  active_project_count    INT          NOT NULL DEFAULT 0,
  created_at              TIMESTAMP    NOT NULL,
  updated_at              TIMESTAMP    NOT NULL,
  PRIMARY KEY (user_id),
  KEY idx_maker_verified (verified),
  KEY idx_maker_online (is_online, last_seen_at DESC),
  KEY idx_maker_active_projects (active_project_count DESC)
);
```

**엔티티**:
```java
@Entity
@Table(name = "maker_profile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class MakerProfile extends BaseEntity {
    @Id
    private Long userId;   // User FK · PK

    @Column(length = 500)
    private String bio;

    @Column(length = 100)
    private String location;

    @Column(nullable = false)
    private boolean verified;

    @Column
    private LocalDateTime verifiedAt;

    @Column(precision = 4, scale = 2)
    private BigDecimal responseRate;

    @Column
    private Integer avgResponseSeconds;

    @Column(nullable = false)
    private boolean isOnline;

    @Column
    private LocalDateTime lastSeenAt;

    @Column(nullable = false)
    private int activeProjectCount;

    public static MakerProfile initialize(Long userId) {
        MakerProfile p = new MakerProfile();
        p.userId = userId;
        p.verified = false;
        p.isOnline = false;
        p.activeProjectCount = 0;
        return p;
    }
}
```

### 완료 기준 (AC)
- Given `MakerProfile.initialize(userId=1)` · When 저장 · Then `is_online=false · verified=false · active_project_count=0`
- Given `response_rate=null` (초기값) · When 조회 · Then null 반환 (FE에서 "-" 표시)
- Given `verified=false` 저장 후 `verified_at=null` · When 저장 · Then constraint 위반 없음

### Definition of Done
- [ ] `MakerProfile` 엔티티
- [ ] DDL 확인 (JPA · dev H2)
- [ ] 단위 테스트: 초기화 · 저장·조회

### 스토리 포인트
0.5d

### 의존성
- 선행: 없음
- 후행: Story 1-2 · 1-3

---

## [Story 1-2] 5개 도메인 메서드 + `responseTimeText` 뷰

### User Story
- As a Maker Profile 도메인 개발자
- I want 5개 도메인 메서드로 상태·통계 원자적 갱신
- so that Service·배치·훅이 안전하게 값 조정

### 설명
- 5개 메서드:
  - `markOnline()` — `is_online=true · last_seen_at=now`
  - `markOffline()` — `is_online=false · last_seen_at=now`
  - `updateResponseStats(BigDecimal rate, Integer avgSeconds)` — 배치 결과 반영
  - `incrementActiveProject()` — `active_project_count++`
  - `decrementActiveProject()` — `active_project_count--` (언더플로우 방지)
- 뷰 메서드: `responseTimeText()` — 초 → 사람이 읽는 문자열 ("보통 1시간 내 응답" 등)
- 인증 메서드: `verify()` — 관리자만 호출 · 이미 인증 시 `MKR002`

**주요 메서드**:
```java
public void markOnline() {
    this.isOnline = true;
    this.lastSeenAt = LocalDateTime.now();
}

public void markOffline() {
    this.isOnline = false;
    this.lastSeenAt = LocalDateTime.now();
}

public void updateResponseStats(BigDecimal rate, Integer avgSeconds) {
    this.responseRate = rate;
    this.avgResponseSeconds = avgSeconds;
}

public void incrementActiveProject() {
    this.activeProjectCount++;
}

public void decrementActiveProject() {
    if (this.activeProjectCount <= 0) {
        log.error("MAKER_ACTIVE_PROJECT_UNDERFLOW userId={}", userId);
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
    this.activeProjectCount--;
}

public void verify() {
    if (this.verified)
        throw new BusinessException(ErrorCode.MKR002);
    this.verified = true;
    this.verifiedAt = LocalDateTime.now();
}

public String responseTimeText() {
    if (avgResponseSeconds == null) return "-";
    if (avgResponseSeconds < 60) return avgResponseSeconds + "초 이내";
    if (avgResponseSeconds < 3600) return "보통 " + (avgResponseSeconds / 60) + "분 내 응답";
    return "보통 " + (avgResponseSeconds / 3600) + "시간 내 응답";
}

public String responseRateText() {
    if (responseRate == null) return "-";
    return responseRate.setScale(0, RoundingMode.HALF_UP) + "%";
}

public void updateProfile(String bio, String location) {
    this.bio = bio;
    this.location = location;
}
```

### 완료 기준 (AC)
- Given `MakerProfile(isOnline=false)` · When `markOnline()` · Then `isOnline=true · lastSeenAt` 설정
- Given `activeProjectCount=0` · When `decrementActiveProject()` · Then `INTERNAL_ERROR` · 로그 마커
- Given `verified=false` · When `verify()` · Then `verified=true · verifiedAt` 설정
- *(예외)* Given `verified=true` · When `verify()` · Then `MKR002`
- Given `avgResponseSeconds=null` · When `responseTimeText()` · Then `"-"`
- Given `avgResponseSeconds=45` · When `responseTimeText()` · Then `"45초 이내"`
- Given `avgResponseSeconds=1800` · When `responseTimeText()` · Then `"보통 30분 내 응답"`
- Given `avgResponseSeconds=7200` · When `responseTimeText()` · Then `"보통 2시간 내 응답"`
- Given `responseRate=BigDecimal("98.50")` · When `responseRateText()` · Then `"99%"`

### Definition of Done
- [ ] 5개 도메인 메서드 · 3개 뷰 메서드 · 1개 업데이트 메서드 (updateProfile)
- [ ] 단위 테스트: 각 성공·엣지·예외
- [ ] 프로퍼티 기반 테스트: `responseTimeText` 초 범위별

### 스토리 포인트
1d

### 의존성
- 선행: Story 1-1
- 후행: Epic 2·3·4

---

## [Story 1-3] `MakerProfileRepository` + User 생성 훅 + `ErrorCode.MKR001~002`

### User Story
- As a Maker Profile 서비스
- I want Repository · User 생성 시 자동 초기화 훅 · 2개 ErrorCode
- so that 조회 · 신규 사용자 유입 시 프로필 성립

### 설명
- Repository: `findByUserId · findActiveMakerIds (배치용) · findOnlineMakerIds` 등
- User 생성 훅: `AuthService.signup` 확장 · `MakerProfileService.initialize(userId)` 호출
- ErrorCode: `MKR001 · MKR002`

**주요 메서드**:
```java
public interface MakerProfileRepository extends JpaRepository<MakerProfile, Long> {
    Optional<MakerProfile> findByUserId(Long userId);

    @Query("SELECT p.userId FROM MakerProfile p WHERE p.activeProjectCount > 0")
    List<Long> findActiveMakerIds();

    @Query("SELECT p.userId FROM MakerProfile p WHERE p.isOnline = true ORDER BY p.lastSeenAt DESC")
    List<Long> findOnlineMakerIds(Pageable pageable);

    long countByIsOnlineTrue();
}
```

### 완료 기준 (AC)
- Given User 100건 (진행 프로젝트 있는 사용자 30명) · When `findActiveMakerIds` · Then 30건
- Given 온라인 사용자 15명 · When `findOnlineMakerIds(PageRequest.of(0, 10))` · Then 10건 · `lastSeenAt` 순
- Given AuthService.signup 호출 · When 완료 · Then MakerProfile 자동 생성

### Definition of Done
- [ ] `MakerProfileRepository` 인터페이스 · 4개 쿼리 메서드
- [ ] `AuthService.signup` 훅 확장 (또는 `@EventListener(UserCreatedEvent)`)
- [ ] `ErrorCode.MKR001 · MKR002` 등록 (Maker 섹션 주석 구분선)
- [ ] `@DataJpaTest` 슬라이스

### 스토리 포인트
1d

### 의존성
- 선행: Story 1-1·1-2 · Auth 도메인 (기존)
- 후행: Epic 2·3·4

---

# [Epic 2] `MakerResponseStatsScheduler` — 응답률·응답시간 일간 배치

## 목표
Chat (Product 9)의 채팅 이력을 원천으로 매일 새벽 3시에 활성 메이커의 응답률·응답시간을 자동 계산하여 `MakerProfile.updateResponseStats` 갱신한다.

## 배경
- Chat Product 9 완결 → 이력 이용 가능
- 24시간 이내 응답을 정합적 응답으로 간주
- 개별 메이커 실패 격리 (한 명 실패가 다른 메이커 계산에 영향 없음)

## 포함 Story
- Story 2-1: 응답 지표 계산 SQL/JPQL (후원자→메이커 첫 메시지 · 메이커 첫 응답 페어링)
- Story 2-2: `MakerResponseStatsScheduler` (일간 배치 · Failure Isolation)

## Epic 인수 시나리오
- Given 채팅 이력 100건 (다양 메이커·후원자) · When 배치 실행 · Then 각 메이커별 응답률·응답시간 계산 · 프로필 갱신
- *(엣지)* Given 이력 없는 메이커 · Then 계산 skip (null 유지)

## Epic 완료 기준 (DoD)
- [ ] 배치 실행 · dev 환경 검증
- [ ] 100건 이력 정확성 통합 테스트
- [ ] 개별 메이커 실패 격리 검증

---

## [Story 2-1] 응답 지표 계산 SQL/JPQL

### User Story
- As a 배치 서비스
- I want 특정 메이커의 최근 90일 채팅 이력에서 응답률·응답시간을 계산하는 SQL
- so that 스케줄러가 각 메이커별 이 로직 재사용

### 설명
- 알고리즘:
  1. 메이커가 참여자인 채팅방 목록 조회
  2. 각 방에서 "후원자가 시작한 메시지" (연속된 후원자 메시지의 첫 메시지) 조회
  3. 각 시작 메시지에 대해 "이후 첫 메이커 응답 메시지" 찾기
  4. 24시간 이내 응답이면 유효 페어 · 초과면 무응답으로 간주
  5. 응답률 = 유효 페어 수 / 전체 시작 메시지 수
  6. 평균 응답 시간 = 유효 페어의 응답 지연 초 평균
- 초기 구현: SQL 위주 (성능 우선) or Java 스트림 (가독성)
- **결정**: 데이터 규모 초기 작아 Java 스트림 채택 · 이력 10만 초과 시 SQL 최적화 (v0.0.4+)

**핵심 파일**:
- `nbc.c1oud_mall.auth.maker.application.MakerResponseStatsCalculator`

**주요 메서드**:
```java
@Component
@RequiredArgsConstructor
public class MakerResponseStatsCalculator {

    private static final int RESPONSE_THRESHOLD_HOURS = 24;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;

    public RespStats compute(Long makerId, LocalDateTime since) {
        // 1. 메이커가 참여한 채팅방 조회
        List<Long> roomIds = chatRoomRepository.findRoomIdsByMakerUserId(makerId);
        if (roomIds.isEmpty()) return RespStats.empty();

        int totalStartMessages = 0;
        int validResponses = 0;
        long totalResponseSeconds = 0L;

        for (Long roomId : roomIds) {
            List<ChatMessage> messages = chatMessageRepository
                .findByRoomIdAndCreatedAtAfterOrderByCreatedAt(roomId, since);

            List<ChatMessage> startMessages = findBackerStartMessages(messages);
            for (ChatMessage start : startMessages) {
                totalStartMessages++;
                Optional<ChatMessage> firstResponse = findFirstMakerResponse(messages, start, makerId);
                if (firstResponse.isPresent()) {
                    long delaySeconds = Duration.between(
                        start.getCreatedAt(), firstResponse.get().getCreatedAt()
                    ).getSeconds();
                    if (delaySeconds <= RESPONSE_THRESHOLD_HOURS * 3600L) {
                        validResponses++;
                        totalResponseSeconds += delaySeconds;
                    }
                }
            }
        }

        if (totalStartMessages == 0) return RespStats.empty();

        BigDecimal rate = BigDecimal.valueOf(validResponses * 100.0 / totalStartMessages)
                                     .setScale(2, RoundingMode.HALF_UP);
        Integer avgSeconds = (validResponses == 0)
                ? null
                : (int) (totalResponseSeconds / validResponses);
        return new RespStats(rate, avgSeconds);
    }

    private List<ChatMessage> findBackerStartMessages(List<ChatMessage> messages) {
        // 후원자가 보낸 메시지 · 이전 메시지가 없거나 이전 메시지가 메이커면 "start"
        // ...
    }

    private Optional<ChatMessage> findFirstMakerResponse(List<ChatMessage> messages,
                                                          ChatMessage startMsg, Long makerId) {
        return messages.stream()
                .filter(m -> m.getCreatedAt().isAfter(startMsg.getCreatedAt()))
                .filter(m -> Objects.equals(m.getSenderUserId(), makerId))
                .findFirst();
    }

    public record RespStats(BigDecimal rate, Integer avgSeconds) {
        public static RespStats empty() { return new RespStats(null, null); }
    }
}
```

### 완료 기준 (AC)
- Given 메이커 A · 채팅방 3개 · 후원자 시작 메시지 10건 · 메이커 응답 8건 (24h 이내) · When `compute` · Then `rate=80.00 · avgSeconds` 계산
- Given 응답 없는 시작 메시지 5건 · When `compute` · Then rate에 반영 (분모 증가)
- Given 24시간 초과 응답 · When `compute` · Then 무응답으로 처리
- Given 이력 없는 메이커 · When `compute` · Then `RespStats.empty()`

### Definition of Done
- [ ] `MakerResponseStatsCalculator` 구현
- [ ] `ChatRoomRepository.findRoomIdsByMakerUserId` · `ChatMessageRepository.findByRoomIdAndCreatedAtAfter...` 쿼리 (Chat Product 확장)
- [ ] 단위 테스트: 시나리오 6+ (성공·다양 응답 시간·outlier·이력 없음)
- [ ] 프로퍼티 기반 테스트: 임의 이력 데이터 → 정확성

### 스토리 포인트
2d

### 의존성
- 선행: Epic 1 · Chat Product 9
- 후행: Story 2-2

---

## [Story 2-2] `MakerResponseStatsScheduler` (일간 배치 · Failure Isolation)

### User Story
- As a 운영자
- I want 매일 새벽 3시 활성 메이커의 응답 지표를 자동 배치 계산
- so that 수동 개입 없이 신뢰 지표 최신 유지

### 설명
- `@Scheduled(cron = "0 0 3 * * *")`
- 활성 메이커(`findActiveMakerIds`) 순회
- 개별 실패는 로그 마커 · 상위 배치 계속
- 관측: `maker.stats_batch.duration_seconds` histogram · `maker.stats_batch.total{result}` counter

**핵심 파일**:
- `nbc.c1oud_mall.auth.maker.infrastructure.MakerResponseStatsScheduler`

**주요 메서드**:
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class MakerResponseStatsScheduler {
    private final MakerProfileRepository profileRepository;
    private final MakerResponseStatsCalculator calculator;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "0 0 3 * * *")
    public void runDaily() {
        long start = System.currentTimeMillis();
        LocalDateTime since = LocalDateTime.now().minusDays(90);

        List<Long> makerIds = profileRepository.findActiveMakerIds();
        long processed = 0, updated = 0, failed = 0;

        for (Long makerId : makerIds) {
            try {
                RespStats stats = calculator.compute(makerId, since);
                updateOne(makerId, stats);
                if (stats.rate() != null) updated++;
                processed++;
            } catch (Exception e) {
                log.error("MAKER_STATS_UPDATE_FAILED userId={}", makerId, e);
                failed++;
            }
        }

        long duration = System.currentTimeMillis() - start;
        meterRegistry.timer("maker.stats_batch.duration_seconds")
                     .record(duration, MILLISECONDS);

        String result = (failed == 0) ? "success"
                     : (processed > 0 ? "partial_fail" : "full_fail");
        meterRegistry.counter("maker.stats_batch.total", "result", result).increment();

        log.info("MAKER_STATS_BATCH_DONE processed={} updated={} failed={} duration={}ms",
            processed, updated, failed, duration);
    }

    @Transactional
    protected void updateOne(Long makerId, RespStats stats) {
        MakerProfile profile = profileRepository.findByUserId(makerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MKR001));
        profile.updateResponseStats(stats.rate(), stats.avgSeconds());
    }
}
```

### 완료 기준 (AC)
- Given 메이커 3명 · 채팅 이력 각 100건 · When 배치 실행 · Then 3명 모두 갱신 · 지표 반영
- Given 메이커 3명 중 1명 계산 실패 · When 실행 · Then 2명 성공 · 1명 로그 마커 · counter `partial_fail` 증가
- Given 이력 없는 메이커 (rate=null) · When 실행 · Then 프로필 그대로 (updated 카운트 미증가)

### Definition of Done
- [ ] `MakerResponseStatsScheduler`
- [ ] Timer/Counter 등록
- [ ] 통합 테스트: 정상 · 부분 실패 · 이력 없음
- [ ] dev 환경 수동 트리거

### 스토리 포인트
1.5d

### 의존성
- 선행: Story 2-1
- 후행: 없음

---

# [Epic 3] `MakerOnlineListener` (STOMP 훅) + Project 상태 부수효과 통합

## 목표
Chat (Product 9)의 STOMP CONNECT/DISCONNECT 이벤트로 메이커 온라인 상태를 실시간 갱신하고, Project (Product 6)의 상태 전이 시 `active_project_count`를 원자적으로 조정하여 프로필 지표의 실시간성과 정합성을 보장한다.

## 배경
- Chat Product 9의 `StompAuthInterceptor` 인증 정합 · Principal 확보 후 이벤트 발동
- Project 상태 전이는 이미 Product 6에서 도메인 메서드로 강제 · 부수효과로 MakerProfile 확장
- 다중 세션(같은 사용자 여러 브라우저)은 카운터 방식으로 처리 (v0.0.3 단순 · v0.0.5+ 정교화)

## 포함 Story
- Story 3-1: `MakerOnlineListener` (STOMP CONNECT/DISCONNECT 이벤트 훅)
- Story 3-2: Project 상태 전이 부수효과 · `active_project_count` 원자적 갱신

## Epic 완료 기준 (DoD)
- [ ] 2개 Story 완료
- [ ] 온라인 상태 통합 테스트 (CONNECT · DISCONNECT · 다중 세션)
- [ ] `active_project_count` SSOT 검증

---

## [Story 3-1] `MakerOnlineListener` STOMP 훅

### User Story
- As a Chat 인프라 확장
- I want STOMP CONNECT/DISCONNECT 이벤트로 `MakerProfile.markOnline · markOffline` 호출
- so that 메이커 온라인 상태 실시간 반영

### 설명
- Spring 이벤트: `SessionConnectEvent` · `SessionDisconnectEvent`
- Principal에서 `userId` 추출 · `MakerProfileService.markOnline(userId)` 호출
- 다중 세션 대응: 초기엔 카운터 없이 단순 CONNECT→online, DISCONNECT→offline (부정확 · 로그 마커)
- v0.0.5+: 세션 카운터 or Redis Set 도입

**핵심 파일**:
- `nbc.c1oud_mall.auth.maker.infrastructure.MakerOnlineListener`

**주요 메서드**:
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class MakerOnlineListener {
    private final MakerProfileService profileService;

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        try {
            Long userId = extractUserId(event);
            profileService.markOnline(userId);
        } catch (Exception e) {
            log.warn("MAKER_ONLINE_EVENT_FAILED event=CONNECT", e);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        try {
            Long userId = extractUserId(event);
            profileService.markOffline(userId);
        } catch (Exception e) {
            log.warn("MAKER_ONLINE_EVENT_FAILED event=DISCONNECT", e);
        }
    }

    private Long extractUserId(AbstractSubProtocolEvent event) {
        Principal user = SimpMessageHeaderAccessor.wrap(event.getMessage()).getUser();
        if (user instanceof AuthenticatedUser au) return au.userId();
        throw new IllegalStateException("Principal is not AuthenticatedUser");
    }
}
```

### 완료 기준 (AC)
- Given STOMP CONNECT · 인증 성공 · When 이벤트 · Then `MakerProfile.markOnline` 호출 · `is_online=true`
- Given DISCONNECT · When 이벤트 · Then `markOffline` · `is_online=false`
- *(엣지 · 다중 세션)* Given 같은 사용자 CONNECT 2회 · DISCONNECT 1회 · When 이벤트 · Then 초기 구현은 `is_online=false` (부정확 · 로그 마커) · v0.0.5+ 정교화

### Definition of Done
- [ ] `MakerOnlineListener` 구현
- [ ] `MakerProfileService.markOnline · markOffline` 노출
- [ ] 통합 테스트: WebSocketStompClient로 CONNECT/DISCONNECT 발생 · MakerProfile 상태 확인
- [ ] 다중 세션 케이스 로그 마커 확인
- [ ] ADR 항목: 다중 인스턴스·다중 세션 로드맵 명시

### 스토리 포인트
1.5d

### 의존성
- 선행: Epic 1 · Chat Product 9
- 후행: Story 3-2 · Epic 4

---

## [Story 3-2] Project 상태 부수효과 · `active_project_count` 원자적 갱신

### User Story
- As a Project 서비스 (Product 6)
- I want Project.launch (DRAFT→LIVE) · markFunded · markRefunded · complete · cancel 등 상태 전이 시 `MakerProfile.active_project_count` 원자적 조정
- so that 메이커 프로필 조회 시 실시간 진행 프로젝트 수 반영

### 설명
- 부수효과는 Project TX 안에서 (`REQUIRED` propagation)
- 상태 전이 규칙:
  - `DRAFT → LIVE` (launch) · `UPCOMING → LIVE` → `+1`
  - `LIVE → SUCCESSFUL` (첫 종료 판정) → 유지 (SUCCESSFUL은 진행 중으로 간주)
  - `SUCCESSFUL → FUNDED` (지급 완료) → 유지
  - `FUNDED → COMPLETED` (프로젝트 종료) → `-1`
  - `LIVE → FAILED` (미달성) → `-1`
  - `FAILED → REFUNDED` → 유지 (이미 감소됨)
  - `X → CANCELLED` (메이커 취소) → `-1` (LIVE·UPCOMING인 경우만)
- Product 6 SDD의 관련 Story (Epic 4 Story 4-1·4-2)에 이 부수효과 추가 (Product 6 개정 필요 · 본 Product SDD에서 명시)

**핵심 파일 확장**:
- `nbc.c1oud_mall.project.application.ProjectService` (부수효과 추가)
- `nbc.c1oud_mall.auth.maker.application.MakerProfileService` (`incrementActiveProject · decrementActiveProject` 제공)

### 완료 기준 (AC)
- Given `MakerProfile(activeProjectCount=0)` · When `project.launch()` · Then `activeProjectCount=1`
- Given `activeProjectCount=1` · When `project.complete()` (FUNDED→COMPLETED) · Then `activeProjectCount=0`
- Given `activeProjectCount=1` · When `project.markFailed()` (LIVE→FAILED) · Then `activeProjectCount=0`
- Given `activeProjectCount=1` · When `project.cancel(makerId)` (LIVE 중) · Then `activeProjectCount=0`
- *(엣지)* Given `activeProjectCount=0` · When `decrementActiveProject()` · Then `INTERNAL_ERROR` · 로그 마커
- *(정합성)* Given SSOT 검증 배치 실행 · When `activeProjectCount != COUNT(project WHERE maker_id=X AND status IN (LIVE, SUCCESSFUL, FUNDED))` · Then 알람

### Definition of Done
- [ ] `MakerProfileService.incrementActiveProject · decrementActiveProject` 노출
- [ ] Product 6 (`product-project.md`) Epic 4 Story 4-1·4-2 · Epic 3에 본 부수효과 반영 (SDD 개정 항목)
- [ ] 통합 테스트: 각 상태 전이 시 카운트 정확
- [ ] SSOT invariant 배치 (주간 · 별도 or 기존 배치에 통합)

### 스토리 포인트
1d

### 의존성
- 선행: Story 1-2 · Project Product 6 Epic 4
- 후행: 없음

---

# [Epic 4] REST 4개 + 관측 4개 + ADR + 규범 갱신

## 목표
사용자 노출 REST API 4개 · 관측 지표 4개 · ADR 2건 · 규범 갱신으로 메이커 프로필 도메인 완결.

## 포함 Story
- Story 4-1: `MakerProfileController` REST 4개 (`GET /users/{userId}/maker-profile · PATCH /users/me/maker-profile · POST /admin/users/{userId}/verify · GET /makers/online`)
- Story 4-2: 관측 지표 4개 등록
- Story 4-3: ADR 2건 + `backend-boundary/error-codes.md` MKR001~002 매핑

## Epic 완료 기준 (DoD)
- [ ] 4개 REST 엔드포인트
- [ ] 4개 지표 노출
- [ ] ADR 2건 발행
- [ ] 규범 갱신 완료

---

## [Story 4-1] REST 4개 엔드포인트

### User Story
- As a 후원자·메이커·관리자
- I want 메이커 프로필 조회·수정·인증·온라인 목록 API
- so that 프로젝트 상세·마이페이지·관리자 UI 지원

### 설명
- `GET /api/v1/users/{userId}/maker-profile` — 공개 · 프로필 조회
- `PATCH /api/v1/users/me/maker-profile` — 인증 필수 · body `{bio, location}` · 본인만
- `POST /api/v1/admin/users/{userId}/verify` — 관리자 권한 필수 · 인증 마크 부여
- `GET /api/v1/makers/online?limit=10` — 공개 · 지금 온라인 메이커 목록 (탐색 UX)

**Response**:
- `MakerProfileResponse` (record) — `userId · bio · location · verified · responseRate · responseTimeText · isOnline · lastSeenAt · activeProjectCount`

### 완료 기준 (AC)
- Given 프로필 존재 · When `GET /users/{userId}/maker-profile` · Then 200 · 응답 DTO
- *(예외 · 없음)* Given `userId=999` 프로필 없음 · When 호출 · Then 404 · `MKR001`
- Given 본인 · When `PATCH /users/me/maker-profile {bio: "..."}` · Then 200 · 갱신
- *(예외 · 미인증)* Then 401 · `C004`
- Given 관리자 · When `POST /admin/users/{userId}/verify` · Then 200 · `verified=true`
- *(예외 · 이미 인증)* Then 409 · `MKR002`
- *(예외 · 관리자 아님)* Then 403 · `C003`
- Given 온라인 메이커 5명 · When `GET /makers/online?limit=10` · Then 5건 · `lastSeenAt` 순

### Definition of Done
- [ ] `MakerProfileController` REST 4개
- [ ] `MakerProfileResponse` record
- [ ] `@WebMvcTest` 슬라이스 · 각 시나리오 · 성공/실패

### 스토리 포인트
1.5d

### 의존성
- 선행: Epic 1·2·3
- 후행: 없음

---

## [Story 4-2] 관측 지표 4개 등록

### User Story
- As a 운영자
- I want Maker 관련 4개 지표 프로덕션 노출
- so that Grafana/Actuator로 상시 관측

### 설명
- 4개 지표 (§관측 지표 참조):
  - `maker.online.gauge` — 현재 온라인 메이커 수 (실시간)
  - `maker.response_rate.avg` — 배치 후 전체 평균 응답률
  - `maker.stats_batch.duration_seconds` — 배치 처리 시간
  - `maker.stats_batch.total{result=success|partial_fail|full_fail}` — 배치 결과 분류

### 완료 기준 (AC)
- Given Prometheus 엔드포인트 · When `curl /actuator/prometheus` · Then 4개 지표 노출 (`maker.*`)
- Given 배치 실행 · Then Timer/Counter 정확 반영

### Definition of Done
- [ ] `MakerProfileService`·`MakerResponseStatsScheduler`·`MakerOnlineListener`에 MeterRegistry 주입
- [ ] Gauge 등록 (실시간 조회 · `countByIsOnlineTrue`)
- [ ] 통합 테스트: 지표 값 검증

### 스토리 포인트
0.5d

### 의존성
- 선행: Epic 2·3
- 후행: 없음

---

## [Story 4-3] ADR 2건 + `backend-boundary/error-codes.md` 갱신

### User Story
- As a 팀 리더
- I want ADR 2건 + 규범 갱신
- so that Maker 도메인 정책 확립 · 후속 Follow(Product 15) 참조

### 설명
- ADR 2건:
  - `021-maker-profile-user-1to1-extension-and-metrics.md` — User 1:1 확장 · 반정규화 필드 · 배치 계산 방식
  - `022-maker-online-state-stomp-hook-and-multi-instance-roadmap.md` — STOMP 훅 · 다중 인스턴스 로드맵
- 규범 갱신:
  - `workflows/backend-boundary/error-codes.md` MKR001~002 매핑

### 완료 기준 (AC)
- Given ADR 2건 · When 확인 · Then 완결
- Given `backend-boundary/error-codes.md` · Then MKR001~002 존재

### Definition of Done
- [ ] ADR 021 · 022 파일
- [ ] `backend-boundary/error-codes.md` MKR 섹션 추가
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
| Epic 1: 엔티티·도메인·Repository·ErrorCode | 3 | 2.5 |
| Epic 2: 응답 지표 배치 | 2 | 3.5 |
| Epic 3: 온라인·반정규화 훅 | 2 | 2.5 |
| Epic 4: REST·관측·ADR | 3 | 2.5 |
| **합계** | **10** | **11.0 SP** |

**진행 순서 (필수)**: Epic 1 → 2 → 3 → 4
- Epic 1은 나머지 모든 Epic의 전제
- Epic 2·3은 Epic 1 완료 후 병렬 가능
- Epic 4는 마지막 (규범 굳힘 · 후속 Follow 참조)

## 메이커 프로필 완결 → Follow(Product 15) 참조 준비

본 Product 10(Maker Profile) 완결 시 프로젝트 상세·채팅에서 후원 결정에 필요한 메이커 신뢰 지표 성립. 다음 Product들이 참조:
- **Follow (Product 15)**: 팔로워 수 조회 · 향후 반정규화 검토
- **Discovery (Product 13)**: use-for-project 흐름 시 메이커 정보 노출
- **Notification (v0.0.5+)**: 오프라인 시 미읽음 메시지 알림 진입점 (`is_online == false`)
