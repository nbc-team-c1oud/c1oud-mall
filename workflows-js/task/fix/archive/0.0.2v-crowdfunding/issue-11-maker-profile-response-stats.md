# Issue: Maker Profile · 응답 지표 (응답률·응답시간·인증·온라인 상태) 도메인 신설

## 배경

> **디자인 발견 (Round 5)**: 프로젝트 상세 · 채팅 페이지에서 메이커 신뢰 지표가 여러 곳에 노출.

- 디자인 확인 요소:
  - **응답률 98%** · **보통 1시간 내 응답** · 온라인 상태(초록 점)
  - **인증 마크** (verified 체크)
  - **진행 프로젝트 수 · 팔로워 수**
  - "1인 메이커" 등 역할·자기소개
- 후원 결정 시 메이커에 대한 신뢰가 결정적 → **응답 지표 자동 계산 · 표시 필수**
- 이슈 #01(채팅) 이력에서 응답 시간을 자동 계산해 배치 갱신 필요

## 조사 결과 — Kickstarter · 텀블벅 · 카카오 크리에이터 벤치마크

| 항목 | Kickstarter | 텀블벅 | 카카오 크리에이터 | c1oud-mall 대응 |
|---|---|---|---|---|
| 응답률 | "일반적으로 24시간 이내" | 없음 | "평균 응답시간" | 채택 (자동 계산) |
| 응답 시간 평균 | 없음 (텍스트 표시만) | 없음 | 정량 표시 (분/시간) | 채택 |
| 인증 마크 | 없음 | 없음 (신원 인증 있음) | 있음 | 관리자 승인 (v0.0.5+) |
| 온라인 상태 | 없음 | 없음 | 실시간 | STOMP CONNECT 시 갱신 |
| 진행 프로젝트 수 | 있음 | 있음 | 있음 | 반정규화 카운트 |
| 팔로워 수 | 있음 | 있음 | 있음 | 이슈 #03 참조 |

## 옵션 비교

### 갈림길 1: MakerProfile 도메인 위치

**Option A (채택) — User 확장 (`user.maker_profile` 컬럼 or 1:1 확장 테이블)**
- 장점: User와 자연 통합 · 인증 상태·팔로워 등 이미 User에 있는 지표 재활용
- 구현: 별도 테이블 `maker_profile(user_id, verified, bio, response_rate, avg_response_seconds, is_online, last_seen_at)` (User와 1:1)
- 초기: User 테이블에 컬럼 추가 · 필드 증가 시 별도 테이블 분리 검토

**Option B — 별도 Aggregate**
- 거부 이유: User와 항상 함께 조회 · 분리 오버헤드

### 갈림길 2: 응답률·응답시간 계산 방식

**Option A (채택) — 배치 (일간 · 최근 90일 기준)**
- 매일 새벽 배치 · 최근 90일 채팅 메시지 중 후원자→메이커 → 메이커 응답 페어 계산
- 응답률 = 응답한 메시지 수 / 전체 후원자 시작 메시지 수 (24시간 내)
- 응답시간 평균 = 각 페어의 응답 지연 초 평균
- 장점: 부하 낮음 · 구현 단순

**Option B — 실시간 (메시지 저장 시 즉시 갱신)**
- 부하 큼 · 락 부담 · 신입 스코프 오버

### 갈림길 3: 온라인 상태

