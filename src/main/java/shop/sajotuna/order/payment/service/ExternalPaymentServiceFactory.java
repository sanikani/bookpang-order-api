package shop.sajotuna.order.payment.service;

import org.springframework.stereotype.Component;
import shop.sajotuna.order.payment.domain.PaymentMethod;
import shop.sajotuna.order.payment.exception.NotAvailablePayment;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ExternalPaymentServiceFactory {

    private final Map<PaymentMethod, ExternalPaymentService> serviceMap;

    public ExternalPaymentServiceFactory(List<ExternalPaymentService> paymentServices) {
        this.serviceMap = paymentServices.stream()
                .collect(Collectors.toMap(
                        ExternalPaymentService::getPaymentMethod,
                        service -> service,
                        (key, value) -> {throw new IllegalStateException("중복된 키가 존재합니다");}
                ));
    }

    public ExternalPaymentService getService(PaymentMethod paymentMethod) {
        return serviceMap.computeIfAbsent(paymentMethod,
                method -> {
                    throw new NotAvailablePayment(method);
        });
    }
}
