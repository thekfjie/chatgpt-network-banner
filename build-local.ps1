$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$VersionName = "1.2.0"
$SdkCandidates = @(
    $env:ANDROID_SDK_ROOT,
    $env:ANDROID_HOME,
    "D:\develop\android-sdk"
) | Where-Object { $_ }
$SdkRoot = $null
foreach ($candidate in $SdkCandidates) {
    if (Test-Path -LiteralPath (Join-Path $candidate "platforms\android-35\android.jar")) {
        $SdkRoot = $candidate
        break
    }
}
if (-not $SdkRoot) {
    throw "Android SDK platform 35 not found. Expected D:\develop\android-sdk or ANDROID_SDK_ROOT."
}
$AndroidJar = if (Test-Path -LiteralPath (Join-Path $SdkRoot "android-35\android.jar")) {
    Join-Path $SdkRoot "android-35\android.jar"
} else {
    Join-Path $SdkRoot "platforms\android-35\android.jar"
}
$BuildToolCandidates = @($env:ANDROID_BUILD_TOOLS)
$BuildToolCandidates += @(
    Get-ChildItem -LiteralPath (Join-Path $SdkRoot "build-tools") -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        Select-Object -ExpandProperty FullName
)
$BuildTools = $null
foreach ($candidate in $BuildToolCandidates) {
    if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate "aapt2.exe"))) {
        $BuildTools = $candidate
        break
    }
}
if (-not $BuildTools) {
    throw "Android build-tools with aapt2.exe not found. Set ANDROID_BUILD_TOOLS."
}
$Aapt2 = Join-Path $BuildTools "aapt2.exe"
$D8 = Join-Path $BuildTools "d8.bat"
$JavaHome = $null
foreach ($candidate in @($env:JAVA_HOME17, $env:JAVA_HOME, "D:\Java17")) {
    if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate "bin\javac.exe"))) {
        $JavaHome = $candidate
        break
    }
}
if (-not $JavaHome) {
    throw "JDK 17 not found. Set JAVA_HOME or JAVA_HOME17."
}
$Javac = Join-Path $JavaHome "bin\javac.exe"
$Jar = Join-Path $JavaHome "bin\jar.exe"
$ApkSigner = Join-Path $BuildTools "lib\apksigner.jar"
$ZipAlign = Join-Path $BuildTools "zipalign.exe"
$Keystore = Join-Path $ProjectRoot "signing\debug.keystore"
$StorePass = "android"
$KeyAlias = "androiddebugkey"

foreach ($required in @($AndroidJar, $Aapt2, $D8, $Javac, $Jar, $ApkSigner, $ZipAlign)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Missing build tool: $required"
    }
}

$Build = Join-Path $ProjectRoot "build-local"
$Gen = Join-Path $Build "gen"
$Classes = Join-Path $Build "classes"
$Dex = Join-Path $Build "dex"
$Compiled = Join-Path $Build "compiled"
$GeneratedManifest = Join-Path $Build "AndroidManifest.xml"
$Output = Join-Path $ProjectRoot "dist"
New-Item -ItemType Directory -Force -Path $Gen, $Classes, $Dex, $Compiled, $Output | Out-Null
Remove-Item -LiteralPath (Join-Path $Build "unsigned.apk"), (Join-Path $Build "aligned.apk") -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $Gen, $Classes, $Dex, $Compiled -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Gen, $Classes, $Dex, $Compiled | Out-Null

$Libs = Join-Path $ProjectRoot "libs"
New-Item -ItemType Directory -Force -Path $Libs | Out-Null
$ApiAar = Join-Path $Libs "api-102.0.0.aar"
$ApiJar = Join-Path $Libs "api-102.0.0-classes.jar"
if (-not (Test-Path -LiteralPath $ApiJar)) {
    $extract = Join-Path $Build "api-extract"
    New-Item -ItemType Directory -Force -Path $extract | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::ExtractToDirectory($ApiAar, $extract)
    Copy-Item -LiteralPath (Join-Path $extract "classes.jar") -Destination $ApiJar
}

$ResDir = Join-Path $ProjectRoot "app\src\main\res"
if (Test-Path -LiteralPath $ResDir) {
    & $Aapt2 compile --dir $ResDir -o (Join-Path $Build "compiled")
    if ($LASTEXITCODE -ne 0) {
        throw "aapt2 compile failed"
    }
}

