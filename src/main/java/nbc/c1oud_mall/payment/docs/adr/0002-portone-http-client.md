# ADR-0002: PortOne V2 REST API 호출용 HTTP 클라이언트 선택

- 상태: Accepted
- 일자: 2026-06-01
- 범위: 결제 BC (Story 2-1)

## Context

Epic 2(결제 확정)의 "본문 비신뢰" 원칙을 구현하려면 결제 BC가 PortOne V2 REST API(`GET /payments/{paymentId}`)를 동기 호출하여 결제 정보를 재조회해야 한다. 결제 확정 트랜잭션은 동기적으로 진행되며(consistency.md §6 — DB 커밋 전 PortOne 조회로 검증), 호출이 끝날 때까지 트랜잭션이 대기하는 흐름이다.

다음 제약을 만족해야 한다.

- 동기 호출 모델 (트랜잭션 컨텍스트 안에서 사용)
- 외부 응답 4xx/5xx/타임아웃의 분리 가능한 예외 매핑
- 인증 토큰(시크릿)의 외부 노출 없는 주입
- 테스트 시 HTTP 모킹이 가벼울 것 (별도 외부 의존성 없이)

## Decision

1. **HTTP 클라이언트로 Spring `RestClient` 채택** — Spring Boot 4 내장 동기 클라이언트.
2. **인증 토큰은 `@ConfigurationProperties("portone")`로 주입** — 환경변수 `PORTONE_API_SECRET`(기본값 빈 문자열)에서 읽음.
3. **`PortOneProperties`(record)**로 `baseUrl`·`secret` 보존. `PortOneClientConfig`에서 `@EnableConfigurationProperties`로 활성화.
4. **테스트는 `MockRestServiceServer`**(`spring-test` 내장) + `@RestClientTest` 자동 구성을 사용. 별도 HTTP 모킹 라이브러리(WireMock, OkHttp MockWebServer) 추가하지 않음.
5. **외부 응답 매핑 격리**: PortOne의 원시 JSON 스키마를 `PortOnePaymentResponse`(infrastructure 패키지 내부 record)로 1차 디시리얼라이즈한 뒤, `toInfo()`로 `PortOnePaymentInfo`(application/dto, 도메인 친화 VO)로 변환. PortOne 스키마 변경의 영향이 어댑터 안으로만 흡수됨.

## Alternatives

- **Spring `WebClient` (Reactor Netty 기반)**: 반응형 모델. 결제 트랜잭션이 동기라 `.block()` 사용 강제 → 코드 복잡도와 가독성 손실. Reactor 스택 트레이스 추적도 부담.
- **Spring Cloud OpenFeign**: 선언형 인터페이스 기반. Spring Cloud 전체 BOM 의존성 추가 필요. 현재 외부 호출 도메인이 PortOne 하나(향후 결제 취소까지 합쳐도 두 엔드포인트)라 도입 ROI 낮음.
- **순수 Apache HttpClient/OkHttp 직접 사용**: 응답 디시리얼라이즈·에러 핸들링을 직접 구성해야 함. Spring 생태계 통합 이점 포기.

## Consequences

- **4xx vs 5xx 분리 번역 강제**: `RestClient.retrieve().onStatus(...)`로 두 분기를 명시. `ErrorCode.PORTONE_RESPONSE_INVALID(PM005, BAD_GATEWAY)`(4xx, 재시도 불가)와 `PORTONE_QUERY_FAILED(PM004, BAD_GATEWAY)`(5xx/타임아웃/IO, 재시도 가능)로 매핑. Story 2-2에서 재시도 정책 분기 시 이 구분을 사용한다.
- **타임아웃·IO 오류 매핑**: `ResourceAccessException` catch → PM004. JSON 파싱 실패 등 일반 `RestClientException` catch → PM005.
- **`PortOnePaymentResponse`의 `toInfo()` 안에서 status enum 매핑 실패도 PM005로 통합** — application 레이어가 "어댑터는 항상 정상 VO 또는 PM004/PM005 중 하나" 라는 단일 계약만 다룸.
- **테스트 격리**: `@RestClientTest`가 `RestClient.Builder`를 MockRestServiceServer에 연결된 빌더로 교체 → 통합 테스트 없이 4xx/5xx/IO 케이스 빠르게 검증. PortOne 실제 호출 없이 회귀 보호.
- **운영 secret 관리는 별도 ADR로**: 현 단계는 환경변수 직접 주입(`PORTONE_API_SECRET`). 운영 단계에서 secret manager(AWS Secrets Manager, Vault 등)로 확장할지는 별도 ADR로 결정한다.

## References

- workflows/product.md — [Story 2-1] 결제 BC가 PortOne 결제 정보를 조회한다
- .claude/rules/consistency.md §6 — 외부 호출은 DB 커밋 전(검증) 또는 후(보상). 본 어댑터는 검증 경로의 동기 호출
- .claude/rules/errorhandling.md §2~§3 — 외부 SDK 예외는 External Adapter에서 Infra/Domain 예외로 번역
- .claude/rules/idempotency.md §7 — PortOne 보장(`paymentId` 기반 결제 멱등)에 의존하지 말고 우리 측 멱등(UNIQUE) 추가. 본 어댑터는 조회 전용이라 멱등성 별도 요구사항 없음
