param (
    [string]$BaseUrl = "http://localhost:8080",
    [int]$TotalRequests = 500,
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

# 3. Warm-up phase
Write-Host "`nWarming up (20 requests)..." -ForegroundColor Yellow
1..20 | ForEach-Object {
    try {
        $wr = [System.Net.HttpWebRequest]::Create("$BaseUrl/$shortCode")
        $wr.AllowAutoRedirect = $false
        $wr.Method = "GET"
        $resp = $wr.GetResponse()
        $resp.Close()
    } catch {}
}

# 4. Benchmark execution
Write-Host "Executing benchmark ($TotalRequests requests, hot-link redirect)..." -ForegroundColor Yellow

$latencies = [System.Collections.Generic.List[double]]::new()
$errors = 0
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

$scriptBlock = {
    param($url)
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $req = [System.Net.HttpWebRequest]::Create($url)
        $req.AllowAutoRedirect = $false
        $req.Method = "GET"
        $req.Timeout = 5000
        $response = [System.Net.HttpWebResponse]$req.GetResponse()
        $sw.Stop()
        $status = [int]$response.StatusCode
        $response.Close()
        return @{ Success = ($status -eq 302); Latency = $sw.Elapsed.TotalMilliseconds }
    } catch {
        $sw.Stop()
        return @{ Success = $false; Latency = $sw.Elapsed.TotalMilliseconds }
    }
}

$targetRedirectUrl = "$BaseUrl/$shortCode"
$results = 1..$TotalRequests | ForEach-Object -Parallel $scriptBlock -ArgumentList $targetRedirectUrl -ThrottleLimit $Concurrency

$stopwatch.Stop()
$totalElapsedSec = $stopwatch.Elapsed.TotalSeconds

foreach ($res in $results) {
    if ($res.Success) {
        $latencies.Add($res.Latency)
    } else {
        $errors++
    }
}

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
