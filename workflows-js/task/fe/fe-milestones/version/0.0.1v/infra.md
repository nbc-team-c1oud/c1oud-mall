# [FE Infra] 0.0.1v — 스켈레톤

> FE 배포 인프라 · 환경 설정 · 도구 스택. FE 착수 시 실측 채움.

---

## 배포 상태 (계획)

| 환경 | 상태 | 배포 방식 (예정) |
|---|---|---|
| local/dev | (착수 시) | Vite dev server (`npm run dev` · 5173) |
| prod | (착수 시) | S3 정적 자산 + CloudFront (계획) |

---

## FE 스택 (계획 · FE-ADR로 확정)

| 계층 | 후보 | 결정 상태 |
|---|---|---|
| 번들러 · dev 서버 | Vite | (권장 · FE-ADR 없음) |
| 프레임워크 | React 18+ | (기본 가정) |
| 라우팅 | React Router v6 | FE-ADR-P0-003 (candidate) |
| 서버 상태 | TanStack Query v5 | FE-ADR-P0-002 (candidate) |
| 스키마 · 검증 | Zod | FE-ADR-P0-004 (decided) |
| 폼 | react-hook-form + Zod resolver | FE-ADR-P1-001 (candidate) |
| 스타일 · 컴포넌트 | Tailwind CSS + shadcn/ui | FE-ADR-P1-002 (candidate) |
| 결제 SDK | `@portone/browser-sdk/v2` | FE-ADR-P0-005 (candidate) |
| 관측 | Sentry (예정) | FE-ADR-P1-010 (candidate) |
| 테스트 · 단위 | Vitest | (기본 가정) |
| 테스트 · E2E | Playwright | (권장) |
| MSW | msw v2 (개발 mock) | (선택) |

---

## 배포 파이프라인 (계획)

```
push (main)
   ↓
1. GHA `deploy-fe.yml` 트리거
2. Node.js 20 setup
3. `npm ci`
4. `npm run build`  (Vite production build)
5. AWS S3 sync (OIDC 인증)
6. CloudFront invalidation
   ↓
prod 배포 완료
```

---

## 환경변수 (계획)

| 변수 | 용도 | dev 값 | prod 값 |
|---|---|---|---|
| `VITE_API_BASE_URL` | BE API 엔드포인트 | `http://localhost:8080` | `https://api.c1oud-mall.dev` (예정) |
| `VITE_PORTONE_STORE_ID` | PortOne 스토어 ID | (테스트) | (운영) |
| `VITE_PORTONE_CHANNEL_KEY` | PortOne 채널 키 | (테스트) | (운영) |
| `VITE_SENTRY_DSN` | Sentry DSN | (dev DSN) | (prod DSN) |
| `VITE_DEBUG_QUERY` | TanStack Query Devtools | `true` | `false` |

**보관**:
- `.env.development` (git 포함 · 안전한 값만)
- `.env.production` (git 포함 · 안전한 값만)
- `.env.local` (gitignore · 개인 로컬 override)

---

## Cache-Control 정책 (계획)

| 자산 | Cache-Control |
|---|---|
| `index.html` | `no-cache, must-revalidate` |
| `assets/*.js`, `assets/*.css` (hashed) | `max-age=31536000, immutable` |
| 이미지 · 폰트 (hashed) | `max-age=31536000, immutable` |

---

## CORS · SameSite · Cookie

| 항목 | dev | prod |
|---|---|---|
| CORS credentials | `include` (필요 시) | `include` |
| SameSite | Lax | None (Secure 필요) |
| HttpOnly Cookie | 미사용 (accessToken은 localStorage) | 동일 |

**BE 연관**: BE prod의 CORS origins 하드코딩 이슈 (`workflows/task/fix/brainstorming/version/0.0.1v/infra.md` Issue 1) — 해소 필요

---

## Web Vitals · Lighthouse 목표

| 지표 | 목표 |
|---|---|
| Lighthouse Performance | ≥ 90 |
| LCP (P95) | ≤ 2.5s |
| INP (P95) | ≤ 200ms |
| CLS | ≤ 0.1 |
| 초기 번들 (gzipped) | ≤ 300KB |

---

## 완료 산출물 (FE 착수 시 채움)

### CI/CD
- [ ] `deploy-fe.yml` GHA 파이프라인 정착
- [ ] Lighthouse CI 통합 (PR 단계)
- [ ] S3 · CloudFront invalidation 자동화

### Runtime
- [ ] Vite 빌드 결과 검증
- [ ] React Router · TanStack Query 등 라이브러리 확정

### 외부 연동
- [ ] BE API `/api/v1/*` 연동
- [ ] PortOne SDK 통합
- [ ] Sentry 초기화 · requestId breadcrumb (BE Log Product 완결 후)

---

## 다음 인프라 우선순위 (FE M2)

1. **`deploy-fe.yml`** 파이프라인 정착
2. **Sentry** + BE `X-Request-Id` breadcrumb 통합
3. **Lighthouse CI** GHA step
4. **CloudFront invalidation** 자동화

---

## 참조

- BE Infra: `../../../../milestones/version/0.0.1v/infra.md`
- FE SDD: `../../../fe-workspectrum/sdd/sdd.md`
- FE-ADR: `../../../fe-workspectrum/FE-ADR-CANDIDATES.md`
- backend-boundary: `../../../../../backend-boundary/error-codes.md`
