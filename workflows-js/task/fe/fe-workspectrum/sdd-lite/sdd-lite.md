# [FE SDD-lite] BE→FE 핸드오프 · 러닝 · 계약 (풀버전)

> **목적**: BE Product/Story가 완료되어 FE가 실 통합해야 하는 시점의 **핸드오프 + 계약 + 러닝 매뉴얼**. 축약된 3축 형식.
> **사용 시점**: BE Product가 1개 완결되어 FE가 소비해야 할 때 · sdd(풀버전)를 쓰기엔 스코프가 작을 때 · 계약 정합성 확인 위주
> **위치**: `sdd-lite/version/{X.Y.Zv}/sdd-lite-{topic}.md`
> **버전 정합**: BE 마일스톤과 동일 SemVer

---

## 사용 시점 (트리거)

다음 중 **2개 이상** 만족 시 본 양식.

- [ ] BE Product/Epic이 완결되어 FE가 실 통합 필요
- [ ] BE 계약 변경 (ErrorCode 추가·응답 필드 변경·엔드포인트 신설)
- [ ] FE 수정 스코프가 sdd(풀버전)보단 작음 (Story 3~5건)
- [ ] "가능한 것 / 안 되는 것" 명확화가 필요 (임시 우회 폐기 시)
- [ ] 실 배포 전 러닝(수동 검증) 시나리오 필요

---

## 섹션 구조 (8섹션)

| # | 섹션 | 필수 | 목적 |
|---|---|---|---|
| 1 | Context | ✅ | 핸드오프 트리거 · 마일스톤 위치 · 다음 액션 |
| 2 | Snapshot | ✅ | 영향 항목 표 + 진실 소스 매트릭스 |
| 3 | Inventory | ✅ | 호출 가능 엔드포인트(화이트리스트) + 미노출 항목 |
| 4 | 계약 (Contract) | ✅ | 성공/실패 응답 JSON 예시 · Zod 스키마 매핑 · ErrorCode 분기 · 인증 헤더 |
| 5 | 체크리스트 | ✅ | `[경로 — 무엇 — 왜 — 확인방법]` 4축 (미달 축 = 실행 불가) |
| 6 | 러닝(Runbook) | ✅ | 부팅 전 신호 + N개 시나리오 (사용자 동작 → 기대 화면/상태/네트워크) + 통과 기준 |
| 7 | 열린 질문 | ✅ | 다음 버전 마이그레이션 기대 |
| 8 | Self-Check | ✅ | 전달 완결성 · 사실 정합성 · 실행 가능성 · 버전 연결 |

---

## 양식 골격

