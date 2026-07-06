# [Product 9] 채팅 (Chat · WebSocket + Rich Message · 후원자↔메이커 소통 채널)

## Product Vision
> 후원자와 메이커가 프로젝트 상세 화면에서 1:1 실시간으로 소통하는 채팅 시스템. STOMP over WebSocket · JWT 인증 · Soft Delete를 기본 골격으로 구축하고, 그 위에 **리워드 카드·프로젝트 카드 발송 (Rich Message) · 프로젝트 컨텍스트 embed · 읽음/타이핑 인디케이터**를 얹어 후원 결정 UX를 자연스럽게 확장한다. 크라우드 펀딩에서 후원 전 문의는 신뢰의 핵심이며, 응답률·응답시간(Product 12 Maker Profile) 계산의 원천 데이터가 된다.

## 배경 및 문제
- 현재 상황 (As-Is)
  - c1oud-mall은 REST 요청-응답만 지원 · 실시간 채널 부재
  - 후원자가 프로젝트를 이해하고 후원 결정 전에 궁금한 점(예: "라즈베리파이도 지원되나요?" · "언제 배송되나요?")을 물어볼 곳 없음 → 메이커 이메일로 우회 · 응답 지연
  - 이슈 #10 Pledge (Product 8) 완결 후 후원 흐름은 성립했으나 **후원 전 문의 UX** 부재
  - 디자인(`메이커 채팅.html`) 검토 결과 정교한 UX 이미 설계됨 (리워드 카드 · 프로젝트 컨텍스트 · 읽음/타이핑 · 빠른 답변)
- 발생하는 문제
  - 후원 결정 마찰 (질문 채널 부재 → 이탈률 상승)
  - 메이커가 다수 후원자와 소통 시 이메일 관리 부담 · 응답 이력 흩어짐
  - 이슈 #12 Rich Message · REWARD_CARD 발송이 이슈 #09 Reward Tier와 이슈 #10 Pledge를 결합하는 UX 진입점인데 진입점 부재로 활성화 안 됨
  - Product 12 Maker Profile의 **응답률·응답시간** 계산 원천 데이터 없음 (채팅 이력이 있어야 배치 계산 가능)
- 왜 지금 해결해야 하는가
  - 크라우드 펀딩 코어(Wallet+Project+Reward+Pledge) 완결 이후 첫 확장 도메인
  - Rich Message 진입점(리워드 카드 → Pledge 후원)이 코어 완결에 의존 · 지금이 결합 시점
  - Product 12 Maker Profile 응답 지표 배치의 데이터 원천 · Product 12 앞에 완결 필요
  - 초기 트래픽 없어 STOMP 다중 인스턴스 확장(Redis pub/sub 등) 없이 SimpleBroker로 시작 가능

## 목표 (To-Be)
- 신규 컨텍스트: `nbc.c1oud_mall.chat.*` (4레이어)
- **WebSocket 인프라**: STOMP + SimpleBroker + `/ws/chat` 엔드포인트 + `StompAuthInterceptor` (JWT 검증)
- **도메인 3개**:
  - `ChatRoom` Aggregate root — `buyerUserId` 재해석 → **`backerUserId · makerUserId · projectId`** (nullable)
  - `ChatMessage` — Soft Delete (`@SQLDelete · @SQLRestriction`) · `MessageType` 5-state enum + JSON `content`
  - `ChatRoomMember` — 참여자별 `last_read_message_id` · 읽음/안 읽은 수 관리
- **MessageType enum**: `TEXT · REWARD_CARD · PROJECT_CARD · IMAGE · SYSTEM`
- **Rich Message payload (JSON)**:
  - `REWARD_CARD` — `{ rewardId, projectId, title, price, description, thumbnailUrl? }`
  - `PROJECT_CARD` — `{ projectId, title, coverUrl, progressPct, dDay, backerCount }`
  - `IMAGE` — `{ imageUrl, width, height }`
  - `SYSTEM` — `{ systemType, text }` (ROOM_CREATED 등)