$ManifestText = Get-Content -LiteralPath (Join-Path $ProjectRoot "app\src\main\AndroidManifest.xml") -Raw
$ManifestText = $ManifestText -replace '<manifest ', '<manifest package="com.kfjie.chatgptnetworkbanner" '
[IO.File]::WriteAllText($GeneratedManifest, $ManifestText, [Text.UTF8Encoding]::new($false))
$FlatFiles = @(Get-ChildItem -LiteralPath $Compiled -Filter "*.flat" -ErrorAction SilentlyContinue)
if ($FlatFiles.Count -eq 0) {
    New-Item -ItemType File -Force -Path (Join-Path $Compiled ".keep") | Out-Null
}

$LinkArgs = @(
    "link",
    "-I", $AndroidJar,
    "--manifest", $GeneratedManifest,
    "--java", $Gen,
    "--auto-add-overlay",
    "-o", (Join-Path $Build "resources.apk")
)
if ($FlatFiles.Count -gt 0) {
    $LinkArgs += $FlatFiles.FullName
}
& $Aapt2 @LinkArgs
if ($LASTEXITCODE -ne 0) {
    throw "aapt2 link failed"
}

$Sources = @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot "app\src\main\java") -Recurse -Filter "*.java")
$Sources += @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot "app\src\release\java") -Recurse -Filter "*.java")
$Sources += @(Get-ChildItem -LiteralPath $Gen -Recurse -Filter "*.java" -ErrorAction SilentlyContinue)
$JavaArgs = @("-encoding", "UTF-8", "--release", "8", "-classpath", "$AndroidJar;$ApiJar", "-d", $Classes)
$JavaArgs += $Sources.FullName
& $Javac @JavaArgs
if ($LASTEXITCODE -ne 0) {
    throw "javac failed; refusing stale APK"
}

$AllClasses = Join-Path $Build "all-classes"
Remove-Item -LiteralPath $AllClasses -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item -LiteralPath $Classes -Destination $AllClasses -Recurse -Force
$OldJava = $env:JAVA_HOME
$env:JAVA_HOME = $JavaHome
try {
    & $D8 --release --min-api 32 --lib $AndroidJar --classpath $ApiJar --output $Dex (Get-ChildItem -LiteralPath $AllClasses -Recurse -Filter "*.class").FullName
    if ($LASTEXITCODE -ne 0) {
        throw "d8 failed; refusing incomplete APK"
    }
} finally {
    $env:JAVA_HOME = $OldJava
}

$Payload = Join-Path $Build "payload"
Remove-Item -LiteralPath $Payload -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Payload | Out-Null
$ResourcesApk = Join-Path $Build "resources.apk"
$Unsigned = Join-Path $Build "unsigned.apk"
Copy-Item -LiteralPath $ResourcesApk -Destination $Unsigned
Copy-Item -LiteralPath (Join-Path $Dex "classes.dex") -Destination (Join-Path $Payload "classes.dex")
Copy-Item -LiteralPath (Join-Path $ProjectRoot "app\src\main\resources\META-INF") -Destination $Payload -Recurse -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open($Unsigned, 2)
try {
    foreach ($file in Get-ChildItem -LiteralPath $Payload -Recurse -File) {
        $relative = $file.FullName.Substring($Payload.Length + 1).Replace("\", "/")
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $relative) | Out-Null
    }
} finally {
    $archive.Dispose()
}

$Aligned = Join-Path $Build "aligned.apk"
& $ZipAlign -f 4 $Unsigned $Aligned
if ($LASTEXITCODE -ne 0) {
    throw "zipalign failed"
}

if (-not (Test-Path -LiteralPath $Keystore)) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Keystore) | Out-Null
    & (Join-Path $JavaHome "bin\keytool.exe") -genkeypair -v -keystore $Keystore -storepass $StorePass -keypass $StorePass -alias $KeyAlias -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=ChatGPT Network Banner, OU=Local Build, O=Local, C=CN"
}
$Signed = Join-Path $Output "ChatGPTNetworkBanner-$VersionName.apk"
& (Join-Path $JavaHome "bin\java.exe") -jar $ApkSigner sign --ks $Keystore --ks-pass "pass:$StorePass" --key-pass "pass:$StorePass" --ks-key-alias $KeyAlias --out $Signed $Aligned
if ($LASTEXITCODE -ne 0) {
    throw "apksigner sign failed"
}
& (Join-Path $JavaHome "bin\java.exe") -jar $ApkSigner verify --verbose --print-certs $Signed
if ($LASTEXITCODE -ne 0) {
    throw "apksigner verify failed"
}
Get-Item -LiteralPath $Signed | Select-Object FullName,Length,LastWriteTime

