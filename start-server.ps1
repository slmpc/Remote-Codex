$ErrorActionPreference = "Stop"
$serverDir = Join-Path $PSScriptRoot "Server"
Set-Location $serverDir
node src/server.js
