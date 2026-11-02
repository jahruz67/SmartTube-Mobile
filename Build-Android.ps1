param(
    [string]$JdkPath = 'C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot',
    [string]$SdkPath = "$env:LOCALAPPDATA\Android\Sdk"
)
$ErrorActionPreference = 'Stop'
$source = Join-Path $PSScriptRoot 'SmarterTube'
$kit = Join-Path $PSScriptRoot 'SmartTube-Mobile-Build-Kit'
$reports = Join-Path $PSScriptRoot 'reports'
New-Item -ItemType Directory -Path $reports -Force | Out-Null

function Invoke-Checked {
    param([string]$Program, [string[]]$Arguments, [string]$Log)
    & $Program @Arguments *> $Log
    if ($LASTEXITCODE -ne 0) {
        Get-Content -LiteralPath $Log -Tail 35
        throw "$Program failed; see $Log"
    }
}

if (-not (Test-Path -LiteralPath "$JdkPath\bin\javac.exe")) { throw 'Set JdkPath to JDK 17.' }
foreach ($component in @('platforms\android-34\android.jar', 'build-tools\30.0.3\aapt.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $SdkPath $component))) {
        throw "Missing Android SDK component: $component"
    }
}
if (Test-Path -LiteralPath "$source\keystore.properties") { throw 'Refusing inherited release signing keys.' }
if (Test-Path -LiteralPath "$source\.git") {
    $revision = & git -C $source rev-parse HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Unable to read source revision.' }
} else {
    $revision = (Get-Content -LiteralPath "$reports\BUILD_PROVENANCE.txt" | Where-Object { $_ -like 'commit=*' }) -replace '^commit=', ''
}
if ($revision -ne '3b780e9a8ac62ba59c90d090fa46d87210c45525') {
    throw 'Unexpected source revision; preserve this checkout and inspect before building.'
}
$env:JAVA_HOME = $JdkPath
$env:ANDROID_HOME = $SdkPath
Set-Content -LiteralPath "$source\local.properties" -Value ('sdk.dir=' + $SdkPath.Replace('\', '/'))
Invoke-Checked 'python' @('-m', 'unittest', 'discover', '-s', "$kit\tests", '-v') "$reports\kit-tests.log"
foreach ($script in @('apply-bottom-navigation.py', 'apply-development-identity.py', 'install-mobile-smoke-test.py', 'apply-build-compatibility.py')) {
    Invoke-Checked 'python' @("$kit\scripts\$script", $source) "$reports\$script.log"
}
Push-Location $source
try {
    Invoke-Checked '.\gradlew.bat' @('--no-daemon', '--console=plain', "-Dorg.gradle.java.home=$JdkPath", ':smarttubetv:assembleStmobileDebug', ':smarttubetv:testStmobileDebugUnitTest', ':common:testStmobileDebugUnitTest', ':smarttubetv:assembleStmobileDebugAndroidTest') "$reports\build-windows.log"
} finally {
    Pop-Location
}
$apks = @(Get-ChildItem -LiteralPath "$source\smarttubetv\build\outputs\apk\stmobile\debug" -Filter '*.apk')
if ($apks.Count -eq 0) { throw 'Build completed without APK outputs.' }
foreach ($apk in $apks) {
    Invoke-Checked 'python' @("$kit\scripts\inspect-apk.py", $apk.FullName, '--package', 'dev.smarttubemobile.unofficial') "$reports\$($apk.BaseName)-inspection.log"
    Write-Output $apk.FullName
}
Write-Output 'Build, local unit tests, package identity and APK signatures verified. No emulator was used.'
