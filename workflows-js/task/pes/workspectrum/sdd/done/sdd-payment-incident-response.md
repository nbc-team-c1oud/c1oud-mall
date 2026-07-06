# [Product · Done] 결제 도메인 장애 대응 Runbook

## Product Vision
> Payment BC 내부에서 발생 가능한 장애·이상 시나리오를 P1·P2·P3 등급으로 분류하고, 각 시나리오별 증상 · 로그 확인 · 수동 개입 절차를 문서화하여 운영자·개발자가 신속하게 대응할 수 있게 한다.

## 배경 및 문제
- 현재 상황 (As-Is)
  - Payment 결제 확정 흐름에서 발생 가능한 실패 케이스가 코드에만 존재
  - PortOne 취소 실패 · TX 내 side-effect 실패 등의 대응 절차 부재
  - 각 에러 코드(PM001~PM009 등)가 어떤 조치를 요구하는지 팀 내 암묵지
- 발생하는 문제
  - 실장애 발생 시 대응이 코드 grep · 로그 파싱에 의존 → 시간 소요
  - "재시도 안전"·"수동 개입 필요"의 판정이 문서화 안 됨
- 왜 지금 해결해야 했는가
  - Payment Product의 결제 e2e 흐름 안정화 이후 운영 대응 문서화 필수
  - 채용 포트폴리오 · 면접에서 "운영 관점"을 보여주는 자료

## 목표 (To-Be)
- P1(즉각 수동 개입) · P2(재시도·코드 대응 가능) · P3(사후 추적) 3단계 등급 분류
- 각 시나리오별 4개 섹션 표준: 언제 발생하나 · 증상 · 대응 절차 · 재발 방지 TODO
- 로그 · SQL 예시 포함 (즉시 실행 가능)
- ErrorCode 매핑 (`PM001~PM009` 등)

## 설계 결정 (Design Decisions)

- **P1/P2/P3 3단계** — SRE 관례
- **자동 처리는 코드에 · 수동 절차는 이 문서에** — 이중 관리 회피
- **PortOne 콘솔 접근 절차 포함** — 수동 취소 API 호출 예시(cURL) 포함
- **재발 방지 TODO** — 각 시나리오마다 "미구현 상태로 인수인계 필요" 명시

## 대안 검토 (Alternatives Considered)

### 문서 형식
**Option A (선택) — 시나리오별 표준 4섹션**
- 비용: 중복성 있음
- 보상: 검색성 우수 · 신입 온보딩 쉬움

**Option B — 원인·결과 매트릭스**
- 거부 이유: 신속 대응에 부적합 (분석용)

### 자동 재시도 도입
**Option A (선택) — 1차는 미도입 · 로그 + 수동 개입만**
- 비용: 운영자 부담
- 보상: 안전 (자동 재시도가 이중 결제 유발 리스크)

**Option B — 배치 재시도 큐**
- 거부 이유: 초기 규모 오버킬

## 전체 아키텍처 (High-Level Architecture)

### 시나리오 분포
```
🔴 P1 (즉각 수동 개입)
  ├── P1-1: PortOne 보상 취소 실패 (DB는 FAILED, PortOne은 PAID)
  └── P1-2: 결제 확정 TX 내 side-effect 실패

🟠 P2 (재시도·코드 대응)
  ├── P2-1: PortOne 조회 실패/타임아웃 (PM004)
  ├── P2-2: 결제 금액 위변조 시도 (PM001) — 자동 보상
  ├── P2-3: 중복 결제 확정 요청 (멱등성) — 자동 처리
  └── P2-4: 웹훅 수신 재시도 누적

🟡 P3 (사후 추적)
  └── (필요 시 추가)
```

## 실패 모드 / 운영 관측 (Failure Modes & Observability)

### 대표 시나리오 요약 (원본에 상세)

| 등급 | 시나리오 | ErrorCode | 자동 처리 | 수동 개입 |
| --- | --- | --- | --- | --- |
| P1 | PortOne 보상 취소 실패 | `PM009` | 로그만 | 즉시 PortOne 콘솔·SQL 확인 후 수동 취소 (cURL) |
| P1 | TX 내 side-effect 실패 | - | 전체 TX 롤백 | 로그에서 실패 step 파악 · idempotency 재시도 |
| P2 | PortOne 조회 실패 | `PM004` | 400 반환 · DB 미변경 | 클라이언트 재시도 안내 (안전) |
| P2 | 금액 위변조 | `PM001` | 자동 보상 취소 트리거 | 반복 발생 시 계정 정지 검토 |
| P2 | 중복 요청 | - | `isCompleted()` silent OK | 자동 (별도 조치 없음) |

