package nbc.c1oud_mall.refund.application;

import nbc.c1oud_mall.refund.domain.RefundAmountCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RefundConfig {

    @Bean
    public RefundAmountCalculator refundAmountCalculator() {
        return new RefundAmountCalculator();
    }
}
