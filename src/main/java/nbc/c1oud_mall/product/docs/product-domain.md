# Product Domain Docs

## API 예외 정책
- 단건 조회 시 존재하지 않는 상품을 요청하면 `ErrorCode.PRODUCT_NOT_FOUND(PRD001)`을 통해 404 응답을 반환합니다.