- **STOMP 이벤트**: 타이핑 인디케이터 (저장 X · 통과)
- **읽음 표시**: `POST /chat-rooms/{id}/read`로 갱신 · `GET /chat-rooms/{id}/unread-count` · 실시간 브로드캐스트
- **REST API** 7개 + **WebSocket** 3개 destination
- `ErrorCode.CHAT001~005` (기본 · 이슈 #01) + `CHM001~003` (Rich Message · 이슈 #12) 통합
- 관측 지표 5종
- Pledge Product 8과 결합: 채팅 REWARD_CARD 클릭 → 후원 진입 흐름 성립
- Maker Profile Product 12의 응답 지표 원천 제공

## 설계 결정 (Design Decisions)
> 큰 갈림길의 결정. 거부된 옵션도 합리적 근거가 있었음을 명시.

- **STOMP + SimpleBroker** 채택 · Redis pub/sub는 v0.0.5+ (Cabbage-Market-10 벤치마크 준수)
  - Spring 표준 · 학습 곡선 낮음 · 인프라 부담 없음
  - 초기 단일 인스턴스 배포에 부합 · 다중 인스턴스 확장 시 `RedisMessageBrokerRelay`로 전환
- **`StompAuthInterceptor`로 CONNECT 시 JWT 검증** — REST와 동일한 JWT 자산 재사용
  - CONNECT 프레임의 `Authorization: Bearer ...` 헤더 파싱 · `AuthenticatedUser`를 `accessor.setUser()`
  - 이후 SEND/SUBSCRIBE에서 `Principal` 참조 가능
- **`ChatRoom` 필드는 크라우드 펀딩 재해석 반영** (이슈 #01 재해석 섹션)
  - `buyer_user_id · seller_user_id · item_id` → `backer_user_id · maker_user_id · project_id`
  - `project_id`는 nullable — 프로젝트 무관 문의도 허용 (미래 확장)
  - `room_uuid` 유지 (외부 노출용 논리 채번 · 이슈 #01 확정)
- **`ChatMessage.content`는 JSON 컬럼** (이슈 #12 확정 · Option A)
  - `MessageType`별 payload 스키마 다름 · JSON으로 유연성 확보
  - MySQL JSON 타입 사용 (H2도 MySQL 호환 모드에서 지원)
  - TEXT 메시지는 `{ "text": "..." }` 형태로 통일
- **참여자별 읽음 표시 = `ChatRoomMember.last_read_message_id`** (이슈 #12 Option A)
  - 안 읽은 수 = `COUNT(message.id > last_read_message_id)`
  - 상대방 읽음 표시 = 상대방의 `last_read_message_id` 조회
  - 인덱스 커버 · 성능 우수
- **타이핑 인디케이터 = STOMP 이벤트만 · DB 저장 X**
  - 발행: `/pub/rooms/{roomId}/typing`
  - 구독: `/sub/rooms/{roomId}/typing`
  - 서버는 통과 (validation만) · at-most-once (놓쳐도 OK)
- **Rich Message 페이로드는 발송 시점 스냅샷** (참조 무결성 X)
  - REWARD_CARD payload에 `price · title` 스냅샷 저장 → 후 이 티어의 가격 변경돼도 채팅 이력은 발송 당시 정보 유지
  - 이슈 #09 Reward Tier의 "LIVE 이후 편집 잠금"과 정합
- **채팅방 개설 시 프로젝트 참여자 검증** (`ChatRoom.verifyParticipant`)
  - 방 개설 시점 이후 backerUserId·makerUserId 검증
  - 프로젝트 상세에서 "1:1 문의" 버튼 클릭한 후원자만 개설 가능
- **`ChatRoom` UNIQUE 제약** — `(backer_user_id, maker_user_id, project_id)` 조합
  - 동일 조합 재개설 시 기존 방 반환 (idempotent)

## 대안 검토 (Alternatives Considered)

### 브로커 방식
**Option A — STOMP + SimpleBroker (선택)**
- 비용: 다중 인스턴스 확장 시 Redis 필요 (v0.0.5+)
- 보상: 초기 부담 없음 · Spring 표준 · Cabbage-Market-10 벤치마크 정확 일치

**Option B — STOMP + Redis pub/sub (초기부터)**
- 거부 이유: 초기 인프라 오버킬 · Redis 미도입 프로젝트에 부담

**Option C — Raw WebSocket (STOMP 아님)**
- 거부 이유: 프로토콜 자체 정의 부담 · 클라이언트 라이브러리 생태계(STOMP.js) 재활용 불가

### 메시지 content 저장 방식
**Option A — JSON 컬럼 + MessageType enum (선택)**
- 비용: 각 타입별 스키마 검증 필요
- 보상: 유연 확장 · 조회 시 단일 쿼리 · MySQL JSON 함수 활용 가능

**Option B — 별도 테이블 (text_message · reward_card_message · image_message ...)**
- 거부 이유: JOIN 부담 · 조회 복잡

**Option C — content(TEXT) + `type_specific_fields(JSON)` 두 컬럼**
- 거부 이유: TEXT와 JSON 이중 관리 부담

### 읽음 표시 저장
**Option A — `ChatRoomMember.last_read_message_id` (선택)**
- 안 읽은 수 = `COUNT(message.id > last_read_message_id)` · 인덱스 커버

**Option B — 메시지별 `read_by JSON 배열`**
- 거부 이유: JSON 배열 조회 어려움 · 인덱스 불가

**Option C — 별도 `message_read` 테이블 (`message_id, user_id, read_at`)**
- 거부 이유: 메시지당 참여자 수만큼 행 추가 · 저장·조회 부담

### 타이핑 인디케이터
**Option A — STOMP 이벤트 · DB 저장 X (선택)**
- 저장 부담 없음 · 실시간성 · 놓쳐도 무해

**Option B — Redis TTL 5초로 저장 (실시간 조회)**
- 거부 이유: Redis 인프라 부담 · v0.0.5+ 검토

### Rich Message 참조 방식
**Option A — 발송 시점 스냅샷** (선택)
- 후 리워드 티어 변경돼도 이력 유지 · 이슈 #09 편집 잠금과 정합

**Option B — FK 참조만 (조회 시 원본 조회)**
- 거부 이유: 원본 삭제 시 이력 깨짐 · 채팅 이력의 원본 참조 부담

## 전체 아키텍처 (High-Level Architecture)

### 컴포넌트 배치
```
presentation ──▶ application ──▶ domain ◀── infrastructure
ChatController         ChatRoomService        ChatRoom                    ChatRoomRepository
- createRoom           - createOrGet          (Aggregate)                 - findByIdForUpdate
- myRooms              - getMyRooms           - verifyParticipant()       - findByBackerAndMakerAndProject
- getMessages          - getMessages          - markLastMessage()         - findByUserIdPaginated
- deleteMessage        ChatMessageService     ChatMessage                 ChatMessageRepository
- markRead             - sendText             (Entity + SoftDelete)       - findByRoomIdCursor
- getUnreadCount       - sendRewardCard       - MessageType (5-state)     - findByRoomIdAndUser
                       - sendProjectCard      ChatRoomMember              ChatRoomMemberRepository
ChatWebSocketController - sendImage           - markRead(msgId)           - findByRoomIdAndUserId
- @MessageMapping      - softDelete           - lastReadMessageId         (WebSocket)
   /rooms/{roomId}/    - markRead             SignalPayload types         WebSocketConfig
     messages          - getUnreadCount       - RewardCardPayload         - /ws/chat
   /rooms/{roomId}/                           - ProjectCardPayload        - SimpleBroker /sub, /pub
     typing                                   - ImagePayload              StompAuthInterceptor
                                              - SystemPayload             - CONNECT JWT 검증

External refs:
- RewardTier (Product 7) — REWARD_CARD payload 스냅샷 원천 · 후원 진입점
- Project (Product 6) — ChatRoom.projectId · PROJECT_CARD payload
- Pledge (Product 8) — REWARD_CARD 클릭 후 Pledge 진입
- Maker Profile (Product 12 · 후속) — 채팅 이력에서 응답률·응답시간 배치 계산
```

### 핵심 플로우
**1. 채팅방 개설 (프로젝트 상세 "1:1 문의" 클릭)**
```
Backer → ChatController.createRoom({projectId, makerUserId})
       → ChatRoomService.createOrGet(backerId, makerId, projectId)
         ├── ChatRoom UNIQUE 검증 (backer + maker + project 조합)
         │     - 이미 존재 → 기존 반환 (idempotent)
         │     - 신규 → ChatRoom.create(backerId, makerId, projectId)
         ├── ChatRoomMember 2건 저장 (backer 참여자 · maker 참여자)
         └── SYSTEM 메시지 자동 발송 (ROOM_CREATED)
       ← 200 OK + ChatRoomResponse (roomId, roomUuid)
```

**2. 메시지 전송 (WebSocket)**
```
Client (Backer) → STOMP SEND `/pub/rooms/{roomId}/messages` {text: "..."}
                → ChatWebSocketController.handleMessage(roomId, message, principal)
                  → ChatMessageService.sendText(userId, roomId, text)
                    ├── ChatRoom 조회 · 참여자 검증 (CHAT002)
                    ├── ChatMessage.of(roomId, senderId, TEXT, {text: "..."})
                    ├── save
                    ├── chatRoom.markLastMessage(now)
                    └── STOMP 브로드캐스트 `/sub/rooms/{roomId}/messages`
                          - 상대방 (Maker) 실시간 수신
```

**3. 리워드 카드 발송 (Rich Message · REST)**
```
Maker → ChatController.sendRewardCard(roomId, {rewardId})
      → ChatMessageService.sendRewardCard(userId, roomId, rewardId)
        ├── ChatRoom 조회 · 참여자 검증
        ├── RewardTier 조회 (Product 7)
        ├── RewardCardPayload.from(reward) → JSON 직렬화
        ├── ChatMessage.of(roomId, userId, REWARD_CARD, payloadJson)
        ├── save
        └── STOMP 브로드캐스트
      ← 200 + ChatMessageResponse

Backer receives card → clicks "이 리워드로 후원" 버튼
  → PledgeController.create (Product 8) — 후원 진입 · 통합 흐름
```

**4. 읽음 표시 갱신 + 실시간 알림**
```
Backer가 채팅방 진입 · 최근 메시지 렌더링 완료
  → ChatController.markRead(roomId, lastMessageId)
    → ChatRoomMemberRepository → member.markRead(lastMessageId)
    → STOMP 브로드캐스트 `/sub/rooms/{roomId}/read`
       - 상대방(Maker)의 UI에 "읽음" 표시 실시간 반영
```

**5. 타이핑 인디케이터 (통과)**
```
Client → STOMP SEND `/pub/rooms/{roomId}/typing`
       → ChatWebSocketController.handleTyping(roomId, principal)
         ├── Validation만 (참여자 확인)
         └── STOMP 브로드캐스트 `/sub/rooms/{roomId}/typing` {userId}
              - 상대방 UI에 타이핑 애니메이션 표시
              - 저장 X · at-most-once
```

### Out-of-Process 의존
- **RDS (MySQL)** — `chat_room` · `chat_room_member` · `chat_message` 영속화
- (참조) Reward Tier · Project · User — payload 스냅샷 원천 (BC 협력 직접 호출)
- **SockJS/STOMP 클라이언트** (FE) — 실시간 통신
- (v0.0.5+) Redis pub/sub — 다중 인스턴스 확장 시

## 실패 모드 / 운영 관측 (Failure Modes & Observability)

### 실패 시나리오와 응답
| 시나리오 | ErrorCode | HTTP/WS | 클라이언트 권장 동작 |
| --- | --- | --- | --- |
| 채팅방 없음 | `CHAT001` CHAT_ROOM_NOT_FOUND | 404 | 목록으로 이동 |
| 참여자 아닌 사용자가 접근 | `CHAT002` CHAT_ACCESS_DENIED | 403 | 접근 거부 |
| 메시지 없음 | `CHAT003` CHAT_MESSAGE_NOT_FOUND | 404 | 새로고침 |
| 본인 메시지 아님 (삭제 시) | `CHAT004` CHAT_MESSAGE_OWNERSHIP_FAILED | 403 | 삭제 거부 |
| WS 인증 실패 (JWT 유효하지 않음) | `CHAT005` CHAT_INVALID_STOMP_TOKEN | WS ERROR | 재연결 유도 · 로그인 |
| Rich Message 타입 지원 안 됨 | `CHM001` CHAT_MESSAGE_INVALID_TYPE | 400 | 클라이언트 버전 확인 |
| Rich Message payload JSON 스키마 위반 | `CHM002` CHAT_MESSAGE_PAYLOAD_INVALID | 400 | 재요청 |
| RewardTier/Project 참조 없음 (카드 발송 시) | `CHM003` CHAT_MESSAGE_ATTACHED_ENTITY_NOT_FOUND | 404 | 티어/프로젝트 상태 확인 |
| STOMP 브로드캐스트 실패 (broker 이슈) | (내부 로그) | - | 저장은 성공 · 재조회 API로 회복 |

### 로깅 정책
- **항상 기록**:
  - `requestId` · `chatRoomId` · `messageId` · `senderId` · `messageType` · 액션 (send/delete/read)
- **debug**: STOMP CONNECT/DISCONNECT · 세션 ID · 타이핑 이벤트 카운트 (샘플링)
- **절대 금지**:
  - `content` 원문 (TEXT 메시지의 내용 · 개인정보 · 사업 정보 포함 가능)
  - `imageUrl` 원문 (Signed URL 노출 방지)
- **특수 마커**: `CHAT_BROADCAST_FAILED roomId={} messageId={}` — 저장은 성공했으나 실시간 전달 실패

### 관측 지표
- `chat.message.total{type=TEXT|REWARD_CARD|PROJECT_CARD|IMAGE|SYSTEM}` — counter — 메시지 발송 유형별
- `chat.room.created.total` — counter — 채팅방 신규 개설
- `chat.read.total` — counter — 읽음 표시 갱신 수
- `chat.typing.event.total` — counter — 타이핑 이벤트 (샘플링 · 카디널리티 없음)
- `chat.ws.connection.gauge` — gauge — 현재 활성 STOMP 세션 수 (Maker Profile 온라인 상태 원천)

## 롤아웃 / 마이그레이션 (Rollout)

### 전제
- Product 5·6·7·8 완결 · Rich Message payload 원천 이용 가능
- 초기 사용자 없음 · 신규 테이블만 · JPA ddl-auto 자동 반영
- FE는 SockJS + STOMP.js 라이브러리 도입 · 별도 이슈로 관리

### Product 의존성
- **선행**: **Wallet(5) · Project(6) · Reward Tier(7) · Pledge(8)** — REWARD_CARD 페이로드 원천 및 후원 진입점
- **동시 대응**: `application.yml`의 CORS origins 갱신 (WebSocket 엔드포인트 허용)
- **후행**: **Maker Profile(12)** — 채팅 이력에서 응답률 계산 · **Notification(v0.0.5+)** — 미읽음 메시지 알림

### Epic·Story 의존성 그래프
```
Epic 1 (WebSocket 인프라) ──► Epic 2 (도메인 3개)
                                    ├─► Epic 3 (Rich Message)
                                    └─► Epic 4 (읽음/타이핑 + REST + 관측)
```

### 환경별 설정 분기
| 항목 | dev (H2 · local) | prod (RDS MySQL) |
| --- | --- | --- |
| SimpleBroker | 활성 (동일) | 동일 (v0.0.5+ Redis 전환) |
| `/ws/chat` allowedOrigins | `http://localhost:*` | FE 도메인 (예정) |
| JSON 컬럼 | H2 MySQL 호환 모드 지원 | 완전 지원 |
| STOMP 세션 heartbeat | 10초/10초 | 동일 |
| `chat.ws.connection.gauge` 추적 | 로컬 · 다중 인스턴스 무관 | 단일 인스턴스 전제 |

## 성공 지표 (KPI)
| 지표 | 목표 값 | 측정 방법 |
| --- | --- | --- |
| WS 메시지 전송 지연 P95 | ≤ 200ms (STOMP 브로커 처리) | `chat.message.total` + 응답 시간 로그 |
| 채팅방 개설 → 첫 메시지 전송 성공률 | ≥ 99% | `chat.room.created` vs 첫 메시지 존재 여부 |
| 읽음 표시 정확성 (상대방 last_read 반영) | 100% | 통합 테스트 |
| REWARD_CARD 클릭 → Pledge 전환율 | ≥ 30% (초기 목표) | 카드 발송 카운트 vs 이후 Pledge 생성 카운트 (분석 쿼리) |
| WS 연결 안정성 (재접속 없이 유지) | ≥ 95% (5분 세션) | `chat.ws.connection.gauge` 관측 |

## Scope
**In Scope**:
- 신규 컨텍스트 `nbc.c1oud_mall.chat.*` (4레이어)
- WebSocket 인프라: `WebSocketConfig` + `StompAuthInterceptor` + `/ws/chat` 엔드포인트
- 3개 도메인: `ChatRoom` (Aggregate) · `ChatMessage` (SoftDelete) · `ChatRoomMember`
- `MessageType` 5-state enum + JSON payload 4종
- Rich Message 발송 REST API (REWARD_CARD · PROJECT_CARD · IMAGE)
- 읽음 표시 (`markRead` + `/sub/rooms/{id}/read` 브로드캐스트)
- 타이핑 인디케이터 (STOMP 이벤트 · 저장 X)
- REST 7개 엔드포인트 + WS 3개 destination
- ErrorCode `CHAT001~005` + `CHM001~003` (총 8개)
- 관측 지표 5종
- SYSTEM 메시지 (ROOM_CREATED)

**Out of Scope**:
- **그룹 채팅** (3+ 참여자) — 1:1 전용 · v0.0.5+
- **파일 첨부** — IMAGE만 지원 · PDF·문서 등은 v0.0.5+
- **음성/영상 통화** — WebRTC 별도 도메인 · 스코프 밖
- **메시지 수정** — Soft Delete만 지원 · 수정은 삭제 후 재발송
- **메시지 예약 발송** — v0.0.5+
- **번역** — 다국어 지원 안 함
- **알림 (Notification)** — 미읽음·오프라인 알림은 v0.0.5+ 별도 Product
- **차단·신고 워크플로우** — v0.0.5+
- **Redis pub/sub 다중 인스턴스** — v0.0.5+ 트래픽 조건 만족 시
- **응답률·응답시간 계산 배치** — Product 12 Maker Profile 스코프 (본 Product는 원천 데이터만 제공)

## 대상 사용자
- **후원자 (Backer)** — 프로젝트 상세에서 "1:1 문의" · 채팅방 개설 · 질문 · REWARD_CARD 수신 → 후원 진입
- **메이커 (Maker)** — 후원자 문의 응답 · 리워드 카드 발송으로 후원 유도 · 프로젝트 카드로 다른 프로젝트 홍보
- **후속 SDD 작성자** — Maker Profile Product 12 · Notification (v0.0.5+)
- **운영자** — 브로드캐스트 실패 대응 · WS 연결 지표 관측
- **개발/QA** — WebSocket 통합 테스트 · Rich Message 스키마 검증

## 연결된 Epic 목록
- [ ] Epic 1: WebSocket 인프라 (`WebSocketConfig` + `StompAuthInterceptor` + `/ws/chat`)
- [ ] Epic 2: 도메인 3개 (`ChatRoom` · `ChatMessage` · `ChatRoomMember`) + 기본 CRUD
- [ ] Epic 3: Rich Message (JSON payload 4종 + `sendRewardCard` · `sendProjectCard` · `sendImage`)
- [ ] Epic 4: 읽음/안읽은수 + 타이핑 (STOMP 이벤트) + REST 7개 + 관측 + ADR

## 관련 문서
- **원본 이슈**: `workflows/task/fix/brainstorming/version/0.0.2v/issue-01-websocket-chat.md` (기본 골격) + `issue-12-chat-rich-message.md` (확장)
- **선행 SDD**: `product-wallet.md` · `product-project.md` · `product-reward.md` · `product-pledge.md`
- **후행 SDD**:
  - `product-maker.md` (Product 12 · 응답률·응답시간 배치 · 채팅 이력 원천 활용)
  - (v0.0.5+) `product-notification.md` (미읽음 · 오프라인 알림)
- **관련 이슈**:
  - `issue-09-reward-tier.md` — REWARD_CARD payload 원천
  - `issue-08-project-domain.md` — PROJECT_CARD payload · ChatRoom.projectId
  - `issue-10-pledge-state-machine.md` — REWARD_CARD 클릭 후 Pledge 진입
  - `issue-11-maker-profile-response-stats.md` — 응답률 계산 원천
- **벤치마크**:
  - Cabbage-Market-10 — STOMP + SimpleBroker + `StompAuthInterceptor` 정확 일치
  - Intercom · Slack · 카카오톡 플러스친구 — Rich Message · 읽음 · 타이핑 UX
- **신규 ADR 후보**:
  - "STOMP + SimpleBroker 채택 · Redis pub/sub v0.0.5+ 로드맵"
  - "ChatMessage content JSON 컬럼 · 5-state MessageType"
  - "Rich Message 발송 시점 스냅샷 (참조 무결성 X)"
  - "읽음 표시 = ChatRoomMember.last_read_message_id"
- **규범 갱신 예정**:
  - `workflows/backend-boundary/error-codes.md` CHAT001~005 · CHM001~003 매핑
  - `.claude/rules/consitency.md` §2 zone 매핑에 "채팅 zone (Event-converged 유사 · 저장 커밋 후 브로드캐스트)" 추가
- **CORS 설정**: `application.yml` allowedOrigins에 `/ws/chat` 엔드포인트 반영

## 열린 질문 (Open Questions)
- **다중 인스턴스 확장 트리거** — Redis pub/sub 도입 시점 · 활성 세션 수 (예: 500+) or 사용자 확장 (v0.0.5+)
- **메시지 페이지 크기** — 초기 50개 · 커서 기반 (`before={messageId}`) · UX 피드백 후 조정
- **SYSTEM 메시지 종류** — 초기 `ROOM_CREATED`만 · 향후 `PROJECT_LAUNCHED`, `REFUND_COMPLETED` 등 알림 통합 검토
- **채팅방 삭제 정책** — 초기 삭제 없음 (Soft Delete 필드만) · 완전 삭제는 GDPR 등 요구 시 v0.0.5+
- **REWARD_CARD 발송자 제한** — 메이커만? or 후원자도? · 초기 메이커만 (후원 유도가 목적)
- **IMAGE 최대 크기·타입** — 초기 5MB · JPG/PNG/WEBP · 별도 Media Product (이슈 #13)와 통합
- **오프라인 사용자에게 메시지 도달 알림** — 이메일 알림 or 푸시 · Notification Product (v0.0.5+)

## 제품 수준 완료 기준 (Product-level DoD)
- [ ] 모든 Epic DoD 통과
- [ ] E2E 시나리오 1: 후원자가 프로젝트 상세에서 "1:1 문의" → 채팅방 개설 → 텍스트 메시지 3회 → 메이커 응답 3회 → 읽음 표시 정확
- [ ] E2E 시나리오 2: 메이커가 REWARD_CARD 발송 → 후원자 클릭 → Pledge 생성 (Product 8과 통합 흐름)
- [ ] E2E 시나리오 3: 타이핑 인디케이터 실시간 전달 · 5초 이내 도착
- [ ] STOMP 인증 통합 테스트 (JWT 검증 · 유효/무효 토큰)
- [ ] Soft Delete 통합 테스트 (`deleted_at IS NULL` 자동 필터)
- [ ] 브로드캐스트 실패 시 로그 마커 확인 · 저장은 성공 검증
- [ ] ADR 최소 3건 발행
- [ ] `backend-boundary/error-codes.md` CHAT·CHM 매핑 완료
- [ ] 관측 지표 5개 프로덕션 노출 · `chat.ws.connection.gauge`가 Maker Profile Product 12에서 활용 가능

---

# [Epic 1] WebSocket 인프라 (`WebSocketConfig` + `StompAuthInterceptor` + `/ws/chat`)

## 목표
STOMP over WebSocket 인프라를 c1oud-mall에 도입하고, 기존 JWT 자산을 재사용한 `StompAuthInterceptor`로 CONNECT 시 인증을 강제하여 후속 Epic이 도메인·메시지에 집중할 수 있는 기반을 마련한다.

## 배경
- c1oud-mall은 STOMP·WebSocket 미도입 · Cabbage-Market-10 벤치마크 규범 그대로 적용
- 인증은 기존 JWT 재사용 · 새 인증 체계 도입 없음
- SimpleBroker 초기 배포 · v0.0.5+ Redis 전환 로드맵 명시

## 포함 Story
- Story 1-1: `WebSocketConfig` (`/ws/chat` 엔드포인트 · SimpleBroker · `/sub` · `/pub` prefix)
- Story 1-2: `StompAuthInterceptor` (CONNECT 프레임 JWT 검증 · `accessor.setUser`)
- Story 1-3: 통합 테스트 (STOMP client mock · CONNECT 성공/실패 · Principal 전달)

## Epic 인수 시나리오
- Given 유효 JWT · When STOMP CONNECT `/ws/chat` · Then `AuthenticatedUser`가 `Principal`로 세팅
- *(예외)* Given 유효하지 않은 JWT · When CONNECT · Then WS ERROR · `CHAT005`

## Epic 완료 기준 (DoD)
- [ ] 3개 Story 완료
- [ ] WS 통합 테스트 통과 (CONNECT · SEND · SUBSCRIBE)
- [ ] `application.yml` allowedOrigins 검증

---

## [Story 1-1] `WebSocketConfig` (엔드포인트 · 브로커 · prefix)

### User Story
- As a c1oud-mall 인프라 담당
- I want `WebSocketConfig`를 도입하여 `/ws/chat` STOMP 엔드포인트 · SimpleBroker · destination prefix 정의
- so that 클라이언트가 WebSocket으로 연결하고 메시지 발행/구독 가능

### 설명
- `@Configuration` + `@EnableWebSocketMessageBroker` + `WebSocketMessageBrokerConfigurer`
- 엔드포인트: `/ws/chat`
- CORS: dev `http://localhost:*` · prod (FE 도메인 · 이후 결정)
- SimpleBroker prefix: `/sub` (구독)
- Application destination prefix: `/pub` (발송)
- SockJS fallback: 활성 (구버전 브라우저 대응)

**핵심 파일**:
- `nbc.c1oud_mall.chat.infrastructure.WebSocketConfig`

**주요 클래스**:
```java
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthInterceptor stompAuthInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("http://localhost:*", "https://*.c1oud-mall.dev")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/sub");
        registry.setApplicationDestinationPrefixes("/pub");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthInterceptor);
    }
}
```

### 완료 기준 (AC)
- Given `WebSocketConfig` 등록 · When 앱 부팅 · Then WS 엔드포인트 `/ws/chat` 활성
- Given 클라이언트 CONNECT · When 성공 · Then 세션 성립 · SimpleBroker가 `/sub` 구독 처리
- Given 유효하지 않은 origin · When CONNECT · Then CORS 거부

### Definition of Done
- [ ] `WebSocketConfig` 구현
- [ ] `application.yml`에 `chat.allowed-origins` 설정 (프로파일별)
- [ ] 통합 테스트: 부팅 후 WS 엔드포인트 접근 가능 (WebSocketStompClient 사용)

### 스토리 포인트
1d

### 의존성
- 선행: 없음
- 후행: Story 1-2 · Epic 2

---

## [Story 1-2] `StompAuthInterceptor` (CONNECT JWT 검증)

### User Story
- As a Chat 인프라
- I want STOMP CONNECT 시 `Authorization: Bearer` 헤더에서 JWT를 파싱·검증하여 `Principal` 세팅
- so that 이후 SEND/SUBSCRIBE에서 인증된 사용자 참조 가능

### 설명
- `ChannelInterceptor.preSend` 오버라이드
- CONNECT 명령만 처리 (다른 명령은 통과)
- 기존 `JwtService` 재사용 (Auth 도메인 자산)
- 실패 시 `MessageDeliveryException` 또는 커스텀 예외 · 클라이언트는 WS ERROR 수신
- `AuthenticatedUser`를 `accessor.setUser()` — `Principal.getName()`으로 이후 참조 가능

**핵심 파일**:
- `nbc.c1oud_mall.chat.infrastructure.StompAuthInterceptor`

**주요 클래스**:
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
            message, StompHeaderAccessor.class);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String bearer = accessor.getFirstNativeHeader("Authorization");
            if (bearer == null || !bearer.startsWith("Bearer ")) {
                throw new BusinessException(ErrorCode.CHAT005);
            }
            String token = bearer.substring(7);
            try {
                AuthenticatedUser user = jwtService.verify(token);
                accessor.setUser(user);
                log.debug("STOMP CONNECT authenticated userId={}", user.userId());
            } catch (Exception e) {
                log.warn("STOMP CONNECT auth failed", e);
                throw new BusinessException(ErrorCode.CHAT005);
            }
        }
        return message;
    }
}
```

### 완료 기준 (AC)
- Given 유효 JWT · When CONNECT with `Authorization: Bearer {token}` · Then `Principal` 세팅 성공
- *(예외)* Given `Authorization` 헤더 없음 · When CONNECT · Then `CHAT005` · WS ERROR
- *(예외)* Given 유효하지 않은 JWT · When CONNECT · Then `CHAT005`
- Given 성공 후 SEND `/pub/rooms/1/messages` · When 수신 · Then `Principal`이 userId 반환

### Definition of Done
- [ ] `StompAuthInterceptor` 구현
- [ ] `ErrorCode.CHAT005` 등록
- [ ] 단위 테스트: 유효/무효 JWT · 헤더 없음
- [ ] 통합 테스트: CONNECT 성공 후 SEND에서 Principal 참조

### 스토리 포인트
1d

### 의존성
- 선행: Story 1-1
- 후행: Epic 2 · 3 · 4

---

## [Story 1-3] WebSocket 통합 테스트 인프라

### User Story
- As a QA
- I want STOMP 통합 테스트 인프라를 구축하여 CONNECT · SEND · SUBSCRIBE 자동화
- so that Chat 도메인 개발 시 회귀 방지

### 설명
- `WebSocketStompClient` + `StandardWebSocketClient` 조합
- 테스트 헬퍼: `stompConnect(jwt)` · `sendMessage(destination, payload)` · `subscribe(destination, handler)`
- `@SpringBootTest` + `@LocalServerPort` 조합

**핵심 파일**:
- `src/test/java/.../chat/support/StompTestClient.java`

### 완료 기준 (AC)
- Given 통합 테스트 · When `stompConnect(validJwt)` · Then 성공 · 세션 반환
- Given `stompConnect(invalidJwt)` · Then 실패 · 예외
- Given `sendMessage · subscribe` · Then 브로드캐스트 수신

### Definition of Done
- [ ] `StompTestClient` 헬퍼
- [ ] 예시 통합 테스트 (CONNECT · SEND · SUBSCRIBE 각각)

### 스토리 포인트
1d

### 의존성
- 선행: Story 1-1 · 1-2
- 후행: Epic 2 · 3 · 4의 통합 테스트

---

# [Epic 2] 도메인 3개 (`ChatRoom` · `ChatMessage` · `ChatRoomMember`) + 기본 CRUD

## 목표
크라우드 펀딩 재해석 반영한 `ChatRoom` Aggregate · Soft Delete 적용 `ChatMessage` · 참여자별 상태 관리 `ChatRoomMember` 3개 도메인을 구축하여 후속 Epic이 메시지·리치·읽음 기능을 확장할 수 있는 기반을 마련한다.

## 배경
- 이슈 #01 재해석: `buyerId → backerUserId`, `sellerId → makerUserId`, `itemId → projectId`
- 이슈 #12: `ChatRoomMember` 신규 추가 (참여자 상태 관리)
- Soft Delete는 `@SQLDelete` + `@SQLRestriction` 조합 (Cabbage 벤치마크)

## 포함 Story
- Story 2-1: `ChatRoom` 엔티티 + `verifyParticipant` · `markLastMessage` 도메인 메서드
- Story 2-2: `ChatMessage` 엔티티 (JSON content · 5-state MessageType · SoftDelete)
- Story 2-3: `ChatRoomMember` 엔티티 (참여자별 상태 · `markRead` 도메인 메서드)
- Story 2-4: 3개 Repository + 기본 CRUD + `ErrorCode.CHAT001~004`

## Epic 완료 기준 (DoD)
- [ ] 4개 Story 완료
- [ ] 3개 엔티티 · Repository 완결
- [ ] Soft Delete 자동 필터 검증
- [ ] `ErrorCode.CHAT001~004` 등록

---

## [Story 2-1] `ChatRoom` Aggregate + 도메인 메서드

### User Story
- As a Chat 도메인 개발자
- I want 크라우드 펀딩 재해석 반영한 `ChatRoom` Aggregate와 참여자 검증·최근 메시지 갱신 도메인 메서드
- so that 채팅방 개설·참여자 검증·정렬 UX 안전

### 설명
- 필드:
  - `id BIGINT` · `room_uuid VARCHAR(36) UNIQUE` (외부 노출용 논리 채번)
  - `backer_user_id BIGINT NOT NULL`
  - `maker_user_id BIGINT NOT NULL`
  - `project_id BIGINT NULL` (프로젝트 무관 문의 허용)
  - `last_message_at TIMESTAMP NULL` (정렬용)
- UNIQUE: `(backer_user_id, maker_user_id, project_id)` — 재개설 시 기존 반환
- 정적 팩토리 `ChatRoom.create(backerId, makerId, projectId)` — `room_uuid` 자동 생성 (`UUID.randomUUID()`)
- 도메인 메서드:
  - `verifyParticipant(userId)` — backer 또는 maker · 위반 시 `CHAT002`
  - `markLastMessage(LocalDateTime at)` — `last_message_at` 갱신

**핵심 파일**:
- `nbc.c1oud_mall.chat.domain.ChatRoom`

**주요 스키마**:
```sql
CREATE TABLE chat_room (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  room_uuid         VARCHAR(36)  NOT NULL,
  backer_user_id    BIGINT       NOT NULL,
  maker_user_id     BIGINT       NOT NULL,
  project_id        BIGINT       NULL,
  last_message_at   TIMESTAMP    NULL,
  created_at        TIMESTAMP    NOT NULL,
  updated_at        TIMESTAMP    NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_room_uuid (room_uuid),
  UNIQUE KEY uk_chat_room_participants (backer_user_id, maker_user_id, project_id),
  KEY idx_chat_room_backer_last (backer_user_id, last_message_at DESC),
  KEY idx_chat_room_maker_last (maker_user_id, last_message_at DESC),
  KEY idx_chat_room_project (project_id)
);
```

**엔티티**:
```java
@Entity
@Table(name = "chat_room")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class ChatRoom extends BaseEntity {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 36)
    private String roomUuid;

    @Column(nullable = false)
    private Long backerUserId;

    @Column(nullable = false)
    private Long makerUserId;

    @Column
    private Long projectId;   // nullable

    @Column
    private LocalDateTime lastMessageAt;

    public static ChatRoom create(Long backerId, Long makerId, Long projectId) {
        if (Objects.equals(backerId, makerId))
            throw new BusinessException(ErrorCode.CHAT002);   // 자기 자신에게 문의 불가
        ChatRoom room = new ChatRoom();
        room.roomUuid = UUID.randomUUID().toString();
        room.backerUserId = backerId;
        room.makerUserId = makerId;
        room.projectId = projectId;
        return room;
    }

    public void verifyParticipant(Long userId) {
        if (!Objects.equals(userId, backerUserId) && !Objects.equals(userId, makerUserId))
            throw new BusinessException(ErrorCode.CHAT002);
    }

    public void markLastMessage(LocalDateTime at) {
        this.lastMessageAt = at;
    }
}
```

### 완료 기준 (AC)
- Given 유효 파라미터 · When `ChatRoom.create(1, 2, 100)` · Then 인스턴스 반환 · `room_uuid` 자동 생성
- *(예외)* Given `backerId == makerId` · When `create` · Then `CHAT002`
- Given ChatRoom(backer=1, maker=2, project=100) · When `verifyParticipant(3)` · Then `CHAT002`
- Given `verifyParticipant(1)` or `verifyParticipant(2)` · Then 성공

### Definition of Done
- [ ] `ChatRoom` 엔티티 · 정적 팩토리 · 2개 도메인 메서드
- [ ] DDL 확인 (JPA · dev H2)
- [ ] 단위 테스트

### 스토리 포인트
1d

### 의존성
- 선행: Epic 1
- 후행: Story 2-2 · 2-3

---

## [Story 2-2] `ChatMessage` (JSON content · 5-state MessageType · SoftDelete)

### User Story
- As a Chat 도메인 개발자
- I want `ChatMessage` 엔티티에 JSON content · `MessageType` 5-state enum · SoftDelete 적용
- so that Rich Message 확장 · 삭제 이력 감사 · Cabbage 벤치마크 준수

### 설명
- 필드:
  - `id BIGINT` · `chat_room_id BIGINT FK` · `sender_user_id BIGINT`
  - `message_type VARCHAR(30) NOT NULL` (`TEXT` · `REWARD_CARD` · `PROJECT_CARD` · `IMAGE` · `SYSTEM`)
  - `content JSON NOT NULL` (payload)
  - `deleted_at TIMESTAMP NULL` (Soft Delete)
- Soft Delete: `@SQLDelete(sql = "UPDATE chat_message SET deleted_at = NOW() WHERE id = ?")` + `@SQLRestriction("deleted_at IS NULL")`
- 정적 팩토리 `ChatMessage.of(roomId, senderId, type, contentJson)`
- 도메인 메서드:
  - `verifyOwnership(userId)` — 위반 시 `CHAT004` (삭제 시)

**핵심 스키마**:
```sql
CREATE TABLE chat_message (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  chat_room_id      BIGINT       NOT NULL,
  sender_user_id    BIGINT       NOT NULL,
  message_type      VARCHAR(30)  NOT NULL,
  content           JSON         NOT NULL,
  deleted_at        TIMESTAMP    NULL,
  created_at        TIMESTAMP    NOT NULL,
  updated_at        TIMESTAMP    NOT NULL,
  PRIMARY KEY (id),
  KEY idx_chat_message_room_created (chat_room_id, created_at DESC),
  KEY idx_chat_message_deleted (deleted_at)
);
```

**엔티티**:
```java
@Entity
@Table(name = "chat_message")
@SQLDelete(sql = "UPDATE chat_message SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class ChatMessage extends BaseEntity {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long chatRoomId;

    @Column(nullable = false)
    private Long senderUserId;

    @Enumerated(STRING)
    @Column(nullable = false, length = 30)
    private MessageType messageType;

    @Column(nullable = false, columnDefinition = "JSON")
    private String content;   // JSON 문자열

    @Column
    private LocalDateTime deletedAt;

    public static ChatMessage of(Long roomId, Long senderId, MessageType type, String contentJson) {
        ChatMessage msg = new ChatMessage();
        msg.chatRoomId = roomId;
        msg.senderUserId = senderId;
        msg.messageType = type;
        msg.content = contentJson;
        return msg;
    }

    public void verifyOwnership(Long userId) {
        if (!Objects.equals(this.senderUserId, userId))
            throw new BusinessException(ErrorCode.CHAT004);
    }
}

public enum MessageType {
    TEXT, REWARD_CARD, PROJECT_CARD, IMAGE, SYSTEM
}
```

### 완료 기준 (AC)
- Given 유효 파라미터 · When `ChatMessage.of(1, 100, TEXT, "{\"text\":\"hi\"}")` · Then 저장 성공
- Given 저장 후 `messageRepository.delete(msg)` · When 조회 · Then Soft Delete 자동 필터 · Optional.empty()
- Given DB에 `deleted_at != null` 행 · When native 조회 · Then 존재하나 JPA 조회는 안 됨

### Definition of Done
- [ ] `ChatMessage` 엔티티 · SoftDelete
- [ ] `MessageType` enum
- [ ] 단위 테스트: 정적 팩토리 · Soft Delete 필터
- [ ] `ErrorCode.CHAT003 · CHAT004` 등록

### 스토리 포인트
1d

### 의존성
- 선행: Story 2-1
- 후행: Epic 3

---

## [Story 2-3] `ChatRoomMember` + `markRead` 도메인 메서드

### User Story
- As a Chat 도메인 개발자
- I want 참여자별 상태 관리 엔티티 `ChatRoomMember` · `markRead(msgId)` 도메인 메서드
- so that 참여자별 안 읽은 수 계산 · 상대방 읽음 표시 UX 지원

### 설명
- 필드:
  - `chat_room_id BIGINT · user_id BIGINT` (복합 PK)
  - `role VARCHAR(20)` — `BACKER` or `MAKER` (이슈 #12 재해석 반영)
  - `last_read_message_id BIGINT NULL`
  - `joined_at TIMESTAMP`
- 도메인 메서드:
  - `markRead(Long messageId)` — 기존 값보다 큰 경우만 갱신 (역행 방지)
  - `getUnreadThreshold()` — 안 읽은 수 계산의 임계값 반환

**핵심 스키마**:
```sql
CREATE TABLE chat_room_member (
  chat_room_id           BIGINT       NOT NULL,
  user_id                BIGINT       NOT NULL,
  role                   VARCHAR(20)  NOT NULL,
  last_read_message_id   BIGINT       NULL,
  joined_at              TIMESTAMP    NOT NULL,
  PRIMARY KEY (chat_room_id, user_id),
  KEY idx_chat_member_user (user_id, last_read_message_id)
);
```

**엔티티**:
```java
@Entity
@IdClass(ChatRoomMemberId.class)
@Table(name = "chat_room_member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class ChatRoomMember {
    @Id private Long chatRoomId;
    @Id private Long userId;

    @Enumerated(STRING)
    @Column(nullable = false, length = 20)
    private ChatRoomRole role;

    @Column
    private Long lastReadMessageId;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    public static ChatRoomMember join(Long roomId, Long userId, ChatRoomRole role) {
        ChatRoomMember m = new ChatRoomMember();
        m.chatRoomId = roomId;
        m.userId = userId;
        m.role = role;
        m.joinedAt = LocalDateTime.now();
        return m;
    }

    public void markRead(Long messageId) {
        if (lastReadMessageId == null || messageId > lastReadMessageId)
            this.lastReadMessageId = messageId;
    }
}

public enum ChatRoomRole { BACKER, MAKER }

public record ChatRoomMemberId(Long chatRoomId, Long userId) implements Serializable {}
```

### 완료 기준 (AC)
- Given `ChatRoomMember(lastReadMessageId=null)` · When `markRead(10)` · Then `lastReadMessageId=10`
- Given `lastReadMessageId=10` · When `markRead(5)` · Then `lastReadMessageId=10` (역행 방지)
- Given `lastReadMessageId=10` · When `markRead(20)` · Then `lastReadMessageId=20`

### Definition of Done
- [ ] `ChatRoomMember` 엔티티 · `@IdClass`
- [ ] `ChatRoomRole` enum
- [ ] `ChatRoomMemberId` record
- [ ] 단위 테스트: `markRead` 정방향/역방향

### 스토리 포인트
0.5d

### 의존성
- 선행: Story 2-1
- 후행: Epic 4 (읽음 흐름)

---

## [Story 2-4] 3개 Repository + `ErrorCode.CHAT001~004` + 조회 쿼리

### User Story
- As a Chat 서비스
- I want 3개 Repository · 목록/커서 조회 쿼리 · 4개 ErrorCode
- so that Service에서 조회·검증 조합 가능

### 설명
- `ChatRoomRepository` — `findByRoomUuid` · `findByBackerAndMakerAndProject` · `findByUserIdOrderByLastMessage`
- `ChatMessageRepository` — `findByRoomIdCursor` (커서 기반 · `before={id}`) · `countUnread` 등
- `ChatRoomMemberRepository` — `findByRoomIdAndUserId`
- `ErrorCode.CHAT001~004`

**주요 메서드**:
```java
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    Optional<ChatRoom> findByRoomUuid(String roomUuid);

    @Query("SELECT r FROM ChatRoom r " +
           "WHERE r.backerUserId = :backer AND r.makerUserId = :maker " +
           "AND (r.projectId = :projectId OR (r.projectId IS NULL AND :projectId IS NULL))")
    Optional<ChatRoom> findByParticipants(@Param("backer") Long backerId,
                                          @Param("maker") Long makerId,
                                          @Param("projectId") Long projectId);

    @Query("SELECT r FROM ChatRoom r " +
           "WHERE r.backerUserId = :userId OR r.makerUserId = :userId " +
           "ORDER BY r.lastMessageAt DESC")
    Page<ChatRoom> findByUserId(@Param("userId") Long userId, Pageable pageable);
}

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    @Query("SELECT m FROM ChatMessage m WHERE m.chatRoomId = :roomId " +
           "AND (:before IS NULL OR m.id < :before) " +
           "ORDER BY m.id DESC")
    List<ChatMessage> findByRoomIdCursor(@Param("roomId") Long roomId,
                                          @Param("before") Long before,
                                          Pageable pageable);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.chatRoomId = :roomId " +
           "AND (:lastReadId IS NULL OR m.id > :lastReadId)")
    long countUnread(@Param("roomId") Long roomId, @Param("lastReadId") Long lastReadId);
}
```

### 완료 기준 (AC)
- Given 3개 채팅방 저장 · When `findByUserId(1)` · Then 사용자 참여 방 반환 · lastMessageAt 순
- Given 커서 조회 · When `findByRoomIdCursor(1, 100, PageRequest.of(0, 50))` · Then id < 100 최근 50건

### Definition of Done
- [ ] 3개 Repository
- [ ] 커서 조회 · 안 읽은 수 쿼리
- [ ] `ErrorCode.CHAT001~004` 등록 (Chat 섹션 주석 구분선)
- [ ] `@DataJpaTest` 슬라이스

### 스토리 포인트
1d

### 의존성
- 선행: Story 2-1·2-2·2-3
- 후행: Epic 3·4

---

# [Epic 3] Rich Message (JSON payload 4종 + 3개 발송 메서드)

## 목표
Rich Message 확장 · JSON payload 4종 (REWARD_CARD · PROJECT_CARD · IMAGE · SYSTEM) 스키마 정의 · 발송 메서드 · STOMP 브로드캐스트 · Product 7·8과의 결합을 통해 채팅에서 후원 진입 UX를 성립시킨다.

## 배경
- 이슈 #12 확정 · REWARD_CARD 발송이 채팅→후원의 브릿지
- 페이로드 검증(스키마 위반 시 `CHM002`) · 참조 엔티티 존재 검증(`CHM003`)
- 발송 시점 스냅샷 원칙 (참조 무결성 X)

## 포함 Story
- Story 3-1: `RewardCardPayload · ProjectCardPayload · ImagePayload · SystemPayload` record + JSON 직렬화
- Story 3-2: `ChatMessageService.sendText · sendRewardCard · sendProjectCard · sendImage · sendSystem`
- Story 3-3: `ErrorCode.CHM001~003` + payload 스키마 검증 (Jackson)

## Epic 인수 시나리오
- Given Maker가 REWARD_CARD 발송 요청 · rewardId=42 유효 · When `sendRewardCard` · Then payload 스냅샷 저장 · STOMP 브로드캐스트
- *(예외)* Given `rewardId=999` 없음 · Then `CHM003`

## Epic 완료 기준 (DoD)
- [ ] 3개 Story 완료
- [ ] Rich Message 발송 통합 테스트 (5개 타입)
- [ ] 페이로드 스키마 검증

---

## [Story 3-1] Payload record 4종 + JSON 직렬화

### User Story
- As a Chat 도메인 개발자
- I want 4개 payload record 정의 · Jackson 직렬화·역직렬화
- so that `MessageType`별 스키마 명확·타입 안전

### 설명
- 4개 record (application layer):
  ```java
  public record RewardCardPayload(Long rewardId, Long projectId, String title, long price, String description, String thumbnailUrl) {}
  public record ProjectCardPayload(Long projectId, String title, String coverUrl, int progressPct, int dDay, int backerCount) {}
  public record ImagePayload(String imageUrl, int width, int height) {}
  public record SystemPayload(String systemType, String text) {}
  public record TextPayload(String text) {}   // 통일성 위해 포함
  ```
- Jackson 직렬화 유틸: `PayloadJsonMapper.toJson(payload)` · `fromJson(json, type)`

**핵심 파일**:
- `nbc.c1oud_mall.chat.application.dto.payload.RewardCardPayload` (record)
- 나머지 4개 record
- `nbc.c1oud_mall.chat.application.PayloadJsonMapper`

### 완료 기준 (AC)
- Given `RewardCardPayload(42, 100, "Early Bird", 35000, "...", null)` · When `toJson()` · Then JSON 문자열
- Given JSON 문자열 · When `fromJson(json, RewardCardPayload.class)` · Then record 복원

### Definition of Done
- [ ] 5개 record
- [ ] `PayloadJsonMapper` 유틸
- [ ] 단위 테스트: 직렬화·역직렬화 왕복

### 스토리 포인트
0.5d

### 의존성
- 선행: Epic 2
- 후행: Story 3-2

---

## [Story 3-2] `ChatMessageService` 발송 메서드 5종

### User Story
- As a Chat 서비스
- I want 텍스트·리워드 카드·프로젝트 카드·이미지·시스템 5종 발송 메서드
- so that REST 진입점 · WebSocket 진입점에서 통합 호출 가능

### 설명
- 5개 메서드 · 모두 검증 → 저장 → 브로드캐스트 흐름
- `sendRewardCard` · `sendProjectCard`는 외부 엔티티 조회 · 페이로드 스냅샷 생성
- STOMP 브로드캐스트: `simpMessagingTemplate.convertAndSend("/sub/rooms/{roomId}/messages", messageResponse)`

**핵심 파일**:
- `nbc.c1oud_mall.chat.application.ChatMessageService`

**주요 메서드**:
```java
@Service
@RequiredArgsConstructor
@Transactional
public class ChatMessageService {
    private final ChatRoomRepository roomRepository;
    private final ChatMessageRepository messageRepository;
    private final RewardTierRepository rewardTierRepository;
    private final ProjectRepository projectRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PayloadJsonMapper payloadMapper;
    private final MeterRegistry meterRegistry;

