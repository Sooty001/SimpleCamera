@echo off
chcp 65001 > nul

powershell.exe -ExecutionPolicy Bypass -Command "Get-ChildItem -Path . -Include *.kt, *.xml -Recurse | Where-Object { $_.FullName -notlike '*\target\*' } | ForEach-Object { '--- ' + $_.FullName + ' ---'; Get-Content -LiteralPath $_.FullName -Encoding UTF8 } | Out-File -FilePath 'temp_output.txt' -Encoding utf8"