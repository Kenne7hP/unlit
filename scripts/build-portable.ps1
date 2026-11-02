param(
    [string]$SdkRoot = $env:ANDROID_HOME,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$Gradle = 'gradle',
    [string]$NdkVersion = '27.0.12077973',
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
if (-not $SdkRoot -or -not $JavaHome) { throw 'Set ANDROID_HOME and JAVA_HOME, or pass -SdkRoot and -JavaHome.' }
$projectRoot = Split-Path $PSScriptRoot -Parent
$llvmRoot = Join-Path $SdkRoot "ndk/$NdkVersion/toolchains/llvm/prebuilt"
$hostTag = if ($IsWindows) { 'windows-x86_64' } elseif ($IsMacOS) { 'darwin-x86_64' } else { 'linux-x86_64' }
$clang = Join-Path $llvmRoot "$hostTag/bin/clang$(if ($IsWindows) { '.exe' })"
$libraryRoot = Join-Path $projectRoot 'app/src/main/jniLibs/arm64-v8a'
$reportRoot = Join-Path $projectRoot 'app/build/reports'
$previousJava = $env:JAVA_HOME
$previousSdk = $env:ANDROID_HOME
try {
    $env:JAVA_HOME = $JavaHome
    $env:ANDROID_HOME = $SdkRoot
    New-Item -ItemType Directory -Force $libraryRoot, $reportRoot | Out-Null
    $native = Join-Path $projectRoot 'native/input_guard.c'
    & $clang '--target=aarch64-linux-android30' '--analyze' '-std=c11' '-Wall' '-Wextra' '-Werror' $native '-o' (Join-Path $reportRoot 'native-analysis.plist')
    if ($LASTEXITCODE -ne 0) { throw 'Native static analysis failed.' }
    & $clang '--target=aarch64-linux-android30' '-std=c11' '-Wall' '-Wextra' '-Werror' '-fPIC' '-shared' '-Wl,-z,max-page-size=16384' $native '-o' (Join-Path $libraryRoot 'libfoldguard.so')
    if ($LASTEXITCODE -ne 0) { throw 'Native compilation failed.' }
    Push-Location $projectRoot
    try {
        $buildArgs = @('--no-daemon', '--console=plain', ':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug')
        if ($Offline) { $buildArgs += '--offline' }
        & $Gradle @buildArgs
        if ($LASTEXITCODE -ne 0) { throw 'Gradle build or verification failed.' }
    } finally { Pop-Location }
    Write-Output (Join-Path $projectRoot 'app/build/outputs/apk/debug/app-debug.apk')
} finally {
    $env:JAVA_HOME = $previousJava
    $env:ANDROID_HOME = $previousSdk
}
