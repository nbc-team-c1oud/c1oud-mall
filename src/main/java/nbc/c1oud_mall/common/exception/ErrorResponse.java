package nbc.c1oud_mall.common.exception;

import java.time.OffsetDateTime;

public record ErrorResponse(
        String code,
        String message,
        String path,
        OffsetDateTime timestamp
) {
}
