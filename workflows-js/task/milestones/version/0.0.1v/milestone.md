# M1 / 0.0.1v — 첫 배포 릴리즈 (frozen)

> **역할**: c1oud-mall의 첫 실 배포 버전(0.0.1v)에서 달성한 스코프·완료 신호·다음 마일스톤 진입 결정을 정리.
> **상태**: **frozen** (배포 완료, 다음 버전 M2로 전환 대기)

---

## 스코프 요약 (한 줄)

> **8개 도메인 골격 완성 + PortOne 실 연동 + 환불 흐름 + EC2/DockerHub CI/CD로 첫 프로덕션 배포 성공.**

---

## 진행 중 Product 결과 인벤토리 (0.0.1v 기준)

| Product | 총 Story | 완료 | 잔여 | 상태 |
|---|---|---|---|---|
| Payment (결제) | 9 | **9** | 0 | ✅ Epic 1~3 전체 완료 (PortOne 실 연동 · 웹훅 멱등) |
| Refund (환불) | 5 | **5** | 0 | ✅ Epic 1~2 전체 완료 (부분 환불 · 복합결제 분리) |
| Order (주문) | — | 대부분 | 잔여 조회 연결 | ✅ Order-Payment 통합(PR #30) |
| Cart (장바구니) | — | 완료 | — | ✅ v3 통합 완료 (조회·선택·삭제·인증) |
| Product (상품) | — | 완료 | — | ✅ QueryDSL 검색 · 페이지네이션 |
| Auth (인증) | — | 완료 | — | ✅ JWT · 회원가입/로그인 · 슈퍼어드민 · 권한 |
| Point (포인트) | — | 완료 | — | ✅ 적립/사용 · 잔액 · 거래 이력 · 관리자 조회 |
| Common (공통) | — | 완료 | — | ✅ ApiResponse · 33개 ErrorCode · BaseEntity · QueryDSL 설정 |
| Log (관측성) | 4 | 0 | 4 | ⏸️ 미착수 → M2로 이월 |
| AI Suggestion | 14 | 0 | 14 | ⏸️ 미착수 (초기 계획에서 제외) → M2/M3 검토 |

**총 코드 규모**: `src/main/java/nbc/c1oud_mall/` **128 Java 파일** · `src/test/java/` **31 테스트 파일**

---

## 완료된 스코프 (Must Tier — 실측 달성)

### Tier 1 · Must (완료 ✅)

| # | 도메인 | 구현 성과 | 근거 |
|---|---|---|---|
| 1 | Payment | Payment Aggregate + `portonePaymentId` UUID 채번 + DB UNIQUE | `payment.domain.Payment` · UK `portone_payment_id` |
| 2 | Payment | `PaymentInitiationService` + `PaymentConfirmationService` + `PaymentCompensationService` 3단 흐름 | `payment.application.*` |
| 3 | Payment | PortOne V2 실 어댑터 (`PortOnePaymentQueryAdapter` · `PortOnePaymentCancelAdapter`) | `payment.infrastructure` |
| 4 | Payment | 웹훅 HMAC-SHA256 서명 검증 + `WebhookEvent` 멱등 (INSERT-first · UNIQUE) | `PortOneWebhookSignatureFilter` · `WebhookSignatureVerifier` · `WebhookEventRegistrar` |
| 5 | Refund | Refund Aggregate + RefundItem + 잔여 수량 추적 | `refund.domain.*` |
| 6 | Refund | 부분 환불 · 복합결제 비율 분리 (floor + 잔액 흡수) | `RefundAmountCalculator` · ADR 009 |
| 7 | Refund | 선검증 → DB 커밋 → PG 취소 흐름 (`RefundProcessService`) | `refund.application` |
| 8 | Order | `OrderService.completeOrder`/`cancelOrder` 신설 + 멱등 가드 (PR #30) | `order.application.OrderService` |
| 9 | Order | `Order.changeStatus` → `BusinessException(OD002)` 정정 | `order.domain.Order` |
| 10 | Order | `OrderFacade`에서 `PaymentInitiationService` 호출로 `portonePaymentId` 실 채번 | `OrderFacade.createOrder` |
| 11 | Cart | `GET /api/v1/carts` · `GET /carts/selected` · 선택/개별 삭제 + JWT 통합 | `cart.presentation.CartController` |
| 12 | Auth | JWT (jjwt 0.11.5) · 회원가입 · 로그인 · 슈퍼어드민 · 권한 관리 | `auth.*` (13 파일) |
| 13 | Point | 포인트 적립/사용/조회/거래 이력 + 관리자 조회 API | `point.*` (10 파일) |
| 14 | Common | `ApiResponse<T>` + `ApiResponses` 유틸 (ok · created · accepted · noContent · error) | `common.response.*` |
| 15 | Common | `ErrorCode` enum **33개** 등록 + `GlobalExceptionHandler` | `common.exception.*` |
| 16 | Infra | Docker 멀티스테이지 (JDK 빌드 + JRE-jammy 런타임 · ARM64) | `Dockerfile` |
| 17 | Infra | GitHub Actions CI/CD (build → DockerHub push → EC2 SSH 배포) | `.github/workflows/deploy.yml` |
| 18 | Infra | prod 프로파일 실 RDS(MySQL) 연동 · `ddl-auto=update` 자동 스키마 | `application-prod.yml` |
| 19 | Infra | 더미 상품 32건 프로파일 조건부 초기화 (`DummyDataInit`) | 최근 커밋 `532d653` |

### Tier 2 · Want (부분 달성)

| # | 항목 | 상태 |
|---|---|---|
| W1 | 결제 확정 e2e (`portonePaymentId` 채번 → SDK 결제 → 확정 → 완료) | ✅ 실 동작 |
| W2 | 웹훅 단독 수신으로 결제 확정 (idempotency) | ✅ 통합 테스트 |
| W3 | Refund 부분·전액 e2e | ✅ 통합 테스트 |

### Tier 3 · Fix 후속

| # | 이슈 | 상태 |
|---|---|---|
| F1 | Payment self-injection 함정 (fix/payment.md Issue 2) | ⏸️ M2로 이월 |
| F2 | Cart `itemId` vs `productId` 혼동 (fix/order.md Issue 2) | ✅ v3에서 명세 명확화 |
| F3 | JPA `ddl-auto=update` 유지 결정 | ✅ 임시 확정 (Flyway는 M3+) |

---

## 종료 신호 (Completion Signals) — 8/8 통과 ✅

- [x] **머지 신호**: Payment Product 9 Story · Refund 5 Story + Order-Payment 통합 완료
- [x] **테스트 신호**: **31개 테스트** 파일 통과 (단위 12+ · 슬라이스 6+ · 통합 10+ · Repository 3+)
- [x] **배포 신호**: EC2 컨테이너 RUNNING · ARM64 이미지 pull 성공
- [x] **DB 신호**: RDS(MySQL) 첫 부팅 시 엔티티 기반 스키마 자동 생성(`ddl-auto=update`) 성공 (커밋 `fcc67db`)
- [x] **결제 신호**: `POST /api/v1/payments/confirm` → PortOne V2 재조회 → COMPLETED 전이 성공
- [x] **웹훅 신호**: `POST /api/v1/payments/webhooks/portone` HMAC 검증 통과 + `WebhookEvent` UNIQUE 멱등
- [x] **인증 신호**: `POST /api/v1/auth/login` → JWT accessToken 발급 + `Authorization: Bearer` 헤더 정상 처리
- [x] **더미 데이터 신호**: prod 프로파일에서 상품 32건 초기화 확인 (커밋 `532d653`)

### 릴리즈 마일스톤 후보 신호 (0.1.0v로 태그 승격 가능?)
- [x] 실 사용자 노출 가능한 완결된 결제 흐름 존재
- [x] Git 태그 부착 가치 있음 (롤백 지점)
- [ ] 관측성(Log Product) 미완 → **아직 M1 · 0.0.1v 유지, 관측성 안착 후 0.1.0v 승격 검토**

---

## 의존 Chain 결과

```
[Payment]  Epic 1 ─► Epic 2 (확정) ─► Epic 3 (웹훅 멱등)
                                          │
                                          └─► [Refund]  Epic 1 (금액) ─► Epic 2 (트랜잭션)

[Order-Payment 통합]  PR #30 (MockOrderService → 실 OrderService)

[Cart 통합]  cart-list-api (v3) + 인증 통합 완료

[Infra]   Dockerfile → GHA (build+push+deploy) → EC2 (ARM64) → RDS(MySQL) prod
```

---

## 실제 작업 기록 (Git 커밋 하이라이트)

| Commit | 내용 | 카테고리 |
|---|---|---|
| `532d653` | chore(prod): DummyDataInit Profile을 prod로 + 더미 상품 32건 확장 | 데이터 |
| `fcc67db` | fix(prod): JPA `ddl-auto=update`로 변경 (RDS 첫 부팅) | Infra |
| `c4fc07b` | fix(ci): docker run 단일 행 + secret 값 따옴표 처리 | CI |
| PR #67 | fix/prod-jpa-ddl-auto-update | Infra |
| PR #65 | fix/deploy-docker-run-single-line | CI |
| PR #30 | MockOrderService 제거 + `PaymentQueryService` 도입 | 통합 |

---

## 리스크 회고 (관찰 결과)

| 영역 | 예상 리스크 | 실제 결과 |
|---|---|---|
| Refund 상태 기계 복잡도 | Story 분할 필요 가능 | ✅ 원안대로 완료 (Aggregate + Item 모델링으로 흡수) |
| Gemini API 키·quota | 확보 못 하면 진입 불가 | ⏸️ 초기 계획에서 제외 → AI Suggestion Product 미착수 |
| Log 병존 이중 출력 | dev 프로파일 이슈 가능 | ⏸️ Log Product 자체 미착수 |
| RDS 스키마 자동 갱신 | rename 등 실패 가능 | ⚠️ 임시로 `ddl-auto=update` 유지 · Flyway는 M3+ |
| Fix F1 self-injection | 결제 확정 경로 규정 흔들기 | ⏸️ M2로 이월 |

---

## 다음 마일스톤 (M2 / 0.0.2v) 후보

- **Log Product 착수** (Epic 1: JSON 구조화 로그 + logstash-logback-encoder)
- **Log Product Epic 2** (MDC 요청 컨텍스트 · requestId · userId)
- **AI Suggestion Epic 7** (Port/Adapter 뼈대 + Static Adapter)
- **Fix F1**: Payment self-injection → `PaymentCompensationService` 분리
- **Order 잔여**: `PaymentQueryService` 조회 연결 (`oMockpaymentId=0L` 제거)
- **Mock 정리**: Payment BC의 잔여 3개 mock (`MockCartService`, `MockPointService`, `MockInventoryService`)
- **관측 인프라 검토**: Actuator + `/health` · 향후 Prometheus 여부 결정

**권장 M2**: Log Product 완결(Epic 1~4) + Fix F1 소화 + Mock 정리 (2주 스코프).

---

## 참조

- **결과 산출물**: [`infra.md`](./infra.md) · [`outcome.md`](./outcome.md) · [`cost.md`](./cost.md) · [`review.md`](./review.md)
- **Product SDD (완료 반영 예정)**: `../../../products/product-payment.md` · `../../../products/product-refund.md`
- **완료된 통합 리포트**: `../../pes/workspectrum/sdd/done/sdd-payment-integration-report.md`
- **Fix 이슈**: `../../fix/brainstorming/version/0.0.1v/{payment,refund,order,infra}.md`
- **PES Brainstorming**: `../../pes/brainstorming/0.0.1v/snapshot.md`
