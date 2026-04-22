package shop.sajotuna.order.stock.controller.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import shop.sajotuna.order.stock.service.dto.StockDeductionMode;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PerfStockDecreaseRequest {

    @NotBlank
    private String isbn;

    @Min(1)
    private int quantity;

    private StockDeductionMode strategy;
}
