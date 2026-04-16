package shop.sajotuna.order.orders.service.process;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import shop.sajotuna.order.orders.controller.dto.response.OrderResponse;
import shop.sajotuna.order.orders.service.dto.command.CreateOrderCommand;

@Service
@RequiredArgsConstructor
public class OrderProcessService {

    private final OrderProcessTransactionalService orderProcessTransactionalService;

    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 100, multiplier = 1.5)
    )
    public OrderResponse processOrder(CreateOrderCommand command) {
        return orderProcessTransactionalService.processOrder(command);
    }
}
