package pe.edu.siscom;

import java.sql.*;
import java.math.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;
import static pe.edu.siscom.Repositorio.*;

public final class Servicio_ventas {
    public static String huella(String valor) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
        StringBuilder salida = new StringBuilder();
        for (byte b : bytes) salida.append(String.format("%02x", b));
        return salida.toString();
    }
    public static JsonObject detalle(Connection conexion, long id, JsonObject usuario) throws Exception {
        Seguridad.permitir(usuario, "ADMIN", "VENDEDOR", "SUPERVISOR");
        JsonObject venta = uno(conexion, "SELECT v.*,c.nombre AS cliente,u.nombre AS vendedor FROM venta v JOIN cliente c ON c.id=v.cliente_id JOIN usuario u ON u.id=v.usuario_id WHERE v.id=?", id);
        if (venta == null) throw new Fallo(404, "Venta no encontrada");
        if (usuario.get("rol").getAsString().equals("VENDEDOR") && usuario.get("id").getAsLong() != venta.get("usuario_id").getAsLong()) throw new Fallo(403, "Venta no autorizada");
        venta.add("detalles", consultar(conexion, "SELECT d.*,p.nombre,p.codigo FROM detalle_venta d JOIN producto p ON p.id=d.producto_id WHERE d.venta_id=?", id));
        return venta;
    }
    public static JsonObject registrar(Connection conexion, JsonObject usuario, JsonObject entrada, String clave) throws Exception {
        Seguridad.permitir(usuario, "ADMIN", "VENDEDOR");
        if (clave == null || !clave.matches("[A-Za-z0-9_-]{12,80}")) throw new Fallo(422, "Clave de peticion requerida");
        int cliente = entero(entrada, "cliente_id", 1, Integer.MAX_VALUE);
        if (!entrada.has("items") || !entrada.get("items").isJsonArray() || entrada.getAsJsonArray("items").size() == 0 || entrada.getAsJsonArray("items").size() > 100) throw new Fallo(422, "Agregue productos a la venta");
        TreeMap<Integer, Integer> cantidades = new TreeMap<Integer, Integer>();
        for (JsonElement elemento : entrada.getAsJsonArray("items")) {
            if (!elemento.isJsonObject()) throw new Fallo(422, "Detalle invalido");
            JsonObject item = elemento.getAsJsonObject();
            int id = entero(item, "producto_id", 1, Integer.MAX_VALUE), cantidad = entero(item, "cantidad", 1, 100000);
            int suma = cantidades.getOrDefault(id, 0) + cantidad;
            if (suma > 100000) throw new Fallo(422, "Cantidad excedida");
            cantidades.put(id, suma);
        }
        String firma = huella(cliente + ":" + cantidades.toString());
        long actor = usuario.get("id").getAsLong();
        conexion.setAutoCommit(false);
        try {
            uno(conexion, "SELECT id FROM usuario WHERE id=? FOR UPDATE", actor);
            JsonObject anterior = uno(conexion, "SELECT * FROM venta WHERE clave_peticion=?", clave);
            if (anterior != null) {
                if (anterior.get("usuario_id").getAsLong() != actor || !firma.equals(anterior.get("huella_peticion").getAsString())) throw new Fallo(409, "Clave usada con otra solicitud");
                conexion.commit();
                return detalle(conexion, anterior.get("id").getAsLong(), usuario);
            }
            if (uno(conexion, "SELECT id FROM cliente WHERE id=? AND activo=TRUE", cliente) == null) throw new Fallo(422, "Cliente no disponible");
            LinkedHashMap<Integer, JsonObject> productos = new LinkedHashMap<Integer, JsonObject>();
            BigDecimal subtotal = BigDecimal.ZERO;
            for (Map.Entry<Integer, Integer> item : cantidades.entrySet()) {
                JsonObject producto = uno(conexion, "SELECT * FROM producto WHERE id=? AND activo=TRUE FOR UPDATE", item.getKey());
                if (producto == null) throw new Fallo(404, "Producto no encontrado");
                if (producto.get("stock").getAsInt() < item.getValue()) throw new Fallo(409, "Stock insuficiente para " + producto.get("nombre").getAsString());
                productos.put(item.getKey(), producto);
                subtotal = subtotal.add(producto.get("precio").getAsBigDecimal().multiply(BigDecimal.valueOf(item.getValue())));
            }
            subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
            BigDecimal tasa = uno(conexion, "SELECT valor FROM parametro WHERE clave='tasa_igv'").get("valor").getAsBigDecimal();
            BigDecimal impuesto = subtotal.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
            BigDecimal limite = new BigDecimal("9999999999.99");
            if (subtotal.compareTo(limite) > 0 || subtotal.add(impuesto).compareTo(limite) > 0) throw new Fallo(422, "El importe supera la capacidad por comprobante");
            long venta = insertar(conexion, "INSERT INTO venta(cliente_id,usuario_id,subtotal,tasa_igv,impuesto,total,clave_peticion,huella_peticion) VALUES(?,?,?,?,?,?,?,?)", cliente, actor, subtotal, tasa, impuesto, subtotal.add(impuesto), clave, firma);
            for (Map.Entry<Integer, Integer> item : cantidades.entrySet()) {
                JsonObject producto = productos.get(item.getKey());
                BigDecimal precio = producto.get("precio").getAsBigDecimal();
                int saldo = producto.get("stock").getAsInt() - item.getValue();
                insertar(conexion, "INSERT INTO detalle_venta(venta_id,producto_id,cantidad,precio_unitario,subtotal) VALUES(?,?,?,?,?)", venta, item.getKey(), item.getValue(), precio, precio.multiply(BigDecimal.valueOf(item.getValue())));
                if (ejecutar(conexion, "UPDATE producto SET stock=stock-? WHERE id=? AND stock>=?", item.getValue(), item.getKey(), item.getValue()) != 1) throw new Fallo(409, "Stock no disponible");
                insertar(conexion, "INSERT INTO movimiento_inventario(producto_id,usuario_id,venta_id,tipo,cantidad,saldo_resultante,motivo) VALUES(?,?,?,?,?,?,?)", item.getKey(), actor, venta, "SALIDA", -item.getValue(), saldo, "Venta " + venta);
            }
            if ("true".equals(Configuracion.obtener("pruebas.fallo", "false")) && booleano(entrada, "fallo_prueba", false)) throw new Fallo(500, "Fallo de prueba antes del commit");
            auditar(conexion, actor, "REGISTRAR_VENTA", "venta", venta);
            conexion.commit();
            return detalle(conexion, venta, usuario);
        } catch (Exception e) { conexion.rollback(); throw e; }
        finally { conexion.setAutoCommit(true); }
    }
    public static JsonObject mover(Connection conexion, JsonObject usuario, JsonObject entrada) throws Exception {
        Seguridad.permitir(usuario, "ADMIN", "ALMACEN");
        int producto = entero(entrada, "producto_id", 1, Integer.MAX_VALUE);
        int cantidad = entero(entrada, "cantidad", -100000, 100000);
        if (cantidad == 0) throw new Fallo(422, "La cantidad no puede ser cero");
        String motivo = texto(entrada, "motivo", 5, 200);
        conexion.setAutoCommit(false);
        try {
            JsonObject fila = uno(conexion, "SELECT stock FROM producto WHERE id=? AND activo=TRUE FOR UPDATE", producto);
            if (fila == null) throw new Fallo(404, "Producto no encontrado");
            int saldo = fila.get("stock").getAsInt() + cantidad;
            if (saldo < 0) throw new Fallo(409, "El ajuste genera stock negativo");
            ejecutar(conexion, "UPDATE producto SET stock=? WHERE id=?", saldo, producto);
            long id = insertar(conexion, "INSERT INTO movimiento_inventario(producto_id,usuario_id,tipo,cantidad,saldo_resultante,motivo) VALUES(?,?,?,?,?,?)", producto, usuario.get("id").getAsLong(), cantidad > 0 ? "ENTRADA" : "AJUSTE", cantidad, saldo, motivo);
            auditar(conexion, usuario.get("id").getAsLong(), "MOVIMIENTO", "movimiento_inventario", id);
            conexion.commit(); return objeto("id", id, "stock", saldo);
        } catch (Exception e) { conexion.rollback(); throw e; }
        finally { conexion.setAutoCommit(true); }
    }
    private Servicio_ventas() { }
}
