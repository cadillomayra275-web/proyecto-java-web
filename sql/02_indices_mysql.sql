CREATE INDEX ix_venta_fecha ON venta(fecha);
CREATE INDEX ix_venta_usuario_fecha ON venta(usuario_id,fecha);
CREATE INDEX ix_mov_producto_fecha ON movimiento_inventario(producto_id,fecha);
CREATE INDEX ix_cambio_estado ON solicitud_cambio(estado);
CREATE INDEX ix_evento_cambio ON evento_cambio(cambio_id,fecha);
CREATE INDEX ix_bitacora_fecha ON bitacora(fecha);
