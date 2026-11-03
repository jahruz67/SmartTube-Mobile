param(
    [string]$JdkPath = $env:JAVA_HOME,
    [string]$SdkPath = $env:ANDROID_HOME,
    [string]$ReferenceApk
)
$ErrorActionPreference = 'Stop'
if (-not $SdkPath) { $SdkPath = $env:ANDROID_SDK_ROOT }
if (-not $SdkPath) { $SdkPath = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
if (-not $JdkPath -or -not (Test-Path -LiteralPath "$JdkPath/bin/javac.exe")) {
    throw 'Set JAVA_HOME or -JdkPath to JDK 17.'
}
foreach ($component in @('platforms/android-36/android.jar', 'build-tools/35.0.0/aapt.exe',
                         'ndk/28.2.13676358/source.properties')) {
    if (-not (Test-Path -LiteralPath (Join-Path $SdkPath $component))) {
        throw "Missing Android SDK component: $component"
    }
}
$env:JAVA_HOME = $JdkPath
$env:ANDROID_HOME = $SdkPath
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

Push-Location $PSScriptRoot
try {
    Invoke-Checked 'python' @('-m', 'unittest', 'discover', '-s', 'tools/tests', '-v') "$reports/tool-tests.log"
    Invoke-Checked '.\gradlew.bat' @('--no-daemon', '--console=plain', "-Dorg.gradle.java.home=$JdkPath",
        'clean', ':app:assembleStmobileDebug', ':app:testStmobileDebugUnitTest',
        ':common:testStmobileDebugUnitTest', ':app:assembleStmobileDebugAndroidTest') "$reports/build.log"
    $apks = @(Get-ChildItem -LiteralPath 'app/build/outputs/apk/stmobile/debug' -Filter '*.apk')
    if ($apks.Count -eq 0) { throw 'Build completed without APK outputs.' }
    foreach ($apk in $apks) {
        $arguments = @('tools/inspect-apk.py', $apk.FullName)
        if ($ReferenceApk -and $apk.BaseName.EndsWith('-universal')) {
            $arguments += @('--reference', $ReferenceApk)
        }
        Invoke-Checked 'python' $arguments "$reports/$($apk.BaseName)-inspection.log"
        Write-Output $apk.FullName
    }
} finally {
    Pop-Location
}
Write-Output 'Debug APKs, JVM tests, instrumentation compilation, package identity and signatures verified.'
Write-Output 'Device tests are separate: gradlew.bat :app:connectedStmobileDebugAndroidTest'
