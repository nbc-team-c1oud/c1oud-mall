# AI-ADR-001: `portonePaymentId` 채번 — UUID v4 + DB UNIQUE

- 상태: Accepted
- 일자: 2026-06-01
- 관련 Story: `workflows/product.md` Story 1-1 (Payment Aggregate 생성 + `portonePaymentId` 사전 채번)
- 관련 ADR: `src/main/java/nbc/c1oud_mall/payment/docs/adr/0001-portone-payment-id-strategy.md`

---

## Context

결제 진입 시 클라이언트가 PortOne SDK로 결제를 시작하려면 **서버가 결제 ID(`portonePaymentId`)를 먼저 채번**해서 클라이언트에 줘야 한다. 이 ID는 두 가지 역할을 동시에 한다:

1. **외부 키** — PortOne SDK가 결제 요청에 같이 보냄
2. **내부 멱등 키** — 결제 확정 API와 웹훅이 "같은 결제"임을 식별할 단일 키

제약:
- 동시 주문 생성 시 충돌이 사실상 0
- 외부 노출 안전 (enumeration 공격 차단)
- 별도 ID 생성 서비스 의존 최소화

선택지: **DB auto-increment** / **UUID v4** / **ULID·KSUID** / **Snowflake**.

---

## Decisions

1. **UUID v4 채택.** `java.util.UUID.randomUUID()`로 `Payment.of(...)` 안에서 생성.
2. **DB 레벨 UNIQUE 제약 강제.** `payments.portone_payment_id`에 `uk_payments_portone_payment_id` 인덱스.
3. **컬럼 타입**: `VARCHAR(36)` (UUID 표준 길이).
4. **충돌 시**: 자동 재시도 안 함. UNIQUE 위반 → `DataIntegrityViolationException` → application layer에서 `BusinessException(PAYMENT_DUPLICATE_PAYMENT_ID, PM002, 409)`로 번역.

---

## 프롬프트 내용 (AI 활용시)

> "결제 도메인에서 클라이언트가 PortOne SDK에 넘길 `paymentId`를 서버가 사전 채번해야 합니다.
> 두 가지 역할을 동시에 합니다:
> 1) PortOne 외부 통신 키
> 2) 결제 확정 API와 웹훅이 공유하는 우리 측 멱등 키
>
> 후보로 (a) DB auto-increment, (b) UUID v4, (c) ULID/KSUID, (d) Snowflake가 있는데 어떤 게 적절할지,
> 그리고 충돌 발생 시 정책을 어떻게 가져가야 할지(자동 재시도 vs 예외 변환) 의견 부탁합니다.
> 현재 환경은 단일 인스턴스 + 단일 DB (H2 dev / MySQL prod)이고, ID는 클라이언트에 노출됩니다."

---

## AI 응답 요약

| 후보 | 평가 |
|---|---|
| auto-increment | ❌ 외부 노출 시 enumeration 공격(연속 ID 추측) 가능. 외부 키로 부적합 |
| UUID v4 | ✅ JDK 표준, 충돌 확률 ~10⁻³⁶, 외부 노출 안전 |
| ULID/KSUID | △ 시간 정렬성 장점이지만 라이브러리 의존 추가. 결제 도메인에서 정렬성은 핵심 가치 아님 |
| Snowflake | ❌ 분산 ID 생성기 — 단일 인스턴스에 과잉. 인프라 비용만 증가 |

충돌 정책:
- UUID v4 충돌은 사실상 0 → 자동 재시도 도입의 ROI가 음수
- DB UNIQUE 위반은 **application layer에서 도메인 예외로 번역** 권장 (Repository try-catch 금지 — errorhandling.md §3)
- 같은 컨텍스트가 반복되면 그때 가서 재시도 ADR로 분리

추가 제안:
- 향후 결제 건수가 매우 커지면 `CHAR(36)` → `BINARY(16)` 변환 검토 가능 (인덱스 페이지 크기 절감)
- 단 1차에는 가독성 우선 → `VARCHAR(36)` 그대로

---

## Consequences

### 장점
- JDK 표준 라이브러리만으로 해결 — 의존성 0
- 외부 노출 안전 (예측 불가)
- DB UNIQUE 위반 처리가 errorhandling.md 패턴과 정확히 일치 (Repository는 자연 발생, application이 번역)
- 향후 분산 환경으로 가도 그대로 유효 (Snowflake로 갈 필요 사라짐)

### 단점
- `VARCHAR(36)` 인덱스가 정수 PK보다 큼 (페이지당 항목 수 ↓)
- 시간 정렬성 없음 → 결제 로그 분석 시 별도 `createdAt` 기준 정렬 필요 (실제론 부담 거의 없음)
- 우연 충돌 발생 시 사용자는 PM002 (409) 받음 — 운영 인시던트로 다뤄야 함

---

## 내가 수정한 부분

- AI는 충돌 시 "재시도 vs 예외 변환" 양쪽 가능성을 열어뒀는데, **재시도는 1차에서 도입하지 않기로 명시 못박음.** 재시도 코드가 들어가는 순간 "이 재시도가 어떤 의미인가"를 매번 추적해야 하고, 사실상 0 확률 사건 대응에 코드 복잡도를 쓰는 게 비합리.
- AI 제안엔 없던 항목 추가: "재시도가 필요해질 경우 **별도 ADR로 결정**한다" — Story 인수 조건 #3의 "재채번 후 재시도 OR 도메인 예외 변환" 중 후자를 선택하면서, 미래에 정책이 바뀔 가능성을 명시적으로 ADR 경로로 못박음.
- AI는 `BINARY(16)` 변환 가능성을 제시했는데, **언제 검토할지의 기준**(결제 건수 임계치 등)은 1차 ADR에서 다루지 않기로 결정. 별도 ADR 트리거로 남김.

---

## 최종 반영 여부

- ✅ 코드 반영: `Payment.of(...)`에서 `UUID.randomUUID().toString()`로 채번. `payments.portone_payment_id` 컬럼 + UNIQUE 제약.
- ✅ ErrorCode: `PAYMENT_DUPLICATE_PAYMENT_ID("PM002", "결제 ID 채번 충돌이 발생했습니다.", HttpStatus.CONFLICT)`
- ✅ 일반 ADR: `payment/docs/adr/0001-portone-payment-id-strategy.md`
- 미래 트리거: 결제 건수 폭증 시 `BINARY(16)` ADR, 충돌 빈도 증가 시 자동 재시도 ADR
