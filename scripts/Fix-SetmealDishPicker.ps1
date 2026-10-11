[CmdletBinding()]
param(
    [string]$FrontendRoot = 'C:\AAAAA\One_Study\frontend\sky-takeout\nginx-1.20.2\html\sky'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Include the visible search results when resolving selected dish names.
$original = 'this.checkedListAll.reverse();var e=this.allDishList.filter('
$replacement = 'this.checkedListAll.reverse();var e=this.allDishList.concat(this.dishList).filter('
$bundleName = 'shopTable.fe534d8f.js'
$bundlePath = Join-Path -Path $FrontendRoot -ChildPath ('js\' + $bundleName)
if (-not (Test-Path -LiteralPath $bundlePath -PathType Leaf)) {
    throw "Expected frontend bundle was not found: $bundlePath"
}
$bundlePath = (Resolve-Path -LiteralPath $bundlePath).Path
$utf8 = [System.Text.UTF8Encoding]::new($false, $true)
$content = [System.IO.File]::ReadAllText($bundlePath, $utf8)
$previousBundleContent = $content
$originalCount = [regex]::Matches($content, [regex]::Escape($original)).Count
$replacementCount = [regex]::Matches($content, [regex]::Escape($replacement)).Count

if ($originalCount -eq 0 -and $replacementCount -eq 1) {
    Write-Output "Dish selection logic already fixed: $bundlePath"
} elseif ($originalCount -ne 1 -or $replacementCount -ne 0) {
    throw "Unrecognized frontend content; expected exactly one original match and no patched match, found $originalCount and $replacementCount. No changes made."
}

$repositoryRoot = Split-Path -Path $PSScriptRoot -Parent
$backupDirectory = Join-Path -Path $repositoryRoot -ChildPath 'sky-server\target\frontend-backup'
[System.IO.Directory]::CreateDirectory($backupDirectory) | Out-Null
$backupPath = Join-Path -Path $backupDirectory -ChildPath ($bundleName + '.bak')
if (-not (Test-Path -LiteralPath $backupPath)) {
    # Never replace the first backup, including if another invocation created it.
    [System.IO.File]::Copy($bundlePath, $backupPath, $false)
}

# Adding a new dish must not reset portions already entered for existing dishes.
$copiesOriginal = 'this.dishTable.forEach((function(t){t.copies=1}))'
$copiesReplacement = 'this.dishTable.forEach((function(t){t.copies>0||(t.copies=1)}))'
$copiesOriginalCount = [regex]::Matches($content, [regex]::Escape($copiesOriginal)).Count
$copiesReplacementCount = [regex]::Matches($content, [regex]::Escape($copiesReplacement)).Count
if ($copiesOriginalCount -eq 1 -and $copiesReplacementCount -eq 0) {
    $content = $content.Replace($copiesOriginal, $copiesReplacement)
} elseif ($copiesOriginalCount -ne 0 -or $copiesReplacementCount -ne 1) {
    throw 'Unrecognized dish portion initialization; no changes made.'
}

$bytes = [System.IO.File]::ReadAllBytes($bundlePath)
$hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
$outputEncoding = [System.Text.UTF8Encoding]::new($hasBom)

# Publish new resource names so browsers cannot reuse the original cached chunks.
# The supplied frontend has no build sources; keep its original application chunk intact.
$appName = 'app.d0aa4eb3.js'
$appPath = Join-Path $FrontendRoot ('js\' + $appName)
$indexPath = Join-Path $FrontendRoot 'index.html'
$appContent = [System.IO.File]::ReadAllText($appPath, $utf8)
$indexContent = [System.IO.File]::ReadAllText($indexPath, $utf8)
$runtimeOriginal = 'shopTable:"fe534d8f"'
if ([regex]::Matches($appContent, [regex]::Escape($runtimeOriginal)).Count -ne 1) {
    throw 'Unrecognized application chunk; expected exactly one shopTable JavaScript hash.'
}
function Get-ContentHash([string]$Value) {
    $hashAlgorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        $digest = $hashAlgorithm.ComputeHash($utf8.GetBytes($Value))
        return ([BitConverter]::ToString($digest).Replace('-', '').ToLowerInvariant()).Substring(0, 8)
    } finally {
        $hashAlgorithm.Dispose()
    }
}
$patchedBundle = $content.Replace($original, $replacement)
$bundleHash = Get-ContentHash $patchedBundle
$versionedBundleName = 'shopTable.' + $bundleHash + '.js'
$patchedApp = $appContent.Replace($runtimeOriginal, ('shopTable:"' + $bundleHash + '"'))
$versionedAppName = 'app.' + (Get-ContentHash $patchedApp) + '.js'
$previousBundleHash = Get-ContentHash $previousBundleContent
$previousApp = $appContent.Replace($runtimeOriginal, ('shopTable:"' + $previousBundleHash + '"'))
$previousAppReference = 'js/app.' + (Get-ContentHash $previousApp) + '.js'
$oldAppReference = 'js/' + $appName
$newAppReference = 'js/' + $versionedAppName
if (-not $indexContent.Contains($oldAppReference) -and -not $indexContent.Contains($newAppReference) -and
        -not $indexContent.Contains($previousAppReference)) {
    throw 'Unrecognized index.html; expected original or fixed application resource references.'
}
$indexBackup = Join-Path $backupDirectory 'index.html.bak'
if (-not (Test-Path -LiteralPath $indexBackup)) {
    [System.IO.File]::Copy($indexPath, $indexBackup, $false)
}
# Finish all content checks before replacing any deployed resource.
[System.IO.File]::WriteAllText($bundlePath, $patchedBundle, $outputEncoding)
[System.IO.File]::WriteAllText((Join-Path $FrontendRoot ('js\' + $versionedBundleName)), $patchedBundle, $outputEncoding)
[System.IO.File]::WriteAllText((Join-Path $FrontendRoot ('js\' + $versionedAppName)), $patchedApp, $utf8)
$patchedIndex = $indexContent.Replace($oldAppReference, $newAppReference)
$patchedIndex = $patchedIndex.Replace($previousAppReference, $newAppReference)
$patchedIndex = $patchedIndex.Replace(('js/' + $bundleName), ('js/' + $versionedBundleName))
$patchedIndex = $patchedIndex.Replace(('js/shopTable.' + $previousBundleHash + '.js'), ('js/' + $versionedBundleName))
[System.IO.File]::WriteAllText($indexPath, $patchedIndex, $utf8)
Write-Output "Fixed setmeal dish selection: $bundlePath"
Write-Output "Original bundle backup: $backupPath"
Write-Output "Versioned frontend resources: $versionedBundleName, $versionedAppName"
