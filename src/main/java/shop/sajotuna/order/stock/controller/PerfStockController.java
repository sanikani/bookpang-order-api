package shop.sajotuna.order.stock.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shop.sajotuna.order.stock.controller.request.PerfStockDecreaseRequest;
import shop.sajotuna.order.stock.service.StockService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/perf/stocks")
public class PerfStockController {

    private final StockService stockService;

    @PostMapping("/decrease")
    public ResponseEntity<Void> decreaseStock(@RequestBody @Valid PerfStockDecreaseRequest request) {
        stockService.decreaseStock(request.getIsbn(), request.getQuantity(), request.getStrategy());
        return ResponseEntity.ok().build();
    }
}
