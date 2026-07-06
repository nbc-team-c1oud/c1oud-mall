# [0.0.3v · Issue 01] Admin 컨텍스트 · 관리자 JWT 롤 · 접근 제어

> **역할**: 백오피스 프레이밍의 시작점 — 모든 이벤트/쿠폰/CS 발동의 주체인 관리자 인증·인가 골격.
> **Week**: 1 · **Day**: 2
> **상태**: pending
> **Tier**: `feature-story`
> **캠프 요구사항 매핑**: (직접 매핑 없음 · 후속 이슈 06/08/09의 전제)

---

## 배경

백오피스 정체성 = 관리자가 타임세일 등록, 쿠폰 발급, CS 상담을 발동. 따라서 관리자 인증·롤 분기·접근 제어가 다음 이슈들의 전제. 기존 `auth` 도메인에 이미 슈퍼어드민 골격은 존재 (M1 완료) → 확장 형태로 신규 `admin` 컨텍스트를 붙임.

## 핵심 스코프

- 신규 컨텍스트 `nbc.c1oud_mall.admin.*` (4레이어)
- 관리자 롤 enum 확장 (`ADMIN` · `SUPER_ADMIN`)
- JWT claim에 role 삽입 · `JwtAuthFilter` 검증 확장
- Spring Security `@PreAuthorize("hasRole('ADMIN')")` 관리자 엔드포인트 보호
- `AdminController` 골격 (`/api/v1/admin/**` 접근 제어)
- 관리자 계정 시드 (dev/prod 프로파일 조건부)

## API 표면 (초안)

| 메서드 | 경로 | 인증 | 용도 |
|---|---|---|---|
| GET | `/api/v1/admin/me` | ADMIN | 관리자 프로필 |
| GET | `/api/v1/admin/users?page=&size=` | ADMIN | 유저 목록 조회 |
| PATCH | `/api/v1/admin/users/{id}/role` | SUPER_ADMIN | 롤 변경 |

## ErrorCode (신규)

- `ADM001` ADMIN_ACCESS_DENIED (403)
- `ADM002` ADMIN_ROLE_INVALID (400)
- `ADM003` ADMIN_ACCOUNT_NOT_FOUND (404)

## 산출물 (BE-Story 초안)

- BE-01-1: `admin` 컨텍스트 골격 (4레이어 폴더 · AdminController · AdminService)
- BE-01-2: `Role` enum 확장 (`ADMIN` · `SUPER_ADMIN`) + JWT claim 매핑
- BE-01-3: `JwtAuthFilter` role claim 추출 · `SecurityContext` 저장
- BE-01-4: Spring Security `@PreAuthorize` 적용 · `SecurityConfig` 관리자 경로 보호
- BE-01-5: `ErrorCode.ADM001~003` 등록
- BE-01-6: 관리자 계정 시드 (`AdminAccountInit` · dev/prod 조건부)
- BE-01-7: 통합 테스트 (관리자 로그인 · 롤 없이 접근 시 403)

## 관련 이슈 / 문서

- 다음: [02 더미 시드](./issue-02-dummy-seed-50k-datafaker.md) — 관리자 계정 포함
- 다음: [06 타임세일](./issue-06-timesale-event-domain.md) — 관리자가 등록
- 다음: [08 쿠폰](./issue-08-first-come-coupon-issuance.md) — 관리자가 정책 등록
- 다음: [09 CS 채팅](./issue-09-cs-chat-stomp-state-machine.md) — 관리자가 상담사
- 규범: `.claude/rules/architecture.md` · `.claude/rules/exception.md`
- 회의록: `C:\Users\user\.claude\plans\splendid-finding-feather.md` (10차 세션)

## 상세 (착수 시 채움)

_pending_
