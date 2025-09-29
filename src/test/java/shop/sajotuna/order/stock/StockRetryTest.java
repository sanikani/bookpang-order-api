package shop.sajotuna.order.stock;

import jakarta.transaction.Transactional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import shop.sajotuna.order.common.domain.Money;
import shop.sajotuna.order.orders.domain.Orderer;
import shop.sajotuna.order.orders.domain.ShippingInfo;
import shop.sajotuna.order.orders.repository.OrderRepository;
import shop.sajotuna.order.orders.service.dto.command.CreateOrderCommand;
import shop.sajotuna.order.orders.service.process.OrderProcessService;
import shop.sajotuna.order.payment.domain.PaymentMethod;
import shop.sajotuna.order.stock.domain.BookStock;
import shop.sajotuna.order.stock.domain.Stock;
import shop.sajotuna.order.stock.exception.StockProcessingFailedException;
import shop.sajotuna.order.stock.repository.BookStockRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
public class StockRetryTest {

    @Autowired
    private OrderProcessService orderService;

    @Autowired
    private BookStockRepository bookStockRepository;

    @Autowired
    private OrderRepository orderRepository;


    @Test
    @DisplayName("동시성 충돌 시 재시도가 작동하지 않는 문제 확인")
    void testOptimisticLockRetryFailure() throws Exception {
        // Given: 재고 10개 설정
        BookStock stock = new BookStock("ISBN-123", Stock.of(10));
        bookStockRepository.save(stock);

        CreateOrderCommand command = CreateOrderCommand.builder()
                .orderer(Orderer.createOrderer(1L, "홍길동", "010-1234-5678","sanikani@naver.com"))
                .shippingInfo(ShippingInfo
                        .create("김산이",
                                "010-46690-4350",
                                "sanikani@naver.com",
                                "광주시",
                                LocalDate.now()))
                .paymentMethod(PaymentMethod.CARD)
                .orderCouponId(null)
                .usedPoint(Money.zero())
                .items(List.of(null))
                .build();

        // When: 동시에 2개의 주문 요청
        CountDownLatch latch = new CountDownLatch(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        List<Exception> exceptions = new CopyOnWriteArrayList<>();

        for (int i = 0; i < 2; i++) {
            executorService.submit(() -> {
                try {
                    orderService.processOrder(command);
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: OptimisticLockingFailureException 발생 확인
        assertThat(exceptions).isNotEmpty();
        assertThat(exceptions.getFirst())
                .isInstanceOfAny(
                        OptimisticLockingFailureException.class,
                        StockProcessingFailedException.class
                );

        // 재시도가 작동했다면 둘 다 성공해야 하지만, 실제로는 하나는 실패함
        long orderCount = orderRepository.count();
        assertThat(orderCount).isLessThan(2); // 실패 확인
    }
}
