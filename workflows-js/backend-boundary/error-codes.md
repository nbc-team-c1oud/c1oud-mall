# [Backend Boundary · ErrorCode] BE ↔ FE UX 매핑 SSOT

> **역할**: BE `common.exception.ErrorCode` enum의 각 항목이 FE에서 **어떤 UX 액션**으로 대응되는지 매핑하는 단일 진실 소스.
>
> **원본 (BE)**: `src/main/java/nbc/c1oud_mall/common/exception/ErrorCode.java` (33개 코드 · 0.0.1v 기준)
>
> **미러 (FE)**: `src/lib/api/errors.ts` (`ERROR_CODE_ACTIONS` 상수 · Zod 스키마 파생)

---

## UX 액션 카탈로그

| 액션 | 의미 | 코드 예시 |
|---|---|---|
| **inline** | 폼/입력 필드 옆에 즉시 오류 메시지 표시 · 페이지 이동 없음 | Validation 실패 |
| **toast** | 화면 상단·하단 알림 배너 (자동 사라짐) · 페이지 유지 | 일시적 오류 |
| **modal** | 모달 다이얼로그 (사용자 확인 필요) · 페이지 유지 | 접근 거부 |
| **routing** | 다른 페이지로 강제 이동 | 401 인증 만료 · 404 |
| **silent** | UX 응답 없음 (백엔드가 자동 처리) | 웹훅 · 멱등 응답 |
| **retry** | 재시도 버튼 노출 · 자동 재시도 옵션 | 5xx 일시적 |

---

## 매핑 표 (33개 · 도메인별)

### 공통 (Common) — 4개

| ErrorCode | HTTP | 메시지 (BE 원본) | FE UX 액션 | 추가 처리 |
|---|---|---|---|---|
| `C001` INVALID_INPUT | 400 | "잘못된 입력입니다." | **inline** | field별 상세 메시지가 `message`에 합쳐서 옴 · Zod 파싱해서 field별 배치 |
| `C002` INTERNAL_ERROR | 500 | "서버 오류가 발생했습니다." | **toast** + **retry** | Sentry 전송 · `X-Request-Id` breadcrumb |
| `C003` ACCESS_DENIED | 403 | "접근 권한이 없습니다." | **modal** | "접근 거부" 다이얼로그 + 홈 이동 CTA |
| `C004` UNAUTHORIZED | 401 | "인증이 필요합니다." | **routing** | `localStorage` accessToken 제거 → `/login` 이동 + toast "재로그인 필요" |

### 사용자 (User) — 6개

| ErrorCode | HTTP | 메시지 | FE UX 액션 | 추가 처리 |
|---|---|---|---|---|
| `U001` EMAIL_DUPLICATE | 409 | "이미 사용 중인 이메일입니다." | **inline** | 회원가입 폼 email 필드 |
| `U002` INVALID_CREDENTIALS | 401 | "이메일 또는 비밀번호가 올바르지 않습니다." | **inline** | 로그인 폼 · 어느 필드가 틀렸는지 구분 X (보안) |
| `U003` USER_NOT_FOUND | 404 | "사용자를 찾을 수 없습니다." | **modal** | 관리자 화면 등 |
| `U004` INVALID_TOKEN | 401 | "유효하지 않은 토큰입니다." | **routing** | `C004`와 동일 (로그인 페이지) |
| `U005` TOKEN_EXPIRED | 401 | "토큰이 만료되었습니다." | **routing** | `C004`와 동일 · v1은 Refresh 없음 |
| `U006` ALREADY_ADMIN | 409 | "이미 관리자입니다." | **inline** | 관리자 화면 |

### 상품 (Product) — 4개

| ErrorCode | HTTP | 메시지 | FE UX 액션 | 추가 처리 |
|---|---|---|---|---|
| `PROD001` PRODUCT_NOT_FOUND | 404 | "상품을 찾을 수 없습니다." | **modal** + **routing** | 상품 목록으로 이동 |
| `PROD002` INSUFFICIENT_STOCK | 409 | "재고가 부족합니다." | **inline** + **toast** | 담기·주문 시점에 다르게 |
| `PROD003` INVALID_PRICE | 400 | "유효하지 않은 가격입니다." | **inline** | (관리자 상품 등록 UI) |
| `PROD004` INVALID_STOCK | 400 | "유효하지 않은 재고입니다." | **inline** | (관리자 상품 등록 UI) |

### 장바구니 (Cart) — 4개

