package shop.sajotuna.order.orders.service.process;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import shop.sajotuna.order.common.domain.Money;
import shop.sajotuna.order.orders.domain.DeliveryPrice;
import shop.sajotuna.order.orders.domain.Orderer;
import shop.sajotuna.order.orders.domain.ShippingInfo;
import shop.sajotuna.order.orders.rabbitmq.OrderEventListener;
import shop.sajotuna.order.orders.repository.DeliveryPriceRepository;
import shop.sajotuna.order.orders.repository.OrderRepository;
import shop.sajotuna.order.orders.service.dto.command.CreateOrderCommand;
import shop.sajotuna.order.orders.service.dto.command.CreateOrderProductCommand;
import shop.sajotuna.order.stock.domain.BookStock;
import shop.sajotuna.order.stock.domain.Stock;
import shop.sajotuna.order.stock.exception.InsufficientStockException;
import shop.sajotuna.order.stock.repository.BookStockRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "stock.strategy.default-mode=atomic")
class OrderProcessAtomicRollbackTest {

    private static final String FIRST_ISBN = "ROLLBACK-ISBN-0001";
    private static final String SECOND_ISBN = "ROLLBACK-ISBN-0002";

    @Autowired
    private OrderProcessTransactionalService orderProcessTransactionalService;

    @Autowired
    private BookStockRepository bookStockRepository;

    @Autowired
    private DeliveryPriceRepository deliveryPriceRepository;

    @Autowired
    private OrderRepository orderRepository;

    @MockitoBean
    private OrderEventListener orderEventListener;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        bookStockRepository.deleteAll();
        deliveryPriceRepository.deleteAll();

        deliveryPriceRepository.save(new DeliveryPrice(null, Money.of(30_000), Money.of(3_000)));
        bookStockRepository.save(new BookStock(FIRST_ISBN, Stock.of(1)));
        bookStockRepository.save(new BookStock(SECOND_ISBN, Stock.of(0)));
    }

    @Test
    void processOrder_rollsBackAlreadyDeductedStockWhenLaterItemIsInsufficient() {
        CreateOrderCommand command = CreateOrderCommand.builder()
                .orderer(Orderer.createOrderer(null, "tester", "010-1234-5678", "tester@example.com"))
                .shippingInfo(ShippingInfo.create(
                        "tester",
                        "010-1234-5678",
                        "tester@example.com",
                        "Seoul",
                        LocalDate.now().plusDays(3)
                ))
                .usedPoint(Money.zero())
                .items(List.of(
                        orderItem(FIRST_ISBN),
                        orderItem(SECOND_ISBN)
                ))
                .build();

        assertThatThrownBy(() -> orderProcessTransactionalService.processOrder(command))
                .isInstanceOf(InsufficientStockException.class);

        BookStock firstStock = bookStockRepository.findByIsbn(FIRST_ISBN).orElseThrow();
        BookStock secondStock = bookStockRepository.findByIsbn(SECOND_ISBN).orElseThrow();

        assertThat(firstStock.getStock().getQuantity()).isEqualTo(1);
        assertThat(secondStock.getStock().getQuantity()).isZero();
        assertThat(orderRepository.count()).isZero();
    }

    private CreateOrderProductCommand orderItem(String isbn) {
        return CreateOrderProductCommand.builder()
                .isbn(isbn)
                .quantity(1)
                .amount(Money.of(15_000))
                .packagingRequest(false)
                .categoryIds(Set.of())
                .build();
    }
}
