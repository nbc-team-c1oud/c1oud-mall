package nbc.c1oud_mall.payment.infrastructure.portone;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PortOneCancelRequest(String reason, Long amount, String requestKey) {
}
