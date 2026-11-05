package pe.edu.siscom;

import java.sql.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.mindrot.jbcrypt.BCrypt;
import static pe.edu.siscom.Repositorio.*;

public final class Base_datos {
    public static Connection abrir() throws SQLException {
        return DriverManager.getConnection(Configuracion.url(), Configuracion.obtener("db.usuario", "sa"), Configuracion.obtener("db.clave", ""));
    }
    public static void iniciar() throws Exception {
        Class.forName(Configuracion.demo() ? "org.h2.Driver" : "com.mysql.cj.jdbc.Driver");
        if (Configuracion.demo()) {
            Files.createDirectories(Paths.get("datos"));
            try (Connection conexion = abrir()) {
                String esquema = new String(Files.readAllBytes(Paths.get("sql/01_esquema.sql")), StandardCharsets.UTF_8);
                for (String sql : esquema.split(";")) if (!sql.trim().isEmpty()) ejecutar(conexion, sql);
                String controles = new String(Files.readAllBytes(Paths.get("sql/06_integridad_apf3.sql")), StandardCharsets.UTF_8);
                for (String sql : controles.split(";")) if (!sql.trim().isEmpty()) {
                    String nombre = sql.trim().split("\\s+")[5].toLowerCase(java.util.Locale.ROOT);
                    if (uno(conexion, "SELECT constraint_name FROM information_schema.table_constraints WHERE LOWER(constraint_name)=?", nombre) == null) ejecutar(conexion, sql);
                }
                if (uno(conexion, "SELECT id FROM usuario LIMIT 1") == null) poblar(conexion);
            }
        } else try (Connection conexion = abrir()) { uno(conexion, "SELECT id FROM usuario LIMIT 1"); }
    }
    public static void poblar(Connection conexion) throws Exception {
        conexion.setAutoCommit(false);
        try {
            String[] nombres = {"Administracion Demo", "Ventas Demo", "Almacen Demo", "Supervision Demo"};
            String[] correos = {"admin@siscom.local", "ventas@siscom.local", "almacen@siscom.local", "supervisor@siscom.local"};
            String[] roles = {"ADMIN", "VENDEDOR", "ALMACEN", "SUPERVISOR"};
            for (int i = 0; i < nombres.length; i++) insertar(conexion, "INSERT INTO usuario(nombre,correo,clave_hash,rol) VALUES(?,?,?,?)", nombres[i], correos[i], BCrypt.hashpw("Siscom2026!", BCrypt.gensalt(12)), roles[i]);
            insertar(conexion, "INSERT INTO cliente(documento,nombre,telefono,correo) VALUES(?,?,?,?)", "00000001", "Cliente demostracion", "900000001", "cliente@example.invalid");
            insertar(conexion, "INSERT INTO cliente(documento,nombre,telefono,correo) VALUES(?,?,?,?)", "00000002", "Empresa demostracion", "900000002", "empresa@example.invalid");
            String[] categorias = {"Computo", "Perifericos", "Almacenamiento", "Redes"};
            for (String nombre : categorias) insertar(conexion, "INSERT INTO categoria(nombre) VALUES(?)", nombre);
            String[] productos = {"Laptop Pro 14", "Teclado mecanico", "Mouse inalambrico", "SSD NVMe 1 TB", "Audifonos Studio", "Router WiFi 6"};
            int[] categorias_id = {1, 2, 2, 3, 2, 4};
            String[] precios = {"2499.00", "189.00", "79.00", "259.00", "169.00", "229.00"};
            int[] cantidades = {8, 24, 32, 3, 16, 10};
            for (int i = 0; i < productos.length; i++) {
                long id = insertar(conexion, "INSERT INTO producto(categoria_id,codigo,nombre,precio,stock,stock_minimo,imagen) VALUES(?,?,?,?,?,?,?)", categorias_id[i], String.format("TEC-%03d", i + 1), productos[i], new java.math.BigDecimal(precios[i]), cantidades[i], 5, "producto-" + (i + 1) + ".svg");
                insertar(conexion, "INSERT INTO movimiento_inventario(producto_id,usuario_id,tipo,cantidad,saldo_resultante,motivo) VALUES(?,?,?,?,?,?)", id, 1, "ENTRADA", cantidades[i], cantidades[i], "Carga inicial de laboratorio");
            }
            insertar(conexion, "INSERT INTO parametro(clave,valor) VALUES('tasa_igv',0.18)");
            String[] titulos = {"Identidad visual y catalogo de productos", "Registro de cambios y trazabilidad CAB", "Rotacion urgente de credencial comprometida"};
            String[] tipos = {"ESTANDAR", "NORMAL", "EMERGENCIA"};
            String[] componentes = {"APLICACION", "BASE_DATOS", "INFRAESTRUCTURA"};
            for (int i = 0; i < titulos.length; i++) {
                long id = insertar(conexion, "INSERT INTO solicitud_cambio(titulo,tipo,componente,justificacion,impacto,probabilidad,riesgo,plan_pruebas,plan_reversion,solicitante_id,version_objetivo,estado) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", titulos[i], tipos[i], componentes[i], "Caso de demostracion para el taller APF3", i == 0 ? 1 : i == 1 ? 3 : 5, i == 0 ? 1 : 3, i == 0 ? 1 : i == 1 ? 9 : 15, "Login venta permisos conciliacion y salud", "Detener servicio y restaurar artefacto estable con respaldo verificado", 1, "3.0.1", "REGISTRADA");
                insertar(conexion, "INSERT INTO evento_cambio(cambio_id,usuario_id,estado_anterior,estado_nuevo,nota) VALUES(?,?,?,?,?)", id, 1, "", "REGISTRADA", "Ejemplo de laboratorio sin aprobacion real");
            }
            auditar(conexion, 1, "INICIALIZAR", "sistema", 0);
            conexion.commit();
        } catch (Exception e) { conexion.rollback(); throw e; }
        finally { conexion.setAutoCommit(true); }
    }
    public static void crearAdministrador(Connection conexion, String correo, String clave) throws Exception {
        if (correo.length() > 120 || !correo.matches("[^ @]+@[^ @]+\\.[^ @]+") || clave.length() < 12 || clave.getBytes(StandardCharsets.UTF_8).length > 72) throw new IllegalStateException("Defina SISCOM_ADMIN_CORREO y SISCOM_ADMIN_CLAVE de 12 caracteres o mas");
        conexion.setAutoCommit(false);
        try {
            if (uno(conexion, "SELECT id FROM usuario LIMIT 1") != null) throw new IllegalStateException("La base ya tiene usuarios Use la administracion del sistema");
            long id = insertar(conexion, "INSERT INTO usuario(nombre,correo,clave_hash,rol) VALUES(?,?,?,?)", "Administrador SISCOM", correo.toLowerCase(java.util.Locale.ROOT), BCrypt.hashpw(clave, BCrypt.gensalt(12)), "ADMIN");
            if (uno(conexion, "SELECT clave FROM parametro WHERE clave='tasa_igv'") == null) ejecutar(conexion, "INSERT INTO parametro(clave,valor) VALUES('tasa_igv',0.18)");
            auditar(conexion, id, "CREAR_ADMIN_INICIAL", "usuario", id);
            conexion.commit();
        } catch (Exception e) { conexion.rollback(); throw e; }
        finally { conexion.setAutoCommit(true); }
    }
    private Base_datos() { }
}
