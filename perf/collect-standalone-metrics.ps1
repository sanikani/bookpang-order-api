$ErrorActionPreference = "Stop"

param(
    [string]$BaseUrl = "http://localhost:10366",
    [string]$OutputDir = ".\perf\results"
)

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"

$targets = @(
    @{ Name = "health"; Path = "/actuator/health" },
    @{ Name = "metrics-index"; Path = "/actuator/metrics" },
    @{ Name = "http-server-requests"; Path = "/actuator/metrics/http.server.requests" },
    @{ Name = "jvm-memory-used"; Path = "/actuator/metrics/jvm.memory.used" },
    @{ Name = "jvm-memory-max"; Path = "/actuator/metrics/jvm.memory.max" },
    @{ Name = "jvm-gc-pause"; Path = "/actuator/metrics/jvm.gc.pause" },
    @{ Name = "system-cpu-usage"; Path = "/actuator/metrics/system.cpu.usage" },
    @{ Name = "process-cpu-usage"; Path = "/actuator/metrics/process.cpu.usage" },
    @{ Name = "executor-active"; Path = "/actuator/metrics/executor.active" },
    @{ Name = "prometheus"; Path = "/actuator/prometheus" }
)

foreach ($target in $targets) {
    $destination = Join-Path $OutputDir "$timestamp-$($target.Name).txt"

    try {
        $content = Invoke-WebRequest -UseBasicParsing "$BaseUrl$($target.Path)"
        $content.Content | Set-Content -Path $destination
        Write-Output "SAVED $destination"
    } catch {
        $message = $_.Exception.Message
        Set-Content -Path $destination -Value $message
        Write-Output "FAILED $($target.Path) -> $message"
    }
}
