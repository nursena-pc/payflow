#requires -Version 5.1

[CmdletBinding()]
param(
    [string] $AlertmanagerBaseUrl = "http://localhost:9093",

    [string] $MailpitBaseUrl = "http://localhost:8025",

    [ValidateRange(30, 300)]
    [int] $TimeoutSeconds = 120
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Assert-EndpointReady {
    param(
        [Parameter(Mandatory)]
        [string] $Uri,

        [Parameter(Mandatory)]
        [string] $Name
    )

    $response = Invoke-WebRequest `
        -UseBasicParsing `
        -Uri $Uri

    if ($response.StatusCode -ne 200) {
        throw "$Name returned HTTP $($response.StatusCode)."
    }
}

function Send-AlertmanagerAlert {
    param(
        [Parameter(Mandatory)]
        [hashtable] $Alert
    )

    $payload = ConvertTo-Json `
        -InputObject @($Alert) `
        -Depth 10 `
        -Compress

    if (-not $payload.TrimStart().StartsWith("[")) {
        throw "Alertmanager payload must be a top-level JSON array."
    }

    Invoke-RestMethod `
        -Method Post `
        -Uri "$AlertmanagerBaseUrl/api/v2/alerts" `
        -ContentType "application/json" `
        -Body (
            [System.Text.Encoding]::UTF8.GetBytes(
                $payload
            )
        ) |
        Out-Null
}

function Get-MailpitMessages {
    $response = Invoke-RestMethod `
        -Uri "$MailpitBaseUrl/api/v1/messages"

    return @($response.messages)
}

function Wait-ForMailpitMessage {
    param(
        [Parameter(Mandatory)]
        [string] $Subject,

        [Parameter(Mandatory)]
        [string] $ExpectedRecipient
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    do {
        $message = Get-MailpitMessages |
            Where-Object {
                $_.Subject -eq $Subject
            } |
            Select-Object -First 1

        if ($null -ne $message) {
            $recipients = @(
                $message.To |
                    ForEach-Object {
                        $_.Address
                    }
            )

            if ($recipients -notcontains $ExpectedRecipient) {
                throw (
                    "Message '$Subject' was delivered to an unexpected " +
                    "recipient: $($recipients -join ', ')."
                )
            }

            if (
                $message.From.Address -ne
                "alertmanager@payflow.local"
            ) {
                throw (
                    "Message '$Subject' has unexpected sender " +
                    "'$($message.From.Address)'."
                )
            }

            return $message
        }

        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    throw (
        "Mailpit did not receive '$Subject' within " +
        "$TimeoutSeconds seconds."
    )
}

function New-SyntheticAlert {
    param(
        [Parameter(Mandatory)]
        [string] $AlertName,

        [Parameter(Mandatory)]
        [string] $Severity,

        [Parameter(Mandatory)]
        [DateTimeOffset] $StartsAt,

        [Parameter(Mandatory)]
        [DateTimeOffset] $EndsAt,

        [Parameter(Mandatory)]
        [string] $Description
    )

    return @{
        labels = @{
            alertname = $AlertName
            severity = $Severity
            service = "payflow"
            component = "transactional-outbox"
        }
        annotations = @{
            summary = "Synthetic PayFlow notification smoke test"
            description = $Description
        }
        startsAt = $StartsAt.ToString("o")
        endsAt = $EndsAt.ToString("o")
        generatorURL = "http://localhost:9090/alerts"
    }
}

Assert-EndpointReady `
    -Uri "$AlertmanagerBaseUrl/-/ready" `
    -Name "Alertmanager"

Assert-EndpointReady `
    -Uri $MailpitBaseUrl `
    -Name "Mailpit"

$runId = [Guid]::NewGuid().ToString("N").Substring(0, 8)

$testCases = @(
    [PSCustomObject]@{
        Severity = "warning"
        SubjectSeverity = "WARNING"
        Recipient = "engineering@payflow.local"
        AlertName = "PayFlowSyntheticWarning-$runId"
        StartsAt = [DateTimeOffset]::UtcNow
    },
    [PSCustomObject]@{
        Severity = "critical"
        SubjectSeverity = "CRITICAL"
        Recipient = "oncall@payflow.local"
        AlertName = "PayFlowSyntheticCritical-$runId"
        StartsAt = [DateTimeOffset]::UtcNow
    }
)

Write-Host "Sending synthetic firing alerts..."

foreach ($testCase in $testCases) {
    $alert = New-SyntheticAlert `
        -AlertName $testCase.AlertName `
        -Severity $testCase.Severity `
        -StartsAt $testCase.StartsAt `
        -EndsAt $testCase.StartsAt.AddMinutes(10) `
        -Description (
            "Validates $($testCase.Severity) firing notification delivery."
        )

    Send-AlertmanagerAlert `
        -Alert $alert
}

$firingResults = foreach ($testCase in $testCases) {
    $subject = (
        "[PayFlow][$($testCase.SubjectSeverity)]" +
        "[firing] $($testCase.AlertName)"
    )

    $message = Wait-ForMailpitMessage `
        -Subject $subject `
        -ExpectedRecipient $testCase.Recipient

    [PSCustomObject]@{
        Severity = $testCase.Severity
        State = "firing"
        Subject = $message.Subject
        Recipient = $testCase.Recipient
    }
}

Write-Host "Sending synthetic resolved alerts..."

foreach ($testCase in $testCases) {
    $resolvedAt = [DateTimeOffset]::UtcNow

    $alert = New-SyntheticAlert `
        -AlertName $testCase.AlertName `
        -Severity $testCase.Severity `
        -StartsAt $testCase.StartsAt `
        -EndsAt $resolvedAt.AddSeconds(-1) `
        -Description (
            "Validates $($testCase.Severity) resolved notification delivery."
        )

    Send-AlertmanagerAlert `
        -Alert $alert
}

$resolvedResults = foreach ($testCase in $testCases) {
    $subject = (
        "[PayFlow][$($testCase.SubjectSeverity)]" +
        "[resolved] $($testCase.AlertName)"
    )

    $message = Wait-ForMailpitMessage `
        -Subject $subject `
        -ExpectedRecipient $testCase.Recipient

    [PSCustomObject]@{
        Severity = $testCase.Severity
        State = "resolved"
        Subject = $message.Subject
        Recipient = $testCase.Recipient
    }
}

@($firingResults) + @($resolvedResults) |
    Format-Table `
        Severity, `
        State, `
        Recipient, `
        Subject `
        -AutoSize

Write-Host "Alertmanager notification smoke test passed."
