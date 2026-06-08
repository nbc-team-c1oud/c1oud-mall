package nbc.c1oud_mall.refund.application;

public interface PointRestorePort {

    void restore(Long userId, long pointAmount, Long paymentId);
}
