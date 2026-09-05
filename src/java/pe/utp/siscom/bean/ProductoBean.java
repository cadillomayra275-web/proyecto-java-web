package pe.utp.siscom.bean;

import pe.utp.siscom.dao.ProductoDAO;
import pe.utp.siscom.model.Producto;
import javax.annotation.PostConstruct;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import java.io.Serializable;
import java.util.List;

@ManagedBean(name="productoBean")
@ViewScoped
public class ProductoBean implements Serializable {
    private List<Producto> productos;
    private Producto producto=new Producto();
    @PostConstruct public void init(){ cargar(); }
    public void cargar(){ try{ productos=new ProductoDAO().listar(); }catch(Exception e){ throw new RuntimeException(e); } }
    public void guardar(){ try{ new ProductoDAO().insertar(producto); producto=new Producto(); cargar(); }catch(Exception e){ throw new RuntimeException(e); } }
    public List<Producto> getProductos(){ return productos; }
    public Producto getProducto(){ return producto; }
    public void setProducto(Producto producto){ this.producto=producto; }
}
