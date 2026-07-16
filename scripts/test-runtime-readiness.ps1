$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$checker = Join-Path $scriptRoot "check-runtime-readiness.ps1"
$shell = (Get-Process -Id $PID).Path
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("smartcs-runtime-readiness-test-" + [Guid]::NewGuid().ToString("N"))
$passed = 0

function New-Snapshot(
    [bool]$HybridEnabled = $false,
    [bool]$KnowledgeHealthy = $true,
    [bool]$KnowledgeConsistent = $true,
    [string]$DeliveryMode = "DIRECT",
    [bool]$AsyncConfigurationReady = $false,
    [int]$Outstanding = 0,
    [int]$Exhausted = 0
) {
    return @{
        knowledge = @{
            mode = if ($HybridEnabled) { "hybrid" } else { "keyword" }
            enabled = $HybridEnabled
            healthy = $KnowledgeHealthy
            consistent = $KnowledgeConsistent
            source = @{ name = "mysql"; available = $KnowledgeHealthy; documentCount = 7 }
            indexes = @(
                @{ name = "elasticsearch"; available = $KnowledgeHealthy; documentCount = 7 },
                @{ name = "pgvector"; available = $KnowledgeHealthy; documentCount = 7 }
            )
        }
        outbox = @{
            enabled = $AsyncConfigurationReady
            notificationEnabled = $AsyncConfigurationReady
            userMessageDeliveryMode = $DeliveryMode
            pending = $Outstanding
            retryableFailed = 0
            exhaustedFailed = $Exhausted
            sent = 10
            due = $Outstanding
            leased = 0
        }
        delivery = @{
            enabled = $AsyncConfigurationReady
            userSessionChannelEnabled = $AsyncConfigurationReady
            accepted = 0
            retryableFailed = 0
            exhaustedFailed = 0
            delivered = 10
            due = 0
            leased = 0
        }
    }
}

function Invoke-Checker([string]$Name, [hashtable]$Snapshot) {
    $snapshotPath = Join-Path $tempRoot "$Name.json"
    $Snapshot | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $snapshotPath -Encoding UTF8
    $output = & $shell -NoProfile -ExecutionPolicy Bypass -File $checker -SnapshotPath $snapshotPath 2>&1 | Out-String
    return [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Output = $output
    }
}

function Assert-Result(
    [string]$Name,
    [int]$ExpectedExitCode,
    [object]$Actual,
    [string[]]$ExpectedText
) {
    if ($Actual.ExitCode -ne $ExpectedExitCode) {
        throw "$Name expected exit code $ExpectedExitCode but got $($Actual.ExitCode). Output: $($Actual.Output)"
    }
    foreach ($text in $ExpectedText) {
        if ($Actual.Output -notmatch [Regex]::Escape($text)) {
            throw "$Name output did not contain [$text]. Output: $($Actual.Output)"
        }
    }
    $script:passed++
    Write-Host "[ok] $Name"
}

try {
    New-Item -ItemType Directory -Path $tempRoot | Out-Null

    Assert-Result `
        "keyword-direct" `
        0 `
        (Invoke-Checker "keyword-direct" (New-Snapshot)) `
        @("KEYWORD_READY", "DIRECT")

    Assert-Result `
        "hybrid-async" `
        0 `
        (Invoke-Checker "hybrid-async" (New-Snapshot -HybridEnabled $true -DeliveryMode "NOTIFICATION" -AsyncConfigurationReady $true)) `
        @("HYBRID_READY", "ASYNC_ACTIVE")

    Assert-Result `
        "hybrid-inconsistent" `
        1 `
        (Invoke-Checker "hybrid-inconsistent" (New-Snapshot -HybridEnabled $true -KnowledgeConsistent $false)) `
        @("HYBRID_INCONSISTENT")

    Assert-Result `
        "async-config-invalid" `
        1 `
        (Invoke-Checker "async-config-invalid" (New-Snapshot -DeliveryMode "NOTIFICATION")) `
        @("ASYNC_CONFIG_INVALID")

    Assert-Result `
        "retry-exhausted" `
        1 `
        (Invoke-Checker "retry-exhausted" (New-Snapshot -AsyncConfigurationReady $true -Exhausted 1)) `
        @("RETRY_EXHAUSTED")

    Assert-Result `
        "recoverable-backlog" `
        0 `
        (Invoke-Checker "recoverable-backlog" (New-Snapshot -DeliveryMode "NOTIFICATION" -AsyncConfigurationReady $true -Outstanding 2)) `
        @("ASYNC_BACKLOG", "runtime dependencies are ready")

    Write-Host "[test] runtime readiness checks passed=$passed"
} finally {
    $resolvedTemp = [System.IO.Path]::GetFullPath($tempRoot)
    $systemTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if ($resolvedTemp.StartsWith($systemTemp, [System.StringComparison]::OrdinalIgnoreCase) -and
        (Test-Path -LiteralPath $resolvedTemp)) {
        Remove-Item -LiteralPath $resolvedTemp -Recurse -Force
    }
}
