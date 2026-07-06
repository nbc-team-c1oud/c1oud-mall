# [0.0.3v · Issue 10] 캐시 Remote 전환 (Caffeine → Redis) + Cache Eviction

> **역할**: 캠프 요구사항의 도전 기능 — v2 Local Cache를 Redis Remote로 전환하고 원본 데이터 변경 시 캐시 동기화 처리.
> **Week**: 3 · **Day**: 15~16
> **상태**: pending
> **Tier**: `feature-story`
> **캠프 요구사항 매핑**: 도전 — Redis Remote Cache (Scale-out) + Cache Eviction (@CacheEvict · @CachePut)

---

## 배경

캠프 요구사항 명시:
- v2 Local Cache의 한계 (Scale-out 시 서버 간 데이터 공유 불가)
- Redis Cache로 전환
- `RedisTemplate` Serializer (StringRedisSerializer + GenericJackson2JsonRedisSerializer)
- `LocalDate/LocalDateTime` 포함 시 JavaTimeModule 등록
- 원본 데이터 변경 시 캐시 동기화 문제 → `@CacheEvict` · `@CachePut`
- TTL 설계 · Eviction Policy (LRU/LFU/FIFO) · 즉시 무효화 vs 자연 만료 trade-off

## 왜 Redis로 전환하는가

- 서버 여러 대(EC2 다중 인스턴스) 시 Caffeine은 서버 간 캐시 데이터 공유 불가 → 사용자마다 다른 서버 히트 시 stale 결과
- Redis는 중앙 저장소로 모든 인스턴스가 공유
- Pub/Sub 확장성 (향후 채팅 성능 개선용)

## 핵심 스코프

### Day 15 — Redis Remote 전환
- `RedisCacheManager` 설정 (Spring Boot Data Redis)
- `RedisTemplate<String, Object>` Bean
  - Key: `StringRedisSerializer`
  - Value: `GenericJackson2JsonRedisSerializer` + `ObjectMapper.registerModule(new JavaTimeModule())`
- v2 API가 이제 Redis 사용 (`@Cacheable(cacheManager = "redisCacheManager")`)
- Local Caffeine (v2 초기 구현) 은 병존 유지 or 제거 (초기 유지 · 선택 설정으로 스위칭)

### Day 16 — Cache Eviction
- 원본 데이터 변경 시점:
  - 관리자 상품 등록/수정/삭제 → `@CacheEvict(value = "productSearch", allEntries = true)`
  - 재고 변동 → 캐시 무효화
  - 타임세일 이벤트 활성화 → 무효화
- `@CachePut` — 상품 상세 조회 시 갱신
- TTL 정책 (요구사항 언급):
  - 검색 결과: 5분 (자주 바뀌지만 무한 대기 방지)
  - 상품 상세: 30분
  - 인기 검색어 ZSet: Redis 자체 TTL 관리
- Eviction Policy: LRU (Redis 기본) · 최대 메모리 설정

## 산출물

- BE-10-1: `spring-boot-starter-data-redis` 의존성 · `RedisConfig`
- BE-10-2: `RedisCacheManager` + Serializer 설정
- BE-10-3: v2 검색 API를 Redis Cache로 스위칭
- BE-10-4: `@CacheEvict` 관리자 상품 CRUD에 적용
- BE-10-5: `@CachePut` 상품 상세에 적용
- BE-10-6: TTL 정책 설정 (도메인별 다르게)
- BE-10-7: Serializer 통합 테스트 (LocalDateTime 필드 포함)
- BE-10-8: Cache Eviction 통합 테스트 (상품 수정 → 다음 검색 = 캐시 미스)

## 관련 이슈 / 문서

- 선행: [04 검색 v2 Caffeine](./issue-04-search-v2-caffeine-local-cache.md)
- 다음: [11 부하테스트](./issue-11-load-test-k6-report.md) — v1/v2(Redis) 성능 비교
- 규범: `.claude/rules/architecture.md`

## 상세 (착수 시 채움)

_pending_
