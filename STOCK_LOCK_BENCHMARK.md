# Stock Lock Benchmark

This guide compares stock deduction strategies on MySQL with a dedicated benchmark API.

## Scope

- Primary path: `POST /api/perf/stocks/decrease`
- Strategies: `OPTIMISTIC`, `PESSIMISTIC`, `ATOMIC`, `HYBRID`
- Primary metrics: TPS, avg, `p50`, `p95`, `p99`, failure rate, retry count, lock timeout count

## Start Dependencies

```powershell
docker compose up -d mysql rabbitmq
```

RabbitMQ is still required because the app context includes queue configuration.

## Start The App

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=benchmark"
```

## Seed Benchmark Stocks

```powershell
.\perf\seed-benchmark-stocks.ps1
```

Default seed creates:

- 5 hot ISBNs: `HOT-ISBN-0001` to `HOT-ISBN-0005`
- 100 cold ISBNs: `COLD-ISBN-0001` to `COLD-ISBN-0100`
- initial stock: `10000`

## Strategy Selection

The benchmark endpoint accepts this payload:

```json
{
  "isbn": "HOT-ISBN-0001",
  "quantity": 1,
  "strategy": "HYBRID"
}
```

Hybrid routing is driven by application config:

- default mode: `OPTIMISTIC`
- hot mode: `PESSIMISTIC`
- hot ISBNs: `HOT-ISBN-0001` to `HOT-ISBN-0005`
- optimistic retry policy: `max-attempts=5`, `delay=50ms`, `multiplier=2.0`

## Retry Tuning

You can tune optimistic retry only in the benchmark profile by editing [application-benchmark.yml](/C:/project/order/src/main/resources/application-benchmark.yml:1).

```yaml
stock:
  retry:
    optimistic:
      max-attempts: 3
      delay: 20
      multiplier: 1.5
```

Suggested retry presets for repeated `skewed_conflict` runs:

- baseline: `max-attempts=5`, `delay=50`, `multiplier=2.0`
- light retry: `max-attempts=3`, `delay=20`, `multiplier=1.5`
- balanced retry: `max-attempts=5`, `delay=100`, `multiplier=2.0`
- aggressive backoff: `max-attempts=7`, `delay=100`, `multiplier=2.5`

## Recommended Runs

Run each combination 3 times and compare the median.

### High TPS, Low Conflict

```powershell
k6 run -e STRATEGY=OPTIMISTIC -e SCENARIO=high_tps_low_conflict .\perf\stock-decrease.js
k6 run -e STRATEGY=PESSIMISTIC -e SCENARIO=high_tps_low_conflict .\perf\stock-decrease.js
k6 run -e STRATEGY=ATOMIC -e SCENARIO=high_tps_low_conflict .\perf\stock-decrease.js
k6 run -e STRATEGY=HYBRID -e SCENARIO=high_tps_low_conflict .\perf\stock-decrease.js
```

### Low TPS, High Conflict

```powershell
k6 run -e STRATEGY=OPTIMISTIC -e SCENARIO=low_tps_high_conflict .\perf\stock-decrease.js
k6 run -e STRATEGY=PESSIMISTIC -e SCENARIO=low_tps_high_conflict .\perf\stock-decrease.js
k6 run -e STRATEGY=ATOMIC -e SCENARIO=low_tps_high_conflict .\perf\stock-decrease.js
k6 run -e STRATEGY=HYBRID -e SCENARIO=low_tps_high_conflict .\perf\stock-decrease.js
```

### High TPS, High Conflict

```powershell
k6 run -e STRATEGY=OPTIMISTIC -e SCENARIO=high_tps_high_conflict .\perf\stock-decrease.js
k6 run -e STRATEGY=PESSIMISTIC -e SCENARIO=high_tps_high_conflict .\perf\stock-decrease.js
k6 run -e STRATEGY=ATOMIC -e SCENARIO=high_tps_high_conflict .\perf\stock-decrease.js
k6 run -e STRATEGY=HYBRID -e SCENARIO=high_tps_high_conflict .\perf\stock-decrease.js
```

### Mixed Hybrid Check

```powershell
k6 run -e STRATEGY=HYBRID -e SCENARIO=mixed_hybrid .\perf\stock-decrease.js
```

### Skewed Conflict

Use this to simulate real traffic where most requests are spread out but a few hot stocks absorb most of the contention.

```powershell
k6 run -e STRATEGY=OPTIMISTIC -e SCENARIO=skewed_conflict .\perf\stock-decrease.js
k6 run -e STRATEGY=PESSIMISTIC -e SCENARIO=skewed_conflict .\perf\stock-decrease.js
k6 run -e STRATEGY=HYBRID -e SCENARIO=skewed_conflict .\perf\stock-decrease.js
```

Defaults:

- hot ISBN count: `5`
- hot traffic share: `70%`
- cold ISBN count: `100`

Optional overrides:

```powershell
k6 run `
  -e STRATEGY=HYBRID `
  -e SCENARIO=skewed_conflict `
  -e HOT_COUNT=5 `
  -e SKEWED_HOT_SHARE=0.8 `
  -e COLD_COUNT=100 `
  .\perf\stock-decrease.js
```

## Observability

Check these metrics during each run:

```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:10366/actuator/metrics/stock.deduction.duration
Invoke-WebRequest -UseBasicParsing http://localhost:10366/actuator/metrics/stock.deduction.retry.count
Invoke-WebRequest -UseBasicParsing http://localhost:10366/actuator/metrics/stock.deduction.lock_timeout.count
Invoke-WebRequest -UseBasicParsing http://localhost:10366/actuator/metrics/stock.deduction.insufficient.count
Invoke-WebRequest -UseBasicParsing http://localhost:10366/actuator/prometheus
```

## Result Template

| Strategy | Scenario | Error Rate | Avg ms | P50 ms | P95 ms | P99 ms | TPS | Retry Count | Lock Timeout | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| optimistic | high_tps_low_conflict |  |  |  |  |  |  |  |  |  |
| pessimistic | high_tps_low_conflict |  |  |  |  |  |  |  |  |  |
| atomic | high_tps_low_conflict |  |  |  |  |  |  |  |  |  |
| hybrid | high_tps_low_conflict |  |  |  |  |  |  |  |  |  |
