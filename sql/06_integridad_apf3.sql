ALTER TABLE usuario ADD CONSTRAINT ck_usuario_version_apf3 CHECK(version_token>=0);
ALTER TABLE venta ADD CONSTRAINT ck_venta_tasa_apf3 CHECK(tasa_igv>=0 AND tasa_igv<=1);
ALTER TABLE venta ADD CONSTRAINT ck_venta_estado_apf3 CHECK(estado IN ('CONFIRMADA','ANULADA'));
ALTER TABLE movimiento_inventario ADD CONSTRAINT ck_mov_tipo_apf3 CHECK((tipo='ENTRADA' AND cantidad>0) OR (tipo='SALIDA' AND cantidad<0) OR (tipo='AJUSTE' AND cantidad<>0));
ALTER TABLE bitacora ADD CONSTRAINT ck_bitacora_resultado_apf3 CHECK(resultado IN ('OK','DENEGADO','ERROR'));
ALTER TABLE solicitud_cambio ADD CONSTRAINT ck_rfc_riesgo_apf3 CHECK(riesgo=impacto*probabilidad);
ALTER TABLE solicitud_cambio ADD CONSTRAINT ck_rfc_componente_apf3 CHECK(componente IN ('APLICACION','BASE_DATOS','INFRAESTRUCTURA','DOCUMENTACION','CRONOGRAMA'));
