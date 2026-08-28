[CmdletBinding()]
param(
    [switch]$RunWebAccessTest
)

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
$entryValidatorUsername = "RMM-EN-ST046-01"
$entryValidatorPassword = "entry-validator-test-password"
$exitValidatorUsername = "RMM-EX-ST020-01"
$exitValidatorPassword = "exit-validator-test-password"

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

function Start-MqttCapture {
    param(
        [Parameter(Mandatory)][string]$Topic,
        [Parameter(Mandatory)][string]$ContainerPath,
        [string]$Username = $machineUsername,
        [string]$Password = $machinePassword
    )
    & docker exec --detach $env:MOSQUITTO_CONTAINER_NAME sh -c `
        "mosquitto_sub -h 127.0.0.1 -p 1883 -u '$Username' -P '$Password' -t '$Topic' -q 1 -C 1 -W 20 > '$ContainerPath' 2>'$ContainerPath.err'"
    if ($LASTEXITCODE -ne 0) { throw "No se pudo iniciar el suscriptor MQTT de prueba" }
    Start-Sleep -Milliseconds 750
}

function Wait-MqttCapture {
    param([Parameter(Mandatory)][string]$ContainerPath)
    for ($attempt = 1; $attempt -le 80; $attempt++) {
        $message = & docker exec $env:MOSQUITTO_CONTAINER_NAME sh -c `
            "if [ -s '$ContainerPath' ]; then cat '$ContainerPath'; fi"
        if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace(($message -join "`n"))) {
            return ($message -join "`n").Trim()
        }
        Start-Sleep -Milliseconds 250
    }
    $errorMessage = & docker exec $env:MOSQUITTO_CONTAINER_NAME sh -c `
        "if [ -s '$ContainerPath.err' ]; then cat '$ContainerPath.err'; fi"
    throw "No se recibio el mensaje MQTT esperado: $(($errorMessage -join "`n").Trim())"
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

