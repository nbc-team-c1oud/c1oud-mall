# Issue: Chat Rich Message · 프로젝트 컨텍스트 · 읽음/타이핑 (이슈 #01 확장)

## 배경

> **디자인 발견 (Round 5)**: 채팅 페이지가 예상보다 정교 — 리워드 카드 발송 · 프로젝트 컨텍스트 embed · 읽음/타이핑 · 빠른 답변.

- 디자인(`메이커 채팅.html`) 확인 요소:
  - **card-bubble** (메시지 안에 리워드 카드 · "이 리워드로 후원" 버튼)
  - **프로젝트 컨텍스트 embed** (스레드 상단에 프로젝트 썸네일 + 달성률 + D-day + "후원 검토중")
  - **읽음 표시** (out 메시지에 "· 읽음")
  - **안 읽은 수** (대화 목록에 unread 배지)
  - **타이핑 인디케이터** (3개 점 애니메이션)
  - **빠른 답변** (칩 버튼: 리워드 문의 · 배송 일정 · 환불 정책 · 셀프호스팅)
  - **첨부** (이미지 · 파일)
- 이슈 #01은 STOMP + SoftDelete만 담당 · 위 UX 요소는 본 이슈 스코프

## 조사 결과 — 카카오톡 · Slack · Discord · Intercom 벤치마크

| UX 요소 | 카카오톡 | Slack | Discord | Intercom | c1oud-mall |
|---|---|---|---|---|---|
| 리치 메시지 (카드) | 있음 (플러스친구) | 있음 (Block Kit) | Embed | Cards | 채택 (**리워드·프로젝트 카드**) |
| 읽음 표시 | 안 읽은 수 | ✓ 아이콘 | 없음 | 있음 | 채택 (양방향) |
| 타이핑 인디케이터 | 있음 | 있음 | 있음 | 있음 | 채택 (STOMP 이벤트) |
| 안 읽은 수 | 있음 | 있음 | 있음 | 있음 | 채택 (대화 목록) |
| 빠른 답변 | 없음 | 없음 | 없음 | 있음 (Bots) | 채택 (**FE만 · 서버 저장 X**) |
| 프로젝트 컨텍스트 | 없음 | 없음 | 없음 | 있음 (컨택트) | 채택 (**ChatRoom.projectId FK**) |

## 옵션 비교

### 갈림길 1: 메시지 컨텐츠 저장 방식

**Option A (채택) — 메시지 타입 enum 확장 + payload JSON**
- `MessageType`: `TEXT` (기존) · **`REWARD_CARD`** · **`PROJECT_CARD`** · **`IMAGE`** (기존) · **`SYSTEM`**
- `content` 컬럼: TEXT · JSON 문자열 (타입별 스키마)
- 장점: 확장 가능 · 스키마 유지 · 신입에 이해 쉬움
- FE는 타입 기반 컴포넌트 렌더링

**Option B — 별도 테이블 (텍스트 · 카드 · 이미지 각각)**
- 거부 이유: 조회 복잡 · JOIN 부담

### 갈림길 2: 프로젝트 컨텍스트 저장 위치

**Option A (채택) — `ChatRoom.projectId` (nullable · FK)**
- 채팅방 개설 시 프로젝트 컨텍스트 결정 (프로젝트 상세 → 문의 버튼)
- 대화 중 프로젝트 정보 조회 = ChatRoom.projectId → Project 조인
- 프로젝트 없는 문의(일반)는 nullable

**Option B — 메시지마다 projectId embed**
- 거부 이유: 중복 저장 · 스레드 전체 프로젝트 컨텍스트 반영 어려움

### 갈림길 3: 읽음 표시 저장

**Option A (채택) — `ChatRoomMember.last_read_message_id` (참여자별 마지막 읽은 메시지 ID)**
- 안 읽은 수 = `COUNT(message.id > last_read_message_id)`
- 읽음 표시: 상대방의 `last_read_message_id` 조회
- 단순 · 성능 우수 (인덱스 커버)

**Option B — 메시지별 read_by JSON 배열**
- 거부 이유: 인덱스 어려움 · JSON 파싱 부담

### 갈림길 4: 타이핑 인디케이터

**Option A (채택) — STOMP 이벤트 · DB 저장 X**
- 클라이언트 → `/pub/rooms/{roomId}/typing` 발행
- 서버 → `/sub/rooms/{roomId}/typing`으로 브로드캐스트
- 서버는 그대로 통과 (validation만) · 저장 X

## 선택: Option A (모든 갈림길)

## 부속 결정

### 도메인 확장 (이슈 #01 위에)

