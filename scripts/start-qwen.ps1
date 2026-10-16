param(
    [switch]$Configure,
    [ValidateSet('TH', 'EN', 'JA')][string]$Language = 'TH'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$credentialsPath = Join-Path $projectRoot '.env.line.credentials.xml'
$jarPath = Join-Path $projectRoot 'backend/target/ai-translator-0.1.0.jar'
if (!(Test-Path -LiteralPath $jarPath)) {
    throw 'Build first: mvn -f backend/pom.xml clean verify'
}
if (!$Configure -and (Get-NetTCPConnection -State Listen -LocalPort 8090 -ErrorAction SilentlyContinue)) {
    throw 'Port 8090 is in use. Stop the existing translator with Ctrl+C first.'
}

$settings = @{
    SPRING_PROFILES_ACTIVE = 'qwen'
    SERVER_ADDRESS = '127.0.0.1'
    SERVER_PORT = '8090'
    AI_PROVIDER = 'local-qwen'
    AI_BASE_URL = 'http://127.0.0.1:8082/v1'
    AI_MODEL = 'Qwen3-14B'
    LINE_PUSH_ENABLED = 'false'
    TRANSLATION_PARTNER_LANGUAGE = $Language.ToUpperInvariant()
    DB_URL = 'jdbc:h2:file:./backend/data/translator;MODE=MariaDB;DB_CLOSE_ON_EXIT=FALSE'
    DB_USER = 'sa'
    DB_PASSWORD = ''
}
$previous = @{}
try {
    $credentials = @{}
    if (!$Configure -and (Test-Path -LiteralPath $credentialsPath)) {
        $credentials = Import-Clixml -LiteralPath $credentialsPath
    }
    foreach ($name in @('LINE_CHANNEL_SECRET', 'LINE_CHANNEL_ACCESS_TOKEN')) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        if ($Configure -or !($credentials[$name] -is [Security.SecureString])) {
            if (!$Configure -and ![string]::IsNullOrWhiteSpace($previous[$name])) {
                $credentials[$name] = ConvertTo-SecureString $previous[$name] -AsPlainText -Force
            } else {
                $inputValue = Read-Host "Enter $name (hidden)" -AsSecureString
                $credentials[$name] = $inputValue
            }
        }
        $value = [System.Net.NetworkCredential]::new('', $credentials[$name]).Password
        if ([string]::IsNullOrWhiteSpace($value)) { throw "$name is required." }
        [Environment]::SetEnvironmentVariable($name, $value.Trim(), 'Process')
        $value = $null
    }
    # Windows DPAPI encrypts SecureString values for this Windows user on this PC.
    $credentials | Export-Clixml -LiteralPath $credentialsPath -Encoding UTF8
    Write-Host 'LINE credentials saved locally (encrypted for this Windows account).'
    if ($Configure) { return }
    foreach ($name in $settings.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process')
    }
    New-Item -ItemType Directory -Path (Join-Path $projectRoot 'backend/data') -Force | Out-Null
    Push-Location $projectRoot
    try {
        & java -jar $jarPath
        if ($LASTEXITCODE -ne 0) { throw "Java exited with code $LASTEXITCODE" }
    } finally { Pop-Location }
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}
