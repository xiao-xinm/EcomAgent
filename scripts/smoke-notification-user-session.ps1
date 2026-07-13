param(
    [ValidateSet("Full", "PrepareRecovery", "VerifyRecovery", "Cleanup")]
    [string]$Mode = "Full",
    [string]$WorkbenchBaseUrl = "http://localhost:8083",
    [string]$NotificationBaseUrl = "http://localhost:8085",
    [string]$MySqlDatabase = "smartcs_agent",
    [string]$MySqlUser = "root",
    [string]$MySqlPassword = "root",
    [string]$StateFile = (Join-Path $env:TEMP "smartcs-notification-user-session-smoke.json"),
    [int]$TimeoutSeconds = 45,
    [switch]$KeepData
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Write-Step([string]$Message) {
    Write-Host "[notification-smoke] $Message"
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw "ASSERT_FAILED: $Message"
    }
}

function Assert-Equal([object]$Actual, [object]$Expected, [string]$Message) {
    if ($Actual -ne $Expected) {
        throw "ASSERT_FAILED: $Message. expected=[$Expected], actual=[$Actual]"
    }
}

function Escape-Sql([string]$Value) {
    if ($null -eq $Value) {
        return ""
    }
    $Value.Replace("'", "''")
}

function Invoke-MySql([string]$Sql) {
    $previousPassword = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $MySqlPassword
        $result = $Sql | mysql `
            "-u$MySqlUser" `
            --default-character-set=utf8mb4 `
            --batch `
            --skip-column-names `
            $MySqlDatabase
        if ($LASTEXITCODE -ne 0) {
            throw "mysql command failed with exit code $LASTEXITCODE"
        }
        @($result)
    } finally {
        $env:MYSQL_PWD = $previousPassword
    }
}

function Invoke-MySqlScalar([string]$Sql) {
    $rows = @(Invoke-MySql $Sql)
    if ($rows.Count -eq 0) {
        return $null
    }
    $rows[0].ToString().Trim()
}

function Test-ServiceHealth([string]$BaseUrl) {
    try {
        $response = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/health" -TimeoutSec 3
        $response.code -eq "0000" -and $response.data.status -eq "UP"
    } catch {
        $false
    }
}

function Invoke-WorkbenchPost([string]$Uri, [hashtable]$Body) {
    $json = $Body | ConvertTo-Json -Depth 20
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    Invoke-RestMethod `
        -Method Post `
        -Uri $Uri `
        -Headers @{
            "X-SmartCS-Operator-Id" = "agent_notification_e2e"
            "X-SmartCS-Roles" = "AGENT"
        } `
        -ContentType "application/json; charset=utf-8" `
        -Body $bytes `
        -TimeoutSec 15
}

function New-SmokeState {
    $suffix = (Get-Date -Format "yyMMddHHmmss") + (Get-Random -Minimum 1000 -Maximum 9999)
    [pscustomobject]@{
        RunId = $suffix
        SessionId = "s_ntf_e2e_$suffix"
        TraceId = "trace_ntf_e2e_$suffix"
        UserId = "u_ntf_e2e_$suffix"
        TicketId = "wo_ntf_e2e_$suffix"
        TakeoverId = "ht_ntf_e2e_$suffix"
        Content = "notification user-session e2e $suffix"
        EventId = $null
    }
}

function Save-State([object]$State) {
    $directory = Split-Path -Parent $StateFile
    if ($directory -and -not (Test-Path -LiteralPath $directory)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }
    $State | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $StateFile -Encoding UTF8
}

function Load-State {
    Assert-True (Test-Path -LiteralPath $StateFile) "state file does not exist: $StateFile"
    Get-Content -LiteralPath $StateFile -Raw -Encoding UTF8 | ConvertFrom-Json
}

function New-Fixture([object]$State) {
    $sessionId = Escape-Sql $State.SessionId
    $traceId = Escape-Sql $State.TraceId
    $userId = Escape-Sql $State.UserId
    $ticketId = Escape-Sql $State.TicketId
    $takeoverId = Escape-Sql $State.TakeoverId
    Invoke-MySql @"
INSERT INTO cs_session (
    session_id, trace_id, user_id, channel, status, dialog_state, current_intent
) VALUES (
    '$sessionId', '$traceId', '$userId', 'h5', 'HUMAN_TAKEOVER', 'HUMAN_TAKEOVER', 'human.takeover'
);
INSERT INTO work_order (
    ticket_id, trace_id, session_id, user_id, intent, risk_level,
    route_decision, status, priority, assigned_agent, reason
) VALUES (
    '$ticketId', '$traceId', '$sessionId', '$userId', 'human.takeover', 'L2',
    'HUMAN_TAKEOVER', 'PROCESSING', 'NORMAL', 'agent_notification_e2e', 'notification user-session e2e'
);
INSERT INTO human_takeover (
    takeover_id, ticket_id, trace_id, session_id, user_id, trigger_source,
    status, priority, assigned_agent, reason, started_at
) VALUES (
    '$takeoverId', '$ticketId', '$traceId', '$sessionId', '$userId', 'USER_REQUEST',
    'IN_PROGRESS', 'NORMAL', 'agent_notification_e2e', 'notification user-session e2e', CURRENT_TIMESTAMP(3)
);
"@ | Out-Null
    Save-State $State
    Write-Step "fixture created ticketId=$($State.TicketId)"
}

function Send-TakeoverMessage([object]$State) {
    $response = Invoke-WorkbenchPost `
        "$WorkbenchBaseUrl/api/workbench/tickets/$($State.TicketId)/takeover/messages" `
        @{
            operatorId = "agent_notification_e2e"
            content = $State.Content
            payload = @{ source = "notification-user-session-smoke"; runId = $State.RunId }
        }
    Assert-Equal $response.code "0000" "workbench takeover message response code"

    $ticketId = Escape-Sql $State.TicketId
    $eventId = Invoke-MySqlScalar "SELECT event_id FROM workbench_notification_outbox WHERE ticket_id = '$ticketId' AND event_type = 'TAKEOVER_MESSAGE_SENT' ORDER BY created_at DESC LIMIT 1;"
    Assert-True (-not [string]::IsNullOrWhiteSpace($eventId)) "outbox event was not created"
    $State.EventId = $eventId
    Save-State $State

    $deliveryMode = Invoke-MySqlScalar "SELECT JSON_UNQUOTE(JSON_EXTRACT(event_payload, '$.payload.userMessageDeliveryMode')) FROM workbench_notification_outbox WHERE event_id = '$(Escape-Sql $eventId)';"
    Assert-Equal $deliveryMode "NOTIFICATION" "outbox user message delivery mode"
    Write-Step "takeover message accepted eventId=$eventId"
}

function Wait-Until([scriptblock]$Condition, [string]$Description) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        if (& $Condition) {
            return
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "TIMEOUT: $Description after $TimeoutSeconds seconds"
}

function Assert-NoUserMessage([object]$State) {
    $count = Invoke-MySqlScalar "SELECT COUNT(*) FROM cs_message WHERE session_id = '$(Escape-Sql $State.SessionId)' AND content = '$(Escape-Sql $State.Content)';"
    Assert-Equal $count "0" "Workbench must not directly write the migrated user message"
}

function Verify-Delivery([object]$State) {
    $eventId = Escape-Sql $State.EventId
    $sessionId = Escape-Sql $State.SessionId
    $content = Escape-Sql $State.Content

    Wait-Until {
        (Invoke-MySqlScalar "SELECT status FROM notification_event WHERE event_id = '$eventId';") -eq "DELIVERED"
    } "notification event delivery"
    Assert-Equal (Invoke-MySqlScalar "SELECT status FROM workbench_notification_outbox WHERE event_id = '$eventId';") "SENT" "outbox status"
    Assert-Equal (Invoke-MySqlScalar "SELECT COUNT(*) FROM cs_message WHERE session_id = '$sessionId' AND content = '$content';") "1" "delivered message count"
    Assert-Equal (Invoke-MySqlScalar "SELECT role FROM cs_message WHERE session_id = '$sessionId' AND content = '$content' LIMIT 1;") "HUMAN_AGENT" "delivered message role"
    Assert-Equal (Invoke-MySqlScalar "SELECT JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.source')) FROM cs_message WHERE session_id = '$sessionId' AND content = '$content' LIMIT 1;") "notification" "delivered message source"
    Write-Step "event delivered and exactly one user message exists"
}

function Replay-Event([object]$State) {
    $eventId = Escape-Sql $State.EventId
    $sessionId = Escape-Sql $State.SessionId
    $content = Escape-Sql $State.Content
    Invoke-MySql "UPDATE notification_event SET status = 'ACCEPTED', retry_count = 0, last_error = NULL, next_retry_at = NULL, delivered_at = NULL WHERE event_id = '$eventId';" | Out-Null
    Wait-Until {
        (Invoke-MySqlScalar "SELECT status FROM notification_event WHERE event_id = '$eventId';") -eq "DELIVERED"
    } "replayed notification event delivery"
    Assert-Equal (Invoke-MySqlScalar "SELECT COUNT(*) FROM cs_message WHERE session_id = '$sessionId' AND content = '$content';") "1" "idempotent message count after replay"
    Write-Step "duplicate replay kept the message count at one"
}

function Remove-Fixture([object]$State) {
    $ticketId = Escape-Sql $State.TicketId
    $sessionId = Escape-Sql $State.SessionId
    Invoke-MySql @"
DELETE FROM notification_event WHERE ticket_id = '$ticketId';
DELETE FROM workbench_notification_outbox WHERE ticket_id = '$ticketId';
DELETE FROM audit_log WHERE ticket_id = '$ticketId';
DELETE FROM work_order_action WHERE ticket_id = '$ticketId';
DELETE FROM human_takeover WHERE ticket_id = '$ticketId';
DELETE FROM cs_message WHERE session_id = '$sessionId';
DELETE FROM work_order WHERE ticket_id = '$ticketId';
DELETE FROM cs_session WHERE session_id = '$sessionId';
"@ | Out-Null
    if (Test-Path -LiteralPath $StateFile) {
        Remove-Item -LiteralPath $StateFile -Force
    }
    Write-Step "fixture cleaned ticketId=$($State.TicketId)"
}

if ($Mode -eq "Cleanup") {
    Remove-Fixture (Load-State)
    exit 0
}

if ($Mode -eq "PrepareRecovery") {
    Assert-True (Test-ServiceHealth $WorkbenchBaseUrl) "Workbench must be running"
    Assert-True (-not (Test-ServiceHealth $NotificationBaseUrl)) "Notification must be stopped for recovery preparation"
    $state = New-SmokeState
    try {
        New-Fixture $state
        Send-TakeoverMessage $state
        Start-Sleep -Seconds 2
        Assert-NoUserMessage $state
        $outboxStatus = Invoke-MySqlScalar "SELECT status FROM workbench_notification_outbox WHERE event_id = '$(Escape-Sql $state.EventId)';"
        Assert-True ($outboxStatus -in @("PENDING", "FAILED")) "outbox should remain recoverable while Notification is unavailable"
        Write-Step "recovery prepared; start Notification and run with -Mode VerifyRecovery"
    } catch {
        Write-Step "preparation failed; state retained at $StateFile"
        throw
    }
    exit 0
}

if ($Mode -eq "VerifyRecovery") {
    Assert-True (Test-ServiceHealth $WorkbenchBaseUrl) "Workbench must be running"
    Assert-True (Test-ServiceHealth $NotificationBaseUrl) "Notification must be running"
    $state = Load-State
    try {
        # 仅加速本次烟测事件，避免等待默认的一分钟首次退避。
        Invoke-MySql "UPDATE workbench_notification_outbox SET next_attempt_at = CURRENT_TIMESTAMP(3) WHERE event_id = '$(Escape-Sql $state.EventId)' AND status = 'FAILED';" | Out-Null
        Verify-Delivery $state
        Replay-Event $state
    } finally {
        if (-not $KeepData) {
            Remove-Fixture $state
        }
    }
    Write-Step "recovery smoke passed"
    exit 0
}

Assert-True (Test-ServiceHealth $WorkbenchBaseUrl) "Workbench must be running"
Assert-True (Test-ServiceHealth $NotificationBaseUrl) "Notification must be running"
$state = New-SmokeState
try {
    New-Fixture $state
    Send-TakeoverMessage $state
    Verify-Delivery $state
    Replay-Event $state
} finally {
    if (-not $KeepData) {
        Remove-Fixture $state
    }
}
Write-Step "full smoke passed"
