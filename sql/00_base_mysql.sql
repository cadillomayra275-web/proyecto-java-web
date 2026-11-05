CREATE DATABASE IF NOT EXISTS siscom_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE siscom_db;
CREATE TABLE IF NOT EXISTS usuario (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 nombre VARCHAR(100) NOT NULL,
 correo VARCHAR(120) NOT NULL UNIQUE,
 clave_hash VARCHAR(60) NOT NULL,
 rol VARCHAR(20) NOT NULL CHECK(rol IN ('ADMIN','VENDEDOR','ALMACEN','SUPERVISOR')),
 activo BOOLEAN NOT NULL DEFAULT TRUE,
 version_token INT NOT NULL DEFAULT 0,
 creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT ck_usuario_version_apf3 CHECK(version_token>=0)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS cliente (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 documento VARCHAR(20) NOT NULL UNIQUE,
 nombre VARCHAR(120) NOT NULL,
 telefono VARCHAR(20),
 correo VARCHAR(120),
 activo BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS categoria (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 nombre VARCHAR(80) NOT NULL UNIQUE,
 activo BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS producto (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 categoria_id BIGINT NOT NULL,
 codigo VARCHAR(30) NOT NULL UNIQUE,
 nombre VARCHAR(120) NOT NULL,
 precio DECIMAL(12,2) NOT NULL CHECK(precio>=0),
 stock INT NOT NULL DEFAULT 0 CHECK(stock>=0),
 stock_minimo INT NOT NULL DEFAULT 0 CHECK(stock_minimo>=0),
 imagen VARCHAR(80) NOT NULL DEFAULT 'producto-1.svg',
 activo BOOLEAN NOT NULL DEFAULT TRUE,
 FOREIGN KEY(categoria_id) REFERENCES categoria(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS parametro (
 clave VARCHAR(40) PRIMARY KEY,
 valor DECIMAL(9,6) NOT NULL CHECK(valor>=0 AND valor<=1)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS venta (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 cliente_id BIGINT NOT NULL,
 usuario_id BIGINT NOT NULL,
 fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 subtotal DECIMAL(12,2) NOT NULL CHECK(subtotal>=0),
 tasa_igv DECIMAL(9,6) NOT NULL,
 impuesto DECIMAL(12,2) NOT NULL CHECK(impuesto>=0),
 total DECIMAL(12,2) NOT NULL CHECK(total=subtotal+impuesto),
 estado VARCHAR(20) NOT NULL DEFAULT 'CONFIRMADA',
 clave_peticion VARCHAR(80) NOT NULL UNIQUE,
 huella_peticion CHAR(64) NOT NULL,
 FOREIGN KEY(cliente_id) REFERENCES cliente(id),
 FOREIGN KEY(usuario_id) REFERENCES usuario(id),
 CONSTRAINT ck_venta_tasa_apf3 CHECK(tasa_igv>=0 AND tasa_igv<=1),
 CONSTRAINT ck_venta_estado_apf3 CHECK(estado IN ('CONFIRMADA','ANULADA'))
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS detalle_venta (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 venta_id BIGINT NOT NULL,
 producto_id BIGINT NOT NULL,
 cantidad INT NOT NULL CHECK(cantidad>0),
 precio_unitario DECIMAL(12,2) NOT NULL CHECK(precio_unitario>=0),
 subtotal DECIMAL(12,2) NOT NULL CHECK(subtotal=cantidad*precio_unitario),
 UNIQUE(venta_id,producto_id),
 FOREIGN KEY(venta_id) REFERENCES venta(id),
 FOREIGN KEY(producto_id) REFERENCES producto(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS movimiento_inventario (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 producto_id BIGINT NOT NULL,
 usuario_id BIGINT NOT NULL,
 venta_id BIGINT,
 tipo VARCHAR(20) NOT NULL,
 cantidad INT NOT NULL CHECK(cantidad<>0),
 saldo_resultante INT NOT NULL CHECK(saldo_resultante>=0),
 motivo VARCHAR(200) NOT NULL,
 fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(producto_id) REFERENCES producto(id),
 FOREIGN KEY(usuario_id) REFERENCES usuario(id),
 FOREIGN KEY(venta_id) REFERENCES venta(id),
 CONSTRAINT ck_mov_tipo_apf3 CHECK((tipo='ENTRADA' AND cantidad>0) OR (tipo='SALIDA' AND cantidad<0) OR (tipo='AJUSTE' AND cantidad<>0))
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS token_revocado (
 jti CHAR(36) PRIMARY KEY,
 usuario_id BIGINT NOT NULL,
 expira_en TIMESTAMP NOT NULL,
 FOREIGN KEY(usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS bitacora (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 usuario_id BIGINT,
 accion VARCHAR(60) NOT NULL,
 entidad VARCHAR(40) NOT NULL,
 entidad_id BIGINT,
 resultado VARCHAR(20) NOT NULL,
 trace_id CHAR(36) NOT NULL,
 fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(usuario_id) REFERENCES usuario(id),
 CONSTRAINT ck_bitacora_resultado_apf3 CHECK(resultado IN ('OK','DENEGADO','ERROR'))
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS solicitud_cambio (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 titulo VARCHAR(160) NOT NULL,
 tipo VARCHAR(20) NOT NULL CHECK(tipo IN ('ESTANDAR','NORMAL','EMERGENCIA')),
 componente VARCHAR(30) NOT NULL,
 justificacion VARCHAR(1200) NOT NULL,
 impacto INT NOT NULL CHECK(impacto BETWEEN 1 AND 5),
 probabilidad INT NOT NULL CHECK(probabilidad BETWEEN 1 AND 5),
 riesgo INT NOT NULL CHECK(riesgo BETWEEN 1 AND 25),
 plan_pruebas VARCHAR(2000) NOT NULL,
 plan_reversion VARCHAR(2000) NOT NULL,
 solicitante_id BIGINT NOT NULL,
 aprobador_id BIGINT,
 version_objetivo VARCHAR(40) NOT NULL,
 estado VARCHAR(30) NOT NULL DEFAULT 'REGISTRADA',
 evidencia_qa VARCHAR(2000),
 respaldo VARCHAR(240),
 artefacto_sha CHAR(64),
 ventana VARCHAR(100),
 enlace_issue VARCHAR(300),
 creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(solicitante_id) REFERENCES usuario(id),
 FOREIGN KEY(aprobador_id) REFERENCES usuario(id),
 CONSTRAINT ck_rfc_riesgo_apf3 CHECK(riesgo=impacto*probabilidad),
 CONSTRAINT ck_rfc_componente_apf3 CHECK(componente IN ('APLICACION','BASE_DATOS','INFRAESTRUCTURA','DOCUMENTACION','CRONOGRAMA'))
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS evento_cambio (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 cambio_id BIGINT NOT NULL,
 usuario_id BIGINT NOT NULL,
 estado_anterior VARCHAR(30) NOT NULL,
 estado_nuevo VARCHAR(30) NOT NULL,
 nota VARCHAR(2000) NOT NULL,
 fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(cambio_id) REFERENCES solicitud_cambio(id),
 FOREIGN KEY(usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;
