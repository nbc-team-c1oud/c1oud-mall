# [0.0.3v · Issue 09] CS 문의 채팅 — WebSocket + STOMP + JWT + 상태기계

> **역할**: 캠프 요구사항의 실시간 채팅 필수 (주제 B: CS 문의). 관리자 = 상담사.
> **Week**: 2 · **Day**: 12~14
> **상태**: pending
> **Tier**: `sdd-lite`
> **캠프 요구사항 매핑**: 필수 — 실시간 채팅 (주제 B · WebSocket + STOMP + JWT ChannelInterceptor + 상태기계 + 커서 페이징 + Fetch Join)

---

## 배경

캠프 요구사항 명시 (주제 B):
- CS 문의 채팅 (고객 → 관리자 문의)
- 상태 관리: `WAITING`(대기중) / `IN_PROGRESS`(처리중) / `COMPLETED`(완료)
- WebSocket 기반 실시간 양방향
- STOMP 프로토콜 · `SUBSCRIBE`/`SEND`
- ChatRoom · ChatMessage · ChatMember ERD (JPA)
- 메시지 중심 설계 (ChatMessage → ChatRoom 단방향)
- Fetch Join (N+1 방지)
- 커서 기반 페이징 (`lastMessageId`)
- JWT 인증 (HTTP Filter 아닌 **ChannelInterceptor** · CONNECT 시점)
- 역할 기반 접근 제어 (고객 vs 관리자)
- 재연결(Reconnect) 전략

## 백오피스 프레이밍 매핑

- 관리자 = 상담사 (다수 CS 문의 방을 동시 모니터링)
- 고객 = 문의 요청자 (본인 문의만 조회)
- 관리자만 상태 전이 API 호출 가능 (`WAITING → IN_PROGRESS → COMPLETED`)

## 핵심 스코프

### Day 12 — STOMP 골격
- 신규 컨텍스트 `nbc.c1oud_mall.chat.*` (4레이어)
- `WebSocketConfig` (`@EnableWebSocketMessageBroker` · `/ws/chat` 엔드포인트 · SimpleBroker `/sub` · prefix `/pub`)
- `StompAuthInterceptor` (`ChannelInterceptor`) — CONNECT 프레임의 `Authorization: Bearer` JWT 검증 → `Principal` 세팅
- `ChatRoom` 엔티티 (id · roomUuid · customerUserId · status enum · createdAt · lastMessageAt)
- `ChatMessage` 엔티티 (id · chatRoomId (단방향 FK) · senderUserId · senderRole · content · createdAt)

### Day 13 — 상태기계 + 관리자 API
- `ChatRoomStatus` enum: `WAITING` · `IN_PROGRESS` · `COMPLETED`
- 상태 전이 도메인 메서드 (`ChatRoom.startHandling(adminId)` · `ChatRoom.complete(adminId)`)
- **역방향 전이 금지** (COMPLETED → WAITING 불가) — 요구사항 언급
- 고객 API: 문의 개설 · 본인 문의 목록 조회
- 관리자 API: 전체 CS 목록 (상태별 필터) · 상태 전이 · 담당자 배정
- 관리자만 상태 전이 (`@PreAuthorize("hasRole('ADMIN')")`)

### Day 14 — 커서 페이징 + Fetch Join + 재연결
- 메시지 조회 API (`GET /chat-rooms/{roomId}/messages?lastMessageId=&size=`)
- 커서 페이징: `WHERE m.id < :lastMessageId ORDER BY m.id DESC LIMIT :size`
- Fetch Join (`sender` JPA `@ManyToOne` · JOIN FETCH 명시)
- 클라이언트 재연결 전략: `lastMessageId` 기준 재조회
- 시스템 메시지 ("○○님이 입장했습니다" · "관리자 승선")

## API 표면

| 메서드 | 경로 | 인증 | 용도 |
|---|---|---|---|
| POST | `/api/v1/chat-rooms` | JWT (고객) | CS 문의 개설 |
| GET | `/api/v1/chat-rooms/my` | JWT (고객) | 본인 문의 목록 |
| GET | `/api/v1/admin/chat-rooms?status=` | ADMIN | 전체 CS 목록 (필터) |
| PATCH | `/api/v1/admin/chat-rooms/{id}/status` | ADMIN | 상태 전이 (WAITING→IN_PROGRESS→COMPLETED) |
| GET | `/api/v1/chat-rooms/{roomId}/messages?lastMessageId=&size=` | JWT | 커서 페이징 조회 |
| WS (send) | `/pub/chat/{roomId}` | STOMP | 메시지 전송 |
| WS (subscribe) | `/sub/chat/{roomId}` | STOMP | 메시지 수신 |

## ErrorCode (신규)

- `CHT001` CHAT_ROOM_NOT_FOUND (404)
- `CHT002` CHAT_ROOM_ACCESS_DENIED (403 · 본인 문의 아니거나 관리자 아님)
- `CHT003` CHAT_INVALID_STATUS_TRANSITION (400 · 역방향)
- `CHT004` CHAT_STOMP_AUTH_FAILED (401 · WS 인증 실패)
- `CHT005` CHAT_MESSAGE_NOT_FOUND (404)

## 산출물

- BE-09-1: `chat` 컨텍스트 골격 + `ChatRoom` · `ChatMessage` 엔티티 + Repository
- BE-09-2: `WebSocketConfig` + `StompAuthInterceptor` (JWT)
- BE-09-3: `ChatRoomService.create` (고객) · `getMyRooms` · `getAdminRooms(status)`
- BE-09-4: `ChatRoom.startHandling(adminId)`·`complete(adminId)` 상태 전이 도메인 메서드
- BE-09-5: `AdminChatController` (상태 전이 API · 역할 기반)
- BE-09-6: `ChatMessageService.list(roomId, lastMessageId, size)` — 커서 페이징 + Fetch Join
- BE-09-7: `ChatMessageController` · WS `@MessageMapping("/{roomId}")` + `simpMessagingTemplate.convertAndSend`
- BE-09-8: 재연결 전략 (클라이언트 lastMessageId 기반)
- BE-09-9: 통합 테스트 (STOMP 클라이언트 mock · 상태 전이 · 역할 기반 접근 · 커서 페이징 N+1 검증)

## 인덱스 설계

- `chat_room (customer_user_id, status)` — 고객 본인 문의 조회
- `chat_room (status, last_message_at DESC)` — 관리자 상태별 목록
- `chat_message (chat_room_id, id DESC)` — 커서 페이징 (요구사항: 문의 상태별 인덱스 언급)

## 관련 이슈 / 문서

- 선행: [01 Admin 컨텍스트](./issue-01-admin-context-and-role.md) — 관리자 롤 사용
- 관련: [13 대시보드](./issue-13-admin-dashboard-observability.md) — CS 통계
- 관련: [14 ADR·README](./issue-14-adr-readme-documentation.md) — STOMP 아키텍처 다이어그램
- 규범: `.claude/rules/architecture.md` · `.claude/rules/exception.md`

## 상세 (착수 시 채움)

_pending_
