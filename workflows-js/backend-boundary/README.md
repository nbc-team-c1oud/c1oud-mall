# [Backend Boundary] BE ↔ FE 정합성 SSOT

> **역할**: 백엔드와 프론트엔드 사이의 계약을 단일 진실 소스(SSOT)로 관리한다. ErrorCode 매핑 · 공통 응답 스키마 · 인증 헤더 · 페이지네이션 규약 등.
>
> **원칙**: BE에서 이 폴더의 내용이 바뀔 때 FE는 관련 코드·SDD·sdd-lite를 함께 갱신한다. 반대도 마찬가지.

---

## 파일 구성

| 파일 | 담당 |
|---|---|
| [`error-codes.md`](./error-codes.md) | BE `ErrorCode` enum 33개 · FE UX 액션 매핑 (SSOT) |
| (추후) `api-response.md` | `ApiResponse<T>` envelope 스펙 · Zod 스키마 매핑 |
| (추후) `auth.md` | JWT 저장·만료·401 처리 정책 |
| (추후) `pagination.md` | 페이징 규약 (page · size · sort) |
| (추후) `portone.md` | PortOne SDK 통합 규약 (storeId · channelKey · V2 Secret 분리) |

---

## 갱신 프로세스

### BE가 ErrorCode를 추가/변경할 때
1. `src/main/java/nbc/c1oud_mall/common/exception/ErrorCode.java` 갱신
2. `backend-boundary/error-codes.md` 매핑 표 갱신 (같은 PR)
3. FE는 감지 (CI 스크립트 또는 리뷰) → `src/lib/api/errors.ts` 액션 매핑 갱신 → FE PR

### FE가 UX 액션을 조정할 때
1. `backend-boundary/error-codes.md`의 "FE UX 액션" 열 갱신
2. `src/lib/api/errors.ts` 코드 반영
3. BE는 감지 (읽기 전용) — BE 자체 로직에는 영향 없음

### 계약 변경 (엔드포인트 · 응답 스키마)
1. BE Product SDD의 §6(계약 · Contract) 또는 §7(아키텍처)에 명세
2. `backend-boundary/`에 관련 문서 신설 (예: `api-response.md`)
3. FE는 `sdd-lite`로 handoff 문서 작성 · Zod 스키마 갱신

---

## PR 게이트

- [ ] BE ErrorCode 신규 추가 시 `backend-boundary/error-codes.md`도 함께 갱신했는가
- [ ] FE UX 매핑 갱신 시 대응 BE ErrorCode가 실제 존재하는가 (grep으로 확인)
- [ ] BE 응답 스키마 필드 변경 시 FE Zod 스키마도 함께 조정 계획이 있는가

---

## 참조

- BE ErrorCode 원본: `src/main/java/nbc/c1oud_mall/common/exception/ErrorCode.java`
- BE ApiResponse 원본: `src/main/java/nbc/c1oud_mall/common/response/ApiResponse.java`
- BE 예외 처리 규범: `.claude/rules/exception.md`
- BE DTO 규범: `.claude/rules/dto.md`
- FE SDD: `workflows/task/fe/fe-workspectrum/sdd/sdd.md`
- FE-ADR-P1-005: ErrorCode UX 매핑 유지 정책
