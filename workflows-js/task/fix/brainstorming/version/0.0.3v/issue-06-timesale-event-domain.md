# [0.0.3v · Issue 06] 타임세일 이벤트 도메인 — Admin 등록 · 스케줄러 오픈 · 재고

> **역할**: 캠프 요구사항의 동시성 제어 필수 시나리오 (타임세일 한정 수량). 관리자가 발동하는 이벤트의 무대.
> **Week**: 2 · **Day**: 8
> **상태**: pending
> **Tier**: `pes` (도메인+스케줄러+락 결합)
> **캠프 요구사항 매핑**: 필수 — 동시성 제어 (타임세일 한정 수량 특가 · 정해진 시간에 한정 수량 선착순 판매)

---

## 배경

캠프 요구사항 예시 1: "타임세일 한정 수량 특가 — 정해진 시간에 한정 수량 상품을 선착순 판매".
백오피스 프레이밍에서 관리자가 등록 · 오픈 시각 스케줄러가 활성화 · 재고 소진 시 자동 마감.
이슈 07(락 3전략)의 무대 · 락 없이는 재고 정합성 깨짐.

## 핵심 스코프

- 신규 컨텍스트 `nbc.c1oud_mall.event.*` (4레이어)
- `TimeSaleEvent` 도메인 (id · productId · discountRate 또는 salePrice · totalStock · remainingStock · startedAt · endedAt · status: SCHEDULED/OPEN/SOLD_OUT/CLOSED)
- Admin CRUD (`POST /api/v1/admin/timesale-events` 등)
- `@Scheduled(fixedDelay=1분)` `TimeSaleEventScheduler` — SCHEDULED → OPEN 전이 · 종료 시각 감지 → CLOSED
- 재고 감소: 락은 이슈 07에서 3전략 비교. 여기선 도메인·API 골격만.
- 구매 API: `POST /api/v1/timesale-events/{id}/purchase` (인증 사용자 · 재고 -1 · Order 생성 결합)

## 상태 전이 규칙

```
SCHEDULED → OPEN (startedAt 도래)
OPEN → SOLD_OUT (remainingStock == 0)
OPEN → CLOSED (endedAt 도래)
SOLD_OUT → CLOSED (endedAt 도래)
```

역방향 전이 금지 · 관리자 CANCEL은 별도 (초기 미포함)

## API 표면

| 메서드 | 경로 | 인증 | 용도 |
|---|---|---|---|
| POST | `/api/v1/admin/timesale-events` | ADMIN | 이벤트 등록 |
| GET | `/api/v1/admin/timesale-events?status=` | ADMIN | 목록 조회 (필터) |
| PATCH | `/api/v1/admin/timesale-events/{id}` | ADMIN | 수정 (SCHEDULED 상태만) |
| DELETE | `/api/v1/admin/timesale-events/{id}` | ADMIN | 취소 (SCHEDULED 상태만) |
| GET | `/api/v1/timesale-events/active` | 공개 | 진행 중 이벤트 목록 |
| POST | `/api/v1/timesale-events/{id}/purchase` | JWT | 구매 (락은 이슈 07) |

## ErrorCode (신규)

- `EVT001` TIMESALE_EVENT_NOT_FOUND (404)
- `EVT002` TIMESALE_EVENT_NOT_OPEN (409 · SCHEDULED/CLOSED/SOLD_OUT에 구매 시도)
- `EVT003` TIMESALE_EVENT_SOLD_OUT (409 · 재고 0)
- `EVT004` TIMESALE_EVENT_INVALID_STATUS_TRANSITION (400)
- `EVT005` TIMESALE_EVENT_INVALID_PERIOD (400 · startedAt >= endedAt)

## 산출물

- BE-06-1: `event` 컨텍스트 골격 · `TimeSaleEvent` 도메인 · Repository
- BE-06-2: `TimeSaleEvent.open()`·`markSoldOut()`·`close()` 상태 전이 도메인 메서드
- BE-06-3: `TimeSaleEventScheduler` (@Scheduled · 1분 주기)
- BE-06-4: `AdminTimeSaleEventController` (Admin CRUD 4개)
- BE-06-5: `TimeSaleEventController` (공개 조회 · 구매) — 구매는 락 미포함 (이슈 07이 대체)
- BE-06-6: `ErrorCode.EVT001~005`
- BE-06-7: 통합 테스트 (상태 전이 · 스케줄러 트리거 · 소유권)

## 관련 이슈 / 문서

- 선행: [01 Admin 컨텍스트](./issue-01-admin-context-and-role.md)
- 다음: [07 락 3전략 비교](./issue-07-concurrency-lock-strategy-comparison.md) — 구매 API에 락 적용
- 다음: [08 선착순 쿠폰](./issue-08-first-come-coupon-issuance.md) — 유사 락 패턴 재사용
- 관련: [13 대시보드](./issue-13-admin-dashboard-observability.md) — 이벤트 판매 지표
- 규범: `.claude/rules/consitency.md` (락 순서 갱신)

## 상세 (착수 시 채움)

_pending_
