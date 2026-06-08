package nbc.c1oud_mall.order.domain;

public enum OrderStatus {
    PENDING_PAYMENT {
        @Override
        public boolean canTransitTo(OrderStatus target) {
            return target == CONFIRMED || target == CANCELLED;
        }
    },
    CONFIRMED {
        @Override
        public boolean canTransitTo(OrderStatus target) {
            return target == CANCELLED;
        }
    },
    CANCELLED {
        @Override
        public boolean canTransitTo(OrderStatus target) {
            return false;
        }
    };

    public abstract boolean canTransitTo(OrderStatus target);
}
