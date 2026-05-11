package shop.sajotuna.order.orders.service.process;

import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Service;
import shop.sajotuna.order.orders.controller.dto.response.OrderResponse;
import shop.sajotuna.order.orders.service.metrics.OrderProcessMetricsRecorder;
import shop.sajotuna.order.orders.service.dto.command.CreateOrderCommand;

@Service
@RequiredArgsConstructor
public class OrderProcessService {

    private final OrderProcessTransactionalService orderProcessTransactionalService;
    private final OrderProcessMetricsRecorder metricsRecorder;

    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttemptsExpression = "${order.retry.max-attempts:5}",
            backoff = @Backoff(
                    delayExpression = "${order.retry.delay:100}",
                    multiplierExpression = "${order.retry.multiplier:1.5}"
            )
    )
    public OrderResponse processOrder(CreateOrderCommand command) {
        Integer retryCount = RetrySynchronizationManager.getContext() == null
                ? null
                : RetrySynchronizationManager.getContext().getRetryCount();
        if (retryCount != null && retryCount > 0) {
            metricsRecorder.incrementRetry();
        }

        Timer.Sample sample = metricsRecorder.startSample();
        try {
            return orderProcessTransactionalService.processOrder(command);
        } finally {
            metricsRecorder.recordDuration(sample);
        }
    }

    @Recover
    public OrderResponse recoverProcessOrder(OptimisticLockingFailureException ex, CreateOrderCommand command) {
        metricsRecorder.incrementFailed();
        throw ex;
    }
}
