package shop.sajotuna.order.stock.service.strategy;

import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import shop.sajotuna.order.stock.exception.StockProcessingFailedException;
import shop.sajotuna.order.stock.service.config.StockStrategyProperties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptimisticStockDeductionStrategyTest {

    @Test
    void recoverDecrease_wrapsOptimisticLockingFailureByDefault() {
        StockStrategyProperties properties = new StockStrategyProperties();
        OptimisticStockDeductionStrategy strategy =
                new OptimisticStockDeductionStrategy(null, null, properties);
        ObjectOptimisticLockingFailureException exception =
                new ObjectOptimisticLockingFailureException("bookStock", 1L);

        assertThatThrownBy(() -> strategy.recoverDecrease(exception, "HOT-ISBN-0001", 1))
                .isInstanceOf(StockProcessingFailedException.class);
    }

    @Test
    void recoverDecrease_rethrowsOptimisticLockingFailureForOrderRetryBenchmark() {
        StockStrategyProperties properties = new StockStrategyProperties();
        properties.getRetry().getOptimistic().setRethrowOnRecovery(true);
        OptimisticStockDeductionStrategy strategy =
                new OptimisticStockDeductionStrategy(null, null, properties);
        ObjectOptimisticLockingFailureException exception =
                new ObjectOptimisticLockingFailureException("bookStock", 1L);

        assertThatThrownBy(() -> strategy.recoverDecrease(exception, "HOT-ISBN-0001", 1))
                .isSameAs(exception);
    }
}
