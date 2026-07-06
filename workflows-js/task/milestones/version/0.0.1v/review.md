# [Review] 0.0.1v — 첫 배포 버전 회고

> 첫 프로덕션 배포 버전(0.0.1v)의 성과 회고 및 M2(0.0.2v) 진입 결정.

---

## 이번 버전 요약 (한 줄)

> **결제·환불·주문·장바구니·인증·포인트·상품·공통 8개 도메인 골격을 128 파일·31 테스트로 완성하고, PortOne V2 실 연동 + ARM64 EC2 + RDS MySQL로 첫 프로덕션 배포에 성공했다.**

---

## Keep (계속 유지할 것)

- **DDD 4레이어를 8개 도메인 전체에 일관 적용** — 신규 도메인 진입 비용 낮음. 리뷰어의 예측 가능성 우수.
- **`ApiResponse<T>` + `BusinessException + ErrorCode` 통일 규범** — 예외 · 응답 포맷을 첫 배포부터 잡아둔 것이 큰 이점. GlobalExceptionHandler 하나로 33개 에러 코드 일관 처리.
- **도메인=JPA Entity 통합 방식(A) 유지** — 팀 규모 대비 오버헤드 최소, 스피드 확보 (memory 확정).
- **멱등성 첫 배포부터 설계 반영** — `portonePaymentId` UUID + DB UNIQUE, `WebhookEvent` INSERT-first (ADR 005 준수).
- **PortOne 실 연동 + 웹훅 HMAC 검증 + 재조회 원칙 준수** — 결제 정합성 규범을 코드로 강제.
- **ARM64 Graviton EC2 + DockerHub Public** — 스타트업 최저가 스택. 이후 ECR·ALB 전환 여지 남김.
- **Cart 통합 · Order-Payment 통합 후 즉시 sdd/done/으로 아카이브** — 완료된 통합을 문서로 굳혀둠.

---

## Problem (고칠 것)

### 관측성 부재 (최대 리스크)
- 배포는 성공했으나 실 트래픽 발생 시 "어디서 뭐가 튀는지"를 로그 grep만으로 추적해야 함.
- MDC `requestId` 없어 동시 요청 로그가 뒤섞임 → 사용자 문의 대응 시간 폭증 예상.
- ERROR 로그가 `warn` 계열과 섞여 있어 신호 무력화 위험.
- → **Log Product Epic 1~2를 M2 최우선 진입 대상으로 확정**.

### CORS 하드코딩 잔존
- prod SecurityConfig에 origins 하드코딩 → FE 도메인 추가 시 재배포 필요.
- Fix Issue 1으로 트래킹 중 · 아직 미처리.

### JPA `ddl-auto=update` 임시 결정
- 첫 부팅 · 컬럼 추가 케이스는 정상. 하지만 **컬럼 rename/삭제 케이스에서 실패 리스크**.
- Flyway 미도입 상태로 방치 시, 첫 스키마 변경 사고 발생 가능.
- → 트리거 조건 문서화만 완료, 실 도입은 M3+로 이월.

### Secrets 관리 원시적
- GitHub Secrets → EC2 환경변수 직접 주입 (8개 secret).
- 로테이션 시 매번 GHA · EC2 · 코드 갱신 필요.
- Secrets Manager 미도입 → M3+ 검토.

### Mock 잔존 (Payment BC)
- `MockCartService`, `MockPointService`, `MockInventoryService` 3개 잔존.
- 결제 확정 zone 진척률 2/6 → 4/6 도달 위해 M2에서 정리 필요.

### Fix F1 자기주입 함정
- `PaymentCompensationService` 프록시 우회 잠재 리스크 (fix/payment.md Issue 2).
- 아직 실 사고 미발생이지만 통합 테스트로 방어 필요.

### 테스트 커버리지 · 형식 편차
- 31개는 도메인당 평균 4건 수준 · Payment 중심(집중). Refund·Point는 상대적으로 얇음.
- Testcontainers 미도입 → H2로 통합 테스트 (MySQL 프로덕션과 dialect 차이 잠재).

---

## Try (다음 버전에서 시도할 것)

- **Log Product Epic 1~2 완결** — JSON 로그 + MDC 도입 후 dev/prod 로그 스크린샷 비교.
- **`REVIEW_PG_CANCEL_FAILED` 등 커스텀 로그 마커 카탈로그화** — Log Product Epic 3에 연결.
- **Testcontainers MySQL 도입 검토** — 통합 테스트를 실 dialect로 실행.
- **PR 템플릿에 "로그 레벨 결정" 체크박스 추가** — Log Epic 4 결과 반영.
- **Actuator `/health` 도입** — 헬스체크 정확도. Prometheus는 지연.
- **자기주입 함정 통합 테스트 1건** — Fix F1 방어망.

---

## 이월 & 결정

