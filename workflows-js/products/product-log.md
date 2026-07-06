# [Product 4] 운영 관측성 — 구조화 로깅 · MDC 컨텍스트 · 에러 로깅 표준

## Product Vision
> c1oud-mall의 모든 런타임 동작(요청·예외·외부 호출·배치)이 남기는 로그를 JSON 구조와 MDC 기반 `requestId` 위에 올린다. 동시 요청이 섞여도 한 요청의 흐름을 `requestId` 하나로 추적할 수 있고, 예외 유형이 적절한 로그 레벨로 자동 분류되는 상태를 만든다. 향후 부하 테스트·모니터링·알림 파이프라인의 최하위 레이어로 기능한다.

## 배경 및 문제
- 현재 상황 (As-Is)
  - Spring Boot 기본 평문 포맷 사용 → `grep` 외에 검색·집계 수단 없음
  - 요청 단위 식별자(`requestId`) 없음 → 동시 요청 로그가 뒤섞임
  - 인증 컨텍스트(`userId`)가 로그에 자동 실리지 않음
  - `GlobalExceptionHandler`가 모든 예외를 동일한 `log.warn(...)`로 → 4xx/5xx 신호 혼재
  - 민감정보 로깅 정책이 코드 컨벤션(`.claude/rules/exception.md`)만 존재, 자동 방지 장치 부재
- 발생하는 문제
  - 특정 사용자 문의 → 로그 검색 시 시간·경로만 기준 → 어렵고 부정확
  - 후속(부하 테스트·모니터링)에서 "지표가 튀는 순간의 요청"을 로그에서 골라낼 수 없음
  - 결제·환불 실패 시 재현에 필요한 컨텍스트가 로그에 없음
  - ERROR 로그가 진짜 5xx와 단순 검증 실패를 구분 못 함 → 향후 알림 무력화 리스크
- 왜 지금 해결해야 하는가
  - 로그 포맷은 prod에서 한 번 굳어지면 호환성 때문에 바꾸기 어려움 (수집기·대시보드 룰 의존)
  - 첫 트래픽 유입 전 표준화가 가장 저렴
  - 결제·환불 도메인이 활성화된 시점 (Product 1·2 진행 중)이므로 관측 기반 시급

## 목표 (To-Be)
- 모든 prod 로그가 JSON 한 줄 포맷 · 표준 스키마 (`timestamp · level · logger · message · requestId · userId · exception`)
- 모든 HTTP 요청에 `requestId` 자동 부여 · 인증 요청은 `userId` MDC 주입
- 응답 헤더 `X-Request-Id`로 클라이언트도 동일 식별자 사용 가능
- 동시 요청 MDC 교차 오염 0건 (스레드 풀 재사용 시 clear 보장)
- `GlobalExceptionHandler`가 예외 유형별 ERROR/WARN/INFO 자동 분리 — ERROR는 실제 5xx만
- 비밀번호·accessToken·PortOne accessToken은 어떠한 경로로도 로그 노출 0건
- 로그 레벨 가이드 `docs/logging.md` 존재 (신규 예외 추가 시 참조)

## 설계 결정 (Design Decisions)

- **Logback + logstash-logback-encoder 채택** (Log4j2 미채택)
  - Spring Boot 기본 스택 위 최소 침습
  - Log4j2는 트래픽 규모 대비 이점 미미, CVE 관리 부담
- **profile 분기는 단일 `logback-spring.xml`에서 `<springProfile>` 태그**
  - 별도 설정 파일 분리(`logback-spring-prod.xml` 등)는 drift 위험
- **MDC 기반 `requestId` 전파, OpenTelemetry 미도입 (v1)**
  - 단일 서비스 구조에서 분산 트레이싱 오버킬
  - 필드명은 OpenTelemetry Semantic Conventions와 호환 (`traceId` 필드 자리 예약)
- **예외 유형별 로그 레벨 강제 분리**
  - ERROR: 예상치 못한 5xx (NPE · DB 연결 실패 · 외부 API 타임아웃)
  - WARN: `BusinessException` 계열 (재고 부족 · 권한 없음 등)
  - INFO: 4xx 검증 실패 (`MethodArgumentNotValidException` 등)
  - DEBUG: 외부 API 요청/응답 본문 (prod 비활성)
- **민감정보는 application 레이어에서 코드 컨벤션으로 차단** (자동 마스킹 v2)
  - `User.toString()` 비밀번호 제외 (`@ToString.Exclude`)
  - `LoginRequest.toString()`에서 password `**` 마스킹
  - JWT 토큰: prefix 8자 + `**`만 로깅

