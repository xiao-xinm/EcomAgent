function Assert-NotificationSmokeWorkbenchSummary([object]$Summary) {
    if ($null -eq $Summary) {
        throw "PREFLIGHT_FAILED: Workbench outbox summary is empty"
    }
    if ($Summary.enabled -ne $true) {
        throw "PREFLIGHT_FAILED: Workbench outbox worker must be enabled"
    }
    if ($Summary.notificationEnabled -ne $true) {
        throw "PREFLIGHT_FAILED: Workbench Notification publishing must be enabled"
    }
    if ($Summary.userMessageDeliveryMode -ne "NOTIFICATION") {
        throw "PREFLIGHT_FAILED: Workbench user message delivery mode must be NOTIFICATION"
    }
}

function Assert-NotificationSmokeDeliverySummary([object]$Summary) {
    if ($null -eq $Summary) {
        throw "PREFLIGHT_FAILED: Notification delivery summary is empty"
    }
    if ($Summary.enabled -ne $true) {
        throw "PREFLIGHT_FAILED: Notification retry worker must be enabled"
    }
    if ($Summary.userSessionChannelEnabled -ne $true) {
        throw "PREFLIGHT_FAILED: Notification USER_SESSION channel must be enabled"
    }
}
