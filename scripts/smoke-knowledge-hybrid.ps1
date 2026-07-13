[CmdletBinding()]
param(
    [string]$KnowledgeBaseUrl = "http://localhost:8084"
)

$ErrorActionPreference = "Stop"

function Invoke-JsonPost {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Uri,
        [Parameter(Mandatory = $true)]
        [object]$Body
    )

    $json = $Body | ConvertTo-Json -Depth 10 -Compress
    return Invoke-RestMethod `
        -Method Post `
        -Uri $Uri `
        -ContentType "application/json; charset=utf-8" `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($json))
}

$health = Invoke-RestMethod -Method Get -Uri "$KnowledgeBaseUrl/api/health"
if ($health.code -ne "0000" -or $health.data.status -ne "UP") {
    throw "Knowledge health check failed: $($health | ConvertTo-Json -Depth 10 -Compress)"
}

$rebuild = Invoke-RestMethod -Method Post -Uri "$KnowledgeBaseUrl/api/knowledge/faq/index/rebuild"
if ($rebuild.code -ne "0000" -or -not $rebuild.data.enabled) {
    throw "Hybrid index rebuild is disabled. Check SMARTCS_RETRIEVAL_MODE=hybrid."
}
if ($rebuild.data.failureCount -gt 0) {
    $operations = $rebuild.data.operations | ConvertTo-Json -Depth 10 -Compress
    throw "Hybrid index rebuild contains failures: $operations"
}

$indexStatus = Invoke-RestMethod -Method Get -Uri "$KnowledgeBaseUrl/api/knowledge/faq/index/status"
if ($indexStatus.code -ne "0000" -or -not $indexStatus.data.healthy -or -not $indexStatus.data.consistent) {
    throw "Hybrid index status is unhealthy or inconsistent: $($indexStatus | ConvertTo-Json -Depth 10 -Compress)"
}

$exactQuestion = -join @(
    [char]0x9000, [char]0x6B3E, [char]0x591A,
    [char]0x4E45, [char]0x5230, [char]0x8D26
)
$exact = Invoke-JsonPost `
    -Uri "$KnowledgeBaseUrl/api/knowledge/faq/query" `
    -Body @{
        traceId = "hybrid-smoke-exact"
        sessionId = "s_hybrid_smoke"
        userId = "u1001"
        channel = "h5"
        question = $exactQuestion
    }
if (-not $exact.data.matched -or $exact.data.source -ne "hybrid-rrf-v1") {
    throw "Exact FAQ was not confirmed by both retrievers: $($exact | ConvertTo-Json -Depth 10 -Compress)"
}

$semanticQuestion = -join @(
    [char]0x9000, [char]0x94B1, [char]0x4E00, [char]0x822C,
    [char]0x9700, [char]0x8981, [char]0x7B49, [char]0x5F85,
    [char]0x591A, [char]0x5C11, [char]0x5929
)
$semantic = Invoke-JsonPost `
    -Uri "$KnowledgeBaseUrl/api/knowledge/faq/query" `
    -Body @{
        traceId = "hybrid-smoke-semantic"
        sessionId = "s_hybrid_smoke"
        userId = "u1001"
        channel = "h5"
        question = $semanticQuestion
    }
$semanticSources = @("hybrid-rrf-v1", "pgvector-cosine-v1")
if (-not $semantic.data.matched -or $semantic.data.source -notin $semanticSources) {
    throw "Semantic FAQ did not use vector retrieval: $($semantic | ConvertTo-Json -Depth 10 -Compress)"
}

[PSCustomObject]@{
    Health = $health.data.status
    IndexedDocuments = $rebuild.data.documentCount
    IndexOperations = $rebuild.data.successCount
    IndexHealthy = $indexStatus.data.healthy
    IndexConsistent = $indexStatus.data.consistent
    ExactSource = $exact.data.source
    ExactConfidence = $exact.data.confidence
    SemanticSource = $semantic.data.source
    SemanticConfidence = $semantic.data.confidence
} | Format-List
