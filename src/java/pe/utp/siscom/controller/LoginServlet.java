package pe.utp.siscom.controller;

import pe.utp.siscom.dao.UsuarioDAO;
import pe.utp.siscom.model.Usuario;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String correo=req.getParameter("correo"); String clave=req.getParameter("clave");
        try {
            Usuario u=new UsuarioDAO().autenticar(correo,clave);
            if (u!=null) {
                req.getSession().setAttribute("usuario",u);
                resp.sendRedirect(req.getContextPath()+"/dashboard.xhtml");
            } else {
                req.setAttribute("error","Credenciales incorrectas");
                req.getRequestDispatcher("/login.jsp").forward(req,resp);
            }
        } catch (Exception e) { throw new ServletException(e); }
    }
}
