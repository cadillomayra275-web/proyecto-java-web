$puerto_siscom = if ($env:SISCOM_PUERTO) { $env:SISCOM_PUERTO } else { '8080' }
$direccion_siscom = "http://localhost:$puerto_siscom"
for ($intento_siscom = 0; $intento_siscom -lt 60; $intento_siscom++) {
    try {
        $salud_siscom = Invoke-RestMethod "$direccion_siscom/api/health" -TimeoutSec 1
        if ($salud_siscom.estado -eq 'UP') { Start-Process $direccion_siscom; exit 0 }
    } catch { }
    Start-Sleep -Seconds 1
}
Write-Host 'Revise la ventana del servidor SISCOM'
