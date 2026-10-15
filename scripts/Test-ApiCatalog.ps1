param([switch]$CheckRemote)
$ErrorActionPreference = 'Stop'
$assetDirectory = Join-Path (Split-Path $PSScriptRoot -Parent) 'app/src/main/assets/api'
$index = Get-Content -LiteralPath (Join-Path $assetDirectory 'index.json') -Raw | ConvertFrom-Json
$seen = [System.Collections.Generic.HashSet[string]]::new()
$modules = @{}
foreach ($entry in $index) {
    $name = [string]$entry.name
    if ($name -notmatch '^services/(?:[a-z0-9_]+/)+[a-z0-9_]+$') { throw "Invalid method path: $name" }
    if (-not $seen.Add($name)) { throw "Duplicate method: $name" }
    $metadata = Get-Content -LiteralPath (Join-Path $assetDirectory ($name.Replace('/', '__') + '.json')) -Raw | ConvertFrom-Json
    if ($metadata.name -ne $name) { throw "Metadata mismatch: $name" }
    if ($null -eq $metadata.auth_options -or $null -eq $metadata.arguments) { throw "Incomplete metadata: $name" }
    foreach ($argument in $metadata.arguments) {
        if ([string]::IsNullOrWhiteSpace($argument.name)) { throw "Unnamed argument: $name" }
    }
    $module = $name.Split('/')[1]
    $modules[$module] = [int]$modules[$module] + 1
}
if ($CheckRemote) {
    $remote = Invoke-RestMethod -Uri 'https://usosapps.vistula.edu.pl/services/apiref/method_index' -TimeoutSec 30
    $difference = Compare-Object ($index.name | Sort-Object) ($remote.name | Sort-Object)
    if ($difference) { throw 'The bundled method list differs from the public USOS index. Run refresh_api_catalog.py.' }
}
[pscustomobject]@{Methods = $seen.Count; Modules = $modules.Count; Metadata = 'Complete'; PublicIndexChecked = [bool]$CheckRemote}