| ErrorCode | HTTP | 메시지 | FE UX 액션 | 추가 처리 |
|---|---|---|---|---|
| `CT001` CART_ITEM_NOT_FOUND | 404 | "장바구니 항목을 찾을 수 없습니다." | **toast** | 목록 새로고침 유도 |
| `CT002` CART_ACCESS_DENIED | 403 | "본인 장바구니만 수정할 수 있습니다." | **modal** | 접근 거부 (버그 신호) |
| `CT003` CART_EMPTY | 400 | "장바구니가 비어있습니다." | **inline** + **toast** | 주문 생성 시점에만 · 조회는 빈 배열 200 |
| `CT004` INVALID_QUANTITY | 400 | "수량은 1 이상이어야 합니다." | **inline** | 수량 입력 필드 |

### 주문 (Order) — 2개

| ErrorCode | HTTP | 메시지 | FE UX 액션 | 추가 처리 |
|---|---|---|---|---|
| `OD001` ORDER_NOT_FOUND | 404 | "주문을 찾을 수 없습니다." | **routing** | 주문 목록으로 |
| `OD002` INVALID_ORDER_STATUS | 400 | "유효하지 않은 주문 상태입니다." | **inline** + **toast** | 취소 시도 시 이미 CANCELLED 상태 등 |

### 포인트 (Point) — 2개

| ErrorCode | HTTP | 메시지 | FE UX 액션 | 추가 처리 |
|---|---|---|---|---|
| `PT001` POINT_AMOUNT_INVALID | 400 | "포인트 금액이 유효하지 않습니다." | **inline** | 포인트 입력 필드 |
| `PT002` POINT_INSUFFICIENT | 400 | "포인트 잔액이 부족합니다." | **inline** | 잔액 표시 · 최대 사용 가능 안내 |

### 결제 (Payment) — 8개

| ErrorCode | HTTP | 메시지 | FE UX 액션 | 추가 처리 |
|---|---|---|---|---|
| `PAY001` PAYMENT_AMOUNT_MISMATCH | 400 | "결제 금액이 일치하지 않습니다." | **inline** + **retry** | "금액 재조회 필요" · 재조회 버튼 · 결제 창 재오픈 |
| `PAY002` PAYMENT_INVALID_AMOUNT | 400 | "결제 금액이 유효하지 않습니다." | **inline** | 폼 검증 실패 (금액 필드) |
| `PAY003` PAYMENT_DUPLICATE_PAYMENT_ID | 409 | "이미 처리된 결제입니다." | **silent** | 멱등 응답 · 정상 성공 처리 |
| `PAY004` PAYMENT_NOT_FOUND | 404 | "결제를 찾을 수 없습니다." | **routing** | 주문 목록으로 |
| `PAY005` PAYMENT_AUTHORIZATION_FAILED | 403 | "본인 결제만 확인할 수 있습니다." | **modal** | 접근 거부 |
| `PAY006` PAYMENT_ORDER_MISMATCH | 400 | "주문과 결제가 일치하지 않습니다." | **modal** | 데이터 정합성 이슈 (버그 신호) · Sentry |
| `PAY007` PAYMENT_INVALID_STATUS | 409 | "결제 상태가 유효하지 않습니다." | **inline** | 상태 표시 · 재시도 안내 |
| `PAY008` PORTONE_PAYMENT_NOT_PAID | 400 | "PortOne 결제가 완료되지 않았습니다." | **inline** + **retry** | 결제 창 재오픈 |
| `PAY009` PORTONE_QUERY_FAILED | 502 | "PortOne 조회에 실패했습니다." | **toast** + **retry** | 재시도 안내 · 자동 재시도 X |
| `PAY010` PORTONE_RESPONSE_INVALID | 502 | "PortOne 응답이 유효하지 않습니다." | **toast** + **retry** | 동상 |
| `PAY011` PORTONE_CANCEL_FAILED | 202 | "PortOne 취소 요청 중입니다." | **toast** | "환불/취소 처리 중 · 운영팀 확인" · 202 응답이므로 부분 성공 |

### 환불 (Refund) — 3개

| ErrorCode | HTTP | 메시지 | FE UX 액션 | 추가 처리 |
|---|---|---|---|---|
| `RF001` REFUND_QUANTITY_EXCEEDED | 409 | "잔여 환불 가능 수량을 초과했습니다." | **inline** + **toast** | 잔여 수량 재조회 후 재요청 안내 |
| `RF002` REFUND_NOT_REFUNDABLE_STATE | 409 | "환불할 수 없는 결제 상태입니다." | **modal** | 상태 표시 · 관리자 문의 안내 |
| `RF003` REFUND_OWNERSHIP_FAILED | 403 | "본인 소유의 결제만 환불할 수 있습니다." | **modal** | 접근 거부 |

---

## 특수 처리 규칙

