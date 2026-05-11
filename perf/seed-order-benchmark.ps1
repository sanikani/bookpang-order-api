$baseUrl = $env:BASE_URL
if (-not $baseUrl) {
    $baseUrl = "http://localhost:10366"
}

$coldCount = if ($env:COLD_COUNT) { [int]$env:COLD_COUNT } else { 100 }
$hotCount = if ($env:HOT_COUNT) { [int]$env:HOT_COUNT } else { 5 }
$initialStock = if ($env:INITIAL_STOCK) { [int]$env:INITIAL_STOCK } else { 10000 }
$hotPrefix = if ($env:HOT_ISBN_PREFIX) { $env:HOT_ISBN_PREFIX } else { "HOT-ISBN-" }
$coldPrefix = if ($env:COLD_PREFIX) { $env:COLD_PREFIX } else { "COLD-ISBN-" }

$body = @{
    hotCount = $hotCount
    coldCount = $coldCount
    initialStock = $initialStock
    hotIsbnPrefix = $hotPrefix
    coldIsbnPrefix = $coldPrefix
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri "$baseUrl/api/perf/orders/seed" `
    -ContentType "application/json" `
    -Body $body
