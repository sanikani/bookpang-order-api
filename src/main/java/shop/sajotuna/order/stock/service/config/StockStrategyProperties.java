package shop.sajotuna.order.stock.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import shop.sajotuna.order.stock.service.dto.StockDeductionMode;

@ConfigurationProperties(prefix = "stock")
public class StockStrategyProperties {

    private Strategy strategy = new Strategy();
    private Retry retry = new Retry();

    public Strategy getStrategy() {
        return strategy;
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    public static class Strategy {
        private StockDeductionMode defaultMode = StockDeductionMode.ATOMIC;

        public StockDeductionMode getDefaultMode() {
            return defaultMode;
        }

        public void setDefaultMode(StockDeductionMode defaultMode) {
            this.defaultMode = defaultMode;
        }

    }

    public static class Retry {
        private Optimistic optimistic = new Optimistic();

        public Optimistic getOptimistic() {
            return optimistic;
        }

        public void setOptimistic(Optimistic optimistic) {
            this.optimistic = optimistic;
        }

        public static class Optimistic {
            private int maxAttempts = 5;
            private long delay = 50L;
            private double multiplier = 2.0;
            private boolean rethrowOnRecovery = false;

            public int getMaxAttempts() {
                return maxAttempts;
            }

            public void setMaxAttempts(int maxAttempts) {
                this.maxAttempts = maxAttempts;
            }

            public long getDelay() {
                return delay;
            }

            public void setDelay(long delay) {
                this.delay = delay;
            }

            public double getMultiplier() {
                return multiplier;
            }

            public void setMultiplier(double multiplier) {
                this.multiplier = multiplier;
            }

            public boolean isRethrowOnRecovery() {
                return rethrowOnRecovery;
            }

            public void setRethrowOnRecovery(boolean rethrowOnRecovery) {
                this.rethrowOnRecovery = rethrowOnRecovery;
            }
        }
    }
}
