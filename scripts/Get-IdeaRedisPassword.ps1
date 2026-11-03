function Get-IdeaRedisPassword {
    param([Parameter(Mandatory=$true)][string]$Path)
    if (!(Test-Path -LiteralPath $Path)) { return $null }
    $reader = $null
    try {
        # XmlReader honors the XML declaration/BOM, including on Windows PowerShell 5.1.
        $settings = New-Object System.Xml.XmlReaderSettings
        $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
        $settings.XmlResolver = $null
        $reader = [System.Xml.XmlReader]::Create($Path, $settings)
        $workspace = New-Object System.Xml.XmlDocument
        $workspace.XmlResolver = $null
        $workspace.Load($reader)
        $redisNode = $workspace.SelectSingleNode('//configuration[@name="SkyApplication"]//env[@name="SKY_REDIS_PASSWORD"]')
        if ($redisNode) { return $redisNode.GetAttribute('value') }
        return $null
    } catch {
        # Never include the XML or underlying parser exception, which can contain credentials.
        throw 'Cannot read IDEA Redis configuration. Set SKY_REDIS_PASSWORD in this terminal, or check the local workspace XML encoding/format.'
    } finally {
        if ($reader) { $reader.Dispose() }
    }
}
