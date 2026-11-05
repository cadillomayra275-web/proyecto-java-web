package pe.edu.siscom;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.Context;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import java.io.File;
import java.sql.Connection;
import java.nio.file.*;
import java.time.*;

public final class Inicio {
    public static void main(String[] argumentos) throws Exception {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Lima"));
        Base_datos.iniciar();
        if (argumentos.length > 0 && argumentos[0].equals("--crear-admin")) {
            String correo = Configuracion.obtener("admin.correo", "");
            String clave = Configuracion.obtener("admin.clave", "");
            try (Connection conexion = Base_datos.abrir()) {
                Base_datos.crearAdministrador(conexion, correo, clave);
            }
            System.out.println("Administrador inicial creado"); return;
        }
        if (argumentos.length > 0 && argumentos[0].equals("--poblar")) {
            try (Connection conexion = Base_datos.abrir()) {
                if (Repositorio.uno(conexion, "SELECT id FROM usuario LIMIT 1") != null) throw new IllegalStateException("La base debe estar vacia");
                Base_datos.poblar(conexion);
            }
            return;
        }
        if (argumentos.length > 0 && argumentos[0].equals("--respaldo")) {
            if (!Configuracion.demo()) throw new IllegalStateException("Utilice mysqldump para MySQL");
            Files.createDirectories(Paths.get("respaldos"));
            String destino = new File("respaldos/siscom-" + System.currentTimeMillis() + ".zip").getAbsolutePath().replace("'", "''");
            try (Connection conexion = Base_datos.abrir()) { Repositorio.ejecutar(conexion, "BACKUP TO '" + destino + "'"); }
            System.out.println(destino); return;
        }
        Seguridad.iniciar();
        Tomcat servidor = new Tomcat();
        String puerto_externo = System.getenv("PORT");
        int puerto = Integer.parseInt(puerto_externo == null ? Configuracion.obtener("puerto", "8080") : puerto_externo);
        servidor.setPort(puerto); servidor.setBaseDir(new File("datos/tomcat").getAbsolutePath());
        servidor.getConnector().setProperty("address", Configuracion.obtener("host", "127.0.0.1"));
        servidor.getConnector().setProperty("maxThreads", "40");
        servidor.getConnector().setProperty("maxPostSize", "65536");
        servidor.getConnector().setProperty("server", "SISCOM");
        Context contexto = servidor.addContext("", new File("src/main/webapp").getAbsolutePath());
        contexto.setParentClassLoader(Inicio.class.getClassLoader());
        Tomcat.addServlet(contexto, "api", new Api_servlet()); contexto.addServletMappingDecoded("/api/*", "api");
        org.apache.catalina.Wrapper archivos = Tomcat.addServlet(contexto, "archivos", "org.apache.catalina.servlets.DefaultServlet");
        archivos.addInitParameter("listings", "false"); archivos.addInitParameter("readonly", "true"); archivos.setLoadOnStartup(1);
        contexto.addServletMappingDecoded("/", "archivos"); contexto.addWelcomeFile("index.html");
        FilterDef filtro = new FilterDef(); filtro.setFilterName("cabeceras"); filtro.setFilter(new Cabeceras()); contexto.addFilterDef(filtro);
        FilterMap mapa = new FilterMap(); mapa.setFilterName("cabeceras"); mapa.addURLPattern("/*"); contexto.addFilterMap(mapa);
        servidor.start();
        System.out.println("SISCOM 3.0.1 disponible en http://localhost:" + puerto);
        servidor.getServer().await();
    }
}
