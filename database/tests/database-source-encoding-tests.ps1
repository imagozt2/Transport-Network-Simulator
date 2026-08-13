[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$databaseRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$utf8Decoder = [System.Text.UTF8Encoding]::new($false, $true)
$mojibakePattern = "$( [char]0x00C3 ).|$( [char]0x00C2 ).|$( [char]0x00E2 ).|$( [char]0xFFFD )"
$failures = [System.Collections.Generic.List[string]]::new()

Get-ChildItem -LiteralPath $databaseRoot -Recurse -File -Filter "*.sql" | ForEach-Object {
    $relativePath = $_.FullName.Substring($databaseRoot.Length).TrimStart('\', '/')
    try {
        $content = $utf8Decoder.GetString([IO.File]::ReadAllBytes($_.FullName))
    } catch {
        $failures.Add("$relativePath no contiene UTF-8 valido")
        return
    }

    if ($content -match $mojibakePattern) {
        $failures.Add("$relativePath contiene una secuencia compatible con texto mal decodificado")
    }
}

$networkSeed = [IO.File]::ReadAllText(
    (Join-Path $databaseRoot "data\01_transport_network.sql"),
    $utf8Decoder
)
$expectedNetworkNames = @(
    "Ram$([char]0x00F3)n y Cajal",
    "Museo Mar$([char]0x00ED)timo",
    "Estadio Ol$([char]0x00ED)mpico",
    "La Galer$([char]0x00ED)a",
    "V$([char]0x00ED)a Aurea",
    "Muralla Ib$([char]0x00E9)rica",
    "San Pedro Ap$([char]0x00F3)stol",
    "Herrer$([char]0x00ED)a",
    "El Espig$([char]0x00F3)n",
    "L$([char]0x00ED)nea 1"
)

foreach ($expectedName in $expectedNetworkNames) {
    if (-not $networkSeed.Contains($expectedName)) {
        $failures.Add("data/01_transport_network.sql no conserva el literal UTF-8 esperado")
    }
}

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ }
    throw "Se detectaron $($failures.Count) problemas de codificacion en los scripts de base de datos"
}

Write-Host "Codificacion de los scripts SQL validada: UTF-8 y literales canonicos correctos."
