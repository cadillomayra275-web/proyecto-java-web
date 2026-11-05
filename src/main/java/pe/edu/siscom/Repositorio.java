package pe.edu.siscom;

import java.sql.*;
import java.util.*;
import com.google.gson.*;

public final class Repositorio {
    public static final Gson json = new GsonBuilder().serializeNulls().create();
    public static void parametros(PreparedStatement sentencia, Object... valores) throws SQLException {
        for (int i = 0; i < valores.length; i++) sentencia.setObject(i + 1, valores[i]);
    }
    public static JsonArray consultar(Connection conexion, String sql, Object... valores) throws SQLException {
        JsonArray filas = new JsonArray();
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            parametros(sentencia, valores);
            try (ResultSet resultado = sentencia.executeQuery()) {
                ResultSetMetaData datos = resultado.getMetaData();
                while (resultado.next()) {
                    JsonObject fila = new JsonObject();
                    for (int i = 1; i <= datos.getColumnCount(); i++) {
                        Object valor = resultado.getObject(i);
                        String nombre = datos.getColumnLabel(i).toLowerCase(Locale.ROOT);
                        if (valor == null) fila.add(nombre, JsonNull.INSTANCE);
                        else if (valor instanceof Number) fila.addProperty(nombre, (Number) valor);
                        else if (valor instanceof Boolean) fila.addProperty(nombre, (Boolean) valor);
                        else fila.addProperty(nombre, valor.toString());
                    }
                    filas.add(fila);
                }
            }
        }
        return filas;
    }
    public static JsonObject uno(Connection conexion, String sql, Object... valores) throws SQLException {
        JsonArray filas = consultar(conexion, sql, valores);
        return filas.size() == 0 ? null : filas.get(0).getAsJsonObject();
    }
    public static long insertar(Connection conexion, String sql, Object... valores) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            parametros(sentencia, valores); sentencia.executeUpdate();
            try (ResultSet llaves = sentencia.getGeneratedKeys()) { if (!llaves.next()) return 0; Object llave = llaves.getObject(1); return llave instanceof Number ? ((Number) llave).longValue() : 0; }
        }
    }
    public static int ejecutar(Connection conexion, String sql, Object... valores) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) { parametros(sentencia, valores); return sentencia.executeUpdate(); }
    }
    public static void auditar(Connection conexion, long usuario, String accion, String entidad, long id) throws SQLException {
        insertar(conexion, "INSERT INTO bitacora(usuario_id,accion,entidad,entidad_id,resultado,trace_id) VALUES(?,?,?,?,?,?)", usuario, accion, entidad, id, "OK", UUID.randomUUID().toString());
    }
    public static String texto(JsonObject objeto, String clave, int minimo, int maximo) {
        try {
            if (!objeto.has(clave) || objeto.get(clave).isJsonNull() || !objeto.get(clave).isJsonPrimitive()) throw new Exception();
            String valor = objeto.get(clave).getAsString().trim();
            if (valor.length() < minimo || valor.length() > maximo) throw new Exception();
            return valor;
        } catch (Exception e) { throw new Fallo(422, "Revisar campo " + clave); }
    }
    public static int entero(JsonObject objeto, String clave, int minimo, int maximo) {
        try {
            int numero = new java.math.BigDecimal(texto(objeto, clave, 1, 15)).intValueExact();
            if (numero < minimo || numero > maximo) throw new Exception();
            return numero;
        } catch (Exception e) { throw new Fallo(422, "Revisar campo " + clave); }
    }
    public static String contrasena(JsonObject objeto, String clave, int minimo) {
        if (!objeto.has(clave) || !objeto.get(clave).isJsonPrimitive() || !objeto.getAsJsonPrimitive(clave).isString()) throw new Fallo(422, "Revisar campo " + clave);
        String valor = objeto.get(clave).getAsString();
        if (valor.length() < minimo || valor.length() > 100) throw new Fallo(422, "Revisar campo " + clave);
        return valor;
    }
    public static boolean booleano(JsonObject objeto, String clave, boolean defecto) {
        if (!objeto.has(clave)) return defecto;
        if (!objeto.get(clave).isJsonPrimitive() || !objeto.getAsJsonPrimitive(clave).isBoolean()) throw new Fallo(422, "Revisar campo " + clave);
        return objeto.get(clave).getAsBoolean();
    }
    public static JsonObject objeto(Object... pares) {
        JsonObject resultado = new JsonObject();
        for (int i = 0; i < pares.length; i += 2) resultado.add(pares[i].toString(), json.toJsonTree(pares[i + 1]));
        return resultado;
    }
    private Repositorio() { }
}
