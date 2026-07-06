# M7 / 0.0.7v — CS Chat 전 Epic 완주 · Week of 2026-07-17 ~ 2026-07-19 (D1 = 2026-07-17 Fri)

> **마일스톤의 역할**: M6가 07-16에 동결됐다 (Timesale 3 Epic 완주 · 락 3전략 비교 리포트 · ADR 013). 본 M7은 Ops Backend 21일 로드맵의 **네 번째** 마일스톤으로 **product-cs-chat 전 Epic 완주**를 목표로 한다. WebSocket + STOMP + JWT ChannelInterceptor 기반 실시간 CS 응대 채널과 WAITING → IN_PROGRESS → COMPLETED 상태기계, 커서 페이징 + Fetch Join 조회를 완결해 캠프 요구사항의 실시간 채팅 필수(주제 B)를 충족한다.
>
> 한 버전 = `version/0.0.Xv/` 폴더 하나. 본 버전(0.0.7v)에는:
> - `milestone.md` *(본 문서)*
> - `outcome.md`·`review.md` — 완주 후 소급
>
> `infra.md`·`performance.md`·`cost.md` 스킵.

**릴리스 대응**: Ops Backend 21일 로드맵의 **네 번째** 마일스톤. 상위 M3(`../0.0.3v/milestone.md`) §Week 2 후반 참조.

**SDD 원본**: `workflows/task/pes/workspectrum/sdd/in-progress/product-cs-chat.md` — Product 3 CS 채팅 (2 Epic · Story ~8개 · SP ~6).

---

## 0.0.7v 스코프 결정 (M6 이후, 2026-07-17)

**M6 착지 결과 요약**:
- ✅ Timesale Epic 1·2·3 완주 (도메인 · **락 3전략 벤치마크** · 쿠폰)
- ✅ ADR 013 발행 · consitency.md §5 갱신 · idempotency.md §2 갱신
- ✅ 이력서 파괴력 최상 축 정착 (`benchmark-report.md` · README §Concurrency)

**M7 축 결정 — product-cs-chat 전 Epic 완주**:

CS 채팅은 백오피스 프레이밍의 3대 축(타임세일 · 쿠폰 · CS)의 마지막. 관리자가 상담사 역할로 실시간 응대하고 상태기계로 진행 상태를 관리. 캠프 요구사항 실시간 채팅 필수 항목(주제 B) 충족. **주력 (~100%)**.

**Epic 단위 PR 2건 예정 (본주 목표 · 3일 스판)**:

| Epic PR | 대응 | 예상 SP | 종료 목표 |
|---|---|---|---|
| PR#1 (CHT-E1-STOMP) | Chat Epic 1 Story 1-1~1-4 (WebSocketConfig · StompAuthInterceptor · ChatRoom·ChatMessage 엔티티 · 고객 API · WsChatController) | 3 | D2 |
| PR#2 (CHT-E2-STATE-CURSOR) | Chat Epic 2 Story 2-1~2-4 (ChatRoomStatus enum · 도메인 메서드 · AdminChatController · 커서 페이징 + Fetch Join · 시스템 메시지 + 재연결 + **ADR 015**) | 3 | D3 |

**Total: 2 Epic PR · 총 ~6 SP**.

**본 버전 제외 사유**:
- **Redis Pub/Sub 다중 서버 브로드캐스팅** — Product SDD §Out of Scope (v0.0.4v+ · 캠프 도전 별도 이슈)
- **파일·이미지 첨부** — 스코프 오버 (v0.0.4v+ · Pre-signed URL 통합)
- **리치 메시지 (카드 등)** — 스코프 오버
- **안 읽은 카운트 · 읽음 표시** — 스코프 오버 (v0.0.4v+)
- **관리자 담당자 배정 · 이력** — 초기 무배정 · 상태만
- **채팅 검색 · 필터** — 스코프 오버

---

## 진행 중 Product 잔여 인벤토리 (Before/After — M7 진입 시 vs 종료 후 예상)

