# [Product 3] CS 문의 채팅 · WebSocket + STOMP + JWT + 상태기계

## Product Vision
> 고객이 문의하면 관리자가 실시간으로 응대하고, WAITING → IN_PROGRESS → COMPLETED 상태 전이가 관리자 명시 액션으로만 진행되며, WebSocket + STOMP + JWT ChannelInterceptor로 인증·라우팅·재연결이 안정적으로 굴러가는 CS 백엔드를 완성한다.

## 배경 및 문제

- 현재 상황 (As-Is)
  - c1oud-mall에 실시간 통신 채널 없음 (M1까지 전부 REST)
  - CS 문의 창구 부재 (사용자 문의 → 이메일·전화 등 우회)
  - 백오피스에서 관리자가 응대할 통합 도구 없음
- 발생하는 문제
  - 캠프 요구사항 필수: WebSocket + STOMP · JWT ChannelInterceptor(HTTP Filter 아님) · 상태기계 · 커서 페이징 · Fetch Join · 재연결 전략
  - 관리자 백오피스가 CS 문의를 응대하는 게 백오피스 프레이밍의 핵심 정체성 중 하나
  - 상태 전이 규칙(역방향 금지 등)이 도메인 로직으로 잡히지 않으면 감사 이력 흐트러짐
- 왜 지금 해결해야 하는가
  - 백오피스 정체성의 한 축 (타임세일 · 쿠폰과 함께)
  - 캠프 필수 요구사항 (주제 B 채택)
  - Week 2 후반 진입 · 이슈 01(Admin)과 07(락 3전략) 완료 후 자연스러운 순서

## 목표 (To-Be)

- 신규 컨텍스트 `nbc.c1oud_mall.chat.*` (4레이어)
- `WebSocketConfig` (@EnableWebSocketMessageBroker · SimpleBroker `/sub` · destination prefix `/pub`)
- `StompAuthInterceptor` (ChannelInterceptor) — CONNECT 프레임의 `Authorization: Bearer` JWT 검증 · Principal 세팅
- `ChatRoom` 엔티티 (id · roomUuid · customerUserId · status enum · createdAt · lastMessageAt) · `ChatMessage` (id · chatRoomId · senderUserId · senderRole · content · createdAt) · `ChatMember` (선택 · 관리자 배정 시)
- `ChatRoomStatus` enum: WAITING · IN_PROGRESS · COMPLETED · 역방향 전이 금지
- 관리자 상태 전이 API (`PATCH /admin/chat-rooms/{id}/status`) — `@PreAuthorize("hasRole('ADMIN')")`
- 커서 페이징 (`WHERE m.id < :lastMessageId ORDER BY m.id DESC LIMIT :size`)
- Fetch Join (`JOIN FETCH m.sender`) — N+1 방지
- ErrorCode `CHT001~005`

## 설계 결정 (Design Decisions)

- **STOMP + SimpleBroker** — Spring 표준 · 학습 곡선 낮음 · 다중 인스턴스 확장 시 Redis Pub/Sub 이관 여지(v0.0.4v+)
- **JWT는 ChannelInterceptor에서 CONNECT 시점 검증** — HTTP Filter는 WebSocket 핸드셰이크에는 통해도 STOMP 프레임에는 안 걸림
- **메시지 중심 설계** (ChatMessage → ChatRoom 단방향 참조) — 양방향 연관은 무한 루프·성능 함정 (팀 컨벤션 forbidden.md 언급)
- **커서 페이징 = `WHERE id < lastMessageId`** — offset 대비 대용량에서 O(1)에 가까움 · 재연결 시 원위치 복구 명확
- **상태 전이 역방향 금지** — 도메인 메서드에서 검증 (`chatRoom.markInProgress(adminId)` · `chatRoom.markCompleted(adminId)`) · 서비스에서 직접 status 세팅 금지
- **관리자만 상태 전이** — `@PreAuthorize("hasRole('ADMIN')")` · 고객은 불가
- **Soft Delete 채택** — 감사 이력 보존 · `@SQLDelete` + `@SQLRestriction`
- **재연결 전략 = lastMessageId 기반 재조회** — WebSocket 재연결 후 클라이언트가 마지막 수신 ID 전달 → 서버가 그 이후 메시지 조회

