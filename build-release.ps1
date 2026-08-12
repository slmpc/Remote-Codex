param(
    [string]$Version = "1.3.1",
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
$workspaceRoot = $PSScriptRoot
$distDirectory = Join-Path $workspaceRoot "dist"
$clientDirectory = Join-Path $workspaceRoot "Client"
$serverDirectory = Join-Path $workspaceRoot "Server"
$signingDirectory = Join-Path ([Environment]::GetFolderPath("LocalApplicationData")) "RemoteCodex\signing"
$keystorePath = Join-Path $signingDirectory "remote-codex-release.jks"
$protectedPasswordPath = Join-Path $signingDirectory "release-password.dpapi"
$keyAlias = "remote-codex"

function New-RandomPassword {
    $bytes = New-Object byte[] 48
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    return [Convert]::ToBase64String($bytes)
}

function ConvertFrom-ProtectedPassword([string]$protectedValue) {
    $secureValue = ConvertTo-SecureString $protectedValue
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

New-Item -ItemType Directory -Force -Path $signingDirectory, $distDirectory | Out-Null

if (-not (Test-Path -LiteralPath $keystorePath)) {
    $password = New-RandomPassword
    $protectedPassword = ConvertFrom-SecureString (ConvertTo-SecureString $password -AsPlainText -Force)
    [IO.File]::WriteAllText($protectedPasswordPath, $protectedPassword)
    $env:REMOTE_CODEX_KEYTOOL_PASSWORD = $password
    & keytool -genkeypair -v `
        -keystore $keystorePath `
        -alias $keyAlias `
        -keyalg RSA `
        -keysize 4096 `
        -validity 36500 `
        -dname "CN=Remote Codex, OU=Release, O=ChenMeng, C=CN" `
        "-storepass:env" REMOTE_CODEX_KEYTOOL_PASSWORD `
        "-keypass:env" REMOTE_CODEX_KEYTOOL_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw "无法创建 Android Release 签名密钥。" }
} elseif (-not (Test-Path -LiteralPath $protectedPasswordPath)) {
    throw "签名密码文件缺失：$protectedPasswordPath"
} else {
    $password = ConvertFrom-ProtectedPassword ([IO.File]::ReadAllText($protectedPasswordPath))
}

$env:REMOTE_CODEX_KEYSTORE = $keystorePath
$env:REMOTE_CODEX_STORE_PASSWORD = $password
$env:REMOTE_CODEX_KEY_ALIAS = $keyAlias
$env:REMOTE_CODEX_KEY_PASSWORD = $password

if (-not $SkipTests) {
    Push-Location $serverDirectory
    try {
        & npm test
        if ($LASTEXITCODE -ne 0) { throw "服务端测试失败。" }
    } finally {
        Pop-Location
    }
}

Push-Location $clientDirectory
try {
    & .\gradlew.bat assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "Android Release 构建失败。" }
} finally {
    Pop-Location
}

$builtApk = Join-Path $clientDirectory "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path -LiteralPath $builtApk)) { throw "找不到 Release APK：$builtApk" }
$releaseApk = Join-Path $distDirectory "Remote-Codex-Android-v$Version.apk"
Copy-Item -LiteralPath $builtApk -Destination $releaseApk -Force

$sdkRoot = if ($env:ANDROID_SDK_ROOT) {
    $env:ANDROID_SDK_ROOT
} elseif ($env:ANDROID_HOME) {
    $env:ANDROID_HOME
} else {
    Join-Path ([Environment]::GetFolderPath("LocalApplicationData")) "Android\Sdk"
}
$buildTools = Get-ChildItem (Join-Path $sdkRoot "build-tools") -Directory | Sort-Object Name -Descending | Select-Object -First 1
$apkSigner = Join-Path $buildTools.FullName "apksigner.bat"
if (-not (Test-Path -LiteralPath $apkSigner)) { throw "找不到 Android apksigner。" }
& $apkSigner verify --verbose --print-certs $releaseApk
if ($LASTEXITCODE -ne 0) { throw "APK 签名校验失败。" }

