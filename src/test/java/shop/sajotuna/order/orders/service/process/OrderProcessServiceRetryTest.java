package shop.sajotuna.order.orders.service.process;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import shop.sajotuna.order.orders.controller.dto.response.OrderResponse;
import shop.sajotuna.order.orders.service.dto.command.CreateOrderCommand;
import shop.sajotuna.order.orders.service.metrics.OrderProcessMetricsRecorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = {
                OrderProcessServiceRetryTest.TestConfig.class,
                OrderProcessService.class,
                OrderProcessMetricsRecorder.class
        },
        properties = {
                "order.retry.max-attempts=3",
                "order.retry.delay=1",
                "order.retry.multiplier=1.0"
        }
)
class OrderProcessServiceRetryTest {

    @Autowired
    private OrderProcessService orderProcessService;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockitoBean
    private OrderProcessTransactionalService transactionalService;

    @Test
    void processOrder_retriesWholeOrderWhenOptimisticLockFails() {
        CreateOrderCommand command = mock(CreateOrderCommand.class);
        OrderResponse response = OrderResponse.builder()
                .orderId(1L)
                .orderNumber("benchmark-order")
                .build();
        ObjectOptimisticLockingFailureException exception =
                new ObjectOptimisticLockingFailureException("bookStock", 1L);

        when(transactionalService.processOrder(command))
                .thenThrow(exception)
                .thenThrow(exception)
                .thenReturn(response);

        OrderResponse result = orderProcessService.processOrder(command);

        assertThat(result).isSameAs(response);
        verify(transactionalService, times(3)).processOrder(command);
        assertThat(meterRegistry.counter("order.process.retry.count").count()).isEqualTo(2.0);
    }

    @Configuration
    @EnableRetry
    static class TestConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
