package pe.edu.siscom;

import com.google.gson.*;
import java.sql.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.mindrot.jbcrypt.BCrypt;
import static pe.edu.siscom.Repositorio.*;

public final class Seguridad {
    private static byte[] secreto;
    private static final String hash_ficticio = BCrypt.hashpw("Cuenta no existente", BCrypt.gensalt(12));
    public static synchronized void iniciar() throws Exception {
        String externo = Configuracion.obtener("jwt.secreto", "");
        if (!externo.isEmpty()) secreto = externo.getBytes(StandardCharsets.UTF_8);
        else if (Configuracion.demo()) {
            Path archivo = Paths.get("datos/clave_jwt.bin");
            if (!Files.exists(archivo)) { byte[] bytes = new byte[48]; new SecureRandom().nextBytes(bytes); Files.write(archivo, bytes, StandardOpenOption.CREATE_NEW); }
            secreto = Files.readAllBytes(archivo);
        } else throw new IllegalStateException("Defina SISCOM_JWT_SECRETO con al menos 32 bytes");
        if (secreto.length < 32) throw new IllegalStateException("Secreto insuficiente");
    }
    private static String base64(byte[] contenido) { return Base64.getUrlEncoder().withoutPadding().encodeToString(contenido); }
    private static byte[] firma(String contenido) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secreto, "HmacSHA256"));
        return mac.doFinal(contenido.getBytes(StandardCharsets.UTF_8));
    }
    public static JsonObject ingresar(Connection conexion, JsonObject entrada) throws Exception {
        String correo = texto(entrada, "correo", 3, 120).toLowerCase(Locale.ROOT);
        String clave = contrasena(entrada, "clave", 1);
        if (clave.getBytes(StandardCharsets.UTF_8).length > 72) throw new Fallo(401, "Credenciales incorrectas");
        JsonObject usuario = uno(conexion, "SELECT * FROM usuario WHERE correo=? AND activo=TRUE", correo);
        boolean valida = BCrypt.checkpw(clave, usuario == null ? hash_ficticio : usuario.get("clave_hash").getAsString());
        if (usuario == null || !valida) throw new Fallo(401, "Credenciales incorrectas");
        long ahora = System.currentTimeMillis() / 1000;
        JsonObject claims = objeto("sub", usuario.get("id").getAsString(), "jti", UUID.randomUUID().toString(), "iss", "siscom", "aud", "siscom-api", "iat", ahora, "nbf", ahora, "exp", ahora + 900, "ver", usuario.get("version_token").getAsInt());
        String cuerpo = base64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8)) + "." + base64(json.toJson(claims).getBytes(StandardCharsets.UTF_8));
        usuario.remove("clave_hash");
        auditar(conexion, usuario.get("id").getAsLong(), "LOGIN", "usuario", usuario.get("id").getAsLong());
        return objeto("token", cuerpo + "." + base64(firma(cuerpo)), "usuario", usuario, "expira_en", 900);
    }
    public static JsonObject autenticar(Connection conexion, String cabecera) throws Exception {
        try {
            if (cabecera == null || !cabecera.startsWith("Bearer ") || cabecera.length() > 4096) throw new Exception();
            String[] partes = cabecera.substring(7).split("\\.");
            if (partes.length != 3) throw new Exception();
            if (!MessageDigest.isEqual(firma(partes[0] + "." + partes[1]), Base64.getUrlDecoder().decode(partes[2]))) throw new Exception();
            JsonObject encabezado = JsonParser.parseString(new String(Base64.getUrlDecoder().decode(partes[0]), StandardCharsets.UTF_8)).getAsJsonObject();
            if (!"HS256".equals(encabezado.get("alg").getAsString())) throw new Exception();
            JsonObject datos = JsonParser.parseString(new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8)).getAsJsonObject();
            long ahora = System.currentTimeMillis() / 1000;
            if (!"siscom".equals(datos.get("iss").getAsString()) || !"siscom-api".equals(datos.get("aud").getAsString()) || datos.get("exp").getAsLong() <= ahora || datos.get("nbf").getAsLong() > ahora || datos.get("iat").getAsLong() > ahora) throw new Exception();
            if (uno(conexion, "SELECT jti FROM token_revocado WHERE jti=?", datos.get("jti").getAsString()) != null) throw new Exception();
            JsonObject usuario = uno(conexion, "SELECT id,nombre,correo,rol,version_token FROM usuario WHERE id=? AND activo=TRUE", datos.get("sub").getAsLong());
            if (usuario == null || usuario.get("version_token").getAsInt() != datos.get("ver").getAsInt()) throw new Exception();
            usuario.addProperty("jti", datos.get("jti").getAsString()); usuario.addProperty("exp", datos.get("exp").getAsLong());
            return usuario;
        } catch (Exception e) { if (e instanceof SQLException) throw e; throw new Fallo(401, "Sesion no valida o vencida"); }
    }
    public static void permitir(JsonObject usuario, String... roles) {
        for (String rol : roles) if (rol.equals(usuario.get("rol").getAsString())) return;
        throw new Fallo(403, "Operacion no autorizada");
    }
    private Seguridad() { }
}
