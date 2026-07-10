param(
    [string]$GatewayBaseUrl = "http://localhost:8080",
    [string]$AgentCoreBaseUrl = "http://localhost:8081",
    [string]$SkillEngineBaseUrl = "http://localhost:8082",
    [string]$WorkbenchBaseUrl = "http://localhost:8083",
    [string]$KnowledgeBaseUrl = "http://localhost:8084",
    [string]$NotificationBaseUrl = "http://localhost:8085",
    [string]$UserId = "u1001",
    [string]$OtherUserId = "u9999",
    [string]$Channel = "h5",
    [string]$MySqlDatabase = "smartcs_agent",
    [string]$MySqlUser = "root",
    [string]$MySqlPassword = "root",
    [switch]$SkipOrderCancelDbSetup
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Write-Step([string]$Message) {
    Write-Host "[smoke] $Message"
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

function Invoke-JsonPost([string]$Uri, [hashtable]$Body) {
    $json = $Body | ConvertTo-Json -Depth 20
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    Invoke-RestMethod `
        -Method Post `
        -Uri $Uri `
        -Headers (New-CustomerIdentityHeaders $UserId) `
        -ContentType "application/json; charset=utf-8" `
        -Body $bytes `
        -TimeoutSec 15
}

function Invoke-JsonGet([string]$Uri, [string]$IdentityUserId = $UserId) {
    Invoke-RestMethod `
        -Method Get `
        -Uri $Uri `
        -Headers (New-CustomerIdentityHeaders $IdentityUserId) `
        -TimeoutSec 15
}

function New-CustomerIdentityHeaders([string]$IdentityUserId) {
    @{
        "X-SmartCS-User-Id" = $IdentityUserId
        "X-SmartCS-Roles" = "CUSTOMER"
    }
}

function Invoke-MySql([string]$Sql) {
    $Sql | mysql "-u$MySqlUser" "-p$MySqlPassword" --default-character-set=utf8mb4 $MySqlDatabase
}

function Test-Health([string]$Name, [string]$BaseUrl) {
    $response = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/health" -TimeoutSec 5
    Assert-Equal $response.code "0000" "$Name health code"
    Write-Step "$Name health OK"
}

function ConvertFrom-CodePoint([int[]]$CodePoints) {
    -join ($CodePoints | ForEach-Object { [char]$_ })
}

$TextFaqRefundTime = ConvertFrom-CodePoint @(0x9000, 0x6B3E, 0x591A, 0x4E45, 0x5230, 0x8D26)
$TextMyOrders = ConvertFrom-CodePoint @(0x6211, 0x7684, 0x8BA2, 0x5355)
$TextMyLogistics = ConvertFrom-CodePoint @(0x6211, 0x7684, 0x7269, 0x6D41, 0x5230, 0x54EA, 0x4E86)
$TextCancelOrderPrefix = ConvertFrom-CodePoint @(0x6211, 0x8981, 0x53D6, 0x6D88, 0x8BA2, 0x5355)
$TextConfirmCancelOrder = ConvertFrom-CodePoint @(0x786E, 0x8BA4, 0x53D6, 0x6D88, 0x8BA2, 0x5355)
$TextRefundApply = ConvertFrom-CodePoint @(0x6211, 0x8981, 0x9000, 0x6B3E)
$TextHumanAgent = ConvertFrom-CodePoint @(0x4EBA, 0x5DE5, 0x5BA2, 0x670D)

function Send-Chat([string]$Content) {
    Invoke-JsonPost "$GatewayBaseUrl/api/chat/messages" @{
        userId = $UserId
        channel = $Channel
        content = $Content
    }
}

function Send-Action([string]$SessionId, [object]$Action, [string]$Content) {
    Invoke-JsonPost "$GatewayBaseUrl/api/chat/actions" @{
        sessionId = $SessionId
        userId = $UserId
        channel = $Channel
        actionId = $Action.value
        actionType = $Action.actionType
        content = $Content
        payload = $Action.payload
    }
}

function Find-Action([object[]]$Actions, [string]$ActionType) {
    $action = $Actions | Where-Object { $_.actionType -eq $ActionType } | Select-Object -First 1
    Assert-True ($null -ne $action) "missing quick action $ActionType"
    $action
}

Write-Step "checking service health"
Test-Health "gateway" $GatewayBaseUrl
Test-Health "agent-core" $AgentCoreBaseUrl
Test-Health "skill-engine" $SkillEngineBaseUrl
Test-Health "workbench" $WorkbenchBaseUrl
Test-Health "knowledge" $KnowledgeBaseUrl
Test-Health "notification" $NotificationBaseUrl

Write-Step "checking FAQ auto reply"
$faq = Send-Chat $TextFaqRefundTime
Assert-Equal $faq.data.routeDecision "AUTO_REPLY" "FAQ route decision"
Assert-Equal $faq.data.metadata.intent "faq.query" "FAQ intent"
Assert-True (-not [string]::IsNullOrWhiteSpace($faq.data.metadata.knowledgeAnswerId)) "FAQ knowledgeAnswerId"

Write-Step "checking order query"
$order = Send-Chat $TextMyOrders
Assert-Equal $order.data.routeDecision "AUTO_REPLY" "order query route decision"
Assert-Equal $order.data.metadata.intent "order.query" "order query intent"
Assert-True (-not [string]::IsNullOrWhiteSpace($order.data.metadata.skillExecutionId)) "order query skillExecutionId"

Write-Step "checking session ownership guard"
$messages = Invoke-JsonGet "$GatewayBaseUrl/api/chat/sessions/$($order.data.sessionId)/messages?limit=100" $UserId
Assert-Equal $messages.code "0000" "owned session messages code"
$forbiddenMessages = Invoke-JsonGet "$GatewayBaseUrl/api/chat/sessions/$($order.data.sessionId)/messages?limit=100" $OtherUserId
Assert-Equal $forbiddenMessages.code "1003" "cross-user session messages code"

Write-Step "checking logistics query"
$logistics = Send-Chat $TextMyLogistics
Assert-Equal $logistics.data.routeDecision "AUTO_REPLY" "logistics route decision"
Assert-Equal $logistics.data.metadata.intent "logistics.query" "logistics intent"
Assert-True (-not [string]::IsNullOrWhiteSpace($logistics.data.metadata.skillExecutionId)) "logistics skillExecutionId"

if (-not $SkipOrderCancelDbSetup) {
    Write-Step "checking order cancel confirmation"
    $orderId = "smoke_cancel_" + (Get-Date -Format "yyyyMMddHHmmss")
    $orderNo = "SMOKE-CANCEL-" + (Get-Date -Format "yyyyMMddHHmmss")
    Invoke-MySql "DELETE FROM ecom_order WHERE order_id = '$orderId'; INSERT INTO ecom_order (order_id, order_no, user_id, order_status, pay_status, logistics_status, total_amount, paid_amount, currency, can_modify_address) VALUES ('$orderId', '$orderNo', '$UserId', 'PAID', 'PAID', 'WAITING_SHIP', 12.34, 12.34, 'CNY', 1);" | Out-Null
    try {
        $cancel = Send-Chat "$TextCancelOrderPrefix $orderNo"
        Assert-Equal $cancel.data.routeDecision "CONFIRM_BEFORE_EXECUTE" "order cancel initial route decision"
        Assert-Equal $cancel.data.metadata.intent "order.cancel" "order cancel intent"
        Assert-Equal $cancel.data.metadata.orderNo $orderNo "order cancel metadata orderNo"
        $confirmAction = Find-Action $cancel.data.quickActions "CONFIRM"
        Assert-Equal $confirmAction.payload.orderNo $orderNo "order cancel quick action orderNo"

        $confirmed = Send-Action $cancel.data.sessionId $confirmAction $TextConfirmCancelOrder
        Assert-Equal $confirmed.data.routeDecision "AUTO_EXECUTE" "order cancel confirmed route decision"
        Assert-Equal $confirmed.data.metadata.intent "order.cancel" "order cancel confirmed intent"
        Assert-True (-not [string]::IsNullOrWhiteSpace($confirmed.data.metadata.skillExecutionId)) "order cancel skillExecutionId"

        $status = Invoke-MySql "SELECT order_status FROM ecom_order WHERE order_id = '$orderId';"
        Assert-True (($status -join "`n") -match "CANCELLED") "order cancel database status"
    } finally {
        Invoke-MySql "DELETE FROM ecom_order WHERE order_id = '$orderId';" | Out-Null
    }
}

Write-Step "checking refund human review"
$refund = Send-Chat $TextRefundApply
Assert-Equal $refund.data.routeDecision "HUMAN_REVIEW" "refund route decision"
Assert-Equal $refund.data.metadata.intent "refund.apply" "refund intent"
Assert-True (-not [string]::IsNullOrWhiteSpace($refund.data.ticketId)) "refund ticketId"

Write-Step "checking human takeover"
$takeover = Send-Chat $TextHumanAgent
Assert-Equal $takeover.data.routeDecision "HUMAN_TAKEOVER" "takeover route decision"
Assert-True (-not [string]::IsNullOrWhiteSpace($takeover.data.ticketId)) "takeover ticketId"

Write-Step "all smoke checks passed"
