package shop.sajotuna.order.stock.service.strategy;

import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import shop.sajotuna.order.stock.exception.InsufficientStockException;
import shop.sajotuna.order.stock.service.config.StockStrategyProperties;
import shop.sajotuna.order.stock.service.dto.StockDeductionMode;
import shop.sajotuna.order.stock.service.metrics.StockMetricsRecorder;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class StockDeductionStrategyResolver {

    private final Map<StockDeductionMode, StockDeductionStrategy> strategies;
    private final StockStrategyProperties properties;
    private final StockMetricsRecorder metricsRecorder;

    public StockDeductionStrategyResolver(
            List<StockDeductionStrategy> strategies,
            StockStrategyProperties properties,
            StockMetricsRecorder metricsRecorder
    ) {
        this.properties = properties;
        this.metricsRecorder = metricsRecorder;
        this.strategies = new EnumMap<>(StockDeductionMode.class);
        for (StockDeductionStrategy strategy : strategies) {
            this.strategies.put(strategy.getMode(), strategy);
        }
    }

    public void decreaseWithDefaultStrategy(String isbn, int quantity) {
        decrease(properties.getStrategy().getDefaultMode(), isbn, quantity);
    }

    public void decrease(StockDeductionMode requestedMode, String isbn, int quantity) {
        StockDeductionStrategy strategy = resolve(requestedMode);
        Timer.Sample sample = metricsRecorder.startSample();
        String isbnType = properties.isHotIsbn(isbn) ? "hot" : "cold";
        StockDeductionMode metricMode = strategy.getMetricMode(isbn);
        long startNanos = System.nanoTime();
        try {
            strategy.decrease(isbn, quantity);
        } catch (InsufficientStockException ex) {
            metricsRecorder.incrementInsufficientStock(metricMode);
            throw ex;
        } finally {
            metricsRecorder.recordDuration(sample, metricMode, isbnType);
            metricsRecorder.recordExecutionTime("stock.deduction.execution", metricMode, System.nanoTime() - startNanos);
        }
    }

    private StockDeductionStrategy resolve(StockDeductionMode requestedMode) {
        StockDeductionMode mode = requestedMode == null
                ? properties.getStrategy().getDefaultMode()
                : requestedMode;
        return strategies.get(mode);
    }
}
