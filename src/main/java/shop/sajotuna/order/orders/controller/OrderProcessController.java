package shop.sajotuna.order.orders.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import shop.sajotuna.order.orders.controller.dto.request.CreateOrderRequest;
import shop.sajotuna.order.orders.controller.dto.response.OrderResponse;
import shop.sajotuna.order.orders.service.process.OrderProcessService;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderProcessController {
    private final OrderProcessService orderProcessService;

    // 상품 주문 저장
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestHeader(value = "X-User-Id", required = false) Long userId, @RequestBody @Valid CreateOrderRequest request) {
        OrderResponse orderResponse = orderProcessService.processOrder(request.toCommand(userId));
        return ResponseEntity.ok(orderResponse);
    }

    @PostMapping("/sync")
    public ResponseEntity<OrderResponse> createOrderSync(@RequestBody @Valid CreateOrderRequest request) {
        OrderResponse orderResponse = orderProcessService.processOrder(request.toCommand(1L));
        return ResponseEntity.ok(orderResponse);
    }
}
