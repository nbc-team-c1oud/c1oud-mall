# [FE Performance] 0.0.1v — 스켈레톤

> Web Vitals · Lighthouse · 번들 크기 · TanStack Query cache 지표 기록. FE 착수 시 실측.

---

## 목표 지표 (KPI)

| 지표 | 목표 | 측정 도구 |
|---|---|---|
| Lighthouse Performance | ≥ 90 | Lighthouse CI (PR 단계) |
| LCP (P95) | ≤ 2.5s | `web-vitals` 라이브러리 RUM |
| INP (P95) | ≤ 200ms | `web-vitals` |
| CLS | ≤ 0.1 | `web-vitals` |
| FID (P95) | ≤ 100ms | `web-vitals` (INP로 대체됨) |
| 초기 번들 크기 (gzipped) | ≤ 300KB | `rollup-plugin-visualizer` · Bundle Buddy |
| 이후 chunk 로드 (gzipped) | ≤ 100KB/chunk | 동상 |

---

## 실측 (FE 착수 시 채움)

### Lighthouse
| 페이지 | Perf | A11y | Best Practices | SEO | 날짜 |
|---|---|---|---|---|---|
| `/` | (미측정) | | | | |
| `/products` | | | | | |
| `/cart` | | | | | |
| `/orders/:id` | | | | | |

### Web Vitals RUM (프로덕션 사용자 실측)
| 지표 | P50 | P75 | P95 | 목표 대비 |
|---|---|---|---|---|
| LCP | | | | |
| INP | | | | |
| CLS | | | | |

### 번들 크기
| Chunk | 크기 (raw) | 크기 (gzipped) | 예산 대비 |
|---|---|---|---|
| main | | | |
| vendor (React + Router) | | | |
| vendor (TanStack Query) | | | |
| features/payment | | | |
| features/cart | | | |

---

## TanStack Query · 캐시 지표 (참고)

| 지표 | 값 |
|---|---|
| stale time 기본값 | (착수 시 결정) |
| gcTime (구 cacheTime) | 기본 5분 |
| refetch on window focus | (정책 결정) |

---

## 관측 · 회귀 방지

- [ ] `web-vitals` 라이브러리 초기화 · `console.log` 또는 Sentry로 전송
- [ ] Lighthouse CI GHA step (main 대상)
- [ ] `rollup-plugin-visualizer`로 번들 크기 시각화 · PR에 첨부
- [ ] 번들 예산 초과 시 CI 실패 (`vite-plugin-bundle-visualizer` 또는 `size-limit`)

---

## 다음 개선 후보

- CDN edge caching 활성 (CloudFront + Cache-Control 정책)
- Code splitting 최적화 (`React.lazy` · route-based)
- Preload 힌트 (`<link rel="preload">` · critical CSS)
- 이미지 최적화 (`.avif` · `.webp` fallback · lazy loading)

---

## 참조

- BE Milestone: `../../../../milestones/version/0.0.1v/milestone.md`
- FE Infra: [`infra.md`](./infra.md)
- FE-ADR-P1-009: Lighthouse CI 통합
