'use strict';
const uuid = () => '10000000-1000-4000-8000-100000000000'.replace(/[018]/g, c => (Number(c) ^ crypto.getRandomValues(new Uint8Array(1))[0] & 15 >> Number(c) / 4).toString(16));
const estado = {token:null,usuario:null,pagina:'dashboard',productos:[],categorias:[],clientes:[],carrito:[],clave_venta:uuid(),reporte:null};
const $ = selector => document.querySelector(selector);
const escapar = valor => String(valor ?? '').replace(/[&<>"']/g, letra => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[letra]));
const moneda = valor => new Intl.NumberFormat('es-PE',{style:'currency',currency:'PEN'}).format(Number(valor || 0));
const fecha = valor => valor ? String(valor).replace('T',' ').slice(0,19) : '';
const activo = valor => valor === true || valor === 1;
const puede = (...roles) => roles.includes(estado.usuario?.rol);
const mostrar = mensaje => {$('#aviso').textContent = mensaje;$('#aviso').classList.remove('oculto');clearTimeout(mostrar.reloj);mostrar.reloj = setTimeout(() => $('#aviso').classList.add('oculto'),5500);};
async function api(ruta,metodo='GET',datos,extras={}) {
  const cabeceras = {...extras};
  if (estado.token) cabeceras.Authorization = `Bearer ${estado.token}`;
  if (datos !== undefined) cabeceras['Content-Type'] = 'application/json';
  const respuesta = await fetch(`api${ruta}`,{method:metodo,headers:cabeceras,body:datos === undefined ? undefined : JSON.stringify(datos)});
  if (respuesta.status === 204) return null;
  const salida = await respuesta.json();
  if (!respuesta.ok) {
    if (respuesta.status === 401 && ruta !== '/auth/login') cerrar_sesion();
    throw new Error(salida.error || 'No se pudo completar la solicitud');
  }
  return salida;
}
function cerrar_sesion() {estado.token=null;estado.usuario=null;estado.carrito=[];$('#aplicacion').classList.add('oculto');$('#acceso').classList.remove('oculto');$('#form_acceso').reset();if($('#modal').open)$('#modal').close();}
$('#form_acceso').addEventListener('submit',async evento => {
  evento.preventDefault();const boton = evento.target.querySelector('button');boton.disabled=true;$('#error_acceso').textContent='';
  try {
    const datos = Object.fromEntries(new FormData(evento.target));const acceso = await api('/auth/login','POST',datos);
    estado.token=acceso.token;estado.usuario=acceso.usuario;
    $('#acceso').classList.add('oculto');$('#aplicacion').classList.remove('oculto');
    $('#nombre_usuario').textContent=estado.usuario.nombre;$('#rol_usuario').textContent=estado.usuario.rol;
    $('#iniciales').textContent=estado.usuario.nombre.split(' ').map(x=>x[0]).slice(0,2).join('');
    navegar(puede('VENDEDOR')?'nueva_venta':'dashboard');
  } catch(error) {$('#error_acceso').textContent=error.message;} finally {boton.disabled=false;}
});
$('#salir').addEventListener('click',async()=>{try{await api('/auth/logout','POST',{});}catch(error){mostrar(error.message);}finally{cerrar_sesion();}});
$('#cerrar_modal').addEventListener('click',()=>$('#modal').close());
$('#menu_movil').addEventListener('click',()=>$('#lateral').classList.toggle('abierto'));
$('#cambiar_clave').addEventListener('click',()=>{
 abrir_modal('Cambiar clave',`<form id="form_clave">${campo('anterior','Clave actual','password','',true)}${campo('nueva','Nueva clave de 12 caracteres o mas','password','',true)}<p class="nota">Al guardar se cierran las sesiones vigentes</p><p class="error" id="error_form"></p><div class="acciones"><button class="primario">Guardar clave</button></div></form>`);
 $('#form_clave').addEventListener('submit',async evento=>{evento.preventDefault();try{await api('/auth/clave','PUT',Object.fromEntries(new FormData(evento.target)));cerrar_sesion();mostrar('Clave actualizada Ingresa nuevamente');}catch(error){$('#error_form').textContent=error.message;}});
});
const paginas = [
 ['dashboard','Resumen','◫',['ADMIN','ALMACEN','SUPERVISOR']],
 ['nueva_venta','Nueva venta','＋',['ADMIN','VENDEDOR']],
 ['ventas','Ventas registradas','▤',['ADMIN','VENDEDOR','SUPERVISOR']],
 ['productos','Productos','◇',['ADMIN','VENDEDOR','ALMACEN','SUPERVISOR']],
 ['movimientos','Inventario','▥',['ADMIN','ALMACEN','SUPERVISOR']],
 ['clientes','Clientes','♙',['ADMIN','VENDEDOR','SUPERVISOR']],
 ['categorias','Categorias','▧',['ADMIN','VENDEDOR','ALMACEN','SUPERVISOR']],
 ['reportes','Reportes','▥',['ADMIN','SUPERVISOR']],
 ['cambios','Control de cambios','⇄',['ADMIN','VENDEDOR','ALMACEN','SUPERVISOR']],
 ['bitacora','Bitacora','◷',['ADMIN','SUPERVISOR']],
 ['usuarios','Usuarios','♙',['ADMIN']]
];
async function navegar(pagina) {
 estado.pagina=pagina;$('#lateral').classList.remove('abierto');
 $('#navegacion').innerHTML=paginas.filter(p=>puede(...p[3])).map(p=>`${p[0]==='cambios'?'<span class="separador_nav">GESTION DEL SERVICIO</span>':''}<button data-pagina="${p[0]}" class="${p[0]===pagina?'activo':''}"><span class="icono_nav" aria-hidden="true">${p[2]}</span>${p[1]}</button>`).join('');
 $('#ruta_actual').textContent=`SISCOM / ${paginas.find(p=>p[0]===pagina)?.[1] || pagina}`;
 $('#contenido').innerHTML='<p class="vacio">Cargando informacion</p>';
 try {
  if(pagina==='dashboard')await dashboard();
  else if(pagina==='productos')await productos();
  else if(pagina==='nueva_venta')await nueva_venta();
  else if(pagina==='ventas')await ventas();
  else if(pagina==='cambios')await cambios();
  else if(pagina==='reportes')await reportes();
  else await listado(pagina);
 }catch(error){$('#contenido').innerHTML=`<div class="error_pagina">${escapar(error.message)}</div>`;}
}
$('#navegacion').addEventListener('click',evento=>{const boton=evento.target.closest('[data-pagina]');if(boton)navegar(boton.dataset.pagina);});
function titulo(nombre,descripcion,accion=''){return `<div class="titulo_fila"><div><h1>${nombre}</h1><p>${descripcion}</p></div>${accion}</div>`;}
function badge(valor){const v=String(valor);return `<span class="badge ${['CONFIRMADA','CERRADA','APROBADA','Activo'].includes(v)?'ok':['EMERGENCIA','REVERTIDA','RECHAZADA','Inactivo'].includes(v)?'rojo':['NORMAL','PRUEBAS','EVALUACION','Bajo stock'].includes(v)?'alerta':''}">${escapar(v.replaceAll('_',' '))}</span>`;}
function tabla(encabezados,filas){return `<div class="tabla_contenedor"><table><thead><tr>${encabezados.map(x=>`<th>${x}</th>`).join('')}</tr></thead><tbody>${filas.length?filas.map(x=>`<tr>${x.map(c=>`<td>${c}</td>`).join('')}</tr>`).join(''):`<tr><td colspan="${encabezados.length}" class="vacio">Sin registros para mostrar</td></tr>`}</tbody></table></div>`;}
function grafico(datos){
 const dias=Array.from({length:7},(_,i)=>{const d=new Date();d.setDate(d.getDate()-6+i);return [d.toLocaleDateString('en-CA'),d.toLocaleDateString('es-PE',{weekday:'short'}).replace('.','')];});
 const valores=dias.map(([d])=>Number(datos.find(x=>x.dia===d)?.total || 0));const max=Math.max(...valores,100);
 const puntos=valores.map((v,i)=>`${40+i*77},${166-v/max*125}`);
 return `<svg class="grafico" viewBox="0 0 540 210" role="img" aria-label="Ventas de los ultimos siete dias">${[0,1,2,3].map(i=>`<line x1="40" y1="${41+i*42}" x2="510" y2="${41+i*42}" stroke="#edf1f5"/><text x="0" y="${45+i*42}">${Math.round(max*(1-i/3))}</text>`).join('')}<path d="M ${puntos.join(' L ')} L 502,166 L 40,166 Z" fill="#e9f5f1"/><polyline points="${puntos.join(' ')}" fill="none" stroke="#168b80" stroke-width="3"/>${valores.map((v,i)=>`<circle cx="${40+i*77}" cy="${166-v/max*125}" r="4" fill="#168b80"/><text x="${30+i*77}" y="198">${escapar(dias[i][1])}</text>`).join('')}</svg>`;
}
async function dashboard(){
 const d=await api('/dashboard');
 $('#contenido').innerHTML=titulo('Resumen comercial','Actividad del sistema y seguimiento del servicio',puede('ADMIN')?'<button class="primario" data-ir="nueva_venta">＋ Nueva venta</button>':'')+
 `<div class="tarjetas"><div class="tarjeta destacada"><div class="nombre">${d.hoy?'Ventas del dia':'Productos activos'}<span>↗</span></div><div class="numero">${d.hoy?moneda(d.hoy.total):d.productos}</div><small>${d.hoy?d.hoy.cantidad+' operaciones confirmadas':'Catalogo disponible'}</small></div><div class="tarjeta"><div class="nombre">Productos activos <span>◇</span></div><div class="numero">${d.productos}</div><small>Catalogo de tecnologia</small></div><div class="tarjeta"><div class="nombre">Alertas de stock <span>!</span></div><div class="numero">${d.alertas.length}</div><small>Productos en el minimo o menos</small></div><div class="tarjeta"><div class="nombre">Cambios abiertos <span>⇄</span></div><div class="numero">${d.cambios}</div><small>Solicitudes en seguimiento</small></div></div>`+
 `<div class="grilla_dos"><section class="panel"><div class="panel_cabecera"><h3>${d.hoy?'Ventas de la semana':'Control de inventario'}</h3><small>${d.hoy?'Importes en soles':'Saldo y movimientos'}</small></div><div class="panel_cuerpo">${d.hoy?grafico(d.tendencia):'<p>Revisa los productos con bajo stock y registra las entradas de mercaderia desde Inventario</p><button class="primario" data-ir="movimientos">Ver movimientos</button>'}</div></section><section class="panel"><div class="panel_cabecera"><h3>Reposicion pendiente</h3><small>${d.alertas.length} alertas</small></div><div class="panel_cuerpo">${d.alertas.length?d.alertas.slice(0,4).map(p=>`<div class="alerta_item"><img src="assets/producto-4.svg" alt=""><div><strong>${escapar(p.nombre)}</strong><small>Minimo ${p.stock_minimo} unidades</small></div><span class="badge alerta">${p.stock} disponibles</span></div>`).join(''):'<p class="vacio">Stock por encima del minimo</p>'}<button class="enlace" data-ir="productos">Revisar catalogo ↗</button></div></section></div>`+
 (d.recientes?`<div class="titulo_fila"><h3>Ultimas ventas</h3><button class="enlace" data-ir="ventas">Ver todas ↗</button></div>${tabla(['Comprobante','Cliente','Fecha','Total','Estado'],d.recientes.map(v=>[`<strong>V-${String(v.id).padStart(5,'0')}</strong>`,escapar(v.cliente),fecha(v.fecha),`<strong>${moneda(v.total)}</strong>`,badge('CONFIRMADA')]))}`:'');
}
async function cargar_catalogos(){[estado.productos,estado.categorias]=await Promise.all([api('/productos'),api('/categorias')]);}
async function productos(){
 await cargar_catalogos();
 $('#contenido').innerHTML=titulo('Catalogo de productos','Precios netos y existencias disponibles',puede('ADMIN','ALMACEN')?'<button class="primario" data-editar="productos" data-id="0">＋ Agregar producto</button>':'')+`<div class="barra"><input id="filtro" class="buscar" placeholder="Buscar por nombre o codigo" aria-label="Buscar producto"><select id="categoria_filtro" aria-label="Filtrar categoria"><option value="">Todas las categorias</option>${estado.categorias.map(c=>`<option value="${c.id}">${escapar(c.nombre)}</option>`).join('')}</select><span class="cantidad_registros">${estado.productos.length} productos</span></div><div class="productos" id="productos_grilla"></div>`;
 function dibujar(){const q=$('#filtro').value.toLowerCase(),categoria=$('#categoria_filtro').value;const lista=estado.productos.filter(p=>(p.nombre+' '+p.codigo).toLowerCase().includes(q)&&(!categoria||String(p.categoria_id)===categoria));$('#productos_grilla').innerHTML=lista.map(p=>`<article class="producto"><div class="producto_imagen"><img src="assets/${escapar(p.imagen)}" alt="Ilustracion de ${escapar(p.nombre)}">${!activo(p.activo)?badge('Inactivo'):p.stock<=p.stock_minimo?badge('Bajo stock'):''}</div><div class="producto_info"><small>${escapar(p.codigo)} · ${escapar(estado.categorias.find(c=>c.id===p.categoria_id)?.nombre)}</small><h3>${escapar(p.nombre)}</h3><span class="precio">${moneda(p.precio)}</span><div class="producto_abajo"><small>${p.stock} unidades en stock</small>${puede('ADMIN','ALMACEN')?`<button class="enlace" data-editar="productos" data-id="${p.id}">Editar ↗</button>`:''}</div></div></article>`).join('')||'<p class="vacio">Sin coincidencias</p>';}
 $('#filtro').addEventListener('input',dibujar);$('#categoria_filtro').addEventListener('change',dibujar);dibujar();
}
function campo(nombre,etiqueta,tipo='text',valor='',requerido=true){return `<label>${etiqueta}<input name="${nombre}" type="${tipo}" value="${escapar(valor)}" ${requerido?'required':''} ${tipo==='number'?'step="any"':''}></label>`;}
function seleccionar(nombre,etiqueta,opciones,valor){return `<label>${etiqueta}<select name="${nombre}">${opciones.map(([id,texto])=>`<option value="${escapar(id)}" ${String(id)===String(valor)?'selected':''}>${escapar(texto)}</option>`).join('')}</select></label>`;}
function area(nombre,etiqueta,valor=''){return `<label class="completo">${etiqueta}<textarea name="${nombre}" required>${escapar(valor)}</textarea></label>`;}
function abrir_modal(titulo,contenido){$('#modal_titulo').textContent=titulo;$('#modal_cuerpo').innerHTML=contenido;if(!$('#modal').open)$('#modal').showModal();}
async function editar(recurso,id){
 const dato=id?await api(`/${recurso}/${id}`):{};let campos='';
 if(recurso==='productos'){
  if(!estado.categorias.length)estado.categorias=await api('/categorias');
  campos=campo('codigo','Codigo','text',dato.codigo||'')+campo('nombre','Nombre','text',dato.nombre||'')+seleccionar('categoria_id','Categoria',estado.categorias.filter(c=>activo(c.activo)).map(c=>[c.id,c.nombre]),dato.categoria_id)+campo('precio','Precio neto','number',dato.precio||'')+campo('stock_minimo','Stock minimo','number',dato.stock_minimo??5)+seleccionar('imagen','Ilustracion',[[1,'Laptop'],[2,'Teclado'],[3,'Mouse'],[4,'SSD'],[5,'Audifonos'],[6,'Router']].map(([n,t])=>[`producto-${n}.svg`,t]),dato.imagen||'producto-1.svg');
 }else if(recurso==='clientes'){campos=campo('nombre','Nombre','text',dato.nombre||'')+campo('documento','DNI o RUC','text',dato.documento||'')+campo('telefono','Telefono','text',dato.telefono||'',false)+campo('correo','Correo','email',dato.correo||'',false);}
 else if(recurso==='usuarios'){campos=campo('nombre','Nombre','text',dato.nombre||'')+campo('correo','Correo','email',dato.correo||'')+seleccionar('rol','Rol',['ADMIN','VENDEDOR','ALMACEN','SUPERVISOR'].map(x=>[x,x]),dato.rol)+campo('clave',id?'Nueva clave opcional':'Clave de 12 caracteres o mas','password','',!id);}
 else campos=campo('nombre','Nombre','text',dato.nombre||'');
 campos+=seleccionar('activo','Estado',[[true,'Activo'],[false,'Inactivo']],dato.activo??true);
 abrir_modal(`${id?'Editar':'Agregar'} ${recurso==='categorias'?'categoria':recurso.slice(0,-1)}`,`<form id="form_editar"><div class="form_grilla">${campos}</div>${recurso==='productos'?'<p class="nota">El stock se modifica mediante movimientos de inventario</p>':''}<p class="error" id="error_form"></p><div class="acciones"><button class="primario">Guardar registro</button></div></form>`);
 $('#form_editar').addEventListener('submit',async evento=>{evento.preventDefault();const boton=evento.target.querySelector('button');boton.disabled=true;try{const datos=Object.fromEntries(new FormData(evento.target));datos.activo=datos.activo==='true';await api(`/${recurso}${id?'/'+id:''}`,id?'PUT':'POST',datos);$('#modal').close();mostrar('Registro guardado');await navegar(estado.pagina);}catch(error){$('#error_form').textContent=error.message;}finally{boton.disabled=false;}});
}
async function listado(pagina){
 const datos=await api(`/${pagina}`);const accion=pagina==='movimientos'?puede('ADMIN','ALMACEN')?'<button class="primario" data-movimiento>＋ Registrar movimiento</button>':'':['clientes','categorias','usuarios'].includes(pagina)&&puede(...(pagina==='clientes'?['ADMIN','VENDEDOR']:pagina==='categorias'?['ADMIN','ALMACEN']:['ADMIN']))?`<button class="primario" data-editar="${pagina}" data-id="0">＋ Agregar registro</button>`:'';
 const titulos={movimientos:['Inventario','Entradas ajustes y salidas vinculadas a ventas'],clientes:['Clientes','Datos de contacto y documentos de venta'],categorias:['Categorias','Organizacion del catalogo comercial'],usuarios:['Usuarios','Accesos y permisos por rol'],bitacora:['Bitacora del sistema','Registro de acciones con actor y fecha']};
 let columnas,filas;
 const editar_boton=d=>`<button class="enlace" data-editar="${pagina}" data-id="${d.id}">Editar</button>`;
 if(pagina==='movimientos'){columnas=['Fecha','Producto','Tipo','Cantidad','Saldo','Motivo','Usuario'];filas=datos.map(d=>[fecha(d.fecha),`<strong>${escapar(d.producto)}</strong>`,badge(d.tipo),d.cantidad,d.saldo_resultante,escapar(d.motivo),escapar(d.usuario)]);}
 else if(pagina==='clientes'){columnas=['Documento','Cliente','Telefono','Correo','Estado',''];filas=datos.map(d=>[escapar(d.documento),`<strong>${escapar(d.nombre)}</strong>`,escapar(d.telefono),escapar(d.correo),badge(activo(d.activo)?'Activo':'Inactivo'),puede('ADMIN','VENDEDOR')?editar_boton(d):'']);}
 else if(pagina==='categorias'){columnas=['Codigo','Nombre','Estado',''];filas=datos.map(d=>[d.id,`<strong>${escapar(d.nombre)}</strong>`,badge(activo(d.activo)?'Activo':'Inactivo'),puede('ADMIN','ALMACEN')?editar_boton(d):'']);}
 else if(pagina==='usuarios'){columnas=['Nombre','Correo','Rol','Estado',''];filas=datos.map(d=>[`<strong>${escapar(d.nombre)}</strong>`,escapar(d.correo),badge(d.rol),badge(activo(d.activo)?'Activo':'Inactivo'),editar_boton(d)]);}
 else{columnas=['Fecha','Actor','Accion','Entidad','Registro','Resultado'];filas=datos.map(d=>[fecha(d.fecha),escapar(d.usuario),escapar(d.accion),escapar(d.entidad),d.entidad_id,badge(d.resultado)]);}
 $('#contenido').innerHTML=titulo(...titulos[pagina],accion)+`<div class="barra"><input id="filtro_tabla" class="buscar" placeholder="Buscar registro" aria-label="Buscar registro"><span class="cantidad_registros">${datos.length} registros</span></div>`+tabla(columnas,filas);
 $('#filtro_tabla').addEventListener('input',evento=>{const q=evento.target.value.toLowerCase();document.querySelectorAll('#contenido tbody tr').forEach(fila=>fila.hidden=!fila.textContent.toLowerCase().includes(q));});
}
async function movimiento(){
 const productos=await api('/productos');abrir_modal('Registrar movimiento',`<form id="form_movimiento">${seleccionar('producto_id','Producto',productos.filter(p=>activo(p.activo)).map(p=>[p.id,`${p.nombre} · stock ${p.stock}`]))}${campo('cantidad','Cantidad positiva para entrada o negativa para ajuste','number','')}${area('motivo','Motivo del movimiento')}<p class="error" id="error_form"></p><div class="acciones"><button class="primario">Registrar movimiento</button></div></form>`);
 $('#form_movimiento').addEventListener('submit',async evento=>{evento.preventDefault();try{await api('/movimientos','POST',Object.fromEntries(new FormData(evento.target)));$('#modal').close();mostrar('Movimiento registrado');navegar('movimientos');}catch(error){$('#error_form').textContent=error.message;}});
}
async function nueva_venta(){
 [estado.productos,estado.clientes]=await Promise.all([api('/productos'),api('/clientes')]);
 $('#contenido').innerHTML=titulo('Nueva venta','El servidor verifica los precios y el stock antes de confirmar')+`<div class="venta_layout"><section><div class="barra"><input id="buscar_venta" class="buscar" placeholder="Buscar producto o codigo" aria-label="Buscar producto"></div><div id="lista_venta" class="lista_venta"></div></section><section class="panel carrito"><h3>Detalle de la venta</h3><label>Cliente<select id="cliente_venta">${estado.clientes.filter(c=>activo(c.activo)).map(c=>`<option value="${c.id}">${escapar(c.nombre)}</option>`).join('')}</select></label><div id="carrito_items"></div><div class="totales" id="totales"></div><button class="primario ancho" id="confirmar_venta">Confirmar venta <span>↗</span></button><p class="nota">Tasa de demostracion 18 % · Comprobante interno</p></section></div>`;
 function listar(){const q=$('#buscar_venta').value.toLowerCase();$('#lista_venta').innerHTML=estado.productos.filter(p=>activo(p.activo)&&(p.nombre+' '+p.codigo).toLowerCase().includes(q)).map(p=>`<button class="item_venta" data-agregar="${p.id}" ${p.stock<=0?'disabled':''}><img src="assets/${escapar(p.imagen)}" alt=""><div><strong>${escapar(p.nombre)}</strong><span>${moneda(p.precio)}</span><small>${p.stock} disponibles · ${escapar(p.codigo)}</small></div></button>`).join('');}
 $('#buscar_venta').addEventListener('input',listar);listar();dibujar_carrito();
 $('#confirmar_venta').addEventListener('click',async()=>{
  if(!estado.carrito.length){mostrar('Agrega al menos un producto');return;}const boton=$('#confirmar_venta');boton.disabled=true;
  try{const resultado=await api('/ventas','POST',{cliente_id:Number($('#cliente_venta').value),items:estado.carrito.map(x=>({producto_id:x.id,cantidad:x.cantidad}))},{'Idempotency-Key':estado.clave_venta});estado.carrito=[];estado.clave_venta=uuid();await navegar('ventas');recibo(resultado);mostrar('Venta confirmada e inventario actualizado');}catch(error){mostrar(error.message);boton.disabled=false;}
 });
}
function dibujar_carrito(){
 if(!$('#carrito_items'))return;
 $('#carrito_items').innerHTML=estado.carrito.length?estado.carrito.map(p=>`<div class="linea_carrito"><div><strong>${escapar(p.nombre)}</strong><p>${moneda(p.precio*p.cantidad)}</p></div><div class="control_cantidad"><button data-cantidad="${p.id}" data-delta="-1" aria-label="Reducir ${escapar(p.nombre)}">−</button><span>${p.cantidad}</span><button data-cantidad="${p.id}" data-delta="1" aria-label="Aumentar ${escapar(p.nombre)}">+</button></div></div>`).join(''):'<p class="vacio">Selecciona productos del catalogo</p>';
 const subtotal=estado.carrito.reduce((a,p)=>a+Math.round(Number(p.precio)*100)*p.cantidad,0)/100;const impuesto=Math.round(subtotal*18)/100;
 $('#totales').innerHTML=`<p><span>Subtotal</span><strong>${moneda(subtotal)}</strong></p><p><span>Impuesto estimado 18 %</span><strong>${moneda(impuesto)}</strong></p><p><span>Total</span><span>${moneda(subtotal+impuesto)}</span></p>`;
}
async function ventas(){const lista=await api('/ventas');$('#contenido').innerHTML=titulo('Ventas registradas','Operaciones confirmadas y comprobantes internos',puede('ADMIN','VENDEDOR')?'<button class="primario" data-ir="nueva_venta">＋ Nueva venta</button>':'')+tabla(['Comprobante','Fecha','Cliente','Vendedor','Total','Estado',''],lista.map(v=>[`<strong>V-${String(v.id).padStart(5,'0')}</strong>`,fecha(v.fecha),escapar(v.cliente),escapar(v.vendedor),`<strong>${moneda(v.total)}</strong>`,badge(v.estado),`<button class="enlace" data-recibo="${v.id}">Ver comprobante</button>`]));}
function recibo(v){abrir_modal(`Comprobante V-${String(v.id).padStart(5,'0')}`,`<div class="recibo"><h3>SISCOM</h3><p>Comprobante interno de venta<br>${fecha(v.fecha)}<br>${escapar(v.cliente)}</p>${tabla(['Producto','Cantidad','Precio','Subtotal'],v.detalles.map(d=>[escapar(d.nombre),d.cantidad,moneda(d.precio_unitario),moneda(d.subtotal)]))}<p>Subtotal ${moneda(v.subtotal)} · Impuesto ${moneda(v.impuesto)}</p><div class="total_recibo">${moneda(v.total)}</div><p>Documento de demostracion sin validez tributaria</p></div><div class="acciones"><button class="primario" data-imprimir>Imprimir</button></div>`);}
async function cambios(){
 const lista=await api('/cambios');const abiertos=lista.filter(c=>!['CERRADA','RECHAZADA'].includes(c.estado));
 $('#contenido').innerHTML=titulo('Control de cambios','Solicitudes RFC y seguimiento del servicio', '<button class="primario" data-nueva_rfc>＋ Nueva solicitud</button>')+`<div class="rfc_resumen"><div><span>Solicitudes registradas</span><strong>${lista.length}</strong></div><div><span>En seguimiento</span><strong>${abiertos.length}</strong></div><div><span>En evaluacion CAB</span><strong>${lista.filter(c=>c.estado==='EVALUACION').length}</strong></div><div><span>Cerradas</span><strong>${lista.filter(c=>c.estado==='CERRADA').length}</strong></div></div>`+tabla(['Solicitud','Tipo','Componente','Riesgo','Estado','Solicitante',''],lista.map(c=>[`<strong>RFC-${String(c.id).padStart(3,'0')}</strong><small>${escapar(c.titulo)}</small>`,badge(c.tipo),escapar(c.componente),`${c.riesgo}/25`,badge(c.estado),escapar(c.solicitante),`<button class="enlace" data-rfc="${c.id}">Ver solicitud ↗</button>`]));
}
function nueva_rfc(){
 abrir_modal('Nueva solicitud de cambio',`<form id="form_rfc"><div class="form_grilla"><div class="completo">${campo('titulo','Titulo de la solicitud')}</div>${seleccionar('tipo','Tipo de cambio',['ESTANDAR','NORMAL','EMERGENCIA'].map(x=>[x,x]),'NORMAL')}${seleccionar('componente','Componente',['APLICACION','BASE_DATOS','INFRAESTRUCTURA','DOCUMENTACION','CRONOGRAMA'].map(x=>[x,x]))}${seleccionar('impacto','Impacto 1 minimo a 5 critico',[1,2,3,4,5].map(x=>[x,x]),3)}${seleccionar('probabilidad','Probabilidad 1 rara a 5 frecuente',[1,2,3,4,5].map(x=>[x,x]),2)}${campo('version_objetivo','Version objetivo','text','3.0.1')}${campo('enlace_issue','Enlace a GitHub Issues o Jira','url','',false)}${area('justificacion','Justificacion e impacto del cambio')}${area('plan_pruebas','Plan de pruebas y criterios de aceptacion')}${area('plan_reversion','Plan de reversion y responsable')}</div><p class="nota">Riesgo = impacto × probabilidad · Estandar admite hasta 4 puntos</p><p class="error" id="error_form"></p><div class="acciones"><button class="primario">Registrar solicitud</button></div></form>`);
 $('#form_rfc').addEventListener('submit',async evento=>{evento.preventDefault();try{const resultado=await api('/cambios','POST',Object.fromEntries(new FormData(evento.target)));$('#modal').close();await navegar('cambios');ver_rfc(resultado.id);mostrar('Solicitud registrada');}catch(error){$('#error_form').textContent=error.message;}});
}
async function ver_rfc(id){
 const c=await api(`/cambios/${id}`);const flujo={REGISTRADA:['EVALUACION'],EVALUACION:['APROBADA','RECHAZADA'],APROBADA:['PRUEBAS'],PRUEBAS:['PROGRAMADA','RECHAZADA'],PROGRAMADA:['DESPLEGADA','REVERTIDA'],DESPLEGADA:['CERRADA','REVERTIDA'],REVERTIDA:['CERRADA']};
 let opciones=(flujo[c.estado]||[]).filter(s=>puede('ADMIN','SUPERVISOR')&&(!['DESPLEGADA','REVERTIDA'].includes(s)||puede('ADMIN'))&&(!['CERRADA','PROGRAMADA'].includes(s)||puede('SUPERVISOR')));
 if(c.solicitante_id===estado.usuario.id)opciones=opciones.filter(s=>!['APROBADA','RECHAZADA','PROGRAMADA'].includes(s));
 if(c.tipo==='NORMAL'&&!puede('SUPERVISOR'))opciones=opciones.filter(s=>!['APROBADA','RECHAZADA'].includes(s));
 if(c.tipo==='EMERGENCIA'&&!puede('ADMIN'))opciones=opciones.filter(s=>!['APROBADA','RECHAZADA'].includes(s));
 abrir_modal(`RFC-${String(c.id).padStart(3,'0')} ${c.titulo}`,`<p>${badge(c.tipo)} ${badge(c.estado)} <span class="nota">Riesgo ${c.riesgo}/25 · Version ${escapar(c.version_objetivo)}</span></p>${[['Justificacion',c.justificacion],['Plan de pruebas',c.plan_pruebas],['Plan de reversion',c.plan_reversion],['Evidencia QA',c.evidencia_qa],['Respaldo',c.respaldo],['Ventana',c.ventana]].filter(([,v])=>v).map(([t,v])=>`<div class="detalle_bloque"><h3>${t}</h3><p>${escapar(v)}</p></div>`).join('')}<div class="detalle_bloque"><h3>Historial de la solicitud</h3><div class="historial">${c.eventos.map(e=>`<div class="evento"><strong>${escapar(e.estado_nuevo)}</strong><small>${escapar(e.actor)} · ${fecha(e.fecha)}</small><p>${escapar(e.nota)}</p></div>`).join('')}</div></div>${opciones.length?`<form id="form_transicion">${seleccionar('estado','Siguiente etapa',opciones.map(x=>[x,x]))}${area('nota','Registro de evaluacion o resultado')}<div id="campos_qa" class="${opciones[0]==='PROGRAMADA'?'':'oculto'}">${area('evidencia_qa','Evidencia y resultados de pruebas')}${campo('respaldo','Ruta del respaldo')}${campo('artefacto_sha','SHA256 del artefacto')}${campo('ventana','Ventana de mantenimiento')}</div><p class="nota">La transicion registra la evidencia El despliegue y la reversion se ejecutan con los scripts operativos</p><p class="error" id="error_form"></p><div class="acciones"><button class="secundario" type="button" data-exportar_rfc="${c.id}">Exportar RFC</button><button class="primario">Registrar transicion</button></div></form>`:`<button class="secundario" data-exportar_rfc="${c.id}">Exportar RFC</button>`}`);
 if($('#form_transicion')){
  function adaptar(){const qa=$('#form_transicion select').value==='PROGRAMADA';$('#campos_qa').classList.toggle('oculto',!qa);$('#campos_qa').querySelectorAll('input,textarea').forEach(x=>{x.required=qa;x.disabled=!qa;});}
  $('#form_transicion select').addEventListener('change',adaptar);adaptar();
  $('#form_transicion').addEventListener('submit',async evento=>{evento.preventDefault();const boton=evento.target.querySelector('button.primario');boton.disabled=true;try{await api(`/cambios/${id}/transicion`,'POST',Object.fromEntries(new FormData(evento.target)));await navegar('cambios');await ver_rfc(id);mostrar('Transicion registrada');}catch(error){$('#error_form').textContent=error.message;boton.disabled=false;}});
 }
}
function descargar(nombre,texto,tipo){const url=URL.createObjectURL(new Blob([texto],{type:tipo}));const enlace=document.createElement('a');enlace.href=url;enlace.download=nombre;enlace.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
async function reportes(){
 const hoy=new Date().toLocaleDateString('en-CA'),inicio=hoy.slice(0,8)+'01';
 $('#contenido').innerHTML=titulo('Reportes de ventas','Consulta por periodo y exportacion de resultados')+`<form id="form_reporte" class="barra"><label>Desde<input type="date" name="desde" value="${inicio}" required></label><label>Hasta<input type="date" name="hasta" value="${hoy}" required></label><button class="primario">Consultar</button><button class="secundario" type="button" id="exportar_csv">Descargar CSV</button></form><div id="resultado_reporte"></div>`;
 async function consultar(){const datos=Object.fromEntries(new FormData($('#form_reporte')));estado.reporte=await api(`/reportes/ventas?desde=${datos.desde}&hasta=${datos.hasta}`);const r=estado.reporte;$('#resultado_reporte').innerHTML=`<div class="reporte_resumen">${[['Operaciones',r.resumen.cantidad],['Subtotal',moneda(r.resumen.subtotal)],['Impuesto',moneda(r.resumen.impuesto)],['Total',moneda(r.resumen.total)]].map(([t,v])=>`<div><span>${t}</span><strong>${v}</strong></div>`).join('')}</div>`+tabla(['Venta','Fecha','Cliente','Subtotal','Impuesto','Total'],r.ventas.map(v=>[v.id,fecha(v.fecha),escapar(v.cliente),moneda(v.subtotal),moneda(v.impuesto),`<strong>${moneda(v.total)}</strong>`]));}
 $('#form_reporte').addEventListener('submit',async evento=>{evento.preventDefault();try{await consultar();}catch(error){mostrar(error.message);}});
 $('#exportar_csv').addEventListener('click',()=>{if(!estado.reporte)return;const limpiar=valor=>{let s=String(valor??'');if(/^[=+@\-\t\r]/.test(s))s="'"+s;return '"'+s.replaceAll('"','""')+'"';};const filas=[['venta','fecha','cliente','vendedor','subtotal','impuesto','total'],...estado.reporte.ventas.map(v=>[v.id,v.fecha,v.cliente,v.vendedor,v.subtotal,v.impuesto,v.total])];descargar('siscom_ventas.csv','\ufeff'+filas.map(f=>f.map(limpiar).join(';')).join('\r\n'),'text/csv;charset=utf-8');});
 await consultar();
}
document.addEventListener('click',async evento=>{
 const b=evento.target.closest('button');if(!b)return;
 try{
 if(b.dataset.ir)navegar(b.dataset.ir);
 if(b.dataset.editar)await editar(b.dataset.editar,Number(b.dataset.id));
 if(b.hasAttribute('data-movimiento'))await movimiento();
 if(b.dataset.agregar){const p=estado.productos.find(p=>p.id===Number(b.dataset.agregar));let item=estado.carrito.find(x=>x.id===p.id);if((item?.cantidad||0)>=p.stock){mostrar('No hay mas unidades disponibles');return;}if(item)item.cantidad++;else estado.carrito.push({...p,cantidad:1});estado.clave_venta=uuid();dibujar_carrito();}
 if(b.dataset.cantidad){const item=estado.carrito.find(x=>x.id===Number(b.dataset.cantidad)),delta=Number(b.dataset.delta);if(item.cantidad+delta>item.stock){mostrar('No hay mas unidades disponibles');return;}item.cantidad+=delta;estado.carrito=estado.carrito.filter(x=>x.cantidad>0);estado.clave_venta=uuid();dibujar_carrito();}
 if(b.dataset.recibo)recibo(await api(`/ventas/${b.dataset.recibo}`));
 if(b.hasAttribute('data-imprimir'))window.print();
 if(b.hasAttribute('data-nueva_rfc'))nueva_rfc();
 if(b.dataset.rfc)await ver_rfc(Number(b.dataset.rfc));
 if(b.dataset.exportar_rfc){const rfc=await api(`/cambios/${b.dataset.exportar_rfc}`);descargar(`RFC-${rfc.id}.json`,JSON.stringify(rfc,null,2),'application/json');}
 }catch(error){mostrar(error.message);}
});
