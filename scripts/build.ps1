$ErrorActionPreference = 'Stop'
function Invoke-BuildStep {
    param([string]$Command, [string[]]$Arguments, [string]$Failure)
    Get-Command $Command -ErrorAction Stop | Out-Null
    $previousPreference = $ErrorActionPreference
    try {
        # Windows PowerShell can treat native stderr warnings as terminating errors
        # when output is redirected. The process exit code determines success.
        $ErrorActionPreference = 'Continue'
        & $Command @Arguments
        $nativeExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($nativeExit -ne 0) { throw $Failure }
}
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    if (Get-NetTCPConnection -State Listen -LocalPort 8090 -ErrorAction SilentlyContinue) {
        throw 'Stop the translator with Ctrl+C before replacing its JAR.'
    }
    Push-Location (Join-Path $projectRoot 'frontend')
    try {
        Invoke-BuildStep -Command npm.cmd -Arguments @('ci') -Failure 'Frontend dependency installation failed.'
        Invoke-BuildStep -Command npm.cmd -Arguments @('run', 'build') -Failure 'Frontend build failed.'
    } finally { Pop-Location }
    Invoke-BuildStep -Command mvn -Arguments @('-B', '-ntp', '-f', 'backend/pom.xml', 'clean', 'verify') -Failure 'Backend verification failed.'
} finally { Pop-Location }