## 대안 검토 (Alternatives Considered)

### 로깅 스택
**Option A — Log4j2**
- 거부 이유: 트래픽 규모에서 성능 차이 무의미, CVE 관리 부담

**Option B (선택) — Logback + logstash-logback-encoder**
- 비용: 이후 로그 규모 커지면 async appender 검토 필요
- 보상: Spring Boot 기본 스택, 안정성

**Option C — 직접 JSON 어펜더 구현**
- 거부 이유: 재발명, 유지보수 부담

### 요청 추적 스코프
**Option A (선택) — MDC 기반 requestId (단일 프로세스 내부)**
- 비용: 향후 서비스 분리 시 OpenTelemetry 도입 필요
- 보상: 지금 규모에 딱 맞음

**Option B — OpenTelemetry Java Agent**
- 거부 이유: 오버킬. 단일 EC2 + RDS 구조에 부적합

### 민감정보 차단
**Option A (선택) — 코드 컨벤션 + `toString()` 정비**
**Option B — 커스텀 `@Loggable` + 리플렉션 자동 마스킹**
- 거부 이유: 오버엔지니어링 (v2 검토)

## 전체 아키텍처 (High-Level Architecture)

### 컴포넌트 배치
```
HTTP 요청 → Servlet Container
              ↓
        [MdcLoggingFilter]   ← Epic 2 Story 2-1
        - requestId 발급
        - MDC.put("requestId", ...)
        - 응답 헤더 echo back
              ↓
        [AuthenticationFilter] (기존)
              ↓
        [UserIdMdcFilter]    ← Epic 2 Story 2-2
        - MDC.put("userId", ...)
              ↓
        Controller → Service → Repository
              ↓
        [예외 발생 시]
        GlobalExceptionHandler → 예외별 로그 레벨 자동 분리 ← Epic 3
              ↓
        Logback → LogstashEncoder → JSON 한 줄 출력  ← Epic 1
```

### 핵심 플로우
**1. 요청 로그 흐름**
```
Client (X-Request-Id: 없음)
  → MdcLoggingFilter (신규 requestId 발급)
    → MDC.put · 응답 헤더 세팅
      → 이후 모든 로그에 requestId 자동 삽입
        → 응답 종료 · MDC.clear (누수 방지)
```

**2. 에러 로그 흐름**
```
Business logic → BusinessException(PAY001) 발생
              → GlobalExceptionHandler.handleBusiness
                → log.warn (WARN 레벨 자동)
                → ApiResponse.error 반환
```

### Out-of-Process 의존
- (v1은 in-process. v2에서 Loki/CloudWatch Logs 등 수집기 도입 시 인바운드 없음, 아웃바운드 표준 stdout)

## 실패 모드 / 운영 관측 (Failure Modes & Observability)
> **이 Product는 관측성 자체가 목표이므로, 이 섹션은 "관측 시스템 자체가 실패했을 때"를 다룬다.**

### 실패 시나리오와 응답
| 시나리오 | 관측 지점 | 응답 |
| --- | --- | --- |
| `MdcLoggingFilter` NPE | 즉시 서비스 실패 · 배포 롤백 | 통합 테스트 필수 |
| MDC 값 누수 (스레드 풀 재사용) | 동시성 테스트에서 검출 | 필터에서 `finally { MDC.clear() }` 강제 |
| JSON 인코딩 실패 (한글·특수문자) | 부팅 시 인코더 오류 로그 | logback-spring.xml 사전 검증 |
| 로그 볼륨 급증 (오류 스톰) | CloudWatch/CPU 지표 | Rate limiting은 v2, 초기엔 관측만 |

### 로깅 정책 (재귀적 — 이 Product 자체의 정책)
- **항상 기록**: 필터 진입·나감, requestId 생성, MDC 세팅
- **절대 금지**: 비밀번호 원문, PortOne accessToken, JWT 전체값 (prefix 8자만)

### 관측 지표 (해당 시 — 향후 Metrics 도입 시)
- `logging_events_total{level=ERROR|WARN|INFO}` — counter
- `mdc_leak_detected_total` — counter (테스트 · 개발 프로파일)

## 롤아웃 / 마이그레이션 (Rollout)

### 전제
- 로그 볼륨 낮음 (초기 사용자)
- 현재 로그 소비자 없음 (grep만) → 포맷 변경 파장 적음
- 일괄 배포 가능

