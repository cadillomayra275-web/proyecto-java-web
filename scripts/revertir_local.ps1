param([Parameter(Mandatory=$true)][string]$Respaldo,[Parameter(Mandatory=$true)][string]$VersionEstable,[switch]$Confirmar)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not $Confirmar) { throw 'Agregue -Confirmar despues de cerrar el servidor y validar el respaldo' }
if (-not (Test-Path $Respaldo -PathType Leaf)) { throw 'Respaldo no encontrado' }
if (-not (Test-Path "$VersionEstable/bin/siscom.jar" -PathType Leaf)) { throw 'Version estable no valida' }
$marca_siscom = Get-Date -Format 'yyyyMMdd-HHmmss'
$anterior_siscom = "respaldos/antes-reversion-$marca_siscom"
New-Item -ItemType Directory -Force $anterior_siscom | Out-Null
if (Test-Path 'datos/siscom.mv.db') {
 $prueba_siscom = [System.IO.File]::Open((Resolve-Path 'datos/siscom.mv.db'), 'Open', 'ReadWrite', 'None')
 $prueba_siscom.Close()
 Copy-Item 'datos/siscom.mv.db' $anterior_siscom
}
Copy-Item 'bin/siscom.jar' $anterior_siscom
$temporal_siscom = Join-Path $anterior_siscom 'restaurado'
Expand-Archive -LiteralPath $Respaldo -DestinationPath $temporal_siscom
$base_siscom = Get-ChildItem $temporal_siscom -Filter 'siscom.mv.db' -Recurse
if ($base_siscom.Count -ne 1) { throw 'El respaldo no contiene una sola base SISCOM' }
Copy-Item $base_siscom.FullName 'datos/siscom.mv.db' -Force
Copy-Item "$VersionEstable/bin/siscom.jar" 'bin/siscom.jar' -Force
Copy-Item "$VersionEstable/lib/*" 'lib' -Force
Copy-Item "$VersionEstable/src/main/webapp/*" 'src/main/webapp' -Recurse -Force
Write-Host "Reversion aplicada Copia previa $anterior_siscom"
Write-Host 'Ejecute INICIAR.bat y verifique salud login venta y conciliacion'
