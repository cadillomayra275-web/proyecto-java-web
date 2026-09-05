package pe.utp.siscom.dao;

import pe.utp.siscom.model.Producto;
import pe.utp.siscom.util.Conexion;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductoDAO {
    private final Conexion conexion = new Conexion();

    public List<Producto> listar() throws SQLException {
        List<Producto> lista = new ArrayList<>();
        String sql = "SELECT id_producto,id_categoria,codigo,nombre,precio,stock,stock_min,estado FROM producto ORDER BY nombre";
        try (Connection cn = conexion.conectar(); PreparedStatement ps = cn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new Producto(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getDouble(5), rs.getInt(6), rs.getInt(7), rs.getBoolean(8)));
            }
        }
        return lista;
    }

    public boolean insertar(Producto p) throws SQLException {
        String sql = "INSERT INTO producto(id_categoria,codigo,nombre,precio,stock,stock_min,estado) VALUES(?,?,?,?,?,?,1)";
        try (Connection cn = conexion.conectar(); PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1,p.getId_categoria()); ps.setString(2,p.getCodigo()); ps.setString(3,p.getNombre());
            ps.setDouble(4,p.getPrecio()); ps.setInt(5,p.getStock()); ps.setInt(6,p.getStock_min());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean actualizarStock(int id_producto, int nuevo_stock) throws SQLException {
        String sql = "UPDATE producto SET stock=? WHERE id_producto=?";
        try (Connection cn = conexion.conectar(); PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1,nuevo_stock); ps.setInt(2,id_producto);
            return ps.executeUpdate() > 0;
        }
    }
}
