$baseUrl = $env:BASE_URL
if (-not $baseUrl) {
    $baseUrl = "http://localhost:10366"
}

$coldCount = if ($env:COLD_COUNT) { [int]$env:COLD_COUNT } else { 100 }
$hotCount = if ($env:HOT_COUNT) { [int]$env:HOT_COUNT } else { 5 }
$initialStock = if ($env:INITIAL_STOCK) { [int]$env:INITIAL_STOCK } else { 10000 }
$hotPrefix = if ($env:HOT_ISBN_PREFIX) { $env:HOT_ISBN_PREFIX } else { "HOT-ISBN-" }

$stocks = @()

for ($i = 1; $i -le $hotCount; $i++) {
    $stocks += @{
        isbn = ("{0}{1:D4}" -f $hotPrefix, $i)
        stock = $initialStock
    }
}

for ($i = 1; $i -le $coldCount; $i++) {
    $stocks += @{
        isbn = ("COLD-ISBN-{0:D4}" -f $i)
        stock = $initialStock
    }
}

Invoke-RestMethod `
    -Method Post `
    -Uri "$baseUrl/api/stocks/batch" `
    -ContentType "application/json" `
    -Body ($stocks | ConvertTo-Json)