## 대안 검토 (Alternatives Considered)

### 프로토콜

**Option A — 순수 WebSocket**
- 거부: 라우팅·구독·발행 규약을 자체 정의해야 함 · 클라이언트 SDK(STOMP.js) 활용 불가

**Option B (선택) — STOMP over WebSocket + SimpleBroker**
- 비용: 러닝커브 (프레임 구조)
- 보상: 표준 · 클라이언트 라이브러리 다수 · Spring 통합

**Option C — Server-Sent Events (SSE)**
- 거부: 양방향 아님 · 사용자→서버는 REST 병행 필요 · 채팅 UX 부적합

### 인증

**Option A — HTTP Filter (핸드셰이크)**
- 부분 채택: 핸드셰이크 단계는 HTTP Filter가 담을 수 있으나 STOMP 프레임 인증은 못함

**Option B (선택) — ChannelInterceptor + CONNECT 프레임 JWT**
- 비용: 커스텀 인터셉터 구현
- 보상: STOMP 프로토콜 안에서 원자적으로 인증 · Principal 세팅 후 `@MessageMapping`에서 활용

### 상태 전이 저장

**Option A (선택) — ENUM + 도메인 메서드 검증**
- 비용: 도메인 메서드마다 상태 검증 로직
- 보상: 명확 · 역방향 금지 자연스러움

**Option B — 별도 상태 이력 테이블 (ChatRoomStatusHistory)**
- 부분 채택 (필요 시 v0.0.4v+): 감사 강화 시나리오. 초기 스코프는 lastStatusChangedAt 필드만

## 전체 아키텍처 (High-Level Architecture)

### 컴포넌트 배치
```
presentation ──▶ application ──▶ domain ◀── infrastructure
  ChatController   ChatRoomService  ChatRoom    ChatRoomRepository
  AdminChatCtrl    ChatMsgService   ChatMessage ChatMessageRepository
  WsController     (Fetch Join)     Status enum
  (@MessageMapping)                 (도메인 메서드)

  WebSocketConfig · StompAuthInterceptor · simpMessagingTemplate
```

### 핵심 플로우

**1. WebSocket 연결 · 인증 (Epic 1)**
```
Client → HTTP GET /ws/chat (Upgrade: websocket) — 핸드셰이크
       → SockJS/STOMP.js 라이브러리
       → STOMP CONNECT 프레임 { Authorization: Bearer <JWT> }
       → StompAuthInterceptor.preSend (CONNECT 감지)
       → jwtService.verify(token) → AuthenticatedUser
       → accessor.setUser(user)  // Principal 세팅
Client ← CONNECTED 프레임
```

**2. 고객이 CS 문의 개설 · 첫 메시지 (Epic 1)**
```
Client → POST /api/v1/chat-rooms (JWT · 고객)
       → ChatRoomService.create(customerUserId)
       → repository.save (status=WAITING · roomUuid=UUID)
       ← 201 { roomId, roomUuid, status: WAITING }

Client → STOMP SEND /pub/chat/{roomId} { content }
       → WsChatController.@MessageMapping("/chat/{roomId}") (Principal p, ChatMessageDto dto)
       → ChatMessageService.send(chatRoomId, senderId=p.userId, content)
       → repository.save + chatRoom.markLastMessageAt
       → simpMessagingTemplate.convertAndSend("/sub/chat/{roomId}", message)

All subscribers of /sub/chat/{roomId} ← 메시지 수신
```

**3. 관리자 상태 전이 (Epic 2)**
```
Admin → PATCH /api/v1/admin/chat-rooms/{id}/status { targetStatus: IN_PROGRESS }
      → AdminChatController (@PreAuthorize)
      → ChatRoomService.transitionStatus(roomId, adminId, targetStatus)
      → chatRoom.markInProgress(adminId)  // 도메인 메서드 · WAITING만 허용
      → repository.save
      → simpMessagingTemplate.convertAndSend("/sub/chat/{roomId}/system", ChatSystemMessage.statusChanged)
      ← 200
```

