param([string]$Direccion='http://localhost:8080')
$ErrorActionPreference='Stop'
$salud_siscom=Invoke-RestMethod "$Direccion/api/health" -TimeoutSec 5
if ($salud_siscom.estado -ne 'UP') { throw 'Servicio no disponible' }
$salud_siscom | ConvertTo-Json
