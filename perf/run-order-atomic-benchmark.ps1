param(
    [string]$BaseUrl = $(if ($env:BASE_URL) { $env:BASE_URL } else { "http://localhost:10366" }),
    [string]$Duration = $(if ($env:DURATION) { $env:DURATION } else { "60s" }),
    [string]$ResultsDir = "perf/results/order-api-showcase",
    [string]$Scenario = $(if ($env:SCENARIO) { $env:SCENARIO } else { "order_skewed_conflict" }),
    [string]$HotShare = $(if ($env:SKEWED_HOT_SHARE) { $env:SKEWED_HOT_SHARE } else { "0.4" }),
    [int]$Rate = $(if ($env:RATE) { [int]$env:RATE } else { 180 }),
    [int]$PreAllocatedVUs = $(if ($env:PRE_ALLOCATED_VUS) { [int]$env:PRE_ALLOCATED_VUS } else { 120 }),
    [int]$MaxVUs = $(if ($env:MAX_VUS) { [int]$env:MAX_VUS } else { 300 }),
    [string[]]$Strategies = @("OPTIMISTIC", "PESSIMISTIC", "ATOMIC")
)

$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path $ResultsDir | Out-Null

$runs = foreach ($strategy in $Strategies) {
    @{ Strategy = $strategy.ToUpperInvariant(); Scenario = $Scenario }
}

function Wait-Health {
    param([string]$Url)

    $deadline = (Get-Date).AddSeconds(120)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing "$Url/actuator/health" -TimeoutSec 3
            if ($response.StatusCode -eq 200) {
                return
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    }

    throw "Application did not become healthy within 120 seconds."
}

function Stop-App {
    param([System.Diagnostics.Process]$Process)

    if ($null -ne $Process -and -not $Process.HasExited) {
        taskkill /PID $Process.Id /T /F | Out-Null
    }
}

function Start-App {
    param(
        [string]$Strategy,
        [string]$LogPath
    )

    $command = @"
`$env:SPRING_PROFILES_ACTIVE = 'benchmark'
`$env:STOCK_STRATEGY_DEFAULT_MODE = '$Strategy'
`$env:STOCK_RETRY_OPTIMISTIC_MAX_ATTEMPTS = '1'
.\mvnw.cmd spring-boot:run *> "$LogPath"
"@

    Start-Process -FilePath "powershell" -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $command) -WorkingDirectory (Get-Location).Path -WindowStyle Hidden -PassThru
}

foreach ($run in $runs) {
    $strategy = $run.Strategy
    $scenario = $run.Scenario
    $hotShareToken = $HotShare.Replace(".", "")
    $prefix = "order-$($strategy.ToLower())-$scenario-hot$hotShareToken-r$Rate"
    $summaryPath = Join-Path $ResultsDir "$prefix.json"
    $metricsPath = Join-Path $ResultsDir "$prefix.metrics.json"
    $logPath = Join-Path $ResultsDir "$prefix.app.log"

    Write-Host "==> Starting $strategy / $scenario / hotShare=$HotShare / rate=$Rate"
    $app = Start-App -Strategy $strategy -LogPath $logPath

    try {
        Wait-Health -Url $BaseUrl

        & .\perf\seed-order-benchmark.ps1

        & k6 run `
            --summary-export $summaryPath `
            -e BASE_URL=$BaseUrl `
            -e STRATEGY=$strategy `
            -e SCENARIO=$scenario `
            -e RATE=$Rate `
            -e DURATION=$Duration `
            -e SKEWED_HOT_SHARE=$HotShare `
            -e PRE_ALLOCATED_VUS=$PreAllocatedVUs `
            -e MAX_VUS=$MaxVUs `
            .\perf\order-create.js

        $k6ExitCode = $LASTEXITCODE

        $metrics = [ordered]@{
            strategy = $strategy
            scenario = $scenario
            hotShare = $HotShare
            rate = $Rate
            preAllocatedVUs = $PreAllocatedVUs
            maxVUs = $MaxVUs
            k6ExitCode = $k6ExitCode
            orderRetry = $null
            orderDuration = $null
            orderFailed = $null
            stockDuration = $null
        }

        foreach ($metricName in @(
            "order.process.retry.count",
            "order.process.duration",
            "order.process.failed.count",
            "stock.deduction.duration"
        )) {
            try {
                $metricResponse = Invoke-WebRequest -UseBasicParsing "$BaseUrl/actuator/metrics/$metricName" -TimeoutSec 5
                $metricContent = $metricResponse.Content
                if ($metricContent -is [byte[]]) {
                    $metricContent = [System.Text.Encoding]::UTF8.GetString($metricContent)
                }
                $metricJson = $metricContent | ConvertFrom-Json
                switch ($metricName) {
                    "order.process.retry.count" { $metrics.orderRetry = $metricJson }
                    "order.process.duration" { $metrics.orderDuration = $metricJson }
                    "order.process.failed.count" { $metrics.orderFailed = $metricJson }
                    "stock.deduction.duration" { $metrics.stockDuration = $metricJson }
                }
            } catch {
                Write-Host "Metric unavailable: $metricName"
            }
        }

        $metrics | ConvertTo-Json -Depth 20 | Set-Content -Encoding UTF8 $metricsPath
    } finally {
        Stop-App -Process $app
        Start-Sleep -Seconds 5
    }
}