### Product 의존성
- 선행: 없음 (관측성의 가장 아래 레이어)
- 후행: 향후 모니터링 Product · 부하 테스트 Product

### Epic·Story 의존성 그래프
```
Epic 1 (JSON 포맷) ──► Epic 2 (MDC 전파)
                          │
                          └─► Epic 3 (에러 로깅 표준)
                                  │
                                  └─► Epic 4 (운영 가이드 문서)
```

### 환경별 설정 분기
| 항목 | dev (H2) / local | prod (RDS) |
| --- | --- | --- |
| logback appender | 콘솔 평문 | 콘솔 JSON |
| 로그 레벨 | DEBUG (com.c1oud) | INFO |
| MDC 화이트리스트 | requestId, userId, method, path | 동일 |
| JVM 로그 옵션 | 기본 | GC 로그 · heap dump on OOM |

## 성공 지표 (KPI)
| 지표 | 현재 값 | 목표 값 | 측정 방법 |
| --- | --- | --- | --- |
| prod 로그 JSON 포맷 비율 | 0% | 100% | prod 로그 샘플 100건 수동 검사 |
| `requestId` 포함 비율 (HTTP 요청) | 0% | 100% | `grep -v requestId` 결과 0건 |
| `userId` 포함 비율 (인증 요청) | 0% | 100% | 인증 요청 샘플 50건 |
| 동시 요청 MDC 교차 오염 | 측정 불가 | 0건 | 동시 요청 격리 단위 테스트 |
| ERROR 로그 중 실제 5xx 비율 | ~30% (추정) | ≥ 95% | 1주 운영 후 로그 분류 |
| 민감정보 노출 건수 | 측정 불가 | 0건 | 마스킹 단위 테스트 + 샘플 검사 |
| 응답 헤더 `X-Request-Id` 포함 비율 | 0% | 100% | 응답 헤더 샘플 |

## Scope
**In Scope**:
- `logstash-logback-encoder` 도입 + `logback-spring.xml` profile 분기
- `MdcLoggingFilter` (requestId 발급·전파·clear · 응답 헤더 echo)
- 인증 후 userId MDC 주입 (JWTFilter 이후)
- `GlobalExceptionHandler` 정비 (예외 유형별 로그 레벨 자동 분리)
- 민감정보 차단 DTO 정비 (User · LoginRequest · JWT)
- 로그 레벨 가이드 (`docs/logging.md`) · README 링크
- 동시 요청 격리·MDC 누수 방지 단위 테스트

**Out of Scope**:
- 로그 수집 인프라 (Loki + Promtail · ELK · CloudWatch Logs) — v2
- OpenTelemetry · Zipkin · Jaeger — 서비스 분리 시점
- `@Loggable` 자동 마스킹 — v2
- 로그 기반 알림 룰 — v2
- Audit Log (감사 로그) — 별도 도메인 요구
- 외부 호출(PortOne 등) trace context 전파 — 분산 트레이싱 도입 시점

## 대상 사용자
- **백엔드 개발자 (팀)**: 신규 예외 추가 시 가이드 참조 · 재현·디버깅 시 requestId 검색
- **운영자**: 에러 신고 접수 시 `X-Request-Id`로 로그 traceback
- **QA**: 통합 테스트에서 MDC 격리 검증

## 연결된 Epic 목록
- [ ] Epic 1: JSON 구조화 로그 포맷 — `logstash-logback-encoder` · profile 분기 · 표준 필드 스키마
- [ ] Epic 2: MDC 요청 컨텍스트 전파 — `MdcLoggingFilter` · requestId · userId · 동시성 안전
- [ ] Epic 3: 에러 로깅 표준화 — `GlobalExceptionHandler` 예외별 로그 레벨 · 민감정보 차단
- [ ] Epic 4: 운영 가이드 문서화 — `docs/logging.md` · PR 템플릿 체크리스트

## 관련 문서
- 후행 Product: 향후 "모니터링·부하 테스트" Product
- 관련 ADR (예정): "Logging Stack Selection", "Tracing Scope", "Exception-to-LogLevel Mapping"
- 위치: `workflows/living-docs/Ai-adr/`
- 참고: Logback 공식 문서 · `logstash-logback-encoder` README · OpenTelemetry Semantic Conventions
- CLAUDE.md §8 · `.claude/rules/exception.md`
- Brainstorming 참조: `workflows/task/pes/brainstorming/0.0.1v/ops.md`

