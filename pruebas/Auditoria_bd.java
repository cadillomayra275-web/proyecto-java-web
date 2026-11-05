import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import pe.edu.siscom.Base_datos;
import pe.edu.siscom.Repositorio;

public class Auditoria_bd {
    private static Connection abrir(String nombre) throws Exception {
        Connection c=DriverManager.getConnection("jdbc:h2:mem:"+nombre+";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        String ddl=new String(Files.readAllBytes(Paths.get("sql/01_esquema.sql")),StandardCharsets.UTF_8);
        for(String s:ddl.split(";"))if(!s.trim().isEmpty())Repositorio.ejecutar(c,s);
        return c;
    }
    private static long contar(Connection c,String tabla) throws Exception {return Repositorio.uno(c,"SELECT COUNT(*) AS n FROM "+tabla).get("n").getAsLong();}
    private static void exigir(boolean condicion) {if(!condicion)throw new AssertionError("No se cumplio el criterio");}
    public static void main(String[] args) throws Exception {
        List<Object> resultados=new ArrayList<Object>();
        try(Connection c=abrir("alta")) {
            Repositorio.ejecutar(c,"INSERT INTO parametro(clave,valor) VALUES('tasa_igv',0.20)");
            Base_datos.crearAdministrador(c,"admin@example.invalid","ClavePrueba2026!");
            exigir(contar(c,"usuario")==1 && contar(c,"bitacora")==1);
            exigir(Repositorio.uno(c,"SELECT valor FROM parametro WHERE clave='tasa_igv'").get("valor").getAsBigDecimal().compareTo(new java.math.BigDecimal("0.20"))==0);
            boolean rechazado=false;try{Base_datos.crearAdministrador(c,"otro@example.invalid","ClavePrueba2026!");}catch(IllegalStateException e){rechazado=true;}
            exigir(rechazado && contar(c,"usuario")==1);
            resultados.add(Repositorio.objeto("id","BD01","nombre","Alta inicial conserva parametro y evita segundo bootstrap","resultado","APROBADO"));
        }
        try(Connection c=abrir("rollback")) {
            Repositorio.ejecutar(c,"ALTER TABLE bitacora ADD CONSTRAINT prueba_fallo CHECK(accion <> 'CREAR_ADMIN_INICIAL')");
            boolean rechazado=false;try{Base_datos.crearAdministrador(c,"admin@example.invalid","ClavePrueba2026!");}catch(SQLException e){rechazado=true;}
            exigir(rechazado && contar(c,"usuario")==0 && contar(c,"parametro")==0 && contar(c,"bitacora")==0 && c.getAutoCommit());
            resultados.add(Repositorio.objeto("id","BD02","nombre","Fallo del alta inicial revierte usuario parametro y bitacora","resultado","APROBADO"));
        }
        try(Connection c=abrir("integridad")) {
            Base_datos.poblar(c);
            String[] invalidos={"UPDATE movimiento_inventario SET tipo='SALIDA' WHERE id=1", "UPDATE solicitud_cambio SET riesgo=25 WHERE id=1", "UPDATE usuario SET version_token=-1 WHERE id=1"};
            for(String sql:invalidos){boolean rechazado=false;try{Repositorio.ejecutar(c,sql);}catch(SQLException e){rechazado=true;}exigir(rechazado);}
            exigir(contar(c,"usuario")==4);
            resultados.add(Repositorio.objeto("id","BD03","nombre","Restricciones SQL rechazan signo riesgo y version invalidos","resultado","APROBADO"));
        }
        Files.write(Paths.get("pruebas/auditoria_bd.json"),Repositorio.json.toJson(Repositorio.objeto("fecha",java.time.Instant.now().toString(),"motor","H2 en memoria","pruebas",resultados.size(),"aprobadas",resultados.size(),"resultados",resultados)).getBytes(StandardCharsets.UTF_8));
        System.out.println("BD01 APROBADO\nBD02 APROBADO\nBD03 APROBADO");
    }
}
