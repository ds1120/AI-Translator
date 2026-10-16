param(
    [string]$ProjectRoot = 'C:\ai\project\AI-Translator',
    [string]$JavaPath = 'C:\ai\runtime\java\bin\java.exe',
    [string]$CaddyRoot = 'C:\ai\caddy'
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$ProjectRoot = [IO.Path]::GetFullPath($ProjectRoot)
$jar = Join-Path $ProjectRoot 'backend\target\ai-translator-0.1.0.jar'
$next = $jar + '.next'
$config = Join-Path $ProjectRoot 'config\application.properties'
$caddy = Join-Path $CaddyRoot 'caddy.exe'
$caddyfile = Join-Path $CaddyRoot 'Caddyfile'
foreach ($required in @($JavaPath, $config, $caddy, $caddyfile)) {
    if (!(Test-Path -LiteralPath $required)) { throw "Required file missing: $required" }
}
if (!(Test-Path -LiteralPath $jar) -and !(Test-Path -LiteralPath $next)) { throw 'No JAR to deploy.' }
# Validate before stopping a running instance.
& $caddy validate --config $caddyfile
if ($LASTEXITCODE -ne 0) { throw 'Caddy validation failed.' }
if (Test-Path -LiteralPath $next) {
    if (Get-ScheduledTask -TaskName 'AI-Translator' -ErrorAction SilentlyContinue) {
        Stop-ScheduledTask -TaskName 'AI-Translator'
    }
    $listener = Get-NetTCPConnection -State Listen -LocalPort 8090 -ErrorAction SilentlyContinue
    foreach ($item in $listener) {
        $server = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $item.OwningProcess)
        if ($server.CommandLine -notlike '*ai-translator-0.1.0.jar*') { throw '8090 belongs to another application.' }
        Stop-Process -Id $server.ProcessId
    }
    if (Test-Path -LiteralPath $jar) { Copy-Item -LiteralPath $jar -Destination ($jar + '.previous') -Force }
    Copy-Item -LiteralPath $next -Destination $jar -Force
    Remove-Item -LiteralPath $next
}
New-Item -ItemType Directory -Force (Join-Path $ProjectRoot 'logs'), (Join-Path $CaddyRoot 'logs') | Out-Null
# API keys and TLS storage must not inherit access for ordinary local users.
& icacls $config /inheritance:r /grant:r '*S-1-5-32-544:F' '*S-1-5-18:F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Unable to restrict configuration ACL.' }
foreach ($folder in @((Join-Path $CaddyRoot 'data'), (Join-Path $ProjectRoot 'backend\data'))) {
    if (!(Test-Path -LiteralPath $folder)) { continue }
    foreach ($entry in @((Get-Item -LiteralPath $folder)) + @(Get-ChildItem -LiteralPath $folder -Recurse -Force)) {
        $adminGrant = if ($entry.PSIsContainer) { '*S-1-5-32-544:(OI)(CI)F' } else { '*S-1-5-32-544:F' }
        $systemGrant = if ($entry.PSIsContainer) { '*S-1-5-18:(OI)(CI)F' } else { '*S-1-5-18:F' }
        & icacls $entry.FullName /inheritance:r /grant:r $adminGrant $systemGrant | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Unable to restrict state directory ACL.' }
    }
}
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) -MultipleInstances IgnoreNew -StartWhenAvailable
$trigger = New-ScheduledTaskTrigger -AtStartup
$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
$javaArgs = '-jar "' + $jar + '" --logging.file.name="' + (Join-Path $ProjectRoot 'logs\server.log') + '" --debug=false'
$action = New-ScheduledTaskAction -Execute $JavaPath -Argument $javaArgs -WorkingDirectory $ProjectRoot
Register-ScheduledTask -TaskName 'AI-Translator' -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Force | Out-Null
$caddyAction = New-ScheduledTaskAction -Execute $caddy -Argument ('run --config "' + $caddyfile + '"') -WorkingDirectory $CaddyRoot
Register-ScheduledTask -TaskName 'AI-Translator-Caddy' -Action $caddyAction -Trigger $trigger -Principal $principal -Settings $settings -Force | Out-Null
foreach ($port in @(80,443)) {
    $rule = 'AI-Translator HTTP ' + $port
    if (!(Get-NetFirewallRule -DisplayName $rule -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -DisplayName $rule -Direction Inbound -Protocol TCP -LocalPort $port -Action Allow | Out-Null
    }
}
Start-ScheduledTask -TaskName 'AI-Translator'
if (!(Get-NetTCPConnection -State Listen -LocalPort 443 -ErrorAction SilentlyContinue)) {
    Start-ScheduledTask -TaskName 'AI-Translator-Caddy'
} else {
    & $caddy reload --config $caddyfile
    if ($LASTEXITCODE -ne 0) { throw 'Caddy reload failed.' }
}
'Tasks installed. Check backend health and HTTPS before redirecting traffic.'
