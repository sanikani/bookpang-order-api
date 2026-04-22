package shop.sajotuna.order.stock.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import shop.sajotuna.order.stock.controller.request.PerfStockDecreaseRequest;
import shop.sajotuna.order.stock.service.StockService;
import shop.sajotuna.order.stock.service.dto.StockDeductionMode;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PerfStockController.class)
@ActiveProfiles("test")
class PerfStockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StockService stockService;

    @Test
    @DisplayName("벤치마크 재고 감소 요청 성공")
    void decreaseStock_success() throws Exception {
        PerfStockDecreaseRequest request =
                new PerfStockDecreaseRequest("HOT-ISBN-0001", 1, StockDeductionMode.HYBRID);

        mockMvc.perform(post("/api/perf/stocks/decrease")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(stockService).decreaseStock("HOT-ISBN-0001", 1, StockDeductionMode.HYBRID);
    }

    @Test
    @DisplayName("벤치마크 재고 감소 요청 검증 실패")
    void decreaseStock_invalidRequest() throws Exception {
        PerfStockDecreaseRequest request =
                new PerfStockDecreaseRequest("", 0, StockDeductionMode.OPTIMISTIC);

        mockMvc.perform(post("/api/perf/stocks/decrease")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(stockService, never()).decreaseStock(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
    }
}