| Product | 총 Story | M7 진입 시 완료 | M7 진입 시 잔여 | M7 대상 | **M7 종료 후 예상 잔여** | **해결율** |
| --- | --- | --- | --- | --- | --- | --- |
| Admin Backoffice | 8 | 4 (E1) | 4 (E2 D20) | — | 4 | 0% |
| Search & Cache | 14 | 8 (E1+E2) | 6 (E3 D20~21) | — | 6 | 0% |
| Timesale Concurrency | 12 | 12 (E1·E2·E3) | 0 | — | 0 | ✅ 완주 (M6) |
| **CS Chat** (`product-cs-chat.md` · 2 Epic) | **~8** | 0 | 8 | **8 Story (E1·E2)** | **0** | **100%** ✅ 완주 |
| Perf Lab | 13 | 4 (E1) | 9 | — | 9 | 0% (M8) |
| **합계** | **~55** | 28 | 27 | **8 Story · 2 Epic PR · 6 SP** | **~19** | **30%** ↑ |

### 📊 M7 예상 성과 카드

- **총 해결 대상**: 8 Story (전체 잔여의 **30% 소진**)
- **완주 예상 SDD Product**:
  - `product-cs-chat.md` Epic 1·2 — **완주 100%** (CS Chat 완결)
- **완주 예상 산출물**:
  - `nbc.c1oud_mall.chat.*` 컨텍스트 (4레이어)
  - `WebSocketConfig` (@EnableWebSocketMessageBroker · SimpleBroker `/sub` · prefix `/pub`)
  - `StompAuthInterceptor` (ChannelInterceptor · CONNECT JWT 검증 · Principal 세팅)
  - `ChatRoom` · `ChatMessage` 엔티티 (`chat_room (customer_user_id, status)` · `chat_message (chat_room_id, id DESC)` 인덱스)
  - `ChatRoomStatus` enum (WAITING · IN_PROGRESS · COMPLETED) · 역방향 금지
  - 도메인 메서드 (`markInProgress(adminId)` · `markCompleted(adminId)`)
  - 고객 API (개설 · 본인 목록 · 메시지 조회)
  - 관리자 API (전체 목록 · 상태 필터 · 상태 전이 @PreAuthorize)
  - `WsChatController @MessageMapping("/chat/{roomId}")` + `simpMessagingTemplate.convertAndSend`
  - 커서 페이징 (`WHERE m.id < :lastMessageId ORDER BY m.id DESC`) + JOIN FETCH sender
  - Soft Delete (`@SQLDelete` + `@SQLRestriction`)
  - 시스템 메시지 (입장·상태 변경 자동)
  - 재연결 전략 (lastMessageId 기반 재조회 · README 문서화)
  - **ADR 015 발행** (CS 채팅 상태기계 · 역방향 금지)
  - `ErrorCode.CHT001~005` 등록
  - Micrometer `chat.message.total{sender_role, room_status}` · `chat.stomp.connect.total{result}` · `chat.status.transition.total{from, to}` · `chat.message.query.duration_seconds`
- **M7 종료 후 남는 것**:
  - Search E3 (Redis Remote D20~D21 예정)
  - Perf Lab E2·E3 (M8)
  - Admin Dashboard (E2 D20)

---

## Epic PR 매트릭스

### Epic PR #1 — `CHT-E1-STOMP` (STOMP + JWT ChannelInterceptor + 개설·전송)

**Base 브랜치**: `feature/cs-chat-stomp-jwt`

**SDD 위치**:
- Epic: `# [Epic 1] STOMP 골격 · JWT ChannelInterceptor · 채팅방 개설·전송`
- Story 범위: `## [Story 1-1]` ~ `## [Story 1-4]`
- 상위 이슈 원천: `issue-09-cs-chat-stomp-state-machine.md`

| # | Story | 한 줄 | SP |
|---|---|---|---|
| 1 | CHT E1 S1-1 | `WebSocketConfig implements WebSocketMessageBrokerConfigurer` (`/ws/chat` · SockJS · SimpleBroker /sub · prefix /pub) + `StompAuthInterceptor implements ChannelInterceptor` (CONNECT 프레임 JWT 검증 + Principal 세팅) | 1 |
| 2 | CHT E1 S1-2 | `ChatRoom` 엔티티 (id · roomUuid · customerUserId · status · createdAt · updatedAt · lastMessageAt · deletedAt) + `ChatMessage` 엔티티 (id · chatRoomId · senderUserId · senderRole · content · createdAt · deletedAt) + @SQLDelete + @SQLRestriction + 인덱스 (`chat_room (customer_user_id, status)` · `chat_message (chat_room_id, id DESC)`) | 0.5 |
| 3 | CHT E1 S1-3 | 고객 API (`POST /api/v1/chat-rooms` 개설 · `GET /api/v1/chat-rooms/my` 본인 목록) + `WsChatController @MessageMapping("/chat/{roomId}")` (Principal p 활용 · 저장 + simpMessagingTemplate.convertAndSend) | 1 |
| 4 | CHT E1 S1-4 | 통합 테스트 (`WebSocketStompClient` · 연결·전송·구독 시나리오 3건 · JWT 정상/만료/헤더 없음) + `ErrorCode.CHT004` (STOMP 인증 실패) | 0.5 |

