$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    if (Get-NetTCPConnection -State Listen -LocalPort 8090 -ErrorAction SilentlyContinue) {
        throw 'Stop the translator with Ctrl+C before replacing its JAR.'
    }
    Push-Location (Join-Path $projectRoot 'frontend')
    try {
        & npm.cmd ci
        if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
    } finally { Pop-Location }
    & mvn -B -ntp -f backend/pom.xml clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Backend verification failed.' }
} finally { Pop-Location }
