package nbc.c1oud_mall.refund.domain;

public enum RefundStatus {
    REQUESTED, DB_COMMITTED, PG_CANCELLED, FAILED
}
