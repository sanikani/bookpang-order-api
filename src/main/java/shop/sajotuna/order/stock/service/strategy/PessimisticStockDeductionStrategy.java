package shop.sajotuna.order.stock.service.strategy;

import jakarta.persistence.LockTimeoutException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import shop.sajotuna.order.stock.domain.BookStock;
import shop.sajotuna.order.stock.domain.Stock;
import shop.sajotuna.order.stock.exception.BookStockNotFoundException;
import shop.sajotuna.order.stock.exception.StockProcessingFailedException;
import shop.sajotuna.order.stock.repository.BookStockRepository;
import shop.sajotuna.order.stock.service.dto.StockDeductionMode;
import shop.sajotuna.order.stock.service.metrics.StockMetricsRecorder;

@Component
public class PessimisticStockDeductionStrategy implements StockDeductionStrategy {

    private final BookStockRepository bookStockRepository;
    private final StockMetricsRecorder metricsRecorder;

    public PessimisticStockDeductionStrategy(
            BookStockRepository bookStockRepository,
            StockMetricsRecorder metricsRecorder
    ) {
        this.bookStockRepository = bookStockRepository;
        this.metricsRecorder = metricsRecorder;
    }

    @Override
    public StockDeductionMode getMode() {
        return StockDeductionMode.PESSIMISTIC;
    }

    @Override
    @Transactional
    public void decrease(String isbn, int quantity) {
        try {
            BookStock bookStock = bookStockRepository.findByIsbnWithPessimisticLock(isbn)
                    .orElseThrow(BookStockNotFoundException::new);
            bookStock.decreaseStock(Stock.of(quantity));
        } catch (PessimisticLockingFailureException | LockTimeoutException ex) {
            metricsRecorder.incrementLockTimeout(getMode());
            throw new StockProcessingFailedException(isbn, quantity);
        }
    }
}
