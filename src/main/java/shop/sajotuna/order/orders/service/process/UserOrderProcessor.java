package shop.sajotuna.order.orders.service.process;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import shop.sajotuna.order.common.domain.Money;
import shop.sajotuna.order.orders.domain.Discounts;
import shop.sajotuna.order.orders.domain.Order;
import shop.sajotuna.order.orders.domain.OrderProduct;
import shop.sajotuna.order.orders.service.pricing.DiscountService;
import shop.sajotuna.order.orders.service.dto.command.CreateOrderCommand;
import shop.sajotuna.order.point.domain.PointPolicy;
import shop.sajotuna.order.point.domain.PointPolicyType;
import shop.sajotuna.order.point.domain.UserPoint;
import shop.sajotuna.order.point.repository.UserPointRepository;
import shop.sajotuna.order.point.service.PointHistoryWriter;
import shop.sajotuna.order.point.service.PointPolicyService;
import shop.sajotuna.order.point.service.PointService;
import shop.sajotuna.order.point.service.dto.event.PointEarnRequest;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UserOrderProcessor implements OrderProcessor {
    
    private final DiscountService discountService;
    private final ApplicationEventPublisher eventPublisher;
    private final PointService pointService;
    private final UserPointRepository userPointRepository;
    private final PointPolicyService pointPolicyService;
    private final PointHistoryWriter pointHistoryWriter;

    @Override
    public Discounts processDiscounts(CreateOrderCommand command, List<OrderProduct> orderProducts) {
        return discountService.applyDiscountsToProducts(
            command.getOrderCouponId(),
            command.getUsedPoint(),
            command.getUserId(),
            orderProducts
        );
    }
    
    @Override
    public void processPointEarn(CreateOrderCommand command, Order order) {
        PointEarnRequest event = pointService.earnPoints(command.getUserId(), PointPolicyType.PURCHASE, order.getFinalProductPrice());

        eventPublisher.publishEvent(event);
        order.setEarnedPoint(event.getPointAmount());
    }

    @Override
    public void processPointEarnSync(CreateOrderCommand command, Order order) {
        PointEarnRequest event = pointService.earnPoints(command.getUserId(), PointPolicyType.PURCHASE, order.getFinalProductPrice());

        UserPoint userPoint = userPointRepository.findByUserId(event.getUserId())
                .orElseGet(() -> userPointRepository.save(UserPoint.create(event.getUserId())));

        Money amount;
        if (event.getPointAmount() == null) {
            PointPolicy pointPolicy = pointPolicyService.getPointPolicy(event.getType());
            amount = pointPolicy.getFixedPoint();
        } else {
            amount = event.getPointAmount();
        }
        userPoint.earnPoint(amount);

        // 포인트 이력 저장
        pointHistoryWriter.savePointEarnHistory(event.getUserId(), amount, event.getType().getDescription());
        order.setEarnedPoint(event.getPointAmount());
    }
}