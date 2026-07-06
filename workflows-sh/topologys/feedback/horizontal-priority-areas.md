# Topology Feedback — 수평 관리 관점 우선순위 영역

> 작성일: 2026-06-05
> 작성자: payment (jun)
> 참조 계약: `workflows/topologys/references/topology.md`
> 기존 topology: `API`, `Architecture-Style`, `Consistency-Design`, `Context-Dependency`, `Error-Handling`, `FK-Cascade-Policy`, `idempotency-Design`, `Identifier-System`, `tx`, `pr-git`(빈 파일)
> 목적: 다음 분기 topology 박음(pin) 시 **수평 관리 우선순위 결정 입력**

---

## 0. 한 줄 결론

> 기존 topology 10건은 **"가치"·"구조"·"코드 패턴"** 축에서 두텁다. 빠진 축은 **운영·보안·진화** — 즉 *시스템이 움직이고 변하는 동안의 관점*. **Auth/Authorization · Observability · Config/Secret · Schema Migration · External Integration(확장)** 5건이 수평 관리 1순위.

---

## 1. "수평 관리" 관점이란 — 정의

수평적 관리 = **한 BC 안에서 닫힌 결정이 아니라, BC 경계를 넘나들며 동일하게 적용돼야 하는 결정의 그래프**.

reference 계약의 행동 원칙(§1)을 다시 풀면:
- topology = "이번 구간 동안 고정되는 graph(노드·엣지·경계·불변식)"
- vocabulary는 그 안에서 자유, 하지만 graph를 BC마다 다르게 그리면 시스템이 분열

**수평 topology의 식별자 3개**:
1. **모든 BC가 같은 결정에 노출** — 한 BC가 어기면 다른 BC가 즉시 영향 받는가
2. **단일 BC 안에서 closed-form 답 불가** — 결정의 SSOT가 어디 한 군데에 있어야 하는가
3. **결정이 늦으면 비용이 증식** — 늦은 박음 = late binding penalty가 큰가

세 조건 모두 충족하면 **Layer 1 또는 Layer 2 area topology로 박을 가치**가 있음.

---

## 2. 기존 10개 topology 커버리지 진단

| 축 | Topology | Layer | 수평성 진단 |
|---|---|---|---|
| 시스템 구조 | Architecture-Style | L1 | ✅ 강함 — 모든 BC가 동일 layer 모형 |
| 데이터 위상 | Context-Dependency | L2 | ✅ 강함 — BC간 의존 그래프가 SSOT |
| 데이터 결합 | FK-Cascade-Policy | L2 | ✅ 강함 — Aggregate 경계 cross-cutting |
| 식별자 | Identifier-System | L1 | ✅ 강함 — 5종 분리가 전 BC 적용 |
| 가치(정합) | Consistency-Design | L1 | ✅ 강함 — Zone 매핑이 BC 횡단 |
| 가치(멱등) | idempotency-Design | L1 | ✅ 강함 — 진입점 카탈로그 horizontal |
| 코드 패턴 | tx | L2 area | 🟡 중간 — 결제 시나리오 중심 |
| 코드 패턴 | Error-Handling | L1 | ✅ 강함 — 번역 지점 horizontal |
| 진입점 | API | L2 area | 🟡 중간 — REST/Webhook 중심 |
| 운영 | pr-git | (빈 파일) | ❌ placeholder만 |

**진단**: 위계 두 축(L1 가치·구조 / L2 데이터 위상·코드 패턴)은 빽빽함. **운영 시점·외부 통합 다양성·시간 축 진화**에 대한 박음이 비어 있음.

---

## 3. 우선순위 TOP 5 — 수평 관리 1순위 누락 영역

### 3-1. ⭐ Authentication & Authorization Topology — P0

**왜 1순위인가**
- 사용자 인증·인가 결정은 **모든 사용자-facing 진입점이 공유하는 게이트**
- 단일 BC가 어긋나면 즉시 보안 사고로 번지는 cross-BC 위험
- 직접 증상: `CartController`의 `memberId = 1L` 하드코딩이 BE 통합 디버깅 절반을 잡아먹음 — Cart BC 단독 결정처럼 보였지만 실제는 *전 시스템이 같은 게이트를 통과한다*는 graph가 박혀있지 않아서

**기존 topology에서 이미 다룬 부분 (보강 출처)**
- `API-Topology` §1 신뢰 경계 (JWT / HMAC / NoAuth / Internal) — **진입 게이트**만 있음
- 인가(authorization) — **누가 무엇에 접근 가능한가** — 빠짐
- 소유권 검증 위상 — Payment.verifyOwnership 같은 메서드가 BC마다 다르게 생김 — graph 박힘 X

**박을 노드/엣지 예시 (vocabulary 아님)**
- Nodes: 인증 주체(End-User / Service / Internal Trigger / External Webhook)
- Edges: 주체 → 진입점, 진입점 → 인가 검증, 인가 검증 → Aggregate 권한
- Boundaries: 인증은 Filter, 인가는 Application Service. Controller에서 인가 로직 금지.
- Invariants: 사용자 자원 접근은 항상 `사용자 = JWT subject` 일치 검증을 통과한다. 임시 ID 하드코딩은 인가 zone 위반.

