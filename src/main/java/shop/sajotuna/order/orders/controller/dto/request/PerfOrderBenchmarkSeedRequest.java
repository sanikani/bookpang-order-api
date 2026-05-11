package shop.sajotuna.order.orders.controller.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PerfOrderBenchmarkSeedRequest {

    @Min(1)
    private int hotCount = 5;

    @Min(1)
    private int coldCount = 100;

    @Min(1)
    private int initialStock = 10_000;

    private String hotIsbnPrefix = "HOT-ISBN-";

    private String coldIsbnPrefix = "COLD-ISBN-";

    public PerfOrderBenchmarkSeedRequest(
            int hotCount,
            int coldCount,
            int initialStock,
            String hotIsbnPrefix,
            String coldIsbnPrefix
    ) {
        this.hotCount = hotCount;
        this.coldCount = coldCount;
        this.initialStock = initialStock;
        this.hotIsbnPrefix = normalizePrefix(hotIsbnPrefix, "HOT-ISBN-");
        this.coldIsbnPrefix = normalizePrefix(coldIsbnPrefix, "COLD-ISBN-");
    }

    public String getHotIsbnPrefix() {
        return normalizePrefix(hotIsbnPrefix, "HOT-ISBN-");
    }

    public String getColdIsbnPrefix() {
        return normalizePrefix(coldIsbnPrefix, "COLD-ISBN-");
    }

    private String normalizePrefix(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
