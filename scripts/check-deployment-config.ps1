param(
    [Parameter(Mandatory = $true)]
    [string]$Path
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$errors = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]

function Add-ValidationError([string]$Message) {
    $errors.Add($Message) | Out-Null
}

function Add-ValidationWarning([string]$Message) {
    $warnings.Add($Message) | Out-Null
}

function Test-Placeholder([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $true
    }
    return $Value -match '^<.+>$' -or $Value -match '^(CHANGE_ME|REPLACE_ME)$'
}

function Read-EnvironmentFile([string]$FilePath) {
    $values = @{}
    $lineNumber = 0

    foreach ($line in Get-Content -LiteralPath $FilePath -Encoding UTF8) {
        $lineNumber++
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith("#")) {
            continue
        }
        if ($line -notmatch '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
            Add-ValidationError "line $lineNumber is not a KEY=VALUE entry"
            continue
        }

        $name = $Matches[1]
        $value = $Matches[2].Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        if ($values.ContainsKey($name)) {
            Add-ValidationError "duplicate variable: $name"
            continue
        }
        $values[$name] = $value
    }

    return $values
}

function Require-Value([hashtable]$Values, [string]$Name) {
    if (-not $Values.ContainsKey($Name) -or (Test-Placeholder ([string]$Values[$Name]))) {
        Add-ValidationError "$Name must be configured with a non-placeholder value"
        return $null
    }
    return [string]$Values[$Name]
}

function Require-Boolean([hashtable]$Values, [string]$Name) {
    $value = Require-Value $Values $Name
    if ($null -eq $value) {
        return $null
    }
    if ($value -notmatch '^(?i:true|false)$') {
        Add-ValidationError "$Name must be true or false"
        return $null
    }
    return [System.Convert]::ToBoolean($value)
}

function Test-LocalEndpoint([string]$Value) {
    return $Value -match '(?i)(localhost|127\.0\.0\.1|0\.0\.0\.0)'
}

function Test-ServiceUrl([hashtable]$Values, [string]$Name) {
    $value = Require-Value $Values $Name
    if ($null -eq $value) {
        return
    }
    $uri = $null
    if (-not [System.Uri]::TryCreate($value, [System.UriKind]::Absolute, [ref]$uri) -or
        $uri.Scheme -notin @("http", "https")) {
        Add-ValidationError "$Name must be an absolute HTTP(S) URL"
    } elseif (Test-LocalEndpoint $value) {
        Add-ValidationError "$Name must not use a local development endpoint"
    }
}

if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    Write-Host "[config] file not found: $Path" -ForegroundColor Red
    exit 1
}

$resolvedPath = (Resolve-Path -LiteralPath $Path).Path
$values = Read-EnvironmentFile $resolvedPath

$dbUrl = Require-Value $values "SMARTCS_DB_URL"
if ($null -ne $dbUrl -and (Test-LocalEndpoint $dbUrl)) {
    Add-ValidationError "SMARTCS_DB_URL must not use a local development endpoint"
}
$dbUsername = Require-Value $values "SMARTCS_DB_USERNAME"
if ($null -ne $dbUsername -and $dbUsername -eq "root") {
    Add-ValidationError "SMARTCS_DB_USERNAME must not use the root account"
}
$dbPassword = Require-Value $values "SMARTCS_DB_PASSWORD"
if ($null -ne $dbPassword -and $dbPassword -match '^(?i:root|password|123456)$') {
    Add-ValidationError "SMARTCS_DB_PASSWORD uses a known development password"
}

$corsOrigins = Require-Value $values "SMARTCS_CORS_ALLOWED_ORIGINS"
if ($null -ne $corsOrigins) {
    foreach ($origin in $corsOrigins.Split(',')) {
        $trimmedOrigin = $origin.Trim()
        $originUri = $null
        if ($trimmedOrigin -eq "*" -or $trimmedOrigin.Contains("*")) {
            Add-ValidationError "SMARTCS_CORS_ALLOWED_ORIGINS must not contain wildcard origins"
        } elseif (-not [System.Uri]::TryCreate($trimmedOrigin, [System.UriKind]::Absolute, [ref]$originUri) -or
            $originUri.Scheme -ne "https") {
            Add-ValidationError "CORS origin must be an absolute HTTPS origin: $trimmedOrigin"
        } elseif (Test-LocalEndpoint $trimmedOrigin) {
            Add-ValidationError "CORS origin must not use a local development endpoint: $trimmedOrigin"
        }
    }
}

@(
    "SMARTCS_AGENT_CORE_BASE_URL",
    "SMARTCS_SKILL_ENGINE_BASE_URL",
    "SMARTCS_KNOWLEDGE_BASE_URL",
    "SMARTCS_NOTIFICATION_BASE_URL"
) | ForEach-Object { Test-ServiceUrl $values $_ }

