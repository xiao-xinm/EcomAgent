$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

. (Join-Path $PSScriptRoot "lib/notification-smoke-preflight.ps1")

$passed = 0

function Assert-Passes([string]$Name, [scriptblock]$Action) {
    & $Action
    $script:passed++
    Write-Host "[ok] $Name"
}

function Assert-Fails([string]$Name, [string]$ExpectedMessage, [scriptblock]$Action) {
    try {
        & $Action
        throw "$Name did not fail"
    } catch {
        if ($_.Exception.Message -notmatch [Regex]::Escape($ExpectedMessage)) {
            throw "$Name returned an unexpected error: $($_.Exception.Message)"
        }
    }
    $script:passed++
    Write-Host "[ok] $Name"
}

$validWorkbench = [pscustomobject]@{
    enabled = $true
    notificationEnabled = $true
    userMessageDeliveryMode = "NOTIFICATION"
}
$validNotification = [pscustomobject]@{
    enabled = $true
    userSessionChannelEnabled = $true
}

Assert-Passes "complete async configuration" {
    Assert-NotificationSmokeWorkbenchSummary $validWorkbench
    Assert-NotificationSmokeDeliverySummary $validNotification
}
Assert-Fails "disabled outbox rejected" "outbox worker must be enabled" {
    Assert-NotificationSmokeWorkbenchSummary ([pscustomobject]@{
        enabled = $false
        notificationEnabled = $true
        userMessageDeliveryMode = "NOTIFICATION"
    })
}
Assert-Fails "direct mode rejected" "delivery mode must be NOTIFICATION" {
    Assert-NotificationSmokeWorkbenchSummary ([pscustomobject]@{
        enabled = $true
        notificationEnabled = $true
        userMessageDeliveryMode = "DIRECT"
    })
}
Assert-Fails "disabled user session channel rejected" "USER_SESSION channel must be enabled" {
    Assert-NotificationSmokeDeliverySummary ([pscustomobject]@{
        enabled = $true
        userSessionChannelEnabled = $false
    })
}

Write-Host "[test] notification smoke preflight checks passed=$passed"
