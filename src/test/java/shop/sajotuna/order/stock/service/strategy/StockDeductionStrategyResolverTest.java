package shop.sajotuna.order.stock.service.strategy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import shop.sajotuna.order.stock.exception.InsufficientStockException;
import shop.sajotuna.order.stock.service.config.StockStrategyProperties;
import shop.sajotuna.order.stock.service.dto.StockDeductionMode;
import shop.sajotuna.order.stock.service.metrics.StockMetricsRecorder;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockDeductionStrategyResolverTest {

    private RecordingStrategy optimisticStrategy;
    private RecordingStrategy pessimisticStrategy;
    private RecordingStrategy atomicStrategy;
    private StockDeductionStrategyResolver resolver;

    @BeforeEach
    void setUp() {
        optimisticStrategy = new RecordingStrategy(StockDeductionMode.OPTIMISTIC);
        pessimisticStrategy = new RecordingStrategy(StockDeductionMode.PESSIMISTIC);
        atomicStrategy = new RecordingStrategy(StockDeductionMode.ATOMIC);

        StockStrategyProperties properties = new StockStrategyProperties();
        properties.getStrategy().setDefaultMode(StockDeductionMode.ATOMIC);

        resolver = new StockDeductionStrategyResolver(
                List.of(optimisticStrategy, pessimisticStrategy, atomicStrategy),
                properties,
                new StockMetricsRecorder(new SimpleMeterRegistry())
        );
    }

    @Test
    @DisplayName("기본 전략 감소는 설정된 기본 전략을 사용한다")
    void decreaseWithDefaultStrategy_usesConfiguredDefaultMode() {
        resolver.decreaseWithDefaultStrategy("COLD-ISBN-0001", 3);

        assertThat(atomicStrategy.invoked).isTrue();
    }

    @Test
    @DisplayName("명시 전략 감소는 요청된 전략을 사용한다")
    void decrease_usesRequestedMode() {
        resolver.decrease(StockDeductionMode.ATOMIC, "COLD-ISBN-0001", 3);

        assertThat(atomicStrategy.invoked).isTrue();
    }

    @Test
    @DisplayName("재고 부족 예외는 그대로 전달한다")
    void decrease_rethrowsInsufficientStockException() {
        atomicStrategy.throwInsufficientStock = true;

        assertThatThrownBy(() -> resolver.decrease(StockDeductionMode.ATOMIC, "COLD-ISBN-0001", 3))
                .isInstanceOf(InsufficientStockException.class);
    }

    private static class RecordingStrategy implements StockDeductionStrategy {
        private final StockDeductionMode mode;
        private final StockDeductionMode metricMode;
        private boolean invoked;
        private boolean throwInsufficientStock;

        private RecordingStrategy(StockDeductionMode mode) {
            this(mode, mode);
        }

        private RecordingStrategy(StockDeductionMode mode, StockDeductionMode metricMode) {
            this.mode = mode;
            this.metricMode = metricMode;
        }

        @Override
        public StockDeductionMode getMode() {
            return mode;
        }

        @Override
        public StockDeductionMode getMetricMode(String isbn) {
            return metricMode;
        }

        @Override
        public void decrease(String isbn, int quantity) {
            invoked = true;
            if (throwInsufficientStock) {
                throw new InsufficientStockException();
            }
        }
    }
}