$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ("remote-codex-release-" + [Guid]::NewGuid().ToString("N"))
$bundleRoot = Join-Path $temporaryRoot "Remote-Codex-Server-v$Version"
try {
    New-Item -ItemType Directory -Force -Path (Join-Path $bundleRoot "Server") | Out-Null
    Copy-Item -LiteralPath (Join-Path $serverDirectory "src") -Destination (Join-Path $bundleRoot "Server\src") -Recurse
    Copy-Item -LiteralPath (Join-Path $serverDirectory "package.json") -Destination (Join-Path $bundleRoot "Server\package.json")
    Copy-Item -LiteralPath (Join-Path $workspaceRoot "start-server.ps1") -Destination $bundleRoot
    Copy-Item -LiteralPath (Join-Path $workspaceRoot "start-server.cmd") -Destination $bundleRoot
    Copy-Item -LiteralPath (Join-Path $workspaceRoot "README.md") -Destination $bundleRoot
    Copy-Item -LiteralPath (Join-Path $workspaceRoot "LICENSE") -Destination $bundleRoot
    Copy-Item -LiteralPath (Join-Path $workspaceRoot "CHANGELOG.md") -Destination $bundleRoot
    Copy-Item -LiteralPath (Join-Path $workspaceRoot "RELEASE_NOTES.md") -Destination $bundleRoot
    $serverArchive = Join-Path $distDirectory "Remote-Codex-Server-v$Version.zip"
    Compress-Archive -LiteralPath $bundleRoot -DestinationPath $serverArchive -CompressionLevel Optimal -Force
} finally {
    if (Test-Path -LiteralPath $temporaryRoot) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}

$checksumLines = @($releaseApk, $serverArchive) | ForEach-Object {
    $hash = Get-FileHash -LiteralPath $_ -Algorithm SHA256
    "$($hash.Hash.ToLowerInvariant())  $([IO.Path]::GetFileName($_))"
}
$checksumFile = Join-Path $distDirectory "SHA256SUMS.txt"
[IO.File]::WriteAllLines($checksumFile, $checksumLines)

$distributionRoot = Join-Path ([IO.Path]::GetTempPath()) ("remote-codex-distribution-" + [Guid]::NewGuid().ToString("N"))
try {
    New-Item -ItemType Directory -Force -Path $distributionRoot | Out-Null
    Copy-Item -LiteralPath $releaseApk, $serverArchive, $checksumFile -Destination $distributionRoot
    Copy-Item -LiteralPath (Join-Path $workspaceRoot "README.md") -Destination $distributionRoot
    Copy-Item -LiteralPath (Join-Path $workspaceRoot "LICENSE") -Destination $distributionRoot
    Copy-Item -LiteralPath (Join-Path $workspaceRoot "RELEASE_NOTES.md") -Destination $distributionRoot
    $distributionArchive = Join-Path $distDirectory "Remote-Codex-v$Version.zip"
    Compress-Archive -Path (Join-Path $distributionRoot "*") -DestinationPath $distributionArchive -CompressionLevel Optimal -Force
} finally {
    if (Test-Path -LiteralPath $distributionRoot) {
        Remove-Item -LiteralPath $distributionRoot -Recurse -Force
    }
}

$distributionHash = Get-FileHash -LiteralPath $distributionArchive -Algorithm SHA256
$distributionChecksum = "$($distributionHash.Hash.ToLowerInvariant())  $([IO.Path]::GetFileName($distributionArchive))"
[IO.File]::WriteAllLines($checksumFile, [string[]]@($checksumLines + $distributionChecksum))

$env:REMOTE_CODEX_KEYTOOL_PASSWORD = $null
$env:REMOTE_CODEX_STORE_PASSWORD = $null
$env:REMOTE_CODEX_KEY_PASSWORD = $null
$password = $null

Write-Host "Release v$Version 已生成：$distDirectory"
