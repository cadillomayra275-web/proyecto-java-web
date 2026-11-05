import {spawnSync} from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';

const marca=Date.now();
const sep=process.platform==='win32'?';':':';
const url='jdbc:h2:./pruebas/datos/siscom;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=15000';
await fs.access('pruebas/datos/siscom.mv.db');
function java(args,env=process.env){const r=spawnSync('java',args,{encoding:'utf8',env});if(r.status!==0)throw new Error(r.stderr||r.stdout||'Fallo de Java');return r.stdout;}
const mensaje=java(['-cp','bin/siscom.jar'+sep+'lib/*','pe.edu.siscom.Inicio','--respaldo'],{...process.env,SISCOM_DB_URL:url});
const respaldo=mensaje.trim().split(/\r?\n/).at(-1);
const destino=`pruebas/restauracion-${marca}`;
const inicio=performance.now();
java(['-cp','lib/h2.jar','org.h2.tools.Restore','-file',respaldo,'-dir',destino,'-db','siscom']);
const consultas='SELECT COUNT(*) AS ventas FROM venta; SELECT COUNT(*) AS cambios FROM solicitud_cambio; SELECT COUNT(*) AS discrepancias FROM (SELECT p.id FROM producto p LEFT JOIN movimiento_inventario m ON m.producto_id=p.id GROUP BY p.id,p.stock HAVING p.stock<>COALESCE(SUM(m.cantidad),0)) t;';
const salida=java(['-cp','lib/h2.jar','org.h2.tools.Shell','-url',`jdbc:h2:./${destino}/siscom;MODE=MySQL;DATABASE_TO_LOWER=TRUE`,'-user','sa','-sql',consultas]);
const tiempo=Math.round((performance.now()-inicio)*1000)/1000000;
const numero=nombre=>Number(salida.match(new RegExp(nombre+'\\s*\\r?\\n(\\d+)','i'))?.[1]);
const ventas=numero('ventas'),cambios=numero('cambios'),discrepancias=numero('discrepancias');
if(!Number.isFinite(ventas)||!Number.isFinite(cambios)||discrepancias!==0)throw new Error('Conciliacion de recuperacion no conforme: '+salida);
const resultado={fecha:new Date().toISOString(),respaldo:path.basename(respaldo),sha256:crypto.createHash('sha256').update(await fs.readFile(respaldo)).digest('hex'),jar_sha256:crypto.createHash('sha256').update(await fs.readFile('bin/siscom.jar')).digest('hex'),restauracion_exit:0,verificacion_exit:0,duracion_restauracion_y_consulta_segundos:tiempo,ventas_recuperadas:ventas,rfc_recuperadas:cambios,discrepancias_stock:discrepancias,salida,alcance:'Restauracion y consulta H2 local. No incluye deteccion del incidente ni reapertura. No mide RTO de produccion.'};
await fs.writeFile('pruebas/recuperacion.json',JSON.stringify(resultado,null,2));
console.log(JSON.stringify(resultado));
