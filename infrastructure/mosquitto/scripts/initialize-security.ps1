[CmdletBinding()]
param(
    [string]$UsersFile,
    [string]$RuntimeDirectory
)

$ErrorActionPreference = "Stop"

$mosquittoDirectory = Split-Path -Parent $PSScriptRoot
$usersPath = if ($UsersFile) {
    $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($UsersFile)
} else {
    Join-Path $mosquittoDirectory "mqtt-users.local"
}
$runtimePath = if ($RuntimeDirectory) {
    $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($RuntimeDirectory)
} else {
    Join-Path $mosquittoDirectory "runtime"
}

if (-not (Test-Path -LiteralPath $usersPath -PathType Leaf)) {
    throw "No existe $usersPath. Copia mqtt-users.example como mqtt-users.local y configura sus valores."
}

$credentials = [ordered]@{}
foreach ($rawLine in Get-Content -LiteralPath $usersPath) {
    $line = $rawLine.Trim()
    if (-not $line -or $line.StartsWith("#")) {
        continue
    }

    $separator = $line.IndexOf("=")
    if ($separator -lt 1) {
        throw "Línea de credenciales MQTT no válida: $rawLine"
    }

    $username = $line.Substring(0, $separator).Trim()
    $password = $line.Substring($separator + 1)
    if (-not $password -or $password.StartsWith("replace_with_")) {
        throw "La identidad $username no tiene una contraseña local válida"
    }
    if ($password.Length -lt 12) {
        throw "La contraseña de $username debe contener al menos 12 caracteres"
    }
    if ($username -ne "rmm-backend" -and
            -not $username.StartsWith("RMM-TM-") -and
            -not $username.StartsWith("RMM-EN-") -and
            -not $username.StartsWith("RMM-EX-")) {
        throw "Identidad MQTT no admitida: $username"
    }
    if ($credentials.Contains($username)) {
        throw "Identidad MQTT duplicada: $username"
    }
    if ($credentials.Values -contains $password) {
        throw "Cada identidad MQTT debe utilizar una contraseña diferente"
    }

    $credentials[$username] = $password
}

if (-not $credentials.Contains("rmm-backend")) {
    throw "Debe existir la identidad de servicio rmm-backend"
}
if ($credentials.Count -lt 2) {
    throw "Debe configurarse al menos una máquina además del backend"
}

New-Item -ItemType Directory -Path $runtimePath -Force | Out-Null
$passwordFile = Join-Path $runtimePath "password_file"
$aclFile = Join-Path $runtimePath "acl_file"

$utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
$passwordLines = $credentials.GetEnumerator() |
    ForEach-Object { "{0}:{1}" -f $_.Key, $_.Value }
[System.IO.File]::WriteAllLines($passwordFile, $passwordLines, $utf8WithoutBom)

$runtimeMount = "{0}:/work" -f $runtimePath.Replace("\", "/")
try {
    docker run --rm --volume $runtimeMount eclipse-mosquitto:2.0.22-openssl `
        mosquitto_passwd -U /work/password_file
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo cifrar el archivo de contraseñas MQTT"
    }
} catch {
    Remove-Item -LiteralPath $passwordFile -Force -ErrorAction SilentlyContinue
    throw
}

$acl = [System.Collections.Generic.List[string]]::new()
$acl.Add("user rmm-backend")
$acl.Add("topic read rmm/v1/devices/+/presence")
$acl.Add("topic read rmm/v1/devices/+/status")
$acl.Add("topic read rmm/v1/devices/+/telemetry")
$acl.Add("topic read rmm/v1/devices/+/events/+")
$acl.Add("topic read rmm/v1/devices/+/requests/validations")
$acl.Add("topic read rmm/v1/devices/+/requests/purchases")
$acl.Add("topic read rmm/v1/devices/+/acks")
$acl.Add("topic write rmm/v1/devices/+/commands")
$acl.Add("topic write rmm/v1/devices/+/responses")
$acl.Add("topic write rmm/v1/devices/+/configuration")
$acl.Add("topic write rmm/v1/configuration/qr-public-keys")

foreach ($username in $credentials.Keys | Where-Object { $_ -ne "rmm-backend" }) {
    $acl.Add("")
    $acl.Add("user $username")
    $acl.Add("topic write rmm/v1/devices/$username/presence")
    $acl.Add("topic write rmm/v1/devices/$username/status")
    $acl.Add("topic write rmm/v1/devices/$username/telemetry")
    $acl.Add("topic write rmm/v1/devices/$username/events/+")
    $acl.Add("topic write rmm/v1/devices/$username/acks")
    if ($username.StartsWith("RMM-EN-") -or $username.StartsWith("RMM-EX-")) {
        $acl.Add("topic write rmm/v1/devices/$username/requests/validations")
    }
    if ($username.StartsWith("RMM-TM-")) {
        $acl.Add("topic write rmm/v1/devices/$username/requests/purchases")
    }
    $acl.Add("topic read rmm/v1/devices/$username/commands")
    $acl.Add("topic read rmm/v1/devices/$username/responses")
    $acl.Add("topic read rmm/v1/devices/$username/configuration")
    $acl.Add("topic read rmm/v1/configuration/qr-public-keys")
}

[System.IO.File]::WriteAllLines($aclFile, $acl, $utf8WithoutBom)

Write-Host "Seguridad MQTT generada para $($credentials.Count) identidades en $runtimePath"
