package shop.sajotuna.order.orders.service.process;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import shop.sajotuna.order.orders.controller.dto.response.OrderResponse;
import shop.sajotuna.order.orders.domain.Discounts;
import shop.sajotuna.order.orders.domain.Order;
import shop.sajotuna.order.orders.domain.OrderPrice;
import shop.sajotuna.order.orders.domain.OrderProduct;
import shop.sajotuna.order.orders.rabbitmq.OrderEvent;
import shop.sajotuna.order.orders.rabbitmq.OrderEventListener;
import shop.sajotuna.order.orders.repository.OrderRepository;
import shop.sajotuna.order.orders.service.dto.command.CreateOrderCommand;
import shop.sajotuna.order.orders.service.pricing.PricingService;
import shop.sajotuna.order.orders.service.product.OrderProductCreateService;
import shop.sajotuna.order.stock.service.StockService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderProcessTransactionalService {

    private final PricingService pricingService;
    private final OrderRepository orderRepository;
    private final OrderProductCreateService orderProductCreateService;
    private final StockService stockService;
    private final OrderProcessorFactory orderProcessorFactory;
    private final OrderEventListener orderEventListener;

    @Transactional
    public OrderResponse processOrder(CreateOrderCommand command) {

        //여기서 쿠폰을 가진 OrderProduct 생성
        List<OrderProduct> orderProducts = orderProductCreateService.createOrderProducts(command.getItems(), command.getUserId());

        orderProducts.forEach(product ->
                stockService.decreaseStock(product.getIsbn(), product.getQty())
        );

        OrderPrice orderPrice = pricingService.calculatePrices(orderProducts);

        // Factory를 통해 적절한 Processor 선택
        OrderProcessor orderProcessor = orderProcessorFactory.getOrderProcessor(command.getUserId());
        Discounts discounts = orderProcessor.processDiscounts(command, orderProducts);

        Order order = Order.createOrder(command.getOrderer(), command.getShippingInfo(), orderPrice, discounts, orderProducts);
        orderRepository.save(order);

        // 포인트 적립 처리
        orderProcessor.processPointEarn(command, order);

        // 주문 큐에 메시지 전송
        orderEventListener.handleOrderCreated(new OrderEvent(order.getId(), command.getUserId()));
        return OrderResponse.from(order);
    }
}