**4. 재연결 · 커서 페이징 (Epic 2)**
```
Client (재연결 후) → GET /api/v1/chat-rooms/{roomId}/messages?lastMessageId=1234&size=20
                 → ChatMessageService.list(roomId, lastMessageId, size)
                 → repository.findByChatRoomIdAndIdLessThan(roomId, lastMessageId, size)
                   with JOIN FETCH sender
                 ← 20개 메시지 최신순 (id DESC)
```

### Out-of-Process 의존
- **WebSocket 클라이언트 (SockJS/STOMP.js)**: 프론트가 사용
- **RDS (MySQL) / H2 (dev)**: ChatRoom · ChatMessage 영속화
- **Redis (v0.0.4v+)**: Pub/Sub 다중 서버 브로드캐스팅 (본 스코프 외)

## 실패 모드 / 운영 관측 (Failure Modes & Observability)

### 실패 시나리오와 응답
| 시나리오 | ErrorCode | HTTP | 클라이언트 권장 동작 |
| --- | --- | --- | --- |
| 채팅방 존재하지 않음 | `CHT001` | 404 | 목록 재조회 |
| 접근 권한 없음 (본인 문의 아님 · 관리자 아님) | `CHT002` | 403 | 목록 재조회 |
| 잘못된 상태 전이 (역방향 · WAITING→COMPLETED 직행 등) | `CHT003` | 400 | 상태 재조회 |
| STOMP CONNECT 인증 실패 | `CHT004` | 401 | 재로그인 |
| 메시지 존재하지 않음 (Soft delete 됨) | `CHT005` | 404 | 이력 재로드 |
| STOMP 브로드캐스트 실패 | (없음) | 200 | 저장은 성공 · 재조회 API로 회복 |

### 로깅 정책
- **항상 기록**: `requestId` · `userId` · `roomId` · `messageId` · `status 전이 from→to` · `senderRole (CUSTOMER/ADMIN)` · `duration_ms`
- **debug**: STOMP CONNECT 프레임 헤더 · Fetch Join SQL 실체
- **절대 금지**: 메시지 본문 (프라이버시 · 관측 지표는 카운트만)

### 관측 지표
- `chat.message.total{sender_role, room_status}` — counter
- `chat.room.status.gauge{status=WAITING|IN_PROGRESS|COMPLETED}` — 상태별 방 수 (관리자 대시보드)
- `chat.stomp.connect.total{result=success|auth_failed}` — counter
- `chat.status.transition.total{from, to, result}` — counter
- `chat.message.query.duration_seconds` — 커서 페이징 조회 시간 (Fetch Join 효과 관찰)

## 롤아웃 / 마이그레이션 (Rollout)

### 전제
- Week 2 Day 12~14 진입 · 이슈 01(Admin) 완료 · Redis 없이도 됨 (SimpleBroker)

### Product 의존성
- 선행: 이슈 01 (Admin 컨텍스트)
- 후행: 이슈 13 (관리자 대시보드 CS 통계)
- 확장 후행 (v0.0.4v+): Redis Pub/Sub 다중 서버 브로드캐스팅

### Epic·Story 의존성 그래프
```
Epic 1 (STOMP 골격 · 인증 · 개설·전송) ──► Epic 2 (상태기계 · 관리자 API · 커서 페이징 · 재연결)
```

### 환경별 설정 분기
| 항목 | dev (H2) | prod (RDS) |
| --- | --- | --- |
| WebSocket 브로커 | SimpleBroker | SimpleBroker (v0.0.4v+ Redis Pub/Sub) |
| Allowed Origins | localhost:* | c1oud-mall.dev 등 |
| JWT 검증 | 개발 키 | 프로덕션 키 (Parameter Store) |

## 성공 지표 (KPI)
| 지표 | 목표 값 | 측정 방법 |
| --- | --- | --- |
| STOMP CONNECT 성공률 | ≥ **99%** (정상 JWT 시) | `chat.stomp.connect.total{result=success} / total` |
| 상태 전이 정합성 | 역방향 전이 **0건** | `chat.status.transition.total{result=fail}` = 0 |
| Fetch Join 효과 (N+1 방지) | 조회 쿼리 수 **1건** (커서 페이징 20개 조회 시) | Hibernate Statistics · 통합 테스트 |
| 커서 페이징 P95 | ≤ **50ms** | Micrometer histogram |
| 재연결 후 메시지 복구 | **100%** (lastMessageId 이후 전체) | 통합 테스트 |

