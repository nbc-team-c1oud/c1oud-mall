# Issue: WebSocket 기반 채팅 도메인 신설

## 크라우드 펀딩 컨셉 재해석 (0.0.2v 확장)

> 이 이슈는 초기 일반 쇼핑몰 컨텍스트로 작성되었으나, 0.0.2v 컨셉 확정(GitHub 기반 대학생 프로젝트 크라우드 펀딩)에 따라 재해석함.

### 도메인 리매핑
| 기존 | 재해석 |
|---|---|
| 판매자 · 구매자 대칭 | **메이커(Maker) · 후원자(Backer)** |
| ChatRoom(itemId) | **ChatRoom(projectId)** — 프로젝트 단위 문의방 |
| 상품 문의 | **프로젝트 후원 검토 Q&A** |

### 채팅 UX 확장 (디자인 반영)
디자인(`C:\Users\user\Desktop\fe\메이커 채팅.html`) 검토 결과 다음 요소가 이슈 #12로 분리 · 본 이슈는 STOMP + SoftDelete 골격만 담당:

- 리워드 카드 발송 (card bubble) → **이슈 #12**
- 프로젝트 컨텍스트 embed (썸네일 · 달성률 · D-day) → **이슈 #12**
- 읽음 표시 · 안 읽은 수 · 타이핑 표시 → **이슈 #12**
- 빠른 답변 버튼 (리워드 문의 · 배송 · 환불 정책 · 셀프호스팅) → **이슈 #12** (FE 위주)
- 메이커 응답률 · 응답시간 표기 → **이슈 #11**

