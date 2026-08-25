[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$OutputEncoding = [Text.UTF8Encoding]::new($false)
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$composeFile = Join-Path $repositoryRoot "compose.yaml"
$executionId = ([Guid]::NewGuid().ToString("N")).Substring(0, 10)
$projectName = "rmm-ecosystem-test-$executionId"
$runtimeDirectory = Join-Path ([IO.Path]::GetTempPath()) $projectName
$usersFile = Join-Path $runtimeDirectory "mqtt-users.test"
$backendUsername = "rmm-backend"
$backendPassword = "backend-container-test-password"
$machineUsername = "RMM-TM-ST046-01"
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
    Invoke-Compose -Arguments @("ps", "--all")
    Invoke-Compose -Arguments @("logs", "--no-color", "mysql", "mosquitto", "backend")
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
    Invoke-Compose -Arguments @("logs", "--no-color", "mosquitto", "backend")
    throw "El backend no confirmó su conexión autenticada con Mosquitto"
}

function Test-OperatorApiFlow {
    param([Parameter(Mandatory)][int]$Port)

    $baseUri = "http://127.0.0.1:$Port/api"
    $session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $csrf = Invoke-RestMethod -Uri "$baseUri/auth/csrf" -WebSession $session `
            -TimeoutSec 5
    if ([string]::IsNullOrWhiteSpace($csrf.headerName) `
            -or [string]::IsNullOrWhiteSpace($csrf.token)) {
        throw "El backend no ha entregado un token CSRF válido"
    }
    $headers = @{}
    $headers[$csrf.headerName] = $csrf.token
    $loginBody = @{
        identifier = $env:OPERATOR_USERNAME
        password = $env:OPERATOR_PASSWORD
    } | ConvertTo-Json
    $operator = Invoke-RestMethod -Uri "$baseUri/auth/login" -Method Post `
            -WebSession $session -Headers $headers -ContentType "application/json" `
            -Body $loginBody -TimeoutSec 5
    if ($operator.username -ne $env:OPERATOR_USERNAME) {
        throw "La sesión autenticada no corresponde al operador de prueba"
    }

    $currentOperator = Invoke-RestMethod -Uri "$baseUri/auth/me" `
            -WebSession $session -TimeoutSec 5
    $dashboard = Invoke-RestMethod -Uri "$baseUri/dashboard/summary" `
            -WebSession $session -TimeoutSec 10
    $networkMap = Invoke-RestMethod -Uri "$baseUri/network-map" `
            -WebSession $session -TimeoutSec 10
    $titles = Invoke-RestMethod -Uri "$baseUri/transport-titles" `
            -WebSession $session -TimeoutSec 10

    if ($currentOperator.username -ne $env:OPERATOR_USERNAME) {
        throw "La sesión del operador no se ha conservado"
    }
    if ($null -eq $dashboard.network -or $null -eq $dashboard.fleet `
            -or @($networkMap.lines).Count -eq 0) {
        throw "Las consultas operativas no han devuelto información"
    }
    if (@($titles.titles).Count -lt 4) {
        throw "El catálogo no contiene todos los títulos de transporte"
    }
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
$env:OPERATOR_USERNAME = "container-admin"
$env:OPERATOR_EMAIL = "container-admin@rmm.local"
$env:OPERATOR_PASSWORD = "ContainerTestPassword-2026"
$env:OPERATOR_FIRST_NAME = "Container"
$env:OPERATOR_LAST_NAME = "Administrator"

try {
    New-Item -ItemType Directory -Path $runtimeDirectory -Force | Out-Null
    @(
        "$backendUsername=$backendPassword"
        "$machineUsername=$machinePassword"
    ) | Set-Content -LiteralPath $usersFile -Encoding utf8
    & (Join-Path $repositoryRoot "infrastructure\mosquitto\scripts\initialize-security.ps1") `
            -UsersFile $usersFile -RuntimeDirectory $runtimeDirectory
    if ($LASTEXITCODE -ne 0) { throw "No se pudo preparar la seguridad MQTT" }

    try {
        Invoke-Compose -Arguments @("up", "--detach", "--build")
    } catch {
        Invoke-Compose -Arguments @("ps", "--all")
        Invoke-Compose -Arguments @("logs", "--no-color", "mysql", "mosquitto", "backend")
        throw
    }
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

    Write-Host "Comprobando identidades MQTT, tipos y estaciones del inventario..."
    $identityIssues = & docker exec "$projectName-mysql" `
        mysql -N -u root "-p$($env:MYSQL_ROOT_PASSWORD)" transport_simulator_db `
        -e "SELECT COUNT(*) FROM devices d JOIN stations s ON s.id = d.station_id LEFT JOIN device_mqtt_identities i ON i.device_id = d.id WHERE i.id IS NULL OR i.mqtt_client_id <> d.code OR d.code <> CONCAT(CASE d.device_type WHEN 'TICKET_MACHINE' THEN 'RMM-TM-' WHEN 'ENTRY_VALIDATOR' THEN 'RMM-EN-' WHEN 'EXIT_VALIDATOR' THEN 'RMM-EX-' ELSE 'INVALID-' END, s.code, '-', RIGHT(d.code, 2));"
    if ($LASTEXITCODE -ne 0) { throw "No se pudo verificar el inventario MQTT" }
    if (($identityIssues -join "`n").Trim() -ne "0") {
        throw "El inventario contiene máquinas con identidad MQTT, tipo o estación incoherentes"
    }

    Write-Host "Comprobando la codificación de MySQL y de los datos iniciales..."
    $encodingVerification = Join-Path $repositoryRoot "database\verification\verify_encoding.sql"
    $encodingIssues = Get-Content -LiteralPath $encodingVerification -Raw -Encoding utf8 |
        & docker exec -i "$projectName-mysql" mysql -N -u root `
            "-p$($env:MYSQL_ROOT_PASSWORD)" transport_simulator_db
    if ($LASTEXITCODE -ne 0) { throw "No se pudo verificar la codificación de MySQL" }
    $encodingIssueCount = ($encodingIssues -join "`n").Trim()
    if ($encodingIssueCount -ne "0") {
        throw "Se detectaron $encodingIssueCount problemas de codificación en MySQL"
    }

    Write-Host "Comprobando la conexión del backend con Mosquitto..."
    Wait-ForBackendMqttConnection

    Write-Host "Comprobando autenticación y consultas funcionales del ecosistema..."
    Test-OperatorApiFlow -Port $backendPort

    Write-Host "Ecosistema validado: infraestructura, autenticación y consultas operativas funcionan."
} finally {
    try { Invoke-Compose -Arguments @("down", "--volumes", "--remove-orphans") }
    catch { Write-Warning "No se pudieron retirar todos los contenedores de prueba: $_" }
    if (Test-Path -LiteralPath $runtimeDirectory) {
        Remove-Item -LiteralPath $runtimeDirectory -Recurse -Force
    }
}