#### 스키마 확장
```sql
-- ChatRoom 확장
ALTER TABLE chat_room ADD COLUMN project_id BIGINT NULL AFTER item_id;   -- 이슈 #01 재해석 반영
ALTER TABLE chat_room ADD KEY idx_chat_room_project (project_id);

-- ChatRoomMember 신규 (참여자별 상태)
CREATE TABLE chat_room_member (
  chat_room_id            BIGINT       NOT NULL,
  user_id                 BIGINT       NOT NULL,
  role                    VARCHAR(20)  NOT NULL,   -- BUYER · SELLER (이슈 #01) or BACKER · MAKER (재해석)
  last_read_message_id    BIGINT       NULL,
  joined_at               TIMESTAMP    NOT NULL,
  PRIMARY KEY (chat_room_id, user_id),
  KEY idx_chat_member_user (user_id, last_read_message_id)
);

-- ChatMessage 확장
ALTER TABLE chat_message MODIFY COLUMN message_type VARCHAR(30) NOT NULL;   -- 기존 · 값 추가
ALTER TABLE chat_message MODIFY COLUMN content JSON;   -- TEXT → JSON (하위 호환)
-- MessageType: TEXT · REWARD_CARD · PROJECT_CARD · IMAGE · SYSTEM · TYPING (in-memory only)
```

#### MessageType payload 스키마 (JSON)
```json
// TEXT
{ "text": "메시지 내용" }

// REWARD_CARD
{
  "rewardId": 42,
  "projectId": 100,
  "title": "얼리 어답터",
  "price": 35000,
  "description": "우선 기능요청권 + 셋업 지원",
  "thumbnailUrl": "https://..."
}

// PROJECT_CARD
{
  "projectId": 100,
  "title": "Driftwood — 셀프호스팅 팀 위키",
  "coverUrl": "https://...",
  "progressPct": 342,
  "dDay": 7,
  "backerCount": 184
}

// IMAGE
{ "imageUrl": "https://...", "width": 800, "height": 600 }

// SYSTEM
{ "systemType": "ROOM_CREATED", "text": "채팅방이 개설되었습니다" }
```

### 도메인 모델 확장
```java
// chat.domain.MessageType (enum 확장)
public enum MessageType {
    TEXT, REWARD_CARD, PROJECT_CARD, IMAGE, SYSTEM
}

// chat.domain.ChatRoomMember (신규 Entity)
@Entity
@IdClass(ChatRoomMemberId.class)
public class ChatRoomMember {
    @Id Long chatRoomId;
    @Id Long userId;
    @Enumerated(STRING) ChatRoomRole role;
    Long lastReadMessageId;
    LocalDateTime joinedAt;

    public void markRead(Long messageId) {
        if (lastReadMessageId == null || messageId > lastReadMessageId)
            this.lastReadMessageId = messageId;
    }
}

// chat.domain.ChatRoom (기존 확장)
public class ChatRoom {
    ...
    Long projectId;   // 신규 · nullable

    public void addMessage(ChatMessage msg) {
        this.lastMessageAt = msg.getCreatedAt();
    }
}
```

### 리워드 카드 발송 흐름
```java
// chat.application.ChatMessageService.sendRewardCard
@Transactional
public ChatMessage sendRewardCard(Long userId, Long chatRoomId, Long rewardId) {
    // 1. 채팅방 참여자 검증 (이슈 #01)
    ChatRoom room = chatRoomRepository.findByIdWithVerify(chatRoomId, userId);

    // 2. RewardTier 조회 (이슈 #09)
    RewardTier reward = rewardTierRepository.findById(rewardId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REWARD_NOT_FOUND));

    // 3. 페이로드 구성
    RewardCardPayload payload = RewardCardPayload.from(reward);

    // 4. 메시지 저장
    ChatMessage message = ChatMessage.of(chatRoomId, userId, REWARD_CARD, payload.toJson());
    chatMessageRepository.save(message);

    // 5. 채팅방 갱신
    room.addMessage(message);

    // 6. STOMP 브로드캐스트
    simpMessagingTemplate.convertAndSend("/sub/rooms/" + chatRoomId, message);

    return message;
}
```

### API 표면
| 메서드 | 경로 | 인증 | 용도 |
|---|---|---|---|
| POST | `/api/v1/chat-rooms/{roomId}/messages/reward-card` | JWT | 리워드 카드 발송 (body: `{rewardId}`) |
| POST | `/api/v1/chat-rooms/{roomId}/messages/project-card` | JWT | 프로젝트 카드 발송 (body: `{projectId}`) |
| POST | `/api/v1/chat-rooms/{roomId}/read` | JWT | 읽음 표시 갱신 (body: `{lastMessageId}`) |
| GET | `/api/v1/chat-rooms/{roomId}/unread-count` | JWT | 안 읽은 수 조회 |
| WS (send) | `/pub/rooms/{roomId}/typing` | STOMP | 타이핑 상태 발행 |
| WS (subscribe) | `/sub/rooms/{roomId}/typing` | STOMP | 타이핑 상태 수신 |
| WS (subscribe) | `/sub/rooms/{roomId}/read` | STOMP | 읽음 표시 실시간 수신 (상대방) |

