param (
    [string]$BaseUrl = "http://localhost:8080",
    [int]$TotalRequests = 200,
    [int]$Concurrency = 10
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "      ENTERPRISE URL SHORTENER - BENCHMARK RUNNER        " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Target Base URL: $BaseUrl"
Write-Host "Total Requests:  $TotalRequests"
Write-Host "Concurrency:     $Concurrency"
Write-Host ""

# 1. Health check
try {
    $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -Method Get -TimeoutSec 5
    Write-Host "[OK] Service is healthy: $($health.status)" -ForegroundColor Green
} catch {
    Write-Error "Service is not reachable at $BaseUrl. Please ensure the application is running."
    exit 1
}

# 2. Create a test short link for hot-link benchmarking
$createBody = @{
    originalUrl = "https://example.com/benchmark/test-destination"
} | ConvertTo-Json

$created = Invoke-RestMethod -Uri "$BaseUrl/api/v1/urls" -Method Post -Body $createBody -ContentType "application/json"
$shortCode = $created.shortCode
Write-Host "[OK] Created benchmark link with code: $shortCode -> $($created.originalUrl)" -ForegroundColor Green

Add-Type -AssemblyName System.Net.Http

# 3. Warm-up phase
Write-Host "`nWarming up (20 requests)..." -ForegroundColor Yellow
$handler = New-Object System.Net.Http.HttpClientHandler
$handler.AllowAutoRedirect = $false
$client = New-Object System.Net.Http.HttpClient($handler)
$client.Timeout = [TimeSpan]::FromSeconds(5)

$targetRedirectUrl = "$BaseUrl/$shortCode"

1..20 | ForEach-Object {
    try {
        $r = $client.GetAsync($targetRedirectUrl).GetAwaiter().GetResult()
        $r.Dispose()
    } catch {}
}

# 4. Benchmark execution using RunspacePool for true multi-threaded concurrency
Write-Host "Executing benchmark ($TotalRequests requests, hot-link redirect)..." -ForegroundColor Yellow

$RunspacePool = [runspacefactory]::CreateRunspacePool(1, $Concurrency)
$RunspacePool.Open()

$ScriptBlock = {
    param($url)
    Add-Type -AssemblyName System.Net.Http
    $handler = New-Object System.Net.Http.HttpClientHandler
    $handler.AllowAutoRedirect = $false
    $client = New-Object System.Net.Http.HttpClient($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(5)

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $success = $false
    try {
        $resp = $client.GetAsync($url).GetAwaiter().GetResult()
        $status = [int]$resp.StatusCode
        $resp.Dispose()
        if ($status -eq 302) { $success = $true }
    } catch {
    } finally {
        $sw.Stop()
        $client.Dispose()
        $handler.Dispose()
    }

    [PSCustomObject]@{
        Success = $success
        Latency = $sw.Elapsed.TotalMilliseconds
    }
}

$tasks = [System.Collections.Generic.List[System.Management.Automation.PowerShell]]::new()
$asyncResults = [System.Collections.Generic.List[System.IAsyncResult]]::new()

$benchmarkTimer = [System.Diagnostics.Stopwatch]::StartNew()

for ($i = 0; $i -lt $TotalRequests; $i++) {
    $ps = [powershell]::Create()
    $ps.RunspacePool = $RunspacePool
    [void]$ps.AddScript($ScriptBlock).AddArgument($targetRedirectUrl)
    $tasks.Add($ps)
    $asyncResults.Add($ps.BeginInvoke())
}

$latencies = [System.Collections.Generic.List[double]]::new()
$errors = 0

for ($i = 0; $i -lt $TotalRequests; $i++) {
    $ps = $tasks[$i]
    $ar = $asyncResults[$i]
    $output = $ps.EndInvoke($ar)
    $ps.Dispose()

    if ($output.Success) {
        $latencies.Add($output.Latency)
    } else {
        $errors++
    }
}

$benchmarkTimer.Stop()
$RunspacePool.Close()
$RunspacePool.Dispose()
$client.Dispose()
$handler.Dispose()

$totalElapsedSec = $benchmarkTimer.Elapsed.TotalSeconds
$sorted = $latencies | Sort-Object
$count = $sorted.Count

if ($count -gt 0) {
    $p50Index = [Math]::Floor($count * 0.50)
    $p95Index = [Math]::Floor($count * 0.95)
    $p99Index = [Math]::Floor($count * 0.99)

    $p50 = [Math]::Round($sorted[$p50Index], 2)
    $p95 = [Math]::Round($sorted[$p95Index], 2)
    $p99 = [Math]::Round($sorted[$p99Index], 2)
    $avg = [Math]::Round(($latencies | Measure-Object -Average).Average, 2)
    $throughput = [Math]::Round($count / $totalElapsedSec, 2)
    $errorRate = [Math]::Round(($errors / $TotalRequests) * 100, 2)

    Write-Host "`n==========================================================" -ForegroundColor Cyan
    Write-Host "                   BENCHMARK RESULTS                      " -ForegroundColor Cyan
    Write-Host "==========================================================" -ForegroundColor Cyan
    Write-Host "Completed Requests: $count / $TotalRequests"
    Write-Host "Total Duration:     $([Math]::Round($totalElapsedSec, 2)) seconds"
    Write-Host "Throughput:         $throughput req/s" -ForegroundColor Green
    Write-Host "p50 Latency:        $p50 ms" -ForegroundColor Green
    Write-Host "p95 Latency:        $p95 ms" -ForegroundColor Green
    Write-Host "p99 Latency:        $p99 ms"
    Write-Host "Mean Latency:       $avg ms"
    Write-Host "Error Rate:         $errorRate % ($errors errors)"
    Write-Host "==========================================================" -ForegroundColor Cyan
} else {
    Write-Host "All requests failed!" -ForegroundColor Red
}
