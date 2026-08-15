<#
.SYNOPSIS
    Build the OS4 Glass Mode Xposed module APK.
.DESCRIPTION
    One-click build for os4-glass-blur v6.1. Requires:
      - JDK (javac/jar)
      - Android SDK Build Tools 37.0.0 (aapt2/d8/zipalign/apksigner)
      - libxposed-api-102.0.0.jar (Vector framework API)
      - android-37 platform android.jar
    Edit the paths below for your environment, then run:  .\build.ps1
#>
$ErrorActionPreference = 'Continue'

# ---------- adjust these paths ----------
$JdkBin        = 'C:\Program Files\Java\jdk-XX\bin'          # javac/jar
$BuildTools    = 'C:\path\to\build-tools\37.0.0'             # aapt2/d8/...
$PlatformJar   = 'C:\path\to\platforms\android-37\android.jar'
$XposedApiJar  = 'C:\path\to\libxposed-api-102.0.0.jar'
$Keystore      = 'C:\path\to\debug.keystore'                 # sign keystore
$KeystoreAlias = 'androiddebugkey'
$KeystorePass  = 'android'
$OutName       = 'os4-glass-blur-v6.1.apk'
# ---------------------------------------

$Root     = Split-Path -Parent $MyInvocation.MyCommand.Path
$App      = Join-Path $Root 'app'
$Out      = Join-Path $Root 'release'
$Work     = Join-Path $Root 'build'
$Classes  = Join-Path $Work 'classes'
$Dex      = Join-Path $Work 'dex'
$ResOut   = Join-Path $Work 'res-out'
$SrcRoot  = Join-Path $App 'src'

foreach ($d in @($Classes, $Dex, $ResOut, $Out)) {
    New-Item -ItemType Directory -Force -Path $d | Out-Null
}

function Fail([string]$m) {
    Write-Host "FAILED: $m"
    exit 1
}

# Compile every Java source under app/src. This keeps SettingsActivity and
# future helper/hook classes from being accidentally omitted.
$JavaSources = @(Get-ChildItem -Path $SrcRoot -Recurse -Filter '*.java' -File |
    ForEach-Object { $_.FullName })
if ($JavaSources.Count -eq 0) { Fail 'no Java sources found' }

Write-Host "== javac ($($JavaSources.Count) source files) =="
& (Join-Path $JdkBin 'javac.exe') -source 8 -target 8 -encoding UTF-8 `
    -cp "$XposedApiJar;$PlatformJar" -d $Classes $JavaSources 2>$null
if ($LASTEXITCODE -ne 0) { Fail 'javac' }

Write-Host '== jar (module classes) =='
& (Join-Path $JdkBin 'jar.exe') cf (Join-Path $Work 'module-classes.jar') -C $Classes . 2>$null
if ($LASTEXITCODE -ne 0) { Fail 'jar classes' }

Write-Host '== d8 =='
& (Join-Path $BuildTools 'd8.bat') --lib $PlatformJar --release --min-api 35 `
    --output $Dex (Join-Path $Work 'module-classes.jar') 2>$null
if ($LASTEXITCODE -ne 0) { Fail 'd8' }

Write-Host '== aapt2 compile =='
& (Join-Path $BuildTools 'aapt2.exe') compile --dir (Join-Path $App 'res') `
    -o (Join-Path $ResOut 'compiled-res.zip') 2>$null
if ($LASTEXITCODE -ne 0) { Fail 'aapt2 compile' }

Write-Host '== aapt2 link =='
$BaseApk = Join-Path $Work 'base-unsigned.apk'
& (Join-Path $BuildTools 'aapt2.exe') link -o $BaseApk -I $PlatformJar `
    --manifest (Join-Path $App 'AndroidManifest.xml') (Join-Path $ResOut 'compiled-res.zip') 2>$null
if ($LASTEXITCODE -ne 0) { Fail 'aapt2 link' }

Write-Host '== add dex + xposed meta =='
& (Join-Path $JdkBin 'jar.exe') uf $BaseApk -C $Dex classes.dex 2>$null
if ($LASTEXITCODE -ne 0) { Fail 'jar dex' }
$Meta = Join-Path $App 'meta'
& (Join-Path $JdkBin 'jar.exe') uf $BaseApk -C $Meta 'META-INF/xposed/java_init.list' `
    -C $Meta 'META-INF/xposed/module.prop' -C $Meta 'META-INF/xposed/scope.list' 2>$null
if ($LASTEXITCODE -ne 0) { Fail 'jar meta' }

Write-Host '== zipalign =='
$Aligned = Join-Path $Work 'base-aligned.apk'
& (Join-Path $BuildTools 'zipalign.exe') -f 4 $BaseApk $Aligned 2>$null
if ($LASTEXITCODE -ne 0) { Fail 'zipalign' }

Write-Host '== apksigner =='
$Final = Join-Path $Out $OutName
& (Join-Path $BuildTools 'apksigner.bat') sign --ks $Keystore --ks-key-alias $KeystoreAlias `
    --ks-pass "pass:$KeystorePass" --out $Final $Aligned 2>$null
if ($LASTEXITCODE -ne 0) { Fail 'apksigner' }

Write-Host "OK -> $Final"
