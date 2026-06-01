package nbc.c1oud_mall.payment.infrastructure.portone;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("portone")
public record PortOneProperties(
        String baseUrl,
        String secret
) {
}
