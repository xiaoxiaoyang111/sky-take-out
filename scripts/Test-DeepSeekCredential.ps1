function Test-DeepSeekCredential {
    param([Parameter(Mandatory=$true)][string]$ApiKey)
    $request = [Net.HttpWebRequest]::Create('https://api.deepseek.com/models')
    $request.Method = 'GET'
    $request.AllowAutoRedirect = $false
    $request.Timeout = 10000
    $request.ReadWriteTimeout = 10000
    $request.Headers['Authorization'] = 'Bearer ' + $ApiKey
    $response = $null
    try {
        $response = $request.GetResponse()
        if ([int]$response.StatusCode -ne 200) { throw 'Unexpected status.' }
    } catch {
        $cause = $_.Exception
        while ($cause.InnerException -and !($cause -is [Net.WebException])) { $cause = $cause.InnerException }
        $failedResponse = $cause.Response
        $status = if ($failedResponse) { [int]$failedResponse.StatusCode } else { 0 }
        if ($failedResponse) { $failedResponse.Dispose() }
        if ($status -eq 401) { throw 'DeepSeek returned 401. Create/copy a valid API Key from platform.deepseek.com; do not use a chat login token or a key from another provider.' }
        if ($status -eq 402) { throw 'DeepSeek account balance is insufficient. Check the API account balance.' }
        if ($status -eq 429) { throw 'DeepSeek rate limit reached. Wait before retrying.' }
        throw 'DeepSeek authentication preflight failed. Check network access and the API account; no running service was stopped.'
    } finally {
        if ($response) { $response.Dispose() }
    }
}

function ConvertTo-DeepSeekApiKey {
    param([AllowNull()][string]$Value)
    $normalized = if ($null -eq $Value) { '' } else { $Value.Trim() }
    if ($normalized.Length -ge 2 -and (($normalized.StartsWith('"') -and $normalized.EndsWith('"')) -or ($normalized.StartsWith("'") -and $normalized.EndsWith("'")))) {
        $normalized = $normalized.Substring(1, $normalized.Length - 2).Trim()
    }
    if (!$normalized -or $normalized -match '\s' -or $normalized.StartsWith('DEEPSEEK_API_KEY=')) { throw 'Paste only the API Key, without a command, Bearer prefix or whitespace.' }
    return $normalized
}