## Scope

**In Scope**:
- WebSocket + STOMP + SimpleBroker
- JWT ChannelInterceptor · CONNECT 시점 인증
- ChatRoom · ChatMessage 엔티티 · Repository
- 상태기계 (WAITING → IN_PROGRESS → COMPLETED) · 역방향 금지
- 고객 API (개설 · 본인 목록 · 메시지 조회)
- 관리자 API (전체 목록 · 상태 필터 · 상태 전이)
- 커서 페이징 (`lastMessageId`) + Fetch Join
- Soft Delete
- 재연결 전략 (`lastMessageId` 기반 재조회)
- 시스템 메시지 (입장·상태 변경)
- ErrorCode `CHT001~005`

**Out of Scope**:
- Redis Pub/Sub 다중 서버 브로드캐스팅 — 사유: 캠프 도전 (v0.0.4v+ · 별도 이슈)
- 파일·이미지 첨부 — 사유: 스코프 오버 (v0.0.4v+ · Presigned URL과 통합)
- 리치 메시지 (카드 등) — 사유: 스코프 오버
- 안 읽은 메시지 카운트 · 읽음 표시 — 사유: 스코프 오버 (v0.0.4v+)
- 관리자 담당자 배정 · 이력 — 사유: 초기 무배정 · 상태만
- 채팅 검색 · 필터 — 사유: 스코프 오버

## 대상 사용자
- **일반 고객** — CS 문의 · 실시간 응대 받기
- **관리자 (상담사)** — 대기 문의 조회 · 처리 상태 전이 · 응대
- **개발자/리뷰어** — STOMP + 상태기계 아키텍처 다이어그램 (README)

## 연결된 Epic 목록
- [ ] Epic 1: STOMP 골격 · JWT ChannelInterceptor · 개설·전송
- [ ] Epic 2: 상태기계 · 관리자 API · 커서 페이징 · 재연결

## 관련 문서
- 대응 이슈:
  - [issue-09-cs-chat-stomp-state-machine](../../../fix/brainstorming/version/0.0.3v/issue-09-cs-chat-stomp-state-machine.md)
- 관련 ADR (발행 예정):
  - `ADR 015` — CS 채팅 상태기계 (WAITING/IN_PROGRESS/COMPLETED 역방향 금지)
- 재활용 조각 (아카이브):
  - `../archive/0.0.2v-crowdfunding/product-project.md` — (참고 없음 · 크라우드 펀딩 채팅은 별도 이슈)
- 관련 Product: `./product-admin-backoffice.md` (관리자 롤 사용) · `./product-perf-lab.md` (부하 리포트에 STOMP 시나리오 포함 가능)
- 관련 milestone: `../../../milestones/version/0.0.3v/milestone.md`

## 열린 질문 (Open Questions)
- **Q1**: STOMP 세션 timeout · heartbeat 설정? (초기 default · 문제 시 조정)
- **Q2**: 상태 전이 이력 별도 테이블(ChatRoomStatusHistory) 필요 시점? (초기: lastStatusChangedAt 필드만 · 감사 요구 시 v0.0.4v+)
- **Q3**: 담당자 배정 (관리자 A → 채팅방 B) 필요 시점? (초기 무배정 · Multi-agent 상담 시 v0.0.4v+)
- **Q4**: 다중 서버 배포 시 Pub/Sub 이관 필수 여부? (v0.0.4v+ 캠프 도전으로 진행 가능)

## 제품 수준 완료 기준 (Product-level DoD)
- [ ] Epic 1·2 완료
- [ ] ADR 015 발행
- [ ] STOMP 연결·인증·상태 전이·커서 페이징 통합 테스트 통과
- [ ] Hibernate Statistics로 N+1 방지 검증 (쿼리 수 1건)
- [ ] 관리자 대시보드 CS 통계 API 노출 (이슈 13 결합)

---

# [Epic 1] STOMP 골격 · JWT ChannelInterceptor · 채팅방 개설·전송

## 목표
WebSocket + STOMP를 세팅하고 JWT ChannelInterceptor로 인증하며, 고객이 CS 문의를 개설·메시지 전송할 수 있는 기본 흐름을 완성한다.

