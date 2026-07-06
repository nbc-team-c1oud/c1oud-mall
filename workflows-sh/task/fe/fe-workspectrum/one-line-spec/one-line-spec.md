# [FE One-Line-Spec] 최소 스펙 양식

> **사용 시점**: ≤1시간 작업 · 단일 파일 · 커밋 메시지 수준 스펙
> **원칙**: 1문장 요구사항 · 1~3개 완료 신호

---

## 사용 시점 (트리거)

- 1건 · ≤1시간
- 단일 파일 변경 (예: 텍스트 수정 · 스타일 조정 · 스키마 필드 추가)
- 커밋 메시지로 충분한 규모

---

## 양식 골격

```markdown
# [Spec] {한 문장 요구사항 — 동사로 시작}

## 대상
- `src/{path}/{file}.tsx` (또는 `.ts`)

## 완료 신호
- [ ] {신호 1 — 관찰 가능한 결과}
- [ ] {신호 2}
- [ ] {신호 3 — 필요 시}

## Out of Scope
- {제외 항목 · 사유}
```

---

## 실 예시

```markdown
# [Spec] CartList의 `subTotal` 필드명을 `subtotal`(소문자) 오탈자에서 정정한다

## 대상
- `src/features/cart/CartListPage.tsx` (렌더링 시 참조 필드)
- `src/lib/api/schemas/cart.ts` (Zod 스키마 필드명 재확인)

## 완료 신호
- [ ] CartList가 정상 렌더링 (undefined 표시 X)
- [ ] Vitest cart 스키마 테스트 통과
- [ ] BE 응답과 필드명 정합 (`subTotal` · v3 문서 준수)

## Out of Scope
- 다른 화면의 `subtotal` 참조 — 별도 스펙
```

---

## 참조

- **FE feature-story (1~3일 티어)**: `../feature-story/feature-story.md`
- **BE one-line-spec**: `../../../pes/workspectrum/one-line-spec/`