## 열린 질문 (Open Questions)
- 로그 수집 인프라 도입 시점 (CloudWatch Logs vs Loki)?
- Async Appender(`AsyncAppender`) 도입 트리거 (부하 테스트 후 GC pause 영향)?
- 부하 테스트에서 MDC 누수 검증 시나리오 어떻게 설계?
- 결제·환불 도메인의 특수 로그 정책이 추가로 필요한가 (예: `paymentId`도 MDC 화이트리스트에)?

## 제품 수준 완료 기준 (Product-level DoD)
- [ ] 모든 Epic DoD 통과
- [ ] prod에서 JSON 로그 정상 출력 · `jq` 파싱 검증
- [ ] 동시 요청 격리 단위 테스트 통과
- [ ] 민감정보 마스킹 단위 테스트 통과
- [ ] ADR 3건 발행 (Stack · Scope · Exception-LogLevel)
- [ ] `docs/logging.md` 작성 · README 링크

---

# [Epic 1] JSON 구조화 로그 포맷

## 목표
c1oud-mall의 prod·dev 로그를 JSON 한 줄 포맷으로 출력하고, 모든 로그 라인이 표준 스키마(`timestamp · level · logger · message · requestId · userId · application · exception`)를 따르게 한다. local 프로파일은 개발자 가독성을 위해 평문 유지.

## 배경
- 현재 Spring Boot 기본 평문 · 환경별 분기 없음
- 로그 수집기(Loki·CloudWatch Logs 등)는 JSON 전제
- 표준 필드명을 OpenTelemetry Semantic Conventions와 미리 호환 → 향후 마이그레이션 비용 낮춤
- 이 Epic 완료 후에야 후속 Epic 2·3에서 MDC 필드가 JSON에 실림

## 포함 Story
- Story 1-1: `logstash-logback-encoder` 도입 및 profile별 JSON Appender 구성

## Epic 인수 시나리오
- Given `spring.profiles.active=prod`
- When 컨트롤러 응답 후 로그 라인 관찰
- Then JSON 한 줄, 표준 스키마 필드 모두 포함

## Epic 완료 기준 (DoD)
- [ ] `logstash-logback-encoder` 의존성 추가 (`build.gradle.kts`)
- [ ] `logback-spring.xml`이 profile 분기 (local 평문 / dev·prod JSON)
- [ ] `application: c1oud-mall` 고정 필드 포함
- [ ] Story 1-1 DoD 통과

---

## [Story 1-1] logstash-logback-encoder 도입 및 profile별 JSON Appender 구성

### User Story
- As a 백엔드 개발자
- I want 모든 prod 로그를 JSON 한 줄 포맷으로 출력, local은 평문 가독성 유지
- so that 향후 수집기 연동 시 포맷 마이그레이션 불필요, 개발 중엔 로그 눈으로 읽기 편함

### 설명
- 의존성: `net.logstash.logback:logstash-logback-encoder:8.0`
- `src/main/resources/logback-spring.xml`:
  - `<springProfile name="local">` → `CONSOLE_PLAIN` (기본 평문)
  - `<springProfile name="dev,prod">` → `CONSOLE_JSON` (LogstashEncoder)
- 표준 필드 화이트리스트: `requestId`, `userId`, `method`, `path`, (`paymentId`는 열린 질문)
- `<customFields>{"application":"c1oud-mall"}</customFields>`
- logger 레벨: `nbc.c1oud_mall: DEBUG(dev)/INFO(prod)`, `org.hibernate.SQL: DEBUG(dev)/OFF(prod)`

**핵심 파일**:
- `build.gradle.kts` (의존성)
- `src/main/resources/logback-spring.xml` (신규)

### 완료 기준 (AC)
- Given `--spring.profiles.active=prod` / When 애플리케이션 실행 / Then 콘솔 로그가 JSON 한 줄
- Given `--spring.profiles.active=local` / When 실행 / Then 기존 평문 유지
- Given JSON 라인 / When jq로 파싱 / Then `timestamp · level · logger_name · thread_name · message · application` 필드 모두 존재
- *(엣지 - 예외)* 예외 발생 시 `stack_trace` 필드에 풀 트레이스가 한 JSON 라인
- *(엣지 - 한글)* 한글 메시지가 JSON 이스케이프되어 안전 출력
- *(엣지 - MDC 누수 방지)* 화이트리스트 외 임의 MDC 키는 JSON에 노출 X