**PR 종료 신호**:
- STOMP CONNECT with 유효 JWT → CONNECTED · Principal.userId 세팅
- STOMP CONNECT with 만료 JWT → ERROR 프레임 · CHT004
- POST /chat-rooms (고객 JWT) → 201 · roomUuid 반환 · status=WAITING
- STOMP SEND /pub/chat/{roomId} → ChatMessage 저장 + /sub/chat/{roomId} 브로드캐스트
- 구독자 모두 메시지 수신 확인 (통합 테스트)

**의존**: M4 Admin (JWT 기반).

### Epic PR #2 — `CHT-E2-STATE-CURSOR` (상태기계 + 관리자 API + 커서 페이징 + ADR 015)

**Base 브랜치**: `feature/cs-chat-state-machine-cursor-paging`

**SDD 위치**:
- Epic: `# [Epic 2] 상태기계 · 관리자 API · 커서 페이징 · 재연결`
- Story 범위: `## [Story 2-1]` ~ `## [Story 2-4]`
- 상위 이슈 원천: `issue-09-cs-chat-stomp-state-machine.md`

| # | Story | 한 줄 | SP |
|---|---|---|---|
| 1 | CHT E2 S2-1 | `ChatRoomStatus` enum + 도메인 메서드 `markInProgress(adminId)` (WAITING만 · 아니면 CHT003) · `markCompleted(adminId)` (IN_PROGRESS만) · **역방향 금지 검증** + `lastStatusChangedAt` · `handledByAdminId` 필드 | 0.5 |
| 2 | CHT E2 S2-2 | `AdminChatController` (`GET /admin/chat-rooms?status=&page=&size=` 상태별 필터 + `PATCH /admin/chat-rooms/{id}/status` 상태 전이 @PreAuthorize) + 상태 전이 성공 시 SYSTEM 메시지 저장 + 브로드캐스트 | 1 |
| 3 | CHT E2 S2-3 | `ChatMessageRepository.findByChatRoomIdAndIdLessThan(roomId, lastMessageId, PageRequest) with JOIN FETCH sender` + `GET /chat-rooms/{roomId}/messages?lastMessageId=&size=` + Hibernate Statistics 통합 테스트 (쿼리 수 = 1 확인 · N+1 방지) | 1 |
| 4 | CHT E2 S2-4 | 시스템 메시지 senderRole=SYSTEM 자동 삽입 (입장 · 상태 변경) + 재연결 전략 README 문서화 (lastMessageId 유지 · 재조회) + **ADR 015 발행** | 0.5 |

**PR 종료 신호**:
- WAITING 방에 관리자가 `PATCH ... {IN_PROGRESS}` → 200 · status 전이 · SYSTEM 메시지 브로드캐스트
- COMPLETED 방에 `PATCH ... {WAITING}` (역방향) → 400 · CHT003
- 채팅방에 메시지 100개 있는 상태에서 `GET .../messages?lastMessageId=50&size=20` → 20개 반환 (id 30~49 · DESC) · Hibernate Statistics 쿼리 수 = 1
- ADR 015 발행 (`docs/adr/015-cs-chat-state-machine.md` · 역방향 금지 · 도메인 메서드 원칙)
- README §Real-time (CS Chat) 섹션 (STOMP + 상태기계 다이어그램)

**의존**: PR#1 완주.

**Reviewer 세션**: 5관점 발사. **특히 Test Reviewer가 "Hibernate Statistics로 N+1 방지 검증"을 · Domain Reviewer가 "역방향 전이 금지 도메인 메서드 캡슐화"를 판정**.

---

## Story 카테고리별 합계

| 카테고리 | SDD Epic/Story | Story 수 | SP | 비중 |
| --- | --- | --- | --- | --- |
| Chat Epic 1 (STOMP · 인증 · 개설·전송) | CHT E1 S1-1~S1-4 | 4 | 3 | 50% |
| Chat Epic 2 (상태기계 · 관리자 · 커서 페이징) | CHT E2 S2-1~S2-4 | 4 | 3 | 50% |
| **합계** | | **8** | **6** | 100% |