**수평성 점수**: 1(모든 BC 노출 ✅) · 2(단일 BC 답 불가 ✅) · 3(늦은 박음 비용 ↑✅)

---

### 3-2. ⭐ Observability & Audit Topology — P0

**왜 1순위인가**
- 사고가 났을 때 **모든 BC의 로그를 같은 어휘로 읽을 수 있어야 함**
- 직접 증상: `BusinessException.detail = null`로 throw하던 패턴이 BC마다 달라 CT003 디버깅에 한참 걸림. error-handling-topology는 *번역 지점*은 박았지만 *번역된 후 무엇이 로그·trace로 흘러가는가*는 박지 않음
- 트레이싱 ID(`X-Request-Id` / `traceparent`) 전파는 BC가 통일하지 않으면 무가치

**기존 topology에서 이미 다룬 부분 (보강 출처)**
- `Error-Handling-Topology` — 예외 *번역*만, 로그/trace 위상 없음
- `API-Topology` — 응답 코드만, observability 헤더 없음

**박을 노드/엣지 예시**
- Nodes: 구조화 로그 / 메트릭 / Trace / Audit Log / Alert
- Edges: 모든 BusinessException → 구조화 로그 + Trace ID 포함, AFTER_COMMIT 이벤트 → audit log
- Boundaries: PII 마스킹은 로깅 어댑터 layer (도메인은 마스킹 모름), Alert 트리거는 메트릭 zone에서만
- Invariants: 모든 진입점은 단일 Trace ID를 전 BC에 전파한다. BusinessException은 throw 직전 `detail`에 식별자 컨텍스트를 포함한다. (CT003 사태가 invariant 위반 사례)

**수평성 점수**: 1✅ · 2✅ · 3✅

---

### 3-3. ⭐ Configuration & Secret Topology — P1

**왜 1순위인가**
- `.env` 도입(어제)을 즉흥적으로 결정했지만, 환경 분리·secret rotation·feature flag 정책이 박혀있지 않으면 6개월 내 staging/prod 분리 시 폭발
- secret 누락 시 `IllegalStateException`으로 startup 실패하는 패턴이 PortOne webhook secret에는 있지만 다른 환경변수에는 없음 — **BC마다 다른 시작-시점 검증 정책**

**기존 topology에서 이미 다룬 부분 (보강 출처)**
- 없음 — 빈 영역

**박을 노드/엣지 예시**
- Nodes: 빌드 시점 상수 / 환경별 properties / Secret (환경변수·KMS) / Feature Flag / 외부 식별자(storeId 등)
- Edges: 환경별 yml은 환경 의존 / Secret은 어떤 yml에도 평문 안 됨 / Feature Flag는 application layer에서만 읽음
- Boundaries: dev/staging/prod 프로필 SSOT, secret은 코드에 진입 금지(envvar로만), feature flag는 도메인 layer에서 못 읽음(application에서만)
- Invariants: 모든 secret은 빈 값이면 startup 실패한다(silent 진행 금지). Feature flag는 결정 트리에 노출되지만 도메인 불변식을 깨지 못한다.

**수평성 점수**: 1✅ · 2🟡(단일 SSOT 가능) · 3✅

---

### 3-4. ⭐ Schema Migration & DB Evolution Topology — P1

**왜 1순위인가**
- 현재 `ddl-auto: create-drop` (dev) — prod 진입 즉시 **Flyway/Liquibase 도입 시점**, 이 결정 늦으면 모든 BC의 DDL 패턴 재작성
- NOT NULL 추가·컬럼 rename·인덱스 추가의 무중단 정책은 BC 단독 답 없음 — 운영 중 발생하면 전 시스템 일관 절차 필요
- `FK-Cascade-Policy`가 *논리적 관계*는 박았지만 *진화 절차*는 진화 섹션 짧게만 다룸

**기존 topology에서 이미 다룬 부분 (보강 출처)**
- `FK-Cascade-Policy-Topology` §진화 — *시점*은 명시, *절차 위상*은 없음

**박을 노드/엣지 예시**
- Nodes: 마이그레이션 도구 / Forward-only vs Reversible / Online Schema Change / Backfill Job
- Edges: 코드 배포 → 마이그레이션 선행, NOT NULL 추가 → nullable 추가 → backfill → NOT NULL 강제 (3단계)
- Boundaries: 마이그레이션은 코드와 별도 PR, prod ddl-auto는 절대 안 함, rollback 가능한 변경만 자동, 불가역 변경은 ADR
- Invariants: 모든 prod 스키마 변경은 마이그레이션 도구를 통한다. 코드 변경과 스키마 변경은 *한 PR에 묶지 않는다* (롤백 안정성).

**수평성 점수**: 1✅ · 2✅ · 3✅(prod 임박)

---

### 3-5. ⭐ External Integration Topology — P1 (Architecture-Style 확장)