### 이월된 Story
- Log Product Epic 1~4 (5 Story · 13 SP)
- AI Suggestion Epic 7~11 (14 Story) — Gemini API 미확보 시 계속 이월
- Fix F1 (Payment self-injection)
- Fix — Cart 삭제 위치 이관
- Fix — CORS 프로파일 분기

### 결정한 사항 (0.0.1v 확정)
- **ADR 001~011 발행 완료** (`workflows/living-docs/Ai-adr/`)
  - 001 `portone-payment-id-uuid`
  - 002 `payment-confirmation-side-effects-order`
  - 003 `portone-compensation-transaction-pattern`
  - 004 `payment-confirmation-validation-order`
  - 005 `webhook-idempotency-insert-first`
  - 006 `bc-collaboration-direct-call`
  - 007 `refund-context-separation`
  - 008 `refund-unit-orderitem-quantity`
  - 009 `refund-amount-split-floor-policy`
  - 010 `refund-transaction-pattern`
  - 011 `portone-cancel-port-extension`
- **BC 협력 = 직접 호출** 유지 (ADR 006)
- **도메인 = JPA Entity 통합 방식(A)** 유지 (memory 반영)
- **환불 예외 = BusinessException + `RF001~003`** (별도 도메인 예외 클래스 금지 · feedback D1)

### 다음 마일스톤 진입 결정 (M2)
- **버전 라벨**: `0.0.2v`
- **기간**: 2주 (M2 착수일 기준)
- **주요 목표**:
  1. **Log Product 완결** (Epic 1: JSON 포맷 · Epic 2: MDC · Epic 3: 에러 로깅 표준 · Epic 4: 운영 가이드)
  2. **Fix F1 소화** (`PaymentCompensationService` Bean 분리 · `REQUIRES_NEW` 명시 · 통합 테스트)
  3. **Mock 정리** — `MockCartService` 제거 (OrderFacade로 이관) · `MockPointService`·`MockInventoryService`는 M3
  4. **CORS 프로파일 분기** (Fix Infra Issue 1)
  5. **Order 조회 시 실 `PaymentQueryService` 연결** (`oMockpaymentId=0L` 제거)

---

## 릴리즈 마일스톤 승격 검토

이번 버전(0.0.1v)이 릴리즈 마일스톤(0.1.0v)으로 승격 가능한 시점인가?

- [x] 사용자에게 노출되는 완결된 가치 있음 (결제 e2e · 환불 · 장바구니 · 주문)
- [x] Git tag 부착 가치 있음 (롤백 지점)
- [x] 프로덕션 배포 검증 완료
- [ ] **관측성 부재로 실 트래픽 대응 위험** — Log Product 완결 후 승격 권장

**현재 판단**: **아직 아님**. 0.0.2v에서 Log Product 완결 후, 0.0.3v를 관측 안정화 사이클로 굴린 뒤 → `0.1.0v` 릴리즈 태그 부착.

---

## 데이터 · 지표 스냅샷

| 지표 | 값 | 목표 | 상태 |
|---|---|---|---|
| Story 머지율 (Payment · Refund) | 100% | ≥ 80% | ✅ |
| 도메인 파일 수 | 128 | — | ✅ (8개 도메인) |
| 테스트 파일 수 | 31 | ≥ 20 | ✅ |
| ErrorCode 등록 개수 | 33 | 도메인당 3+ | ✅ |
| ADR 발행 건수 | 11 | ≥ 5 | ✅ (역대급) |
| 실 프로덕션 배포 성공 | 1건 | 1건 | ✅ |
| 이월 Story 수 (Log + AI) | 19 | 최소화 | ⚠️ (의도적 이월) |

---

## 인상적인 성과 (Highlight)

1. **PortOne 실 연동 + 웹훅 멱등을 첫 배포에 포함** — 결제 e2e가 mock이 아닌 실 데이터로 성립.
2. **ADR 11건 발행** — 결제·환불 도메인의 정합성·멱등성·트랜잭션 경계 규범을 문서로 굳혀둠.
3. **8개 도메인 · 128 파일을 DDD 4레이어로 일관 유지** — 대부분의 스타트업이 초기에 흐트러지는 지점을 처음부터 강제.
4. **Docker ARM64 (Graviton) 채택** — 배포 비용 최적화를 첫 릴리즈부터 고려.
5. **Cart · Order-Payment 통합 리포트를 sdd/done/으로 아카이브** — 완료된 통합을 즉시 문서로 굳혀 미래의 재조사 비용 절감.

---

## 참조

- Milestone: [`milestone.md`](./milestone.md)
- Outcome: [`outcome.md`](./outcome.md)
- Infra: [`infra.md`](./infra.md)
- Cost: [`cost.md`](./cost.md)
- 완료된 통합 SDD: `../../pes/workspectrum/sdd/done/`
- 다음 버전 스냅샷: `../0.0.2v/milestone.md` (생성 예정)
- ADR 인덱스: `workflows/living-docs/Ai-adr/README.md`
