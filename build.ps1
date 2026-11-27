param(
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$OutputApk = (Join-Path $PSScriptRoot 'bin/NothingGlass.apk')
)

$ErrorActionPreference = 'Stop'
if (!$Sdk) { $Sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$tools = Join-Path $Sdk 'build-tools/36.0.0'
$androidJar = Join-Path $Sdk 'platforms/android-37.0/android.jar'
$OutputApk = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputApk)

function Invoke-AndroidTool {
    param([string]$Executable, [string[]]$Arguments)
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Executable exited with code $LASTEXITCODE" }
}

$build = Join-Path $PSScriptRoot '.build'
$run = Join-Path $build ([Guid]::NewGuid().ToString('N'))
$classes = Join-Path $run 'classes'
$dex = Join-Path $run 'dex'
$compiledResources = Join-Path $run 'compiled-res.zip'
$base = Join-Path $run 'base.apk'
$aligned = Join-Path $run 'aligned.apk'
$signed = Join-Path $run 'signed.apk'
$keystore = Join-Path $build 'local-debug.p12'
New-Item -ItemType Directory -Path $classes, $dex -Force | Out-Null

Invoke-AndroidTool "$tools/aapt2.exe" @('compile', '--dir', "$PSScriptRoot/res", '-o', $compiledResources)
Invoke-AndroidTool "$tools/aapt2.exe" @('link', '-I', $androidJar, '--manifest', "$PSScriptRoot/AndroidManifest.xml",
    '-A', "$PSScriptRoot/assets", '-o', $base, $compiledResources)

$sources = @(Get-ChildItem -LiteralPath "$PSScriptRoot/src" -Recurse -Filter '*.java' | ForEach-Object FullName)
Invoke-AndroidTool 'javac' (@('--release', '17', '-cp', $androidJar, '-d', $classes) + $sources)
$moduleClasses = @(Get-ChildItem -LiteralPath "$classes/com" -Recurse -Filter '*.class' | ForEach-Object FullName)
Invoke-AndroidTool 'java' (@('-cp', "$tools/lib/d8.jar", 'com.android.tools.r8.D8',
    '--min-api', '36', '--lib', $androidJar, '--classpath', $classes, '--output', $dex) + $moduleClasses)

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($base, 'Update')
try {
    foreach ($dexFile in Get-ChildItem -LiteralPath $dex -Filter '*.dex') {
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $dexFile.FullName, $dexFile.Name) | Out-Null
    }
} finally {
    $zip.Dispose()
}

if (!(Test-Path -LiteralPath $keystore)) {
    Invoke-AndroidTool 'keytool' @('-genkeypair', '-keystore', $keystore, '-storetype', 'PKCS12',
        '-storepass', 'android', '-alias', 'androiddebugkey', '-keyalg', 'RSA', '-keysize', '2048',
        '-validity', '10000', '-dname', 'CN=Android Debug,O=Android,C=US')
}

Invoke-AndroidTool "$tools/zipalign.exe" @('-f', '4', $base, $aligned)
Invoke-AndroidTool 'java' @('-jar', "$tools/lib/apksigner.jar", 'sign', '--ks', $keystore,
    '--ks-pass', 'pass:android', '--v4-signing-enabled', 'false', '--out', $signed, $aligned)
Invoke-AndroidTool 'java' @('-jar', "$tools/lib/apksigner.jar", 'verify', '--verbose', $signed)
New-Item -ItemType Directory -Path (Split-Path -Parent $OutputApk) -Force | Out-Null
Copy-Item -LiteralPath $signed -Destination $OutputApk -Force
Write-Output $OutputApk
