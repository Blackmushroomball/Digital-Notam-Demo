$ErrorActionPreference = 'Stop'

function Get-TreeFingerprint([string]$Path) {
    $root = (Resolve-Path -LiteralPath $Path).Path
    $entries = Get-ChildItem -LiteralPath $Path -Recurse -File | Sort-Object FullName | ForEach-Object {
        $relative = $_.FullName.Substring($root.Length)
        $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash
        "$relative|$($_.Length)|$hash"
    }
    $bytes = [Text.Encoding]::UTF8.GetBytes(($entries -join "`n"))
    $sha = [Security.Cryptography.SHA256]::Create()
    return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '')
}

$expected = @{
    'utils\NOTAM-Production-Templates' = '70D945ADEBCC06F93B7A6680C810B958419C50DCF07B27C54671ADE6C75C73C8'
    'data\virtual data' = '8D6FCF68BF9147FA1D0C225943E25308F44EF86CFEFD6DA8C7ED4EA3A08D977B'
}

foreach ($entry in $expected.GetEnumerator()) {
    $actual = Get-TreeFingerprint $entry.Key
    if ($actual -ne $entry.Value) {
        throw "External resource changed: $($entry.Key)`nExpected: $($entry.Value)`nActual:   $actual"
    }
    Write-Host "OK $($entry.Key) $actual"
}
