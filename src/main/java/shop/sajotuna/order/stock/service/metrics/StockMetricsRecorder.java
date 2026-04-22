package shop.sajotuna.order.stock.service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import shop.sajotuna.order.stock.service.dto.StockDeductionMode;

import java.util.concurrent.TimeUnit;

@Component
public class StockMetricsRecorder {

    private final MeterRegistry meterRegistry;

    public StockMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer.Sample startSample() {
        return Timer.start(meterRegistry);
    }

    public void recordDuration(Timer.Sample sample, StockDeductionMode strategy, String isbnType) {
        sample.stop(
                Timer.builder("stock.deduction.duration")
                        .tag("strategy", strategy.name().toLowerCase())
                        .tag("isbn_type", isbnType)
                        .register(meterRegistry)
        );
    }

    public void incrementRetry(StockDeductionMode strategy) {
        increment("stock.deduction.retry.count", strategy);
    }

    public void incrementInsufficientStock(StockDeductionMode strategy) {
        increment("stock.deduction.insufficient.count", strategy);
    }

    public void incrementLockTimeout(StockDeductionMode strategy) {
        increment("stock.deduction.lock_timeout.count", strategy);
    }

    public void incrementAtomicMiss(StockDeductionMode strategy) {
        increment("stock.deduction.atomic_miss.count", strategy);
    }

    public void recordExecutionTime(String metricName, StockDeductionMode strategy, long nanos) {
        Timer.builder(metricName)
                .tag("strategy", strategy.name().toLowerCase())
                .register(meterRegistry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    private void increment(String metricName, StockDeductionMode strategy) {
        Counter.builder(metricName)
                .tag("strategy", strategy.name().toLowerCase())
                .register(meterRegistry)
                .increment();
    }
}
