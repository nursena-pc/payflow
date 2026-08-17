Set-StrictMode -Version Latest

function ConvertFrom-PayFlowBase32 {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Value
    )

    $Alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
    $Normalized = $Value.Trim().TrimEnd('=').ToUpperInvariant()

    # Canonical Base32 is uppercase ASCII; keep validation case-sensitive.
    if (
        [string]::IsNullOrWhiteSpace($Normalized) -or
        $Normalized -cnotmatch '^[A-Z2-7]+$'
    ) {
        throw 'TOTP secret must be canonical Base32.'
    }

    $Bits = New-Object System.Text.StringBuilder

    foreach ($Character in $Normalized.ToCharArray()) {
        $Index = $Alphabet.IndexOf($Character)

        if ($Index -lt 0) {
            throw 'TOTP secret contains an invalid Base32 character.'
        }

        [void] $Bits.Append(
            [Convert]::ToString($Index, 2).PadLeft(5, '0')
        )
    }

    $Bytes = New-Object System.Collections.Generic.List[byte]

    for ($Offset = 0; $Offset + 8 -le $Bits.Length; $Offset += 8) {
        $Bytes.Add(
            [Convert]::ToByte(
                $Bits.ToString($Offset, 8),
                2
            )
        )
    }

    return $Bytes.ToArray()
}

function Wait-PayFlowStableTotpWindow {
    $Now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $SecondInWindow = $Now % 30

    if ($SecondInWindow -ge 27) {
        $Delay = (30 - $SecondInWindow) + 1
        Start-Sleep -Seconds $Delay
    }
}

function New-PayFlowTotpCode {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Base32Secret
    )

    $Key = ConvertFrom-PayFlowBase32 -Value $Base32Secret
    [UInt64] $Counter = [UInt64] [Math]::Floor(
        [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() / 30
    )

    [byte[]] $CounterBytes = New-Object byte[] 8

    for ($Index = 7; $Index -ge 0; $Index--) {
        $CounterBytes[$Index] = [byte] ($Counter -band 0xff)
        $Counter = $Counter -shr 8
    }

    $Hmac = [System.Security.Cryptography.HMACSHA1]::new($Key)

    try {
        $Hash = $Hmac.ComputeHash($CounterBytes)
    }
    finally {
        $Hmac.Dispose()
    }

    $DynamicOffset = $Hash[$Hash.Length - 1] -band 0x0f
    $BinaryCode =
        (($Hash[$DynamicOffset] -band 0x7f) -shl 24) -bor
        (($Hash[$DynamicOffset + 1] -band 0xff) -shl 16) -bor
        (($Hash[$DynamicOffset + 2] -band 0xff) -shl 8) -bor
        ($Hash[$DynamicOffset + 3] -band 0xff)

    $Code = $BinaryCode % 1000000
    return '{0:D6}' -f $Code
}
