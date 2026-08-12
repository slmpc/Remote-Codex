param(
    [ValidateRange(1, 65535)]
    [int]$Port = 8787
)

$ErrorActionPreference = "Stop"
$serverDirectory = Join-Path $PSScriptRoot "Server"

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw "未找到 Node.js。请先安装 Node.js 20 或更高版本，再重新打开终端。"
}
if (-not (Get-Command codex -ErrorAction SilentlyContinue)) {
    throw "未找到 Codex CLI。请先安装 Codex CLI 并完成登录。"
}

$env:REMOTE_CODEX_PORT = $Port.ToString()

Write-Host ""
Write-Host "Remote Codex 1.2.2"
Write-Host "保持此窗口开启；按 Ctrl+C 停止服务。"
Write-Host ""

Push-Location $serverDirectory
try {
    & node src/server.js
    if ($LASTEXITCODE -ne 0) { throw "Remote Codex 服务异常退出（代码 $LASTEXITCODE）。" }
} finally {
    Pop-Location
}
