[CmdletBinding()]
param(
    [string]$CertificatesDirectory
)

$ErrorActionPreference = "Stop"

$mosquittoDirectory = Split-Path -Parent $PSScriptRoot
$certificatesPath = if ($CertificatesDirectory) {
    $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($CertificatesDirectory)
} else {
    Join-Path $mosquittoDirectory "runtime\certificates"
}

$requiredFiles = @(
    "ca.crt",
    "broker.crt",
    "broker.key",
    "clients\rmm-backend.crt",
    "clients\rmm-backend.key"
)

$missingFiles = $requiredFiles | Where-Object {
    $path = Join-Path $certificatesPath $_
    -not (Test-Path -LiteralPath $path -PathType Leaf) -or
        (Get-Item -LiteralPath $path).Length -eq 0
}

if ($missingFiles.Count -gt 0) {
    $list = $missingFiles -join ", "
    throw "Faltan certificados TLS obligatorios en ${certificatesPath}: $list"
}

$repositoryRoot = (git rev-parse --show-toplevel).Trim().Replace("/", "\")
if ($LASTEXITCODE -ne 0) {
    throw "No se pudo localizar la raíz del repositorio"
}

if ($certificatesPath.StartsWith($repositoryRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    $relativeCertificatesPath = $certificatesPath.Substring($repositoryRoot.Length).TrimStart([char]92)
    $trackedFiles = git ls-files -- $relativeCertificatesPath
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo comprobar el estado de Git de los certificados"
    }
    if ($trackedFiles) {
        throw "Hay material TLS versionado por Git. Retíralo antes de continuar: $trackedFiles"
    }
}

Write-Host "Material TLS presente y excluido de Git en $certificatesPath"
