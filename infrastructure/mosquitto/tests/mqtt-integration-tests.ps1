[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$mosquittoDirectory = Join-Path $repositoryRoot "infrastructure\mosquitto"
$runtimeDirectory = Join-Path $mosquittoDirectory "runtime"
$usersFile = Join-Path $runtimeDirectory "mqtt-users.test"
$containerName = "rmm-mqtt-integration-tests"
$brokerImage = "eclipse-mosquitto:2.0.22-openssl"

$backendUsername = "rmm-backend"
$backendPassword = "backend-test-password"
$saleUsername = "RMM-SALE-ST046-01"
$salePassword = "sale-device-test-password"
$validatorUsername = "RMM-VAL-ST046-ENT-01"
$validatorPassword = "validator-test-password"

function Invoke-Docker {
    param([Parameter(Mandatory)][string[]]$Arguments)

    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "El comando docker $($Arguments -join ' ') terminó con código $LASTEXITCODE"
    }
}

function Wait-ForBroker {
    for ($attempt = 1; $attempt -le 20; $attempt++) {
        & docker exec $containerName mosquitto_pub -h 127.0.0.1 -p 1883 `
            -u $backendUsername -P $backendPassword `
            -t "rmm/v1/devices/$saleUsername/commands" -n 2>$null
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Start-Sleep -Milliseconds 500
    }

    Invoke-Docker -Arguments @("logs", $containerName)
    throw "Mosquitto no quedó disponible para las pruebas"
}

function Wait-ForContainerFile {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$ExpectedContent
    )

    for ($attempt = 1; $attempt -le 20; $attempt++) {
        $content = & docker exec $containerName sh -c "if [ -f '$Path' ]; then cat '$Path'; fi"
        if ($LASTEXITCODE -eq 0 -and ($content -join "`n").Trim() -eq $ExpectedContent) {
            return
        }
        Start-Sleep -Milliseconds 250
    }

    throw "No se recibió '$ExpectedContent' en $Path"
}

try {
    New-Item -ItemType Directory -Path $runtimeDirectory -Force | Out-Null
    @(
        "$backendUsername=$backendPassword"
        "$saleUsername=$salePassword"
        "$validatorUsername=$validatorPassword"
    ) | Set-Content -LiteralPath $usersFile -Encoding utf8

    & (Join-Path $mosquittoDirectory "scripts\initialize-security.ps1") `
        -UsersFile $usersFile -RuntimeDirectory $runtimeDirectory
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo preparar la seguridad MQTT de prueba"
    }

    & docker rm --force $containerName 2>$null | Out-Null
    $runtimeMount = "{0}:/mosquitto/security:ro" -f $runtimeDirectory.Replace("\", "/")
    $configMount = "{0}:/mosquitto/config:ro" -f (Join-Path $mosquittoDirectory "config").Replace("\", "/")
    Invoke-Docker -Arguments @(
        "run", "--detach", "--name", $containerName,
        "--volume", $configMount,
        "--volume", $runtimeMount,
        $brokerImage,
        "mosquitto", "-c", "/mosquitto/config/mosquitto.conf"
    )
    Wait-ForBroker

    Write-Host "Comprobando publicación y suscripción autorizadas..."
    $statusTopic = "rmm/v1/devices/$saleUsername/status"
    Invoke-Docker -Arguments @(
        "exec", "--detach", $containerName, "sh", "-c",
        "mosquitto_sub -h 127.0.0.1 -u '$backendUsername' -P '$backendPassword' -t '$statusTopic' -C 1 -W 10 > /tmp/authorized-message"
    )
    Start-Sleep -Milliseconds 500
    Invoke-Docker -Arguments @(
        "exec", $containerName, "mosquitto_pub", "-h", "127.0.0.1",
        "-u", $saleUsername, "-P", $salePassword, "-t", $statusTopic,
        "-m", '{"state":"AVAILABLE"}'
    )
    Wait-ForContainerFile -Path "/tmp/authorized-message" -ExpectedContent '{"state":"AVAILABLE"}'

    Write-Host "Comprobando el aislamiento entre identidades..."
    $foreignTopic = "rmm/v1/devices/$validatorUsername/status"
    Invoke-Docker -Arguments @(
        "exec", "--detach", $containerName, "sh", "-c",
        "mosquitto_sub -h 127.0.0.1 -u '$backendUsername' -P '$backendPassword' -t '$foreignTopic' -C 1 -W 10 > /tmp/isolation-message"
    )
    Start-Sleep -Milliseconds 500

    # mosquitto_pub puede devolver código 0 para una publicación QoS 0 aunque el broker la descarte
    # por las ACL. Por eso se comprueba qué mensaje entrega realmente el broker al suscriptor.
    & docker exec $containerName mosquitto_pub -h 127.0.0.1 `
        -u $saleUsername -P $salePassword -q 1 -t $foreignTopic `
        -m '{"source":"UNAUTHORIZED_SALE_DEVICE"}' 2>$null

    Invoke-Docker -Arguments @(
        "exec", $containerName, "mosquitto_pub", "-h", "127.0.0.1",
        "-u", $validatorUsername, "-P", $validatorPassword, "-q", "1",
        "-t", $foreignTopic, "-m", '{"source":"AUTHORIZED_VALIDATOR"}'
    )
    Wait-ForContainerFile -Path "/tmp/isolation-message" `
        -ExpectedContent '{"source":"AUTHORIZED_VALIDATOR"}'

    Write-Host "Comprobando la reconexión y la cola persistente QoS 1..."
    $commandsTopic = "rmm/v1/devices/$saleUsername/commands"
    $clientId = "mqtt-reconnect-test"
    & docker exec $containerName mosquitto_sub -h 127.0.0.1 `
        -u $saleUsername -P $salePassword -i $clientId -c -q 1 `
        -t $commandsTopic -W 1 2>$null
    if ($LASTEXITCODE -notin @(0, 27)) {
        throw "No se pudo registrar la sesión MQTT persistente"
    }

    Invoke-Docker -Arguments @(
        "exec", $containerName, "mosquitto_pub", "-h", "127.0.0.1",
        "-u", $backendUsername, "-P", $backendPassword, "-q", "1",
        "-t", $commandsTopic, "-m", '{"command":"SYNC_CONFIGURATION"}'
    )
    $reconnectedMessage = & docker exec $containerName mosquitto_sub -h 127.0.0.1 `
        -u $saleUsername -P $salePassword -i $clientId -c -q 1 `
        -t $commandsTopic -C 1 -W 10
    if ($LASTEXITCODE -ne 0 -or ($reconnectedMessage -join "`n").Trim() -ne '{"command":"SYNC_CONFIGURATION"}') {
        throw "La sesión reconectada no recuperó el mensaje QoS 1 pendiente"
    }

    Write-Host "Todas las pruebas de integración MQTT han finalizado correctamente."
} finally {
    & docker rm --force $containerName 2>$null | Out-Null
    Remove-Item -LiteralPath $usersFile -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath (Join-Path $runtimeDirectory "password_file") -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath (Join-Path $runtimeDirectory "acl_file") -Force -ErrorAction SilentlyContinue
}
