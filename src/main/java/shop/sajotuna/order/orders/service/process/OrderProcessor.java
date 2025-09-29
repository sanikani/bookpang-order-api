package shop.sajotuna.order.orders.service.process;

import shop.sajotuna.order.orders.domain.Discounts;
import shop.sajotuna.order.orders.domain.Order;
import shop.sajotuna.order.orders.domain.OrderProduct;
import shop.sajotuna.order.orders.service.dto.command.CreateOrderCommand;

import java.util.List;

public interface OrderProcessor {
    Discounts processDiscounts(CreateOrderCommand command, List<OrderProduct> orderProducts);

    void processPointEarn(CreateOrderCommand command, Order order);

    void processPointEarnSync(CreateOrderCommand command, Order order);
}
