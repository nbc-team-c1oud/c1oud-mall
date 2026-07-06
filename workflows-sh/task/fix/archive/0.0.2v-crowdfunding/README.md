# [Archived] 0.0.2v — 크라우드 펀딩 컨셉 이슈들

> **상태**: archived
> **아카이브 일자**: 2026-07-05
> **아카이브 이유**: 백오피스 중심 프레이밍(0.0.3v)으로 전환되면서 크라우드 펀딩 컨셉이 폐기됨.
> **상세 배경**: `C:\Users\user\.claude\plans\splendid-finding-feather.md` 회의록 (1~10차 세션)

---

## 원본 스코프

- GitHub 기반 대학생 프로젝트 크라우드 펀딩 (Kickstarter/텀블벅 스타일)
- 15개 이슈: Maker↔Backer · Project 리네임 · Reward Tier · Pledge 상태기계 · All-or-Nothing 배치 · GitHub 연동 · Discovery RSS+Gemini

## 폐기 결정 요약

1. **third-tool 포트폴리오와 활동 축 겹침** — 크라우드 펀딩은 기획·데이터·유저 확보에 에너지가 빨려 들어감. 순수 백엔드 훈련장 포지션이 얇아짐.
2. **2~3주 시한 앞에서 완주 확률 낮음** — Pledge 배치·상태기계·Wallet·Reward 결합도 높아 부분 완성 시 "미완의 야심작" 인상.
3. **캠프 요구사항과 정합성 낮음** — 타임세일 한정 수량·선착순 쿠폰·CS 채팅이 크라우드 펀딩 도메인 룰과 안 맞음.

## 백오피스 프레이밍(0.0.3v)에 재활용된 자산

| 원본 (0.0.2v) | 재활용 대상 (0.0.3v) | 재활용 요지 |
|---|---|---|
| issue-01 WebSocket 채팅 | issue-09 CS 채팅 STOMP + 상태기계 | STOMP + JWT ChannelInterceptor 골격 재사용 |
| issue-04 좋아요 반정규화·비관 락 | issue-07 락 3전략 비교 | 비관 락 시나리오 참고 |
| issue-06 QueryDSL 검색 | issue-03 검색 v1 QueryDSL LIKE 커서 페이징 | 동적 쿼리 · Projection 재사용 |
| issue-15 부하테스트 | issue-11 k6 부하 리포트 | 부하 축 · 관측 스택 참고 |

## 원본 파일 목록

- issue-01-websocket-chat.md
- issue-02-category-hierarchy.md
- issue-03-follow.md
- issue-04-product-like.md
- issue-05-review.md
- issue-06-search.md
- issue-07-wallet-prepaid-balance.md
- issue-08-project-domain.md
- issue-09-reward-tier.md
- issue-10-pledge-state-machine.md
- issue-11-maker-profile-response-stats.md
- issue-12-chat-rich-message.md
- issue-13-media-upload-gallery.md
- issue-14-github-integration.md
- issue-15-discovery-signal-collector.md

## 참조

- 회의록: `C:\Users\user\.claude\plans\splendid-finding-feather.md`
- 새 브레인스토밍: `../../brainstorming/version/0.0.3v/`
- 새 milestone: `../../../milestones/version/0.0.3v/milestone.md`
