package pe.utp.siscom.model;

public class Producto {
    private int id_producto;
    private int id_categoria;
    private String codigo;
    private String nombre;
    private double precio;
    private int stock;
    private int stock_min;
    private boolean estado;

    public Producto() {}
    public Producto(int id_producto, int id_categoria, String codigo, String nombre, double precio, int stock, int stock_min, boolean estado) {
        this.id_producto = id_producto;
        this.id_categoria = id_categoria;
        this.codigo = codigo;
        this.nombre = nombre;
        this.precio = precio;
        this.stock = stock;
        this.stock_min = stock_min;
        this.estado = estado;
    }
    public int getId_producto() { return id_producto; }
    public void setId_producto(int id_producto) { this.id_producto = id_producto; }
    public int getId_categoria() { return id_categoria; }
    public void setId_categoria(int id_categoria) { this.id_categoria = id_categoria; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public double getPrecio() { return precio; }
    public void setPrecio(double precio) { this.precio = precio; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
    public int getStock_min() { return stock_min; }
    public void setStock_min(int stock_min) { this.stock_min = stock_min; }
    public boolean isEstado() { return estado; }
    public void setEstado(boolean estado) { this.estado = estado; }
}
