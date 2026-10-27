[CmdletBinding()]
param(
    [string]$DatabaseHost = 'localhost',
    [int]$DatabasePort = 3306,
    [Parameter(Mandatory=$true)][string]$Database,
    [Parameter(Mandatory=$true)][string]$DatabaseUser,
    [switch]$DemoCatalog
)
$ErrorActionPreference = 'Stop'
# Supply MYSQL_PWD in the invoking shell; do not place passwords in command arguments or this file.
$rentalRoot = Split-Path $PSScriptRoot -Parent
$sqlFiles = @((Join-Path $rentalRoot 'database/rental/001-schema.sql'))
$sqlFiles += Join-Path $rentalRoot 'database/rental/001b-order-index.sql'
if ($DemoCatalog) { $sqlFiles += Join-Path $rentalRoot 'database/rental/002-demo-catalog.sql' }
foreach ($sqlFile in $sqlFiles) {
    $sql = [System.IO.File]::ReadAllText($sqlFile, [System.Text.Encoding]::UTF8)
    $sql | & mysql --host=$DatabaseHost --port=$DatabasePort --user=$DatabaseUser --database=$Database --default-character-set=utf8mb4
    if ($LASTEXITCODE -ne 0) { throw "Rental database initialization failed: $sqlFile" }
    Write-Output ('Applied: ' + (Split-Path $sqlFile -Leaf))
}