$strictAuth = Require-Boolean $values "SMARTCS_AUTH_STRICT_ENABLED"
$jwtEnabled = Require-Boolean $values "SMARTCS_AUTH_JWT_ENABLED"
if ($strictAuth -ne $true) {
    Add-ValidationError "SMARTCS_AUTH_STRICT_ENABLED must be true for production"
}
if ($jwtEnabled -ne $true) {
    Add-ValidationError "SMARTCS_AUTH_JWT_ENABLED must be true for production"
}
$jwtSecret = Require-Value $values "SMARTCS_AUTH_JWT_SECRET"
if ($null -ne $jwtSecret -and $jwtSecret.Length -lt 32) {
    Add-ValidationError "SMARTCS_AUTH_JWT_SECRET must contain at least 32 characters"
}
Require-Value $values "SMARTCS_AUTH_JWT_ISSUER" | Out-Null
Require-Value $values "SMARTCS_AUTH_JWT_AUDIENCE" | Out-Null

$retrievalMode = Require-Value $values "SMARTCS_RETRIEVAL_MODE"
if ($null -ne $retrievalMode -and $retrievalMode -notmatch '^(?i:keyword|hybrid)$') {
    Add-ValidationError "SMARTCS_RETRIEVAL_MODE must be keyword or hybrid"
}
if ($retrievalMode -match '^(?i:hybrid)$') {
    @(
        "SMARTCS_VECTOR_DB_URL",
        "SMARTCS_VECTOR_DB_USERNAME",
        "SMARTCS_VECTOR_DB_PASSWORD",
        "SMARTCS_ES_URL",
        "SMARTCS_ES_USERNAME",
        "SMARTCS_ES_PASSWORD",
        "DASHSCOPE_API_KEY",
        "SMARTCS_EMBEDDING_MODEL",
        "SMARTCS_EMBEDDING_DIMENSIONS"
    ) | ForEach-Object { Require-Value $values $_ | Out-Null }

    foreach ($name in @("SMARTCS_VECTOR_DB_URL", "SMARTCS_ES_URL")) {
        if ($values.ContainsKey($name) -and (Test-LocalEndpoint ([string]$values[$name]))) {
            Add-ValidationError "$name must not use a local development endpoint"
        }
    }
}

$notificationEnabled = Require-Boolean $values "SMARTCS_NOTIFICATION_ENABLED"
$directWriteEnabled = Require-Boolean $values "SMARTCS_NOTIFICATION_USER_MESSAGE_DIRECT_WRITE_ENABLED"
$outboxEnabled = Require-Boolean $values "SMARTCS_NOTIFICATION_OUTBOX_ENABLED"
$retryEnabled = Require-Boolean $values "SMARTCS_NOTIFICATION_RETRY_ENABLED"
$userSessionEnabled = Require-Boolean $values "SMARTCS_NOTIFICATION_USER_SESSION_CHANNEL_ENABLED"

if ($notificationEnabled -ne $true) {
    Add-ValidationError "SMARTCS_NOTIFICATION_ENABLED must be true for production"
}
if ($outboxEnabled -eq $true -and $notificationEnabled -ne $true) {
    Add-ValidationError "outbox delivery requires SMARTCS_NOTIFICATION_ENABLED=true"
}
if ($retryEnabled -ne $userSessionEnabled) {
    Add-ValidationError "Notification retry and USER_SESSION channel must be enabled or disabled together"
}
if ($directWriteEnabled -eq $false) {
    if ($outboxEnabled -ne $true) {
        Add-ValidationError "disabling direct user-message writes requires SMARTCS_NOTIFICATION_OUTBOX_ENABLED=true"
    }
    if ($retryEnabled -ne $true -or $userSessionEnabled -ne $true) {
        Add-ValidationError "disabling direct user-message writes requires Notification retry and USER_SESSION channel"
    }
}
if ($directWriteEnabled -eq $true -and $outboxEnabled -eq $true) {
    Add-ValidationWarning "direct writes and outbox are both enabled; this is valid only during migration observation"
}

foreach ($warning in $warnings) {
    Write-Host "[warn] $warning" -ForegroundColor Yellow
}
foreach ($validationError in $errors) {
    Write-Host "[fail] $validationError" -ForegroundColor Red
}

if ($errors.Count -gt 0) {
    Write-Host "[config] deployment configuration rejected: errors=$($errors.Count) warnings=$($warnings.Count)" -ForegroundColor Red
    exit 1
}

Write-Host "[config] deployment configuration accepted: variables=$($values.Count) warnings=$($warnings.Count)"
exit 0