### 관련 신규 이슈
- [#11 메이커 프로필 · 응답 지표](./issue-11-maker-profile-response-stats.md) — 채팅 이력이 응답률 계산의 원천
- [#12 채팅 Rich Message](./issue-12-chat-rich-message.md) — 본 이슈의 확장 (리워드 카드 · 프로젝트 컨텍스트)
- [#08 Project 도메인](./issue-08-project-domain.md) — ChatRoom.projectId 참조 대상

### 디자인 참조
- `C:\Users\user\Desktop\fe\메이커 채팅.html`
- (향후 `workflows/products/design/`으로 이동 예정)

---

## 배경

> **사용자 지시**: "WebSocket 기반 채팅 기능을 붙여나갈 것" · Cabbage-Market-10을 벤치마크로 참고.

- 현재 c1oud-mall은 8개 도메인(auth · product · cart · order · payment · refund · point · common)이 모두 요청-응답 REST만 사용
- 구매자↔판매자(관리자·seller 도메인 확장 시) 실시간 커뮤니케이션 채널 부재 → 문의·거래 상담 흐름을 우회(전화·메일)해야 함
- 향후 상품 리뷰(이슈 #05) · 좋아요(이슈 #04) 같은 사용자간 상호작용 기능이 붙기 전에 실시간 인프라 골격 필요
- 결제 확정 후 판매자·구매자간 배송 문의 UX 확장 여지 (Refund 트리거 대신 채팅으로 사전 조율)

## 조사 결과 — Cabbage-Market-10 벤치마크

| 항목 | Cabbage 방식 | 우리 컨텍스트 대응 |
|---|---|---|
| 프로토콜 | STOMP over WebSocket · `/ws/chat` 엔드포인트 | 동일 채택 (Spring Messaging 표준) |
| 브로커 | `SimpleBroker` (in-memory) · `/sub` subscribe · `/pub` send prefix | 초기엔 SimpleBroker · 다중 인스턴스 확장 시 Redis pub/sub |
| 인증 | `StompAuthInterceptor` (ChannelInterceptor) — CONNECT 프레임의 `Authorization: Bearer` JWT 검증 후 `accessor.setUser()` | 우리 JWT 정책과 동일 · Interceptor로 재사용 |
| ChatRoom PK | String UUID | Long auto-increment (기존 우리 컨벤션) · UUID는 논리 채번용 별도 컬럼 검토 |
| 참여자 검증 | `ChatRoom.inspectClientAsParticipant()` (구매자 또는 판매자만) | 유사 도메인 로직 · c1oud-mall은 seller 도메인 아직 없음 → `ChatRoom.buyerId + sellerUserId` 초기 방식 |
| 메시지 삭제 | `@SQLDelete` + `@SQLRestriction("deleted_at IS NULL")` Soft Delete | 채택 (감사·복원 가능성) |
| Redis 사용 | 채팅에는 미사용 (경매 락에만 사용) | 우리도 초기엔 미사용 |
| 이미지 첨부 | `imageUrl` 컬럼만 · 업로드 로직은 별개 | 초기 스코프 제외 (텍스트만) |

## 옵션 비교

**Option A — STOMP + SimpleBroker + JWT Interceptor (Cabbage 방식 채택)** `(채택)`
- 장점: Spring 표준 · 학습 곡선 낮음 · 기존 JWT 그대로 재사용 · 다중 인스턴스 확장 시 Redis 전환 여지
- 비용: SimpleBroker는 단일 인스턴스 전제 (M2 배포 스코프에 부합)
- 실 사례 벤치마크 존재 (Cabbage-Market-10)

**Option B — Raw WebSocket + 자체 프로토콜**
- 거부 이유: STOMP 미사용 시 subscribe/broadcast 규약을 자체 정의해야 함 · 라이브러리 생태계(STOMP.js) 활용 불가

**Option C — Server-Sent Events (SSE) + REST POST**
- 거부 이유: 양방향 아니라 클라이언트→서버는 REST · 실시간성 저하 · 구현은 단순하나 채팅 UX 부적합

## 선택: Option A

## 부속 결정

### 도메인 컨텍스트
- 신규 컨텍스트: `nbc.c1oud_mall.chat.*`
- 4레이어 유지 (`presentation` · `application` · `domain` · `infrastructure`)
- Client 도메인(우리는 `User`) 참조는 `userId`(Long) FK만 (BC 협력은 직접 호출 · ADR 006 준수)

### 엔티티 · 스키마
```sql
-- V{N}__chat_rooms.sql (Flyway 도입 시 · 초기엔 JPA ddl-auto)
CREATE TABLE chat_room (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  room_uuid     VARCHAR(36)  NOT NULL UNIQUE,   -- 논리 채번 · 외부 노출용
  buyer_id      BIGINT       NOT NULL,          -- 구매자 (User.id)
  seller_id     BIGINT       NOT NULL,          -- 판매자 (초기엔 관리자 or seller 확장 시 seller.id)
  item_id       BIGINT       NULL,              -- 대화 시작점 상품 (nullable · 문의 페이지 진입도 허용)
  last_message_at TIMESTAMP  NULL,
  created_at    TIMESTAMP    NOT NULL,
  updated_at    TIMESTAMP    NOT NULL,
  PRIMARY KEY (id),
  KEY idx_chat_room_buyer (buyer_id, last_message_at DESC),
  KEY idx_chat_room_seller (seller_id, last_message_at DESC)
);

CREATE TABLE chat_message (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  chat_room_id  BIGINT       NOT NULL,
  sender_id     BIGINT       NOT NULL,   -- buyer_id 또는 seller_id 중 하나
  message_type  VARCHAR(20)  NOT NULL,   -- TEXT · IMAGE · SYSTEM
  content       TEXT         NOT NULL,
  image_url     VARCHAR(500) NULL,
  deleted_at    TIMESTAMP    NULL,       -- Soft Delete
  created_at    TIMESTAMP    NOT NULL,
  PRIMARY KEY (id),
  KEY idx_chat_message_room_created (chat_room_id, created_at DESC),
  KEY idx_chat_message_deleted (deleted_at)
);
```

### 도메인 모델
```java
// chat.domain.ChatRoom (Aggregate root)
@Entity
@Table(name = "chat_room")
public class ChatRoom extends BaseEntity {
    @Id @GeneratedValue Long id;
    @Column(unique = true) String roomUuid;   // UUID.randomUUID().toString()
    Long buyerId;
    Long sellerId;
    Long itemId;   // nullable
    LocalDateTime lastMessageAt;

    public static ChatRoom of(Long buyerId, Long sellerId, Long itemId) { ... }
    public void verifyParticipant(Long userId) {
        if (!Objects.equals(userId, buyerId) && !Objects.equals(userId, sellerId))
            throw new BusinessException(ErrorCode.CHAT_ACCESS_DENIED);
    }
    public void markLastMessageAt(LocalDateTime at) { ... }
}

// chat.domain.ChatMessage (Entity · Aggregate 내부)
@Entity
@Table(name = "chat_message")
@SQLDelete(sql = "UPDATE chat_message SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class ChatMessage extends BaseEntity {
    @Id @GeneratedValue Long id;
    @ManyToOne(fetch = LAZY) ChatRoom chatRoom;
    Long senderId;
    @Enumerated(STRING) MessageType type;   // TEXT · IMAGE · SYSTEM
    @Column(columnDefinition = "TEXT") String content;
    String imageUrl;   // nullable
    LocalDateTime deletedAt;
}
```

### 인증 · WebSocket 설정
```java
// chat.infrastructure.WebSocketConfig
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("http://localhost:*", "https://c1oud-mall.dev");
    }
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/sub");    // 구독 prefix
        registry.setApplicationDestinationPrefixes("/pub");  // 전송 prefix
    }
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthInterceptor);  // JWT 검증
    }
}

// chat.infrastructure.StompAuthInterceptor
@Component
public class StompAuthInterceptor implements ChannelInterceptor {
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String bearer = accessor.getFirstNativeHeader("Authorization");
            AuthenticatedUser user = jwtService.verify(bearer);
            accessor.setUser(user);   // WebSocketPrincipal
        }
        return message;
    }
}
```

### API 표면
| 메서드 | 경로 | 용도 |
|---|---|---|
| POST | `/api/v1/chat-rooms` | 채팅방 개설 (body: `{itemId?, sellerUserId}`) |
| GET | `/api/v1/chat-rooms/my` | 내 채팅방 목록 (buyer or seller 참여 전부) |
| GET | `/api/v1/chat-rooms/{roomId}/messages?before=&size=` | 메시지 페이지 조회 (커서 기반 · `before` = 이전 message id) |
| DELETE | `/api/v1/chat-messages/{messageId}` | 메시지 Soft Delete (본인만) |
| WS (send) | `/pub/{roomId}/messages` | 메시지 전송 |
| WS (subscribe) | `/sub/{roomId}/messages` | 메시지 수신 |

### ErrorCode (신규 · CHAT001~005 예약)
- `CHAT001` CHAT_ROOM_NOT_FOUND (404)
- `CHAT002` CHAT_ACCESS_DENIED (403 · 참여자 아닌 자가 접근)
- `CHAT003` CHAT_MESSAGE_NOT_FOUND (404)
- `CHAT004` CHAT_MESSAGE_OWNERSHIP_FAILED (403 · 본인 메시지 아님)
- `CHAT005` CHAT_INVALID_STOMP_TOKEN (401 · WS 인증 실패)

### 정합성 · 멱등성
- 메시지 전송: 서버 채번 message id · 클라이언트 재전송 시 idempotency key(옵션) 지원 검토 (v2)
- ChatRoom 개설: `(buyer_id, seller_id, item_id)` 조합에 UNIQUE 제약 (동일 조합 재개설 시 기존 반환)
- Soft Delete는 `@SQLRestriction`으로 자동 필터 · 리스토어 API는 v2

### 관측
- `ws.connection.total{status=connected|disconnected}` counter
- `ws.message.total{room_id, type}` counter (개별 room_id 집계는 카디널리티 위험 · sampling 검토)

## 이관 산출물

- **BE-Story #01-1**: `chat` 컨텍스트 신규 패키지 골격 + `ChatRoom`·`ChatMessage` 엔티티 + Repository
- **BE-Story #01-2**: `WebSocketConfig` + `StompAuthInterceptor` (JWT 검증) + `/ws/chat` 엔드포인트 활성화
- **BE-Story #01-3**: `ChatService.createRoom` + `verifyParticipant` + 참여자 목록 조회
- **BE-Story #01-4**: `ChatController` REST 4개 엔드포인트 (개설·목록·메시지 페이지·삭제)
- **BE-Story #01-5**: `ChatWebSocketController` (`@MessageMapping("/{roomId}/messages")` + `simpMessagingTemplate.convertAndSend`)
- **BE-Story #01-6**: `ErrorCode.CHAT001~005` 등록 + `.claude/rules/exception.md` 도메인 섹션 추가
- **BE-Story #01-7**: 통합 테스트 (STOMP 클라이언트 mock · 참여자 검증 · Soft Delete 필터)
- **FE-Story #01-1**: `src/lib/ws/stomp-client.ts` — SockJS + STOMP.js 래퍼 · JWT 헤더 자동 첨부
- **FE-Story #01-2**: `src/features/chat/ChatRoomListPage.tsx` · `ChatRoomPage.tsx`
- **FE-Story #01-3**: `useSubscribeMessages(roomId)` · `useSendMessage(roomId)` 훅
- **Docs-Story #01-1**: `workflows/backend-boundary/error-codes.md`에 CHAT001~005 UX 매핑 추가
- **SDD 개정**: `workflows/products/product-chat.md` 신규 작성 (0.0.2v M2 진입 시)

## 관련 이슈 / 문서

- 관련: [#04 상품 좋아요](./issue-04-product-like.md) — 실시간 이벤트 인프라 재사용 여지 (좋아요 카운트 broadcast는 후속)
- 관련: [#05 리뷰](./issue-05-review.md) — 판매자·구매자간 리뷰 이전 소통 채널
- 짝 이슈: 없음 (독립)
- SDD 신설: `workflows/products/product-chat.md` (M2 진입 시)
- 벤치마크 원본: `pcb2002/Cabbage-Market-10` — `src/main/java/com/example/cabbagemarket10/domain/chat/`
- 규범 참조: `.claude/rules/consitency.md` §4 (외부 zone 경계 없음 · 채팅은 자체 zone · Point/Order와 무관)
