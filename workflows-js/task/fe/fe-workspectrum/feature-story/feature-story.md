# [FE Feature-Story] 1 PR 단위 양식

> **사용 시점**: 1~3일 작업 · 단일 파일 or 소수 파일 · 1 PR
> **원칙**: User Value + AC + DoD + INVEST 체크 (Independent · Negotiable · Valuable · Estimable · Small · Testable)

---

## 사용 시점 (트리거)

- 1건 · 1~3일 완료
- 단일 컴포넌트 or 훅 or 스키마 or 라우트 (조합 소규모)
- 1 PR 스코프
- BE 짝 Story는 완결되어 있고 FE만 조정

---

## 양식 골격

```markdown
# [Story] {제목}

## User Value
- As a {역할}
- I want {행위}
- so that {가치}

## 설명

**대상 파일** (1~3개):
- `src/features/{feature}/{File}.tsx` — 무엇을 어떻게 바꾸는가
- `src/lib/api/schemas/{file}.ts` — Zod 스키마 필드 추가/변경

**Hook 시그니처** (있으면):
- `const { data } = use{X}()` — 데이터 소스 / 변화

**BE 계약** (해당 시):
- Endpoint: `{METHOD} /api/v1/...`
- ErrorCode 대응: `{CODE}` → {UX 액션}

## 완료 기준 (AC)
- Given {선행} / When {트리거} / Then {결과}
- *(엣지 - 사유)* Given ... / When ... / Then ...
- *(예외 - 사유)* Given ... / When ... / Then ...

## Definition of Done
- [ ] 대상 파일 구현
- [ ] Zod 스키마 (해당 시)
- [ ] Vitest 단위 테스트 ({N}건, 해피/엣지/예외 매트릭스)
- [ ] a11y 검증 (해당 시)
- [ ] backend-boundary 매핑 최신 (ErrorCode 관련 시)

## Out of Scope
- {제외 1} — 사유
- {제외 2}

## INVEST 점검
- **Independent**: 다른 Story 없이 단독 완결 가능
- **Negotiable**: {협상 여지 · 예: 라이브러리 선택}
- **Valuable**: {즉시 얻는 사용자 가치}
- **Estimable**: 파일 · 테스트 범위 명확
- **Small**: 1~3일 내
- **Testable**: 단위 or E2E로 검증 가능
```

---

## 실 예시 (골드 스탠다드 참고)

```markdown
# [Story] 결제 확정 실패 시 PAY001 inline error UI

## User Value
- As a 결제 시도 사용자
- I want 금액 불일치(PAY001) 시 페이지 이동 없이 즉시 재조회할 수 있길
- so that 결제 창을 다시 열지 않고 상황을 파악·재시도할 수 있다

## 설명

**대상 파일**:
- `src/features/payment/PaymentConfirmPage.tsx` — 400 · PAY001 응답 시 `<InlineError>` 표시 + 재조회 버튼
- `src/features/payment/usePaymentConfirm.ts` — `onError` 콜백에서 code 분기

**Hook**:
- `const { mutate, error } = usePaymentConfirm()`

**BE 계약**:
- Endpoint: `POST /api/v1/payments/confirm`
- ErrorCode: `PAY001 PAYMENT_AMOUNT_MISMATCH` (HTTP 400)
- UX 액션: inline error + 재조회 버튼 (라우팅 X · toast X)

## 완료 기준 (AC)
- Given PortOne 결제 완료 후 confirm 호출 / When BE가 400 · `code=PAY001` 반환 / Then `<InlineError text="금액 재조회 필요" />` + 재조회 버튼 표시
- *(엣지 - 재조회 클릭)* Given 재조회 버튼 클릭 / When mutation 재실행 / Then loading state 진입 · 이전 error 클리어
- *(예외 - 500)* Given 예상 외 500 응답 / When 응답 처리 / Then toast "서버 오류" (inline X · 기존 catch-all 유지)

## Definition of Done
- [ ] `PaymentConfirmPage.tsx` 수정
- [ ] `usePaymentConfirm.ts` onError 분기 로직
- [ ] Vitest 3 케이스 (성공 / PAY001 / 500)
- [ ] `backend-boundary/error-codes.md` PAY001 UX 매핑 반영

## Out of Scope
- 다른 결제 관련 ErrorCode 처리 — 별도 Story
- 재조회 버튼의 재시도 rate limit — 별도 Story

## INVEST 점검
- **Independent**: ✅ 단독 완결
- **Negotiable**: 재조회 UX 형태(버튼 vs 자동 재시도)
- **Valuable**: 결제 실패 UX의 첫 번째 개선점
- **Estimable**: 파일 2개 · 테스트 3건
- **Small**: 0.5일
- **Testable**: Vitest + MSW로 완결
```

---

## 참조

- **FE SDD (풀버전)**: `../sdd/sdd.md`
- **FE PES (3~10 Story 티어)**: `../pes/pes.md`
- **FE one-line-spec (≤1시간 티어)**: `../one-line-spec/one-line-spec.md`
- **BE feature-story**: `../../../pes/workspectrum/feature-story/`
- **backend-boundary**: `../../../../backend-boundary/error-codes.md`