$DebugManifest = Join-Path $Build "AndroidManifest-debug.xml"
$DebugManifestText = $ManifestText -replace '<application', @'
<application
'@
$DebugManifestText = $DebugManifestText.Replace(
    "</application>",
    "</application>"
)
[IO.File]::WriteAllText($DebugManifest, $DebugManifestText, [Text.UTF8Encoding]::new($false))
$DebugGen = Join-Path $Build "debug-gen"
$DebugClasses = Join-Path $Build "debug-classes"
$DebugDex = Join-Path $Build "debug-dex"
Remove-Item -LiteralPath $DebugGen, $DebugClasses, $DebugDex -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $DebugGen, $DebugClasses, $DebugDex | Out-Null
$DebugLinkArgs = @(
    "link",
    "-I", $AndroidJar,
    "--manifest", $DebugManifest,
    "--java", $DebugGen,
    "--auto-add-overlay",
    "-o", (Join-Path $Build "debug-resources.apk")
)
if ($FlatFiles.Count -gt 0) {
    $DebugLinkArgs += $FlatFiles.FullName
}
& $Aapt2 @DebugLinkArgs
if ($LASTEXITCODE -ne 0) {
    throw "aapt2 debug link failed"
}
$DebugSources = @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot "app\src\main\java") -Recurse -Filter "*.java")
$ReleaseDebugTrigger = [IO.Path]::GetFullPath(
    (Join-Path $ProjectRoot "app\src\main\java\com\kfjie\chatgptnetworkbanner\DebugTrigger.java")
)
$DebugSources = @($DebugSources | Where-Object { $_.FullName -ne $ReleaseDebugTrigger })
$DebugSources += @(Get-ChildItem -LiteralPath (Join-Path $ProjectRoot "app\src\debug\java") -Recurse -Filter "*.java")
$DebugSources += @(Get-ChildItem -LiteralPath $DebugGen -Recurse -Filter "*.java" -ErrorAction SilentlyContinue)
$DebugJavaArgs = @("-encoding", "UTF-8", "--release", "8", "-classpath", "$AndroidJar;$ApiJar", "-d", $DebugClasses)
$DebugJavaArgs += $DebugSources.FullName
& $Javac @DebugJavaArgs
if ($LASTEXITCODE -ne 0) {
    throw "javac debug failed"
}
$env:JAVA_HOME = $JavaHome
try {
    & $D8 --release --min-api 32 --lib $AndroidJar --classpath $ApiJar --output $DebugDex (Get-ChildItem -LiteralPath $DebugClasses -Recurse -Filter "*.class").FullName
    if ($LASTEXITCODE -ne 0) {
        throw "d8 debug failed"
    }
} finally {
    $env:JAVA_HOME = $OldJava
}
$DebugUnsigned = Join-Path $Build "debug-unsigned.apk"
Copy-Item -LiteralPath (Join-Path $Build "debug-resources.apk") -Destination $DebugUnsigned -Force
$debugArchive = [System.IO.Compression.ZipFile]::Open($DebugUnsigned, 2)
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($debugArchive, (Join-Path $DebugDex "classes.dex"), "classes.dex") | Out-Null
    foreach ($file in Get-ChildItem -LiteralPath (Join-Path $ProjectRoot "app\src\main\resources\META-INF") -Recurse -File) {
        $relative = $file.FullName.Substring((Join-Path $ProjectRoot "app\src\main\resources").Length + 1).Replace("\", "/")
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($debugArchive, $file.FullName, $relative) | Out-Null
    }
} finally {
    $debugArchive.Dispose()
}
$DebugAligned = Join-Path $Build "debug-aligned.apk"
& $ZipAlign -f 4 $DebugUnsigned $DebugAligned
if ($LASTEXITCODE -ne 0) {
    throw "zipalign debug failed"
}
$DebugSigned = Join-Path $Output "ChatGPTNetworkBanner-$VersionName-debug.apk"
& (Join-Path $JavaHome "bin\java.exe") -jar $ApkSigner sign --ks $Keystore --ks-pass "pass:$StorePass" --key-pass "pass:$StorePass" --ks-key-alias $KeyAlias --out $DebugSigned $DebugAligned
if ($LASTEXITCODE -ne 0) {
    throw "apksigner debug sign failed"
}
& (Join-Path $JavaHome "bin\java.exe") -jar $ApkSigner verify --verbose $DebugSigned
if ($LASTEXITCODE -ne 0) {
    throw "apksigner debug verify failed"
}
Get-Item -LiteralPath $DebugSigned | Select-Object FullName,Length,LastWriteTime