---

## 종료 신호 — "STOMP 연결 완주 + 상태기계 정합 + N+1 방지 + ADR 015"

본 M7 종료 시점에 다음이 모두 성립해야 한다. (7 신호 중 6개 이상 → 0.0.7v 동결)

- [ ] **머지 신호**: Epic PR 2개 모두 머지 (100%)
- [ ] **STOMP 신호**: STOMP CONNECT with 유효 JWT → CONNECTED · Principal.userId 세팅 · 만료 JWT → ERROR · CHT004
- [ ] **메시지 전송 신호**: STOMP SEND → 저장 + 브로드캐스트 · 통합 테스트 3건 통과
- [ ] **상태 전이 신호**: WAITING → IN_PROGRESS → COMPLETED 정상 · 역방향 400 CHT003 · SYSTEM 메시지 자동 삽입
- [ ] **커서 페이징 신호**: `lastMessageId` 기반 조회 · id DESC · 지정 size · 재연결 시나리오 통합 테스트
- [ ] **Fetch Join 신호**: Hibernate Statistics 쿼리 수 = 1 (20개 조회 · N+1 방지 검증)
- [ ] **ADR 신호**: ADR 015 발행 · README §Real-time 섹션 · 다이어그램

**미합격 처리**: 6 신호 미만 시 0.0.7.1v 패치 발행 → M8 진입 지연.

---

## 의존 chain

```
[선행: M4·M5·M6 착지]
Admin 컨텍스트 · JWT ✅ 완료 (M4)
Redis Lettuce (M5) ✅ 확보 (본 M7 SimpleBroker이라 미필요 · v0.0.4v+ Pub/Sub 대비)
Timesale Product 완주 ✅ (M6)

[D1: 착수]
PR#1 (CHT-E1-STOMP)  단독 진입
  CHT E1 S1-1 ~ S1-4
     │
     ▼
PR#2 (CHT-E2-STATE-CURSOR)
  CHT E2 S2-1 ~ S2-4 (ADR 015)
     │
     ▼
CS Chat Product 완주
```

**병렬 진입 가능 묶음**:
- **A** (D1 · 07-17 Fri): PR#1 착수 (CHT E1 S1-1 WebSocketConfig + StompAuthInterceptor) + S1-2 (엔티티 · 인덱스) 진행
- **B** (D2 · 07-18 Sat): PR#1 S1-3 (고객 API + WsChatController) + S1-4 (통합 테스트 + CHT004) 완주 → **PR#1 머지 + Reviewer**
- **C** (D3 · 07-19 Sun): PR#2 착수 (CHT E2 S2-1 상태기계 도메인) + S2-2 (AdminChatController + SYSTEM 메시지) + S2-3 (커서 페이징 + Fetch Join + N+1 검증) + S2-4 (재연결 문서 + ADR 015) 완주 → **PR#2 머지 + Reviewer 세션 (Test·Domain 강조)** · 0.0.7v 동결

---

## 작업 일정 (Day별 체크리스트)

D1 = 2026-07-17 (Fri). 종료 D3 = 2026-07-19 (Sun). 3일 안에 2 Epic PR.

| 일 | 날짜 | 잡힌 작업 |
| --- | --- | --- |
| D1 (금) | 07-17 | PR#1 착수 (**CHT E1 S1-1** WebSocketConfig + StompAuthInterceptor · 통합 인증 검증) + **S1-2** (ChatRoom · ChatMessage 엔티티 · 인덱스) 진행 |
| D2 (토) | 07-18 | PR#1 **S1-3** (고객 API + WsChatController @MessageMapping) + **S1-4** (WebSocketStompClient 통합 테스트 · CHT004) 완주 → **PR#1 머지 + Reviewer 세션** |
| D3 (일) | 07-19 | PR#2 착수 (**CHT E2 S2-1** ChatRoomStatus + 도메인 메서드 역방향 금지) + **S2-2** (AdminChatController + 상태 전이 API + SYSTEM 메시지 브로드캐스트) + **S2-3** (커서 페이징 + Fetch Join + Hibernate Statistics N+1 검증) + **S2-4** (재연결 README + ADR 015) 완주 → **PR#2 머지 + Reviewer 세션 (Test 강조 · N+1 검증)** · 0.0.7v 동결 |

---

