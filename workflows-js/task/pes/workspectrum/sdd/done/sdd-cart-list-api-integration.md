# [Product · Done] Cart 조회 API 도입 — 결제 e2e 부분 선택 unblock

## Product Vision
> Cart 팀에 `GET /api/v1/carts` 신설을 요청하고, FE가 `cartItemId`를 알 수 있는 정상 흐름을 확보하여, 사용자가 장바구니에서 일부만 선택해 주문하는 UX(부분 선택 결제)를 정상 동작하게 만든다.

## 배경 및 문제
- 현재 상황 (As-Is)
  - `POST /api/v1/carts/items` → 200, Location `/api/v1/carts/` (cartItemId 미반환)
  - FE가 `cartItemId`를 알 방법 없음 (응답 body null · Location에도 없음 · GET 엔드포인트 부재)
  - `POST /api/v1/orders {cartItemIds: [4]}` → 400 `CT003 CART_EMPTY` (BE는 `cart_item.id IN (4)` 조회 · 매칭 0건 → 빈 장바구니 판단)
- 발생하는 문제
  - `OrderCheckoutRequest.cartItemIds`가 `cart_item.id`(PK)를 받는데 FE는 productId를 추측 송신 → 매칭 실패
  - 임시 우회: `cartItemIds: []` 빈 배열 → 전체 결제만 가능, **부분 선택 UX 불가**
- 왜 지금 해결해야 했는가
  - 결제 e2e 부분 선택 UX가 막힘 → 장바구니 기본 동작 자체 blocker
  - BE 작업량 매우 작음 (30분~1시간, 기존 서비스·쿼리 재사용 가능)

## 목표 (To-Be)
- `GET /api/v1/carts/items` (또는 `GET /api/v1/carts`) 신설 — 응답에 `cartItemId` 포함
- 기타 관련 엔드포인트 부수 정비: 선택 조회 · 다중 삭제 · 개별 삭제 · 전체 비우기
- Cart `memberId=1L` 하드코딩 해소 → JWT `@AuthenticationPrincipal` 적용

## 설계 결정 (Design Decisions)

- **`cartItemId` 필수 응답 필드**
  - FE가 부분 선택 주문 시 `cartItemIds` 배열로 그대로 송신
- **필드명 `subTotal` (camelCase 중 T 대문자)** — `subtotal` 아님
- **기존 서비스 재사용**
  - `CartService.findCartEntities(userId)` + `CartItemJpaRepository.findByUserId` JOIN FETCH — 추가 쿼리 X
- **인증 통합** — `@AuthenticationPrincipal Long userId` 적용 (모든 cart 엔드포인트)
- **빈 장바구니는 빈 배열 반환 (200)**
  - `CART_EMPTY(CT003)`는 주문 생성 외에는 에러 아님

## 대안 검토 (Alternatives Considered)

### FE에 cartItemId 전달 방식
**Option A — POST /carts/items 응답 body에 cartItemId 포함**
- 거부 이유: 담은 직후만 알 수 있음. 페이지 새로고침 시 다시 알 방법 없음. 근본 해결 아님

**Option B (선택) — GET /carts 신설**
- 비용: 신규 엔드포인트 + DTO
- 보상: 언제든 재조회 가능 · localStorage 폐기 · 정상 흐름

**Option C — cartItemIds 대신 productId 배열 받기**
- 거부 이유: BE 도메인 규칙(cart_item PK 기준) 변경 부담

### 인증 통합
**Option A (선택) — JWT `@AuthenticationPrincipal` 적용**
- 보상: 진짜 사용자별 격리 · 다른 사용자로 e2e 테스트 가능
- 비용: 다른 Controller와 일괄 정비 필요

**Option B — 계속 `memberId=1L` 유지**
- 거부 이유: e2e 테스트 불가 · 임시 코드 잔존

## 전체 아키텍처 (High-Level Architecture)

### 최종 API 시맵 (통합 완료)
```
GET  /api/v1/carts                        전체 조회         ⭐ 신규
GET  /api/v1/carts/selected?ids=1&ids=2    선택 조회         ⭐ 신규
DELETE /api/v1/carts/selected?ids=1&ids=2  선택 삭제 (다중)  ⭐ 신규
DELETE /api/v1/carts/items/{cartItemId}    개별 삭제         ✅ 인증 적용
DELETE /api/v1/carts                       전체 비우기       ✅ 인증 적용
POST   /api/v1/carts/items                 담기              ✅ 인증 적용
PATCH  /api/v1/carts/items/{cartItemId}    수량 변경         ✅ 인증 적용
```

