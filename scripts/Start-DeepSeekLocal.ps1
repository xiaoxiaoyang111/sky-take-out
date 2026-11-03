[CmdletBinding()]
param([switch]$Build, [switch]$PromptForKey)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Get-IdeaRedisPassword.ps1')
. (Join-Path $PSScriptRoot 'Test-DeepSeekCredential.ps1')
$previousKey = $env:DEEPSEEK_API_KEY
$previousRedis = $env:SKY_REDIS_PASSWORD
$secretInput = $null
try {
    # Validate local XML before asking for a key or stopping the running site.
    if (!$env:SKY_REDIS_PASSWORD) {
        $ideaPath = Join-Path (Split-Path $PSScriptRoot -Parent) '.idea\workspace.xml'
        $env:SKY_REDIS_PASSWORD = Get-IdeaRedisPassword -Path $ideaPath
    }
    if ($PromptForKey -or (!$env:DEEPSEEK_API_KEY -and !$env:RENTAL_ASSISTANT_API_KEY)) {
        $secretInput = Read-Host 'Paste your DeepSeek API Key (hidden; do not send it in chat)' -AsSecureString
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secretInput)
        try { $env:DEEPSEEK_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    }
    if ([string]::IsNullOrWhiteSpace($env:DEEPSEEK_API_KEY) -and [string]::IsNullOrWhiteSpace($env:RENTAL_ASSISTANT_API_KEY)) { throw 'API Key cannot be empty.' }
    $candidate = if ($env:DEEPSEEK_API_KEY) { $env:DEEPSEEK_API_KEY } else { $env:RENTAL_ASSISTANT_API_KEY }
    $env:DEEPSEEK_API_KEY = ConvertTo-DeepSeekApiKey -Value $candidate
    Test-DeepSeekCredential -ApiKey $env:DEEPSEEK_API_KEY
    Write-Output 'DeepSeek authentication preflight passed.'
    & (Join-Path $PSScriptRoot 'Stop-RentalLocal.ps1')
    & (Join-Path $PSScriptRoot 'Start-RentalLocal.ps1') -DeepSeek -UseIdeaRedisConfiguration -Build:$Build
    Write-Output 'DeepSeek configuration loaded. Real connectivity is verified only after a successful model reply.'
} finally {
    $env:DEEPSEEK_API_KEY = $previousKey
    $env:SKY_REDIS_PASSWORD = $previousRedis
    if ($secretInput) { $secretInput.Dispose() }
}
