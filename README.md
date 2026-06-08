# c1oud-mall

내일배움캠프 Spring 심화 팀 프로젝트 — 결제·환불·재고·포인트가 정합성 있게 연동되는 **쇼핑몰 백엔드**.

PortOne V2 연동을 통한 실제 PG 결제 흐름, DDD + 레이어드 아키텍처 기반 7개 Bounded Context, 비관적 락·이벤트 기반 정합성 설계를 핵심으로 한다.

---

## 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 언어·런타임 | Java 21 |
| 프레임워크 | Spring Boot **4.0.6** (Spring 7 / Spring Security 7) |
| 영속성 | Spring Data JPA, Hibernate, QueryDSL 6.x |
| DB | H2 (dev/test), MySQL (운영 예정) |
| 인증 | JWT (jjwt 0.11.5), Spring Security |
| 외부 연동 | PortOne V2 REST API (RestClient 기반) |
| 빌드 | Gradle Kotlin DSL |
| 테스트 | JUnit 5, Mockito, AssertJ, MockWebServer |

---

## 아키텍처

### DDD + 레이어드 아키텍처

```
presentation ──▶ application ──▶ domain ◀── infrastructure
```

- **domain**: 비즈니스 규칙·상태 전이. 외부 기술에 무지.
- **application**: 유스케이스 조합, 트랜잭션 경계 (`@Transactional`).
- **presentation**: HTTP 입출력, 검증, Request↔Command 매핑.
- **infrastructure**: JPA 영속, 외부 API 어댑터, QueryDSL 조회.

의존성 방향은 항상 domain을 향한다. domain은 어느 레이어에도 의존하지 않는다.

### Bounded Context (7개)

```
nbc.c1oud_mall
├── auth        # 인증·인가 (User, JWT, 권한 승격)
├── product     # 상품 카탈로그, 재고 비관락 관리
├── cart        # 장바구니
├── order       # 주문 생성·확정·취소, OrderItem 가격 스냅샷
├── payment     # PortOne 결제 확정·웹훅·보상 트랜잭션
├── point       # 포인트 적립·차감·회수, 원장 정합성 검증
├── refund      # 부분/전액 환불, 비관락 + 잔여 수량 재검증
└── common      # 공통 응답 래퍼, 예외, JWT, 보안 설정
```

각 컨텍스트는 위 4개 레이어 패키지를 가진다.

### 정합성·동시성 설계 핵심

- **TX-bound zone (consistency.md §1)**: 결제 확정·환불·주문 취소를 단일 트랜잭션으로 묶어 즉시 일관성 보장
- **비관적 락 순서 통일** (`Order → Payment → Point → Inventory`): 데드락 방지
- **재고 차감/복구**: `Product.findByIdForUpdate` 비관락 + `productId` 정렬 루프 (`OrderFacade.createOrder` 패턴)
- **외부 호출은 트랜잭션 밖**: 환불 시 DB 커밋 → PortOne 부분취소 호출 (consistency.md §6)
- **멱등성**: 결제 확정은 `portonePaymentId` UNIQUE + 사전조회 이중(S+ 등급), 환불은 비관락 + 잔여수량 재검증(A 등급), 웹훅은 `(portonePaymentId, eventType)` INSERT-first

---

## 주요 도메인 흐름

### 주문 → 결제 흐름

1. `POST /api/v1/orders` (`OrderFacade.createOrder`) — 장바구니 항목으로 Order 생성, **상품 재고 차감**, Payment 사전등록(`portonePaymentId` 채번)
2. 프론트엔드가 PortOne SDK로 결제 진행
3. `POST /api/v1/payments/confirm` (`PaymentConfirmationService.confirm`) — PortOne 재조회로 금액·상태 검증, Payment.COMPLETED, Order.CONFIRMED, 포인트 차감/적립, 장바구니 비우기
4. `POST /api/v1/payments/webhooks` — 같은 도메인 서비스 공유로 양방향 동기화

### 환불 흐름

`POST /api/v1/orders/{orderId}/refunds` (`RefundProcessService.process`)

1. 선검증 (락 없는 fast-fail): 소유권 / Payment 상태 / 잔여 수량
2. DB 트랜잭션: Payment 비관락 → 잔여 수량 재검증 → Refund 저장 → 포인트/재고 복구
3. TX 밖: PortOne 부분취소 호출 → 성공 시 별도 TX로 `Refund.markPgCancelled`
4. 응답: 200(PG_CANCELLED) / 202(DB_COMMITTED with warning)

---

## 주요 API 엔드포인트

