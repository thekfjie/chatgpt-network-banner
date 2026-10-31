$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$Apk = Join-Path $ProjectRoot "dist\ChatGPTNetworkBanner-1.1.0.apk"
if (-not (Test-Path -LiteralPath $Apk)) {
    throw "APK not found: $Apk"
}

$SdkRoot = $null
foreach ($candidate in @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME, "D:\develop\android-sdk")) {
    if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate "platforms\android-35\android.jar"))) {
        $SdkRoot = $candidate
        break
    }
}
$BuildToolCandidates = @($env:ANDROID_BUILD_TOOLS)
if ($SdkRoot) {
    $BuildToolCandidates += @(
        Get-ChildItem -LiteralPath (Join-Path $SdkRoot "build-tools") -Directory `
            -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            Select-Object -ExpandProperty FullName
    )
}
$BuildTools = $null
foreach ($candidate in $BuildToolCandidates) {
    if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate "aapt2.exe"))) {
        $BuildTools = $candidate
        break
    }
}
if (-not $BuildTools) {
    throw "Android build-tools not found. Set ANDROID_BUILD_TOOLS."
}
$Aapt2 = Join-Path $BuildTools "aapt2.exe"
$ApkSigner = Join-Path $BuildTools "lib\apksigner.jar"
$Java = $null
foreach ($candidate in @($env:JAVA_HOME17, $env:JAVA_HOME, "D:\Java17")) {
    if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate "bin\java.exe"))) {
        $Java = $candidate
        break
    }
}
if (-not $Java) {
    throw "JDK not found. Set JAVA_HOME."
}

$Badging = & $Aapt2 dump badging $Apk
if ($LASTEXITCODE -ne 0 -or -not ($Badging -match "package: name='com\.kfjie\.chatgptnetworkbanner' versionCode='5' versionName='1\.1\.0'")) {
    throw "Unexpected package or version."
}
$Permissions = & $Aapt2 dump permissions $Apk
if ($LASTEXITCODE -ne 0 -or @($Permissions | Select-String -Pattern "^uses-permission:").Count -gt 0) {
    throw "Unexpected Android permission."
}

$Signature = & (Join-Path $Java "bin\java.exe") -jar $ApkSigner verify --verbose --print-certs $Apk 2>&1
if ($LASTEXITCODE -ne 0 -or -not ($Signature -match "Verified using v3 scheme \(APK Signature Scheme v3\): true")) {
    throw "APK v3 signature verification failed."
}

$Archive = [IO.Compression.ZipFile]::OpenRead($Apk)
try {
    $Names = @($Archive.Entries | ForEach-Object FullName)
    foreach ($required in @(
        "classes.dex",
        "META-INF/xposed/module.prop",
        "META-INF/xposed/scope.list",
        "META-INF/xposed/java_init.list"
    )) {
        if ($Names -notcontains $required) {
            throw "Missing APK entry: $required"
        }
    }
    $DexEntry = $Archive.GetEntry("classes.dex")
    $Reader = [IO.StreamReader]::new($DexEntry.Open(), [Text.Encoding]::UTF8)
    try {
        $DexText = $Reader.ReadToEnd()
    } finally {
        $Reader.Dispose()
    }
    foreach ($forbidden in @("chatgpt_banner_debug_trigger", "Settings.Global", "ContentObserver")) {
        if ($DexText.Contains($forbidden)) {
            throw "Production DEX contains forbidden marker: $forbidden"
        }
    }
} finally {
    $Archive.Dispose()
}

$Hash = (Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash
Write-Output "PASS: package/version, permissions, v3 signature, APK entries, production DEX"
Write-Output "APK: $Apk"
Write-Output "SHA-256: $Hash"
