ALTER TABLE producto ADD COLUMN imagen VARCHAR(80) NOT NULL DEFAULT 'producto-1.svg';
ALTER TABLE venta ADD COLUMN clave_peticion VARCHAR(80) NULL;
ALTER TABLE venta ADD COLUMN huella_peticion CHAR(64) NULL;
UPDATE venta SET clave_peticion=CONCAT('legacy-',id),huella_peticion=SHA2(CONCAT('legacy-',id),256);
ALTER TABLE venta MODIFY clave_peticion VARCHAR(80) NOT NULL, MODIFY huella_peticion CHAR(64) NOT NULL, ADD UNIQUE (clave_peticion);
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
 solicitante_id BIGINT UNSIGNED NOT NULL,
 aprobador_id BIGINT UNSIGNED,
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
 FOREIGN KEY(aprobador_id) REFERENCES usuario(id)
);
CREATE TABLE IF NOT EXISTS evento_cambio (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 cambio_id BIGINT NOT NULL,
 usuario_id BIGINT UNSIGNED NOT NULL,
 estado_anterior VARCHAR(30) NOT NULL,
 estado_nuevo VARCHAR(30) NOT NULL,
 nota VARCHAR(2000) NOT NULL,
 fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(cambio_id) REFERENCES solicitud_cambio(id),
 FOREIGN KEY(usuario_id) REFERENCES usuario(id)
);
