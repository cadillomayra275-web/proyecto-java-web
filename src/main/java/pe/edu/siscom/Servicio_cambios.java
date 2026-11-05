package pe.edu.siscom;

import java.sql.*;
import java.util.*;
import com.google.gson.*;
import static pe.edu.siscom.Repositorio.*;

public final class Servicio_cambios {
    public static JsonObject crear(Connection conexion, JsonObject usuario, JsonObject entrada) throws Exception {
        String tipo = texto(entrada, "tipo", 1, 20), componente = texto(entrada, "componente", 1, 30);
        if (!Arrays.asList("ESTANDAR", "NORMAL", "EMERGENCIA").contains(tipo)) throw new Fallo(422, "Tipo de cambio invalido");
        if (!Arrays.asList("APLICACION", "BASE_DATOS", "INFRAESTRUCTURA", "DOCUMENTACION", "CRONOGRAMA").contains(componente)) throw new Fallo(422, "Componente invalido");
        int impacto = entero(entrada, "impacto", 1, 5), probabilidad = entero(entrada, "probabilidad", 1, 5);
        if (tipo.equals("ESTANDAR") && impacto * probabilidad > 4) throw new Fallo(422, "Un cambio estandar requiere riesgo bajo");
        conexion.setAutoCommit(false);
        try {
            long id = insertar(conexion, "INSERT INTO solicitud_cambio(titulo,tipo,componente,justificacion,impacto,probabilidad,riesgo,plan_pruebas,plan_reversion,solicitante_id,version_objetivo,enlace_issue) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", texto(entrada, "titulo", 8, 160), tipo, componente, texto(entrada, "justificacion", 15, 1200), impacto, probabilidad, impacto * probabilidad, texto(entrada, "plan_pruebas", 15, 2000), texto(entrada, "plan_reversion", 15, 2000), usuario.get("id").getAsLong(), texto(entrada, "version_objetivo", 1, 40), texto(entrada, "enlace_issue", 0, 300));
            insertar(conexion, "INSERT INTO evento_cambio(cambio_id,usuario_id,estado_anterior,estado_nuevo,nota) VALUES(?,?,?,?,?)", id, usuario.get("id").getAsLong(), "", "REGISTRADA", "Solicitud registrada");
            auditar(conexion, usuario.get("id").getAsLong(), "CREAR_RFC", "solicitud_cambio", id);
            conexion.commit(); return detalle(conexion, id);
        } catch (Exception e) { conexion.rollback(); throw e; }
        finally { conexion.setAutoCommit(true); }
    }
    public static JsonObject detalle(Connection conexion, long id) throws Exception {
        JsonObject cambio = uno(conexion, "SELECT c.*,u.nombre AS solicitante,a.nombre AS aprobador FROM solicitud_cambio c JOIN usuario u ON u.id=c.solicitante_id LEFT JOIN usuario a ON a.id=c.aprobador_id WHERE c.id=?", id);
        if (cambio == null) throw new Fallo(404, "Solicitud no encontrada");
        cambio.add("eventos", consultar(conexion, "SELECT e.*,u.nombre AS actor FROM evento_cambio e JOIN usuario u ON u.id=e.usuario_id WHERE cambio_id=? ORDER BY e.id", id));
        return cambio;
    }
    public static JsonObject avanzar(Connection conexion, JsonObject usuario, long id, JsonObject entrada) throws Exception {
        Seguridad.permitir(usuario, "ADMIN", "SUPERVISOR");
        String nuevo = texto(entrada, "estado", 1, 30), nota = texto(entrada, "nota", 10, 2000);
        conexion.setAutoCommit(false);
        try {
            JsonObject cambio = uno(conexion, "SELECT * FROM solicitud_cambio WHERE id=? FOR UPDATE", id);
            if (cambio == null) throw new Fallo(404, "Solicitud no encontrada");
            String actual = cambio.get("estado").getAsString();
            Map<String, List<String>> flujo = new HashMap<String, List<String>>();
            flujo.put("REGISTRADA", Arrays.asList("EVALUACION"));
            flujo.put("EVALUACION", Arrays.asList("APROBADA", "RECHAZADA"));
            flujo.put("APROBADA", Arrays.asList("PRUEBAS"));
            flujo.put("PRUEBAS", Arrays.asList("PROGRAMADA", "RECHAZADA"));
            flujo.put("PROGRAMADA", Arrays.asList("DESPLEGADA", "REVERTIDA"));
            flujo.put("DESPLEGADA", Arrays.asList("CERRADA", "REVERTIDA"));
            flujo.put("REVERTIDA", Arrays.asList("CERRADA"));
            if (!flujo.containsKey(actual) || !flujo.get(actual).contains(nuevo)) throw new Fallo(409, "Transicion no permitida desde " + actual);
            long actor = usuario.get("id").getAsLong();
            String tipo = cambio.get("tipo").getAsString();
            if (nuevo.equals("APROBADA") || nuevo.equals("RECHAZADA")) {
                if (actor == cambio.get("solicitante_id").getAsLong()) throw new Fallo(403, "El solicitante no puede aprobar su cambio");
                if (tipo.equals("EMERGENCIA")) Seguridad.permitir(usuario, "ADMIN");
                if (tipo.equals("NORMAL")) Seguridad.permitir(usuario, "SUPERVISOR");
                ejecutar(conexion, "UPDATE solicitud_cambio SET aprobador_id=? WHERE id=?", actor, id);
            }
            if (nuevo.equals("PROGRAMADA")) {
                Seguridad.permitir(usuario, "SUPERVISOR");
                if (actor == cambio.get("solicitante_id").getAsLong()) throw new Fallo(403, "QA requiere un revisor diferente");
                String sha = texto(entrada, "artefacto_sha", 64, 64);
                if (!sha.matches("[a-fA-F0-9]{64}")) throw new Fallo(422, "SHA256 invalido");
                ejecutar(conexion, "UPDATE solicitud_cambio SET evidencia_qa=?,respaldo=?,artefacto_sha=?,ventana=? WHERE id=?", texto(entrada, "evidencia_qa", 15, 2000), texto(entrada, "respaldo", 5, 240), sha, texto(entrada, "ventana", 10, 100), id);
            }
            if (nuevo.equals("DESPLEGADA") || nuevo.equals("REVERTIDA")) Seguridad.permitir(usuario, "ADMIN");
            if (nuevo.equals("CERRADA")) Seguridad.permitir(usuario, "SUPERVISOR");
            ejecutar(conexion, "UPDATE solicitud_cambio SET estado=?,actualizado_en=CURRENT_TIMESTAMP WHERE id=?", nuevo, id);
            insertar(conexion, "INSERT INTO evento_cambio(cambio_id,usuario_id,estado_anterior,estado_nuevo,nota) VALUES(?,?,?,?,?)", id, actor, actual, nuevo, nota);
            auditar(conexion, actor, "RFC_" + nuevo, "solicitud_cambio", id);
            conexion.commit(); return detalle(conexion, id);
        } catch (Exception e) { conexion.rollback(); throw e; }
        finally { conexion.setAutoCommit(true); }
    }
    private Servicio_cambios() { }
}
