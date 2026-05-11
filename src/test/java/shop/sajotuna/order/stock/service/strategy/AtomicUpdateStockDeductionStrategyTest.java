package shop.sajotuna.order.stock.service.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import shop.sajotuna.order.stock.domain.BookStock;
import shop.sajotuna.order.stock.domain.Stock;
import shop.sajotuna.order.stock.exception.BookStockNotFoundException;
import shop.sajotuna.order.stock.exception.InsufficientStockException;
import shop.sajotuna.order.stock.repository.BookStockRepository;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AtomicUpdateStockDeductionStrategyTest {

    private static final String ISBN = "ATOMIC-ISBN-0001";

    @Autowired
    private AtomicUpdateStockDeductionStrategy strategy;

    @Autowired
    private BookStockRepository bookStockRepository;

    @BeforeEach
    void setUp() {
        bookStockRepository.deleteAll();
    }

    @Test
    void decrease_reducesStockWhenQuantityIsEnough() {
        bookStockRepository.save(new BookStock(ISBN, Stock.of(10)));

        strategy.decrease(ISBN, 3);

        BookStock stock = bookStockRepository.findByIsbn(ISBN).orElseThrow();
        assertThat(stock.getStock().getQuantity()).isEqualTo(7);
    }

    @Test
    void decrease_doesNotReduceStockWhenQuantityIsInsufficient() {
        bookStockRepository.save(new BookStock(ISBN, Stock.of(2)));

        assertThatThrownBy(() -> strategy.decrease(ISBN, 3))
                .isInstanceOf(InsufficientStockException.class);

        BookStock stock = bookStockRepository.findByIsbn(ISBN).orElseThrow();
        assertThat(stock.getStock().getQuantity()).isEqualTo(2);
    }

    @Test
    void decrease_throwsBookStockNotFoundWhenIsbnDoesNotExist() {
        assertThatThrownBy(() -> strategy.decrease("UNKNOWN-ISBN", 1))
                .isInstanceOf(BookStockNotFoundException.class);
    }

    @Test
    void decrease_concurrentlyReducesOnlyAvailableQuantity() throws InterruptedException {
        bookStockRepository.save(new BookStock(ISBN, Stock.of(5)));

        int requestCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(requestCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger insufficientCount = new AtomicInteger();

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    strategy.decrease(ISBN, 1);
                    successCount.incrementAndGet();
                } catch (InsufficientStockException ex) {
                    insufficientCount.incrementAndGet();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        BookStock stock = bookStockRepository.findByIsbn(ISBN).orElseThrow();
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(insufficientCount.get()).isEqualTo(15);
        assertThat(stock.getStock().getQuantity()).isZero();
    }
}