## 배경
Epic 2의 상태기계·관리자 흐름의 전제. STOMP·인증이 안 돌면 다음 진입 불가.

## 포함 Story
- Story 1-1: WebSocketConfig · StompAuthInterceptor · JWT CONNECT
- Story 1-2: ChatRoom · ChatMessage 엔티티 · Repository
- Story 1-3: 고객 API (개설 · 본인 목록) + WsChatController @MessageMapping
- Story 1-4: 통합 테스트 (STOMP 클라이언트 mock · 연결·전송·구독)

## Epic 인수 시나리오
- Given 고객 JWT 발급 · 백엔드 부팅 (SimpleBroker)
- When 클라이언트가 SockJS + STOMP.js로 `/ws/chat` 연결 · CONNECT 헤더 `Authorization: Bearer <JWT>`
- Then StompAuthInterceptor가 검증 · Principal 세팅 · CONNECTED 반환

- Given 인증된 고객
- When `POST /chat-rooms` 개설 후 `/pub/chat/{roomId}`에 메시지 전송
- Then 저장됨 · `/sub/chat/{roomId}` 구독자 모두에게 브로드캐스트

## Epic 완료 기준 (DoD)
- [ ] Story 1-1 ~ 1-4 완료
- [ ] STOMP 통합 테스트 (Spring `WebSocketStompClient` 사용)
- [ ] ErrorCode `CHT001~005` 등록

## [Story 1-1] WebSocketConfig · StompAuthInterceptor · JWT CONNECT

### User Story
- As a 개발자
- I want STOMP 프로토콜과 JWT 인증을 통합 세팅해서
- so that 이후 채팅방·메시지 개발이 인증된 Principal 기반으로 진행된다

### 설명
- `WebSocketConfig implements WebSocketMessageBrokerConfigurer`:
  - `registerStompEndpoints(registry)`: `/ws/chat` · SockJS 활성화 · allowedOriginPatterns
  - `configureMessageBroker(registry)`: `enableSimpleBroker("/sub")` · `setApplicationDestinationPrefixes("/pub")`
  - `configureClientInboundChannel(registration)`: `stompAuthInterceptor` 등록
- `StompAuthInterceptor implements ChannelInterceptor`:
  - `preSend(message, channel)` — StompHeaderAccessor로 CONNECT 명령 감지
  - `Authorization` 헤더 추출 · `jwtService.verify` · Principal 세팅 (`accessor.setUser`)
  - 실패 시 `AccessDeniedException` throw → ChannelInterceptor가 CONNECTED 대신 ERROR 프레임 반환

**핵심 클래스/인터페이스**:
- `nbc.c1oud_mall.chat.infrastructure.websocket.WebSocketConfig`
- `nbc.c1oud_mall.chat.infrastructure.websocket.StompAuthInterceptor`
- 재사용: `nbc.c1oud_mall.common.jwt.JwtUtil` (M1 완료)

### 완료 기준 (AC)
- Given 유효 JWT / When STOMP CONNECT / Then CONNECTED · Principal에 userId 세팅
- Given 만료 JWT / When STOMP CONNECT / Then ERROR 프레임 · `CHT004`
- Given Authorization 헤더 없음 / When STOMP CONNECT / Then ERROR · `CHT004`

### Definition of Done
- [ ] 구현: `WebSocketConfig` · `StompAuthInterceptor`
- [ ] 통합 테스트: WebSocketStompClient로 3 시나리오 (정상·만료·헤더 없음)
- [ ] ErrorCode `CHT004` 등록

### 스토리 포인트
1d

### 의존성
- 선행: 이슈 01 (Admin · JWT 기반 · 이미 M1 자산)
- 후행: Story 1-2 · 1-3

## [Story 1-2] ChatRoom · ChatMessage 엔티티

목록:
- `ChatRoom`: id · roomUuid(UUID) · customerUserId · status(WAITING) · createdAt · updatedAt · lastMessageAt · deletedAt
- `ChatMessage`: id · chatRoomId (단방향 FK) · senderUserId · senderRole (CUSTOMER/ADMIN/SYSTEM) · content · createdAt · deletedAt
- `@SQLDelete` + `@SQLRestriction` Soft Delete
- 인덱스: `chat_room (customer_user_id, status)` · `chat_message (chat_room_id, id DESC)`

