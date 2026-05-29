# ApiResponse<T> 적용 가이드

이 문서는 컨트롤러에서 `ApiResponse<T>` 공통 응답 래퍼를 어떻게 쓰는지(신규/기존 코드)와 마이그레이션 단계를 설명한다.

> 배경·결정은 `docs/adr/0001-api-response-wrapper.md` 참고.
> 컨벤션 위치: `CLAUDE.md` 4장(DTO), 7장(금지), 8장(예외 처리).

---

## 1. 기본 패턴

### 1.1 성공 응답 (데이터 있음)

```java
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> get(@PathVariable Long id) {
        return ApiResponses.ok(orderService.findById(id));
    }
}
```

응답 본문:
```json
{
  "success": true,
  "code": "OK",
  "message": "Success",
  "data": { "id": 1, "status": "CREATED", ... },
  "timestamp": "2026-05-29T16:42:11.123Z"
}
```

### 1.2 생성 (201 Created + Location)

```java
@PostMapping
public ResponseEntity<ApiResponse<OrderResponse>> create(
        @RequestBody @Valid OrderCreateRequest request) {
    OrderResponse response = orderService.create(request.toCommand());
    return ApiResponses.created(response, URI.create("/orders/" + response.id()));
}
```

### 1.3 데이터 없는 성공 (취소·삭제·상태 변경 등)

```java
@PostMapping("/{id}/cancel")
public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable Long id) {
    orderService.cancel(id);
    return ApiResponses.noContent();   // 200 OK + data:null (직렬화 제외)
}
```

응답 본문:
```json
{
  "success": true,
  "code": "OK",
  "message": "Success",
  "timestamp": "2026-05-29T16:42:11.123Z"
}
```

### 1.4 사용자 정의 메시지

```java
@PostMapping("/{id}/approve")
public ResponseEntity<ApiResponse<PaymentResponse>> approve(@PathVariable Long id) {
    return ApiResponses.ok(paymentService.approve(id), "결제가 정상 승인되었습니다.");
}
```

### 1.5 목록 / 페이지

```java
@GetMapping
public ResponseEntity<ApiResponse<Page<OrderListResponse>>> list(
        @ModelAttribute OrderListQuery query) {
    return ApiResponses.ok(orderService.list(query));
}
```

---

## 2. 실패 응답 (자동 처리)

컨트롤러에서 `BusinessException`을 throw하면 `GlobalExceptionHandler`가 자동으로 `ApiResponse.error(code, message)` 응답을 반환한다. 컨트롤러는 try-catch 불필요.

```java
// 서비스에서
if (order == null) {
    throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
}
```

응답 본문 (자동):
```json
{
  "success": false,
  "code": "ORDER001",
  "message": "주문을 찾을 수 없습니다.",
  "timestamp": "2026-05-29T16:42:11.123Z"
}
```

상세 정보가 필요하면 `withDetail`:
```java
throw BusinessException.withDetail(ErrorCode.ORDER_NOT_FOUND, "orderId=" + id);
// message → "주문을 찾을 수 없습니다. — orderId=42"
```

### 2.1 Validation 실패

`@Valid` 검증 실패는 자동으로 `ErrorCode.INVALID_INPUT` (400) 응답:
```json
{
  "success": false,
  "code": "C001",
  "message": "잘못된 입력입니다. — userId: must not be null, items: must not be empty",
  "timestamp": "..."
}
```

---

## 3. 신규 컨트롤러 체크리스트

- [ ] 반환 타입 `ResponseEntity<ApiResponse<XxxResponse>>` (혹은 `<Void>`)
- [ ] 성공 응답은 `ApiResponses.ok / created / noContent` 헬퍼로
- [ ] 실패는 `BusinessException` throw — `try-catch` 금지 (GlobalExceptionHandler가 처리)
- [ ] Service는 `XxxResponse` / `Page<XxxResponse>`까지만 반환 — `ApiResponse` 래핑은 **컨트롤러에서만**
- [ ] `ApiResponse` 생성자 직접 호출 금지 — 정적 팩토리만

---

## 4. 기존 컨트롤러 마이그레이션 단계

(현재는 도메인 컨트롤러가 없지만, 추후 코드가 도입된 후를 가정한 가이드)