### 수동 취소 절차 (P1-1)
```bash
curl -X DELETE "https://api.portone.io/v2/payments/{portonePaymentId}/cancel" \
  -H "Authorization: Bearer {SECRET_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"reason": "결제 검증 실패 — 수동 보상 취소"}'
```

### 조회 SQL (P1-1)
```sql
SELECT id, portone_payment_id, portone_tx_id, amount, created_at
FROM payment
WHERE status = 'FAILED'
  AND portone_tx_id IS NOT NULL
  AND created_at > NOW() - INTERVAL 24 HOUR
ORDER BY created_at DESC;
```

## 롤아웃 / 마이그레이션 (Rollout)

### 전제
- Payment 결제 확정 코드 안정화 이후 문서화
- 관측 지표(로그 기반)로 실측 가능

### 재발 방지 TODO (인수인계 필요)
- `PM009` 발생 시 별도 보상 큐/재시도 배치 (현재 미구현)
- Side-effect 별도 TX(`REQUIRES_NEW`) 또는 도메인 이벤트 + outbox 검토
- 반복 위변조 자동 감지·계정 정지 (현재 수동)

## 성공 지표 (KPI)
| 지표 | 목표 | 결과 |
| --- | --- | --- |
| P1 시나리오 발견 → 대응까지 시간 | ≤ 30분 | 문서 참조 시 즉시 절차 시작 가능 |
| 자동 처리 시나리오 커버리지 | P2 대부분 | ✅ PM001·PM004·멱등 자동 |
| 수동 조치 SQL·cURL 예시 즉시 실행 | 100% | ✅ 카피&페이스트 가능 |

## Scope
**In Scope**:
- Payment BC 내부 시나리오 (payment 팀이 직접 조치 가능한 것)
- PortOne 연동 관련 (조회 실패 · 취소 실패 · 웹훅 재시도)
- 로그 · SQL · cURL 예시

**Out of Scope**:
- 인프라 장애 (RDS · ECS 등) — 별도 인프라 Runbook
- 관측 대시보드 · 알림 룰 — 별도 관측성 Product
- 자동 재시도 · Outbox — 재발 방지 TODO로 이월

## 대상 사용자
- **운영자**: 장애 발생 시 신속 대응
- **개발자**: 온콜 준비 · 재발 방지 개선 우선순위
- **면접 자료**: "운영 관점" 증거

## 연결된 Epic 목록 (완료)
- [x] Epic 1: P1 시나리오 (보상 실패 · TX side-effect 실패)
- [x] Epic 2: P2 시나리오 (PG 조회 실패 · 금액 위변조 · 중복 · 웹훅 재시도)
- [x] Epic 3: SQL·cURL 예시 · 로그 마커
- [x] Epic 4: 재발 방지 TODO 명시

## 관련 문서
- 원본 문서: `workflows/payment-incident-scenarios.md` (2026-06-02)
- 짝 문서: [`sdd-payment-integration-report.md`](./sdd-payment-integration-report.md), [`sdd-order-payment-integration.md`](./sdd-order-payment-integration.md)
- Fix 이슈: `workflows/task/fix/brainstorming/version/0.0.1v/payment.md`
- Product SDD: `workflows/products/product-payment.md` §8 (실패 모드)
- `.claude/rules/consitency.md` §7 (외부 시스템 정합성) · `.claude/rules/idempotency.md` §7 (외부 멱등 의존)

## 열린 질문 (Open Questions)
- 자동 재시도 배치 도입 트리거 (PM009 발생 빈도)
- Side-effect TX 분리 방식 결정 (`REQUIRES_NEW` vs outbox)
- 반복 위변조 자동 감지 임계값 (fail_count ≥ 3 in 1h → 계정 정지)
- 관측성 도구(Grafana·CloudWatch)와의 알림 연동 시점

## 제품 수준 완료 기준 (Product-level DoD)
- [x] P1·P2 시나리오 4개 이상 문서화
- [x] 각 시나리오에 SQL/cURL/로그 마커 포함
- [x] 자동 처리와 수동 개입 명확히 구분
- [x] 재발 방지 TODO 명시