### ErrorCode (신규)
- `CHM001` CHAT_MESSAGE_INVALID_TYPE (400 · 지원 안 되는 타입)
- `CHM002` CHAT_MESSAGE_PAYLOAD_INVALID (400 · JSON 스키마 위반)
- `CHM003` CHAT_MESSAGE_ATTACHED_ENTITY_NOT_FOUND (404 · rewardId · projectId 없음)

### 정합성 · 멱등성
- 메시지 저장은 서버 채번 · S 등급 멱등
- 읽음 표시는 상태 기반 멱등 (`markRead(id)`는 max 값만 저장 · 재요청 안전)
- 타이핑은 저장 X · at-most-once (놓쳐도 OK)
- STOMP 브로드캐스트 실패 시: 저장은 성공이므로 재조회 API로 회복 가능

### 관측
- `chat.message.total{type}` counter (TEXT · REWARD_CARD · PROJECT_CARD · IMAGE 등)
- `chat.read.total` counter
- `chat.typing.event.total` counter (샘플링)
- `chat.unread.gauge` (사용자별 · 카디널리티 주의)

## 이관 산출물

- **BE-Story #12-1**: `MessageType` enum 확장 (`REWARD_CARD`·`PROJECT_CARD`·`SYSTEM`)
- **BE-Story #12-2**: `ChatMessage.content` 컬럼 JSON 마이그레이션 (TEXT → JSON)
- **BE-Story #12-3**: `ChatRoom.projectId` 컬럼 추가 + 이슈 #01의 ChatRoom 재해석 반영
- **BE-Story #12-4**: `ChatRoomMember` 신규 엔티티 + 참여자 관리 로직 (이슈 #01 대체 · verify 로직 이관)
- **BE-Story #12-5**: `RewardCardPayload`·`ProjectCardPayload` 페이로드 클래스 (record + 직렬화)
- **BE-Story #12-6**: `ChatMessageService.sendRewardCard`·`sendProjectCard` 메서드
- **BE-Story #12-7**: 읽음 표시 API + `ChatRoomMember.markRead` 도메인 메서드
- **BE-Story #12-8**: 타이핑 STOMP 이벤트 핸들러 (`@MessageMapping("/rooms/{roomId}/typing")`)
- **BE-Story #12-9**: `ErrorCode.CHM001~003` 등록
- **BE-Story #12-10**: 통합 테스트 (리워드 카드 · 프로젝트 카드 · 읽음 · 타이핑)
- **FE-Story #12-1**: `src/features/chat/messages/RewardCardBubble.tsx`
- **FE-Story #12-2**: `src/features/chat/messages/ProjectCardBubble.tsx`
- **FE-Story #12-3**: `src/features/chat/ProjectContextStrip.tsx` (스레드 상단)
- **FE-Story #12-4**: `src/features/chat/TypingIndicator.tsx`
- **FE-Story #12-5**: `src/features/chat/UnreadBadge.tsx` + `ReadReceipt.tsx`
- **FE-Story #12-6**: 빠른 답변 chip 버튼 (FE 하드코딩 · 서버 저장 X)
- **Docs-Story #12-1**: `backend-boundary/error-codes.md` CHM001~003 매핑

## 관련 이슈 / 문서

- 선행: [#01 채팅](./issue-01-websocket-chat.md) — 본 이슈의 STOMP 골격
- 관련: [#08 Project](./issue-08-project-domain.md) — ChatRoom.projectId 참조 · PROJECT_CARD 페이로드
- 관련: [#09 Reward Tier](./issue-09-reward-tier.md) — REWARD_CARD 페이로드
- 관련: [#11 Maker Profile](./issue-11-maker-profile-response-stats.md) — 채팅 이력으로 응답 지표 계산 · 온라인 상태
- 벤치마크: Intercom (컨텍스트 · Bot) · Slack (Block Kit) · 카카오톡 (플러스친구 카드)

## 디자인 참조
- `C:\Users\user\Desktop\fe\메이커 채팅.html` — 전체 UI (card-bubble · ctx · typing · quick · unread · read)
- `C:\Users\user\Desktop\fe\프로젝트 상세.html` — "메이커에게 1:1 문의" 진입점
