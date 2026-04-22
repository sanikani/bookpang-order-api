package shop.sajotuna.order.stock.service.strategy;

import org.springframework.stereotype.Component;
import shop.sajotuna.order.stock.service.config.StockStrategyProperties;
import shop.sajotuna.order.stock.service.dto.StockDeductionMode;

@Component
public class HybridStockDeductionStrategy implements StockDeductionStrategy {

    private final StockStrategyProperties properties;
    private final OptimisticStockDeductionStrategy optimisticStrategy;
    private final PessimisticStockDeductionStrategy pessimisticStrategy;
    private final AtomicUpdateStockDeductionStrategy atomicStrategy;

    public HybridStockDeductionStrategy(
            StockStrategyProperties properties,
            OptimisticStockDeductionStrategy optimisticStrategy,
            PessimisticStockDeductionStrategy pessimisticStrategy,
            AtomicUpdateStockDeductionStrategy atomicStrategy
    ) {
        this.properties = properties;
        this.optimisticStrategy = optimisticStrategy;
        this.pessimisticStrategy = pessimisticStrategy;
        this.atomicStrategy = atomicStrategy;
    }

    @Override
    public StockDeductionMode getMode() {
        return StockDeductionMode.HYBRID;
    }

    @Override
    public StockDeductionMode getMetricMode(String isbn) {
        return selectStrategy(isbn).getMode();
    }

    @Override
    public void decrease(String isbn, int quantity) {
        selectStrategy(isbn).decrease(isbn, quantity);
    }

    private StockDeductionStrategy selectStrategy(String isbn) {
        StockDeductionMode targetMode = properties.isHotIsbn(isbn)
                ? properties.getStrategy().getHotMode()
                : properties.getStrategy().getDefaultMode();

        return switch (targetMode) {
            case PESSIMISTIC -> pessimisticStrategy;
            case ATOMIC -> atomicStrategy;
            case OPTIMISTIC, HYBRID -> optimisticStrategy;
        };
    }
}
