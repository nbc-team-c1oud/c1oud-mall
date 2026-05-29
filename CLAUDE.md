# c1oud-mall 코딩 컨벤션

> Spring Boot 4.0.6 / Java 21 기반 쇼핑몰 프로젝트.
> DDD + 레이어드 아키텍처. 모든 코드 작성·생성은 아래 규칙을 따른다.

## 0. 기술 스택

- Spring Boot 4.0.6 / Java 21 (Gradle Kotlin DSL)
- Spring Web MVC, Security, Validation, Data JPA
- H2 (개발/테스트) / MySQL (운영)
- QueryDSL — 동적·복잡 조회용 (도입 예정)
- Lombok — 보일러플레이트 제거용

---

## 1. 기본 원칙

- DDD 기반으로 진행한다. 비즈니스 규칙은 **domain**에 둔다.
- 레이어: `presentation` · `application` · `domain` · `infrastructure`
- 의존성 방향은 **항상 domain을 향한다.** domain은 어떤 레이어에도 의존하지 않는다.

```
presentation ──▶ application ──▶ domain ◀── infrastructure
```

---

## 2. 패키지 구조

컨텍스트(예: order, payment, product, user, cart) 아래에 레이어로 나눈다.

```
nbc.c1oud_mall.<context>
├── presentation     # Controller, Request/Response DTO
├── application      # Service, Command/Query DTO, 유스케이스 조합
├── domain           # 엔티티/VO/도메인 서비스, Repository 인터페이스(port)
└── infrastructure   # JPA Entity, Repository 구현, Projection, QueryDSL
```

공통 컴포넌트(예외 처리, 설정 등)는 `nbc.c1oud_mall.common` 아래에 둔다.

```
nbc.c1oud_mall.common
├── exception        # ErrorCode, BusinessException, GlobalExceptionHandler, ErrorResponse
└── config           # SecurityConfig, QuerydslConfig 등
```

---

## 3. 레이어별 책임

| 레이어 | 책임 | 의존 |
| --- | --- | --- |
| presentation | HTTP 입출력, 검증, Request↔Command 매핑 | application |
| application | 유스케이스 조합, 트랜잭션 경계(`@Transactional`) | domain |
| domain | 비즈니스 규칙, 상태 전이. 외부 기술 모름 | 없음 |
| infrastructure | 영속성·외부연동. domain 인터페이스 구현 | domain |

---

## 4. DTO 규칙 ⭐

레이어마다 DTO 타입이 다르다. **DTO는 레이어를 넘어 새지 않는다.**

| 레이어 | 입력 | 출력 | 비고 |
| --- | --- | --- | --- |
| presentation | `XxxRequest` | `XxxResponse` | 도메인별 응답 DTO. `ApiResponse.data`에 들어감 |
| application | `XxxCommand` (쓰기) / `XxxQuery` (읽기) | 도메인 객체 or `XxxResponse`로 변환 | 서비스 입력 DTO |
| infrastructure | - | `XxxProjection` (조회 결과) / JPA Entity | 조회는 Projection, 영속은 ORM |

**공통 응답 래퍼** ⭐

- 모든 HTTP 응답(성공/실패)은 `ApiResponse<T>` 래퍼로 통일
- 컨트롤러 반환 타입: `ResponseEntity<ApiResponse<XxxResponse>>` 또는 `ResponseEntity<ApiResponse<Void>>`
- **정적 팩토리만 사용**: `ApiResponse.success(data)` / `ApiResponse.success(data, message)` / `ApiResponse.successNoContent()` / `ApiResponse.error(code, message)`
- `ResponseEntity` 래핑 단축은 `ApiResponses.ok(...)` / `ApiResponses.created(...)` 등 헬퍼 사용
- 패키지: `nbc.c1oud_mall.common.response`

**흐름**

- 쓰기: `Request` → `Command` → domain → (infra) Entity 저장 → `Response` → `ApiResponse.success(response)` 반환
- 읽기: `Query` → (infra) `Projection` 조회 → application → `Response` → `ApiResponse.success(response)` 반환
- 실패: `BusinessException` throw → `GlobalExceptionHandler`가 `ApiResponse.error(code, message)`로 변환

---

## 5. ORM / 조회

- 기본은 **JPA**.
- 동적 쿼리·복잡 조회는 **QueryDSL** 사용. 결과는 `Projection`으로 받는다.
- **JPA Entity를 presentation까지 노출하지 않는다.** 항상 Response로 변환.

---

## 6. 네이밍

- 패키지·클래스는 도메인 용어(유비쿼터스 언어)로 명명.
- DTO: `PaymentCreateRequest`, `PaymentCreateCommand`, `PaymentListQuery`, `PaymentResponse`, `PaymentListProjection`
- Repository 인터페이스는 domain에, 구현체(`XxxRepositoryImpl`)는 infrastructure에.
- ErrorCode 상수: 도메인 접두사 + 3자리 번호 (`USER001`, `ORDER001`, 공통은 `C001`)

---

## 7. 금지 ❌

**아키텍처 / DTO**
- domain → 다른 레이어 의존
- JPA Entity를 Request/Response로 직접 사용
- Command/Query를 컨트롤러에서 직접 반환
- 컨트롤러에서 `ApiResponse` 래핑 없이 `XxxResponse`/원시 타입 직접 반환 (반드시 `ResponseEntity<ApiResponse<T>>`)
- `ApiResponse` 생성자 직접 호출 (정적 팩토리 메서드만 사용)