| Method | Path | 도메인 |
|---|---|---|
| POST | `/api/v1/auth/signup` · `/login` | 인증 |
| GET | `/api/v1/products` · `/{id}` | 상품 조회 |
| POST | `/api/v1/carts` | 장바구니 |
| POST | `/api/v1/orders` | 주문 생성 |
| GET | `/api/v1/orders` · `/{id}` | 주문 내역 |
| PATCH | `/api/v1/orders/{id}/cancel` | 주문 취소 (PENDING만) |
| POST | `/api/v1/payments/confirm` | 결제 확정 |
| POST | `/api/v1/payments/webhooks` | PortOne 웹훅 (HMAC 서명 인증) |
| POST | `/api/v1/orders/{id}/refunds` | 환불 (COMPLETED Payment만) |
| GET | `/api/v1/points/histories` | 포인트 내역 |
| GET | `/api/v1/admin/points/reconciliation/{userId}` | 포인트 원장 정합성 검증 |
| POST | `/api/v1/admin/users/{id}/role` | 권한 승격 (SUPER_ADMIN) |

응답은 모두 `ApiResponse<T>` 공통 래퍼로 통일 (성공/실패 동일 포맷).

---

## 실행 방법

### 사전 요구사항

- JDK 21+
- (선택) PortOne 콘솔 가입 → API Secret + Webhook Secret 발급

### 로컬 실행

```bash
./gradlew bootRun
```

기본 dev 프로파일 활성, H2 in-memory DB 사용. 부팅 후:

- API: `http://localhost:8080`
- H2 콘솔: `http://localhost:8080/h2-console` (JDBC: `jdbc:h2:mem:c1oud_mall`, user: `sa`)

### 환경변수 (운영 오버라이드)

```bash
export PORTONE_API_SECRET=...
export PORTONE_WEBHOOK_SECRET=whsec_...
export JWT_SECRET=...
export SUPER_ADMIN_EMAIL=...
export SUPER_ADMIN_PASSWORD=...
```

dev 프로파일은 위 값들 모두 placeholder 기본값 제공 → 별도 설정 없이도 부팅 가능.

### 테스트

```bash
./gradlew test
```

---

## 프로젝트 구조

```
c1oud_mall/
├── src/main/java/nbc/c1oud_mall/
│   ├── auth/ cart/ order/ payment/ point/ product/ refund/    # 7 Bounded Context
│   │   ├── presentation/   # @RestController, Request/Response DTO
│   │   ├── application/    # @Service, Command/Query DTO
│   │   ├── domain/         # 엔티티, VO, 도메인 메서드
│   │   ├── infrastructure/ # JPA Repository, 외부 API 어댑터
│   │   └── docs/adr/       # 도메인별 ADR (Architecture Decision Record)
│   ├── common/
│   │   ├── response/       # ApiResponse, ApiResponses 헬퍼
│   │   ├── exception/      # ErrorCode, BusinessException, GlobalExceptionHandler
│   │   ├── security/       # SecurityConfig, JwtAuthFilter
│   │   └── config/         # JpaConfig, QuerydslConfig
│   └── C1oudMallApplication.java
├── src/main/resources/
│   ├── application.yml          # 공통 + 적립률 설정
│   └── application-dev.yml      # dev 프로파일 (H2, placeholder 기본값)
└── src/test/                    # 단위·슬라이스·통합 테스트
```

---

## 주요 설계 결정 (ADR)

도메인별 의사결정 기록이 `src/main/java/nbc/c1oud_mall/<도메인>/docs/adr/`에 위치.

| 도메인 | ADR | 결정 |
|---|---|---|
| order | 0001 | 재고 차감 비관락 + productId 정렬로 데드락 방지 |
| payment | 0001 | `portonePaymentId` 서버 채번 (UUID, 멱등 키 이중 역할) |
| payment | 0002 | PortOne HTTP 클라이언트로 RestClient 채택 |
| payment | 0003 | BC 협력 mock 클래스 + 사후 실구현 교체 정책 |
| payment | 0004 | 결제 보상 트랜잭션 (REQUIRES_NEW + DB 커밋 후 PortOne 취소) |
| payment | 0005 | Webhook HMAC 서명 검증 |
| payment | 0006 | Webhook 동기 처리 (같은 도메인 서비스 공유) |
| payment | 0007 | Webhook 멱등성: `(portonePaymentId, eventType)` INSERT-first |
| payment | 0009 | 적립 포인트 정책: totalAmount × 1% (basis points 외부화) |
| refund | 0008 | 환불 금액 분리: PG floor + 포인트 잔액 흡수 (사용자 무손해) |

---

## 컨벤션

- **DTO**: 레이어마다 타입 분리 (`Request`/`Command`/`Query`/`Projection`/`Response`)
- **예외**: `BusinessException` + `ErrorCode` enum 단일화 (도메인별 예외 클래스 신설 금지)
- **응답**: 모든 HTTP 응답 `ApiResponse<T>` 래퍼
- **Git**: Conventional Commits (`feat`/`fix`/`refactor`/`docs`/`test`/`chore` 등), Squash merge

상세 컨벤션은 `CLAUDE.md`, `.claude/rules/`에 정리되어 있음.