### Definition of Done
- [ ] 코드 리뷰 완료
- [ ] prod·local 로그 샘플 비교 스크린샷
- [ ] `bootRun ... | jq .` 정상 파싱 검증
- [ ] ADR "Logging Stack Selection" 작성

### 스토리 포인트
3 SP

### 의존성
- 선행: 없음
- 후행: Story 2-1 (MDC 필드가 JSON에 실리려면 이 화이트리스트 필요)

---

# [Epic 2] MDC 요청 컨텍스트 전파

## 목표
모든 HTTP 요청 흐름에 `requestId`가 자동 부여되고, 인증된 요청에는 `userId`가 함께 MDC에 실리며, 응답 헤더 `X-Request-Id`로 클라이언트도 동일 식별자를 사용할 수 있다. 동시 요청 · 스레드 풀 재사용 시에도 MDC 누수 0건.

## 포함 Story
- Story 2-1: `MdcLoggingFilter` 구현 (requestId 발급·전파·clear · 응답 헤더 echo)
- Story 2-2: 인증 컨텍스트(userId) MDC 주입 (JWTFilter 이후 위치)

## Epic 인수 시나리오
- Given HTTP 요청 도착
- When 필터 체인 통과
- Then 모든 후속 로그에 `requestId` 자동 삽입 · 응답 헤더 `X-Request-Id` 포함

*(엣지 - 동시성)* Given 동시 다중 요청 / When 각 스레드 처리 / Then MDC 값 교차 오염 0건 (finally clear 보장)

## Epic 완료 기준 (DoD)
- [ ] `MdcLoggingFilter` + `UserIdMdcFilter` 구현
- [ ] 응답 헤더 `X-Request-Id` echo back
- [ ] 동시 요청 격리 단위 테스트 통과
- [ ] 각 Story DoD 통과

### [Epic 2 Story 세부 — 후속 상세화]

---

# [Epic 3] 에러 로깅 표준화

## 목표
`GlobalExceptionHandler`가 예외 유형별로 ERROR/WARN/INFO 로그 레벨을 자동 분리하고, 민감정보(비밀번호·토큰)는 어떠한 경로로도 로그에 노출되지 않게 한다. ERROR 로그는 향후 알림의 신호로만 예약.

## 포함 Story
- Story 3-1: `GlobalExceptionHandler` 정비 (예외별 로그 레벨) + 민감정보 차단 (DTO `toString()` 정비 + JWT 마스킹)

## Epic 완료 기준 (DoD)
- [ ] 예외 → 레벨 매핑 표 코드에 반영 (ERROR/WARN/INFO)
- [ ] `User`·`LoginRequest`·JWT 관련 DTO `toString` 정비
- [ ] 단위 테스트: 비밀번호 필드 포함 요청 검증 실패 시 로그에 값 노출 X
- [ ] ADR "Exception-to-LogLevel Mapping" 작성

### [Epic 3 Story 세부 — 후속 상세화]

---

# [Epic 4] 운영 가이드 문서화

## 목표
로그 레벨 사용 가이드 · 표준 필드 스키마 · 신규 예외 추가 시 결정 프로세스를 `docs/logging.md`에 문서화하고, README에서 링크한다. PR 템플릿에도 "새 예외 추가 시 로그 레벨 결정 근거" 체크박스 추가.

## 포함 Story
- Story 4-1: `docs/logging.md` 작성 + README 링크 + PR 템플릿 체크리스트

## Epic 완료 기준 (DoD)
- [ ] `docs/logging.md` 존재
- [ ] README에 링크
- [ ] PR 템플릿에 로그 레벨 체크박스 추가
- [ ] 신규 예외 1건 추가 시 가이드만 보고 결정 가능 여부 검증

### [Epic 4 Story 세부 — 후속 상세화]

---

## Product 요약

| Epic | Story 수 | SP 합계 |
| --- | --- | --- |
| Epic 1: JSON 구조화 | 1 | 3 |
| Epic 2: MDC 전파 | 2 | 5 |
| Epic 3: 에러 로깅 표준 | 1 | 3 |
| Epic 4: 운영 가이드 | 1 | 2 |
| **합계** | **5** | **13 SP** |

**진행 순서 (필수)**: Epic 1 → 2 → 3 → 4
- Epic 1·2는 PR을 묶어도 무방
- Epic 3은 Epic 2의 MDC 존재 전제
- Epic 4는 마지막 일괄 (코드 안정 후 문서화)
