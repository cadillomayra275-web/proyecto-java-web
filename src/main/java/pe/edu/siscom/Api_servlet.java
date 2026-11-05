package pe.edu.siscom;

import javax.servlet.http.*;
import java.io.*;
import java.sql.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import com.google.gson.*;
import org.mindrot.jbcrypt.BCrypt;
import static pe.edu.siscom.Repositorio.*;

public final class Api_servlet extends HttpServlet {
    private final Map<String, long[]> intentos = new HashMap<String, long[]>();
    private synchronized void limitar(String direccion) {
        long ahora = System.currentTimeMillis();
        Iterator<Map.Entry<String, long[]>> iterador = intentos.entrySet().iterator();
        while (iterador.hasNext()) if (ahora - iterador.next().getValue()[0] > 60000) iterador.remove();
        if (intentos.size() > 10000) throw new Fallo(429, "Intente mas tarde");
        long[] registro = intentos.get(direccion);
        if (registro == null) { registro = new long[] {ahora, 0}; intentos.put(direccion, registro); }
        if (++registro[1] > 15) throw new Fallo(429, "Espere un minuto antes de ingresar");
    }
    private JsonObject leer(HttpServletRequest peticion) throws IOException {
        if (peticion.getContentType() == null || !peticion.getContentType().toLowerCase(Locale.ROOT).startsWith("application/json")) throw new Fallo(415, "Use contenido JSON");
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        byte[] bloque = new byte[4096]; int cantidad;
        while ((cantidad = peticion.getInputStream().read(bloque)) >= 0) { salida.write(bloque, 0, cantidad); if (salida.size() > 65536) throw new Fallo(413, "Solicitud demasiado grande"); }
        try { return JsonParser.parseString(new String(salida.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (Exception e) { throw new Fallo(400, "JSON invalido"); }
    }
    private void responder(HttpServletResponse respuesta, int estado, Object datos) throws IOException {
        respuesta.setStatus(estado);
        if (estado != 204) respuesta.getWriter().write(json.toJson(datos));
    }
    @Override public void service(HttpServletRequest peticion, HttpServletResponse respuesta) throws IOException {
        peticion.setCharacterEncoding("UTF-8"); respuesta.setCharacterEncoding("UTF-8"); respuesta.setContentType("application/json;charset=UTF-8");
        respuesta.setHeader("Cache-Control", "no-store");
        String ruta = peticion.getPathInfo(); if (ruta == null) ruta = "/";
        String metodo = peticion.getMethod();
        try (Connection conexion = Base_datos.abrir()) {
            if (ruta.equals("/health") && metodo.equals("GET")) { uno(conexion, "SELECT 1 AS disponible"); responder(respuesta, 200, objeto("estado", "UP", "version", "3.0.1")); return; }
            if (ruta.equals("/auth/login") && metodo.equals("POST")) { limitar(peticion.getRemoteAddr()); responder(respuesta, 200, Seguridad.ingresar(conexion, leer(peticion))); return; }
            JsonObject usuario = Seguridad.autenticar(conexion, peticion.getHeader("Authorization"));
            long actor = usuario.get("id").getAsLong(); String rol = usuario.get("rol").getAsString();
            if (ruta.equals("/auth/me") && metodo.equals("GET")) { usuario.remove("jti"); usuario.remove("exp"); responder(respuesta, 200, usuario); return; }
            if (ruta.equals("/auth/logout") && metodo.equals("POST")) {
                ejecutar(conexion, "DELETE FROM token_revocado WHERE expira_en<CURRENT_TIMESTAMP");
                insertar(conexion, "INSERT INTO token_revocado(jti,usuario_id,expira_en) VALUES(?,?,?)", usuario.get("jti").getAsString(), actor, new Timestamp(usuario.get("exp").getAsLong() * 1000));
                responder(respuesta, 204, null); return;
            }
            if (ruta.equals("/auth/clave") && metodo.equals("PUT")) {
                JsonObject entrada = leer(peticion); String nueva = contrasena(entrada, "nueva", 12), anterior = contrasena(entrada, "anterior", 1);
                if (nueva.getBytes(StandardCharsets.UTF_8).length > 72 || anterior.getBytes(StandardCharsets.UTF_8).length > 72) throw new Fallo(422, "Maximo 72 bytes por clave");
                conexion.setAutoCommit(false);
                try {
                    JsonObject registro = uno(conexion, "SELECT clave_hash FROM usuario WHERE id=? FOR UPDATE", actor);
                    if (!BCrypt.checkpw(anterior, registro.get("clave_hash").getAsString())) throw new Fallo(401, "Clave actual incorrecta");
                    ejecutar(conexion, "UPDATE usuario SET clave_hash=?,version_token=version_token+1 WHERE id=?", BCrypt.hashpw(nueva, BCrypt.gensalt(12)), actor);
                    auditar(conexion, actor, "CAMBIAR_CLAVE", "usuario", actor);
                    conexion.commit();
                } catch (Exception e) { conexion.rollback(); throw e; }
                finally { conexion.setAutoCommit(true); }
                responder(respuesta, 204, null); return;
            }
            String[] partes = ruta.split("/");
            String recurso = partes.length > 1 ? partes[1] : "";
            long id = 0;
            if (partes.length > 2 && !recurso.equals("reportes")) {
                try { id = Long.parseLong(partes[2]); if (id <= 0) throw new Exception(); }
                catch (Exception e) { throw new Fallo(400, "Identificador invalido"); }
            }
            if (recurso.equals("productos") || recurso.equals("categorias") || recurso.equals("clientes") || recurso.equals("usuarios")) {
                if (partes.length > 3) throw new Fallo(404, "Ruta no encontrada");
                catalogo(conexion, usuario, recurso, id, metodo, peticion, respuesta); return;
            }
            if (recurso.equals("ventas")) {
                if (partes.length > 3) throw new Fallo(404, "Ruta no encontrada");
                Seguridad.permitir(usuario, "ADMIN", "VENDEDOR", "SUPERVISOR");
                if (metodo.equals("POST") && id == 0) { responder(respuesta, 201, Servicio_ventas.registrar(conexion, usuario, leer(peticion), peticion.getHeader("Idempotency-Key"))); return; }
                if (metodo.equals("GET") && id > 0) { responder(respuesta, 200, Servicio_ventas.detalle(conexion, id, usuario)); return; }
                if (metodo.equals("GET")) {
                    String consulta = "SELECT v.id,v.fecha,v.total,v.estado,c.nombre AS cliente,u.nombre AS vendedor FROM venta v JOIN cliente c ON c.id=v.cliente_id JOIN usuario u ON u.id=v.usuario_id";
                    responder(respuesta, 200, rol.equals("VENDEDOR") ? consultar(conexion, consulta + " WHERE v.usuario_id=? ORDER BY v.id DESC LIMIT 500", actor) : consultar(conexion, consulta + " ORDER BY v.id DESC LIMIT 500")); return;
                }
            }
            if (recurso.equals("movimientos")) {
                if (partes.length != 2) throw new Fallo(404, "Ruta no encontrada");
                Seguridad.permitir(usuario, "ADMIN", "ALMACEN", "SUPERVISOR");
                if (metodo.equals("POST")) { responder(respuesta, 201, Servicio_ventas.mover(conexion, usuario, leer(peticion))); return; }
                if (metodo.equals("GET")) { responder(respuesta, 200, consultar(conexion, "SELECT m.*,p.nombre AS producto,u.nombre AS usuario FROM movimiento_inventario m JOIN producto p ON p.id=m.producto_id JOIN usuario u ON u.id=m.usuario_id ORDER BY m.id DESC LIMIT 500")); return; }
            }
            if (recurso.equals("cambios")) {
                if (partes.length > 3 && !(partes.length == 4 && partes[3].equals("transicion") && metodo.equals("POST"))) throw new Fallo(404, "Ruta no encontrada");
                if (metodo.equals("GET") && id == 0) { responder(respuesta, 200, consultar(conexion, "SELECT c.*,u.nombre AS solicitante FROM solicitud_cambio c JOIN usuario u ON u.id=c.solicitante_id ORDER BY c.id DESC LIMIT 500")); return; }
                if (metodo.equals("GET") && id > 0) { responder(respuesta, 200, Servicio_cambios.detalle(conexion, id)); return; }
                if (metodo.equals("POST") && id == 0) { responder(respuesta, 201, Servicio_cambios.crear(conexion, usuario, leer(peticion))); return; }
                if (metodo.equals("POST") && id > 0 && partes.length == 4 && partes[3].equals("transicion")) { responder(respuesta, 200, Servicio_cambios.avanzar(conexion, usuario, id, leer(peticion))); return; }
            }
            if (ruta.equals("/dashboard") && metodo.equals("GET")) {
                Seguridad.permitir(usuario, "ADMIN", "SUPERVISOR", "ALMACEN");
                JsonObject datos = objeto("productos", uno(conexion, "SELECT COUNT(*) AS n FROM producto WHERE activo=TRUE").get("n"), "alertas", consultar(conexion, "SELECT id,codigo,nombre,stock,stock_minimo FROM producto WHERE activo=TRUE AND stock<=stock_minimo ORDER BY stock"), "cambios", uno(conexion, "SELECT COUNT(*) AS n FROM solicitud_cambio WHERE estado NOT IN ('CERRADA','RECHAZADA')").get("n"));
                if (!rol.equals("ALMACEN")) {
                    datos.add("hoy", uno(conexion, "SELECT COUNT(*) AS cantidad,COALESCE(SUM(total),0) AS total FROM venta WHERE CAST(fecha AS DATE)=CURRENT_DATE"));
                    datos.add("tendencia", consultar(conexion, "SELECT CAST(fecha AS DATE) AS dia,SUM(total) AS total FROM venta WHERE fecha>=? GROUP BY CAST(fecha AS DATE) ORDER BY dia", java.sql.Date.valueOf(LocalDate.now().minusDays(6))));
                    datos.add("recientes", consultar(conexion, "SELECT v.id,c.nombre AS cliente,v.total,v.fecha FROM venta v JOIN cliente c ON c.id=v.cliente_id ORDER BY v.id DESC LIMIT 5"));
                }
                responder(respuesta, 200, datos); return;
            }
            if (ruta.equals("/reportes/ventas") && metodo.equals("GET")) {
                Seguridad.permitir(usuario, "ADMIN", "SUPERVISOR");
                LocalDate desde, hasta;
                try { desde = LocalDate.parse(peticion.getParameter("desde")); hasta = LocalDate.parse(peticion.getParameter("hasta")); if (hasta.isBefore(desde)) throw new Exception(); }
                catch (Exception e) { throw new Fallo(422, "Rango de fechas invalido"); }
                JsonArray ventas = consultar(conexion, "SELECT v.id,v.fecha,c.nombre AS cliente,u.nombre AS vendedor,v.subtotal,v.impuesto,v.total FROM venta v JOIN cliente c ON c.id=v.cliente_id JOIN usuario u ON u.id=v.usuario_id WHERE v.fecha>=? AND v.fecha<? ORDER BY v.id", java.sql.Date.valueOf(desde), java.sql.Date.valueOf(hasta.plusDays(1)));
                responder(respuesta, 200, objeto("ventas", ventas, "resumen", uno(conexion, "SELECT COUNT(*) AS cantidad,COALESCE(SUM(subtotal),0) AS subtotal,COALESCE(SUM(impuesto),0) AS impuesto,COALESCE(SUM(total),0) AS total FROM venta WHERE fecha>=? AND fecha<?", java.sql.Date.valueOf(desde), java.sql.Date.valueOf(hasta.plusDays(1))))); return;
            }
            if (ruta.equals("/conciliacion") && metodo.equals("GET")) {
                Seguridad.permitir(usuario, "ADMIN", "SUPERVISOR");
                responder(respuesta, 200, objeto("stock", consultar(conexion, "SELECT p.id,p.stock,COALESCE(SUM(m.cantidad),0) AS calculado FROM producto p LEFT JOIN movimiento_inventario m ON m.producto_id=p.id GROUP BY p.id,p.stock HAVING p.stock<>COALESCE(SUM(m.cantidad),0)"), "ventas", consultar(conexion, "SELECT v.id,v.subtotal,COALESCE(SUM(d.subtotal),0) AS calculado FROM venta v LEFT JOIN detalle_venta d ON d.venta_id=v.id GROUP BY v.id,v.subtotal HAVING v.subtotal<>COALESCE(SUM(d.subtotal),0)"))); return;
            }
            if (ruta.equals("/bitacora") && metodo.equals("GET")) { Seguridad.permitir(usuario, "ADMIN", "SUPERVISOR"); responder(respuesta, 200, consultar(conexion, "SELECT b.*,u.nombre AS usuario FROM bitacora b LEFT JOIN usuario u ON u.id=b.usuario_id ORDER BY b.id DESC LIMIT 500")); return; }
            throw new Fallo(404, "Ruta no encontrada");
        } catch (Fallo e) {
            if (e.estado == 401) respuesta.setHeader("WWW-Authenticate", "Bearer");
            if (e.estado == 429) respuesta.setHeader("Retry-After", "60");
            responder(respuesta, e.estado, objeto("error", e.getMessage()));
        } catch (SQLException e) {
            boolean integridad = e.getSQLState() != null && e.getSQLState().startsWith("23");
            System.err.println("SQL " + e.getSQLState() + " " + e.getErrorCode());
            responder(respuesta, integridad ? 409 : 503, objeto("error", integridad ? "Registro duplicado o relacion no valida" : "Servicio de datos no disponible"));
        } catch (Exception e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
            responder(respuesta, 500, objeto("error", "No se pudo completar la operacion"));
        }
    }
    private void catalogo(Connection conexion, JsonObject usuario, String recurso, long id, String metodo, HttpServletRequest peticion, HttpServletResponse respuesta) throws Exception {
        String tabla = recurso.equals("categorias") ? "categoria" : recurso.equals("productos") ? "producto" : recurso.equals("clientes") ? "cliente" : "usuario";
        boolean lectura = metodo.equals("GET");
        if (recurso.equals("usuarios")) Seguridad.permitir(usuario, "ADMIN");
        else if (recurso.equals("clientes")) Seguridad.permitir(usuario, lectura ? new String[] {"ADMIN", "VENDEDOR", "SUPERVISOR"} : new String[] {"ADMIN", "VENDEDOR"});
        else if (!lectura) Seguridad.permitir(usuario, "ADMIN", "ALMACEN");
        String campos = recurso.equals("usuarios") ? "id,nombre,correo,rol,activo,version_token" : "*";
        if (lectura) {
            if (id > 0) { JsonObject dato = uno(conexion, "SELECT " + campos + " FROM " + tabla + " WHERE id=?", id); if (dato == null) throw new Fallo(404, "Registro no encontrado"); responder(respuesta, 200, dato); }
            else responder(respuesta, 200, consultar(conexion, "SELECT " + campos + " FROM " + tabla + " ORDER BY id DESC LIMIT 5000"));
            return;
        }
        if (!metodo.equals("POST") && !metodo.equals("PUT")) throw new Fallo(405, "Metodo no permitido");
        if ((metodo.equals("POST") && id > 0) || (metodo.equals("PUT") && id == 0)) throw new Fallo(400, "Ruta no valida");
        JsonObject entrada = leer(peticion);
        if (id > 0 && uno(conexion, "SELECT id FROM " + tabla + " WHERE id=?", id) == null) throw new Fallo(404, "Registro no encontrado");
        LinkedHashMap<String, Object> campos_nuevos = new LinkedHashMap<String, Object>();
        campos_nuevos.put("nombre", texto(entrada, "nombre", 2, recurso.equals("categorias") ? 80 : recurso.equals("usuarios") ? 100 : 120));
        if (recurso.equals("clientes")) {
            String documento = texto(entrada, "documento", 8, 11);
            if (!documento.matches("[0-9]{8}|[0-9]{11}")) throw new Fallo(422, "Documento de 8 u 11 digitos");
            campos_nuevos.put("documento", documento); campos_nuevos.put("telefono", texto(entrada, "telefono", 0, 20)); campos_nuevos.put("correo", texto(entrada, "correo", 0, 120));
        }
        if (recurso.equals("usuarios")) {
            String correo = texto(entrada, "correo", 3, 120).toLowerCase(Locale.ROOT);
            if (!correo.matches("[^ @]+@[^ @]+\\.[^ @]+")) throw new Fallo(422, "Correo invalido");
            String rol = texto(entrada, "rol", 1, 20);
            if (!Arrays.asList("ADMIN", "VENDEDOR", "ALMACEN", "SUPERVISOR").contains(rol)) throw new Fallo(422, "Rol invalido");
            campos_nuevos.put("correo", correo); campos_nuevos.put("rol", rol);
            String clave_opcional = entrada.has("clave") ? contrasena(entrada, "clave", 0) : "";
            if (id == 0 || !clave_opcional.isEmpty()) {
                String clave = contrasena(entrada, "clave", 12);
                if (clave.getBytes(StandardCharsets.UTF_8).length > 72) throw new Fallo(422, "Maximo 72 bytes por clave");
                campos_nuevos.put("clave_hash", BCrypt.hashpw(clave, BCrypt.gensalt(12)));
            }
        }
        if (recurso.equals("productos")) {
            int categoria = entero(entrada, "categoria_id", 1, Integer.MAX_VALUE);
            if (uno(conexion, "SELECT id FROM categoria WHERE id=? AND activo=TRUE", categoria) == null) throw new Fallo(422, "Categoria no disponible");
            BigDecimal precio;
            try { precio = new BigDecimal(texto(entrada, "precio", 1, 15)).setScale(2, RoundingMode.UNNECESSARY); if (precio.signum() < 0 || precio.compareTo(new BigDecimal("9999999.99")) > 0) throw new Exception(); }
            catch (Exception e) { throw new Fallo(422, "Precio invalido con maximo dos decimales"); }
            campos_nuevos.put("categoria_id", categoria); campos_nuevos.put("codigo", texto(entrada, "codigo", 2, 30)); campos_nuevos.put("precio", precio); campos_nuevos.put("stock_minimo", entero(entrada, "stock_minimo", 0, 100000));
            String imagen = texto(entrada, "imagen", 1, 80);
            if (!imagen.matches("producto-[1-6]\\.svg")) throw new Fallo(422, "Imagen no valida");
            campos_nuevos.put("imagen", imagen);
        }
        boolean activo = booleano(entrada, "activo", true);
        if (recurso.equals("usuarios") && id == usuario.get("id").getAsLong() && (!activo || !campos_nuevos.get("rol").equals("ADMIN"))) throw new Fallo(422, "Conserve su cuenta administrativa activa");
        campos_nuevos.put("activo", activo);
        conexion.setAutoCommit(false);
        try {
            if (id == 0) {
                String columnas = String.join(",", campos_nuevos.keySet());
                String marcadores = String.join(",", Collections.nCopies(campos_nuevos.size(), "?"));
                id = insertar(conexion, "INSERT INTO " + tabla + "(" + columnas + ") VALUES(" + marcadores + ")", campos_nuevos.values().toArray());
            } else {
                List<String> asignaciones = new ArrayList<String>();
                for (String campo : campos_nuevos.keySet()) asignaciones.add(campo + "=?");
                List<Object> valores = new ArrayList<Object>(campos_nuevos.values()); valores.add(id);
                ejecutar(conexion, "UPDATE " + tabla + " SET " + String.join(",", asignaciones) + (recurso.equals("usuarios") ? ",version_token=version_token+1" : "") + " WHERE id=?", valores.toArray());
            }
            auditar(conexion, usuario.get("id").getAsLong(), metodo.equals("POST") ? "CREAR" : "ACTUALIZAR", tabla, id);
            conexion.commit(); responder(respuesta, metodo.equals("POST") ? 201 : 200, uno(conexion, "SELECT " + campos + " FROM " + tabla + " WHERE id=?", id));
        } catch (Exception e) { conexion.rollback(); throw e; }
        finally { conexion.setAutoCommit(true); }
    }
}