**예외 처리**
- 도메인마다 별도 Exception 클래스 남발 (`BusinessException`으로 통일)
- ErrorCode에 없는 메시지를 컨트롤러/서비스에서 하드코딩
- `ApiResponse` 외의 임의 응답 포맷 (단, 파일 업로드 등 명시적 예외는 유지)
- Lombok으로 대체 가능한 생성자/getter 수동 작성

---

## 8. 예외 처리 (필수 준수)

우리 프로젝트의 예외 처리는 `common.exception` 패키지의 4개 파일을 **표준**으로 삼는다.
새 도메인/기능 작업 시 반드시 이 패턴을 그대로 따른다.

### 8.1 구조 개요

- **`ErrorCode`** (enum): 모든 에러를 `(code, message, HttpStatus)` 3요소로 정의하는 단일 출처(SSOT)
- **`BusinessException`**: 비즈니스 예외의 단일 타입. ErrorCode를 들고 다님
- **`ApiResponse<T>`** (`common.response`): 모든 HTTP 응답의 공통 래퍼 (성공/실패 통일)
- **`GlobalExceptionHandler`** (`@RestControllerAdvice`): 모든 예외를 `ApiResponse<Void>` JSON으로 변환
- **도메인별 예외 클래스를 새로 만들지 않고** `BusinessException + ErrorCode` 조합으로 표현

### 8.2 ErrorCode 작성 규칙

- enum 상수는 `이름("코드", "메시지", HttpStatus.XXX)` 형식
- 코드 네이밍: 도메인 접두사 + 3자리 번호 (예: `DECK001`, `USER003`, `AUTH005`)
- 공통 에러는 `C001`처럼 `C` 접두사
- 도메인별로 `// ─── 도메인명 ───` 주석 구분선으로 섹션을 나눌 것
- `@Getter @RequiredArgsConstructor` 사용. 생성자/getter 수동 정의 금지 (Lombok 위임)
- 필드: `private final String code; private final String message; private final HttpStatus status;`
- 보안 민감 케이스(로그인 실패 등)는 외부 메시지를 동일하게 통일하고, 코드로만 구분
  (`USER_NOT_FOUND` ↔ `PASSWORD_NOT_MATCHED` 패턴 참고)

### 8.3 예외 발생(throw) 규칙

- 비즈니스 예외는 **항상 `BusinessException`을 throw**한다. 커스텀 도메인 예외 클래스를 새로 만들지 말 것
- 기본: `throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);`
- 추가 정보가 필요하면: `throw BusinessException.withDetail(ErrorCode.XXX, "상세 내용");`
  - 이때 메시지는 `"기본메시지 — 상세"` 형태로 합쳐짐
- 새 에러 상황이 생기면 임의 메시지를 던지지 말고 **반드시 ErrorCode에 항목을 먼저 추가**한 뒤 사용

### 8.4 GlobalExceptionHandler 규칙

- 모든 핸들러는 `ResponseEntity<ApiResponse<Void>>` 반환 — 본문은 `ApiResponse.error(code, message)`
  - `timestamp`는 `ApiResponse` 내부에서 자동 생성 (`Instant.now()`)
  - 요청 경로(`req.getRequestURI()`)는 응답 본문에 포함하지 않고 로그에만 기록 (디버깅 용도)
- `BusinessException` → ErrorCode의 status/code 사용, message는 detail 포함된 `ex.getMessage()`
- `MethodArgumentNotValidException` (Validation) → `ErrorCode.INVALID_INPUT` 기반 400
- 새 표준 예외 타입을 추가할 때만 핸들러 메서드를 추가하고, `ApiResponse` 응답 포맷은 그대로 유지
- 보안 민감 응답(`AccessDeniedException` 등)은 ErrorCode enum 메시지로 통일하고, 구체 사유는 log에만 기록

---

## 9. Git 컨벤션 (필수 준수)

### 9.1 브랜치 전략

- `main` — 배포 기준. **직접 커밋 금지** (머지/태그만)
- `develop` — 통합 브랜치. **직접 커밋 금지** (PR 머지만)
- 모든 작업은 `develop`에서 분기한 단기 브랜치에서 진행 → `develop`으로 PR
- `hotfix/*`만 예외적으로 `main`에서 분기, 수정 후 `main`과 `develop` **양쪽 모두**에 머지

### 9.2 브랜치 네이밍

- 형식: `<type>/<설명>` 또는 `<type>/<번호>-<설명>`
- 소문자 + kebab-case만 (대문자/카멜케이스 금지)
- type: `feature` `fix` `hotfix` `refactor` `docs` `chore` `test`
- 예: `feature/006-deck-progress-status`, `fix/142-webhook-retry`

### 9.3 커밋 메시지 (Conventional Commits)

- 형식: `<type>(<scope>): <subject>` (+ 선택 body, footer)
- type: `feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`
- subject: 50자 이내, 명령형(현재형), 마침표 없음
- **한 commit = 한 가지 논리적 변경.** 변경 섞지 않기. 약 400~600줄로 분할 권장

### 9.4 PR 규칙

- 타겟 브랜치: `develop` (hotfix만 `main`)
- 제목은 커밋 컨벤션과 동일 형식: `feat(deck): 덱 진행 상태 조회 추가`
- 본문: **무엇을 / 왜 / 어떻게 테스트했는지** 포함
- 머지 방식: **Squash merge 권장**
- 한 PR = 한 가지 논리적 변경. 관련 없는 변경 섞지 않기

### 9.5 상세 규칙·PR 본문 템플릿·예시

`.claude/rules/git.md` 참고 (개인용 상세 문서).

---

## 참고

- **이 문서는 팀 공유 컨벤션이다.** 변경은 PR로 합의 후 반영.
- 개인 세부 규칙·코드 스니펫·체크리스트는 `.claude/rules/`(gitignore)에 둔다.