### Step 1. 반환 타입 변경
```java
// Before
public OrderResponse get(Long id) { return service.find(id); }

// After
public ResponseEntity<ApiResponse<OrderResponse>> get(Long id) {
    return ApiResponses.ok(service.find(id));
}
```

### Step 2. void 메서드 처리
```java
// Before
public void cancel(Long id) { service.cancel(id); }

// After
public ResponseEntity<ApiResponse<Void>> cancel(Long id) {
    service.cancel(id);
    return ApiResponses.noContent();
}
```

### Step 3. `@ResponseStatus` 어노테이션 정리
`ResponseEntity`로 status 제어가 가능하므로 `@ResponseStatus(HttpStatus.CREATED)` 같은 어노테이션은 제거하고 `ApiResponses.created(...)` 사용.

### Step 4. 임의 에러 응답 제거
컨트롤러나 서비스에서 `Map`, `String`, 직접 만든 에러 DTO로 응답하던 부분을 모두 `BusinessException` throw로 전환.

```java
// ❌ Before
@ExceptionHandler(SomeException.class)
public ResponseEntity<Map<String, Object>> handle(SomeException ex) {
    return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
}

// ✅ After: ErrorCode 추가 + BusinessException으로 변환하거나 GlobalExceptionHandler에 핸들러 추가
@ExceptionHandler(SomeException.class)
public ResponseEntity<ApiResponse<Void>> handle(SomeException ex, HttpServletRequest req) {
    log.warn("SomeException at {}", req.getRequestURI(), ex);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ErrorCode.SOME_DOMAIN_ERROR.getCode(),
                                    ErrorCode.SOME_DOMAIN_ERROR.getMessage()));
}
```

### Step 5. 테스트 갱신
JSON 경로 매칭이 달라진다.

```java
// Before
.andExpect(jsonPath("$.id").value(1))

// After
.andExpect(jsonPath("$.success").value(true))
.andExpect(jsonPath("$.code").value("OK"))
.andExpect(jsonPath("$.data.id").value(1))
.andExpect(jsonPath("$.timestamp").exists())
```

실패 테스트:
```java
.andExpect(jsonPath("$.success").value(false))
.andExpect(jsonPath("$.code").value("ORDER001"))
.andExpect(jsonPath("$.data").doesNotExist())   // @JsonInclude(NON_NULL) 효과
```

---

## 5. 자주 빠지는 함정

| 함정 | 해결 |
|---|---|
| 컨트롤러에서 `XxxResponse` 직접 반환 | `ResponseEntity<ApiResponse<XxxResponse>>`로 감싸기 (`ApiResponses.ok(...)`) |
| `ApiResponse` 생성자 직접 호출 | 정적 팩토리(`success`/`error`/`successNoContent`)만 사용 |
| 컨트롤러에서 try-catch로 예외 처리 | throw만 하고 `GlobalExceptionHandler`에 위임 |
| Service에서 `ApiResponse` 반환 | Service는 도메인 DTO까지만, 래핑은 컨트롤러에서 |
| 임의 에러 메시지 하드코딩 | `ErrorCode` enum에 먼저 추가 후 `BusinessException(ErrorCode.XXX)` |
| `data: null`이 응답에 포함되는 경우 | `ApiResponse` 클래스 레벨 `@JsonInclude(NON_NULL)` 덕분에 제외 — 다른 클래스에 동작 안 하면 전역 ObjectMapper 설정 확인 |
| `timestamp` 직렬화가 epoch milliseconds로 나옴 | 전역 `ObjectMapper`에 `JavaTimeModule` 등록 + `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS = false`. Spring Boot starter-webmvc는 기본 활성 |

---

## 6. 파일 위치 정리

| 파일 | 위치 |
|---|---|
| `ApiResponse<T>` | `nbc.c1oud_mall.common.response.ApiResponse` |
| `ApiResponses` 헬퍼 | `nbc.c1oud_mall.common.response.ApiResponses` |
| `BusinessException` | `nbc.c1oud_mall.common.exception.BusinessException` |
| `ErrorCode` | `nbc.c1oud_mall.common.exception.ErrorCode` |
| `GlobalExceptionHandler` | `nbc.c1oud_mall.common.exception.GlobalExceptionHandler` |
| 단위 테스트 | `src/test/java/nbc/c1oud_mall/common/response/` |
