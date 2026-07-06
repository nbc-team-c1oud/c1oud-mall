# [Outcome] 0.0.1v — 첫 배포 버전 실측 성과

> 첫 배포 버전에서 실제 완결된 Story · 머지된 PR · 도메인/기능/기술 자산 delta 실측.

---

## 총괄 요약

- **소스 규모**: `src/main/java/nbc/c1oud_mall/` — **128 Java 파일**
- **테스트 규모**: `src/test/java/` — **31 테스트 파일** (단위·슬라이스·통합·Repo)
- **도메인 개수**: **8개** (auth · product · cart · order · payment · refund · point · common)
- **컨트롤러 개수**: **11개** · 엔드포인트 대략 **26개**
- **ErrorCode 개수**: **33개** (`common.exception.ErrorCode` enum)
- **외부 연동**: PortOne V2 (조회 · 취소 · 웹훅) — 실 연동

---

## 완료된 도메인 (Product 관점)

### Payment (결제) — 40 파일
- [x] Payment Aggregate + PaymentBreakdown VO + PaymentStatus enum
- [x] `portonePaymentId` 서버 UUID 채번 + DB UNIQUE
- [x] `PaymentInitiationService` (Story 1-2)
- [x] `PaymentConfirmationService` (3대 조건 · 단일 TX)
- [x] `PaymentCompensationService` (외부 성공·내부 실패 보상)
- [x] `PortOnePaymentQueryAdapter` (V2 REST 실 어댑터)
- [x] `PortOnePaymentCancelAdapter` (전체/부분 취소 · 멱등키)
- [x] `PortOneWebhookSignatureFilter` (HMAC-SHA256 서명)
- [x] `WebhookEvent` Aggregate + UNIQUE 제약 (멱등 · INSERT-first)
- [x] `WebhookEventRegistrar` (idempotency S+ 패턴)
- [x] `PaymentController` (`POST /api/v1/payments/confirm`)
- [x] `PaymentWebhookController` (`POST /api/v1/payments/webhooks/portone`)

**엔드포인트**:
- `POST /api/v1/payments/confirm`
- `POST /api/v1/payments/webhooks/portone`

### Refund (환불) — 18 파일
- [x] Refund Aggregate + RefundItem + RefundBreakdown VO
- [x] `RefundAmountCalculator` (floor + 잔액 흡수 · ADR 009)
- [x] `RefundProcessService` (선검증 → DB 커밋 → PG 취소)
- [x] `RefundController` (`POST /api/v1/orders/{orderId}/refunds`)
- [x] BusinessException + ErrorCode `RF001~RF003` 통일 (feedback D1)
- [x] HTTP 409 응답 (RF001·RF002) · 403 (RF003) (feedback §1-2)
- [x] PG 실패 시 202 Accepted (feedback §1-3)
- [x] PortOne 취소 포트 확장 (`amount`·`requestKey` — ADR 011)

**엔드포인트**:
- `POST /api/v1/orders/{orderId}/refunds`

