SELECT p.id,p.codigo,p.stock,COALESCE(SUM(m.cantidad),0) AS saldo_calculado
FROM producto p LEFT JOIN movimiento_inventario m ON m.producto_id=p.id
GROUP BY p.id,p.codigo,p.stock
HAVING p.stock<>COALESCE(SUM(m.cantidad),0);
SELECT v.id,v.subtotal,COALESCE(SUM(d.subtotal),0) AS suma_detalles
FROM venta v LEFT JOIN detalle_venta d ON d.venta_id=v.id
GROUP BY v.id,v.subtotal
HAVING v.subtotal<>COALESCE(SUM(d.subtotal),0);
SELECT id FROM venta WHERE impuesto<>ROUND(subtotal*tasa_igv,2) OR total<>subtotal+impuesto;
SELECT id FROM producto WHERE stock<0;
SELECT id FROM solicitud_cambio WHERE solicitante_id=aprobador_id;
SELECT id FROM solicitud_cambio WHERE estado IN ('PROGRAMADA','DESPLEGADA','CERRADA') AND evidencia_qa IS NULL;
