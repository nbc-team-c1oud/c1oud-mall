# [Cost] 0.0.1v — 첫 배포 버전 비용 프로파일

> 첫 배포 버전에서 실 발생한 비용 카테고리와 초기 프로파일 (실측 수치는 팀 결산 후 갱신).

---

## 비용 프로파일 요약 (한 줄)

> **ARM64 EC2(Graviton) + RDS MySQL + DockerHub(무료 tier) + PortOne(결제 수수료 별개) 조합으로 스타트업 최저가 스택.** Gemini · Secrets Manager 미도입.

---

## AWS 비용

| 항목 | 스펙 | 예상 월비용 | 실제 | 비고 |
|---|---|---|---|---|
| **EC2 (ARM64 Graviton)** | `t4g.small` 추정 | ~$14/월 (720h × $0.02) | (기록 필요) | ARM64 이미지 대응 · x86 대비 ~20% 절감 |
| **RDS (MySQL)** | `t3.micro` 추정 · Single-AZ | ~$16/월 (720h × $0.022) | (기록 필요) | Multi-AZ 미도입 |
| **RDS 스토리지** | 20GB gp3 | ~$2.5/월 | (기록 필요) | 초기 저사용 |
| **네트워크 (아웃바운드)** | 100MB 미만 예상 | ~$0 (1GB Free) | (기록 필요) | 트래픽 낮음 |
| **EBS (EC2 루트)** | 8~30GB gp3 | ~$1/월 | (기록 필요) | |
| **ALB** | 미도입 | $0 | $0 | 단일 EC2 직결 (M3+ 시 도입) |
| **NAT Gateway** | 미도입 | $0 | $0 | Public subnet EC2 직결 |
| **Secrets Manager** | 미도입 | $0 | $0 | 환경변수 직접 (`GHA Secrets`) |
| **ECR** | 미도입 | $0 | $0 | DockerHub 사용 |
| **CloudWatch** | 기본 로그만 | $0~$5 | (기록 필요) | Custom metrics 미도입 |
| **Route53** | (미명시) | ~$0.5/월/도메인 | (기록 필요) | |
| **ACM** | 무료 | $0 | $0 | AWS 인증서 |
| **AWS 소계 (예상)** | | **~$35~$40/월** | **(기록 필요)** | |

---

## DockerHub

| 항목 | 스펙 | 비용 |
|---|---|---|
| DockerHub 저장소 | Public (또는 Free tier) | **$0** |
| 이미지 태그 개수 | 1 (`latest`) 또는 커밋 SHA | 무제한 pull (Public 시) |

**특이점**: ECR 대신 DockerHub 사용 → 저비용 유지. 이미지 private 필요 시 ECR 전환 검토.

---

## 외부 API 비용

| 항목 | 사용량 | 비용 | 비고 |
|---|---|---|---|
| **PortOne (수수료 별개)** | 결제 건당 | (PG 수수료 별도 정산) | 우리 서버 → PortOne V2 API 호출 자체는 무료 · 실 결제 수수료는 PG 정산 |
| **Gemini 2.5 Flash** | 0 요청 | **$0** | AI Suggestion Product 미착수 |
| **JWT (자체 구현)** | in-process | $0 | jjwt 0.11.5 라이브러리 (무료) |
| **외부 API 소계** | | **$0** | |

---

## Docker Registry / CI

| 항목 | 사용량 | 비용 |
|---|---|---|
| **GitHub Actions** | 저사용 (main push 트리거) | **$0** (Free tier 2000분/월) |
| **DockerHub 이미지 push** | 커밋당 1회 | **$0** (Public 저장소) |

---

## 인력 · 시간 비용 (참고)

| 항목 | 소요 |
|---|---|
| Payment Product Epic 1~3 구현 | (팀 시간 결산) |
| Refund Product Epic 1~2 구현 | |
| Order-Payment 통합 (PR #30) | |
| 초기 인프라 세팅 (Docker · GHA · EC2 · RDS) | |
| 더미 데이터 확장 | |
| 테스트 작성 (31개) | |

---

## 총계 및 이상 신호

| 카테고리 | 예상 월비용 | 실제 |
|---|---|---|
| AWS | ~$35~$40 | (기록 필요) |
| 외부 API | $0 | $0 |
| DockerHub / GHA | $0 | $0 |
| **주간 · 월간 총계** | **~$40 미만** | **(기록 필요)** |

### 이상 신호 관찰 (0.0.1v 기간)
- [ ] NAT Gateway 트래픽 초과? — **N/A** (NAT 미도입)
- [ ] RDS 스토리지 자동 확장 발생? — 관측 필요
- [ ] Gemini API 요청 급증? — **N/A** (미연동)
- [ ] DockerHub pull rate limit? — **관측 필요** (익명 pull 시 리스크)

---

## 비용 최적화 결정 · 결과

| 결정 | 결과 | 비고 |
|---|---|---|
| **ARM64 (Graviton) EC2 채택** | ✅ x86 대비 ~20% 절감 | Docker 이미지 ARM64 빌드 필요 (Buildx) |
| **DockerHub Public 사용 (ECR 미도입)** | ✅ 저장소 비용 $0 | 이미지 노출 리스크 관리 필요 |
| **Multi-AZ RDS 미도입** | ✅ RDS 비용 절반 | 단일 AZ 리스크 감수 |
| **ALB · NAT Gateway 미도입** | ✅ 월 ~$30+ 절감 | 단일 EC2 직결 (스케일 시 필요) |
| **Secrets Manager 미도입** | ✅ 월 ~$0.4/secret 절감 | 환경변수 로테이션 부담 |
| **Actuator/Prometheus 미도입** | ✅ CloudWatch metrics $0 | 관측 시야 저하 |

---

## 다음 마일스톤에서 추가될 비용 (M2+)

| 항목 | 예상 추가 비용/월 | 트리거 |
|---|---|---|
| CloudWatch Logs (로그 수집) | ~$5~$10 | Log Product 도입 시 |
| Gemini 2.5 Flash API | ~$5~$20 (사용량 의존) | AI Suggestion 도입 시 |
| ALB | ~$16 | 다중 인스턴스 or 도메인 확장 시 |
| Secrets Manager (5개 secret) | ~$2 | 로테이션 요건 발생 시 |
| Multi-AZ RDS | 현 비용 × 2 | 프로덕션 SLA 요건 발생 시 |

---

## 참조

- Milestone: [`milestone.md`](./milestone.md)
- Infra 상세: [`infra.md`](./infra.md)
- Outcome: [`outcome.md`](./outcome.md)
- 배포 체크리스트: `workflows/living-docs/production-deployment-checklist.md`
