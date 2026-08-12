@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-server.ps1"
if errorlevel 1 (
  echo.
  echo Start failed. Read the error above, then press any key to close.
  pause >nul
)
