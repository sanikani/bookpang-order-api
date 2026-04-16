# Order API Performance Measurement

This guide measures the local `standalone` order creation flow with H2 and local RabbitMQ.

## Scope

- Primary path: `POST /api/orders`
- Service chain: `OrderProcessController -> OrderProcessService -> OrderProcessTransactionalService`
- Async follow-up checks: point earn, coupon issue, order expiration consumers
- Excluded: Feign external calls, Toss real payment, Eureka, Config Server

## Prerequisites

- Start RabbitMQ locally
- Ensure vhost `sajotuna` exists
- Ensure user `guest` / password `guest` can access that vhost
- Use the `standalone` profile
- Keep the machine state stable during measurement

## Start The App

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=standalone"
```

## Verify Observability Endpoints

Check these endpoints before load generation:

```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:10366/actuator/health
Invoke-WebRequest -UseBasicParsing http://localhost:10366/actuator/metrics
Invoke-WebRequest -UseBasicParsing http://localhost:10366/actuator/metrics/http.server.requests
Invoke-WebRequest -UseBasicParsing http://localhost:10366/actuator/prometheus
```

Target metrics:

- API: average, `p50`, `p95`, `p99`, TPS, error rate
- JVM: CPU, heap usage, GC pause, thread or executor usage
- MQ: queue depth, publish/ack behavior, backlog duration
- DB: query count trend, slow SQL candidates, transaction delay

## Prepare Test Data

Order creation needs at least one packaging record and one stock record.

1. Create a package:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:10366/api/admin/packages/package `
  -ContentType "application/json" `
  -Body '{"packaging":"Default Box","price":500}'
```

2. Create stock for a test ISBN:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:10366/api/stocks `
  -ContentType "application/json" `
  -Body '{"isbn":"9788901234567","stock":10000}'
```

3. Confirm the package list:

```powershell
Invoke-RestMethod http://localhost:10366/api/orders/package
```

4. Keep the returned package `id` and use it as `orderPackagingId`

Sample request body is in [`perf/sample-order-create.json`](C:\project\nhn-order-portpolio\perf\sample-order-create.json).

## Baseline Measurement

Run a low-rate baseline first.

```powershell
k6 run .\perf\order-create.js
```

Recommended baseline:

- 30 to 50 total requests
- About 1 request per second
- Record mean, `p50`, `p95`, `p99`, error rate

After the run, collect actuator snapshots:

```powershell
.\perf\collect-standalone-metrics.ps1
```

## Load Measurement

Run each stage three times and record the median of the three runs.

```powershell
k6 run -e STAGE_PROFILE=load5 .\perf\order-create.js
k6 run -e STAGE_PROFILE=load10 .\perf\order-create.js
k6 run -e STAGE_PROFILE=load20 .\perf\order-create.js
k6 run -e STAGE_PROFILE=load50 .\perf\order-create.js
```

Recommended environment variables:

```powershell
$env:ORDER_PACKAGING_ID="1"
$env:ISBN="9788901234567"
$env:USER_ID="100"
$env:AMOUNT="15000"
$env:QUANTITY="1"
```

Optional override:

```powershell
k6 run `
  -e BASE_URL=http://localhost:10366 `
  -e ORDER_PACKAGING_ID=1 `
  -e ISBN=9788901234567 `
  -e USER_ID=100 `
  -e STAGE_PROFILE=load20 `
  .\perf\order-create.js
```

## Stock Conflict Measurement

Use the same ISBN and a smaller stock pool to expose optimistic lock retry cost.

1. Reset stock to a tight value:

```powershell
Invoke-RestMethod `
  -Method Put `
  -Uri http://localhost:10366/api/stocks `
  -ContentType "application/json" `
  -Body '{"isbn":"9788901234567","quantity":100}'
```

2. Run the conflict profile:

```powershell
k6 run -e STAGE_PROFILE=stock_conflict .\perf\order-create.js
```

3. Compare `p95`, error rate, and throughput against the baseline

Interpretation guide:

- Fast API but growing RabbitMQ backlog: consumer bottleneck
- Rising `p95` with low CPU: DB or lock contention
- More failures or long tails on the same ISBN: stock retry cost

## MQ Follow-up Checks

During or right after each run, inspect RabbitMQ queues:

- `sajotuna.point.queue`
- `sajotuna.coupon.queue`
- `sajotuna.order.dlq`
- related DLQ and parking lot queues

Check:

- queue depth returns to zero
- no unexpected DLQ growth
- consumer lag clears within an acceptable time

## Result Template

Record each run in a table like this:

| Stage | Requests | Error Rate | Avg ms | P50 ms | P95 ms | P99 ms | TPS | CPU | Heap | MQ Backlog | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| baseline-1 |  |  |  |  |  |  |  |  |  |  |  |
| load5-1 |  |  |  |  |  |  |  |  |  |  |  |
| load10-1 |  |  |  |  |  |  |  |  |  |  |  |
| load20-1 |  |  |  |  |  |  |  |  |  |  |  |
| load50-1 |  |  |  |  |  |  |  |  |  |  |  |
| stock-conflict-1 |  |  |  |  |  |  |  |  |  |  |  |

## Next Step Criteria

Move to code-level timing or custom Micrometer timers only if one of these is true:

- actuator metrics are too coarse to isolate the bottleneck
- `p95` or `p99` grows sharply but root cause is unclear
- MQ backlog grows without a clear producer or consumer explanation
- retry cost in stock conflict runs needs explicit counters
