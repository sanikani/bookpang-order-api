package shop.sajotuna.order.stock.service.strategy;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import shop.sajotuna.order.stock.exception.BookStockNotFoundException;
import shop.sajotuna.order.stock.exception.InsufficientStockException;
import shop.sajotuna.order.stock.repository.BookStockRepository;
import shop.sajotuna.order.stock.service.dto.StockDeductionMode;
import shop.sajotuna.order.stock.service.metrics.StockMetricsRecorder;

@Component
public class AtomicUpdateStockDeductionStrategy implements StockDeductionStrategy {

    private final BookStockRepository bookStockRepository;
    private final StockMetricsRecorder metricsRecorder;

    public AtomicUpdateStockDeductionStrategy(
            BookStockRepository bookStockRepository,
            StockMetricsRecorder metricsRecorder
    ) {
        this.bookStockRepository = bookStockRepository;
        this.metricsRecorder = metricsRecorder;
    }

    @Override
    public StockDeductionMode getMode() {
        return StockDeductionMode.ATOMIC;
    }

    @Override
    @Transactional
    public void decrease(String isbn, int quantity) {
        if (!bookStockRepository.existsByIsbn(isbn)) {
            throw new BookStockNotFoundException();
        }

        int affectedRows = bookStockRepository.decreaseStockAtomically(isbn, quantity);
        if (affectedRows == 0) {
            metricsRecorder.incrementAtomicMiss(getMode());
            throw new InsufficientStockException();
        }
    }
}
