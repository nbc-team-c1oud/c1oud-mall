# ADR 0001 — ApiResponse<T> 공통 응답 래퍼 도입

- 상태: Accepted
- 날짜: 2026-05-29
- 결정자: c1oud_mall 팀
- 관련: `CLAUDE.md` 4장(DTO), 7장(금지), 8장(예외 처리)

---

## 배경 (Context)

c1oud_mall은 Spring Boot 4 + Java 21 기반 쇼핑몰 백엔드로, 다수의 도메인(주문, 결제, 상품, 사용자, 카트 등)에서 다양한 REST API를 노출한다.
프론트엔드/모바일/3rd-party 연동이 늘어남에 따라 다음 요구가 생겼다.

- 모든 응답이 **동일한 메타 구조**(성공 여부·코드·메시지·서버 시각)를 갖길 원함
- 성공/실패 응답이 **같은 포맷**이어야 클라이언트 파싱·에러 처리가 단순해짐
- 외부 변경 시점·코드 식별이 일관돼야 함 (로깅·재시도·관측)
- HTTP status만 보고 분기하지 않고 본문의 `success` 또는 `code`로도 분기 가능해야 함

## 결정 (Decision)

성공/실패 모든 HTTP 응답을 공통 래퍼 `ApiResponse<T>`로 통일한다.

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success,
    String code,        // 성공은 "OK", 실패는 도메인 에러 코드 (USER001 등)
    String message,
    T data,             // 실패 시 null (직렬화 제외)
    Instant timestamp   // 서버 응답 시각
) { ... }
```

- **정적 팩토리만 공개**: `success(data)`, `success(data, message)`, `successNoContent()`, `error(code, message)`
- **컨트롤러 반환 타입은 `ResponseEntity<ApiResponse<XxxResponse>>`** 로 통일
- 헬퍼 `ApiResponses.ok / ok(msg) / noContent / created(uri)` 제공
- 실패 응답은 `GlobalExceptionHandler`가 자동으로 `ApiResponse.error(code, message)`로 변환

이전 `ErrorResponse(code, message, path, timestamp)`는 **폐기**. 디버깅용 요청 경로는 응답 본문이 아닌 GlobalExceptionHandler 로그에만 기록한다.

## 대안 (Alternatives)

### A. raw DTO 직접 반환 (`ResponseEntity<XxxResponse>` 또는 `XxxResponse`)
- 장점: Spring 기본, 가장 단순, ProblemDetail 같은 표준과도 호환
- 단점: 성공/실패 포맷 불일치, 클라이언트가 200/4xx별로 다른 스키마 처리 필요, 응답 메타 없음 (시각·식별 코드)

### B. RFC 7807 `ProblemDetail` (Spring 6+)
- 장점: 표준 스펙(IANA application/problem+json), Spring이 기본 지원
- 단점: **실패 응답 전용**이라 성공 응답과 포맷이 다름 (통일 목적과 맞지 않음). 성공/실패 통일이라는 본 결정의 핵심 요구를 충족 못 함.

### C. ApiResponse<T> 통합 래퍼 — **채택**
- 장점: 성공/실패 동일 구조, 클라이언트 파싱 단순, 메타데이터(success/code/timestamp) 일관
- 단점: HTTP status와 본문 `success`가 중복(약간), 비표준, 외부 공개 API에 사용 시 표준 클라이언트 도구와 호환성 낮음

## 트레이드오프

| 항목 | 영향 |
|---|---|
| HTTP status와 본문 `success` 이중 표시 | 클라이언트는 둘 중 하나만 봐도 됨. 모순될 일 없음 (GlobalExceptionHandler가 동시에 세팅) |
| `record`의 canonical constructor가 public | 정적 팩토리 사용을 강제할 수 없음 (Java record 한계). 컨벤션 + 코드 리뷰로 강제 |
| `path` 필드 미포함 | 디버깅 시 로그에서 path 조회 필요. 응답 본문 슬림화의 trade-off로 수용 |
| 비표준 포맷 | 외부 공개 API 추가 시 별도 ProblemDetail 응답을 병행하거나 컨버터 도입 필요 |
| Jackson `@JsonInclude(NON_NULL)` 의존 | `data: null`이 직렬화에서 제외됨. 전역 ObjectMapper 설정과 무관하게 클래스 레벨로 보장 |

## 결과 (Consequences)

- 모든 컨트롤러는 `ResponseEntity<ApiResponse<T>>` 반환 (헬퍼로 단축)
- `GlobalExceptionHandler` 4개 핸들러 모두 `ResponseEntity<ApiResponse<Void>>` 반환
- 예전 `ErrorResponse.java` 삭제
- 컨벤션 문서(`CLAUDE.md` 4/7/8장, `.claude/rules/` 3개) 갱신
- 도메인이 늘어날 때마다 `ErrorCode` enum에 항목 추가 → 자동으로 `ApiResponse.error` 본문에 일관 적용

## 향후 검토 트리거

- 외부 파트너에 공개 API 제공 시 ProblemDetail 병행 여부
- WebSocket·SSE 등 비 HTTP 응답 채널이 늘어날 경우 별도 메시지 포맷 정의