## 리스크와 관찰 포인트

| 영역 | 리스크 | 관찰 포인트·완화 |
| --- | --- | --- |
| StompAuthInterceptor JWT 검증 실패 처리 | ERROR 프레임 반환 시 클라이언트 재연결 루프 위험 | 통합 테스트에 만료 JWT · 헤더 없음 시나리오 · 클라이언트 재로그인 유도 명시 |
| SimpleBroker 단일 인스턴스 전제 | 다중 서버 배포 시 메시지 유실 · Pub/Sub 이관 필요 | Product SDD §Out of Scope 명시 · v0.0.4v+ 별도 이슈로 관리 |
| Fetch Join N+1 방지 검증 | Hibernate Statistics 활성화 없이는 검증 불가 | @SpringBootTest에 `spring.jpa.properties.hibernate.generate_statistics=true` · 테스트에서 Statistics.getEntityFetchCount 등 확인 |
| 상태 전이 도메인 캡슐화 | 서비스에서 직접 `chatRoom.setStatus(...)` 호출 시 검증 우회 리스크 | Setter 금지 · 도메인 메서드만 노출 · Domain Reviewer 검증 |
| 커서 페이징 id 순서 | `WHERE id < :lastMessageId ORDER BY id DESC` 정합 · 삭제 메시지 있을 때 gap | Soft Delete `@SQLRestriction`으로 필터 자동 · gap OK |
| STOMP 세션 timeout · heartbeat | 초기 default · 문제 시 조정 | Product SDD §Open Question Q1 관찰만 |
| Reviewer 세션 3일 시한 | 2 Epic PR · Reviewer 2회 · 3일 스판 · 시간 부담 | D2 진행률 확인 → 미달 시 S2-4 재연결 문서만 M8 이관 · ADR 015는 필수 유지 |

---

## 다음 마일스톤 (M8 / 0.0.8v) 후보

**M8 / 0.0.8v (07-20 ~ 07-24) — Perf Lab 잔여 Epic 완주 + Search E3 + Admin E2 최종**
- **Perf-Lab Epic 2 (k6 부하)** — v1/v2 · 락 3전략 부하 리포트 · vUser Ramp Up · TPS · Saturation
- **Perf-Lab Epic 3 (인덱싱)** — EXPLAIN Before/After · 복합 인덱스 · 커버링 인덱스 · **ADR 016**
- **Search Epic 3** — Redis Remote + Eviction (M5 D15~16 대기 배치 흡수)
- **Admin Epic 2** — Dashboard 통합·세부 API (D20 대기 흡수)

**Epic PR 예상 4건** (총 ~9.5 SP).

---

## Product 상태 전환 신호 (M7 종료 시)

- `in-progress/product-cs-chat.md` — **Epic 1·2 완주** 표기 (CS Chat 완결)
- `fix/brainstorming/version/0.0.3v/issue-09-cs-chat-stomp-state-machine.md` → **resolved**
- **ADR 015 발행 완료** (CS 채팅 상태기계)
- README §Real-time (CS Chat) 섹션 완성

---

## brainstorming 트리거

본 M7 완료 후 `workflows/task/pes/brainstorming/0.0.7v/` 신설 (선택):
- Redis Pub/Sub 다중 서버 이관 시점 (`brainstorming/0.0.7v/chat-redis-pubsub-adoption.md`) — v0.0.4v+ 캠프 도전 여부
- 파일·이미지 첨부 UX (`brainstorming/0.0.7v/chat-attachment-flow.md`) — Pre-signed URL 통합 v0.0.4v+
- 관리자 담당자 배정 정책 (`brainstorming/0.0.7v/admin-agent-assignment.md`) — Multi-agent 상담 시 v0.0.4v+

---

## 참고

- SDD: `../../pes/workspectrum/sdd/in-progress/product-cs-chat.md`
- 대응 이슈: `../../fix/brainstorming/version/0.0.3v/issue-09-cs-chat-stomp-state-machine.md`
- 상위 마일스톤: `../0.0.3v/milestone.md`
- 이전 마일스톤: `../0.0.6v/milestone.md` — M6 · Timesale 완주 (본 프로젝트 정면)
- 회의록: `C:\Users\user\.claude\plans\splendid-finding-feather.md`
- **주요 SDD 참조 비율**:
  - `product-cs-chat.md` **100%** (Epic 1·2 · Story 1-1~2-4 · 8 Story · SP 6)
