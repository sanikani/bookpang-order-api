package shop.sajotuna.order.orders.service.process;

import org.springframework.stereotype.Component;
import shop.sajotuna.order.common.domain.Money;
import shop.sajotuna.order.orders.domain.Discounts;
import shop.sajotuna.order.orders.domain.Order;
import shop.sajotuna.order.orders.domain.OrderProduct;
import shop.sajotuna.order.orders.service.dto.command.CreateOrderCommand;

import java.util.List;

@Component
public class GuestOrderProcessor implements OrderProcessor{
    
    @Override
    public Discounts processDiscounts(CreateOrderCommand command, List<OrderProduct> orderProducts) {
        // 비회원은 할인 혜택 없음
        return new Discounts(Money.zero(), Money.zero(), null);
    }
    
    @Override
    public void processPointEarn(CreateOrderCommand command, Order order) {
        // 비회원은 포인트 적립 없음
    }

    @Override
    public void processPointEarnSync(CreateOrderCommand command, Order order) {
        // 비회원은 포인트 적립 없음
    }
}