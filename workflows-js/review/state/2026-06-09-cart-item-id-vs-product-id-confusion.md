# [트러블슈팅] CT003 `cartItemId` vs `productId` 혼동 — FE는 productId 보내고 BE는 cart_item.id로 조회

---
date: 2026-06-09
domain: [cart, fe-integration, payment]
tags: [identifier-confusion, error-code-overload, fe-be-contract, fast-fail]
related-story: workflows/products/product.md (Epic 1·2 통합 흐름)
related-doc: workflows/fe-integration-guide-v2-addendum.md §1, fe-integration-guide-v3.md §6
related-topology: workflows/topologys/Identifier-System-Topology.md
---

## 1. 문제 (What)

FE가 장바구니에 상품을 담은 직후 주문 생성을 호출하는데, BE가 빈 장바구니로 응답.

```
POST /api/v1/carts/items { "productId": 4, "quantity": 2 }
→ 201 Created (cart_item PK=1로 INSERT)

POST /api/v1/orders { "cartItemIds": [4], "pointUsedAmount": 0 }
→ 400 { "success": false, "code": "CT003", "message": "장바구니가 비어있습니다." }
```

분명히 담았는데 "비어있음"이 떨어짐. 같은 사용자, 같은 세션.

## 2. 문제 해결 방법 (How)

1. **BE 코드부터 확인** — `OrderFacade.getValidateCartItems` 추적. `cartService.findCartEntitiesByIds(userId, cartItemIds)` 호출.
2. **JPA 쿼리 확인** — `CartItemJpaRepository`의 fetch join 쿼리가 `WHERE ci.member_id = :userId AND ci.id IN :ids` 형태. `ci.id` = cart_item 테이블의 PK.
3. **FE 송신값 확인** — `cartItemIds: [4]`는 사실 *방금 담은 상품의 productId*. cart_item.id가 아님.
4. **의문 — FE가 cart_item.id를 어떻게 알지?** 확인 결과 받을 길이 없었음:
   - `POST /carts/items` 응답: `data: null`, `Location: /api/v1/carts/`
   - `GET /carts` 또는 `GET /carts/items` **부재**
5. **즉시 우회** — `OrderFacade.getValidateCartItems:181-183`이 빈 배열을 "전체 장바구니"로 처리하는 것 확인 → FE가 `cartItemIds: []`로 강제 송신, e2e 즉시 통과.
6. **근본 해결** — v3 시점에 `GET /api/v1/carts` 신설 + 응답 DTO에 `cartItemId` 포함. FE가 정상적으로 부분 선택 주문 가능.

## 3. 방식 (Why this way)

### 즉시 우회를 `cartItemIds: []`로 한 이유
- 결제 e2e가 막혀 *전체 흐름 검증*이 멈춰있었음. 빠른 unblock이 우선.
- BE 시그니처를 바꾸는 옵션(예: cartItemIds에 productId도 허용)은 *오히려 의미 모호*하게 만듦. ID 두 종류를 같은 필드로 받는 건 새로운 함정의 시작.
- 빈 배열 = 전체 처리는 *이미 코드에 있던 동작*이라 추가 위험 0.

### 근본 해결을 `GET /carts` 신설로 한 이유
- 대안 (A): `POST /carts/items` 응답 body에 `cartItemId` 포함 — 단건 응답에 ID 노출만 추가하면 됨
- 대안 (B): `GET /carts` 신설 — 전체 장바구니 화면도 동시에 해결
- (A)만 하면 *화면 갱신*은 여전히 localStorage 추적이 필요. (B)는 한 번에 둘 다 해결 → ROI ↑.

### CT003 코드 의미 분리는 하지 않기로
- 응답상 "장바구니 비어있음"과 "cartItemId 매칭 0건"이 같은 `CT003`/메시지. 사용자 입장에선 같은 결과(주문 실패).
- 두 케이스를 다른 코드로 분리하면 FE 분기 늘어남 + BE 의미는 같음.
- 대신 BE 로그에 `BusinessException.withDetail(..., "userId=X, requested=Y")`로 디버깅 컨텍스트 보강.

## 4. 결과 (Outcome)

- **v2 시점**: FE 임시 우회(`cartItemIds: []` 강제) + localStorage로 장바구니 화면 표시. 부분 선택 주문 불가.
- **v3 시점** (현재): 
  - `GET /api/v1/carts` 신설 (`CartController:48-55`) → 정상 화면 그리기 + cartItemId 획득
  - `GET /api/v1/carts/selected?ids=` 신설 → 선택 합계 미리보기
  - `DELETE /api/v1/carts/selected?ids=` 신설 → 선택 삭제
  - FE v3 가이드 §6에 "v2 잔존 블로커 모두 해소" 명시
  - localStorage 우회 코드 폐기 가능
- **잔존 함정**: `POST /carts/items` 응답엔 여전히 `cartItemId` 없음. 담은 *직후* 부분 주문하려면 `GET /carts` 1회 추가 호출 필요. v3 §13 typo/잔존 목록에 기록.

## 5. 좋아진 점 (What got better)

- **FE-BE 계약에서 "같은 단어의 다른 의미" 함정을 인지함.** `cartItemIds`라는 이름이 *cart_item PK*인지 *cart에 담긴 product ID*인지가 자명하지 않으면 FE가 잘못 추측. 이후 모든 ID 필드는 *주 키 소속 테이블*을 변수명에 박는 컨벤션으로 정착 (`orderItemId`, `cartItemId`, `productId`).
- **에러 코드 오버로딩의 디버깅 비용을 체감.** "같은 코드가 두 원인을 가리키면 응답만 보고는 구분 불가" — `BusinessException.withDetail`을 적극 활용해 BE 로그에 컨텍스트를 남기는 패턴 채택 (`RefundProcessService:117-120`에서 환불 흐름에도 동일 패턴 적용).
- **API 응답 설계 원칙 1개 추가**: *생성 직후 ID가 필요할 가능성이 0이 아니면 응답 body에 ID 포함*. 헤더 `Location`만으로는 FE가 파싱 비용을 짐.
- v2-addendum 문서가 *FE 클로드가 같은 함정을 안 밟도록* 명시적 경고 역할 — 인간 디버깅 시간을 문서로 이전.
