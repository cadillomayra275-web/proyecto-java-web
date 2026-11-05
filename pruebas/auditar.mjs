import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import fs from 'node:fs/promises';
import crypto from 'node:crypto';

const puerto=18082;
const base=`http://127.0.0.1:${puerto}/api`;
const resultados=[];
const log=await fs.open('pruebas/auditoria_servidor.log','w');
const servidor=spawn('java',['-cp',`bin/siscom.jar${process.platform==='win32'?';':':'}lib/*`,'pe.edu.siscom.Inicio'],{env:{...process.env,SISCOM_PUERTO:String(puerto),SISCOM_DB_URL:`jdbc:h2:./pruebas/auditoria-${Date.now()}/siscom;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=15000`},stdio:['ignore',log.fd,log.fd]});
const marca=String(Date.now());
let token,usuario,token_usuario;
async function peticion(ruta,metodo='GET',datos,acceso=token,extra={}){
 const r=await fetch(base+ruta,{method:metodo,headers:{'Content-Type':'application/json',...(acceso?{Authorization:`Bearer ${acceso}`} : {}),...extra},body:datos===undefined?undefined:JSON.stringify(datos)});
 const t=await r.text();return {estado:r.status,datos:t?JSON.parse(t):null,cabeceras:r.headers};
}
async function ingresar(correo,clave){return peticion('/auth/login','POST',{correo,clave},null);}
async function caso(id,nombre,fn){const t=performance.now();try{await fn();resultados.push({id,nombre,resultado:'APROBADO',duracion_ms:Math.round(performance.now()-t)});}catch(e){resultados.push({id,nombre,resultado:'FALLIDO',detalle:e.message,duracion_ms:Math.round(performance.now()-t)});}console.log(id+' '+resultados.at(-1).resultado+' '+nombre);}
try{
 let listo=false;for(let i=0;i<60;i++){try{if((await fetch(base+'/health')).ok){listo=true;break;}}catch{}await new Promise(r=>setTimeout(r,500));}
 if(!listo)throw new Error('No inicio el servidor de auditoria');
 token=(await ingresar('admin@siscom.local','Siscom2026!')).datos.token;
 await caso('AU01','Booleano estructurado rechazado sin error interno',async()=>{assert.equal((await peticion('/categorias','POST',{nombre:'AU Array '+marca,activo:[]})).estado,422);});
 await caso('AU02','Booleano de tipo cadena rechazado sin crear categoria',async()=>{const antes=(await peticion('/categorias')).datos.length;assert.equal((await peticion('/categorias','POST',{nombre:'AU String '+marca,activo:'false'})).estado,422);assert.equal((await peticion('/categorias')).datos.length,antes);});
 await caso('AU03','Clave estructurada devuelve validacion controlada',async()=>{const u=(await peticion('/usuarios/2')).datos;assert.equal((await peticion('/usuarios/2','PUT',{...u,clave:{valor:'incorrecto'}})).estado,422);});
 await caso('AU04','Ruta adicional de venta devuelve 404',async()=>{const v=await peticion('/ventas','POST',{cliente_id:1,items:[{producto_id:1,cantidad:1}]},token,{'Idempotency-Key':'auditoria-ruta-'+marca});assert.equal(v.estado,201);assert.equal((await peticion('/ventas/'+v.datos.id+'/no-existe')).estado,404);});
 await caso('AU05','Ruta adicional de movimiento no modifica stock',async()=>{const antes=(await peticion('/productos/1')).datos.stock;assert.equal((await peticion('/movimientos/999','POST',{producto_id:1,cantidad:1,motivo:'Ruta inexistente de auditoria'})).estado,404);assert.equal((await peticion('/productos/1')).datos.stock,antes);});
 await caso('AU06','Ruta adicional de RFC devuelve 404',async()=>assert.equal((await peticion('/cambios/1/no-existe')).estado,404));
 await caso('AU07','Contrasena conserva exactamente los espacios',async()=>{
  const correo='espacios'+marca+'@example.invalid';const clave='  ClaveAudit2026!  ';
  assert.equal((await peticion('/usuarios','POST',{nombre:'Cuenta de espacios',correo,rol:'VENDEDOR',clave,activo:true})).estado,201);
  assert.equal((await ingresar(correo,clave)).estado,200);assert.equal((await ingresar(correo,clave.trim())).estado,401);
 });
 await caso('AU08','Cambio de clave revoca sesiones y registra bitacora',async()=>{
  const correo='seguridad'+marca+'@example.invalid',clave='ClaveAudit2026!';
  const r=await peticion('/usuarios','POST',{nombre:'Cuenta de seguridad',correo,rol:'VENDEDOR',clave,activo:true});assert.equal(r.estado,201);usuario=r.datos;
  const t1=(await ingresar(correo,clave)).datos.token,t2=(await ingresar(correo,clave)).datos.token;
  assert.equal((await peticion('/auth/clave','PUT',{anterior:clave,nueva:'NuevaClave2026!'},t1)).estado,204);
  for(const t of [t1,t2])assert.equal((await peticion('/auth/me','GET',undefined,t)).estado,401);
  const acceso=await ingresar(correo,'NuevaClave2026!');assert.equal(acceso.estado,200);token_usuario=acceso.datos.token;
  const bitacora=(await peticion('/bitacora')).datos;assert.ok(bitacora.some(x=>x.accion==='CAMBIAR_CLAVE'&&x.usuario_id===usuario.id));
 });
 await caso('AU09','Desactivar usuario invalida JWT aun vigente',async()=>{assert.ok(token_usuario);assert.equal((await peticion('/usuarios/'+usuario.id,'PUT',{...usuario,activo:false,clave:''})).estado,200);assert.equal((await peticion('/auth/me','GET',undefined,token_usuario)).estado,401);});
 await caso('AU10','Importe fuera de capacidad rechazado sin mutacion',async()=>{
  const p=await peticion('/productos','POST',{nombre:'Limite decimal',codigo:'AU'+marca,categoria_id:1,precio:'9999999.99',stock_minimo:0,imagen:'producto-1.svg',activo:true});assert.equal(p.estado,201);
  assert.equal((await peticion('/movimientos','POST',{producto_id:p.datos.id,cantidad:100000,motivo:'Prueba de limite monetario'})).estado,201);
  const n=(await peticion('/ventas')).datos.length;
  assert.equal((await peticion('/ventas','POST',{cliente_id:1,items:[{producto_id:p.datos.id,cantidad:100000}]},token,{'Idempotency-Key':'auditoria-limite-'+marca})).estado,422);
  assert.equal((await peticion('/productos/'+p.datos.id)).datos.stock,100000);assert.equal((await peticion('/ventas')).datos.length,n);
 });
 await caso('AU11','JSON y tipo de contenido invalidos rechazados',async()=>{
  for(const [body,type,esperado] of [['{','application/json',400],['{}','text/plain',415],[JSON.stringify({nombre:'x'.repeat(66000)}),'application/json',413]]){
   const r=await fetch(base+'/categorias',{method:'POST',headers:{Authorization:`Bearer ${token}`,'Content-Type':type},body});assert.equal(r.status,esperado);
  }
 });
 await caso('AU12','Cantidad fraccionaria no altera inventario',async()=>{const antes=(await peticion('/productos/1')).datos.stock;assert.equal((await peticion('/movimientos','POST',{producto_id:1,cantidad:1.5,motivo:'Cantidad fraccionaria invalida'})).estado,422);assert.equal((await peticion('/productos/1')).datos.stock,antes);});
 await caso('AU13','Cabeceras y conciliacion despues de casos adversos',async()=>{const r=await peticion('/conciliacion');assert.equal(r.estado,200);assert.equal(r.datos.stock.length,0);assert.equal(r.datos.ventas.length,0);assert.equal(r.cabeceras.get('cache-control'),'no-store');assert.equal(r.cabeceras.get('x-content-type-options'),'nosniff');assert.ok(r.cabeceras.get('content-security-policy').includes("script-src 'self'"));});
 await caso('AU14','Limite de login devuelve 429 y Retry-After',async()=>{let limitado;for(let i=0;i<17;i++){const r=await ingresar('inexistente@example.invalid','incorrecta');if(r.estado===429){limitado=r;break;}}assert.ok(limitado);assert.equal(limitado.cabeceras.get('retry-after'),'60');});
}finally{servidor.kill('SIGTERM');await new Promise(resolve=>servidor.once('exit',resolve));await log.close();}
const informe={fecha:new Date().toISOString(),entorno:'Java 17 y H2 local aislado',jar_sha256:crypto.createHash('sha256').update(await fs.readFile('bin/siscom.jar')).digest('hex'),pruebas:resultados.length,aprobadas:resultados.filter(x=>x.resultado==='APROBADO').length,resultados};
await fs.writeFile('pruebas/auditoria.json',JSON.stringify(informe,null,2));
if(informe.aprobadas!==informe.pruebas)process.exitCode=1;
