package pe.utp.siscom.dao;

import pe.utp.siscom.model.Usuario;
import pe.utp.siscom.util.Conexion;
import java.sql.*;

public class UsuarioDAO {
    private final Conexion conexion = new Conexion();
    public Usuario autenticar(String correo, String clave) throws SQLException {
        String sql = "SELECT id_usuario,nombre,correo,rol FROM usuario WHERE correo=? AND clave_hash=SHA2(?,256) AND estado=1";
        try (Connection cn=conexion.conectar(); PreparedStatement ps=cn.prepareStatement(sql)) {
            ps.setString(1,correo); ps.setString(2,clave);
            try (ResultSet rs=ps.executeQuery()) {
                if (rs.next()) {
                    Usuario u=new Usuario(); u.setId_usuario(rs.getInt(1)); u.setNombre(rs.getString(2)); u.setCorreo(rs.getString(3)); u.setRol(rs.getString(4)); return u;
                }
            }
        }
        return null;
    }
}
