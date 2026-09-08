@echo off
setlocal
chcp 65001 >nul
title EFucoser ESP8266 ULN2003 Firmware Flasher - Erase
cd /d %~dp0
%~dp0runtime\python.exe %~dp0flash.py --erase
endlocal
