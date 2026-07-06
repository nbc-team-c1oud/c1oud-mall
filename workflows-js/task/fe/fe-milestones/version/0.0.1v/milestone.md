# [FE M1 / 0.0.1v] — 첫 배포 정합 스켈레톤

> **역할**: BE 0.0.1v 완결(첫 프로덕션 배포)에 대응하는 FE 마일스톤 스켈레톤.
> **상태**: planning (양식만 이관 · 실 FE 착수 시 채움)

---

## BE M1 참조

- **BE 마일스톤**: `../../../../milestones/version/0.0.1v/milestone.md` (frozen · 8개 도메인 골격 완성 · PortOne 실 연동 · EC2 배포 성공)
- **BE 완료 신호**: 8/8 통과
- **FE 대응 스코프**: (착수 시 결정)

---

## 이번 주 스코프 (FE 관점)

### 정합 유지 필수 항목

- [ ] BE `ApiResponse<T>` 스키마와 정합되는 Zod 스키마 셋 (`src/lib/api/schemas/`)
- [ ] BE 33개 `ErrorCode` UX 매핑 (`workflows/backend-boundary/error-codes.md`)
- [ ] BE 26개 엔드포인트 대응 `api.*.*(input)` 함수 (`src/lib/api/endpoints/`)
- [ ] JWT accessToken 저장·재사용·401 강제 로그아웃

### Product 우선순위 (BE 완결도 기반)

| Product | BE 완료도 | FE 우선순위 |
|---|---|---|
| Payment | ✅ Epic 1~3 완료 | P0 · FE 결제 confirm 페이지 · PortOne SDK 통합 |
| Refund | ✅ Epic 1~2 완료 | P1 · 환불 요청 폼 (관리자 or 사용자 페이지) |
| Order | ✅ 대부분 | P0 · 주문 생성·조회·취소 페이지 |
| Cart | ✅ v3 통합 | P0 · 장바구니 화면 (`cartItemId` 정합) |
| Auth | ✅ 완료 | P0 · 회원가입·로그인·JWT 저장 |
| Product | ✅ 완료 | P0 · 상품 목록·상세 (QueryDSL 검색 활용) |
| Point | ✅ 완료 | P1 · 포인트 잔액·이력 표시 |
| Log | ⏸️ 미착수 | (FE Sentry 연동은 별도) |

---

## Tier 1 · Must (FE 착수 시 채움)

| # | Product | Story | 한 줄 설명 | SP |
|---|---|---|---|---|
| 1 | (착수 후 채움) | | | |

---

## Tier 2 · Want (stretch)

| # | 항목 | 비고 |
|---|---|---|
| W1 | | |

---

## 종료 신호 (Completion Signals)

FE 마일스톤 종료 조건:

- [ ] Tier 1 Story 중 ≥ 80% 머지
- [ ] Lighthouse ≥ 90 (main 페이지)
- [ ] Web Vitals RUM 초기 관측 시작 (LCP · INP · CLS)
- [ ] BE 26개 엔드포인트 중 사용자 노출 필수 항목 100% 통합
- [ ] `backend-boundary/error-codes.md` UX 매핑 완결
- [ ] E2E 시나리오 3건 이상 통과 (로그인 · 담기 → 주문 → 결제 확정)
- [ ] a11y 스크린 리더 검증 통과 (주요 페이지)

---

## 의존 Chain

```
[Auth Product]  로그인·JWT → 이후 모든 인증 API 흐름의 전제
      │
      ├─► [Product 목록·상세]
      │        │
      │        └─► [Cart 담기·조회·수량 변경]
      │                 │
      │                 └─► [Order 미리보기·생성]
      │                          │
      │                          └─► [Payment PortOne SDK · Confirm]
      │                                   │
      │                                   ├─► 성공: /orders/{id}/complete
      │                                   └─► 실패: PAY001·PAY002 UX 분기
      │
      └─► [Point 잔액·이력]  (결제 확정 후 잔액 갱신 관측)

[Refund] — 관리자 UI 또는 별도 사용자 UI (BE는 API 미노출 상태 · FE 착수 시 정책 결정)
```

---

## 작업 일정

FE 착수 후 채움.

---

## 리스크와 관찰 포인트

| 영역 | 리스크 | 관찰 포인트 |
|---|---|---|
| BE 계약 정합 | 배포 후 응답 필드가 문서와 달라진 케이스 | Zod 스키마 런타임 검증 실패 · Sentry 이벤트 |
| CORS | prod 하드코딩 origins (BE fix Issue 1 미해결) | FE 도메인 확정 시 BE 배포 필요 |
| JWT 만료 UX | Refresh 없음 · 1시간 만료 시 로그인 페이지 강제 이동 | 사용자 UX 피드백 |
| PortOne SDK | 팝업 차단 · 콜백 손실 | 시나리오 3건 이상 검증 |
| Bundle 크기 | 초기 300KB 예산 초과 | rollup-plugin-visualizer 관측 |

---

## 다음 마일스톤 (FE M2 / 0.0.2v) 후보

- BE Log Product 완결과 함께 Sentry + `X-Request-Id` breadcrumb 통합
- 401 처리 정책 리뷰 (Refresh Token 도입 여부 · FE-ADR-P0-001 재검토)
- FE 배포 파이프라인 정착 (`deploy-fe.yml` · S3 · CloudFront)
- Lighthouse CI GHA 통합

---

## 참조

- BE 마일스톤: `../../../../milestones/version/0.0.1v/milestone.md`
- FE 산출물: [`infra.md`](./infra.md) · [`performance.md`](./performance.md) · [`outcome.md`](./outcome.md) · [`cost.md`](./cost.md) · [`review.md`](./review.md) · [`ux-test.md`](./ux-test.md)
- FE SDD: `../../../fe-workspectrum/sdd/sdd.md`
- FE-ADR: `../../../fe-workspectrum/FE-ADR-CANDIDATES.md`
- backend-boundary: `../../../../../backend-boundary/error-codes.md`
