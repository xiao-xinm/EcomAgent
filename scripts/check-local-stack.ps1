param(
    [string]$GatewayBaseUrl = "http://localhost:8080",
    [string]$AgentCoreBaseUrl = "http://localhost:8081",
    [string]$SkillEngineBaseUrl = "http://localhost:8082",
    [string]$WorkbenchBaseUrl = "http://localhost:8083",
    [string]$KnowledgeBaseUrl = "http://localhost:8084",
    [string]$NotificationBaseUrl = "http://localhost:8085",
    [string]$ClientH5BaseUrl = "http://localhost:3000",
    [string]$WorkstationBaseUrl = "http://localhost:3001",
    [string]$AppH5BaseUrl = "http://localhost:3002",
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$SkipAppH5
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$results = New-Object System.Collections.Generic.List[object]

function Write-Step([string]$Message) {
    Write-Host "[stack] $Message"
}

function Add-Result([string]$Name, [string]$Uri, [bool]$Passed, [int]$ElapsedMs, [string]$Message) {
    $results.Add([pscustomobject]@{
        Name = $Name
        Uri = $Uri
        Passed = $Passed
        ElapsedMs = $ElapsedMs
        Message = $Message
    }) | Out-Null
}

function Test-BackendHealth([string]$Name, [string]$BaseUrl) {
    $uri = "$BaseUrl/api/health"
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-RestMethod -Method Get -Uri $uri -TimeoutSec 5
        $watch.Stop()

        if ($response.code -ne "0000") {
            throw "unexpected response code [$($response.code)]"
        }
        if ($response.data.status -ne "UP") {
            throw "unexpected health status [$($response.data.status)]"
        }

        $serviceName = $response.data.serviceName
        Write-Host ("[ok]   {0,-16} {1} ({2} ms)" -f $Name, $serviceName, $watch.ElapsedMilliseconds)
        Add-Result $Name $uri $true $watch.ElapsedMilliseconds "OK"
    } catch {
        $watch.Stop()
        Write-Host ("[fail] {0,-16} {1} ({2} ms) {3}" -f $Name, $uri, $watch.ElapsedMilliseconds, $_.Exception.Message) -ForegroundColor Red
        Add-Result $Name $uri $false $watch.ElapsedMilliseconds $_.Exception.Message
    }
}

function Test-FrontendEntry([string]$Name, [string]$BaseUrl) {
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-WebRequest -Method Get -Uri $BaseUrl -TimeoutSec 5 -UseBasicParsing
        $watch.Stop()

        if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 400) {
            throw "unexpected HTTP status [$($response.StatusCode)]"
        }

        Write-Host ("[ok]   {0,-16} HTTP {1} ({2} ms)" -f $Name, $response.StatusCode, $watch.ElapsedMilliseconds)
        Add-Result $Name $BaseUrl $true $watch.ElapsedMilliseconds "OK"
    } catch {
        $watch.Stop()
        Write-Host ("[fail] {0,-16} {1} ({2} ms) {3}" -f $Name, $BaseUrl, $watch.ElapsedMilliseconds, $_.Exception.Message) -ForegroundColor Red
        Add-Result $Name $BaseUrl $false $watch.ElapsedMilliseconds $_.Exception.Message
    }
}

$backendServices = @(
    @{ Name = "gateway"; BaseUrl = $GatewayBaseUrl },
    @{ Name = "agent-core"; BaseUrl = $AgentCoreBaseUrl },
    @{ Name = "skill-engine"; BaseUrl = $SkillEngineBaseUrl },
    @{ Name = "workbench"; BaseUrl = $WorkbenchBaseUrl },
    @{ Name = "knowledge"; BaseUrl = $KnowledgeBaseUrl },
    @{ Name = "notification"; BaseUrl = $NotificationBaseUrl }
)

$frontendApps = @(
    @{ Name = "client-h5"; BaseUrl = $ClientH5BaseUrl },
    @{ Name = "workstation"; BaseUrl = $WorkstationBaseUrl }
)

if (-not $SkipAppH5) {
    $frontendApps += @{ Name = "app-h5"; BaseUrl = $AppH5BaseUrl }
}

if (-not $SkipBackend) {
    Write-Step "checking backend health endpoints"
    foreach ($service in $backendServices) {
        Test-BackendHealth $service.Name $service.BaseUrl
    }
}

if (-not $SkipFrontend) {
    Write-Step "checking frontend entry pages"
    foreach ($app in $frontendApps) {
        Test-FrontendEntry $app.Name $app.BaseUrl
    }
}

$failed = @($results | Where-Object { -not $_.Passed })
$passedCount = @($results | Where-Object { $_.Passed }).Count
$failedCount = $failed.Count

Write-Step "summary: passed=$passedCount failed=$failedCount"

if ($failedCount -gt 0) {
    Write-Host ""
    Write-Host "Failed checks:" -ForegroundColor Red
    $failed | ForEach-Object {
        Write-Host ("- {0}: {1} -> {2}" -f $_.Name, $_.Uri, $_.Message) -ForegroundColor Red
    }
    exit 1
}

Write-Step "local stack is ready"
