# [Archived] 0.0.2v — 크라우드 펀딩 SDD 4개

> **상태**: archived
> **아카이브 일자**: 2026-07-05
> **아카이브 이유**: 07-05 회의(회의록 `C:\Users\user\.claude\plans\splendid-finding-feather.md` 1~11차 세션)에서 크라우드 펀딩 컨셉을 폐기하고 **백오피스 중심 프레이밍**으로 전환. 4개 SDD는 새 프레이밍과 정합하지 않음.

---

## 원본 파일 목록 (각 ~55K)

| 파일 | Product | Epic 수 | SP | 상태 (아카이브 시점) |
|---|---|---|---|---|
| `product-wallet.md` | 지갑(Wallet) 예치금·SSOT·비관 락 | 4 | 12.5 | in-progress |
| `product-project.md` | Project 리네임·10-state FundingStatus·slug | 4 | ~15 | in-progress |
| `product-reward.md` | Reward Tier 재고·편집 잠금 | 4 | 11.5 | in-progress |
| `product-pledge.md` | Pledge 상태기계·All-or-Nothing·자동 종료 | 4 | ~14 | in-progress |

**총 16 Epic · ~53 SP · 4~5주 스코프** — 2~3주 시한 앞에서 완주 불가능하다고 판정.

## 재활용된 백엔드 조각 (0.0.3v 새 SDD로 이식)

| 원본 (0.0.2v) | 재활용 대상 (0.0.3v) | 조각 |
|---|---|---|
| product-wallet.md §Epic 1 | product-timesale-concurrency.md §Epic 2 | 비관 락 `findByIdForUpdate` · @Version 낙관 락 패턴 |
| product-wallet.md §Epic 4 | product-perf-lab.md §Epic 3 · product-admin-backoffice.md §Epic 2 | Micrometer 5개 지표 등록 방식 |
| product-wallet.md §Epic 2 Story 2-3 | product-timesale-concurrency.md §Epic 3 | REQUIRES_NEW Failure Isolation 패턴 |
| product-project.md §Epic 3 | product-search-cache.md §Epic 1 | QueryDSL 동적 검색 확장 |
| product-pledge.md §Epic 3 | (미이식) | All-or-Nothing 자동 종료 스케줄러 (Order 자동 취소 배치로 대체 가능하나 0.0.3v 스코프 밖) |

## 폐기 결정 근거 요약 (회의록 §5·§9 세션)

1. **2~3주 시한 앞에서 완주 확률 30% 이하** — Wallet + Project + Reward + Pledge 결합도 높아 부분 완성 시 "미완의 야심작" 인상
2. **third-tool 포트폴리오와 활동 축 겹침** — 크라우드 펀딩은 기획·데이터·유저 확보에 에너지 빨려 들어감
3. **캠프 요구사항과 정합성 낮음** — 타임세일 한정 수량·선착순 쿠폰·CS 채팅이 크라우드 펀딩 도메인 룰과 안 맞음
4. **백오피스 프레이밍(9차 세션 통찰)** — "쇼핑몰 자체"가 아니라 "그 뒤에서 운영이 어떻게 굴러가느냐"가 이야기의 무대

## 참조

- 신규 in-progress SDD 5개: `../` (product-timesale-concurrency · product-search-cache · product-cs-chat · product-admin-backoffice · product-perf-lab)
- 회의록: `C:\Users\user\.claude\plans\splendid-finding-feather.md`
- 크라우드 펀딩 이슈 아카이브: `../../../../fix/archive/0.0.2v-crowdfunding/`
- 크라우드 펀딩 milestone 아카이브: `../../../../milestones/version/0.0.3v/archive-crowdfunding-wallet/`