function Test-TicketMachinePurchaseFlow {
    # The backend connection is asynchronous: allow Mosquitto to acknowledge all
    # topic subscriptions before publishing the one-shot purchase request.
    Start-Sleep -Seconds 2

    $presenceFileName = "ticket-machine-presence.json"
    $presenceFile = Join-Path $runtimeDirectory $presenceFileName
    $presence = @{
        schemaVersion = 1
        state = "ONLINE"
        reason = "FUNCTIONAL_TEST"
        changedAt = [DateTimeOffset]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
    } | ConvertTo-Json -Compress
    [IO.File]::WriteAllText($presenceFile, $presence, [Text.UTF8Encoding]::new($false))
    & docker exec $env:MOSQUITTO_CONTAINER_NAME mosquitto_pub `
        -h 127.0.0.1 -p 1883 -u $machineUsername -P $machinePassword `
        -t "rmm/v1/devices/$machineUsername/presence" `
        -q 1 -r -f "/mosquitto/security/$presenceFileName"
    if ($LASTEXITCODE -ne 0) {
        throw "La maquina de venta no pudo anunciar su presencia MQTT"
    }

    $statusFileName = "ticket-machine-status.json"
    $statusFile = Join-Path $runtimeDirectory $statusFileName
    $statusTime = [DateTimeOffset]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
    $status = @{
        schemaVersion = 1
        messageId = [Guid]::NewGuid().ToString()
        correlationId = $null
        type = "device.status-reported"
        deviceCode = $machineUsername
        occurredAt = $statusTime
        sentAt = $statusTime
        payload = @{
            operationalState = "AVAILABLE"
            serviceMode = "FUNCTIONAL_TEST"
            softwareVersion = "ci"
            uptimeSeconds = 1
        }
    } | ConvertTo-Json -Depth 5 -Compress
    [IO.File]::WriteAllText($statusFile, $status, [Text.UTF8Encoding]::new($false))
    & docker exec $env:MOSQUITTO_CONTAINER_NAME mosquitto_pub `
        -h 127.0.0.1 -p 1883 -u $machineUsername -P $machinePassword `
        -t "rmm/v1/devices/$machineUsername/status" `
        -q 1 -r -f "/mosquitto/security/$statusFileName"
    if ($LASTEXITCODE -ne 0) {
        throw "La maquina de venta no pudo publicar su estado operativo"
    }
    Start-Sleep -Seconds 2

    $purchaseReference = [Guid]::NewGuid().ToString()
    $messageId = [Guid]::NewGuid().ToString()
    $occurredAt = [DateTimeOffset]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
    $requestFileName = "ticket-machine-purchase-$purchaseReference.json"
    $requestFile = Join-Path $runtimeDirectory $requestFileName
    $commandContainerPath = "/tmp/ticket-machine-command-$purchaseReference.json"
    $request = @{
        schemaVersion = 1
        messageId = $messageId
        correlationId = $null
        type = "ticket.purchase-requested"
        deviceCode = $machineUsername
        occurredAt = $occurredAt
        sentAt = $occurredAt
        payload = @{
            purchaseReference = $purchaseReference
            productCode = "MULTI_TRIP"
            paymentMethod = "SIMULATED"
            paidAmount = 2.00
            currency = "EUR"
            configuration = @{ quantity = 2 }
        }
    } | ConvertTo-Json -Depth 6 -Compress
    [IO.File]::WriteAllText($requestFile, $request, [Text.UTF8Encoding]::new($false))

    Start-MqttCapture -Topic "rmm/v1/devices/$machineUsername/commands" `
        -ContainerPath $commandContainerPath
    & docker exec $env:MOSQUITTO_CONTAINER_NAME mosquitto_pub `
        -h 127.0.0.1 -p 1883 -u $machineUsername -P $machinePassword `
        -t "rmm/v1/devices/$machineUsername/requests/purchases" `
        -q 1 -f "/mosquitto/security/$requestFileName"
    if ($LASTEXITCODE -ne 0) {
        throw "La máquina de venta no pudo publicar la solicitud de compra"
    }

    $command = Wait-MqttCapture -ContainerPath $commandContainerPath | ConvertFrom-Json
    if ($command.type -ne "ticket.issue-command" `
            -or $command.correlationId -ne $purchaseReference `
            -or $command.payload.issuanceKind -ne "PURCHASE" `
            -or [string]::IsNullOrWhiteSpace($command.payload.ticket.ticketCode) `
            -or -not $command.payload.ticket.qrValue.StartsWith("RMM:TICKET:2:") `
            -or [string]::IsNullOrWhiteSpace($command.payload.ticket.qrPngBase64)) {
        throw "La orden de emisión no contiene un billete y un QR válidos"
    }

    $escapedReference = $purchaseReference.Replace("'", "''")
    $persistenceResult = & docker exec $env:MYSQL_CONTAINER_NAME `
        mysql -N -u root "-p$($env:MYSQL_ROOT_PASSWORD)" transport_simulator_db `
        -e "SELECT COUNT(*), COUNT(p.ticket_id), COUNT(q.id) FROM purchases p LEFT JOIN ticket_qr_credentials q ON q.ticket_id = p.ticket_id WHERE p.external_reference = '$escapedReference' AND p.purchase_status = 'COMPLETED';"
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo comprobar la persistencia de la compra"
    }
    $counts = (($persistenceResult -join "`n").Trim() -split "\s+")
    if ($counts.Count -ne 3 -or $counts[0] -ne "1" -or $counts[1] -ne "1" -or $counts[2] -ne "1") {
        throw "La compra no ha persistido de forma coherente su billete y credencial QR"
    }

    return $command
}

function Test-TicketMachineRechargeFlow {
    param(
        [Parameter(Mandatory = $true)] $IssuedTicketCommand,
        [Parameter(Mandatory = $true)] [int] $BackendPort
    )

    $ticket = $IssuedTicketCommand.payload.ticket
    $lookupBody = @{ qrValue = $ticket.qrValue } | ConvertTo-Json -Compress
    $lookup = Invoke-RestMethod `
        -Uri "http://127.0.0.1:$BackendPort/api/public/v1/ticket-recharges/lookup" `
        -Method Post -ContentType "application/json; charset=utf-8" `
        -Body $lookupBody -TimeoutSec 10
    if (-not $lookup.rechargeable `
            -or $lookup.ticketCode -ne $ticket.ticketCode `
            -or $lookup.productType -ne "MULTI_TRIP" `
            -or [int]$lookup.remainingTrips -ne 2) {
        throw "El billete emitido no se puede consultar correctamente antes de recargarlo"
    }

    $rechargeReference = [Guid]::NewGuid().ToString()
    $messageId = [Guid]::NewGuid().ToString()
    $occurredAt = [DateTimeOffset]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
    $requestFileName = "ticket-machine-recharge-$rechargeReference.json"
    $requestFile = Join-Path $runtimeDirectory $requestFileName
    $responseContainerPath = "/tmp/ticket-machine-recharge-response-$rechargeReference.json"
    $request = @{
        schemaVersion = 1
        messageId = $messageId
        correlationId = $null
        type = "ticket.recharge-requested"
        deviceCode = $machineUsername
        occurredAt = $occurredAt
        sentAt = $occurredAt
        payload = @{
            rechargeReference = $rechargeReference
            qrValue = $ticket.qrValue
            paymentMethod = "SIMULATED"
            paidAmount = 3.00
            currency = "EUR"
            configuration = @{ trips = 3 }
        }
    } | ConvertTo-Json -Depth 6 -Compress
    [IO.File]::WriteAllText($requestFile, $request, [Text.UTF8Encoding]::new($false))

    Start-MqttCapture -Topic "rmm/v1/devices/$machineUsername/responses" `
        -ContainerPath $responseContainerPath
    & docker exec $env:MOSQUITTO_CONTAINER_NAME mosquitto_pub `
        -h 127.0.0.1 -p 1883 -u $machineUsername -P $machinePassword `
        -t "rmm/v1/devices/$machineUsername/requests/recharges" `
        -q 1 -f "/mosquitto/security/$requestFileName"
    if ($LASTEXITCODE -ne 0) {
        throw "La maquina de venta no pudo publicar la solicitud de recarga"
    }

    $response = Wait-MqttCapture -ContainerPath $responseContainerPath | ConvertFrom-Json
    if ($response.type -ne "ticket.recharge-completed" `
            -or $response.correlationId -ne $messageId `
            -or $response.payload.rechargeReference -ne $rechargeReference `
            -or $response.payload.ticketCode -ne $ticket.ticketCode `
            -or $response.payload.productType -ne "MULTI_TRIP" `
            -or [int]$response.payload.remainingTrips -ne 5 `
            -or $response.payload.qrValue -ne $ticket.qrValue) {
        throw "La respuesta de recarga no refleja el nuevo estado del billete"
    }

    $escapedReference = $rechargeReference.Replace("'", "''")
    $escapedTicketCode = $ticket.ticketCode.Replace("'", "''")
    $persistenceResult = & docker exec $env:MYSQL_CONTAINER_NAME `
        mysql -N -u root "-p$($env:MYSQL_ROOT_PASSWORD)" transport_simulator_db `
        -e "SELECT COUNT(*), t.remaining_trips, COUNT(o.id) FROM purchases p JOIN tickets t ON t.id = p.ticket_id LEFT JOIN ticket_operations o ON o.purchase_id = p.id AND o.operation_type = 'RECHARGED' WHERE p.external_reference = '$escapedReference' AND p.purchase_type = 'RECHARGE' AND p.purchase_status = 'COMPLETED' AND t.code = '$escapedTicketCode' GROUP BY t.remaining_trips;"
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo comprobar la persistencia de la recarga"
    }
    $counts = (($persistenceResult -join "`n").Trim() -split "\s+")
    if ($counts.Count -ne 3 -or $counts[0] -ne "1" -or $counts[1] -ne "5" -or $counts[2] -ne "1") {
        throw "La recarga no ha persistido el saldo de viajes y su historial de operacion"
    }
}