### Response 스펙 (`CartListResponse`)
```json
{
  "success": true, "code": "OK", "message": "Success",
  "data": {
    "items": [
      {
        "cartItemId": 1,
        "productId": 4,
        "productName": "맛있는 고흥 붉바리 (생물)",
        "price": 45000,
        "quantity": 2,
        "subTotal": 90000
      }
    ],
    "totalPrice": 90000
  },
  "timestamp": "..."
}
```

## 실패 모드 / 운영 관측 (Failure Modes & Observability)
| 시나리오 | 응답 |
| --- | --- |
| 빈 장바구니 조회 | 200 · 빈 배열 (에러 아님) |
| 다중 삭제 시 일부 매칭 안 됨 | 204 · BE 로그에 mismatch warning |
| 미인증 | 401 (Spring Security) |

## 롤아웃 / 마이그레이션 (Rollout)

### 전제
- Cart 팀 단독 영역 · 다른 팀 협의 불필요
- BE 작업량 매우 작음 (~30분 · 기존 서비스 재사용)

### Product 의존성
- 선행: Cart 도메인 · CartService 구현
- 후행: FE 통합 가이드 v3 · 결제 e2e 부분 선택

### Epic·Story 의존성 그래프
```
Epic 1 (CartService 재사용 확인) ──► Epic 2 (Controller + DTO 추가)
                                          │
                                          └─► Epic 3 (다른 엔드포인트 인증 적용)
                                                  │
                                                  └─► Epic 4 (FE 통합 알림)
```

## 성공 지표 (KPI)
| 지표 | 목표 | 결과 |
| --- | --- | --- |
| 부분 선택 결제 가능 여부 | 가능 | ✅ v3 시점 정상 동작 |
| `cartItemIds: []` 빈 배열 임시 우회 사용 | 폐기 | ✅ FE에서 완전 제거 |
| `CT003 CART_EMPTY` 발생률 (정상 흐름) | 0% | ✅ 정상 흐름에서 미발생 |
| Cart `memberId=1L` 하드코딩 | 0건 | ✅ JWT 적용 완료 |
| localStorage 기반 장바구니 표시 | 폐기 | ✅ 서버 응답으로 직접 표시 |

## Scope
**In Scope**:
- `GET /api/v1/carts` 신설
- `GET /api/v1/carts/selected?ids=...` 신설
- `DELETE /api/v1/carts/selected?ids=...` 신설
- 모든 Cart 엔드포인트에 `@AuthenticationPrincipal Long userId` 적용
- `memberId=1L` 하드코딩 제거
- FE 통합 알림 발송

**Out of Scope**:
- `POST /carts/items` 응답 body에 `cartItemId` 포함 — 부가 요청, GET으로 충분
- `BusinessException.withDetail(...)` 채택 — 별도 개선 트랙

## 대상 사용자
- **FE 개발자**: 정상 흐름으로 부분 선택 결제 구현 가능
- **BE Cart 팀**: 소규모 작업으로 e2e blocker 해소
- **사용자**: 장바구니에서 원하는 상품만 선택 결제 가능

## 연결된 Epic 목록 (완료)
- [x] Epic 1: `CartService.findCartEntities` 재사용 확인
- [x] Epic 2: `CartController` + `CartListResponse` DTO 신설
- [x] Epic 3: 모든 Cart 엔드포인트에 인증 적용
- [x] Epic 4: 다중/개별 삭제 · 선택 조회 엔드포인트 추가
- [x] Epic 5: FE 통합 알림 (`fe-cart-api-available-notice.md`)

## 관련 문서
- 원본 요청: `workflows/cart-team-request-list-api.md` (2026-06-05)
- 원본 알림: `workflows/fe-cart-api-available-notice.md` (2026-06-05)
- 후속 FE 통합 가이드: [`sdd-fe-integration-guide.md`](./sdd-fe-integration-guide.md)
- `.claude/rules/dto.md` (Response DTO 규범 준수)
- `.claude/rules/idempotency.md` §2 (Cart는 본질적 멱등 GET · 상태 기반 명시)

## 열린 질문 (Open Questions)
- Cart 내 재고 부족·품절 UI 표시를 위해 `stockQuantity`·`productStatus` 필드 응답 포함 필요? (권장 필드로 협의)
- Cart 삭제 위치의 최종 결정 (OrderFacade.createOrder에서 삭제 · [`sdd-payment-integration-report.md`](./sdd-payment-integration-report.md) 참조)

## 제품 수준 완료 기준 (Product-level DoD)
- [x] `GET /api/v1/carts` 정상 응답 (200 · `cartItemId` 포함)
- [x] 부분 선택 결제 시나리오 e2e 검증
- [x] FE 임시 우회 코드 폐기 확인
- [x] Cart 하드코딩 memberId 제거 · 사용자별 격리 확인