**Option A (채택) — STOMP CONNECT/DISCONNECT 이벤트로 갱신**
- WebSocket 연결 시 `is_online=true` · 해제 시 `is_online=false`
- `last_seen_at` 기록 (마지막 활동 시각)
- 구현: `StompAuthInterceptor` (이슈 #01)에 CONNECT/DISCONNECT 훅 추가

**Option B — HTTP heartbeat**
- 거부 이유: 채팅 STOMP 이미 있으므로 재사용이 자연

## 선택: Option A (모든 갈림길)

## 부속 결정

### 도메인 컨텍스트
- 기존 `nbc.c1oud_mall.auth.user.*` 확장 (User 도메인 안 서브패키지 or User 자체 확장)
- 서브패키지 신설: `nbc.c1oud_mall.auth.maker.*` (MakerProfile + 배치 서비스)

### 엔티티 · 스키마
```sql
-- User 1:1 확장 테이블
CREATE TABLE maker_profile (
  user_id                 BIGINT       NOT NULL,
  bio                     VARCHAR(500) NULL,          -- "1인 메이커 · 오픈소스" 등 자기소개
  location                VARCHAR(100) NULL,          -- "서울"
  verified                BOOLEAN      NOT NULL DEFAULT FALSE,  -- 인증 마크
  verified_at             TIMESTAMP    NULL,
  response_rate           DECIMAL(4,2) NOT NULL DEFAULT 0.00,   -- 0~100% · 배치 계산
  avg_response_seconds    INT          NULL,          -- 평균 응답 지연 (초)
  is_online               BOOLEAN      NOT NULL DEFAULT FALSE,
  last_seen_at            TIMESTAMP    NULL,
  active_project_count    INT          NOT NULL DEFAULT 0,      -- 반정규화
  updated_at              TIMESTAMP    NOT NULL,
  PRIMARY KEY (user_id),
  KEY idx_maker_verified (verified),
  KEY idx_maker_online (is_online)
);
```

### 도메인 모델
```java
// auth.maker.domain.MakerProfile
@Entity
public class MakerProfile {
    @Id Long userId;
    String bio;
    String location;
    boolean verified;
    LocalDateTime verifiedAt;
    BigDecimal responseRate;         // 0.00 ~ 100.00
    Integer avgResponseSeconds;
    boolean isOnline;
    LocalDateTime lastSeenAt;
    int activeProjectCount;

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

    public String responseTimeText() {
        if (avgResponseSeconds == null) return "-";
        if (avgResponseSeconds < 60) return avgResponseSeconds + "초 이내";
        if (avgResponseSeconds < 3600) return "보통 " + (avgResponseSeconds / 60) + "분 내 응답";
        return "보통 " + (avgResponseSeconds / 3600) + "시간 내 응답";
    }
}
```

### 응답 지표 배치 (핵심 스토리)
```java
// auth.maker.infrastructure.MakerResponseStatsScheduler
@Component
@RequiredArgsConstructor
public class MakerResponseStatsScheduler {
    private final ChatMessageRepository messageRepository;
    private final MakerProfileRepository profileRepository;

    // 매일 새벽 3시
    @Scheduled(cron = "0 0 3 * * *")
    public void recalculate() {
        LocalDateTime since = LocalDateTime.now().minusDays(90);
        // 모든 메이커 순회 (활성 프로젝트 소유자만)
        for (Long makerId : profileRepository.findActiveMakerIds()) {
            RespStats stats = computeStats(makerId, since);
            profileRepository.updateStats(makerId, stats.rate, stats.avgSeconds);
        }
    }

    private RespStats computeStats(Long makerId, LocalDateTime since) {
        // 1. 후원자→메이커 시작 메시지 목록 조회
        // 2. 각 메시지에 대해 메이커의 첫 응답 메시지 검색
        // 3. 응답 여부 · 응답 지연 초 계산
        // 4. 응답률 = 응답 있는 메시지 / 전체
        // 5. 평균 응답 지연 = 응답 페어들의 초 평균
        // ... (SQL 집계 or Java 스트림)
    }
}
```

### 온라인 상태 갱신 (STOMP 훅)
```java
// auth.maker.infrastructure.MakerOnlineListener
@Component
@RequiredArgsConstructor
public class MakerOnlineListener {
    private final MakerProfileService profileService;

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        Long userId = extractUserId(event);
        profileService.markOnline(userId);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Long userId = extractUserId(event);
        profileService.markOffline(userId);
    }
}
```

### API 표면
| 메서드 | 경로 | 인증 | 용도 |
|---|---|---|---|
| GET | `/api/v1/users/{userId}/maker-profile` | 공개 | 메이커 프로필 조회 (인증·응답률·온라인 등) |
| PATCH | `/api/v1/users/me/maker-profile` | JWT | 내 프로필 수정 (bio · location) |
| POST | `/api/v1/admin/users/{userId}/verify` | 관리자 | 메이커 인증 (v0.0.5+) |
| GET | `/api/v1/makers/online` | 공개 | 지금 온라인 메이커 (탐색 UX) |

### ErrorCode (신규)
- `MKR001` MAKER_PROFILE_NOT_FOUND (404)
- `MKR002` MAKER_ALREADY_VERIFIED (409 · 이미 인증됨)

### 정합성 · 멱등성
- MakerProfile은 User 생성 시 자동 초기화 (`created_at` = User.created_at)
- 응답 지표 배치는 idempotent (같은 날 여러 번 실행해도 결과 동일)
- 온라인 상태는 STOMP 세션 기반 · 세션 종료 시 자동 offline

### 성능 · 부하
- 응답 지표 배치: 프로젝트 활성 메이커 100명 · 채팅 이력 10만 건 예상 → 배치 5분 이내 완료 (초기 규모)
- 온라인 상태는 실시간 갱신 · Redis 필요 없음 (초기)

### 관측
- `maker.response_rate.avg` gauge
- `maker.online.count` gauge (현재 온라인 메이커 수)
- `maker.stats_batch.duration_seconds` histogram

## 이관 산출물

- **BE-Story #11-1**: `MakerProfile` 엔티티 + Repository + User와 1:1 관계
- **BE-Story #11-2**: `MakerProfile.markOnline`·`markOffline`·`updateResponseStats` 도메인 메서드
- **BE-Story #11-3**: `MakerProfileService` (조회 · 수정)
- **BE-Story #11-4**: `MakerResponseStatsScheduler` (일간 배치 · Cron)
- **BE-Story #11-5**: `MakerOnlineListener` (STOMP 이벤트 · 이슈 #01 확장)
- **BE-Story #11-6**: `MakerProfileController` (4개 엔드포인트)
- **BE-Story #11-7**: `ErrorCode.MKR001~002` 등록
- **BE-Story #11-8**: 배치 통합 테스트 (더미 채팅 이력 · 응답률 정확성)
- **BE-Story #11-9**: STOMP 온라인 상태 통합 테스트 (연결·해제·다중 세션)
- **FE-Story #11-1**: `src/features/maker/MakerBadge.tsx` (온라인 상태 · 인증 마크)
- **FE-Story #11-2**: `src/features/maker/MakerStats.tsx` (응답률 · 응답시간 · 프로젝트 · 팔로워)
- **FE-Story #11-3**: 프로젝트 상세 페이지에 메이커 aside 카드 (디자인 반영)
- **Docs-Story #11-1**: `backend-boundary/error-codes.md` MKR001~002 매핑
- **SDD 개정**: 향후 `product-maker.md` 신규 (또는 `product-project.md`에 흡수)

## 관련 이슈 / 문서

- 선행: [#01 채팅](./issue-01-websocket-chat.md) — 응답 지표 원천 · STOMP 이벤트
- 관련: [#03 팔로우](./issue-03-follow.md) — 팔로워 수 표기
- 관련: [#08 Project](./issue-08-project-domain.md) — 메이커 = Project.makerUserId
- 관련: [#12 Chat Rich Message](./issue-12-chat-rich-message.md) — 채팅 UI에 메이커 온라인 상태 노출
- 벤치마크: 카카오 크리에이터 (평균 응답시간 자동 계산)

## 디자인 참조
- `C:\Users\user\Desktop\fe\프로젝트 상세.html` — 메이커 aside 카드 (진행 3 · 팔로워 1.2k · 응답률 98%)
- `C:\Users\user\Desktop\fe\메이커 채팅.html` — thread-head 메이커 정보 (verified 마크 · "보통 1시간 내 응답")
