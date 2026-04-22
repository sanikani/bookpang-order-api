package shop.sajotuna.order.stock.service.strategy;

import shop.sajotuna.order.stock.service.dto.StockDeductionMode;

public interface StockDeductionStrategy {

    StockDeductionMode getMode();

    default StockDeductionMode getMetricMode(String isbn) {
        return getMode();
    }

    void decrease(String isbn, int quantity);
}
