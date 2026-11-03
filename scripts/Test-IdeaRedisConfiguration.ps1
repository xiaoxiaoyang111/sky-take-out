[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Get-IdeaRedisPassword.ps1')
$fixture = Join-Path ([IO.Path]::GetTempPath()) ('rental-xml-' + [guid]::NewGuid().ToString('N') + '.xml')
try {
    $chinese = [string][char]0x83DC + [char]0x54C1
    $xml = '<?xml version="1.0" encoding="UTF-8"?><project><task summary="' + $chinese + '"/><configuration name="Other"><env name="SKY_REDIS_PASSWORD" value="wrong-fixture"/></configuration><configuration name="SkyApplication"><env name="SKY_REDIS_PASSWORD" value="test&amp;only"/></configuration></project>'
    [IO.File]::WriteAllText($fixture, $xml, [Text.UTF8Encoding]::new($false))
    if ((Get-IdeaRedisPassword -Path $fixture) -ne 'test&only') { throw 'UTF-8 parsing or configuration selection failed.' }
    [IO.File]::WriteAllText($fixture, $xml, [Text.UTF8Encoding]::new($true))
    if ((Get-IdeaRedisPassword -Path $fixture) -ne 'test&only') { throw 'UTF-8 BOM parsing failed.' }
    [IO.File]::WriteAllText($fixture, '<project><env value="synthetic-sensitive-value"/><invalid></project>', [Text.UTF8Encoding]::new($false))
    $failed = $false
    try { $null = Get-IdeaRedisPassword -Path $fixture }
    catch { $failed = $true; if ($_.Exception.Message.Contains('synthetic-sensitive-value') -or $_.Exception.Message.Contains('<project>')) { throw 'Parser failure exposed XML content.' } }
    if (!$failed) { throw 'Malformed XML was accepted.' }
    [IO.File]::WriteAllText($fixture, '<project/>', [Text.UTF8Encoding]::new($false))
    if ($null -ne (Get-IdeaRedisPassword -Path $fixture)) { throw 'Missing configuration should be empty.' }
    Write-Output ('PASS: UTF-8, BOM, exact run configuration, XML entities, malformed XML redaction and missing configuration. PowerShell ' + $PSVersionTable.PSVersion)
} finally {
    if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture }
}
