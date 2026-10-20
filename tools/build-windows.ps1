param([string]$PackagingJavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$projectDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not (Test-Path -LiteralPath (Join-Path $PackagingJavaHome 'bin/jpackage.exe'))) {
    throw 'Windows packaging requires a full JDK with jpackage. Set JAVA_HOME or pass -PackagingJavaHome.'
}
Push-Location -LiteralPath $projectDirectory
try {
    & ./gradlew.bat ':shared:desktopTest' ':desktopApp:createDistributable' ':desktopApp:packageMsi' "-PdesktopJavaHome=$PackagingJavaHome" '--no-daemon'
    if ($LASTEXITCODE -ne 0) { throw 'Windows tests or packaging failed.' }
    $buildConfig = [IO.File]::ReadAllText((Join-Path $projectDirectory 'desktopApp/build.gradle.kts'))
    $version = [regex]::Match($buildConfig, 'packageVersion = "([0-9.]+)"').Groups[1].Value
    if (-not $version) { throw 'Missing desktop version.' }
    $outputDirectory = Join-Path $projectDirectory 'desktopApp/build/windows-publication'
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
    $installer = Join-Path $projectDirectory "desktopApp/build/compose/binaries/main/msi/VistulaChecker-$version.msi"
    $application = Join-Path $projectDirectory 'desktopApp/build/compose/binaries/main/app/VistulaChecker'
    if (-not (Test-Path -LiteralPath (Join-Path $application 'VistulaChecker.exe'))) { throw 'Missing Windows launcher.' }
    Copy-Item -LiteralPath $installer -Destination (Join-Path $outputDirectory "VistulaChecker-$version-windows-x64.msi")
    Compress-Archive -LiteralPath $application -DestinationPath (Join-Path $outputDirectory "VistulaChecker-$version-windows-x64-portable.zip") -Force
    $sums = Get-ChildItem -LiteralPath $outputDirectory -File | Where-Object Extension -in '.msi','.zip' | Sort-Object Name | ForEach-Object {
        $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        "$hash  $($_.Name)"
    }
    [IO.File]::WriteAllLines((Join-Path $outputDirectory 'SHA256SUMS.txt'), $sums, [Text.UTF8Encoding]::new($false))
    Write-Output "Windows MSI, portable ZIP and SHA-256: $outputDirectory"
} finally { Pop-Location }