    public ChatMessage sendText(Long userId, Long roomId, String text) {
        ChatRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT001));
        room.verifyParticipant(userId);

        String contentJson = payloadMapper.toJson(new TextPayload(text));
        ChatMessage msg = ChatMessage.of(roomId, userId, MessageType.TEXT, contentJson);
        messageRepository.save(msg);
        room.markLastMessage(LocalDateTime.now());

        broadcast(roomId, msg);
        meterRegistry.counter("chat.message.total", "type", "TEXT").increment();
        return msg;
    }

    public ChatMessage sendRewardCard(Long userId, Long roomId, Long rewardId) {
        ChatRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT001));
        room.verifyParticipant(userId);

        RewardTier reward = rewardTierRepository.findById(rewardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHM003));

        RewardCardPayload payload = new RewardCardPayload(
            reward.getId(), reward.getProjectId(), reward.getTitle(),
            reward.getPrice(), reward.getDescription(), null   // thumbnailUrl 향후
        );
        String contentJson = payloadMapper.toJson(payload);

        ChatMessage msg = ChatMessage.of(roomId, userId, MessageType.REWARD_CARD, contentJson);
        messageRepository.save(msg);
        room.markLastMessage(LocalDateTime.now());

        broadcast(roomId, msg);
        meterRegistry.counter("chat.message.total", "type", "REWARD_CARD").increment();
        return msg;
    }

    // sendProjectCard · sendImage · sendSystem 동일 패턴

    public void softDelete(Long userId, Long messageId) {
        ChatMessage msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT003));
        msg.verifyOwnership(userId);
        messageRepository.delete(msg);   // @SQLDelete 트리거
    }

    private void broadcast(Long roomId, ChatMessage msg) {
        try {
            messagingTemplate.convertAndSend("/sub/rooms/" + roomId + "/messages",
                                              ChatMessageResponse.from(msg));
        } catch (Exception e) {
            log.warn("CHAT_BROADCAST_FAILED roomId={} messageId={}", roomId, msg.getId(), e);
            // 저장은 성공 · 실시간 실패는 재조회 API로 회복 가능
        }
    }
}
```

### 완료 기준 (AC)
- Given 유효 참여자 · When `sendText(userId, roomId, "hi")` · Then 저장 · `markLastMessage` · 브로드캐스트 시도
- Given `rewardId=42` 존재 · When `sendRewardCard(userId, roomId, 42)` · Then payload 스냅샷 저장
- *(예외)* Given `rewardId=999` · Then `CHM003`
- *(예외)* Given 참여자 아님 · Then `CHAT002`
- Given 본인 메시지 · When `softDelete(userId, msgId)` · Then Soft Delete
- *(예외)* Given 타인 메시지 · When `softDelete` · Then `CHAT004`

### Definition of Done
- [ ] `ChatMessageService` 5개 발송 + softDelete
- [ ] `ChatMessageResponse` (record · 응답 DTO)
- [ ] 통합 테스트: 각 타입 발송 · Soft Delete
- [ ] 브로드캐스트 실패 시 로그 마커 검증

### 스토리 포인트
1.5d

### 의존성
- 선행: Story 3-1 · Epic 2 · Product 7 · Product 6
- 후행: Epic 4

---

## [Story 3-3] `ErrorCode.CHM001~003` + Payload 스키마 검증

### User Story
- As a Chat 서비스
- I want 페이로드 스키마 위반·타입 불일치·참조 없음을 명확한 ErrorCode로 응답
- so that 클라이언트가 문제 진단 가능

### 설명
- `ErrorCode.CHM001~003` 등록
- Jackson 파싱 실패 시 `CHM002` 변환

### 완료 기준 (AC)
- Given ErrorCode 등록 · When `errorCode.getCode()` · Then "CHM001" · "CHM002" · "CHM003"
- Given 유효하지 않은 JSON · When `payloadMapper.fromJson` · Then `CHM002`

### Definition of Done
- [ ] 3개 ErrorCode 등록
- [ ] `PayloadJsonMapper` 예외 변환
- [ ] 단위 테스트

### 스토리 포인트
0.5d

### 의존성
- 선행: Story 3-1
- 후행: Epic 4

---

# [Epic 4] 읽음/타이핑 + REST 7개 + WS 3개 + 관측 + ADR

## 목표
읽음 표시·타이핑 인디케이터 STOMP 이벤트·REST 7개 엔드포인트·5개 관측 지표·3건 ADR로 채팅 도메인 완결.

## 포함 Story
- Story 4-1: `ChatRoomService.createOrGet` · `getMyRooms` · REST 3개
- Story 4-2: `ChatMessageService.markRead` · `getUnreadCount` · REST 2개 + `/sub/rooms/{id}/read` 브로드캐스트
- Story 4-3: `ChatWebSocketController` (SEND `/pub/rooms/{id}/messages` · `/typing` · Rich Message 발송 REST 3개)
- Story 4-4: 관측 5개 + ADR 3건 + `backend-boundary/error-codes.md` 갱신

## Epic 완료 기준 (DoD)
- [ ] 4개 Story 완료
- [ ] REST 7개 · WS 3개 · 관측 5개 통합 테스트
- [ ] ADR 3건 발행

---

## [Story 4-1] `ChatRoomService.createOrGet` + `getMyRooms` + REST 3개

### User Story
- As a 후원자
- I want 채팅방 개설 (idempotent) · 내 채팅방 목록 조회 · 특정 방 정보 조회
- so that 프로젝트 상세에서 "1:1 문의" · 마이페이지 채팅 목록

### 설명
- `ChatRoomService.createOrGet(backerId, makerId, projectId)` — UNIQUE 조합 검증 · 기존이면 반환 · 신규면 저장 + ChatRoomMember 2건 저장 + SYSTEM 메시지 발송
- REST 3개:
  - `POST /api/v1/chat-rooms` — 개설 or 기존 반환 · body `{makerUserId, projectId?}`
  - `GET /api/v1/chat-rooms/my?page=&size=` — 내 참여 방 목록 · lastMessageAt 순
  - `GET /api/v1/chat-rooms/{roomId}` — 방 정보 (참여자 검증)

### 완료 기준 (AC)
- Given 최초 요청 · When `POST /chat-rooms` · Then 201 · 신규 방 · SYSTEM 메시지 자동
- Given 재요청 · Then 200 · 기존 방 반환 (idempotent)
- *(예외)* Given 자기 자신 · Then 400 · `CHAT002`
- Given `GET /my` · Then 참여 방 목록 · lastMessageAt DESC

### Definition of Done
- [ ] `ChatRoomService.createOrGet · getMyRooms · getRoom`
- [ ] `ChatController` REST 3개
- [ ] Request/Response DTO
- [ ] 통합 테스트: idempotent · 목록 · 참여자 검증

### 스토리 포인트
1.5d

### 의존성
- 선행: Epic 2·3
- 후행: 없음

---

## [Story 4-2] `markRead` + `getUnreadCount` + REST 2개

### User Story
- As a 후원자·메이커
- I want 읽음 표시 갱신 · 안 읽은 수 조회
- so that 채팅 목록에 미읽음 배지 · 상대방에게 읽음 상태 실시간 전달

### 설명
- `POST /api/v1/chat-rooms/{roomId}/read` body `{lastMessageId}` — `ChatRoomMember.markRead` + `/sub/rooms/{id}/read` 브로드캐스트
- `GET /api/v1/chat-rooms/{roomId}/unread-count` — `messageRepository.countUnread(roomId, lastReadId)`

### 완료 기준 (AC)
- Given 참여자 · When `POST /read {lastMessageId: 42}` · Then `ChatRoomMember.lastReadMessageId=42` · 브로드캐스트
- Given `lastReadMessageId=42` · When `GET /unread-count` · Then 42 이후 메시지 수 반환

### Definition of Done
- [ ] `markRead · getUnreadCount` Service
- [ ] REST 2개
- [ ] `/sub/rooms/{id}/read` STOMP 브로드캐스트
- [ ] 통합 테스트

### 스토리 포인트
1d

### 의존성
- 선행: Story 4-1
- 후행: 없음

---

## [Story 4-3] `ChatWebSocketController` + Rich Message 발송 REST 3개

### User Story
- As a 후원자·메이커
- I want WebSocket으로 텍스트 메시지 발송 · 타이핑 이벤트 · REST로 Rich Message 발송
- so that 실시간 대화 · 리워드 카드 유도

### 설명
- WebSocket:
  - `@MessageMapping("/rooms/{roomId}/messages")` — TEXT 발송
  - `@MessageMapping("/rooms/{roomId}/typing")` — 타이핑 통과
- REST:
  - `POST /api/v1/chat-rooms/{roomId}/messages/reward-card` body `{rewardId}` — REWARD_CARD 발송
  - `POST /api/v1/chat-rooms/{roomId}/messages/project-card` body `{projectId}` — PROJECT_CARD 발송
  - `DELETE /api/v1/chat-messages/{messageId}` — Soft Delete (본인만)

**핵심 파일**:
- `nbc.c1oud_mall.chat.presentation.ChatWebSocketController`

**주요 메서드**:
```java
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {
    private final ChatMessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/rooms/{roomId}/messages")
    public void handleMessage(@DestinationVariable Long roomId,
                              @Payload SendMessageWs message,
                              Principal principal) {
        Long userId = extractUserId(principal);
        messageService.sendText(userId, roomId, message.text());
        // sendText 안에서 브로드캐스트
    }

    @MessageMapping("/rooms/{roomId}/typing")
    public void handleTyping(@DestinationVariable Long roomId, Principal principal) {
        Long userId = extractUserId(principal);
        // 참여자 검증 후 브로드캐스트만 · 저장 X
        messagingTemplate.convertAndSend("/sub/rooms/" + roomId + "/typing",
                                          new TypingEvent(userId, LocalDateTime.now()));
    }
}
```

### 완료 기준 (AC)
- Given WS 연결 · When SEND `/pub/rooms/1/messages {text:"hi"}` · Then 저장 + 브로드캐스트
- Given SEND `/pub/rooms/1/typing` · Then 저장 없음 · 브로드캐스트만
- Given 메이커 · When `POST /messages/reward-card {rewardId:42}` · Then REWARD_CARD 저장 · 브로드캐스트
- *(예외)* Given `rewardId=999` · Then 404 · `CHM003`
- Given 본인 메시지 · When `DELETE /chat-messages/{id}` · Then 204

### Definition of Done
- [ ] `ChatWebSocketController` WS 2개 destination
- [ ] REST 3개 (reward-card · project-card · delete)
- [ ] `@WebMvcTest` + WebSocket 통합 테스트
- [ ] 브로드캐스트 동작 확인 (WebSocketStompClient · Story 1-3 유틸 활용)

### 스토리 포인트
1.5d

### 의존성
- 선행: Story 3-2 · 4-1
- 후행: 없음

---

## [Story 4-4] 관측 5개 + ADR 3건 + 규범 갱신

### User Story
- As a 팀 리더 · 운영자
- I want Chat 관측 5개 · ADR 3건 · `backend-boundary/error-codes.md` 갱신
- so that 채팅 도메인 정책·규범 확립 · 후속 Maker Profile Product 12에서 참조

### 설명
- 지표 5개 (§관측 지표 참조)
- ADR 3건:
  - `018-chat-stomp-simple-broker-and-redis-roadmap.md`
  - `019-chat-message-json-content-and-message-types.md`
  - `020-chat-rich-message-snapshot-payload.md`
- 규범 갱신:
  - `workflows/backend-boundary/error-codes.md`: CHAT001~005 · CHM001~003 UX 매핑
  - `.claude/rules/consitency.md` §2 zone 매핑에 "채팅 zone (Event-converged 유사 · 저장 커밋 후 브로드캐스트)" 추가

### 완료 기준 (AC)
- Given 프로덕션 · `curl /actuator/prometheus` · Then 5개 지표 노출 (`chat.*`)
- Given ADR 3건 · When 확인 · Then 완결
- Given `backend-boundary/error-codes.md` · Then CHAT · CHM 매핑 존재

### Definition of Done
- [ ] 5개 지표 counter/gauge 등록
- [ ] ADR 3건 파일
- [ ] `backend-boundary/error-codes.md` 갱신
- [ ] `.claude/rules/consitency.md` §2 zone 매핑 갱신

### 스토리 포인트
1d

### 의존성
- 선행: Epic 1~3
- 후행: 없음 (Product-level 완결)

---

## 요약

| Epic | Story | SP 합계 |
|---|---|---|
| Epic 1: WebSocket 인프라 | 3 | 3.0 |
| Epic 2: 도메인 3개 | 4 | 3.5 |
| Epic 3: Rich Message | 3 | 2.5 |
| Epic 4: 읽음/타이핑·REST·관측·ADR | 4 | 5.0 |
| **합계** | **14** | **14.0 SP** |

**진행 순서 (필수)**: Epic 1 → 2 → 3 → 4
- Epic 1은 WebSocket 인프라 전제 (Epic 2~4 전체)
- Epic 2 완료 후 Epic 3·4 병렬 가능
- Epic 4는 규범 굳힘 · 후속 Maker Profile Product 12로 이관

## 채팅 도메인 완결 → Maker Profile Product 12 앞선 완결

본 Product 9(Chat) 완결 시 크라우드 펀딩 UX의 핵심 소통 채널 성립. Product 12(Maker Profile)의 응답률·응답시간 배치 계산이 이 채팅 이력을 원천으로 삼음 · 이후 Product 12 진입 시 참조 지점 명확.
