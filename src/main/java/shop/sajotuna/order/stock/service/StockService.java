package shop.sajotuna.order.stock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Recover;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shop.sajotuna.order.stock.controller.request.CreateStockRequest;
import shop.sajotuna.order.stock.controller.response.BookStockResponse;
import shop.sajotuna.order.stock.domain.BookStock;
import shop.sajotuna.order.stock.domain.Stock;
import shop.sajotuna.order.stock.exception.BookStockNotFoundException;
import shop.sajotuna.order.stock.exception.StockProcessingFailedException;
import shop.sajotuna.order.stock.repository.BookStockRepository;
import shop.sajotuna.order.stock.service.dto.StockDeductionMode;
import shop.sajotuna.order.stock.service.strategy.StockDeductionStrategyResolver;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StockService {

    private final BookStockRepository bookStockRepository;
    private final StockDeductionStrategyResolver strategyResolver;

    public void decreaseStock(String isbn, int quantity) {
        strategyResolver.decreaseWithDefaultStrategy(isbn, quantity);
    }

    public void decreaseStock(String isbn, int quantity, StockDeductionMode strategy) {
        strategyResolver.decrease(strategy, isbn, quantity);
    }

    public void increaseStock(String isbn, int quantity) {
        BookStock bookStock = bookStockRepository.findByIsbn(isbn)
                .orElseThrow(BookStockNotFoundException::new);
        bookStock.increaseStock(Stock.of(quantity));
    }

    public BookStockResponse createStock(String isbn, int quantity) {
        if (bookStockRepository.existsByIsbn(isbn)) {
            throw new DuplicateBookStockException();
        }
        BookStock bookStock = new BookStock(isbn, Stock.of(quantity));
        bookStockRepository.save(bookStock);
        return BookStockResponse.from(bookStock);
    }

    @Recover
    public void recoverDecreaseStock(OptimisticLockingFailureException ex, String isbn, int quantity) {
        log.error("Stock deduction failed after retries. isbn={}, quantity={}", isbn, quantity, ex);
        throw new StockProcessingFailedException(isbn, quantity);
    }

    @Recover
    public void recoverIncreaseStock(OptimisticLockingFailureException ex, String isbn, int quantity) {
        log.error("Stock increase failed after retries. isbn={}, quantity={}", isbn, quantity, ex);
        throw new StockProcessingFailedException(isbn, quantity);
    }

    public List<BookStockResponse> createStocks(List<CreateStockRequest> createStockRequest) {
        List<String> isbns = createStockRequest.stream()
                .map(CreateStockRequest::getIsbn)
                .toList();

        List<String> existingIsbns = bookStockRepository.findByIsbnIn(isbns).stream()
                .map(BookStock::getIsbn)
                .toList();

        List<BookStock> bookStocks = createStockRequest.stream()
                .filter(request -> !existingIsbns.contains(request.getIsbn()))
                .map(request -> new BookStock(request.getIsbn(), Stock.of(request.getStock())))
                .toList();

        List<BookStock> savedStocks = bookStockRepository.saveAll(bookStocks);

        return savedStocks.stream()
                .map(BookStockResponse::from)
                .toList();
    }

    public void updateStock(String isbn, int quantity) {
        BookStock bookStock = bookStockRepository.findByIsbn(isbn)
                .orElseThrow(BookStockNotFoundException::new);
        bookStock.update(Stock.of(quantity));
    }
}
