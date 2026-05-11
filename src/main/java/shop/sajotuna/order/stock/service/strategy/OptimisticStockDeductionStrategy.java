package shop.sajotuna.order.stock.service.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import shop.sajotuna.order.stock.domain.BookStock;
import shop.sajotuna.order.stock.domain.Stock;
import shop.sajotuna.order.stock.exception.BookStockNotFoundException;
import shop.sajotuna.order.stock.exception.StockProcessingFailedException;
import shop.sajotuna.order.stock.repository.BookStockRepository;
import shop.sajotuna.order.stock.service.config.StockStrategyProperties;
import shop.sajotuna.order.stock.service.dto.StockDeductionMode;
import shop.sajotuna.order.stock.service.metrics.StockMetricsRecorder;

@Slf4j
@Component
public class OptimisticStockDeductionStrategy implements StockDeductionStrategy {

    private final BookStockRepository bookStockRepository;
    private final StockMetricsRecorder metricsRecorder;
    private final StockStrategyProperties properties;

    public OptimisticStockDeductionStrategy(
            BookStockRepository bookStockRepository,
            StockMetricsRecorder metricsRecorder,
            StockStrategyProperties properties
    ) {
        this.bookStockRepository = bookStockRepository;
        this.metricsRecorder = metricsRecorder;
        this.properties = properties;
    }

    @Override
    public StockDeductionMode getMode() {
        return StockDeductionMode.OPTIMISTIC;
    }

    @Override
    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttemptsExpression = "${stock.retry.optimistic.max-attempts:5}",
            backoff = @Backoff(
                    delayExpression = "${stock.retry.optimistic.delay:50}",
                    multiplierExpression = "${stock.retry.optimistic.multiplier:2.0}"
            )
    )
    @Transactional
    public void decrease(String isbn, int quantity) {
        Integer retryCount = RetrySynchronizationManager.getContext() == null
                ? null
                : RetrySynchronizationManager.getContext().getRetryCount();
        if (retryCount != null && retryCount > 0) {
            metricsRecorder.incrementRetry(getMode());
        }

        BookStock bookStock = bookStockRepository.findByIsbn(isbn)
                .orElseThrow(BookStockNotFoundException::new);
        bookStock.decreaseStock(Stock.of(quantity));
    }

    @Recover
    public void recoverDecrease(OptimisticLockingFailureException ex, String isbn, int quantity) {
        log.error("Optimistic stock deduction failed after retries. isbn={}, quantity={}", isbn, quantity, ex);
        if (properties.getRetry().getOptimistic().isRethrowOnRecovery()) {
            throw ex;
        }
        throw new StockProcessingFailedException(isbn, quantity);
    }
}