**왜 1순위인가**
- 현재 `Architecture-Style-Topology`에 Port-Adapter 모양만 있음. PortOne 1건이라 안 부각됐지만, 곧 추가될 외부 시스템(SMS·이메일·CDN·결제 다양화) 각각이 **retry/timeout/circuit breaker/보상 정책을 자기 식으로 결정**하면 시스템 일관성 깨짐
- 보상 트랜잭션 위상은 `tx-topology`에 있지만 **외부 통합 일반의 위상**으로는 안 박힘

**기존 topology에서 이미 다룬 부분 (보강 출처)**
- `Architecture-Style-Topology` — Port-Adapter 모양만
- `Consistency-Design-Topology` — External-reconciled zone *원칙*만
- `tx-topology` — 외부 호출 트랜잭션 *위치*만

**박을 노드/엣지 예시**
- Nodes: 외부 시스템 (PG / 알림 / CDN / 인증 IdP) / Port / Adapter / Retry Policy / Circuit Breaker / Compensation
- Edges: 모든 외부 호출 = Port 통과 / Retry는 idempotent 호출만 / 보상은 비가역 호출 후 등록
- Boundaries: 외부 호출은 항상 트랜잭션 밖, Adapter는 InfrastructureException으로 번역, Retry/Timeout 정책은 외부별 분리되지만 BC가 가르지 않고 외부 시스템별로 박힘
- Invariants: 외부 응답을 우리 zone의 진실로 가정하지 않는다. 외부 호출 실패 시 silent 진행하지 않는다.

**수평성 점수**: 1✅ · 2✅ · 3🟡(외부 1개일 땐 낮음, 2개 넘으면 ↑)

---

## 4. 차순위 (6 ~ 10) — 박을 가치는 있으나 시점 늦춰도 됨

### 4-6. Domain Event Lifecycle Topology — P2
- 현재 `Consistency-Design`이 AFTER_COMMIT만 박음, 이벤트 명명·페이로드 진화·at-least-once 보장·DLQ·replay 정책 없음
- Membership(도전) / Refund 도입 시점에 박을 것

### 4-7. PR & Git Workflow Topology — P2
- placeholder 빈 파일 존재. 머지 전략(squash)·핫픽스·릴리스 cadence·브랜치 보호는 사용자 memory에 흩어져 있음
- 코드보다 운영의 graph지만 horizontal — 모든 PR이 같은 graph 통과

### 4-8. Testing Strategy Topology — P2
- 슬라이스/통합/e2e 비율, 외부 의존 모킹 정책(`MockOrderService` 삭제 사례), 픽스처 관리(`DummyDataInit`)가 ad-hoc로 늘어남
- 테스트 피라미드 graph 박음 가치 있음

### 4-9. Time & Scheduling Topology — P2 (구독 도입 전)
- 구독·만료·스케줄러 멱등성·타임존·clock skew 정책
- 현재 스케줄러 사용처가 거의 없어 시급도 ↓, 구독 직전에 박을 것

### 4-10. Performance Budget Topology — P3
- Latency 목표·페이지네이션 위상·N+1 가드(이미 JOIN FETCH 적용 중)
- 트래픽 관측치 쌓인 후

---

## 5. 회의 안건 (Desktop 합의용)

수평 관리 우선순위 결정을 위해 다음 4가지를 명시 결정 필요:

### 5-1. 박는 순서
권장: **Auth → Observability → Config → Schema Migration → External Integration**
이유: 보안·디버깅 가능성(앞 두 개)이 운영 진입 비용. Config/Migration은 prod 진입 직전에 박지 않으면 사고. External은 두 번째 외부 도입 직전에 박으면 됨.

### 5-2. 박음 시점 — Layer 분류
- Layer 1 후보: Auth, Observability (정책이 거의 안 변함)
- Layer 2 area 후보: Config, Schema Migration, External Integration (BC 추가 시 진화)

### 5-3. 박는 단위
- 한 번에 5개 박지 말 것. **한 Phase = 한 토폴로지** (references §0 Validity 원칙)
- 권장: 다음 Epic에서 Auth 1건만 박고, 그 다음에 Observability

### 5-4. Re-pin Trigger 사전 합의
각 topology의 §4 trigger를 미리 합의해야 박은 후 흔들리지 않음. 특히 Auth는 "역할 추가 / IdP 도입"이 trigger 후보.

---

## 6. 기존 topology 보강 메모 (참고)

위 5건과 별개로 기존 10건 중 가벼운 보강 권장:

- `API-Topology` — 인증과 인가의 명시적 분리 (3-1과 연결)
- `Error-Handling-Topology` — `BusinessException.detail` 사용 규약 (3-2와 연결, CT003 사태 반복 방지)
- `tx-topology` — REQUIRES_NEW 외 보상 트랜잭션 시나리오는 결제 중심. 다른 BC(구독·환불)에 일반화될 때 보강
- `pr-git-topology` — 빈 파일이므로 4-7 채우면서 해소

---

## 7. 변경 이력

| 날짜 | 변경 |
|---|---|
| 2026-06-05 | 초안. 수평 관리 우선순위 5건 도출 + 차순위 5건 + 회의 안건 |
