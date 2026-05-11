package shop.sajotuna.order.orders.service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class OrderProcessMetricsRecorder {

    private final MeterRegistry meterRegistry;

    public OrderProcessMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer.Sample startSample() {
        return Timer.start(meterRegistry);
    }

    public void recordDuration(Timer.Sample sample) {
        sample.stop(Timer.builder("order.process.duration").register(meterRegistry));
    }

    public void incrementRetry() {
        increment("order.process.retry.count");
    }

    public void incrementFailed() {
        increment("order.process.failed.count");
    }

    private void increment(String metricName) {
        Counter.builder(metricName)
                .register(meterRegistry)
                .increment();
    }
}
