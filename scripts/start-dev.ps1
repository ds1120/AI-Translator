param([int]$Port = 8090)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) { throw "Port $Port is already in use." }
Push-Location $root
try {
    # Isolated mock configuration: no production LINE/API keys or file database.
    & java -jar backend/target/ai-translator-0.1.0.jar --spring.config.location=classpath:/ --spring.profiles.active=mock "--server.port=$Port" --debug=false
    if ($LASTEXITCODE -ne 0) { throw "Development server exited with code $LASTEXITCODE" }
} finally { Pop-Location }