```markdown
# [FE SDD-lite] {제목} — {YYYY-MM-DD}

> 트리거: BE {Product/Epic} 완결 · FE {통합/UX 조정}
> BE 원본: `workflows/products/product-{name}.md` (또는 done 링크)
> 대응 마일스톤: `../../../fe-milestones/version/{X.Y.Zv}/milestone.md`

---

## 1. Context

- **핸드오프 트리거**: {BE PR 링크 · 완료 SDD · 새 ErrorCode 등}
- **마일스톤 위치**: {FE 버전 · 이번 주 스코프의 어디에 해당}
- **다음 액션**: {FE가 이 문서 이후 취할 첫 행동 1건}

---

## 2. Snapshot

### 영향 항목
| 항목 | 이전 | 이후 | 영향 파일 |
| --- | --- | --- | --- |
| `POST /api/v1/payments/confirm` 응답 | (미노출) | ✅ 노출 · `PaymentConfirmResponse` | `src/lib/api/endpoints/payment.ts` |
| ErrorCode `PAY001` | (없음) | ✅ 추가 · HTTP 400 | `src/lib/api/errors.ts` |

### 진실 소스 매트릭스
| 계약 항목 | 진실 소스 (BE) | FE 미러 |
| --- | --- | --- |
| `ApiResponse<T>` 스키마 | `common.response.ApiResponse` | `src/lib/api/schemas/api-response.ts` |
| ErrorCode 카탈로그 | `common.exception.ErrorCode` | `workflows/backend-boundary/error-codes.md` |
| PortOne 계약 | ADR 001 · ADR 011 | `src/lib/portone/*.ts` |

---

## 3. Inventory

### 호출 가능 엔드포인트 (화이트리스트)
| 메서드 | 경로 | 인증 | 비고 |
| --- | --- | --- | --- |
| POST | `/api/v1/payments/confirm` | ✅ | `PaymentConfirmRequest` → `PaymentConfirmResponse` |
| POST | `/api/v1/payments/webhooks/portone` | HMAC | **FE 호출 금지** — BE↔PG 전용 |

### FE 호출 금지 (미노출)
- `POST /api/v1/payments/webhooks/portone` — 서명 검증 필요, PG 전용
- 관리자 API (`/api/v1/admin/*`) — 별도 권한 검증

---

## 4. 계약 (Contract)

### 성공 응답 예시
`​`​`json
POST /api/v1/payments/confirm 200 OK
{
  "success": true,
  "code": "OK",
  "message": "Success",
  "data": {
    "paymentId": 123,
    "orderId": 456,
    "status": "COMPLETED",
    "pointEarnedAmount": 500,
    "confirmedAt": "2026-07-02T10:30:00.000Z"
  },
  "timestamp": "2026-07-02T10:30:00.123Z"
}
`​`​`

### 실패 응답 · ErrorCode 분기
| ErrorCode | HTTP | FE 처리 |
| --- | --- | --- |
| `PAY001` | 400 | inline error "결제 금액 재조회 필요" + 재조회 버튼 |
| `PAY002` | 502 | toast "일시적 오류 · 재시도" |
| `C003` | 403 | 접근 거부 modal · 홈 이동 |
| `C004` | 401 | 로그인 페이지 강제 이동 |

### Zod 스키마 매핑
`​`​`ts
// src/lib/api/schemas/payment.ts
export const PaymentConfirmRequestSchema = z.object({
  orderId: z.number().int().positive(),
  portonePaymentId: z.string().uuid(),
});

export const PaymentConfirmResponseSchema = z.object({
  paymentId: z.number(),
  orderId: z.number(),
  status: z.enum(['PENDING', 'COMPLETED', 'FAILED']),
  pointEarnedAmount: z.number().int().nonnegative(),
  confirmedAt: z.string().datetime(),
});
`​`​`

### 인증 헤더
- 모든 요청: `Authorization: Bearer <accessToken>`
- 만료 시 401 → 로그인 페이지 (Refresh 없음 · v1)

---

## 5. 체크리스트

각 항목은 `[경로 — 무엇 — 왜 — 확인방법]` 4축. 하나라도 빠지면 실행 불가.

- [ ] `src/lib/api/schemas/payment.ts` — Zod 스키마 등록 — BE `ApiResponse` 미러 — `tsc` 통과 · Vitest 통과
- [ ] `src/lib/api/endpoints/payment.ts` — `api.payment.confirm(input)` 함수 — 호출 진입점 — MSW mock 검증
- [ ] `src/features/payment/PaymentConfirmPage.tsx` — 페이지 컴포넌트 — 결제 확정 UI 진입점 — Route 등록 확인 (`/orders/:id/pay`)
- [ ] `src/features/payment/usePaymentConfirm.ts` — mutation hook — `useMutation` 래퍼 — 성공 시 라우팅 · 실패 시 ErrorCode 분기
- [ ] `workflows/backend-boundary/error-codes.md` — `PAY001·PAY002` UX 매핑 반영 — SSOT 갱신 — grep으로 확인

---

## 6. 러닝(Runbook)

### 부팅 전 신호
- [ ] BE prod 배포 완료 (`https://api.c1oud-mall.dev/actuator/health` 200 OK)
- [ ] `.env.production`의 `VITE_API_BASE_URL` 정확히 설정
- [ ] FE 빌드 통과 (`tsc && vitest run`)

### 시나리오 (사용자 동작 → 기대 결과)

**시나리오 1: 정상 결제 확정**
1. 로그인 → 상품 담기 → 주문 생성 → PortOne SDK 결제
2. `PaymentConfirmPage` 진입
3. **네트워크**: `POST /api/v1/payments/confirm` → 200 · `success=true`
4. **화면**: "결제 완료" toast + `/orders/:id/complete` 라우팅
5. **통과 기준**: DevTools Network 200 · Redux/Query cache에 `PaymentConfirmResponse` 저장

**시나리오 2: 금액 불일치 (PAY001)**
1. (테스트) 임의로 `totalAmount` 조작 후 confirm 호출
2. **네트워크**: 400 · `code: "PAY001"`
3. **화면**: inline error "결제 금액 재조회 필요" + 재조회 버튼
4. **통과 기준**: 페이지 이동 없음 · toast 없음 · 재조회 버튼 활성

**시나리오 3: 401 만료**
1. `localStorage.setItem('accessToken', 'invalid')`
2. Confirm 클릭
3. **네트워크**: 401 · `code: "C004"`
4. **화면**: toast "재로그인 필요" + `/login` 강제 이동
5. **통과 기준**: `localStorage.accessToken` 제거 확인 · 로그인 페이지 도달

---

## 7. 열린 질문

- 다음 버전(0.0.2v)에서 Refresh Token 도입 시 401 처리 로직 변경 필요
- PortOne SDK 결제 창 콜백에서 `portonePaymentId`가 누락될 엣지 케이스 대응?

---

## 8. Self-Check

- [ ] **전달 완결성**: BE에서 오는 계약이 이 문서만으로 실 통합 가능한가
- [ ] **사실 정합성**: `backend-boundary/error-codes.md`와 §4 계약이 일치
- [ ] **실행 가능성**: §5 체크리스트의 4축이 모두 채워짐 (경로 · 무엇 · 왜 · 확인)
- [ ] **버전 연결**: 대응 BE 버전 · FE 마일스톤 명시
- [ ] **러닝 검증**: §6 시나리오 최소 3건 · 통과 기준 명확
```

---

## 예시 (실 사례 링크)

`version/0.0.1v/`에 축적 시 여기에 링크:

- `version/0.0.1v/sdd-lite-payment-integration.md` — BE Payment Product 완료 후 FE 통합
- `version/0.0.1v/sdd-lite-refund-integration.md` — BE Refund Product 완료 후 FE 통합
- `version/0.0.1v/sdd-lite-cart-list-api.md` — Cart 조회 API 도입 후 FE 통합

---

## 참조

- **BE Handoff 리포트 (원본)**: `../../../pes/workspectrum/sdd/done/sdd-*-integration-*.md`
- **backend-boundary**: `../../../../backend-boundary/error-codes.md`
- **FE 마일스톤**: `../../fe-milestones/version/{X.Y.Zv}/milestone.md`
- **FE SDD (풀버전)**: `../sdd/sdd.md`
