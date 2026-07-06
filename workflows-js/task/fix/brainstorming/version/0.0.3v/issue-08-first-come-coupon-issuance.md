# [0.0.3v · Issue 08] 선착순 쿠폰 발급 이벤트 — Coupon 도메인 · 락 재사용

> **역할**: 캠프 요구사항의 동시성 시나리오 2번 (선착순 쿠폰). 이슈 07의 락 전략 재사용.
> **Week**: 2 · **Day**: 11
> **상태**: pending
> **Tier**: `feature-story`
> **캠프 요구사항 매핑**: 필수 — 동시성 제어 (선착순 할인쿠폰 발급 이벤트)

---

## 배경

캠프 요구사항 예시 2: "선착순 할인쿠폰 발급 이벤트 — 프로모션 쿠폰을 선착순으로 발급".
관리자가 쿠폰 정책 등록 (총 수량·유효기간·할인율) → 사용자가 발급 요청 → 선착순 소진.
이슈 07의 `LockService`를 그대로 재사용 (같은 락 패턴).

## 핵심 스코프

- 신규 컨텍스트 `nbc.c1oud_mall.coupon.*` (4레이어)
- `Coupon` (정책 · 관리자 등록): id · code · name · discountType (RATE/AMOUNT) · discountValue · minOrderAmount · totalQuantity · issuedQuantity · issueStartedAt · issueEndedAt · usableUntil · status
- `UserCoupon` (발급 이력): id · userId · couponId · issuedAt · usedAt · orderId
- Admin CRUD 쿠폰 정책 (`POST /api/v1/admin/coupons`)
- 사용자 발급 요청 (`POST /api/v1/coupons/{couponId}/issue`) — 락 사용
- 중복 발급 방지 (`UNIQUE(user_id, coupon_id)`)
- 발급 카운트 정합성 (`Coupon.issuedQuantity < totalQuantity` 검증)

## API 표면

| 메서드 | 경로 | 인증 | 용도 |
|---|---|---|---|
| POST | `/api/v1/admin/coupons` | ADMIN | 쿠폰 정책 등록 |
| GET | `/api/v1/admin/coupons?status=` | ADMIN | 정책 목록 |
| PATCH | `/api/v1/admin/coupons/{id}/status` | ADMIN | 활성/비활성 |
| GET | `/api/v1/coupons/available` | JWT | 발급 가능 쿠폰 목록 |
| POST | `/api/v1/coupons/{couponId}/issue` | JWT | 쿠폰 발급 (선착순 락) |
| GET | `/api/v1/users/me/coupons` | JWT | 내 쿠폰 목록 |
| POST | `/api/v1/users/me/coupons/{userCouponId}/use` | JWT | 쿠폰 사용 (주문 시) |

## ErrorCode (신규)

- `CPN001` COUPON_NOT_FOUND (404)
- `CPN002` COUPON_NOT_AVAILABLE (409 · 발급 기간 아님)
- `CPN003` COUPON_SOLD_OUT (409 · 수량 소진)
- `CPN004` COUPON_ALREADY_ISSUED (409 · 중복 발급 · S+ 이중 방어)
- `CPN005` USER_COUPON_NOT_FOUND (404)
- `CPN006` USER_COUPON_ALREADY_USED (409)
- `CPN007` USER_COUPON_EXPIRED (409)

## 산출물

- BE-08-1: `coupon` 컨텍스트 골격 · `Coupon` · `UserCoupon` 엔티티
- BE-08-2: `UNIQUE(user_id, coupon_id)` DB 제약
- BE-08-3: `CouponIssueService.issue(userId, couponId)` — `LockService` 재사용 (이슈 07)
- BE-08-4: 사전조회 + DB UNIQUE 이중 방어 (S+ 등급 · `.claude/rules/idempotency.md`)
- BE-08-5: `AdminCouponController` (정책 CRUD)
- BE-08-6: `CouponController` (발급·조회·사용)
- BE-08-7: `ErrorCode.CPN001~007`
- BE-08-8: 동시성 통합 테스트 (100명 동시 발급 · 수량 정확성)

## 관련 이슈 / 문서

- 선행: [07 락 3전략](./issue-07-concurrency-lock-strategy-comparison.md) — `LockService` 재사용
- 관련: [13 대시보드](./issue-13-admin-dashboard-observability.md) — 쿠폰 사용률 지표
- 규범: `.claude/rules/idempotency.md` §2 카탈로그 갱신 (쿠폰 발급 S+ 등급)

## 상세 (착수 시 채움)

_pending_
