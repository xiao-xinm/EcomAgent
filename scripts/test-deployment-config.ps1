$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$validator = Join-Path $scriptRoot "check-deployment-config.ps1"
$shell = (Get-Process -Id $PID).Path
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("smartcs-deployment-config-test-" + [Guid]::NewGuid().ToString("N"))
$passed = 0

function Invoke-Validator([string]$ConfigPath) {
    $output = & $shell -NoProfile -ExecutionPolicy Bypass -File $validator -Path $ConfigPath 2>&1 | Out-String
    return [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Output = $output
    }
}

function Assert-Result(
    [string]$Name,
    [int]$ExpectedExitCode,
    [object]$Actual,
    [string]$ExpectedText
) {
    if ($Actual.ExitCode -ne $ExpectedExitCode) {
        throw "$Name expected exit code $ExpectedExitCode but got $($Actual.ExitCode). Output: $($Actual.Output)"
    }
    if ($Actual.Output -notmatch [Regex]::Escape($ExpectedText)) {
        throw "$Name output did not contain [$ExpectedText]. Output: $($Actual.Output)"
    }
    $script:passed++
    Write-Host "[ok] $Name"
}

$validConfig = @"
SMARTCS_DB_URL=jdbc:mysql://mysql.internal:3306/smartcs_agent
SMARTCS_DB_USERNAME=smartcs_app
SMARTCS_DB_PASSWORD=a-long-random-database-secret
SMARTCS_CORS_ALLOWED_ORIGINS=https://customer.example.com,https://workstation.example.com
SMARTCS_AGENT_CORE_BASE_URL=http://smartcs-agent-core:8081
SMARTCS_SKILL_ENGINE_BASE_URL=http://smartcs-skill-engine:8082
SMARTCS_KNOWLEDGE_BASE_URL=http://smartcs-knowledge:8084
SMARTCS_NOTIFICATION_BASE_URL=http://smartcs-notification:8085
SMARTCS_AUTH_STRICT_ENABLED=true
SMARTCS_AUTH_JWT_ENABLED=true
SMARTCS_AUTH_JWT_SECRET=0123456789abcdef0123456789abcdef
SMARTCS_AUTH_JWT_ISSUER=https://identity.example.com
SMARTCS_AUTH_JWT_AUDIENCE=smartcs
SMARTCS_RETRIEVAL_MODE=keyword
SMARTCS_NOTIFICATION_ENABLED=true
SMARTCS_NOTIFICATION_USER_MESSAGE_DIRECT_WRITE_ENABLED=true
SMARTCS_NOTIFICATION_OUTBOX_ENABLED=false
SMARTCS_NOTIFICATION_RETRY_ENABLED=false
SMARTCS_NOTIFICATION_USER_SESSION_CHANNEL_ENABLED=false
"@

try {
    New-Item -ItemType Directory -Path $tempRoot | Out-Null

    $validPath = Join-Path $tempRoot "valid.env"
    Set-Content -LiteralPath $validPath -Value $validConfig -Encoding UTF8
    Assert-Result "secure synchronous profile" 0 (Invoke-Validator $validPath) "configuration accepted"

    $developmentPath = Join-Path $tempRoot "development.env"
    $developmentConfig = $validConfig `
        -replace 'jdbc:mysql://mysql\.internal', 'jdbc:mysql://localhost' `
        -replace 'SMARTCS_DB_USERNAME=smartcs_app', 'SMARTCS_DB_USERNAME=root' `
        -replace 'SMARTCS_DB_PASSWORD=a-long-random-database-secret', 'SMARTCS_DB_PASSWORD=root' `
        -replace 'https://customer\.example\.com,https://workstation\.example\.com', 'http://localhost:3000' `
        -replace 'SMARTCS_AUTH_STRICT_ENABLED=true', 'SMARTCS_AUTH_STRICT_ENABLED=false'
    Set-Content -LiteralPath $developmentPath -Value $developmentConfig -Encoding UTF8
    Assert-Result "development defaults rejected" 1 (Invoke-Validator $developmentPath) "must not use the root account"

    $partialAsyncPath = Join-Path $tempRoot "partial-async.env"
    $partialAsyncConfig = $validConfig -replace 'SMARTCS_NOTIFICATION_USER_MESSAGE_DIRECT_WRITE_ENABLED=true', 'SMARTCS_NOTIFICATION_USER_MESSAGE_DIRECT_WRITE_ENABLED=false'
    Set-Content -LiteralPath $partialAsyncPath -Value $partialAsyncConfig -Encoding UTF8
    Assert-Result "partial async profile rejected" 1 (Invoke-Validator $partialAsyncPath) "requires SMARTCS_NOTIFICATION_OUTBOX_ENABLED=true"

    $partialHybridPath = Join-Path $tempRoot "partial-hybrid.env"
    $partialHybridConfig = $validConfig -replace 'SMARTCS_RETRIEVAL_MODE=keyword', 'SMARTCS_RETRIEVAL_MODE=hybrid'
    Set-Content -LiteralPath $partialHybridPath -Value $partialHybridConfig -Encoding UTF8
    Assert-Result "partial hybrid profile rejected" 1 (Invoke-Validator $partialHybridPath) "SMARTCS_VECTOR_DB_URL must be configured"

    Write-Host "[test] deployment config checks passed=$passed"
} finally {
    $resolvedTemp = [System.IO.Path]::GetFullPath($tempRoot)
    $systemTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if ($resolvedTemp.StartsWith($systemTemp, [System.StringComparison]::OrdinalIgnoreCase) -and
        (Test-Path -LiteralPath $resolvedTemp)) {
        Remove-Item -LiteralPath $resolvedTemp -Recurse -Force
    }
}
