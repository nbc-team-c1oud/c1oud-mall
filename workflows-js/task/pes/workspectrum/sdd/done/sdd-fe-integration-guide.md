# [Product · Done] FE 통합 가이드 v3 — 자가완결 API 참조 문서

## Product Vision
> 별도 폴더에서 작업하는 프론트엔드(FE) Claude Code / 개발자가 백엔드 레포에 접근하지 않고도 c1oud-mall 백엔드와 통신할 수 있도록, 인증·상품·장바구니·주문·결제·포인트·환불의 모든 접점을 단일 참조 문서 하나로 자가완결시킨다.

## 배경 및 문제
- 현재 상황 (As-Is)
  - FE 통합 가이드가 v1 · v2 · v2-addendum으로 파편화 (임시 우회안 · 잔존 blocker 산재)
  - 결제 e2e 부분 선택이 막혀 있고, localStorage 기반 장바구니 표시 등 우회 코드 잔존
  - Cart 팀 통합 · Order-Payment 통합이 완료됐음에도 FE는 여전히 이전 우회 코드 사용
- 발생하는 문제
  - FE 개발자가 어느 문서를 진실 소스로 삼을지 혼동
  - 임시 우회 코드가 정상 흐름 도입 후에도 정리되지 않고 남음
- 왜 지금 해결해야 했는가
  - Cart 팀 통합 완료(v3 시점) · Order-Payment 통합 완료 (PR #30)
  - "이 문서 하나만 보고 작업 가능" 상태 확정 필요

## 목표 (To-Be)
- 모든 v1 · v2 · v2-addendum 폐기 가능
- FE가 접근하는 모든 엔드포인트(auth · products · carts · orders · payments · points) 스펙 · 예제 코드 · 에러 처리 통합
- `ApiResponse<T>` envelope · JWT 인증 · CORS · PortOne SDK 연동까지 커버
- FE 임시 우회 코드 폐기 안내

## 설계 결정 (Design Decisions)

- **단일 참조 문서** (v1·v2·v2-addendum 대체)
  - 자가완결 · FE 개발자가 BE 레포 접근 불필요
- **`ApiResponse<T>` envelope 통일**
  - `success · code · message · data · timestamp` 5필드 · `@JsonInclude(NON_NULL)` (data null 시 생략)
- **JWT 만료 1시간 · Refresh 없음**
  - 만료 시 재로그인 (초기 규모 · Refresh 흐름은 v2에서)
- **CORS: 모든 localhost 포트 허용** (`http://localhost:*`, `https://localhost:*`)
  - FE 로컬 dev 서버 (Vite 5173 · Next 3000 등) 직접 호출 가능

## 대안 검토 (Alternatives Considered)

### 버전 관리
**Option A (선택) — v3 단일 통합**
- 비용: 대규모 문서 재작성
- 보상: FE 개발자 혼동 제거

**Option B — v2 유지 + delta 문서**
- 거부 이유: 파편화 지속 · FE는 여러 문서 크로스 참조 필요

### JWT Refresh 도입
**Option A (선택) — Refresh 없음**
- 비용: 만료 시 UX 저하
- 보상: 초기 규모에 단순

**Option B — Refresh Token 도입**
- 거부 이유: 초기 오버킬 · 별도 트랙

## 전체 아키텍처 (High-Level Architecture)

### FE ↔ BE 통합 경로 (완성)
```
FE (localhost:5173)
  │
  ├── POST /api/v1/auth/{signup,login}       (인증 불필요)
  ├── GET  /api/v1/auth/me                   (JWT)
  │
  ├── GET  /api/v1/products                  (인증 불필요)
  ├── GET  /api/v1/products/{id}             (인증 불필요)
  │
  ├── POST /api/v1/carts/items               (JWT)  ⭐ v3 신규 인증
  ├── GET  /api/v1/carts                     (JWT)  ⭐ v3 신규
  ├── GET  /api/v1/carts/selected?ids=...    (JWT)  ⭐ v3 신규
  ├── DELETE /api/v1/carts/selected?ids=...  (JWT)  ⭐ v3 신규
  ├── DELETE /api/v1/carts/items/{id}        (JWT)
  ├── DELETE /api/v1/carts                   (JWT)
  ├── PATCH /api/v1/carts/items/{id}         (JWT)
  │
  ├── GET  /api/v1/orders/preview            (JWT)
  ├── POST /api/v1/orders                    (JWT)  ⭐ portonePaymentId 실 채번
  ├── GET  /api/v1/orders                    (JWT)  ⭐ paymentId 실 연결
  ├── GET  /api/v1/orders/{id}               (JWT)
  ├── POST /api/v1/orders/{id}/cancel        (JWT)  ⭐ v3 신규
  │
  ├── POST /api/v1/payments/confirm          (JWT)
  ├── POST /api/v1/payments/webhooks/portone (HMAC-SHA256 서명 검증)
  │
  ├── GET  /api/v1/points/me                 (JWT)  ⭐ v3 신규
  ├── GET  /api/v1/points/me/history         (JWT)  ⭐ v3 신규 (typo 있음)
  │
  └── (환불) 사용자 API 없음 — 내부 운영용

PortOne SDK (@portone/browser-sdk/v2)
  ↕ (client-side)
FE ↔ PortOne (storeId · channelKey는 FE 공개 · V2 API Secret은 BE 전용)
```

## 실패 모드 / 운영 관측 (Failure Modes & Observability)

### 공통 실패 응답 (`ApiResponse<Void>` error 형)
```json
{ "success": false, "code": "U002", "message": "...", "timestamp": "..." }
```

### Validation 실패
- 항상 `code=C001` · `message`에 필드별 상세 합쳐짐

### 대표 에러 코드
| 코드 | 의미 | HTTP |
| --- | --- | --- |
| `C001` INVALID_INPUT | Bean Validation 실패 | 400 |
| `U001` EMAIL_DUPLICATE | 이메일 중복 | 409 |
| `U002` INVALID_CREDENTIALS | 로그인 실패 | 401 |
| `U003`~`U006` | 인증 컨텍스트 관련 | 401·403 |
| `OD002` INVALID_ORDER_STATUS | 주문 상태 전이 오류 | 400 |
| `PM001` PAYMENT_AMOUNT_MISMATCH | 금액 위변조 | 400 |

## 롤아웃 / 마이그레이션 (Rollout)

### 전제
- Cart · Order · Payment 통합 완료 (PR #30 등)
- 모든 우회 코드 폐기 가능

### FE 정리 체크리스트 (v2 → v3)
- [x] `cartItemIds: []` 빈 배열 우회 폐기
- [x] localStorage 기반 장바구니 추적 폐기 (서버 응답 사용)
- [x] `memberId=1L` 잔존 가정 제거 (실 사용자별 격리 됨)
- [x] `OrderResponse.paymentId=0` 이슈 해소 (실 PK 연결)

## 성공 지표 (KPI)
| 지표 | 목표 | 결과 |
| --- | --- | --- |
| v1·v2·v2-addendum 폐기 가능 여부 | 가능 | ✅ v3 단독으로 자가완결 |
| FE가 BE 레포 접근 필요성 | 0건 | ✅ 이 문서만 참조 |
| 결제 e2e 부분 선택 가능 여부 | 가능 | ✅ |
| CORS FE localhost 직접 호출 | 가능 | ✅ 모든 localhost 포트 |
| 문서화 커버리지 (엔드포인트) | 100% | ✅ auth · products · carts · orders · payments · points |

## Scope
**In Scope**:
- 모든 사용자 API 스펙 · Request/Response · 예제 코드
- `ApiResponse<T>` envelope 규범
- JWT 인증 · CORS · 만료 정책
- PortOne SDK 연동 (storeId · channelKey · V2 Secret 분리)
- FE 헬퍼 함수 예제
- 에러 코드 매핑 · Validation 실패 형식

**Out of Scope**:
- 환불 사용자 API — 미노출 (내부 운영용)
- 관리자 API — 별도
- 웹훅 페이로드 스펙 (BE 내부 · FE 무관)

## 대상 사용자
- **FE 개발자 (또는 FE Claude Code)**: BE 레포 접근 없이 통합 가능
- **BE 개발자**: FE 팀과의 API 계약 참조
- **QA**: e2e 테스트 시 요청·응답 형식 확인

## 연결된 Epic 목록 (완료)
- [x] Epic 1: 환경 설정 · CORS · JWT 설명
- [x] Epic 2: `ApiResponse` envelope · 헬퍼 함수 예제
- [x] Epic 3: 인증 API 스펙
- [x] Epic 4: 상품 · 장바구니 API 스펙
- [x] Epic 5: 주문 · 결제 API 스펙 (PortOne SDK 연동 포함)
- [x] Epic 6: 포인트 API 스펙
- [x] Epic 7: v2 → v3 정리 안내

## 관련 문서
- 원본 문서: `workflows/fe-integration-guide-v3.md` (2026-06-08 · branch `refactor/replace-mock-inventory-with-product-service`)
- 짝 문서: [`sdd-cart-list-api-integration.md`](./sdd-cart-list-api-integration.md), [`sdd-order-payment-integration.md`](./sdd-order-payment-integration.md)
- 후속 리포트: [`sdd-payment-integration-report.md`](./sdd-payment-integration-report.md)
- `.claude/rules/dto.md` (Response 규범) · `.claude/rules/exception.md` (Error 규범)

## 열린 질문 (Open Questions)
- Point history 경로 typo (§9 명시) → 해소 예정
- JWT Refresh Token 도입 트리거 (사용자 지표 · UX 피드백)
- 환불 사용자 API 노출 시점 (관리자 워크플로우 도입 시)

## 제품 수준 완료 기준 (Product-level DoD)
- [x] 모든 사용자 API 스펙 문서화
- [x] `ApiResponse<T>` envelope 예제 포함
- [x] JWT · CORS · PortOne 통합 방법 포함
- [x] v2 → v3 정리 체크리스트 제공
- [x] FE가 이 문서만으로 e2e 작업 가능 확인
