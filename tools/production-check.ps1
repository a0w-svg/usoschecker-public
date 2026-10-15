param([switch]$DeviceTests)

$ErrorActionPreference = 'Stop'
$projectDirectory = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
function Assert-DeviceReport([string]$Variant, [int]$MinimumTests) {
    $reportDirectory = Join-Path $projectDirectory "app/build/outputs/androidTest-results/connected/$Variant"
    $reports = @(Get-ChildItem -LiteralPath $reportDirectory -Recurse -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue)
    if ($reports.Count -eq 0) { throw "Brak raportu testów $Variant." }
    $passed = 0
    foreach ($file in $reports) {
        [xml]$report = Get-Content -LiteralPath $file.FullName -Raw
        $root = $report.DocumentElement
        $failures = [int]$root.GetAttribute('failures') + [int]$root.GetAttribute('errors')
        if ($failures -gt 0) { throw "Nieudane testy $Variant w $($file.Name)." }
        $passed += [int]$root.GetAttribute('tests') - [int]$root.GetAttribute('skipped')
    }
    if ($passed -lt $MinimumTests) {
        throw "Niepełne testy ${Variant}: wykonano $passed, wymagane co najmniej $MinimumTests. Sprawdź start runnera i filtry testów."
    }
    Write-Output "Zweryfikowany raport ${Variant}: $passed testów."
}

$checks = @(':core:test', ':app:testDebugUnitTest', ':app:lintRelease',
    ':app:verifyReleaseConfiguration', ':app:assembleRelease', ':app:bundleRelease', '-PisolatedTests')
if ($DeviceTests) { $checks += ':app:connectedDebugAndroidTest' }

Push-Location -LiteralPath $projectDirectory
try {
    & (Join-Path $projectDirectory 'gradlew.bat') @checks
    if ($LASTEXITCODE -ne 0) { throw 'Kontrola wydania nie powiodła się. Sprawdź raporty Gradle przed publikacją.' }
    if ($DeviceTests) {
        $expectedTests = 0
        Get-ChildItem -LiteralPath (Join-Path $projectDirectory 'app/src/androidTest') -Recurse -Filter '*Test.java' | ForEach-Object {
            $expectedTests += [regex]::Matches([System.IO.File]::ReadAllText($_.FullName), '@Test\b').Count
        }
        Assert-DeviceReport 'debug' $expectedTests
        & (Join-Path $projectDirectory 'gradlew.bat') ':app:connectedOptimizedQaAndroidTest' '-PoptimizedQa' '-Pandroid.testInstrumentationRunnerArguments.class=pl.vistula.checker.ProductionReadinessTest'
        if ($LASTEXITCODE -ne 0) { throw 'Testy zoptymalizowanej wersji QA nie powiodły się.' }
        $expectedOptimizedTests = [regex]::Matches([System.IO.File]::ReadAllText((Join-Path $projectDirectory 'app/src/androidTest/java/pl/vistula/checker/ProductionReadinessTest.java')), '@Test\b').Count
        Assert-DeviceReport 'optimizedQa' $expectedOptimizedTests
    }
} finally {
    Pop-Location
}