### 401 계열 (`C004` · `U004` · `U005`)
- 공통 로직 (interceptor에서 처리):
  1. `localStorage.removeItem('accessToken')`
  2. TanStack Query 캐시 clear (`queryClient.clear()`)
  3. `/login` 이동 (현재 경로를 `?redirect=...`로 전달)
  4. toast "재로그인 필요"
- **v1은 Refresh 없음** — Refresh Token 도입 시 이 로직 재검토 (FE-ADR-P0-001)

### 5xx 계열 (`C002` · `PAY009` · `PAY010`)
- Sentry 자동 전송 (`X-Request-Id` breadcrumb 포함)
- toast + retry 버튼
- 자동 재시도 X (사용자 명시 재시도만) — 이중 결제 방지

### 202 Accepted (`PAY011`)
- "부분 성공 · 처리 진행 중" 상태
- toast 표시 (`type: info`)
- 페이지 이동 O (성공 흐름 계속) · 단 "처리 중" 배지 표시

### 멱등 응답 (`PAY003`)
- **silent** — 사용자에게는 정상 성공으로 처리
- 개발 모드에서만 콘솔 로그

### Validation (`C001`)
- BE `message` 필드에 `"잘못된 입력입니다. — email: 이메일 형식이 올바르지 않습니다., password: 비밀번호는 8자 이상이어야 합니다."` 형식으로 옴
- FE에서 `— ` 분리 후 `: `로 field·message 파싱
- field별 inline error 배치
- Zod 클라이언트 검증이 대부분 잡아냄 · 이 코드는 서버 검증 통과 실패 시 폴백

---

## 미분류 · 예상 외 코드

- BE가 카탈로그에 없는 새 코드를 반환하면:
  - 개발 모드: 콘솔 warn + Sentry 전송
  - 프로덕션: `C002` (INTERNAL_ERROR)와 동일 처리 (toast + retry)
- FE 팀 확인 후 이 표에 추가

---

## FE 구현 스켈레톤 (`src/lib/api/errors.ts`)

```ts
// 이 파일은 backend-boundary/error-codes.md와 동기 유지 필요
export type UxAction = 'inline' | 'toast' | 'modal' | 'routing' | 'silent' | 'retry';

export interface ErrorAction {
  actions: UxAction[];
  message?: string;  // 오버라이드 (없으면 BE message 사용)
  routingTo?: string;  // routing 시 이동 경로
}

export const ERROR_CODE_ACTIONS: Record<string, ErrorAction> = {
  // Common
  C001: { actions: ['inline'] },
  C002: { actions: ['toast', 'retry'] },
  C003: { actions: ['modal'] },
  C004: { actions: ['routing'], routingTo: '/login' },

  // User
  U001: { actions: ['inline'] },
  U002: { actions: ['inline'] },
  // ... (33개 전체)

  // Payment
  PAY001: { actions: ['inline', 'retry'], message: '결제 금액이 변경되었습니다. 재조회 후 다시 시도해주세요.' },
  PAY003: { actions: ['silent'] },  // 멱등 응답
  PAY009: { actions: ['toast', 'retry'] },
  // ...
};

export function resolveAction(code: string): ErrorAction {
  return ERROR_CODE_ACTIONS[code] ?? { actions: ['toast', 'retry'] };  // fallback
}
```

---

## 검증 · CI

- [ ] FE Vitest: 모든 BE ErrorCode가 `ERROR_CODE_ACTIONS`에 등록되었는지 assertion (매핑 누락 감지)
- [ ] BE 신규 코드 추가 PR에 이 문서 갱신 강제 (PR 템플릿 체크박스)
- [ ] BE `ErrorCode.java`와 이 문서의 코드 리스트 diff 자동 감지 (스크립트)

---

## 갱신 이력

| 날짜 | 변경 | 담당 |
|---|---|---|
| 2026-07-02 | 초기 33개 코드 매핑 (0.0.1v BE 기준) | (초기) |
| — | (BE 신규 코드 추가 시 이력 기록) | |

---

## 참조

- BE 원본: `src/main/java/nbc/c1oud_mall/common/exception/ErrorCode.java`
- BE 예외 규범: `.claude/rules/exception.md`
- BE ApiResponse: `src/main/java/nbc/c1oud_mall/common/response/ApiResponse.java`
- FE SDD (§8 실패 모드): `workflows/task/fe/fe-workspectrum/sdd/sdd.md`
- FE sdd-lite (§4 계약): `workflows/task/fe/fe-workspectrum/sdd-lite/sdd-lite.md`
- FE-ADR-P1-005: ErrorCode UX 매핑 유지 정책
