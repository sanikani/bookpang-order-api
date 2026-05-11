package shop.sajotuna.order.orders.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import shop.sajotuna.order.common.domain.Money;
import shop.sajotuna.order.orders.controller.dto.request.CreateOrderRequest;
import shop.sajotuna.order.orders.controller.dto.response.OrderResponse;
import shop.sajotuna.order.orders.domain.*;
import shop.sajotuna.order.orders.service.process.OrderProcessService;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderProcessController.class)
@ActiveProfiles("test")
public class OrderProcessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderProcessService orderProcessService;

    Long userId = 1L;

    OrderResponse orderResponse;

    @BeforeEach
    public void setup() {
        Order fakeOrder = createTestOrder();

        orderResponse = OrderResponse.from(fakeOrder);
    }

    @Test
    @DisplayName("POST /api/orders - 주문 완료")
    void createOrder() throws Exception {
        given(orderProcessService.processOrder(any())).willReturn(orderResponse);

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content("""
                                {
                                  "ordererName": "홍길동",
                                  "ordererPhoneNumber": "010-1234-5678",
                                  "ordererEmail": "hong@example.com",
                                  "recipientName": "김수령",
                                  "recipientPhoneNumber": "010-9876-5432",
                                  "recipientEmail": "kim@example.com",
                                  "recipientAddress": "서울시 강남구 테헤란로 123",
                                  "expectedDeliveryDate": "20261230",
                                  "orderCouponId": 6,
                                  "usedPoint": 1000,
                                  "items": [
                                    {
                                      "isbn": "1235433121",
                                      "qty": 1,
                                      "amount": 70000,
                                      "packagingRequest": false
                                    }
                                  ]
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.orderNumber").exists())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.totalPrice").exists());
    }

    @Test
    @DisplayName("POST /api/orders - request에 데이터가 없으면 에러 발생")
    void createOrderFail() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();

        given(orderProcessService.processOrder(any())).willThrow(new IllegalArgumentException("Invalid request"));

        // Act & Assert
        mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private Order createTestOrder() {
        Orderer orderer = new Orderer(1L, "홍길동", "010-1234-5678", "test@example.com");
        ShippingInfo shippingInfo = ShippingInfo.create(
                "홍길동", "010-1234-5678", "test@example.com",
                "서울시 강남구 테헤란로 123",
                LocalDate.now().plusDays(3)
        );
        OrderPrice orderPrice = OrderPrice.create(Money.of(17000), Money.of(0), Money.of(3000));
        Discounts discounts = new Discounts(Money.of(0), Money.of(0), null);

        OrderProduct orderProduct = createTestOrderProduct();

        Order order = Order.createOrder(orderer, shippingInfo, orderPrice, discounts, List.of(orderProduct));

        order.completePayment();

        return order;
    }

    private OrderProduct createTestOrderProduct() {
        return OrderProduct.builder()
                .isbn("1235433121")
                .qty(1)
                .amount(Money.of(10000))
                .packagingRequest(false)
                .build();
    }
}
