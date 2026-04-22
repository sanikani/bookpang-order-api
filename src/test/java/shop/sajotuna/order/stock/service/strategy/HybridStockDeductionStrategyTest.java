package shop.sajotuna.order.stock.service.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import shop.sajotuna.order.stock.service.config.StockStrategyProperties;
import shop.sajotuna.order.stock.service.dto.StockDeductionMode;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HybridStockDeductionStrategyTest {

    private RecordingOptimisticStrategy optimisticStrategy;
    private RecordingPessimisticStrategy pessimisticStrategy;
    private RecordingAtomicStrategy atomicStrategy;
    private HybridStockDeductionStrategy hybridStrategy;

    @BeforeEach
    void setUp() {
        StockStrategyProperties properties = new StockStrategyProperties();
        properties.getStrategy().setDefaultMode(StockDeductionMode.OPTIMISTIC);
        properties.getStrategy().setHotMode(StockDeductionMode.PESSIMISTIC);
        properties.setHotIsbns(Set.of("HOT-ISBN-0001"));

        optimisticStrategy = new RecordingOptimisticStrategy();
        pessimisticStrategy = new RecordingPessimisticStrategy();
        atomicStrategy = new RecordingAtomicStrategy();
        hybridStrategy = new HybridStockDeductionStrategy(
                properties,
                optimisticStrategy,
                pessimisticStrategy,
                atomicStrategy
        );
    }

    @Test
    @DisplayName("하이브리드는 hot ISBN을 비관적 락으로 라우팅한다")
    void decrease_routesHotIsbnToPessimisticStrategy() {
        hybridStrategy.decrease("HOT-ISBN-0001", 1);

        assertThat(pessimisticStrategy.invoked).isTrue();
        assertThat(optimisticStrategy.invoked).isFalse();
    }

    @Test
    @DisplayName("하이브리드는 cold ISBN을 기본 전략으로 라우팅한다")
    void decrease_routesColdIsbnToDefaultStrategy() {
        hybridStrategy.decrease("COLD-ISBN-0001", 1);

        assertThat(optimisticStrategy.invoked).isTrue();
        assertThat(pessimisticStrategy.invoked).isFalse();
    }

    private static class RecordingOptimisticStrategy extends OptimisticStockDeductionStrategy {
        private boolean invoked;

        private RecordingOptimisticStrategy() {
            super(null, null);
        }

        @Override
        public void decrease(String isbn, int quantity) {
            invoked = true;
        }
    }

    private static class RecordingPessimisticStrategy extends PessimisticStockDeductionStrategy {
        private boolean invoked;

        private RecordingPessimisticStrategy() {
            super(null, null);
        }

        @Override
        public void decrease(String isbn, int quantity) {
            invoked = true;
        }
    }

    private static class RecordingAtomicStrategy extends AtomicUpdateStockDeductionStrategy {
        private RecordingAtomicStrategy() {
            super(null, null);
        }

        @Override
        public void decrease(String isbn, int quantity) {
        }
    }
}