### Order (주문) — 14 파일
- [x] Order Aggregate + OrderItem + OrderStatus
- [x] `OrderService.completeOrder`/`cancelOrder` (PR #30 · 멱등 가드 · `REQUIRED`)
- [x] `Order.changeStatus` → `BusinessException(OD002)` 정정
- [x] `OrderFacade.createOrder`에서 `PaymentInitiationService` 실 호출 · `portonePaymentId` 실 채번
- [x] 재고 선차감 · 주문 미리보기
- [x] `OrderController` (5개 엔드포인트)

**엔드포인트**:
- `GET /api/v1/orders/preview`
- `POST /api/v1/orders`
- `GET /api/v1/orders`
- `GET /api/v1/orders/{id}`
- `PATCH /api/v1/orders/{id}/cancel`

### Cart (장바구니) — 8 파일 (v3 통합 완료)
- [x] CartItem 도메인
- [x] `CartController` — 조회 · 선택조회 · 담기 · 수량변경 · 개별삭제 · 다중삭제 · 전체비우기
- [x] JWT `@AuthenticationPrincipal` 통합 (`memberId=1L` 하드코딩 해소)

**엔드포인트** (v3):
- `GET /api/v1/carts` · `GET /api/v1/carts/selected?ids=...`
- `POST /api/v1/carts/items` · `PATCH /api/v1/carts/items/{id}`
- `DELETE /api/v1/carts/items/{id}` · `DELETE /api/v1/carts/selected?ids=...` · `DELETE /api/v1/carts`

### Auth/User (인증) — 13 파일
- [x] JWT (jjwt 0.11.5) 발급/검증
- [x] 회원가입 · 로그인 (401 시 단일 메시지)
- [x] 슈퍼어드민 초기 계정 자동 등록
- [x] Spring Security 통합 · `@AuthenticationPrincipal`
- [x] 권한 관리 (USER · ADMIN)

**엔드포인트**:
- `POST /api/v1/auth/signup` · `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`

### Product (상품) — 10 파일
- [x] Product Aggregate + Category · Status · Stock
- [x] QueryDSL 커스텀 리포지토리 (`ProductJpaRepositoryCustom`)
- [x] `deduckStock(quantity)` 도메인 메서드
- [x] `DummyDataInit` — 상품 32건 초기화 (prod 프로파일 조건부)

**엔드포인트**:
- `GET /api/v1/products` (페이징 · 검색 QueryDSL)
- `GET /api/v1/products/{id}`

### Point (포인트) — 10 파일
- [x] PointHistory 이력 (USE · EARN · USE_CANCEL · EARN_CANCEL)
- [x] PointAccount 잔액
- [x] 결제 확정 TX 안에서 포인트 사용/적립
- [x] 관리자 조회 API

**엔드포인트**:
- `GET /api/v1/points/histories`
- `GET /api/v1/admin/points/reconciliation/users/{id}`

### Common (공통) — 14 파일
- [x] `ApiResponse<T>` 표준 응답 (success · code · message · data · timestamp)
- [x] `ApiResponses` 헬퍼 (ok · created · accepted · noContent · error)
- [x] `ErrorCode` enum **33개** (C001~C004 · USER001~005 · PROD001~004 · CART001~004 · ORDER001~002 · POINT001~002 · PAY001~010 · RF001~003)
- [x] `GlobalExceptionHandler` (BusinessException · Validation · AccessDenied · Unknown)
- [x] `BaseEntity` (`createdAt` · `updatedAt` JPA Auditing)
- [x] `QuerydslConfig` (`JPAQueryFactory` Bean)
- [x] Security config · JWT filter · CORS 설정

---

## 최근 커밋 하이라이트

| Commit | 내용 | 카테고리 |
|---|---|---|
| `532d653` | chore(prod): DummyDataInit Profile을 prod로 + 더미 상품 32건 확장 | 데이터 |
| `fcc67db` | fix(prod): JPA `ddl-auto=update`로 변경 (RDS 첫 부팅) | Infra |
| `c4fc07b` | fix(ci): docker run 단일 행 + secret 값 따옴표 처리 | CI |
| Merge PR #67 | fix/prod-jpa-ddl-auto-update | Infra |
| Merge PR #65 | fix/deploy-docker-run-single-line | CI |
| PR #30 | MockOrderService 제거 · PaymentQueryService 도입 · 실 Order 연결 | 통합 |

---

## 사용자·기능 delta (0 → 0.0.1v)

- [x] 회원가입 · 로그인 (실 JWT 발급)
- [x] 상품 목록 조회 (더미 32건, 실감 있게)
- [x] 장바구니 담기 · 조회 · 선택 · 삭제
- [x] 주문 미리보기 · 생성 · 조회 · 취소
- [x] 결제 확정 (PortOne SDK → 서버 검증 → 완료)
- [x] 결제 취소 (검증 실패 시 자동 보상)
- [x] 웹훅 수신 (HMAC + 멱등)
- [x] 부분/전액 환불 (복합결제 비율 분리)
- [x] 포인트 사용/적립 (결제 확정 TX 안)
- [x] 포인트 이력 조회
- [x] (관리자) 슈퍼어드민 계정 · 사용자 포인트 조회

---

## 기술 자산 delta

### 아키텍처
- [x] DDD 4레이어 (presentation · application · domain · infrastructure) — 8개 도메인 전체 적용
- [x] `ResponseEntity<ApiResponse<T>>` 100% 컨트롤러 준수 (CLAUDE.md §4)
- [x] `BusinessException + ErrorCode` 100% 통일 (CLAUDE.md §8)
- [x] 도메인=JPA Entity 통합 방식(A) (`.claude/rules/persistence.md`)

### 정합성 (`.claude/rules/consitency.md`)
- [x] TX-bound zone: 결제확정 · 환불 · 주문생성 · 주문취소 (4개 zone 실현)
- [x] External-reconciled zone: PortOne 결제 (별도 zone · 재조회 · 재검증)
- [x] 락 순서 규범: Order → Payment → Point → Inventory

### 멱등성 (`.claude/rules/idempotency.md`)
- [x] 결제 확정 — `portonePaymentId` (S+ · DB UNIQUE + 사전조회)
- [x] 웹훅 — HMAC + 상태 기반 + `WebhookEvent` UNIQUE
- [x] 환불 — 비즈 식별자 + 잔여 수량 검증 (A)
- [x] 회원가입 — email UNIQUE (S)
- [x] 장바구니 담기 — memberId+productId (A · 수량 합산)

### 테스트
- [x] 단위 테스트 ~12건: `PaymentTest`, `RefundTest`, `PaymentAmountCalculatorTest`, `RefundAmountCalculatorTest`, `ApiResponseTest`, `WebhookSignatureVerifierTest` 등
- [x] 슬라이스 테스트 ~6건: `PaymentControllerSliceTest`, `PaymentWebhookControllerSliceTest`, `RefundControllerSliceTest`, `AuthControllerTest` 등
- [x] 통합 테스트 ~10건: `PaymentInitiationServiceIntegrationTest`, `PaymentConfirmationServiceIntegrationTest`, `PaymentWebhookControllerIntegrationTest`, `PaymentConfirmationPointAccrualIntegrationTest`, `RefundProcessServiceIntegrationTest`, `WebhookIdempotencyIntegrationTest`
- [x] Repository/Adapter 테스트 ~3건: `PaymentJpaRepositoryTest`, `PortOnePaymentQueryAdapterTest`, `PortOnePaymentCancelAdapterTest`

---

## 미완료 이월 (M2로)

| Product | Story | 사유 |
|---|---|---|
| Log (관측성) | Epic 1~4 전부 (Story 5건 · 13 SP) | 배포 안정화 우선 · 실 트래픽 발생 전 |
| AI Suggestion | Epic 7~11 전부 (Story 14건) | Gemini API 키/quota 미확보 · 초기 필수 아님 |
| Fix F1 | Payment self-injection 함정 | M2 대응 |
| Fix — Cart 삭제 위치 | OrderFacade로 이관 | M2 대응 |
| Infra CORS 분기 | prod 하드코딩 잔존 | Log Epic 1과 함께 |

---

## KPI 관측 (v0.0.1v 시점)

| 지표 | 목표 | 이번 버전 결과 |
|---|---|---|
| 결제 도메인 SDD Product-level DoD | 통과 | ✅ Epic 1~3 완료 |
| 환불 도메인 SDD Product-level DoD | 통과 | ✅ Epic 1~2 완료 |
| 8개 도메인 골격 완성 | 100% | ✅ 100% (Log · AI 제외) |
| 실 PortOne 연동 | 가능 | ✅ V2 조회 · 취소 · 웹훅 |
| ARM64 Docker 배포 | 성공 | ✅ EC2 pull → run |
| RDS 첫 부팅 자동 스키마 | 성공 | ✅ `ddl-auto=update` |
| 테스트 파일 수 | ≥ 20 | ✅ 31개 |
| ErrorCode 카탈로그 | 도메인당 3+ | ✅ 33개 · 도메인당 2~7개 |

---

## 참조

- Milestone: [`milestone.md`](./milestone.md)
- Infra 상세: [`infra.md`](./infra.md)
- 비용 결산: [`cost.md`](./cost.md)
- 회고: [`review.md`](./review.md)
