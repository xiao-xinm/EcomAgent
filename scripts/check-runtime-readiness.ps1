[CmdletBinding()]
param(
    [string]$WorkbenchBaseUrl = "http://localhost:8083",
    [string]$KnowledgeBaseUrl = "http://localhost:8084",
    [string]$NotificationBaseUrl = "http://localhost:8085",
    [string]$OperatorId = "agent_001",
    [string]$Roles = "AGENT",
    [string]$SnapshotPath,
    [ValidateSet("KEYWORD_READY", "HYBRID_READY")]
    [string]$ExpectedKnowledgeState,
    [ValidateSet("DIRECT", "CUTOVER_READY", "ASYNC_BACKLOG", "ASYNC_ACTIVE")]
    [string]$ExpectedNotificationState
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$results = New-Object System.Collections.Generic.List[object]

function Add-ReadinessResult(
    [string]$Name,
    [bool]$Passed,
    [string]$State,
    [string]$Message
) {
    $results.Add([pscustomobject]@{
        Name = $Name
        Passed = $Passed
        State = $State
        Message = $Message
    }) | Out-Null
}

function Invoke-SmartCsGet([string]$Uri, [hashtable]$Headers = @{}) {
    $response = Invoke-RestMethod -Method Get -Uri $Uri -Headers $Headers -TimeoutSec 10
    if ($response.code -ne "0000") {
        throw "unexpected response code [$($response.code)]: $($response.message)"
    }
    if ($null -eq $response.data) {
        throw "response data is empty"
    }
    return $response.data
}

function Test-KnowledgeReadiness([object]$Summary) {
    if ($null -eq $Summary) {
        throw "knowledge summary is missing"
    }
    if (-not [bool]$Summary.healthy) {
        Add-ReadinessResult "knowledge" $false "DEPENDENCY_UNHEALTHY" "MySQL or the configured retrieval dependency is unavailable"
        return
    }
    if ([bool]$Summary.enabled -and -not [bool]$Summary.consistent) {
        Add-ReadinessResult "knowledge" $false "HYBRID_INCONSISTENT" "MySQL, Elasticsearch, and pgvector document counts are inconsistent"
        return
    }

    if ([bool]$Summary.enabled) {
        $indexCounts = @($Summary.indexes | ForEach-Object { "$($_.name)=$($_.documentCount)" }) -join ", "
        Add-ReadinessResult "knowledge" $true "HYBRID_READY" "source=$($Summary.source.documentCount), $indexCounts"
    } else {
        Add-ReadinessResult "knowledge" $true "KEYWORD_READY" "MySQL keyword retrieval is ready"
    }
}

function Test-NotificationReadiness([object]$Outbox, [object]$Delivery) {
    if ($null -eq $Outbox -or $null -eq $Delivery) {
        throw "notification summaries are missing"
    }

    $mode = [string]$Outbox.userMessageDeliveryMode
    if ($mode -notin @("DIRECT", "NOTIFICATION")) {
        Add-ReadinessResult "notification" $false "DELIVERY_MODE_INVALID" "unsupported user message delivery mode [$mode]"
        return
    }

    $configurationReady = [bool]$Outbox.enabled -and
        [bool]$Outbox.notificationEnabled -and
        [bool]$Delivery.enabled -and
        [bool]$Delivery.userSessionChannelEnabled
    $exhausted = [long]$Outbox.exhaustedFailed + [long]$Delivery.exhaustedFailed
    $outstanding = [long]$Outbox.pending +
        [long]$Outbox.retryableFailed +
        [long]$Outbox.leased +
        [long]$Delivery.accepted +
        [long]$Delivery.retryableFailed +
        [long]$Delivery.leased
    $due = [long]$Outbox.due + [long]$Delivery.due

    if ($mode -eq "NOTIFICATION" -and -not $configurationReady) {
        Add-ReadinessResult "notification" $false "ASYNC_CONFIG_INVALID" "direct write is disabled but the asynchronous delivery chain is incomplete"
        return
    }
    if ($exhausted -gt 0) {
        Add-ReadinessResult "notification" $false "RETRY_EXHAUSTED" "exhausted=$exhausted; manual recovery is required"
        return
    }
    if ($configurationReady -and ($outstanding -gt 0 -or $due -gt 0)) {
        Add-ReadinessResult "notification" $true "ASYNC_BACKLOG" "outstanding=$outstanding, due=$due; continue observing until drained"
        return
    }
    if ($mode -eq "NOTIFICATION") {
        Add-ReadinessResult "notification" $true "ASYNC_ACTIVE" "Notification owns user message delivery"
        return
    }
    if ($configurationReady) {
        Add-ReadinessResult "notification" $true "CUTOVER_READY" "the asynchronous chain is enabled and has no backlog"
        return
    }
    Add-ReadinessResult "notification" $true "DIRECT" "Workbench transactionally writes user messages"
}

$knowledgeSummary = $null
$outboxSummary = $null
$deliverySummary = $null

if (-not [string]::IsNullOrWhiteSpace($SnapshotPath)) {
    if (-not (Test-Path -LiteralPath $SnapshotPath -PathType Leaf)) {
        throw "snapshot file not found: $SnapshotPath"
    }
    $snapshot = Get-Content -LiteralPath $SnapshotPath -Encoding UTF8 -Raw | ConvertFrom-Json
    $knowledgeSummary = $snapshot.knowledge
    $outboxSummary = $snapshot.outbox
    $deliverySummary = $snapshot.delivery
} else {
    try {
        $knowledgeSummary = Invoke-SmartCsGet "$KnowledgeBaseUrl/api/knowledge/faq/index/status"
    } catch {
        Add-ReadinessResult "knowledge" $false "ENDPOINT_UNAVAILABLE" $_.Exception.Message
    }

    $headers = @{
        "X-SmartCS-Operator-Id" = $OperatorId
        "X-SmartCS-Roles" = $Roles
    }
    try {
        $outboxSummary = Invoke-SmartCsGet "$WorkbenchBaseUrl/api/workbench/notifications/outbox/summary" $headers
    } catch {
        Add-ReadinessResult "notification" $false "WORKBENCH_UNAVAILABLE" $_.Exception.Message
    }
    try {
        $deliverySummary = Invoke-SmartCsGet "$NotificationBaseUrl/api/notifications/events/delivery-summary"
    } catch {
        Add-ReadinessResult "notification" $false "NOTIFICATION_UNAVAILABLE" $_.Exception.Message
    }
}

if ($null -ne $knowledgeSummary) {
    Test-KnowledgeReadiness $knowledgeSummary
}
if ($null -ne $outboxSummary -and $null -ne $deliverySummary) {
    Test-NotificationReadiness $outboxSummary $deliverySummary
}

function Test-ExpectedState([string]$Name, [string]$ExpectedState) {
    if ([string]::IsNullOrWhiteSpace($ExpectedState)) {
        return
    }
    $actual = $results | Where-Object { $_.Name -eq $Name } | Select-Object -First 1
    if ($null -eq $actual -or $actual.State -ne $ExpectedState) {
        $actualState = if ($null -eq $actual) { "MISSING" } else { $actual.State }
        Add-ReadinessResult "$Name-expectation" $false "STATE_MISMATCH" "expected=$ExpectedState, actual=$actualState"
    }
}

Test-ExpectedState "knowledge" $ExpectedKnowledgeState
Test-ExpectedState "notification" $ExpectedNotificationState

foreach ($result in $results) {
    $prefix = if ($result.Passed) { "[ok]" } else { "[fail]" }
    $color = if ($result.Passed) { "Green" } else { "Red" }
    Write-Host ("{0} {1,-14} {2,-24} {3}" -f $prefix, $result.Name, $result.State, $result.Message) -ForegroundColor $color
}

$failed = @($results | Where-Object { -not $_.Passed })
if ($results.Count -lt 2) {
    Write-Host "[fail] runtime readiness checks are incomplete" -ForegroundColor Red
    exit 1
}
if ($failed.Count -gt 0) {
    Write-Host "[readiness] failed=$($failed.Count) total=$($results.Count)" -ForegroundColor Red
    exit 1
}

Write-Host "[readiness] runtime dependencies are ready"
