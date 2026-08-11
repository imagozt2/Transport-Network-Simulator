[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$composeFile = Join-Path $repositoryRoot "compose.yaml"
$executionId = ([Guid]::NewGuid().ToString("N")).Substring(0, 10)
$projectName = "rmm-ecosystem-test-$executionId"
$runtimeDirectory = Join-Path ([IO.Path]::GetTempPath()) $projectName
$usersFile = Join-Path $runtimeDirectory "mqtt-users.test"
$backendUsername = "rmm-backend"
$backendPassword = "backend-container-test-password"
$machineUsername = "RMM-SALE-ST046-01"
$machinePassword = "machine-container-test-password"

function Get-FreeTcpPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return ([Net.IPEndPoint]$listener.LocalEndpoint).Port }
    finally { $listener.Stop() }
}

function Invoke-Compose {
    param([Parameter(Mandatory)][string[]]$Arguments)
    & docker compose --project-name $projectName --file $composeFile @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose $($Arguments -join ' ') terminó con código $LASTEXITCODE"
    }
}

function Wait-ForBackend {
    param([Parameter(Mandatory)][int]$Port)
    for ($attempt = 1; $attempt -le 40; $attempt++) {
        try {
            $response = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/health" `
                    -TimeoutSec 3
            if ($response.status -eq "UP" -and $response.database -eq "UP") {
                return
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    Invoke-Compose -Arguments @("logs", "backend")
    throw "El backend no alcanzó un estado saludable con MySQL"
}

function Wait-ForBackendMqttConnection {
    for ($attempt = 1; $attempt -le 20; $attempt++) {
        $logs = (& docker compose --project-name $projectName --file $composeFile `
                logs --no-color backend 2>&1) | Out-String
        if ($LASTEXITCODE -eq 0 -and $logs -match "MQTT client rmm-backend connected") {
            return
        }
        Start-Sleep -Seconds 1
    }
    Invoke-Compose -Arguments @("logs", "backend")
    throw "El backend no confirmó su conexión autenticada con Mosquitto"
}

$mysqlPort = Get-FreeTcpPort
$mqttPort = Get-FreeTcpPort
$backendPort = Get-FreeTcpPort
$env:MYSQL_ROOT_PASSWORD = "root-container-test-password"
$env:DB_USERNAME = "rmm_container_test"
$env:DB_PASSWORD = "database-container-test-password"
$env:MQTT_BACKEND_USERNAME = $backendUsername
$env:MQTT_BACKEND_PASSWORD = $backendPassword
$env:MYSQL_HOST_PORT = "$mysqlPort"
$env:RMM_MQTT_HOST_PORT = "$mqttPort"
$env:BACKEND_HOST_PORT = "$backendPort"
$env:MYSQL_CONTAINER_NAME = "$projectName-mysql"
$env:MOSQUITTO_CONTAINER_NAME = "$projectName-mosquitto"
$env:BACKEND_CONTAINER_NAME = "$projectName-backend"
$env:RMM_NETWORK_NAME = "$projectName-network"
$env:MOSQUITTO_RUNTIME_DIRECTORY = $runtimeDirectory.Replace("\", "/")
$env:DEVICE_EVENT_SIMULATION_ENABLED = "false"

try {
    New-Item -ItemType Directory -Path $runtimeDirectory -Force | Out-Null
    @(
        "$backendUsername=$backendPassword"
        "$machineUsername=$machinePassword"
    ) | Set-Content -LiteralPath $usersFile -Encoding utf8
    & (Join-Path $repositoryRoot "infrastructure\mosquitto\scripts\initialize-security.ps1") `
            -UsersFile $usersFile -RuntimeDirectory $runtimeDirectory
    if ($LASTEXITCODE -ne 0) { throw "No se pudo preparar la seguridad MQTT" }

    Invoke-Compose -Arguments @("up", "--detach", "--build", "--wait", "--wait-timeout", "240")
    Wait-ForBackend -Port $backendPort

    Write-Host "Comprobando el esquema y los datos iniciales de MySQL..."
    $databaseCounts = & docker exec "$projectName-mysql" `
        mysql -N -u root "-p$($env:MYSQL_ROOT_PASSWORD)" transport_simulator_db `
        -e "SELECT (SELECT COUNT(*) FROM transport_lines), (SELECT COUNT(*) FROM stations), (SELECT COUNT(*) FROM devices), (SELECT COUNT(*) FROM ticket_products);"
    if ($LASTEXITCODE -ne 0) { throw "No se pudo consultar MySQL" }
    $counts = ($databaseCounts -join "`n").Trim() -split "\s+"
    if ($counts.Count -ne 4 -or ($counts | Where-Object { [int]$_ -le 0 })) {
        throw "El contenedor MySQL no contiene todos los datos iniciales requeridos"
    }

    Write-Host "Comprobando la conexión del backend con Mosquitto..."
    Wait-ForBackendMqttConnection

    Write-Host "Ecosistema validado: backend, MySQL y Mosquitto están conectados y saludables."
} finally {
    try { Invoke-Compose -Arguments @("down", "--volumes", "--remove-orphans") }
    catch { Write-Warning "No se pudieron retirar todos los contenedores de prueba: $_" }
    if (Test-Path -LiteralPath $runtimeDirectory) {
        Remove-Item -LiteralPath $runtimeDirectory -Recurse -Force
    }
}
