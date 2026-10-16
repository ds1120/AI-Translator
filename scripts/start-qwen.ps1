param(
    [switch]$Configure,
    [ValidateSet('TH', 'EN', 'JA')][string]$Language
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$configPath = Join-Path $projectRoot 'config/application.properties'
$jarPath = Join-Path $projectRoot 'backend/target/ai-translator-0.1.0.jar'
if (!(Test-Path -LiteralPath $configPath)) {
    throw 'Copy config/application.properties.example to config/application.properties and fill in app.line.secret and app.line.token.'
}
if ($Configure) {
    Write-Host "Edit settings in: $configPath"
    return
}
if (!(Test-Path -LiteralPath $jarPath)) {
    throw 'Build first: mvn -f backend/pom.xml clean verify'
}
if (Get-NetTCPConnection -State Listen -LocalPort 8090 -ErrorAction SilentlyContinue) {
    throw 'Port 8090 is in use. Stop the existing translator with Ctrl+C first.'
}
Push-Location $projectRoot
try {
    $javaArguments = @('-jar', $jarPath)
    if ($PSBoundParameters.ContainsKey('Language')) {
        $javaArguments += '--app.translation.partner-language=' + $Language.ToUpperInvariant()
    }
    & java @javaArguments
    if ($LASTEXITCODE -ne 0) { throw "Java exited with code $LASTEXITCODE" }
} finally { Pop-Location }
