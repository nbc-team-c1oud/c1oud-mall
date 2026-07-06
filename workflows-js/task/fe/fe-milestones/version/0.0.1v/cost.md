# [FE Cost] 0.0.1v — 스켈레톤

> FE 관련 실 발생 비용. BE와 공유 인프라(CloudFront · Route53 · ACM)는 BE cost.md에도 기록.

---

## AWS FE 비용

| 항목 | 스펙 | 예상 월비용 | 실제 | 비고 |
|---|---|---|---|---|
| S3 (정적 자산) | ~1GB 저장 | ~$0.023/월 | (기록 필요) | Standard 등급 |
| CloudFront (배포) | ~100GB 트래픽 | ~$8.5/월 | (기록 필요) | 1TB Free tier 이내면 $0 |
| Route53 (도메인) | 1 도메인 | ~$0.5/월 | (기록 필요) | BE와 공유 |
| ACM (인증서) | 무료 | $0 | $0 | AWS 무료 |
| **AWS 소계** | | **~$9/월** | (기록 필요) | BE 인프라 별개 |

---

## 도구 · 라이선스

| 항목 | 사용량 | 비용 |
|---|---|---|
| **Figma** | 팀 워크스페이스 | (팀 결정) |
| **Sentry** | Free tier (5k events/월) | **$0** (초기) |
| **Lighthouse CI** | GHA action (무료) | **$0** |
| **PortOne 클라이언트 SDK** | 무료 | $0 |
| **npm 패키지** | 오픈소스 | $0 |
| **GitHub Actions** | Free tier 2000분/월 | $0 |
| **도구 소계** | | **~$0** (초기 · Sentry 확장 시 증가) |

---

## 인력·시간 비용 (참고)

| 항목 | 소요 |
|---|---|
| FE 스켈레톤 셋업 (Vite · React · Router · TanStack Query · Zod) | (착수 시) |
| BE 26개 엔드포인트 통합 | (착수 시) |
| PortOne SDK 통합 · 시나리오 검증 | (착수 시) |
| E2E 테스트 작성 | (착수 시) |
| Web Vitals · Lighthouse 목표 달성 | (착수 시) |

---

## 총계 · 이상 신호

| 카테고리 | 예상 월비용 | 실제 |
|---|---|---|
| AWS FE | ~$9 | (기록 필요) |
| 도구 | ~$0 | $0 |
| **주간 · 월간 총계** | **~$9 미만** | **(기록 필요)** |

### 이상 신호 관찰
- [ ] CloudFront 트래픽이 1TB Free tier 초과? — 초기엔 저사용
- [ ] Sentry event 5k Free tier 초과? — 도입 후 관측
- [ ] S3 요청 수 급증? (invalidation 잘못 관리 시)

---

## 비용 최적화 결정 · 결과

| 결정 | 결과 | 비고 |
|---|---|---|
| CloudFront + S3 정적 배포 (Vercel/Netlify 대신) | ✅ AWS 통합 · BE와 도메인 공유 | Cache-Control 수동 관리 |
| Sentry Free tier | ✅ 초기 $0 | 5k event/월 초과 시 확장 |
| Figma 팀 워크스페이스 | (결정 필요) | 개인 계정으로 시작 · 팀 확장 시 이관 |

---

## 다음 마일스톤에서 추가될 비용 (M2+)

| 항목 | 예상 추가 비용/월 | 트리거 |
|---|---|---|
| Sentry Team plan | ~$29 | 5k event/월 초과 시 |
| Vercel/Netlify (SSR 필요 시) | ~$20 | Next.js 전환 시 |
| Figma 팀 유료 | ~$15/user/월 | 팀 확장 시 |
| Percy · Chromatic (Visual regression) | ~$149 | UX 회귀 자동 감지 필요 시 |

---

## 참조

- BE Cost: `../../../../milestones/version/0.0.1v/cost.md`
- FE Infra: [`infra.md`](./infra.md)
