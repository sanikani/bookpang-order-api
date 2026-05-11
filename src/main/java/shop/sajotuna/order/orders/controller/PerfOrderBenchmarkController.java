package shop.sajotuna.order.orders.controller;

import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shop.sajotuna.order.common.domain.Money;
import shop.sajotuna.order.orders.controller.dto.request.PerfOrderBenchmarkSeedRequest;
import shop.sajotuna.order.orders.domain.DeliveryPrice;
import shop.sajotuna.order.orders.repository.DeliveryPriceRepository;
import shop.sajotuna.order.orders.repository.OrderProductRepository;
import shop.sajotuna.order.orders.repository.OrderRepository;
import shop.sajotuna.order.stock.domain.BookStock;
import shop.sajotuna.order.stock.domain.Stock;
import shop.sajotuna.order.stock.repository.BookStockRepository;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/perf/orders")
public class PerfOrderBenchmarkController {

    private final BookStockRepository bookStockRepository;
    private final DeliveryPriceRepository deliveryPriceRepository;
    private final EntityManager entityManager;
    private final OrderProductRepository orderProductRepository;
    private final OrderRepository orderRepository;

    @PostMapping("/seed")
    @Transactional
    public ResponseEntity<Void> seed(@RequestBody @Valid PerfOrderBenchmarkSeedRequest request) {
        orderProductRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        seedDeliveryPrice();
        seedStocks(request);
        return ResponseEntity.ok().build();
    }

    private void seedDeliveryPrice() {
        if (!deliveryPriceRepository.existsById(DeliveryPriceRepository.DEFAULT_DELIVERY_PRICE_ID)) {
            entityManager.createNativeQuery("""
                            insert ignore into delivery_price
                                (id, free_delivery_min_price, delivery_price)
                            values
                                (:id, :freeDeliveryMinPrice, :deliveryPrice)
                            """)
                    .setParameter("id", DeliveryPriceRepository.DEFAULT_DELIVERY_PRICE_ID)
                    .setParameter("freeDeliveryMinPrice", Money.of(30_000).getAmount())
                    .setParameter("deliveryPrice", Money.of(3_000).getAmount())
                    .executeUpdate();
        }
    }

    private void seedStocks(PerfOrderBenchmarkSeedRequest request) {
        for (int i = 1; i <= request.getHotCount(); i++) {
            upsertStock(request.getHotIsbnPrefix() + String.format("%04d", i), request.getInitialStock());
        }

        for (int i = 1; i <= request.getColdCount(); i++) {
            upsertStock(request.getColdIsbnPrefix() + String.format("%04d", i), request.getInitialStock());
        }
    }

    private void upsertStock(String isbn, int quantity) {
        bookStockRepository.findByIsbn(isbn)
                .ifPresentOrElse(
                        bookStock -> bookStock.update(Stock.of(quantity)),
                        () -> bookStockRepository.save(new BookStock(isbn, Stock.of(quantity)))
                );
    }
}