function Invoke-ValidatorRequest {
    param(
        [Parameter(Mandatory)][string]$Username,
        [Parameter(Mandatory)][string]$Password,
        [Parameter(Mandatory)][string]$Direction,
        [Parameter(Mandatory)][string]$StationCode,
        [Parameter(Mandatory)][string]$QrValue,
        [Parameter(Mandatory)][string]$ExpectedDecision,
        [string]$ExpectedReason
    )

    $messageId = [Guid]::NewGuid().ToString()
    $validationReference = [Guid]::NewGuid().ToString()
    $occurredAt = [DateTimeOffset]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
    $requestFileName = "validator-$validationReference.json"
    $requestFile = Join-Path $runtimeDirectory $requestFileName
    $responseContainerPath = "/tmp/validator-response-$validationReference.json"
    $request = @{
        schemaVersion = 1
        messageId = $messageId
        correlationId = $null
        type = "ticket.validation-requested"
        deviceCode = $Username
        occurredAt = $occurredAt
        sentAt = $occurredAt
        payload = @{
            validationReference = $validationReference
            direction = $Direction
            stationCode = $StationCode
            qrValue = $QrValue
        }
    } | ConvertTo-Json -Depth 5 -Compress
    [IO.File]::WriteAllText($requestFile, $request, [Text.UTF8Encoding]::new($false))

    Start-MqttCapture -Topic "rmm/v1/devices/$Username/responses" `
        -ContainerPath $responseContainerPath -Username $Username -Password $Password
    & docker exec $env:MOSQUITTO_CONTAINER_NAME mosquitto_pub `
        -h 127.0.0.1 -p 1883 -u $Username -P $Password `
        -t "rmm/v1/devices/$Username/requests/validations" `
        -q 1 -f "/mosquitto/security/$requestFileName"
    if ($LASTEXITCODE -ne 0) {
        throw "La validadora $Username no pudo publicar su solicitud"
    }

    $response = Wait-MqttCapture -ContainerPath $responseContainerPath | ConvertFrom-Json
    if ($response.type -ne "ticket.validation-decided" `
            -or $response.correlationId -ne $messageId `
            -or $response.payload.validationReference -ne $validationReference `
            -or $response.payload.decision -ne $ExpectedDecision) {
        throw "La decisión de $Direction no corresponde a la solicitud enviada"
    }
    if ($ExpectedReason -and $response.payload.reasonCode -ne $ExpectedReason) {
        throw "Se esperaba $ExpectedReason y se recibió $($response.payload.reasonCode)"
    }
    return $response
}

function Test-TicketValidationJourneyFlow {
    param([Parameter(Mandatory = $true)] $IssuedTicketCommand)

    $ticket = $IssuedTicketCommand.payload.ticket
    $entry = Invoke-ValidatorRequest `
        -Username $entryValidatorUsername -Password $entryValidatorPassword `
        -Direction "ENTRY" -StationCode "ST046" -QrValue $ticket.qrValue `
        -ExpectedDecision "ACCEPTED" -ExpectedReason "VALID"
    if ($entry.payload.ticketCode -ne $ticket.ticketCode) {
        throw "La entrada aceptada no corresponde al billete emitido"
    }

    Invoke-ValidatorRequest `
        -Username $entryValidatorUsername -Password $entryValidatorPassword `
        -Direction "ENTRY" -StationCode "ST046" -QrValue $ticket.qrValue `
        -ExpectedDecision "REJECTED" -ExpectedReason "ENTRY_ALREADY_OPEN" | Out-Null

    $exit = Invoke-ValidatorRequest `
        -Username $exitValidatorUsername -Password $exitValidatorPassword `
        -Direction "EXIT" -StationCode "ST020" -QrValue $ticket.qrValue `
        -ExpectedDecision "ACCEPTED" -ExpectedReason "VALID"
    if ($exit.payload.ticketCode -ne $ticket.ticketCode) {
        throw "La salida aceptada no corresponde al billete emitido"
    }

    $escapedTicketCode = $ticket.ticketCode.Replace("'", "''")
    $result = & docker exec $env:MYSQL_CONTAINER_NAME `
        mysql -N -u root "-p$($env:MYSQL_ROOT_PASSWORD)" transport_simulator_db `
        -e "SELECT (SELECT COUNT(*) FROM ticket_validations v WHERE v.ticket_id = t.id), (SELECT COUNT(*) FROM ticket_journeys j WHERE j.ticket_id = t.id AND j.status = 'CLOSED'), (SELECT COUNT(*) FROM operational_logs l JOIN devices d ON d.id = l.device_id WHERE d.code IN ('$entryValidatorUsername', '$exitValidatorUsername') AND l.log_origin = 'MQTT' AND l.event_source = 'REAL' AND l.event_type = 'VALIDATION_REQUESTED'), t.remaining_trips FROM tickets t WHERE t.code = '$escapedTicketCode';"
    if ($LASTEXITCODE -ne 0) { throw "No se pudo comprobar el trayecto validado" }
    $counts = (($result -join "`n").Trim() -split "\s+")
    if ($counts.Count -ne 4 -or $counts[0] -ne "2" -or $counts[1] -ne "1" `
            -or [int]$counts[2] -lt 3 -or $counts[3] -ne "4") {
        throw "Las validaciones, el trayecto, los logs y el saldo no son coherentes " +
            "(validaciones=$($counts[0]); trayectos=$($counts[1]); " +
            "logs=$($counts[2]); viajes_restantes=$($counts[3]))"
    }
}

function Test-OperatorWebAccess {
    $frontendDirectory = Join-Path $repositoryRoot "frontend"
    $npmCommand = Get-Command npm -All -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandType -eq "Application" } |
        Select-Object -First 1
    if ($null -eq $npmCommand) {
        throw "No se encontró npm para ejecutar la prueba de acceso web"
    }

    $env:RMM_E2E_REAL_BACKEND = "true"
    $env:RMM_E2E_OPERATOR_USERNAME = $env:OPERATOR_USERNAME
    $env:RMM_E2E_OPERATOR_PASSWORD = $env:OPERATOR_PASSWORD

    Push-Location $frontendDirectory
    try {
        & $($npmCommand.Source) run test:e2e -- operator-web-access.integration.spec.ts
        if ($LASTEXITCODE -ne 0) {
            throw "La aplicación web no pudo autenticarse contra el backend real"
        }
    } finally {
        Pop-Location
        Remove-Item Env:RMM_E2E_REAL_BACKEND -ErrorAction SilentlyContinue
        Remove-Item Env:RMM_E2E_OPERATOR_USERNAME -ErrorAction SilentlyContinue
        Remove-Item Env:RMM_E2E_OPERATOR_PASSWORD -ErrorAction SilentlyContinue
    }
}

$mysqlPort = Get-FreeTcpPort
$mqttPort = Get-FreeTcpPort
$backendPort = if ($RunWebAccessTest) { 8080 } else { Get-FreeTcpPort }
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
        "$entryValidatorUsername=$entryValidatorPassword"
        "$exitValidatorUsername=$exitValidatorPassword"
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

    Write-Host "Comprobando una compra completa de la maquina de venta contra el backend real..."
    $issuedTicketCommand = Test-TicketMachinePurchaseFlow

    Write-Host "Comprobando la consulta y recarga del billete contra el backend real..."
    Test-TicketMachineRechargeFlow `
        -IssuedTicketCommand $issuedTicketCommand -BackendPort $backendPort

    Write-Host "Comprobando entrada, rechazo duplicado, salida y trayecto completo..."
    Test-TicketValidationJourneyFlow -IssuedTicketCommand $issuedTicketCommand

    Write-Host "Comprobando autenticación y consultas funcionales del ecosistema..."
    Test-OperatorApiFlow -Port $backendPort

    if ($RunWebAccessTest) {
        Write-Host "Comprobando el acceso de Angular al backend y MySQL reales..."
        Test-OperatorWebAccess
    }

    Write-Host "Ecosistema validado: compra, recarga, validaciones, trayecto, logs y acceso funcionan."
} finally {
    try { Invoke-Compose -Arguments @("down", "--volumes", "--remove-orphans") }
    catch { Write-Warning "No se pudieron retirar todos los contenedores de prueba: $_" }
    if (Test-Path -LiteralPath $runtimeDirectory) {
        Remove-Item -LiteralPath $runtimeDirectory -Recurse -Force
    }
}
