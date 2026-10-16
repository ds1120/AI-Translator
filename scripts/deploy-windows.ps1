param(
    [string]$Server = '192.168.1.120',
    [string]$User = 'ds1120',
    [string]$ProjectRoot = 'C:\ai\project\AI-Translator',
    [string]$IdentityFile = "$env:USERPROFILE\.ssh\id_ai_translator_migration",
    [string]$JarPath
)
$ErrorActionPreference = 'Stop'
$localRoot = Split-Path -Parent $PSScriptRoot
if (!$JarPath) { $JarPath = Join-Path $localRoot 'backend\target\ai-translator-0.1.0.jar' }
foreach ($file in @($JarPath, $IdentityFile)) { if (!(Test-Path -LiteralPath $file)) { throw "Required file missing: $file" } }
$destination = $User + '@' + $Server
$sshArgs = @('-i', $IdentityFile, '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=accept-new')
function Invoke-Server([string]$Script) {
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes("`$ProgressPreference='SilentlyContinue';`$ErrorActionPreference='Stop';" + $Script))
    & ssh @sshArgs $destination "powershell.exe -NoProfile -NonInteractive -EncodedCommand $encoded"
    if ($LASTEXITCODE -ne 0) { throw 'Remote command failed.' }
}
$quotedRoot = "'" + $ProjectRoot.Replace("'", "''") + "'"
Invoke-Server ("New-Item -ItemType Directory -Force (Join-Path $quotedRoot 'backend\target'),(Join-Path $quotedRoot 'scripts') | Out-Null")
$remoteRoot = $ProjectRoot.Replace('\','/')
& scp -i $IdentityFile $JarPath ($destination + ':' + $remoteRoot + '/backend/target/ai-translator-0.1.0.jar.next')
if ($LASTEXITCODE -ne 0) { throw 'JAR upload failed.' }
& scp -i $IdentityFile (Join-Path $PSScriptRoot 'install-windows-server.ps1') ($destination + ':' + $remoteRoot + '/scripts/install-windows-server.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Installer upload failed.' }
Invoke-Server ("& (Join-Path $quotedRoot 'scripts\install-windows-server.ps1') -ProjectRoot $quotedRoot")
Invoke-Server @'
$deadline = (Get-Date).AddMinutes(2)
while ((Get-Date) -lt $deadline) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8090/actuator/health -TimeoutSec 5
        if ($response.StatusCode -eq 200) { 'Backend health: 200'; exit 0 }
    } catch {}
    Start-Sleep -Seconds 2
}
throw 'Backend did not become healthy; inspect logs\server.log on the server.'
'@
# Runtime, Caddyfile, credentials and database are provisioned separately once.
# Subsequent releases replace only the JAR and installer; production settings and DB remain on the server.
