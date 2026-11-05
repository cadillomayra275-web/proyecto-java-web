import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import crypto from 'node:crypto';

const base = process.argv[2] || 'http://localhost:8080/api';
if (!['localhost','127.0.0.1'].includes(new URL(base).hostname)) throw new Error('Las pruebas mutantes requieren un entorno local');
const resultados = [];
const prefijo = 'QA' + Date.now();
const tokens = {};
let producto, cliente, categoria, venta, cambio;
async function solicitud(ruta,metodo='GET',datos,token,clave) {
 const encabezados = {'Content-Type':'application/json'};
 if(token)encabezados.Authorization=`Bearer ${token}`;
 if(clave)encabezados['Idempotency-Key']=clave;
 const respuesta = await fetch(base+ruta,{method:metodo,headers:encabezados,body:datos===undefined?undefined:JSON.stringify(datos)});
 const texto = await respuesta.text();
 return {estado:respuesta.status,datos:texto?JSON.parse(texto):null};
}
async function caso(id,nombre,accion){
 const inicio=performance.now();
 try{await accion();resultados.push({id,nombre,resultado:'APROBADO',duracion_ms:Math.round(performance.now()-inicio)});console.log(id+' APROBADO '+nombre);}
 catch(error){resultados.push({id,nombre,resultado:'FALLIDO',detalle:error.message,duracion_ms:Math.round(performance.now()-inicio)});console.error(id+' FALLIDO '+error.message);}
}
const nuevo_producto=(nombre,codigo)=>({nombre,codigo,categoria_id:categoria,precio:'100.00',stock_minimo:2,imagen:'producto-1.svg',activo:true});
const nueva_rfc=(tipo='NORMAL')=>({titulo:prefijo+' Control transaccional',tipo,componente:'APLICACION',justificacion:'Prevenir cambios sin autorizacion en el sistema',impacto:tipo==='ESTANDAR'?1:3,probabilidad:tipo==='ESTANDAR'?1:2,plan_pruebas:'Verificar venta login stock roles y registros',plan_reversion:'Detener servicio restaurar artefacto y verificar integridad',version_objetivo:'3.0.0',enlace_issue:''});
const transicion=(id,estado,token,extra={})=>solicitud(`/cambios/${id}/transicion`,'POST',{estado,nota:'Evidencia de prueba automatizada en laboratorio',...extra},token);
await caso('CP01','Salud del servicio',async()=>assert.equal((await solicitud('/health')).estado,200));
await caso('CP02','Proteccion sin token',async()=>assert.equal((await solicitud('/productos')).estado,401));
await caso('CP03','Rechazo de clave incorrecta',async()=>assert.equal((await solicitud('/auth/login','POST',{correo:'admin@siscom.local',clave:'incorrecta'})).estado,401));
await caso('CP04','Rechazo de inyeccion SQL en login',async()=>assert.equal((await solicitud('/auth/login','POST',{correo:"' OR 1=1 --",clave:'incorrecta'})).estado,401));
await caso('CP05','Login de cuatro roles y JWT de 15 minutos',async()=>{
 for(const [rol,correo] of [['admin','admin'],['ventas','ventas'],['almacen','almacen'],['supervisor','supervisor']]){
  const r=await solicitud('/auth/login','POST',{correo:correo+'@siscom.local',clave:'Siscom2026!'});assert.equal(r.estado,200);assert.equal(r.datos.expira_en,900);assert.equal(r.datos.usuario.clave_hash,undefined);tokens[rol]=r.datos.token;
  const payload=JSON.parse(Buffer.from(r.datos.token.split('.')[1],'base64url'));assert.equal(payload.exp-payload.iat,900);assert.equal(payload.iss,'siscom');assert.equal(payload.aud,'siscom-api');
 }
});
await caso('CP06','Vendedor sin acceso a usuarios',async()=>assert.equal((await solicitud('/usuarios','GET',undefined,tokens.ventas)).estado,403));
await caso('CP07','Almacen sin acceso a ventas',async()=>assert.equal((await solicitud('/ventas','GET',undefined,tokens.almacen)).estado,403));
await caso('CP08','Supervisor no modifica catalogo',async()=>assert.equal((await solicitud('/categorias','POST',{nombre:prefijo},tokens.supervisor)).estado,403));
await caso('CP09','Alta y duplicidad de categoria',async()=>{const r=await solicitud('/categorias','POST',{nombre:prefijo},tokens.admin);assert.equal(r.estado,201);categoria=r.datos.id;assert.equal((await solicitud('/categorias','POST',{nombre:prefijo},tokens.admin)).estado,409);});
await caso('CP10','Alta y validacion de cliente',async()=>{
 const cuerpo={nombre:'Cliente '+prefijo,documento:String(Date.now()).slice(-8),telefono:'900000000',correo:'qa@example.invalid'};
 assert.equal((await solicitud('/clientes','POST',{...cuerpo,documento:'abc'},tokens.admin)).estado,422);
 const r=await solicitud('/clientes','POST',cuerpo,tokens.admin);assert.equal(r.estado,201);cliente=r.datos.id;
});
await caso('CP11','Alta de producto y precio invalido',async()=>{assert.equal((await solicitud('/productos','POST',{...nuevo_producto('Producto '+prefijo,prefijo),precio:'-1'},tokens.admin)).estado,422);const r=await solicitud('/productos','POST',nuevo_producto('Producto '+prefijo,prefijo),tokens.admin);assert.equal(r.estado,201);producto=r.datos.id;assert.equal(r.datos.stock,0);});
await caso('CP12','Entrada con trazabilidad de stock',async()=>{const r=await solicitud('/movimientos','POST',{producto_id:producto,cantidad:10,motivo:'Entrada para prueba de integridad'},tokens.almacen);assert.equal(r.estado,201);assert.equal(r.datos.stock,10);});
await caso('CP13','Venta calculada en servidor',async()=>{const r=await solicitud('/ventas','POST',{cliente_id:cliente,items:[{producto_id:producto,cantidad:2}],total:1,usuario_id:999},tokens.ventas,prefijo+'venta');assert.equal(r.estado,201);assert.equal(Number(r.datos.subtotal),200);assert.equal(Number(r.datos.impuesto),36);assert.equal(Number(r.datos.total),236);assert.equal(r.datos.usuario_id,2);venta=r.datos.id;assert.equal((await solicitud('/productos/'+producto,'GET',undefined,tokens.admin)).datos.stock,8);});
await caso('CP14','Reintento idempotente sin segundo descuento',async()=>{const r=await solicitud('/ventas','POST',{cliente_id:cliente,items:[{producto_id:producto,cantidad:2}]},tokens.ventas,prefijo+'venta');assert.equal(r.estado,201);assert.equal(r.datos.id,venta);assert.equal((await solicitud('/productos/'+producto,'GET',undefined,tokens.admin)).datos.stock,8);});
await caso('CP15','Rechazo de clave con contenido diferente',async()=>assert.equal((await solicitud('/ventas','POST',{cliente_id:cliente,items:[{producto_id:producto,cantidad:1}]},tokens.ventas,prefijo+'venta')).estado,409));
await caso('CP16','Cantidad cero rechazada',async()=>assert.equal((await solicitud('/ventas','POST',{cliente_id:cliente,items:[{producto_id:producto,cantidad:0}]},tokens.ventas,prefijo+'cero')).estado,422));
await caso('CP17','Sobreventa rechazada sin cambiar stock',async()=>{assert.equal((await solicitud('/ventas','POST',{cliente_id:cliente,items:[{producto_id:producto,cantidad:999}]},tokens.ventas,prefijo+'sobreventa')).estado,409);assert.equal((await solicitud('/productos/'+producto,'GET',undefined,tokens.admin)).datos.stock,8);});
await caso('CP18','Concurrencia sobre la ultima unidad',async()=>{
 const r=await solicitud('/productos','POST',nuevo_producto('Ultima unidad '+prefijo,prefijo+'C'),tokens.admin);const id=r.datos.id;
 await solicitud('/movimientos','POST',{producto_id:id,cantidad:1,motivo:'Ultima unidad para concurrencia'},tokens.admin);
 const cuerpo={cliente_id:cliente,items:[{producto_id:id,cantidad:1}]};
 const salidas=await Promise.all([solicitud('/ventas','POST',cuerpo,tokens.admin,prefijo+'c1'),solicitud('/ventas','POST',cuerpo,tokens.ventas,prefijo+'c2')]);
 assert.deepEqual(salidas.map(x=>x.estado).sort(),[201,409]);assert.equal((await solicitud('/productos/'+id,'GET',undefined,tokens.admin)).datos.stock,0);
});
await caso('CP19','Vendedor no accede a venta ajena',async()=>{const r=await solicitud('/ventas','POST',{cliente_id:cliente,items:[{producto_id:producto,cantidad:1}]},tokens.admin,prefijo+'propiedad');assert.equal(r.estado,201);assert.equal((await solicitud('/ventas/'+r.datos.id,'GET',undefined,tokens.ventas)).estado,403);});
await caso('CP20','Conciliacion de stock y ventas',async()=>{const r=await solicitud('/conciliacion','GET',undefined,tokens.admin);assert.equal(r.estado,200);assert.equal(r.datos.stock.length,0);assert.equal(r.datos.ventas.length,0);});
await caso('CP21','Identificador mal formado e inexistente',async()=>{assert.equal((await solicitud('/productos/abc','GET',undefined,tokens.admin)).estado,400);assert.equal((await solicitud('/productos/999999','GET',undefined,tokens.admin)).estado,404);});
await caso('CP22','Rechazo de firma JWT alterada',async()=>{const p=tokens.admin.split('.');p[2]=(p[2][0]==='a'?'b':'a')+p[2].slice(1);assert.equal((await solicitud('/productos','GET',undefined,p.join('.'))).estado,401);});
await caso('CP23','Persistencia de texto potencialmente XSS',async()=>{const r=await solicitud('/clientes','POST',{nombre:'<img src=x onerror=alert(1)>',documento:String(Date.now()).slice(-8),telefono:'',correo:''},tokens.admin);assert.equal(r.estado,201);assert.equal(r.datos.nombre,'<img src=x onerror=alert(1)>');});
await caso('CP24','RFC con riesgo calculado y evento inicial',async()=>{const r=await solicitud('/cambios','POST',nueva_rfc(),tokens.admin);assert.equal(r.estado,201);assert.equal(r.datos.riesgo,6);assert.equal(r.datos.eventos.length,1);cambio=r.datos.id;});
await caso('CP25','Cambio estandar no admite riesgo alto',async()=>assert.equal((await solicitud('/cambios','POST',{...nueva_rfc('ESTANDAR'),impacto:5},tokens.admin)).estado,422));
await caso('CP26','No se permite saltar a despliegue',async()=>assert.equal((await transicion(cambio,'DESPLEGADA',tokens.admin)).estado,409));
await caso('CP27','Segregacion del solicitante y aprobador',async()=>{assert.equal((await transicion(cambio,'EVALUACION',tokens.admin)).estado,200);assert.equal((await transicion(cambio,'APROBADA',tokens.admin)).estado,403);});
await caso('CP28','Aprobacion normal y entrada a pruebas',async()=>{assert.equal((await transicion(cambio,'APROBADA',tokens.supervisor)).estado,200);assert.equal((await transicion(cambio,'PRUEBAS',tokens.admin)).estado,200);});
await caso('CP29','QA requiere evidencia respaldo SHA y ventana',async()=>{assert.equal((await transicion(cambio,'PROGRAMADA',tokens.supervisor)).estado,422);const extra={evidencia_qa:'CP01 a CP28 aprobados en ejecucion local',respaldo:'respaldos/ensayo-controlado.zip',artefacto_sha:crypto.createHash('sha256').update(await fs.readFile('bin/siscom.jar')).digest('hex'),ventana:'Laboratorio 21 octubre 2026 20:00 a 20:30'};assert.equal((await transicion(cambio,'PROGRAMADA',tokens.supervisor,extra)).estado,200);});
await caso('CP30','Despliegue autorizado y cierre supervisado',async()=>{assert.equal((await transicion(cambio,'DESPLEGADA',tokens.supervisor)).estado,403);assert.equal((await transicion(cambio,'DESPLEGADA',tokens.admin)).estado,200);assert.equal((await transicion(cambio,'CERRADA',tokens.supervisor)).estado,200);const r=await solicitud('/cambios/'+cambio,'GET',undefined,tokens.admin);assert.equal(r.datos.estado,'CERRADA');assert.equal(r.datos.eventos.length,7);});
await caso('CP31','Emergencia reservada a autoridad administrativa',async()=>{const r=await solicitud('/cambios','POST',nueva_rfc('EMERGENCIA'),tokens.ventas);const id=r.datos.id;await transicion(id,'EVALUACION',tokens.admin);assert.equal((await transicion(id,'APROBADA',tokens.supervisor)).estado,403);assert.equal((await transicion(id,'APROBADA',tokens.admin)).estado,200);});
await caso('CP32','Vendedor sin permiso para transiciones',async()=>assert.equal((await transicion(1,'EVALUACION',tokens.ventas)).estado,403));
await caso('CP33','Reporte por periodo y fechas invalidas',async()=>{const r=await solicitud('/reportes/ventas?desde=2026-01-01&hasta=2030-12-31','GET',undefined,tokens.admin);assert.equal(r.estado,200);assert.ok(r.datos.resumen.cantidad>=3);assert.equal((await solicitud('/reportes/ventas?desde=x&hasta=y','GET',undefined,tokens.admin)).estado,422);});
await caso('CP34','Bitacora registra operaciones y autor',async()=>{const r=await solicitud('/bitacora','GET',undefined,tokens.admin);assert.equal(r.estado,200);assert.ok(r.datos.some(x=>x.accion==='REGISTRAR_VENTA'));assert.ok(r.datos.some(x=>x.accion==='RFC_CERRADA'));});
await caso('CP35','Logout revoca JWT inmediatamente',async()=>{assert.equal((await solicitud('/auth/logout','POST',{},tokens.almacen)).estado,204);assert.equal((await solicitud('/productos','GET',undefined,tokens.almacen)).estado,401);});
await caso('CP36','Ajuste negativo protegido',async()=>assert.equal((await solicitud('/movimientos','POST',{producto_id:producto,cantidad:-1000,motivo:'Prueba de saldo negativo'},tokens.admin)).estado,409));
await caso('CP37','Proteccion de la cuenta administrativa propia',async()=>{const u=(await solicitud('/usuarios/1','GET',undefined,tokens.admin)).datos;assert.equal((await solicitud('/usuarios/1','PUT',{...u,activo:false,clave:''},tokens.admin)).estado,422);});
await caso('CP38','Rollback integral ante fallo antes del commit',async()=>{
 const antes=(await solicitud('/productos/'+producto,'GET',undefined,tokens.admin)).datos.stock;
 const total_antes=(await solicitud('/ventas','GET',undefined,tokens.admin)).datos.length;
 const r=await solicitud('/ventas','POST',{cliente_id:cliente,items:[{producto_id:producto,cantidad:1}],fallo_prueba:true},tokens.admin,prefijo+'rollback');
 assert.equal(r.estado,500);assert.equal((await solicitud('/productos/'+producto,'GET',undefined,tokens.admin)).datos.stock,antes);assert.equal((await solicitud('/ventas','GET',undefined,tokens.admin)).datos.length,total_antes);
 const c=await solicitud('/conciliacion','GET',undefined,tokens.admin);assert.equal(c.datos.stock.length,0);assert.equal(c.datos.ventas.length,0);
});
await caso('CP39','JWT firmado pero expirado rechazado',async()=>{
 const partes=tokens.admin.split('.');const datos=JSON.parse(Buffer.from(partes[1],'base64url'));datos.exp=Math.floor(Date.now()/1000)-1;
 const cuerpo=partes[0]+'.'+Buffer.from(JSON.stringify(datos)).toString('base64url');
 const secreto=process.env.SISCOM_JWT_SECRETO?Buffer.from(process.env.SISCOM_JWT_SECRETO):await fs.readFile('datos/clave_jwt.bin');
 const token=cuerpo+'.'+crypto.createHmac('sha256',secreto).update(cuerpo).digest('base64url');
 assert.equal((await solicitud('/productos','GET',undefined,token)).estado,401);
});
await caso('CP40','Clave superior a 72 bytes rechazada',async()=>assert.equal((await solicitud('/auth/login','POST',{correo:'admin@siscom.local',clave:'x'.repeat(73)})).estado,401));
const resumen={jar_sha256:crypto.createHash('sha256').update(await fs.readFile('bin/siscom.jar')).digest('hex'),fecha:new Date().toISOString(),entorno:base,java_objetivo:'8 o superior',pruebas:resultados.length,aprobadas:resultados.filter(x=>x.resultado==='APROBADO').length,resultados};
await fs.writeFile('pruebas/resultados.json',JSON.stringify(resumen,null,2));
console.log(JSON.stringify({pruebas:resumen.pruebas,aprobadas:resumen.aprobadas}));
if(resumen.aprobadas!==resumen.pruebas)process.exitCode=1;