**SP**: 0.5d

## [Story 1-3] 고객 API + WsChatController

목록:
- `POST /api/v1/chat-rooms` — 개설
- `GET /api/v1/chat-rooms/my` — 본인 목록
- `WsChatController @MessageMapping("/chat/{roomId}")` — 메시지 수신 · 저장 · 브로드캐스트

**SP**: 1d

## [Story 1-4] 통합 테스트

목록:
- `WebSocketStompClient` 사용 · Testcontainers 없이 SimpleBroker
- 연결·전송·구독 검증

**SP**: 0.5d

---

# [Epic 2] 상태기계 · 관리자 API · 커서 페이징 · 재연결

## 목표
관리자가 CS 문의 상태를 전이하고, 커서 페이징+Fetch Join으로 이력을 조회하며, 재연결 시 lastMessageId 이후 메시지가 복구된다.

## 배경
Epic 1 STOMP·개설·전송이 굴러가면 관리자 응대 흐름을 잇는다. 상태 전이·페이징이 핵심.

## 포함 Story
- Story 2-1: ChatRoomStatus enum + 도메인 메서드 · 역방향 금지
- Story 2-2: AdminChatController (전체 목록 · 상태 전이 API)
- Story 2-3: 메시지 조회 API (커서 페이징 · Fetch Join · Hibernate Statistics N+1 검증)
- Story 2-4: 시스템 메시지 · 재연결 · ADR 015

## Epic 인수 시나리오
- Given WAITING 상태 채팅방 존재
- When 관리자가 `PATCH /admin/chat-rooms/{id}/status {targetStatus: IN_PROGRESS}`
- Then 200 · `status=IN_PROGRESS` · `/sub/chat/{roomId}/system` 브로드캐스트 (SYSTEM 메시지)

- Given COMPLETED 상태 채팅방
- When 관리자가 `PATCH ... {targetStatus: WAITING}` (역방향)
- Then 400 · `CHT003`

- Given 채팅방에 메시지 100개
- When `GET /chat-rooms/{id}/messages?lastMessageId=50&size=20`
- Then 20개 반환 (id 30~49 · DESC) · Hibernate Statistics 쿼리 수 = 1

## Epic 완료 기준 (DoD)
- [ ] 4개 Story 완료
- [ ] ADR 015 발행
- [ ] Fetch Join N+1 방지 검증

## [Story 2-1] ChatRoomStatus + 도메인 메서드

목록:
- `enum ChatRoomStatus { WAITING, IN_PROGRESS, COMPLETED }`
- `chatRoom.markInProgress(adminId)` — WAITING만 허용 · 아니면 `CHT003`
- `chatRoom.markCompleted(adminId)` — IN_PROGRESS만
- 역방향 전이 시 `BusinessException(CHT003)`
- `lastStatusChangedAt` · `handledByAdminId` 필드

**SP**: 0.5d

## [Story 2-2] AdminChatController

목록:
- `GET /api/v1/admin/chat-rooms?status=&page=&size=` — 상태별 필터
- `PATCH /api/v1/admin/chat-rooms/{id}/status {targetStatus}` — 상태 전이 (@PreAuthorize)
- 상태 전이 성공 시 SYSTEM 메시지 저장 + 브로드캐스트

**SP**: 1d

## [Story 2-3] 메시지 조회 · 커서 페이징 · Fetch Join

목록:
- `ChatMessageRepository.findByChatRoomIdAndIdLessThan(roomId, lastMessageId, PageRequest)` with `JOIN FETCH sender`
- `GET /api/v1/chat-rooms/{roomId}/messages?lastMessageId=&size=`
- Hibernate Statistics 활성화 (테스트 프로파일) · 쿼리 수 검증 통합 테스트

**SP**: 1d

## [Story 2-4] 시스템 메시지 · 재연결 · ADR 015

목록:
- 시스템 메시지 senderRole=SYSTEM · 자동 삽입 (입장 · 상태 변경)
- 재연결 문서 (README) — 클라이언트가 lastMessageId 유지 · 재연결 후 재조회
- ADR 015 발행 (상태기계 역방향 금지 · 도메인 메서드 원칙)

**SP**: 0.5d
